package com.cgcpms.settlement;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.service.StlSettlementWriteService;
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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TDD tests for 6 new StlSettlementController endpoints.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("StlSettlementController — 6 new endpoints")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StlSettlementControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private StlSettlementWriteService stlSettlementWriteService;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30003L;

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
        setUserContext();
        try {
            StlSettlement settlement = new StlSettlement();
            settlement.setProjectId(PROJECT_ID);
            settlement.setContractId(CONTRACT_ID);
            settlement.setSettlementType("FINAL");
            settlementId = stlSettlementWriteService.create(settlement);
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
                .andExpect(jsonPath("$.data").isArray());
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. GET /settlements/{id}/payments
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("GET /settlements/{id}/payments -> 200")
    void testGetPayments() throws Exception {
        mockMvc.perform(getWithApi("/settlements/" + settlementId + "/payments")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. GET /settlements/{id}/costs
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("GET /settlements/{id}/costs -> 200")
    void testGetCosts() throws Exception {
        mockMvc.perform(getWithApi("/settlements/" + settlementId + "/costs")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ═══════════════════════════════════════════════════════════════
    // 3b. GET /settlements/{id}/items (listItems fix — returns array)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("GET /settlements/{id}/items -> 200, data is array (item list, not parent VO)")
    void testListItemsReturnsArray() throws Exception {
        mockMvc.perform(getWithApi("/settlements/" + settlementId + "/items")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. GET /settlements/{id}/attachments
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("GET /settlements/{id}/attachments -> 200")
    void testGetAttachments() throws Exception {
        mockMvc.perform(getWithApi("/settlements/" + settlementId + "/attachments")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. GET /settlements/{id}/approval-records
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("GET /settlements/{id}/approval-records -> 200")
    void testGetApprovalRecords() throws Exception {
        mockMvc.perform(getWithApi("/settlements/" + settlementId + "/approval-records")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. POST /settlements/{id}/submit
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(7)
    @DisplayName("POST /settlements/{id}/submit -> 200")
    void testSubmitForApproval() throws Exception {
        mockMvc.perform(postWithApi("/settlements/" + settlementId + "/submit")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. Unauthorized -> 401
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(8)
    @DisplayName("GET without JWT -> 401")
    void testUnauthorized() throws Exception {
        mockMvc.perform(getWithApi("/settlements/" + settlementId + "/variations"))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private MockHttpServletRequestBuilder getWithApi(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder postWithApi(String pathWithinContext) {
        return post("/api" + pathWithinContext).contextPath("/api");
    }
}
