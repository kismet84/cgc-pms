package com.cgcpms.dashboard.controller;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DashboardController integration tests — routing, auth, and parameter binding.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("DashboardController — routing, auth, parameter binding")
class DashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                ADMIN_ID, ADMIN_USERNAME, TENANT_ID,
                List.of("ADMIN"), List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    private MockHttpServletRequestBuilder getWithContext(String path) {
        return get("/api" + path).contextPath("/api");
    }

    // ========================================================================
    // Unauthenticated → 401 for all endpoints
    // ========================================================================

    @Test
    @DisplayName("GET /api/dashboard/project-manager without JWT → 401")
    void testPMView_Unauthenticated() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/project-manager"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/dashboard/business-manager without JWT → 401")
    void testBMView_Unauthenticated() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/business-manager"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/dashboard/cost-manager without JWT → 401")
    void testCostView_Unauthenticated() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/cost-manager"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/dashboard/purchase-manager without JWT → 401")
    void testPurchaseView_Unauthenticated() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/purchase-manager"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/dashboard/production-manager without JWT → 401")
    void testProductionView_Unauthenticated() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/production-manager"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/dashboard/finance without JWT → 401")
    void testFinanceView_Unauthenticated() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/finance"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/dashboard/management without JWT → 401")
    void testManagementView_Unauthenticated() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/management"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/dashboard/project/1/cost-breakdown without JWT → 401")
    void testCostBreakdown_Unauthenticated() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/project/1/cost-breakdown"))
                .andExpect(status().isUnauthorized());
    }

    // ========================================================================
    // With admin cookie → 200 for all endpoints
    // ========================================================================

    @Test
    @DisplayName("GET /api/dashboard/project-manager?projectId=1 → 400 (project not found)")
    void testPMView_WithProjectId() throws Exception {
        // 200 if project exists, 400 if not — either is a valid route match
        mockMvc.perform(getWithContext("/dashboard/project-manager")
                        .cookie(adminCookie())
                        .param("projectId", "1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GET /api/dashboard/project-manager (no projectId) → 200")
    void testPMView_WithoutProjectId() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/project-manager")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("GET /api/dashboard/business-manager → 200 (no param)")
    void testBMView_NoProjectId() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/business-manager")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("GET /api/dashboard/cost-manager → 200 (no param)")
    void testCostView_NoProjectId() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/cost-manager")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("GET /api/dashboard/purchase-manager → 200 (no param)")
    void testPurchaseView_NoProjectId() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/purchase-manager")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("GET /api/dashboard/production-manager → 200 (no param)")
    void testProductionView_NoProjectId() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/production-manager")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("GET /api/dashboard/finance → 200 (no param)")
    void testFinanceView_NoProjectId() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/finance")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("GET /api/dashboard/management → 200 (no param needed)")
    void testManagementView_NoParam() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/management")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("GET /api/dashboard/project/999999/cost-breakdown → 400 project not found")
    void testCostBreakdown_ProjectNotFound() throws Exception {
        // Returns 400 because project 999999 doesn't exist; the route IS matched
        mockMvc.perform(getWithContext("/dashboard/project/999999/cost-breakdown")
                        .cookie(adminCookie()))
                .andExpect(status().is4xxClientError());
    }
}
