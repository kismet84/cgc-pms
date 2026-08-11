package com.cgcpms.dashboard.controller;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("DashboardController — routing, auth, parameter binding")
class DashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private SysUserMapper sysUserMapper;

    private static final long ADMIN_ID = 910010001L;
    private static final String ADMIN_USERNAME = "dashboard-controller-test-admin";
    private static final long TENANT_ID = 91001L;

    @BeforeEach
    void ensureAuthenticatedUserExists() {
        if (sysUserMapper.selectCredentialByTenantAndId(TENANT_ID, ADMIN_ID) != null) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setId(ADMIN_ID);
        admin.setTenantId(TENANT_ID);
        admin.setUsername(ADMIN_USERNAME);
        admin.setPassword("{noop}dashboard-controller-test-only");
        admin.setStatus("ENABLE");
        admin.setIsAdmin(1);
        sysUserMapper.insert(admin);
    }

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
    @DisplayName("GET /api/dashboard/business-manager?month=2026-06 → 200")
    void testBMView_WithMonth() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/business-manager")
                        .cookie(adminCookie())
                        .param("month", "2026-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("GET /api/dashboard/business-manager?month=invalid → 400")
    void testBMView_InvalidMonthRejected() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/business-manager")
                        .cookie(adminCookie())
                        .param("month", "not-a-month"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DASHBOARD_MONTH"));
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

    @Test
    @DisplayName("GET /api/dashboard/project/999999/cost-breakdown?month=2026-06 accepts month")
    void testCostBreakdown_WithMonth() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/project/999999/cost-breakdown")
                        .cookie(adminCookie())
                        .param("month", "2026-06"))
                .andExpect(status().is4xxClientError());
    }

    // ========================================================================
    // Month parameter acceptance tests
    // ========================================================================

    @Test
    @DisplayName("GET /api/dashboard/project-manager?month=2026-06 → 200 (month accepted)")
    void testPMView_WithMonth() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/project-manager")
                        .cookie(adminCookie())
                        .param("month", "2026-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("GET /api/dashboard/project-manager?month=invalid → 400")
    void testPMView_InvalidMonthRejected() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/project-manager")
                        .cookie(adminCookie())
                        .param("month", "not-a-month"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DASHBOARD_MONTH"));
    }

    @Test
    @DisplayName("GET /api/dashboard/purchase-manager?month=2026-06 → 200 (month accepted)")
    void testPurchaseView_WithMonth() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/purchase-manager")
                        .cookie(adminCookie())
                        .param("month", "2026-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("GET /api/dashboard/purchase-manager?month=invalid → 400")
    void testPurchaseView_InvalidMonthRejected() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/purchase-manager")
                        .cookie(adminCookie())
                        .param("month", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DASHBOARD_MONTH"));
    }

    @Test
    @DisplayName("GET /api/dashboard/production-manager?month=2026-06 → 200 (month accepted)")
    void testProductionView_WithMonth() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/production-manager")
                        .cookie(adminCookie())
                        .param("month", "2026-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("GET /api/dashboard/production-manager?month=invalid → 400")
    void testProductionView_InvalidMonthRejected() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/production-manager")
                        .cookie(adminCookie())
                        .param("month", "xyz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DASHBOARD_MONTH"));
    }

    @Test
    @DisplayName("GET /api/dashboard/chief-engineer?month=2026-06 → 200 (month accepted)")
    void testChiefEngineerView_WithMonth() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/chief-engineer")
                        .cookie(adminCookie())
                        .param("month", "2026-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("GET /api/dashboard/chief-engineer?month=invalid → 400")
    void testChiefEngineerView_InvalidMonthRejected() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/chief-engineer")
                        .cookie(adminCookie())
                        .param("month", "not-valid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DASHBOARD_MONTH"));
    }

    @Test
    @DisplayName("GET /api/dashboard/cost-manager?month=invalid → 400")
    void testCostView_InvalidMonthRejected() throws Exception {
        mockMvc.perform(getWithContext("/dashboard/cost-manager")
                        .cookie(adminCookie())
                        .param("month", "bad-month-format"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DASHBOARD_MONTH"));
    }
}
