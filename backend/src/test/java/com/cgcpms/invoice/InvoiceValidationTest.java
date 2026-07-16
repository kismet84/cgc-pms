package com.cgcpms.invoice;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("发票字段校验集成测试")
class InvoiceValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PayInvoiceMapper payInvoiceMapper;

    @Autowired
    private PayRecordMapper payRecordMapper;

    @Autowired
    private PayApplicationMapper payApplicationMapper;

    @Autowired
    private PmProjectMapper projectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long SEED_PAY_RECORD_ID = 90001L;
    private static final long SEED_PROJECT_ID = 90001L;

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                1L, "admin", 0L,
                List.of("ADMIN"),
                List.of("invoice:add"));
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM invoice_payment_allocation WHERE pay_record_id = ?", SEED_PAY_RECORD_ID);
        jdbcTemplate.update("DELETE FROM pay_invoice WHERE pay_record_id = ?", SEED_PAY_RECORD_ID);
        jdbcTemplate.update("""
                DELETE FROM pay_invoice
                WHERE invoice_no LIKE 'INV-VAL-%'
                   OR invoice_no LIKE 'INV-SEC-%'
                   OR invoice_no LIKE 'INV-FILTER-%'
                """);
        jdbcTemplate.update("DELETE FROM pay_record WHERE id = ?", SEED_PAY_RECORD_ID);
        jdbcTemplate.update("DELETE FROM pay_application WHERE id = ?", SEED_PAY_RECORD_ID);
        jdbcTemplate.update("DELETE FROM pm_project WHERE id = ?", SEED_PROJECT_ID);

        PmProject project = new PmProject();
        project.setId(SEED_PROJECT_ID);
        project.setTenantId(0L);
        project.setProjectCode("PRJ-INVOICE-VAL-90001");
        project.setProjectName("发票校验测试项目");
        project.setProjectType("CONSTRUCTION");
        project.setContractAmount(new BigDecimal("1000000.00"));
        project.setTargetCost(new BigDecimal("800000.00"));
        project.setStatus("RUNNING");
        project.setApprovalStatus("APPROVED");
        projectMapper.insert(project);

        PayApplication application = new PayApplication();
        application.setId(SEED_PAY_RECORD_ID);
        application.setTenantId(0L);
        application.setProjectId(SEED_PROJECT_ID);
        application.setApplyCode("PAY-INVOICE-VAL-90001");
        application.setPayType("PROGRESS");
        application.setApplyAmount(new BigDecimal("100000.00"));
        application.setApprovedAmount(new BigDecimal("100000.00"));
        application.setApprovalStatus("APPROVED");
        application.setPayStatus("PAID");
        payApplicationMapper.insert(application);

        PayRecord seed = new PayRecord();
        seed.setId(SEED_PAY_RECORD_ID);
        seed.setTenantId(0L);
        seed.setProjectId(SEED_PROJECT_ID);
        seed.setPayApplicationId(SEED_PAY_RECORD_ID);
        seed.setPayAmount(new BigDecimal("100000.00"));
        seed.setPayDate(java.time.LocalDate.of(2026, 6, 1));
        seed.setPayStatus("SUCCESS");
        payRecordMapper.insert(seed);
    }

    @Test
    @DisplayName("POST /api/invoices with null invoiceAmount → 400 with field name in error")
    void shouldRejectNullAmount() throws Exception {
        String body = """
                {
                    "invoiceNo": "INV-VAL-001",
                    "invoiceType": "VAT_SPECIAL"
                }""";

        mockMvc.perform(post("/api/invoices")
                        .contextPath("/api")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("invoiceAmount")));
    }

    @Test
    @DisplayName("POST /api/invoices with blank invoiceNo → 400 with field name in error")
    void shouldRejectBlankInvoiceNo() throws Exception {
        String body = """
                {
                    "invoiceNo": "",
                    "invoiceType": "VAT_SPECIAL",
                    "invoiceAmount": "10000.00"
                }""";

        mockMvc.perform(post("/api/invoices")
                        .contextPath("/api")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("invoiceNo")));
    }

    @Test
    @DisplayName("POST /api/invoices with blank invoiceType → 400 with field name in error")
    void shouldRejectBlankInvoiceType() throws Exception {
        String body = """
                {
                    "invoiceNo": "INV-VAL-003",
                    "invoiceType": "",
                    "invoiceAmount": "10000.00"
                }""";

        mockMvc.perform(post("/api/invoices")
                        .contextPath("/api")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("invoiceType")));
    }

    @Test
    @DisplayName("POST /api/invoices with tenantId=999 in body → tenantId is NOT 999 in DB (READ_ONLY guard)")
    void shouldIgnoreTenantIdFromRequestBody() throws Exception {
        String body = """
                {
                    "tenantId": 999,
                    "payRecordId": %d,
                    "invoiceNo": "INV-SEC-TENANT-001",
                    "invoiceType": "VAT_SPECIAL",
                    "invoiceAmount": "5000.00",
                    "invoiceDate": "2026-01-15"
                }""".formatted(SEED_PAY_RECORD_ID);

        MvcResult result = mockMvc.perform(post("/api/invoices")
                        .contextPath("/api")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn();

        // Extract invoice ID from response (handles both numeric and string JSON values)
        String json = result.getResponse().getContentAsString();
        Long invoiceId = Long.valueOf(json.replaceAll(".*\"data\":\"?(\\d+)\"?.*", "$1"));

        // Fetch from DB and verify tenantId is NOT 999
        PayInvoice dbInvoice = payInvoiceMapper.selectById(invoiceId);
        assertNotNull(dbInvoice, "Invoice should exist in DB");
        assertEquals(0L, dbInvoice.getTenantId(),
                "tenantId should be 0 (from JWT), not 999 (from request body)");
    }

    @Test
    @DisplayName("GET /api/invoices?invoiceNo=xxx → returns matching invoices only")
    void shouldFilterByInvoiceNoPartialMatch() throws Exception {
        // Create an invoice with a known partial invoiceNo
        String body = """
                {
                    "payRecordId": %d,
                    "invoiceNo": "INV-FILTER-PARTIAL-001",
                    "invoiceType": "VAT_SPECIAL",
                    "invoiceAmount": "3000.00",
                    "invoiceDate": "2026-01-15"
                }""".formatted(SEED_PAY_RECORD_ID);
        mockMvc.perform(post("/api/invoices")
                .contextPath("/api")
                .cookie(adminCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        // Filter by partial invoice number
        mockMvc.perform(get("/api/invoices")
                .contextPath("/api")
                .cookie(adminCookie())
                .param("invoiceNo", "FILTER-PARTIAL")
                .param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.records[0].invoiceNo").value(org.hamcrest.Matchers.containsString("FILTER-PARTIAL")));
    }

    @Test
    @DisplayName("GET /api/invoices?verifyStatus=PENDING → returns only PENDING invoices")
    void shouldFilterByVerifyStatus() throws Exception {
        // Create an invoice (defaults to PENDING)
        String body = """
                {
                    "payRecordId": %d,
                    "invoiceNo": "INV-FILTER-STATUS-001",
                    "invoiceType": "VAT_SPECIAL",
                    "invoiceAmount": "4000.00",
                    "invoiceDate": "2026-01-15"
                }""".formatted(SEED_PAY_RECORD_ID);
        mockMvc.perform(post("/api/invoices")
                .contextPath("/api")
                .cookie(adminCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        // Filter by verifyStatus=PENDING
        mockMvc.perform(get("/api/invoices")
                .contextPath("/api")
                .cookie(adminCookie())
                .param("verifyStatus", "PENDING")
                .param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.records[0].verifyStatus").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/invoices?verifyStatus=NONEXISTENT → returns empty, not error")
    void shouldReturnEmptyForNonMatchingVerifyStatus() throws Exception {
        mockMvc.perform(get("/api/invoices")
                .contextPath("/api")
                .cookie(adminCookie())
                .param("verifyStatus", "NONEXISTENT_STATUS")
                .param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
