package com.cgcpms.inventory;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
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
 * TDD RED phase — reproduce inventory controller binding failures.
 * <p>
 * Known bugs:
 * <ul>
 *   <li>POST /inventory/stock/in and /out use {@code @RequestParam} but frontend sends JSON body → 500</li>
 *   <li>GET /inventory/stock/ledger requires {@code materialId} but frontend may omit it → 500</li>
 * </ul>
 * ALL tests expect HTTP error (400/500) on current code — this is the RED phase.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("MatStockController — JSON body binding and ledger validation")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MatStockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;
    private static final long WAREHOUSE_ID = 100L;
    private static final long MATERIAL_ID = 1001L;

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                ADMIN_ID, ADMIN_USERNAME, TENANT_ID,
                List.of("ADMIN"),
                List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-1: POST JSON body to /inventory/stock/in → EXPECTS 200
    // Bug: controller uses @RequestParam, frontend sends JSON body
    // Current behavior: MissingServletRequestParameterException → 500
    // RED assertion: expects 200, but gets 500 → TEST FAILS → RED
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("RED-1: POST /inventory/stock/in with JSON body → expects 200 but gets error (binding mismatch)")
    void testStockInWithJsonBodyFails() throws Exception {
        mockMvc.perform(postWithApi("/inventory/stock/in")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"warehouseId":100,"materialId":1001,"quantity":"100.0000"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-2: POST JSON body to /inventory/stock/out → EXPECTS 200
    // Same binding mismatch as stock-in
    // RED assertion: expects 200, but gets error → FAILS → RED
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("RED-2: POST /inventory/stock/out with JSON body → expects 200 but gets error (binding mismatch)")
    void testStockOutWithJsonBodyFails() throws Exception {
        mockMvc.perform(postWithApi("/inventory/stock/out")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"warehouseId":100,"materialId":1001,"quantity":"50.0000"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-3: GET /inventory/stock/ledger WITHOUT materialId → EXPECTS 200
    // Bug: controller @RequestParam Long materialId is required,
    // frontend may omit it when only warehouse is selected
    // RED assertion: expects 200, but gets 500 → FAILS → RED
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("RED-3: GET /inventory/stock/ledger without materialId → expects 200 but gets error")
    void testGetLedgerWithoutMaterialIdFails() throws Exception {
        mockMvc.perform(getWithApi("/inventory/stock/ledger")
                        .cookie(adminCookie())
                        .param("warehouseId", String.valueOf(WAREHOUSE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-4: GET /inventory/stock/ledger WITH materialId → 200 (OK)
    // This is the current valid case — warehouse AND material both provided.
    // Should return 200 with valid JSON response (even if stock is empty).
    // NOTE: This test documents the current working behavior.
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("RED-4: GET /inventory/stock/ledger with warehouseId and materialId → 200")
    void testGetLedgerWithMaterialIdSucceeds() throws Exception {
        mockMvc.perform(getWithApi("/inventory/stock/ledger")
                        .cookie(adminCookie())
                        .param("warehouseId", String.valueOf(WAREHOUSE_ID))
                        .param("materialId", String.valueOf(MATERIAL_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-5: Unauthenticated POST → 401
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("RED-5: POST /inventory/stock/in without JWT → 401")
    void testUnauthenticatedStockIn() throws Exception {
        mockMvc.perform(postWithApi("/inventory/stock/in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"warehouseId":100,"materialId":1001,"quantity":"100.0000"}"""))
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
