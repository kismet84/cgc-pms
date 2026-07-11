package com.cgcpms.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.payment.service.PayRecordService;
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

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PayRecordController integration tests for list, getById, and writeback.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("PayRecordController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PayRecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PayApplicationService payApplicationService;

    @Autowired
    private PayApplicationMapper payApplicationMapper;

    @Autowired
    private PayRecordService payRecordService;

    @Autowired
    private PayRecordMapper payRecordMapper;

    @Autowired
    private CashJournalEntryMapper cashJournalEntryMapper;

    @Autowired
    private CostSummaryService costSummaryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;
    private static final long PARTNER_ID = 20002L;

    private Long payApplicationId;
    private Long payRecordId;

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
    void initData() {
        setUserContext();
        try {
            // Create a pay application first (prerequisite for pay_record)
            PayApplication app = new PayApplication();
            app.setProjectId(PROJECT_ID);
            app.setContractId(CONTRACT_ID);
            app.setPartnerId(PARTNER_ID);
            app.setApplyAmount(new BigDecimal("10000.00"));
            app.setPayType("MATERIAL");
            app.setApplyReason("集成测试-付款申请");
            payApplicationId = payApplicationService.create(app);
            PayApplication approvedApp = payApplicationMapper.selectById(payApplicationId);
            approvedApp.setApprovalStatus("APPROVED");
            approvedApp.setPayStatus("APPROVED");
            approvedApp.setApprovedAmount(approvedApp.getApplyAmount());
            payApplicationMapper.updateById(approvedApp);

            // Create a pay record via writeback (prerequisite for getById)
            PayRecord record = new PayRecord();
            record.setPayApplicationId(payApplicationId);
            record.setPayAmount(new BigDecimal("5000.00"));
            record.setPayDate(LocalDate.now());
            record.setPayMethod("BANK_TRANSFER");
            record.setExternalTxnNo("INIT-" + System.nanoTime());
            var vo = payRecordService.writeback(record);
            payRecordId = Long.parseLong(vo.getId());
        } finally {
            clearUserContext();
        }
    }

    @AfterAll
    void cleanupData() {
        try {
            setUserContext();
            Assertions.assertAll("cleanup PayRecordControllerTest data",
                    this::deleteDerivedCashJournalEntries,
                    () -> payRecordMapper.delete(new LambdaQueryWrapper<PayRecord>()
                            .eq(PayRecord::getPayApplicationId, payApplicationId)),
                    () -> payApplicationMapper.deleteById(payApplicationId),
                    this::recalculateContractPaidAmount,
                    () -> costSummaryService.updatePaidAmount(PROJECT_ID),
                    this::assertCleanupComplete);
        } finally {
            clearUserContext();
        }
    }

    private void deleteDerivedCashJournalEntries() {
        List<Long> payRecordIds = payRecordMapper.selectList(new LambdaQueryWrapper<PayRecord>()
                        .select(PayRecord::getId)
                        .eq(PayRecord::getPayApplicationId, payApplicationId))
                .stream()
                .map(PayRecord::getId)
                .toList();
        if (!payRecordIds.isEmpty()) {
            cashJournalEntryMapper.delete(new LambdaQueryWrapper<CashJournalEntry>()
                    .eq(CashJournalEntry::getTenantId, TENANT_ID)
                    .eq(CashJournalEntry::getSourceType, CashbookConstants.SourceType.PAY_RECORD)
                    .in(CashJournalEntry::getSourceId, payRecordIds));
        }
    }

    private void recalculateContractPaidAmount() {
        jdbcTemplate.update("""
                UPDATE ct_contract
                SET paid_amount = (
                    SELECT COALESCE(SUM(pay_amount), 0)
                    FROM pay_record
                    WHERE tenant_id = ? AND contract_id = ?
                      AND pay_status = 'SUCCESS' AND deleted_flag = 0
                )
                WHERE tenant_id = ? AND id = ? AND deleted_flag = 0
                """, TENANT_ID, CONTRACT_ID, TENANT_ID, CONTRACT_ID);
    }

    private void assertCleanupComplete() {
        BigDecimal contractPaid = amount("SELECT paid_amount FROM ct_contract WHERE id = ?", CONTRACT_ID);
        BigDecimal expectedContractPaid = amount("""
                SELECT COALESCE(SUM(pay_amount), 0) FROM pay_record
                WHERE tenant_id = ? AND contract_id = ? AND pay_status = 'SUCCESS' AND deleted_flag = 0
                """, TENANT_ID, CONTRACT_ID);
        BigDecimal expectedProjectPaid = amount("""
                SELECT COALESCE(SUM(pay_amount), 0) FROM pay_record
                WHERE tenant_id = ? AND project_id = ? AND pay_status = 'SUCCESS' AND deleted_flag = 0
                """, TENANT_ID, PROJECT_ID);

        Assertions.assertAll("verify PayRecordControllerTest cleanup",
                () -> assertEquals(0, count("""
                        SELECT COUNT(*) FROM pay_application
                        WHERE id = ? AND deleted_flag = 0
                        """, payApplicationId)),
                () -> assertEquals(0, count("""
                        SELECT COUNT(*) FROM pay_record
                        WHERE pay_application_id = ? AND deleted_flag = 0
                        """, payApplicationId)),
                () -> assertEquals(0, count("""
                        SELECT COUNT(*) FROM cash_journal_entry cj
                        JOIN pay_record pr ON pr.id = cj.source_id
                        WHERE pr.pay_application_id = ? AND cj.source_type = 'PAY_RECORD'
                          AND cj.deleted_flag = 0
                        """, payApplicationId)),
                () -> assertEquals(0, expectedContractPaid.compareTo(contractPaid)),
                () -> assertEquals(0, count("""
                        SELECT COUNT(*) FROM cost_summary
                        WHERE tenant_id = ? AND project_id = ? AND deleted_flag = 0
                          AND (paid_amount IS NULL OR paid_amount <> ?)
                        """, TENANT_ID, PROJECT_ID, expectedProjectPaid)));
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private BigDecimal amount(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
    }

    // ═══════════════════════════════════════════════════════════════
    // Unauthorized checks
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("GET /pay-records without JWT -> 401")
    void testList_Unauthorized() throws Exception {
        mockMvc.perform(getWithApi("/pay-records"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1)
    @DisplayName("GET /pay-records/{id} without JWT -> 401")
    void testGetById_Unauthorized() throws Exception {
        mockMvc.perform(getWithApi("/pay-records/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1)
    @DisplayName("POST /pay-records/writeback without JWT -> 401")
    void testWriteback_Unauthorized() throws Exception {
        mockMvc.perform(postWithApi("/pay-records/writeback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payApplicationId\":1,\"payAmount\":1000}"))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════════════
    // GET list
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("GET /pay-records -> 200 with paginated data")
    void testList() throws Exception {
        mockMvc.perform(getWithApi("/pay-records")
                        .cookie(adminCookie())
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").isString());
    }

    // ═══════════════════════════════════════════════════════════════
    // GET by id
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("GET /pay-records/{id} -> 200 with record data")
    void testGetById() throws Exception {
        Assertions.assertNotNull(payRecordId, "Prerequisite: payRecordId must be created");

        mockMvc.perform(getWithApi("/pay-records/" + payRecordId)
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.payAmount").exists())
                .andExpect(jsonPath("$.data.payStatus").value("SUCCESS"));
    }

    @Test
    @Order(4)
    @DisplayName("GET /pay-records/{id} for non-existent -> 400")
    void testGetById_NotFound() throws Exception {
        mockMvc.perform(getWithApi("/pay-records/999999")
                        .cookie(adminCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAY_RECORD_NOT_FOUND"));
    }

    // ═══════════════════════════════════════════════════════════════
    // POST writeback
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("POST /pay-records/writeback -> 200 creates pay record")
    void testWriteback() throws Exception {
        Assertions.assertNotNull(payApplicationId, "Prerequisite: payApplicationId must be created");

        String body = """
                {
                    "payApplicationId": %d,
                    "payAmount": 2000.00,
                    "payDate": "2026-06-22",
                    "payMethod": "CASH",
                    "externalTxnNo": "TXN-%d"
                }
                """.formatted(payApplicationId, System.nanoTime());

        mockMvc.perform(postWithApi("/pay-records/writeback")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.payStatus").value("SUCCESS"));
    }

    @Test
    @Order(6)
    @DisplayName("POST /pay-records/writeback with missing payApplicationId -> 400")
    void testWriteback_MissingAppId() throws Exception {
        mockMvc.perform(postWithApi("/pay-records/writeback")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payAmount\":1000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    @DisplayName("POST /pay-records/writeback with 3-decimal amount -> 400")
    void testWriteback_RejectsThreeDecimalAmount() throws Exception {
        String body = """
                {
                    "payApplicationId": %d,
                    "payAmount": 1.001,
                    "payDate": "2026-06-22",
                    "externalTxnNo": "TXN-SCALE-%d"
                }
                """.formatted(payApplicationId, System.nanoTime());

        mockMvc.perform(postWithApi("/pay-records/writeback")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    @DisplayName("POST /pay-records/writeback without payDate -> 400")
    void testWriteback_RejectsMissingPayDate() throws Exception {
        String body = """
                {
                    "payApplicationId": %d,
                    "payAmount": 1.00,
                    "externalTxnNo": "TXN-NO-DATE-%d"
                }
                """.formatted(payApplicationId, System.nanoTime());

        mockMvc.perform(postWithApi("/pay-records/writeback")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(9)
    @DisplayName("writeback 带审计注解")
    void testWritebackAuditedOperationPresent() throws Exception {
        Method method = com.cgcpms.payment.controller.PayRecordController.class
                .getMethod("writeback", PayRecord.class);
        AuditedOperation audited = method.getAnnotation(AuditedOperation.class);
        assertNotNull(audited);
        assertEquals("CREATE", audited.type());
        assertEquals("PAYMENT", audited.businessType());
        assertEquals("#input.payApplicationId", audited.businessIdExpression());
    }

    // ── helpers ──

    private MockHttpServletRequestBuilder getWithApi(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder postWithApi(String pathWithinContext) {
        return post("/api" + pathWithinContext).contextPath("/api");
    }
}
