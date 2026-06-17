package com.cgcpms.variation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.variation.service.VarOrderService;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTemplate;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTemplateMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED phase — reproduce variation order submit failures.
 * <p>
 * Known bugs:
 * <ul>
 *   <li>VAR_ORDER workflow template may be missing in MySQL (V17 migration data drift)</li>
 *   <li>Duplicate workflow instances block resubmission (logical-delete + unique key)</li>
 * </ul>
 * ALL tests expect failure on current code — this is the RED phase.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("VarOrderService — VAR_ORDER submit and template verification")
class VarOrderServiceTest {

    private static final long PROJECT_ID = 10001L;
    private static final long PARTNER_ID = 20001L;
    private static final long CONTRACT_ID = 30001L;

    @Autowired
    private VarOrderService varOrderService;

    @Autowired
    private VarOrderMapper varOrderMapper;

    @Autowired
    private PmProjectMapper projectMapper;

    @Autowired
    private MdPartnerMapper partnerMapper;

    @Autowired
    private CtContractMapper contractMapper;

    @Autowired
    private WfInstanceMapper wfInstanceMapper;

    @Autowired
    private WfTemplateMapper wfTemplateMapper;

    private Long varOrderId;

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
            project.setProjectCode("PRJ-TEST-VAR");
            project.setProjectName("测试变更新项目");
            project.setProjectType("CONSTRUCTION");
            project.setContractAmount(new BigDecimal("5000000.00"));
            project.setTargetCost(new BigDecimal("4200000.00"));
            project.setStatus("RUNNING");
            project.setApprovalStatus("APPROVED");
            projectMapper.insert(project);
        }

        if (partnerMapper.selectById(PARTNER_ID) == null) {
            MdPartner partner = new MdPartner();
            partner.setId(PARTNER_ID);
            partner.setPartnerCode("PT-TEST-VAR");
            partner.setPartnerName("变更测试合作方");
            partner.setPartnerType("PARTY_A");
            partner.setBlacklistFlag(0);
            partner.setStatus("ENABLE");
            partnerMapper.insert(partner);
        }

        if (contractMapper.selectById(CONTRACT_ID) == null) {
            CtContract contract = new CtContract();
            contract.setId(CONTRACT_ID);
            contract.setProjectId(PROJECT_ID);
            contract.setContractCode("CT-TEST-VAR-001");
            contract.setContractName("变更测试合同");
            contract.setContractType("SUB");
            contract.setPartyAId(PARTNER_ID);
            contract.setPartyBId(PARTNER_ID);
            contract.setContractAmount(new BigDecimal("640000.00"));
            contract.setCurrentAmount(new BigDecimal("640000.00"));
            contract.setPaidAmount(BigDecimal.ZERO);
            contract.setContractStatus("DRAFT");
            contract.setApprovalStatus("DRAFT");
            contractMapper.insert(contract);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-1: Verify VAR_ORDER template EXISTS
    // This test FAILS if the template is missing (known MySQL bug).
    // In H2 local profile, V17 migration may or may not seed it
    // (depends on JSON_OBJECT support). If missing, this test
    // documents the TemplateNotFoundException.
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("RED-1: VAR_ORDER workflow template should exist (enabled)")
    void testVarOrderTemplateExists() {
        WfTemplate template = wfTemplateMapper.selectOne(
                new LambdaQueryWrapper<WfTemplate>()
                        .eq(WfTemplate::getBusinessType, "VAR_ORDER")
                        .eq(WfTemplate::getEnabled, 1));
        assertNotNull(template,
                "VAR_ORDER审批模板应存在且启用 — 当前缺失表示 V17 迁移数据异常");
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-2: Create and submit VarOrder → FAILS if template missing
    // Current code: submitForApproval calls workflowEngine.submit
    // which calls core.findTemplate("VAR_ORDER").
    // If template is missing → BusinessException("TEMPLATE_NOT_FOUND")
    // This test FAILS (unexpected success) if the template IS present,
    // which is the RED indicator for the missing-template bug.
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("RED-2: submitForApproval fails when VAR_ORDER template is missing")
    void testSubmitFailsOnMissingTemplate() {
        VarOrder order = new VarOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setPartnerId(PARTNER_ID);
        order.setVarName("测试变更签证-RED2");
        order.setVarType("DESIGN_CHANGE");
        order.setDirection("COST");
        order.setReportedAmount(new BigDecimal("50000.00"));
        order.setApprovalStatus("DRAFT");
        varOrderId = varOrderService.create(order);
        assertNotNull(varOrderId, "创建VarOrder应返回ID");

        // Attempt submit — expect failure due to missing template
        // RED assertion: the submit SHOULD throw TEMPLATE_NOT_FOUND
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            varOrderService.submitForApproval(varOrderId);
        }, "提交审批应因缺少VAR_ORDER模板而失败");
        assertEquals("TEMPLATE_NOT_FOUND", ex.getCode(),
                "错误码应为 TEMPLATE_NOT_FOUND，表明模板缺失");
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-3: Duplicate submit should be rejected
    // Even if RED-2 eventually passes (template exists), submitting
    // the same VarOrder twice must throw VAR_ORDER_ALREADY_SUBMITTED.
    // This test verifies the duplicate-submit guard works.
    // NOTE: This test depends on the template existing to test
    // the duplicate guard. If template is missing, this test will
    // also fail with TEMPLATE_NOT_FOUND (still RED).
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("RED-3: duplicate submit of same VarOrder throws VAR_ORDER_ALREADY_SUBMITTED")
    void testDuplicateSubmitRejected() {
        VarOrder order = new VarOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setPartnerId(PARTNER_ID);
        order.setVarName("测试变更签证-RED3");
        order.setVarType("DESIGN_CHANGE");
        order.setDirection("COST");
        order.setReportedAmount(new BigDecimal("30000.00"));
        order.setApprovalStatus("DRAFT");
        varOrderId = varOrderService.create(order);
        assertNotNull(varOrderId);

        // First submit — may succeed if template exists, or fail if not
        // Either way, the second submit should fail if first succeeded
        try {
            varOrderService.submitForApproval(varOrderId);
        } catch (BusinessException e) {
            // Template not found — this is the RED-2 scenario
            // In this case RED-3 cannot test duplicate guard fully,
            // but the test still fails (RED) because we could not
            // complete the first submit.
            if ("TEMPLATE_NOT_FOUND".equals(e.getCode())) {
                fail("RED-3: 无法测试重复提交，因为模板缺失导致首次提交失败（TEMPLATE_NOT_FOUND）");
            }
            throw e;
        }

        // Verify first submit created wf_instance
        WfInstance instance = wfInstanceMapper.selectOne(
                new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getBusinessType, "VAR_ORDER")
                        .eq(WfInstance::getBusinessId, varOrderId));
        assertNotNull(instance, "首次提交应创建审批实例");

        // Second submit must be rejected
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            varOrderService.submitForApproval(varOrderId);
        }, "重复提交应抛出异常");
        assertEquals("VAR_ORDER_ALREADY_SUBMITTED", ex.getCode(),
                "错误码应为 VAR_ORDER_ALREADY_SUBMITTED");
    }
}
