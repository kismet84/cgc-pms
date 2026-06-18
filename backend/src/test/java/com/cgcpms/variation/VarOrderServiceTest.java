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
 * VarOrder submit and template verification — GREEN phase.
 * <p>
 * Changes since the RED phase:
 * <ul>
 *   <li>VAR_ORDER workflow template has been seeded in H2 (V70 + V71 repair).</li>
 *   <li>V75 added deleted_flag to uk_wf_instance_business, allowing soft-deleted rows
 *       and active rows to coexist without a unique-key conflict.</li>
 * </ul>
 * Tests now validate correct behaviour when the template IS present.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("VarOrderService — VAR_ORDER submit and template verification")
class VarOrderServiceTest {

    // Use unique IDs to avoid collisions with ContractCompositeSaveTest
    // which seeds PmProject(id=10001) with a different name.
    private static final long PROJECT_ID  = 10018L;
    private static final long PARTNER_ID  = 20018L;
    private static final long CONTRACT_ID = 30018L;

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
            project.setProjectCode("PRJ-TEST-VAR-018");
            project.setProjectName("变更单测专用项目");
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
            partner.setPartnerCode("PT-TEST-VAR-018");
            partner.setPartnerName("变更单测合作方");
            partner.setPartnerType("PARTY_A");
            partner.setBlacklistFlag(0);
            partner.setStatus("ENABLE");
            partnerMapper.insert(partner);
        }

        if (contractMapper.selectById(CONTRACT_ID) == null) {
            CtContract contract = new CtContract();
            contract.setId(CONTRACT_ID);
            contract.setProjectId(PROJECT_ID);
            contract.setContractCode("CT-TEST-VAR-018");
            contract.setContractName("变更单测合同");
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
    // GREEN-1: VAR_ORDER template EXISTS (V70+V71 seeded it)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("GREEN-1: VAR_ORDER workflow template exists and is enabled")
    void testVarOrderTemplateExists() {
        WfTemplate template = wfTemplateMapper.selectOne(
                new LambdaQueryWrapper<WfTemplate>()
                        .eq(WfTemplate::getBusinessType, "VAR_ORDER")
                        .eq(WfTemplate::getEnabled, 1));
        assertNotNull(template,
                "VAR_ORDER审批模板应存在且启用");
    }

    // ═══════════════════════════════════════════════════════════════
    // GREEN-2: Submit succeeds because template is now present
    // (Previously RED-2 expected TEMPLATE_NOT_FOUND)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("GREEN-2: submitForApproval succeeds when VAR_ORDER template is present")
    void testSubmitSucceedsWithTemplate() {
        VarOrder order = new VarOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setPartnerId(PARTNER_ID);
        order.setVarName("变更单测-GREEN2");
        order.setVarType("DESIGN_CHANGE");
        order.setDirection("COST");
        order.setReportedAmount(new BigDecimal("50000.00"));
        order.setApprovalStatus("DRAFT");
        Long id = varOrderService.create(order);
        assertNotNull(id, "创建VarOrder应返回ID");

        // Submit must succeed now — template is present
        assertDoesNotThrow(() ->
                varOrderService.submitForApproval(id),
                "提交审批应成功，临时模板已存在");
    }

    // ═══════════════════════════════════════════════════════════════
    // GREEN-3: Duplicate submit is rejected
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("GREEN-3: duplicate submit of same VarOrder throws VAR_ORDER_ALREADY_SUBMITTED")
    void testDuplicateSubmitRejected() {
        VarOrder order = new VarOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setPartnerId(PARTNER_ID);
        order.setVarName("变更单测-GREEN3");
        order.setVarType("DESIGN_CHANGE");
        order.setDirection("COST");
        order.setReportedAmount(new BigDecimal("30000.00"));
        order.setApprovalStatus("DRAFT");
        Long id = varOrderService.create(order);
        assertNotNull(id);

        // First submit succeeds (template exists)
        varOrderService.submitForApproval(id);

        // Verify first submit created wf_instance
        WfInstance instance = wfInstanceMapper.selectOne(
                new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getBusinessType, "VAR_ORDER")
                        .eq(WfInstance::getBusinessId, id));
        assertNotNull(instance, "首次提交应创建审批实例");

        // Second submit must be rejected
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            varOrderService.submitForApproval(id);
        }, "重复提交应抛出异常");
        assertEquals("VAR_ORDER_ALREADY_SUBMITTED", ex.getCode(),
                "错误码应为 VAR_ORDER_ALREADY_SUBMITTED");
    }

    // ═══════════════════════════════════════════════════════════════
    // SAFE: Null-safe batch VO mapping tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("SAFE-1: getPage with null contractId/partnerId should not NPE")
    void testGetPageWithNullRelationIds() {
        VarOrder order = new VarOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(null);
        order.setPartnerId(null);
        order.setVarName("null-relation 单测");
        order.setVarType("DESIGN_CHANGE");
        order.setDirection("COST");
        order.setReportedAmount(new BigDecimal("10000.00"));
        order.setApprovalStatus("DRAFT");
        Long id = varOrderService.create(order);
        assertNotNull(id);

        com.baomidou.mybatisplus.core.metadata.IPage<com.cgcpms.variation.vo.VarOrderVO> page =
                varOrderService.getPage(1, 10, null, null, null, null, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() > 0, "至少应有一条记录");

        com.cgcpms.variation.vo.VarOrderVO vo = page.getRecords().get(0);
        assertEquals("null-relation 单测", vo.getVarName());
        assertNotNull(vo.getProjectName(), "projectName 应填充");
        assertEquals("变更单测专用项目", vo.getProjectName());
        assertNull(vo.getContractName(), "contractName 应为 null");
        assertNull(vo.getPartnerName(), "partnerName 应为 null");
    }

    @Test
    @Transactional
    @DisplayName("SAFE-2: getPage with all relation IDs should populate all names")
    void testGetPageWithAllRelationIds() {
        VarOrder order = new VarOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setPartnerId(PARTNER_ID);
        order.setVarName("full-relation 单测");
        order.setVarType("DESIGN_CHANGE");
        order.setDirection("COST");
        order.setReportedAmount(new BigDecimal("20000.00"));
        order.setApprovalStatus("DRAFT");
        Long id = varOrderService.create(order);
        assertNotNull(id);

        com.baomidou.mybatisplus.core.metadata.IPage<com.cgcpms.variation.vo.VarOrderVO> page =
                varOrderService.getPage(1, 10, null, null, null, null, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() > 0, "至少应有一条记录");

        com.cgcpms.variation.vo.VarOrderVO vo = page.getRecords().get(0);
        assertEquals("full-relation 单测", vo.getVarName());
        assertNotNull(vo.getProjectName(), "projectName 应填充");
        assertEquals("变更单测专用项目", vo.getProjectName());
        assertNotNull(vo.getContractName(), "contractName 应填充");
        assertEquals("变更单测合同", vo.getContractName());
        assertNotNull(vo.getPartnerName(), "partnerName 应填充");
        assertEquals("变更单测合作方", vo.getPartnerName());
    }
}
