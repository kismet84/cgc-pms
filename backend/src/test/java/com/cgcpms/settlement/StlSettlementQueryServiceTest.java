package com.cgcpms.settlement;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.settlement.constant.SettlementStatusConstants;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.settlement.service.StlSettlementQueryService;
import com.cgcpms.settlement.vo.SettlementAttachmentVO;
import com.cgcpms.settlement.vo.SettlementCostItemVO;
import com.cgcpms.settlement.vo.SettlementPaymentItemVO;
import com.cgcpms.settlement.vo.StlSettlementVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.variation.vo.VarOrderVO;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direct tests for StlSettlementQueryService read-only methods.
 * Supplements StlSettlementControllerMockMvcTest (integration) and
 * StlSettlementServiceTest (WriteService).
 *
 * <p>Demo data: contract 30001 (tenant_id=0, project_id=10001).
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
class StlSettlementQueryServiceTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ID = 1L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;
    private static final long COST_SUBJECT_ID = 910001L;

    @Autowired private StlSettlementQueryService queryService;
    @Autowired private StlSettlementMapper settlementMapper;
    @Autowired private VarOrderMapper varOrderMapper;
    @Autowired private CostItemMapper costItemMapper;
    @Autowired private CostSubjectMapper costSubjectMapper;
    @Autowired private SysFileMapper sysFileMapper;
    @Autowired private PayApplicationMapper payApplicationMapper;
    @Autowired private PayRecordMapper payRecordMapper;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long settlementId;

    @BeforeEach
    void setUp() {
        UserContext.set(Jwts.claims().subject("admin").add("userId", USER_ID)
                .add("username", "admin").add("tenantId", TENANT_ID)
                .add("roleCodes", java.util.List.of("ADMIN")).build());

        cleanupPayments();
        jdbcTemplate.update("DELETE FROM stl_settlement WHERE tenant_id = ? AND settlement_code = ?",
                TENANT_ID, "STL-20260520-001");

        StlSettlement s = new StlSettlement();
        s.setTenantId(TENANT_ID); s.setProjectId(PROJECT_ID); s.setContractId(CONTRACT_ID);
        s.setSettlementCode("STL-20260520-001"); s.setSettlementType("FINAL");
        s.setContractAmount(new BigDecimal("100000.00"));
        s.setChangeAmount(new BigDecimal("5000.00"));
        s.setMeasuredAmount(new BigDecimal("8000.00"));
        s.setDeductionAmount(new BigDecimal("1000.00"));
        s.setPaidAmount(new BigDecimal("20000.00"));
        s.setFinalAmount(new BigDecimal("112000.00"));
        s.setUnpaidAmount(new BigDecimal("86400.00"));
        s.setWarrantyAmount(new BigDecimal("5600.00"));
        s.setApprovalStatus("DRAFT");
        s.setApprovalStatus("DRAFT");
        s.setSettlementStatus(SettlementStatusConstants.SETTLEMENT_DRAFT);
        settlementMapper.insert(s);
        settlementId = s.getId();

        seedVariation();
        seedCost();
        seedAttachment();
        seedPayments();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM sys_file WHERE business_type = 'SETTLEMENT' AND business_id = ?", settlementId);
        jdbcTemplate.update("DELETE FROM cost_item WHERE contract_id = ? AND source_type = 'SETTLEMENT_QUERY_TEST_COST'", CONTRACT_ID);
        jdbcTemplate.update("DELETE FROM var_order WHERE contract_id = ? AND var_name = 'settlement-query-test-variation'", CONTRACT_ID);
        cleanupPayments();
        jdbcTemplate.update("DELETE FROM cost_subject WHERE id = ?", COST_SUBJECT_ID);
        jdbcTemplate.update("DELETE FROM stl_settlement WHERE tenant_id = ? AND settlement_code = ?",
                TENANT_ID, "STL-20260520-001");
        UserContext.clear();
    }

    private void cleanupPayments() {
        String ownApplications = "SELECT id FROM pay_application WHERE tenant_id = ? "
                + "AND apply_code LIKE 'PAY-SETTLEMENT-QUERY-%'";
        jdbcTemplate.update("DELETE FROM invoice_payment_allocation WHERE pay_record_id IN "
                + "(SELECT id FROM pay_record WHERE pay_application_id IN (" + ownApplications + "))", TENANT_ID);
        jdbcTemplate.update("DELETE FROM pay_record WHERE pay_application_id IN (" + ownApplications + ")", TENANT_ID);
        jdbcTemplate.update("DELETE FROM pay_application WHERE tenant_id = ? "
                + "AND apply_code LIKE 'PAY-SETTLEMENT-QUERY-%'", TENANT_ID);
    }

    // ================================================================
    // getPage
    // ================================================================

    @Test @DisplayName("getPage — 默认无过滤返回全部")
    void testGetPage_All() {
        IPage<StlSettlementVO> page = queryService.getPage(1, 10, null, null, null, null, null, null);
        assertTrue(page.getTotal() >= 1);
        assertFalse(page.getRecords().isEmpty());
    }

    @Test @DisplayName("getPage — 按项目过滤")
    void testGetPage_FilterByProject() {
        IPage<StlSettlementVO> page = queryService.getPage(1, 10, PROJECT_ID, null, null, null, null, null);
        assertTrue(page.getTotal() >= 1);
        for (StlSettlementVO vo : page.getRecords()) {
            assertEquals(String.valueOf(PROJECT_ID), vo.getProjectId());
        }
    }

    @Test @DisplayName("getPage — 按合同过滤")
    void testGetPage_FilterByContract() {
        IPage<StlSettlementVO> page = queryService.getPage(1, 10, null, CONTRACT_ID, null, null, null, null);
        assertTrue(page.getTotal() >= 1);
    }

    @Test @DisplayName("getPage — 按类型过滤并验证字段映射")
    void testGetPage_FilterByType() {
        IPage<StlSettlementVO> page = queryService.getPage(1, 10, null, null, null, null, "FINAL", null);
        assertTrue(page.getTotal() >= 1);
        for (StlSettlementVO vo : page.getRecords()) {
            assertEquals("FINAL", vo.getSettlementType());
            assertNotNull(vo.getSettlementCode());
            assertNotNull(vo.getProjectName(), "projectName 应由 Assembler 解析");
            assertNotNull(vo.getContractName(), "contractName 应由 Assembler 解析");
        }
    }

    @Test @DisplayName("getPage — 按结算编号模糊搜索")
    void testGetPage_SearchByCode() {
        IPage<StlSettlementVO> page = queryService.getPage(1, 10, null, null, null, "STL-20260520-001", null, null);
        assertEquals(1, page.getTotal());
        assertEquals("STL-20260520-001", page.getRecords().get(0).getSettlementCode());
    }

    @Test @DisplayName("getPage — 按关键字搜索无结果")
    void testGetPage_SearchByKeyword_NoMatch() {
        IPage<StlSettlementVO> page = queryService.getPage(1, 10, null, null, null, null, null, "nonexistent-xyz");
        assertEquals(0, page.getTotal());
    }

    // ================================================================
    // getKpi
    // ================================================================

    @Test @DisplayName("getKpi — 无过滤返回汇总")
    void testGetKpi_All() {
        Map<String, Object> kpi = queryService.getKpi(null, null, null, null, null);
        assertNotNull(kpi);
        assertTrue((long) kpi.get("totalCount") >= 1L);
        assertNotNull(kpi.get("totalContractAmount"));
        assertNotNull(kpi.get("totalFinalAmount"));
        assertTrue(kpi.get("draftCount") instanceof Long);
    }

    @Test @DisplayName("getKpi — 按项目过滤")
    void testGetKpi_FilterByProject() {
        Map<String, Object> kpi = queryService.getKpi(PROJECT_ID, null, null, null, null);
        assertTrue((long) kpi.get("totalCount") >= 1L);
    }

    @Test @DisplayName("getKpi — 无匹配数据全为零")
    void testGetKpi_NoData() {
        Map<String, Object> kpi = queryService.getKpi(99999L, null, null, null, null);
        assertEquals(0L, (long) kpi.get("totalCount"));
        assertEquals("0", kpi.get("totalContractAmount"));
    }

    // ================================================================
    // getById
    // ================================================================

    @Test @DisplayName("getById — 存在时返回完整 VO")
    void testGetById_Found() {
        StlSettlementVO vo = queryService.getById(settlementId);
        assertNotNull(vo);
        assertEquals("STL-20260520-001", vo.getSettlementCode());
        assertNotNull(vo.getItems(), "items 不应为 null");
        assertNotNull(vo.getProjectName());
        assertNotNull(vo.getContractName());
    }

    @Test @DisplayName("getById — 不存在抛异常")
    void testGetById_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> queryService.getById(99999999L));
        assertEquals("STL_SETTLEMENT_NOT_FOUND", ex.getCode());
    }

    @Test @DisplayName("getById — 租户不匹配")
    void testGetById_WrongTenant() {
        UserContext.set(Jwts.claims().subject("admin").add("userId", USER_ID)
                .add("username", "admin").add("tenantId", 999L)
                .add("roleCodes", java.util.List.of("ADMIN")).build());
        try {
            assertThrows(BusinessException.class, () -> queryService.getById(settlementId));
        } finally {
            UserContext.set(Jwts.claims().subject("admin").add("userId", USER_ID)
                    .add("username", "admin").add("tenantId", TENANT_ID)
                    .add("roleCodes", java.util.List.of("ADMIN")).build());
        }
    }

    // ================================================================
    // computeSettlementAmount
    // ================================================================

    @Test @DisplayName("computeSettlementAmount — 有效合同返回计算结果")
    void testComputeSettlementAmount_Valid() {
        StlSettlementVO vo = queryService.computeSettlementAmount(CONTRACT_ID);
        assertNotNull(vo);
        assertNotNull(vo.getContractAmount());
        assertNotNull(vo.getFinalAmount());
        assertNotNull(vo.getWarrantyAmount());
        assertNotNull(vo.getUnpaidAmount());
    }

    @Test @DisplayName("computeSettlementAmount — 无效合同抛异常")
    void testComputeSettlementAmount_NotFound() {
        assertThrows(BusinessException.class,
                () -> queryService.computeSettlementAmount(99999999L));
    }

    // ================================================================
    // getSources
    // ================================================================

    @Test @DisplayName("getSources — 返回结构化来源数据")
    void testGetSources() {
        var sources = queryService.getSources(settlementId);
        assertNotNull(sources);
        assertNotNull(sources.getVarOrders());
        assertNotNull(sources.getSubMeasures());
        assertNotNull(sources.getPayRecords());
    }

    @Test @DisplayName("getSources — 不存在抛异常")
    void testGetSources_NotFound() {
        assertThrows(BusinessException.class, () -> queryService.getSources(99999999L));
    }

    // ================================================================
    // Related queries — happy path
    // ================================================================

    @Test @DisplayName("getVariations — 返回空列表")
    void testGetVariations() {
        var variations = queryService.getVariations(settlementId);
        assertEquals(1, variations.size());
        VarOrderVO variation = variations.get(0);
        assertEquals("settlement-query-test-variation", variation.getVarName());
        assertEquals("VO-SETTLEMENT-QUERY-001", variation.getVarCode());
    }

    @Test @DisplayName("getPayments — 返回结算页付款 VO 并归一化状态")
    void testGetPayments() throws Exception {
        List<SettlementPaymentItemVO> payments = queryService.getPayments(settlementId);
        List<SettlementPaymentItemVO> ownPayments = payments.stream()
                .filter(item -> item.getApplyCode() != null
                        && item.getApplyCode().startsWith("PAY-SETTLEMENT-QUERY-"))
                .toList();
        assertEquals(3, ownPayments.size());

        SettlementPaymentItemVO paid = ownPayments.stream()
                .filter(item -> "PAY-SETTLEMENT-QUERY-PAID".equals(item.getApplyCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("PAID", paid.getPayStatus());
        assertEquals("1000.00", paid.getActualPayAmount());
        assertEquals("1200.00", paid.getApplyAmount());
        assertEquals("1100.00", paid.getApprovedAmount());
        assertEquals("2026-07-02", paid.getPayDate());
        assertEquals("VCH-SETTLEMENT-QUERY-001", paid.getVoucherNo());
        assertNotNull(paid.getCreatedAt());

        SettlementPaymentItemVO partial = ownPayments.stream()
                .filter(item -> "PAY-SETTLEMENT-QUERY-PARTIAL".equals(item.getApplyCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("PARTIAL", partial.getPayStatus());

        SettlementPaymentItemVO unpaid = ownPayments.stream()
                .filter(item -> "PAY-SETTLEMENT-QUERY-UNPAID".equals(item.getApplyCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("UNPAID", unpaid.getPayStatus());

        JsonNode json = objectMapper.valueToTree(paid);
        assertFalse(json.has("tenantId"));
        assertFalse(json.has("deletedFlag"));
        assertFalse(json.has("externalTxnNo"));
        assertFalse(json.has("payMethod"));
        assertFalse(json.has("projectId"));
        assertFalse(json.has("contractId"));
        assertFalse(json.has("partnerId"));
        assertFalse(json.has("remark"));
    }

    @Test @DisplayName("getCosts — 返回脱敏后的成本项")
    void testGetCosts() {
        var costs = queryService.getCosts(settlementId);
        assertEquals(1, costs.size());
        SettlementCostItemVO cost = costs.get(0);
        assertEquals(String.valueOf(COST_SUBJECT_ID), cost.getCostSubjectId());
        assertEquals("单测成本科目", cost.getCostSubjectName());
        assertEquals("1234.56", cost.getAmount());
        assertEquals("2026-07-02", cost.getCostDate());
    }

    @Test @DisplayName("getAttachments — 返回脱敏后的附件")
    void testGetAttachments() {
        SysFile generated = new SysFile();
        generated.setTenantId(TENANT_ID);
        generated.setBusinessType("SETTLEMENT");
        generated.setBusinessId(settlementId);
        generated.setFileName("generated-settlement.pdf");
        generated.setOriginalName("generated-settlement.pdf");
        generated.setFileSize(1024L);
        generated.setContentType("application/pdf");
        generated.setDocumentType("GENERATED_DOCUMENT");
        generated.setStoragePath("SETTLEMENT/" + settlementId + "/generated-settlement.pdf");
        generated.setBucketName("test-bucket");
        generated.setCreatedBy(USER_ID);
        sysFileMapper.insert(generated);

        var attachments = queryService.getAttachments(settlementId);
        assertEquals(1, attachments.size());
        SettlementAttachmentVO attachment = attachments.get(0);
        assertEquals("settlement-query-test.pdf", attachment.getOriginalName());
        assertEquals("application/pdf", attachment.getFileType());
        assertEquals(String.valueOf(USER_ID), attachment.getUploadedBy());
        assertNotNull(attachment.getUploadedAt());
    }

    @Test @DisplayName("getApprovalRecords — 返回空列表")
    void testGetApprovalRecords() { assertNotNull(queryService.getApprovalRecords(settlementId)); }

    // ================================================================
    // Related queries — not-found guards
    // ================================================================

    @Test @DisplayName("getVariations — 不存在抛异常")
    void testGetVariations_NotFound() {
        assertThrows(BusinessException.class, () -> queryService.getVariations(99999999L));
    }

    @Test @DisplayName("getPayments — 不存在抛异常")
    void testGetPayments_NotFound() {
        assertThrows(BusinessException.class, () -> queryService.getPayments(99999999L));
    }

    @Test @DisplayName("getCosts — 不存在抛异常")
    void testGetCosts_NotFound() {
        assertThrows(BusinessException.class, () -> queryService.getCosts(99999999L));
    }

    @Test @DisplayName("getAttachments — 不存在抛异常")
    void testGetAttachments_NotFound() {
        assertThrows(BusinessException.class, () -> queryService.getAttachments(99999999L));
    }

    @Test @DisplayName("getApprovalRecords — 不存在抛异常")
    void testGetApprovalRecords_NotFound() {
        assertThrows(BusinessException.class, () -> queryService.getApprovalRecords(99999999L));
    }

    private void seedVariation() {
        VarOrder order = new VarOrder();
        order.setTenantId(TENANT_ID);
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setVarCode("VO-SETTLEMENT-QUERY-001");
        order.setVarName("settlement-query-test-variation");
        order.setVarType("DESIGN_CHANGE");
        order.setDirection("COST");
        order.setReportedAmount(new BigDecimal("1000.00"));
        order.setApprovedAmount(new BigDecimal("900.00"));
        order.setConfirmedAmount(new BigDecimal("800.00"));
        order.setOwnerConfirmFlag(1);
        order.setImpactDays(3);
        order.setApprovalStatus("APPROVED");
        order.setCostGeneratedFlag(0);
        order.setCreatedBy(USER_ID);
        varOrderMapper.insert(order);
    }

    private void seedCost() {
        if (costSubjectMapper.selectById(COST_SUBJECT_ID) == null) {
            CostSubject subject = new CostSubject();
            subject.setId(COST_SUBJECT_ID);
            subject.setTenantId(TENANT_ID);
            subject.setParentId(0L);
            subject.setSubjectCode("CS-SETTLEMENT-QUERY-001");
            subject.setSubjectName("单测成本科目");
            subject.setSubjectType("DETAIL");
            subject.setAccountCategory("COST");
            subject.setLevel(1);
            subject.setSortOrder(1);
            subject.setStatus("ENABLE");
            costSubjectMapper.insert(subject);
        }

        CostItem item = new CostItem();
        item.setTenantId(TENANT_ID);
        item.setProjectId(PROJECT_ID);
        item.setContractId(CONTRACT_ID);
        item.setPartnerId(20001L);
        item.setCostSubjectId(COST_SUBJECT_ID);
        item.setCostType("MATERIAL");
        item.setAmount(new BigDecimal("1234.56"));
        item.setTaxAmount(new BigDecimal("34.56"));
        item.setAmountWithoutTax(new BigDecimal("1200.00"));
        item.setSourceType("SETTLEMENT_QUERY_TEST_COST");
        item.setSourceId(settlementId);
        item.setSourceItemId(880001L);
        item.setCostDate(LocalDate.of(2026, 7, 2));
        item.setCostStatus("CONFIRMED");
        item.setGeneratedFlag(1);
        item.setCreatedBy(USER_ID);
        costItemMapper.insert(item);
    }

    private void seedAttachment() {
        SysFile file = new SysFile();
        file.setTenantId(TENANT_ID);
        file.setBusinessType("SETTLEMENT");
        file.setBusinessId(settlementId);
        file.setFileName("settlement-query-test.pdf");
        file.setOriginalName("settlement-query-test.pdf");
        file.setFileSize(2048L);
        file.setContentType("application/pdf");
        file.setStoragePath("SETTLEMENT/" + settlementId + "/settlement-query-test.pdf");
        file.setBucketName("test-bucket");
        file.setCreatedBy(USER_ID);
        sysFileMapper.insert(file);
    }

    private void seedPayments() {
        PayApplication paidApplication = insertPayApplication("PAY-SETTLEMENT-QUERY-PAID", "BANK", "1200.00", "1100.00", "PAID");
        insertPayRecord(paidApplication.getId(), "1000.00", LocalDate.of(2026, 7, 2), "VCH-SETTLEMENT-QUERY-001", "SUCCESS");

        PayApplication partialApplication = insertPayApplication("PAY-SETTLEMENT-QUERY-PARTIAL", "CASH", "900.00", "800.00", "PARTIALLY_PAID");
        insertPayRecord(partialApplication.getId(), "500.00", LocalDate.of(2026, 7, 3), "VCH-SETTLEMENT-QUERY-002", "SUCCESS");

        PayApplication unpaidApplication = insertPayApplication("PAY-SETTLEMENT-QUERY-UNPAID", "TRANSFER", "700.00", "650.00", "APPROVED");
        insertPayRecord(unpaidApplication.getId(), "0.00", LocalDate.of(2026, 7, 4), "VCH-SETTLEMENT-QUERY-003", "PENDING");
    }

    private PayApplication insertPayApplication(String applyCode, String payType, String applyAmount,
                                                String approvedAmount, String payStatus) {
        PayApplication application = new PayApplication();
        application.setTenantId(TENANT_ID);
        application.setProjectId(PROJECT_ID);
        application.setContractId(CONTRACT_ID);
        application.setPartnerId(20001L);
        application.setApplyCode(applyCode);
        application.setApplyAmount(new BigDecimal(applyAmount));
        application.setApprovedAmount(new BigDecimal(approvedAmount));
        application.setActualPayAmount(BigDecimal.ZERO);
        application.setPayType(payType);
        application.setPayStatus(payStatus);
        application.setApprovalStatus("APPROVED");
        application.setApplyReason("settlement query test");
        payApplicationMapper.insert(application);
        return application;
    }

    private void insertPayRecord(Long payApplicationId, String payAmount, LocalDate payDate,
                                 String voucherNo, String payStatus) {
        PayRecord record = new PayRecord();
        record.setTenantId(TENANT_ID);
        record.setProjectId(PROJECT_ID);
        record.setPayApplicationId(payApplicationId);
        record.setContractId(CONTRACT_ID);
        record.setPartnerId(20001L);
        record.setPayAmount(new BigDecimal(payAmount));
        record.setPayDate(payDate);
        record.setPayMethod("BANK_TRANSFER");
        record.setVoucherNo(voucherNo);
        record.setPayStatus(payStatus);
        record.setExternalTxnNo("EXT-" + voucherNo);
        record.setRemark("should not leak");
        payRecordMapper.insert(record);
    }
}
