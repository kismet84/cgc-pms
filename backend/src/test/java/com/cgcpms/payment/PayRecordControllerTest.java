package com.cgcpms.payment;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
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
    private PayRecordService payRecordService;

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
