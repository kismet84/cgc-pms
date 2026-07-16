package com.cgcpms.invoice;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.PaymentTestFixtures;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.invoice.entity.InvoicePaymentAllocation;
import com.cgcpms.invoice.service.InvoiceService;
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
 * InvoiceController integration tests covering list, getById, create, update, delete, verify, and register.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("InvoiceController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PayRecordMapper payRecordMapper;

    @Autowired
    private PayApplicationMapper payApplicationMapper;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;
    private static final long PARTNER_ID = 20002L;
    private static final String CSRF_TOKEN = "test-csrf-token";

    private Long payRecordId;
    private Long invoiceId;

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
            PaymentTestFixtures.insertApplication(payApplicationMapper, PROJECT_ID, TENANT_ID,
                    PROJECT_ID, CONTRACT_ID, PARTNER_ID, new BigDecimal("3000.00"));
            PayRecord record = new PayRecord();
            record.setTenantId(TENANT_ID);
            record.setProjectId(PROJECT_ID);
            record.setPayApplicationId(PROJECT_ID);
            record.setContractId(CONTRACT_ID);
            record.setPartnerId(PARTNER_ID);
            record.setPayAmount(new BigDecimal("10000.00"));
            record.setPayDate(LocalDate.now());
            record.setPayMethod("BANK_TRANSFER");
            record.setPayStatus("SUCCESS");
            record.setExternalTxnNo("INV-TEST-TXN-" + System.nanoTime());
            payRecordMapper.insert(record);
            payRecordId = record.getId();
        } finally {
            clearUserContext();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Unauthorized checks
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("GET /invoices without JWT -> 401")
    void testList_Unauthorized() throws Exception {
        mockMvc.perform(getWithApi("/invoices"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1)
    @DisplayName("GET /invoices/{id} without JWT -> 401")
    void testGetById_Unauthorized() throws Exception {
        mockMvc.perform(getWithApi("/invoices/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1)
    @DisplayName("POST /invoices without JWT -> 401")
    void testCreate_Unauthorized() throws Exception {
        mockMvc.perform(withCsrf(postWithApi("/invoices"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceNo\":\"INV001\",\"invoiceAmount\":1000,\"invoiceDate\":\"2026-06-22\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1)
    @DisplayName("POST /invoices/register without JWT -> 401")
    void testRegister_Unauthorized() throws Exception {
        mockMvc.perform(withCsrf(postWithApi("/invoices/register"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceNo\":\"INV001\",\"invoiceAmount\":1000,\"invoiceDate\":\"2026-06-22\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════════════
    // GET list
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("GET /invoices -> 200 with paginated data")
    void testList() throws Exception {
        mockMvc.perform(getWithApi("/invoices")
                        .cookie(adminCookie())
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").isString());
    }

    // ═══════════════════════════════════════════════════════════════
    // POST create
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("POST /invoices -> 200 creates invoice and returns id")
    void testCreate() throws Exception {
        String body = """
                {
                    "invoiceNo": "INV-TEST-%d",
                    "invoiceType": "VAT_SPECIAL",
                    "invoiceAmount": 5000.00,
                    "invoiceDate": "2026-06-22",
                    "payRecordId": %d
                }
                """.formatted(System.nanoTime(), payRecordId);

        String response = mockMvc.perform(postWithApi("/invoices")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();

        // Extract created invoice ID (serialized as JSON string)
        invoiceId = Long.parseLong(
                response.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(invoiceId, "Created invoice ID should not be null");
        Assertions.assertTrue(invoiceId > 0, "Created invoice ID should be positive");
    }

    @Test
    @Order(4)
    @DisplayName("POST /invoices with missing required field -> 400")
    void testCreate_MissingRequired() throws Exception {
        mockMvc.perform(withCsrf(postWithApi("/invoices"))
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("POST /invoices malformed JSON -> 400 with fixed validation message")
    void testCreate_MalformedJson_UsesFixedMessage() throws Exception {
        mockMvc.perform(withCsrf(postWithApi("/invoices"))
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceNo\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请求数据格式错误"));
    }

    // ═══════════════════════════════════════════════════════════════
    // GET by id
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("GET /invoices/{id} -> 200 with invoice data")
    void testGetById() throws Exception {
        Assertions.assertNotNull(invoiceId, "Prerequisite: invoiceId must be created");

        mockMvc.perform(getWithApi("/invoices/" + invoiceId)
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.invoiceNo").exists())
                .andExpect(jsonPath("$.data.verifyStatus").value("PENDING"));
    }

    @Test
    @Order(6)
    @DisplayName("GET /invoices/{id} for non-existent -> 400")
    void testGetById_NotFound() throws Exception {
        mockMvc.perform(getWithApi("/invoices/999999")
                        .cookie(adminCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));
    }

    // ═══════════════════════════════════════════════════════════════
    // PUT update
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(7)
    @DisplayName("PUT /invoices/{id} -> 200 updates invoice")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(invoiceId, "Prerequisite: invoiceId must be created");

        String body = """
                {
                    "invoiceNo": "INV-TEST-%d",
                    "invoiceType": "VAT_SPECIAL",
                    "invoiceAmount": 6000.00,
                    "invoiceDate": "2026-06-22"
                }
                """.formatted(System.nanoTime());

        mockMvc.perform(withCsrf(putWithApi("/invoices/" + invoiceId))
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ═══════════════════════════════════════════════════════════════
    // PUT verify
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(8)
    @DisplayName("PUT /invoices/{id}/verify invalid status -> 400")
    void testVerify_InvalidStatus() throws Exception {
        Assertions.assertNotNull(invoiceId, "Prerequisite: invoiceId must be created");

        mockMvc.perform(withCsrf(putWithApi("/invoices/" + invoiceId + "/verify"))
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verifyStatus\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @Order(8)
    @DisplayName("POST /invoices/{id}/verify invalid status -> 400")
    void testVerify_PostInvalidStatus() throws Exception {
        Assertions.assertNotNull(invoiceId, "Prerequisite: invoiceId must be created");

        mockMvc.perform(withCsrf(postWithApi("/invoices/" + invoiceId + "/verify"))
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verifyStatus\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @Order(9)
    @DisplayName("PUT /invoices/{id}/verify -> 200 verifies invoice")
    void testVerify() throws Exception {
        Assertions.assertNotNull(invoiceId, "Prerequisite: invoiceId must be created");

        setUserContext();
        try {
            InvoicePaymentAllocation allocation = new InvoicePaymentAllocation();
            allocation.setPayRecordId(payRecordId);
            allocation.setAllocatedAmount(new BigDecimal("6000.00"));
            invoiceService.saveAllocations(invoiceId, List.of(allocation));
            jdbcTemplate.update("""
                    INSERT INTO sys_file(id, tenant_id, business_type, document_type, business_id,
                        file_name, original_name, file_size, content_type, storage_path, bucket_name,
                        created_at, updated_at, deleted_flag)
                    VALUES(?, 0, 'INVOICE', 'ELECTRONIC_INVOICE', ?, 'invoice.pdf', 'invoice.pdf',
                        128, 'application/pdf', ?, 'cgc-pms', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    """, System.nanoTime(), invoiceId, "INVOICE/" + invoiceId + "/invoice.pdf");
        } finally {
            clearUserContext();
        }

        mockMvc.perform(withCsrf(putWithApi("/invoices/" + invoiceId + "/verify"))
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verifyStatus\":\"VERIFIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ═══════════════════════════════════════════════════════════════
    // POST register
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("POST /invoices/register -> 200 links pay record and duplicate invoiceNo -> 400")
    void testRegisterLinksPayRecordAndRejectsDuplicateInvoiceNo() throws Exception {
        Assertions.assertNotNull(payRecordId, "Prerequisite: payRecordId must be created");
        String invoiceNo = "INV-REG-" + System.nanoTime();

        String body = """
                {
                    "payRecordId": %d,
                    "invoiceNo": "%s",
                    "invoiceType": "VAT_SPECIAL",
                    "invoiceAmount": 3000.00,
                    "invoiceDate": "2026-06-22"
                }
                """.formatted(payRecordId, invoiceNo);

        String response = mockMvc.perform(withCsrf(postWithApi("/invoices/register"))
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();

        Long registeredId = Long.parseLong(response.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        mockMvc.perform(getWithApi("/invoices/" + registeredId)
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceNo").value(invoiceNo))
                .andExpect(jsonPath("$.data.invoiceAmount").value("3000.00"))
                .andExpect(jsonPath("$.data.invoiceDate").value("2026-06-22"))
                .andExpect(jsonPath("$.data.payRecordId").value(String.valueOf(payRecordId)));

        mockMvc.perform(withCsrf(postWithApi("/invoices/register"))
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVOICE_NO_DUPLICATE"));
    }

    // ═══════════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(11)
    @DisplayName("DELETE /invoices/{id} -> 200 deletes invoice")
    void testDelete() throws Exception {
        Assertions.assertNotNull(payRecordId, "Prerequisite: payRecordId must be created");
        String body = """
                {
                    "invoiceNo": "INV-DEL-%d",
                    "invoiceType": "VAT_SPECIAL",
                    "invoiceAmount": 1800.00,
                    "invoiceDate": "2026-06-23",
                    "payRecordId": %d
                }
                """.formatted(System.nanoTime(), payRecordId);
        String response = mockMvc.perform(withCsrf(postWithApi("/invoices"))
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long deleteId = Long.parseLong(response.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));

        mockMvc.perform(withCsrf(deleteWithApi("/invoices/" + deleteId))
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        // Verify deleted
        mockMvc.perform(getWithApi("/invoices/" + deleteId)
                        .cookie(adminCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));
    }

    // ── helpers ──

    private MockHttpServletRequestBuilder getWithApi(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder postWithApi(String pathWithinContext) {
        return post("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder putWithApi(String pathWithinContext) {
        return put("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder deleteWithApi(String pathWithinContext) {
        return delete("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        return request.cookie(csrfCookie())
                .header("X-XSRF-TOKEN", CSRF_TOKEN);
    }

    private Cookie csrfCookie() {
        return new Cookie("XSRF-TOKEN", CSRF_TOKEN);
    }
}
