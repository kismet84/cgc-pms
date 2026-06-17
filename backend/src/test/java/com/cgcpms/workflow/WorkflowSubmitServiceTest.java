package com.cgcpms.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD GREEN phase — workflow duplicate and logical-delete fixes.
 * <p>
 * Fixes applied:
 * <ul>
 *   <li>Duplicate (businessType,businessId) now throws
 *       {@code BusinessException("WORKFLOW_INSTANCE_EXISTS")}
 *       instead of DuplicateKeyException → SYSTEM_ERROR (500)</li>
 *   <li>Logically-deleted stale instances are hard-deleted before
 *       insert, freeing the unique key slot for resubmission</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("WorkflowSubmitService — duplicate and logical-delete conflicts")
class WorkflowSubmitServiceTest {

    private static final long PROJECT_ID = 10001L;
    private static final long PARTNER_A_ID = 20001L;
    private static final long PARTNER_B_ID = 20002L;
    private static final long CONTRACT_ID_DUPLICATE = 31001L;
    private static final long CONTRACT_ID_DELETED = 31002L;
    private static final String BUSINESS_TYPE = "CONTRACT_APPROVAL";

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private WfInstanceMapper wfInstanceMapper;

    @Autowired
    private PmProjectMapper projectMapper;

    @Autowired
    private MdPartnerMapper partnerMapper;

    @Autowired
    private CtContractMapper contractMapper;

    @BeforeEach
    void setupContext() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        seedReferenceData();
    }

    @AfterEach
    void clearContext() {
        TestUserContext.clear();
    }

    private void seedReferenceData() {
        if (projectMapper.selectById(PROJECT_ID) == null) {
            PmProject project = new PmProject();
            project.setId(PROJECT_ID);
            project.setProjectCode("PRJ-TEST-WF");
            project.setProjectName("测试工作流项目");
            project.setProjectType("CONSTRUCTION");
            project.setContractAmount(new BigDecimal("5000000.00"));
            project.setTargetCost(new BigDecimal("4200000.00"));
            project.setStatus("RUNNING");
            project.setApprovalStatus("APPROVED");
            projectMapper.insert(project);
        }

        if (partnerMapper.selectById(PARTNER_A_ID) == null) {
            MdPartner partyA = new MdPartner();
            partyA.setId(PARTNER_A_ID);
            partyA.setPartnerCode("PT-TEST-WF-A");
            partyA.setPartnerName("工作流测试甲方");
            partyA.setPartnerType("PARTY_A");
            partyA.setBlacklistFlag(0);
            partyA.setStatus("ENABLE");
            partnerMapper.insert(partyA);
        }

        if (partnerMapper.selectById(PARTNER_B_ID) == null) {
            MdPartner partyB = new MdPartner();
            partyB.setId(PARTNER_B_ID);
            partyB.setPartnerCode("PT-TEST-WF-B");
            partyB.setPartnerName("工作流测试乙方");
            partyB.setPartnerType("PARTY_B");
            partyB.setBlacklistFlag(0);
            partyB.setStatus("ENABLE");
            partnerMapper.insert(partyB);
        }

        ensureContract(CONTRACT_ID_DUPLICATE, "CT-TEST-WF-DUP", "工作流重复提交测试合同");
        ensureContract(CONTRACT_ID_DELETED, "CT-TEST-WF-DEL", "工作流逻辑删除测试合同");
    }

    private void ensureContract(Long id, String code, String name) {
        if (contractMapper.selectById(id) != null) return;
        CtContract contract = new CtContract();
        contract.setId(id);
        contract.setProjectId(PROJECT_ID);
        contract.setContractCode(code);
        contract.setContractName(name);
        contract.setContractType("SUB");
        contract.setPartyAId(PARTNER_A_ID);
        contract.setPartyBId(PARTNER_B_ID);
        contract.setContractAmount(new BigDecimal("640000.00"));
        contract.setCurrentAmount(new BigDecimal("640000.00"));
        contract.setPaidAmount(BigDecimal.ZERO);
        contract.setContractStatus("DRAFT");
        contract.setApprovalStatus("DRAFT");
        contractMapper.insert(contract);
    }

    // ═══════════════════════════════════════════════════════════════
    // GREEN-1: Duplicate submission produces explicit BusinessException
    // Submitting the same (businessType, businessId) twice now
    // throws BusinessException with code WORKFLOW_INSTANCE_EXISTS
    // instead of a raw DuplicateKeyException → SYSTEM_ERROR (500).
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("GREEN-1: duplicate (businessType,businessId) submit throws BusinessException")
    void testDuplicateSubmitCausesUniqueError() {
        long businessId = CONTRACT_ID_DUPLICATE;

        // First submit — should succeed (CONTRACT_APPROVAL template exists in H2 V9)
        WfInstance first = workflowEngine.submit(
                TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                BUSINESS_TYPE, businessId,
                "工作流重复提交测试", new BigDecimal("640000.00"),
                PROJECT_ID, businessId,
                null, null, null);
        assertNotNull(first, "首次提交应创建实例");
        assertEquals(WorkflowConstants.INSTANCE_RUNNING, first.getInstanceStatus());

        // Second submit with SAME businessType+businessId → BusinessException
        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.submit(
                        TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                        BUSINESS_TYPE, businessId,
                        "工作流重复提交测试-第二次", new BigDecimal("640000.00"),
                        PROJECT_ID, businessId,
                        null, null, null));
        assertEquals("WORKFLOW_INSTANCE_EXISTS", ex.getCode(), "重复提交应返回业务错误码");
        assertEquals("该业务已提交审批，请勿重复提交", ex.getMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // GREEN-2: Logically deleted stale instances are hard-deleted
    // before insert, freeing the unique key slot for resubmission.
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("GREEN-2: logically deleted instance cleaned up, resubmit succeeds")
    void testDeletedInstanceBlocksResubmission() {
        long businessId = CONTRACT_ID_DELETED;

        // First submit — create an active instance
        WfInstance first = workflowEngine.submit(
                TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                BUSINESS_TYPE, businessId,
                "工作流逻辑删除测试", new BigDecimal("640000.00"),
                PROJECT_ID, businessId,
                null, null, null);
        assertNotNull(first, "首次提交应创建实例");

        // Logically delete the instance (set deleted_flag=1 via MyBatis-Plus)
        wfInstanceMapper.deleteById(first.getId());

        // Verify the instance is logically deleted (MyBatis-Plus hides it)
        WfInstance deleted = wfInstanceMapper.selectById(first.getId());
        assertNull(deleted, "逻辑删除后 MyBatis-Plus 查询应返回 null");

        // Resubmit with same (businessType, businessId) — should succeed now
        // because submit() hard-deletes stale logically-deleted rows first
        WfInstance second = workflowEngine.submit(
                TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                BUSINESS_TYPE, businessId,
                "工作流逻辑删除后重新提交", new BigDecimal("640000.00"),
                PROJECT_ID, businessId,
                null, null, null);
        assertNotNull(second, "清理逻辑删除旧数据后应允许重新提交");
        assertEquals(WorkflowConstants.INSTANCE_RUNNING, second.getInstanceStatus());
        assertNotEquals(first.getId(), second.getId(), "新实例应有不同的ID");
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-3: Verify CONTRACT_APPROVAL template exists
    // Prerequisite for RED-1 and RED-2. If the template is missing,
    // the workflow submit would fail with TEMPLATE_NOT_FOUND instead
    // of hitting the duplicate key issue.
    // This test verifies the template exists.
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("RED-3: CONTRACT_APPROVAL workflow template exists and is enabled")
    void testContractApprovalTemplateExists() {
        WfInstance instance = workflowEngine.submit(
                TestUserContext.USER_ADMIN, "admin", TestUserContext.TENANT_0,
                BUSINESS_TYPE, 31999L,
                "模板存在性验证", new BigDecimal("1000.00"),
                PROJECT_ID, 31999L,
                null, null, null);
        assertNotNull(instance, "CONTRACT_APPROVAL 模板应存在并可创建实例");
        assertEquals(WorkflowConstants.INSTANCE_RUNNING, instance.getInstanceStatus());
    }
}
