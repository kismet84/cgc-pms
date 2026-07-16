package com.cgcpms.settlement;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
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
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.settlement.service.SettlementAmountPolicy;
import com.cgcpms.settlement.service.StlSettlementWriteService;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * VUL-019 settlement desensitization tests plus submit workflow baseline.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("StlSettlementController — VUL-019 desensitization")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StlSettlementControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StlSettlementWriteService stlSettlementWriteService;
    @Autowired
    private StlSettlementMapper settlementMapper;
    @Autowired
    private VarOrderMapper varOrderMapper;
    @Autowired
    private CostItemMapper costItemMapper;
    @Autowired
    private CostSubjectMapper costSubjectMapper;
    @Autowired
    private SysFileMapper sysFileMapper;
    @Autowired
    private PayApplicationMapper payApplicationMapper;
    @Autowired
    private PayRecordMapper payRecordMapper;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30003L;
    private static final long COST_SUBJECT_ID = 910002L;

    private Long settlementId;

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                ADMIN_ID, ADMIN_USERNAME, TENANT_ID,
                List.of("ADMIN"),
                List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    private void setUserContext() {
        Claims claims = Jwts.claims()
                .subject("admin")
                .add("userId", ADMIN_ID)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN"))
                .build();
        UserContext.set(claims);
    }

    private void clearUserContext() {
        UserContext.clear();
    }

    @BeforeAll
    void initSettlement() {
        seedAdminUser();
        setUserContext();
        try {
            StlSettlement settlement = new StlSettlement();
            settlement.setProjectId(PROJECT_ID);
            settlement.setContractId(CONTRACT_ID);
            settlement.setSettlementType("FINAL");
            settlementId = stlSettlementWriteService.create(settlement);
            seedVariation();
            seedCost();
            seedAttachment();
            seedPayments();
        } finally {
            clearUserContext();
        }
    }

    @AfterAll
    void cleanupSettlement() {
        setUserContext();
        try {
            sysFileMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysFile>()
                    .eq(SysFile::getBusinessType, "SETTLEMENT")
                    .eq(SysFile::getBusinessId, settlementId)
                    .eq(SysFile::getOriginalName, "settlement-controller-test.pdf"));
            costItemMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CostItem>()
                    .eq(CostItem::getContractId, CONTRACT_ID)
                    .eq(CostItem::getSourceType, "SETTLEMENT_CONTROLLER_TEST_COST"));
            varOrderMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VarOrder>()
                    .eq(VarOrder::getContractId, CONTRACT_ID)
                    .eq(VarOrder::getVarName, "settlement-controller-test-variation"));
            payRecordMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayRecord>()
                    .eq(PayRecord::getContractId, CONTRACT_ID)
                    .eq(PayRecord::getTenantId, TENANT_ID));
            payApplicationMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayApplication>()
                    .eq(PayApplication::getContractId, CONTRACT_ID)
                    .eq(PayApplication::getTenantId, TENANT_ID));
            costSubjectMapper.deleteById(COST_SUBJECT_ID);
            settlementMapper.deleteById(settlementId);
        } finally {
            clearUserContext();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. GET /settlements/{id}/variations
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("GET /settlements/{id}/variations -> 200")
    void testGetVariations() throws Exception {
        mockMvc.perform(getWithApi("/settlements/" + settlementId + "/variations")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].varCode").value("VO-SETTLEMENT-CONTROLLER-001"))
                .andExpect(jsonPath("$.data[0].varName").value("settlement-controller-test-variation"))
                .andExpect(jsonPath("$.data[0].deletedFlag").doesNotExist());
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. GET /settlements/{id}/costs
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("GET /settlements/{id}/costs -> 200")
    void testGetCosts() throws Exception {
        mockMvc.perform(getWithApi("/settlements/" + settlementId + "/costs")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].costSubjectId").value(String.valueOf(COST_SUBJECT_ID)))
                .andExpect(jsonPath("$.data[0].costSubjectName").value("控制器单测成本科目"))
                .andExpect(jsonPath("$.data[0].amount").value("888.00"))
                .andExpect(jsonPath("$.data[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.data[0].generatedFlag").doesNotExist());
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. GET /settlements/{id}/attachments
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("GET /settlements/{id}/attachments -> 200")
    void testGetAttachments() throws Exception {
        mockMvc.perform(getWithApi("/settlements/" + settlementId + "/attachments")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].originalName").value("settlement-controller-test.pdf"))
                .andExpect(jsonPath("$.data[0].fileType").value("application/pdf"))
                .andExpect(jsonPath("$.data[0].uploadedBy").value(String.valueOf(ADMIN_ID)))
                .andExpect(jsonPath("$.data[0].storagePath").doesNotExist())
                .andExpect(jsonPath("$.data[0].bucketName").doesNotExist());
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. GET /settlements/{id}/payments
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("GET /settlements/{id}/payments -> 200 returns settlement payment items only")
    void testGetPayments() throws Exception {
        mockMvc.perform(getWithApi("/settlements/" + settlementId + "/payments")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].applicationId").exists())
                .andExpect(jsonPath("$.data[0].applyCode").exists())
                .andExpect(jsonPath("$.data[0].payType").exists())
                .andExpect(jsonPath("$.data[0].applyAmount").exists())
                .andExpect(jsonPath("$.data[0].approvedAmount").exists())
                .andExpect(jsonPath("$.data[0].actualPayAmount").exists())
                .andExpect(jsonPath("$.data[0].payStatus").exists())
                .andExpect(jsonPath("$.data[0].payDate").exists())
                .andExpect(jsonPath("$.data[0].voucherNo").value("VCH-SETTLEMENT-CONTROLLER-001"))
                .andExpect(jsonPath("$.data[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.data[0].deletedFlag").doesNotExist())
                .andExpect(jsonPath("$.data[0].externalTxnNo").doesNotExist())
                .andExpect(jsonPath("$.data[0].payMethod").doesNotExist())
                .andExpect(jsonPath("$.data[0].projectId").doesNotExist())
                .andExpect(jsonPath("$.data[0].contractId").doesNotExist())
                .andExpect(jsonPath("$.data[0].partnerId").doesNotExist())
                .andExpect(jsonPath("$.data[0].remark").doesNotExist());
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. GET /settlements/amount-baseline
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("GET /settlements/amount-baseline -> 200 previews historical drift without mutation")
    void testPreviewAmountBaseline() throws Exception {
        mockMvc.perform(getWithApi("/settlements/amount-baseline")
                        .cookie(adminCookie())
                        .param("pageNo", "1")
                        .param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[?(@.settlementId == '" + settlementId + "')]").exists())
                .andExpect(jsonPath("$.data.records[?(@.settlementId == '" + settlementId + "')].targetFormulaVersion")
                        .value(org.hamcrest.Matchers.hasItem(SettlementAmountPolicy.FORMULA_VERSION)));
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. POST /settlements/{id}/submit
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("POST /settlements/{id}/submit -> 200 submits for approval")
    void testSubmitForApproval() throws Exception {
        mockMvc.perform(postWithApi("/settlements/" + settlementId + "/submit")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ---- helpers ----

    private MockHttpServletRequestBuilder getWithApi(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder postWithApi(String pathWithinContext) {
        return post("/api" + pathWithinContext).contextPath("/api");
    }

    private void seedAdminUser() {
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 1, 0, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', '13800000000', 'admin@cgc-pms.com', 'ENABLE', 1, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 1)");
    }

    private void seedVariation() {
        VarOrder order = new VarOrder();
        order.setTenantId(TENANT_ID);
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setPartnerId(20001L);
        order.setVarCode("VO-SETTLEMENT-CONTROLLER-001");
        order.setVarName("settlement-controller-test-variation");
        order.setVarType("DESIGN_CHANGE");
        order.setDirection("COST");
        order.setReportedAmount(new BigDecimal("1000.00"));
        order.setApprovedAmount(new BigDecimal("900.00"));
        order.setConfirmedAmount(new BigDecimal("800.00"));
        order.setOwnerConfirmFlag(1);
        order.setImpactDays(2);
        order.setApprovalStatus("APPROVED");
        order.setCostGeneratedFlag(0);
        order.setCreatedBy(ADMIN_ID);
        varOrderMapper.insert(order);
    }

    private void seedCost() {
        if (costSubjectMapper.selectById(COST_SUBJECT_ID) == null) {
            CostSubject subject = new CostSubject();
            subject.setId(COST_SUBJECT_ID);
            subject.setTenantId(TENANT_ID);
            subject.setParentId(0L);
            subject.setSubjectCode("CS-SETTLEMENT-CONTROLLER-001");
            subject.setSubjectName("控制器单测成本科目");
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
        item.setCostType("LABOR");
        item.setAmount(new BigDecimal("888.00"));
        item.setTaxAmount(new BigDecimal("8.00"));
        item.setAmountWithoutTax(new BigDecimal("880.00"));
        item.setSourceType("SETTLEMENT_CONTROLLER_TEST_COST");
        item.setSourceId(settlementId);
        item.setSourceItemId(880002L);
        item.setCostDate(LocalDate.of(2026, 7, 2));
        item.setCostStatus("CONFIRMED");
        item.setGeneratedFlag(1);
        item.setCreatedBy(ADMIN_ID);
        costItemMapper.insert(item);
    }

    private void seedAttachment() {
        SysFile file = new SysFile();
        file.setTenantId(TENANT_ID);
        file.setBusinessType("SETTLEMENT");
        file.setBusinessId(settlementId);
        file.setFileName("settlement-controller-test.pdf");
        file.setOriginalName("settlement-controller-test.pdf");
        file.setFileSize(4096L);
        file.setContentType("application/pdf");
        file.setStoragePath("SETTLEMENT/" + settlementId + "/settlement-controller-test.pdf");
        file.setBucketName("test-bucket");
        file.setCreatedBy(ADMIN_ID);
        sysFileMapper.insert(file);
    }

    private void seedPayments() {
        PayApplication application = new PayApplication();
        application.setTenantId(TENANT_ID);
        application.setProjectId(PROJECT_ID);
        application.setContractId(CONTRACT_ID);
        application.setPartnerId(20001L);
        application.setApplyCode("PAY-SETTLEMENT-CONTROLLER-001");
        application.setApplyAmount(new BigDecimal("1500.00"));
        application.setApprovedAmount(new BigDecimal("1400.00"));
        application.setActualPayAmount(new BigDecimal("1000.00"));
        application.setPayType("BANK");
        application.setPayStatus("PAID");
        application.setApprovalStatus("APPROVED");
        application.setApplyReason("controller test");
        payApplicationMapper.insert(application);

        PayRecord record = new PayRecord();
        record.setTenantId(TENANT_ID);
        record.setProjectId(PROJECT_ID);
        record.setPayApplicationId(application.getId());
        record.setContractId(CONTRACT_ID);
        record.setPartnerId(20001L);
        record.setPayAmount(new BigDecimal("1000.00"));
        record.setPayDate(LocalDate.of(2026, 7, 2));
        record.setPayMethod("BANK_TRANSFER");
        record.setVoucherNo("VCH-SETTLEMENT-CONTROLLER-001");
        record.setPayStatus("SUCCESS");
        record.setExternalTxnNo("EXT-VCH-SETTLEMENT-CONTROLLER-001");
        record.setRemark("should not leak");
        payRecordMapper.insert(record);
    }
}
