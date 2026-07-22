package com.cgcpms.variation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.entity.VarOrderItem;
import com.cgcpms.variation.mapper.VarOrderItemMapper;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.variation.service.VarOrderService;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTemplate;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTemplateMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
    private static final long TENANT_ID = 0L;
    private static final long USER_ADMIN = 1L;

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
    private VarOrderItemMapper varOrderItemMapper;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupContext() {
        setAdminContext();
        seedWorkflowApprover();
        seedReferenceData();
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    private void seedWorkflowApprover() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ?", Integer.class, USER_ADMIN);
        if (count != null && count == 0) {
            jdbcTemplate.update("""
                    INSERT INTO sys_user
                        (id, tenant_id, username, password, real_name, status, is_admin,
                         created_by, updated_by, deleted_flag, remark)
                    VALUES (?, ?, ?, ?, ?, 'ENABLE', 1, ?, ?, 0, ?)
                    """, USER_ADMIN, TENANT_ID,
                    "var_order_test_approver", "{noop}test", "变更审批测试人",
                    USER_ADMIN, USER_ADMIN,
                    "VarOrderServiceTest local approver");
        } else {
            jdbcTemplate.update("""
                    UPDATE sys_user
                    SET tenant_id = ?, status = 'ENABLE', deleted_flag = 0
                    WHERE id = ?
                    """, TENANT_ID, USER_ADMIN);
        }
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
        prepareSubmission(id);

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
        prepareSubmission(id);

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
    // GREEN-4: saveItems — 保存明细后金额映至订单
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("GREEN-4: saveItems 自动聚合明细金额到订单")
    void testSaveItemsAggregatesAmountToOrder() {
        VarOrder order = new VarOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setPartnerId(PARTNER_ID);
        order.setVarName("变更单测-金额聚合");
        order.setVarType("DESIGN_CHANGE");
        order.setDirection("COST");
        order.setReportedAmount(BigDecimal.ZERO);
        order.setApprovalStatus("DRAFT");
        Long id = varOrderService.create(order);
        assertNotNull(id);

        VarOrderItem item1 = new VarOrderItem();
        item1.setVarOrderId(id);
        item1.setItemName("条目A");
        item1.setUnit("m²");
        item1.setQuantity(new BigDecimal("10"));
        item1.setUnitPrice(new BigDecimal("200.00"));
        item1.setAmount(new BigDecimal("2000.00"));
        item1.setCostSubjectId(90001L);

        VarOrderItem item2 = new VarOrderItem();
        item2.setVarOrderId(id);
        item2.setItemName("条目B");
        item2.setUnit("m");
        item2.setQuantity(new BigDecimal("50"));
        item2.setUnitPrice(new BigDecimal("60.00"));
        item2.setAmount(new BigDecimal("3000.00"));
        item2.setCostSubjectId(90002L);

        varOrderService.saveItems(id, java.util.List.of(item1, item2));

        VarOrder updated = varOrderMapper.selectById(id);
        assertNotNull(updated.getReportedAmount(), "金额应被聚合");
        assertEquals(0, new BigDecimal("5000.00").compareTo(updated.getReportedAmount()),
                "聚合金额应为 2000+3000=5000");
    }

    private void prepareSubmission(Long id) {
        VarOrderItem item = new VarOrderItem();
        item.setItemName("审批测试明细");
        item.setUnit("项");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setClaimUnitPrice(new BigDecimal("120.00"));
        item.setCostSubjectId(90001L);
        varOrderService.saveItems(id, java.util.List.of(item));
        jdbcTemplate.update("""
                INSERT INTO sys_file(id, tenant_id, business_type, document_type, business_id,
                    file_name, original_name, file_size, content_type, storage_path, bucket_name,
                    created_at, updated_at, deleted_flag)
                VALUES(?, 0, 'VARIATION', 'SITE_EVIDENCE', ?, ?, 'evidence.pdf',
                    128, 'application/pdf', ?, 'cgc-pms', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), id,
                "evidence-" + id + ".pdf", "VARIATION/" + id + "/evidence.pdf");
    }

    @Test
    @Transactional
    @DisplayName("GREEN-4B: create requires contract and varType")
    void testCreateRequiresContractAndVarType() {
        VarOrder missingContract = new VarOrder();
        missingContract.setProjectId(PROJECT_ID);
        missingContract.setVarName("缺合同");
        missingContract.setVarType("现场签证");
        missingContract.setDirection("COST");

        BusinessException missingContractEx = assertThrows(BusinessException.class, () ->
                varOrderService.create(missingContract));
        assertEquals("VAR_ORDER_CONTRACT_REQUIRED", missingContractEx.getCode());

        VarOrder missingType = new VarOrder();
        missingType.setProjectId(PROJECT_ID);
        missingType.setContractId(CONTRACT_ID);
        missingType.setVarName("缺类型");
        missingType.setDirection("COST");

        BusinessException missingTypeEx = assertThrows(BusinessException.class, () ->
                varOrderService.create(missingType));
        assertEquals("VAR_ORDER_TYPE_REQUIRED", missingTypeEx.getCode());
    }

    @Test
    @Transactional
    @DisplayName("GREEN-4C: saveItems rejects empty or invalid draft items")
    void testSaveItemsRejectsEmptyOrInvalidItems() {
        VarOrder order = new VarOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setPartnerId(PARTNER_ID);
        order.setVarName("变更单测-无效明细");
        order.setVarType("现场签证");
        order.setDirection("COST");
        Long id = varOrderService.create(order);

        BusinessException emptyItemsEx = assertThrows(BusinessException.class, () ->
                varOrderService.saveItems(id, java.util.List.of()));
        assertEquals("VAR_ORDER_ITEMS_REQUIRED", emptyItemsEx.getCode());

        VarOrderItem invalidItem = new VarOrderItem();
        invalidItem.setItemName("缺成本科目");
        invalidItem.setQuantity(new BigDecimal("1"));
        invalidItem.setUnitPrice(new BigDecimal("100.00"));
        invalidItem.setAmount(new BigDecimal("100.00"));

        BusinessException missingCostSubjectEx = assertThrows(BusinessException.class, () ->
                varOrderService.saveItems(id, java.util.List.of(invalidItem)));
        assertEquals("VAR_ORDER_ITEM_COST_SUBJECT_REQUIRED", missingCostSubjectEx.getCode());
    }

    @Test
    @Transactional
    @DisplayName("CAS: 旧版本不可更新、保存明细或删除草稿")
    void staleVersionRejectsDraftMutations() {
        VarOrder order = new VarOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setPartnerId(PARTNER_ID);
        order.setVarName("变更单测-CAS");
        order.setVarType("DESIGN_CHANGE");
        order.setDirection("COST");
        Long id = varOrderService.create(order);
        Integer staleVersion = varOrderMapper.selectById(id).getVersion() + 1;

        VarOrder update = new VarOrder();
        update.setId(id);
        update.setVersion(staleVersion);
        update.setPartnerId(PARTNER_ID);
        update.setVarName("旧版本更新");
        update.setVarType("DESIGN_CHANGE");
        update.setDirection("COST");
        BusinessException updateError = assertThrows(BusinessException.class,
                () -> varOrderService.update(update));
        assertEquals("VAR_ORDER_VERSION_CONFLICT", updateError.getCode());

        VarOrderItem item = new VarOrderItem();
        item.setItemName("旧版本明细");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(BigDecimal.ONE);
        item.setCostSubjectId(90001L);
        BusinessException itemError = assertThrows(BusinessException.class,
                () -> varOrderService.saveItems(id, List.of(item), staleVersion));
        assertEquals("VAR_ORDER_VERSION_CONFLICT", itemError.getCode());

        BusinessException deleteError = assertThrows(BusinessException.class,
                () -> varOrderService.delete(id, staleVersion));
        assertEquals("VAR_ORDER_VERSION_CONFLICT", deleteError.getCode());
        assertNotNull(varOrderMapper.selectById(id));
    }

    @Test
    @Transactional
    @DisplayName("CAS: 旧版本提交审批失败关闭")
    void staleVersionRejectsApprovalSubmission() {
        VarOrder order = new VarOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setPartnerId(PARTNER_ID);
        order.setVarName("变更单测-提交CAS");
        order.setVarType("DESIGN_CHANGE");
        order.setDirection("COST");
        Long id = varOrderService.create(order);
        prepareSubmission(id);
        Integer currentVersion = varOrderMapper.selectById(id).getVersion();

        BusinessException error = assertThrows(BusinessException.class,
                () -> varOrderService.submitForApproval(id, currentVersion - 1));
        assertEquals("VAR_ORDER_CONCURRENT_SUBMIT", error.getCode());
    }

    @Test
    @Transactional
    @DisplayName("GREEN-4D: create plus saveItems can be queried by list and detail")
    void testCreatedDraftCanBeQueried() {
        VarOrder order = new VarOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setPartnerId(PARTNER_ID);
        order.setVarName("变更单测-可查询草稿");
        order.setVarType("现场签证");
        order.setDirection("COST");
        order.setEventDate(LocalDate.of(2026, 7, 15));
        Long id = varOrderService.create(order);

        VarOrderItem item = new VarOrderItem();
        item.setItemName("条目C");
        item.setUnit("项");
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("800.00"));
        item.setAmount(new BigDecimal("1600.00"));
        item.setCostSubjectId(90001L);
        varOrderService.saveItems(id, java.util.List.of(item));

        var detail = varOrderService.getById(id);
        assertEquals("变更单测-可查询草稿", detail.getVarName());
        assertEquals("DRAFT", detail.getApprovalStatus());
        assertNotNull(detail.getItems());
        assertEquals(1, detail.getItems().size());

        var page = varOrderService.getPage(1, 20, PROJECT_ID, CONTRACT_ID, PARTNER_ID, null, null, null,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        assertTrue(page.getRecords().stream().anyMatch(record -> id.toString().equals(record.getId())),
                "分页列表应能查到新建草稿");
    }

    @Test
    @Transactional
    @DisplayName("SAFE-0: 无项目权限时列表 fail-close")
    void testGetPageWithoutProjectAccessReturnsEmpty() {
        PmProject project = projectMapper.selectById(PROJECT_ID);
        project.setCreatedBy(77L);
        project.setProjectManagerId(null);
        projectMapper.updateById(project);

        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", 88L)
                .add("username", "var-reader")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of())
                .build());

        var page = varOrderService.getPage(1, 20, null, null, null, null, null, null, null, null);
        assertTrue(page.getRecords().isEmpty());
        assertEquals(0L, page.getTotal());
    }

    // ═══════════════════════════════════════════════════════════════
    // GREEN-5: update draft rejected when in approval
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("GREEN-5: 审批中的变更签证不可更新")
    void testUpdateRejectedWhenApproving() {
        VarOrder order = new VarOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setPartnerId(PARTNER_ID);
        order.setVarName("变更单测-不可编辑");
        order.setVarType("DESIGN_CHANGE");
        order.setDirection("COST");
        order.setReportedAmount(new BigDecimal("10000.00"));
        order.setApprovalStatus("DRAFT");
        Long id = varOrderService.create(order);
        assertNotNull(id);

        // Narrow the test to update protection and avoid coupling to workflow approver fixtures.
        VarOrder approving = new VarOrder();
        approving.setId(id);
        approving.setApprovalStatus("APPROVING");
        varOrderMapper.updateById(approving);

        // Verify status is no longer DRAFT
        VarOrder after = varOrderMapper.selectById(id);
        String approvalStatus = after.getApprovalStatus();
        assertNotEquals("DRAFT", approvalStatus, "提交后 status 应非 DRAFT");

        // Attempt update should throw
        VarOrder update = new VarOrder();
        update.setId(id);
        update.setVarName("尝试修改");

        com.cgcpms.common.exception.BusinessException ex =
                assertThrows(com.cgcpms.common.exception.BusinessException.class, () -> {
                    varOrderService.update(update);
                }, "审批中不可编辑");
        assertTrue(ex.getCode().contains("APPROVAL") || ex.getCode().contains("SUBMIT"),
                "错误码应提示审批相关");
    }

    // ═══════════════════════════════════════════════════════════════
    // SAFE: Null-safe batch VO mapping tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("SAFE-1: getPage with null partnerId should not NPE")
    void testGetPageWithNullPartnerId() {
        VarOrder order = new VarOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setPartnerId(null);
        order.setVarName("null-relation 单测");
        order.setVarType("DESIGN_CHANGE");
        order.setDirection("COST");
        order.setReportedAmount(new BigDecimal("10000.00"));
        order.setApprovalStatus("DRAFT");
        Long id = varOrderService.create(order);
        assertNotNull(id);

        com.baomidou.mybatisplus.core.metadata.IPage<com.cgcpms.variation.vo.VarOrderVO> page =
                varOrderService.getPage(1, 10, null, null, null, null, null, null, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() > 0, "至少应有一条记录");

        com.cgcpms.variation.vo.VarOrderVO vo = page.getRecords().get(0);
        assertEquals("null-relation 单测", vo.getVarName());
        assertNotNull(vo.getProjectName(), "projectName 应填充");
        assertEquals("变更单测专用项目", vo.getProjectName());
        assertNotNull(vo.getContractName(), "contractName 应填充");
        assertEquals("变更单测合同", vo.getContractName());
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
                varOrderService.getPage(1, 10, null, null, null, null, null, null, null, null);
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

    private void setAdminContext() {
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN"))
                .build());
    }
}
