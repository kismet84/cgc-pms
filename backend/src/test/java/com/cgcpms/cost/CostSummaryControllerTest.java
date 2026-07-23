package com.cgcpms.cost;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.cost.controller.CostSummaryController;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CostSummaryController integration tests covering getLatest, refresh, and history.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("CostSummaryController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CostSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PmProjectMapper projectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long MANAGED_PROJECT_ID = 8261001L;
    private static final long PROJECT_MANAGER_ID = 93061L;
    private static final long NO_PROJECT_ACCESS_USER_ID = 93062L;

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                ADMIN_ID, ADMIN_USERNAME, TENANT_ID,
                List.of("ADMIN"),
                List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    private Cookie summaryViewerCookie(long userId, long tenantId, List<String> roles) {
        return authorityCookie(userId, tenantId, roles, "cost:summary:view");
    }

    private Cookie authorityCookie(long userId, long tenantId, List<String> roles, String... authorities) {
        String token = jwtUtils.generateToken(
                userId, "summary-user-" + userId, tenantId,
                roles,
                List.of(authorities));
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    private Cookie authenticatedCookieWithoutPermissions(long userId) {
        String token = jwtUtils.generateToken(
                userId, "authenticated-user-" + userId, TENANT_ID,
                List.of("COMMON_USER"),
                List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    // ═══════════════════════════════════════════════════════════════
    // Unauthorized checks
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("GET /cost-summary/{projectId} without JWT -> 401")
    void testGetLatest_Unauthorized() throws Exception {
        mockMvc.perform(getWithApi("/cost-summary/" + PROJECT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1)
    @DisplayName("POST /cost-summary/{projectId}/refresh without JWT -> 401")
    void testRefresh_Unauthorized() throws Exception {
        mockMvc.perform(postWithApi("/cost-summary/" + PROJECT_ID + "/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1)
    @DisplayName("GET /cost-summary/{projectId}/history without JWT -> 401")
    void testGetHistory_Unauthorized() throws Exception {
        mockMvc.perform(getWithApi("/cost-summary/" + PROJECT_ID + "/history"))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════════════
    // GET latest (getProjectSummary)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("GET /cost-summary/{projectId} -> 200 with project summary data")
    void testGetLatest() throws Exception {
        mockMvc.perform(getWithApi("/cost-summary/" + PROJECT_ID)
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.projectId").exists())
                .andExpect(jsonPath("$.data.projectName").exists());
    }

    @Test
    @Order(3)
    @DisplayName("GET /cost-summary/{projectId} for non-existent -> 400")
    void testGetLatest_NotFound() throws Exception {
        mockMvc.perform(getWithApi("/cost-summary/999999")
                        .cookie(adminCookie()))
                .andExpect(status().isBadRequest());
    }

    // ═══════════════════════════════════════════════════════════════
    // POST refresh
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("POST /cost-summary/{projectId}/refresh -> 200 refreshes and returns summary")
    void testRefresh() throws Exception {
        mockMvc.perform(postWithApi("/cost-summary/" + PROJECT_ID + "/refresh")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.projectId").exists())
                .andExpect(jsonPath("$.data.projectName").exists());
    }

    @Test
    @Order(4)
    @DisplayName("POST refresh requires dedicated permission and carries audit metadata")
    void testRefresh_DedicatedPermissionAndAudit() throws Exception {
        mockMvc.perform(postWithApi("/cost-summary/" + PROJECT_ID + "/refresh")
                        .cookie(summaryViewerCookie(980000000000000023L, TENANT_ID, List.of("COMMERCIAL_MANAGER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        mockMvc.perform(postWithApi("/cost-summary/" + PROJECT_ID + "/refresh")
                        .cookie(authorityCookie(980000000000000023L, TENANT_ID,
                                List.of("COMMERCIAL_MANAGER"), "cost:summary:refresh")))
                .andExpect(status().isOk());

        AuditedOperation audit = CostSummaryController.class
                .getMethod("refresh", Long.class).getAnnotation(AuditedOperation.class);
        assertEquals("REFRESH", audit.type());
        assertEquals("COST_SUMMARY", audit.businessType());
        assertEquals("#projectId", audit.businessIdExpression());
    }

    // ═══════════════════════════════════════════════════════════════
    // GET history
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("GET /cost-summary/{projectId}/history -> 200 with list of snapshots")
    void testGetHistory() throws Exception {
        mockMvc.perform(getWithApi("/cost-summary/" + PROJECT_ID + "/history")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(5)
    @DisplayName("GET /cost-summary/{projectId}/history keeps extreme money as decimal strings")
    void testGetHistory_MoneyRemainsDecimalString() throws Exception {
        long summaryId = 995301703L;
        try {
            jdbc.update("INSERT INTO cost_summary(id,tenant_id,project_id,summary_date,target_cost,contract_locked_cost,actual_cost,paid_amount,estimated_remaining_cost,dynamic_cost,contract_income,confirmed_revenue,expected_profit,cost_deviation,responsibility_cost,forecast_at_completion_cost,forecast_profit,profit_margin,created_at,updated_at,deleted_flag) VALUES(?,0,?,'2099-12-31',9007199254740993.25,0.00,-1.25,0.00,0.00,9007199254740992.00,9007199254740993.25,0.00,-1.25,0.00,0.00,9007199254740992.00,-1.25,0.000000,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", summaryId, PROJECT_ID);

            mockMvc.perform(getWithApi("/cost-summary/" + PROJECT_ID + "/history").cookie(adminCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].targetCost").value("9007199254740993.25"))
                    .andExpect(jsonPath("$.data[0].actualCost").value("-1.25"))
                    .andExpect(jsonPath("$.data[0].paidAmount").value("0.00"))
                    .andExpect(jsonPath("$.data[0].expectedProfit").value("-1.25"));
        } finally {
            jdbc.update("DELETE FROM cost_summary WHERE id=?", summaryId);
        }
    }

    @Test
    @Order(6)
    @DisplayName("GET /cost-summary/{projectId} same-tenant user without project access -> 403")
    void testGetLatest_SameTenantWithoutProjectAccessForbidden() throws Exception {
        mockMvc.perform(getWithApi("/cost-summary/" + PROJECT_ID)
                        .cookie(authorityCookie(NO_PROJECT_ACCESS_USER_ID, TENANT_ID, List.of("COMMON_USER"), "cost:summary:refresh")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @Order(7)
    @DisplayName("POST /cost-summary/{projectId}/refresh same-tenant user without project access -> 403")
    void testRefresh_SameTenantWithoutProjectAccessForbidden() throws Exception {
        mockMvc.perform(postWithApi("/cost-summary/" + PROJECT_ID + "/refresh")
                        .cookie(summaryViewerCookie(NO_PROJECT_ACCESS_USER_ID, TENANT_ID, List.of("COMMON_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @Order(8)
    @DisplayName("GET /cost-summary/{projectId}/history same-tenant user without project access -> 403")
    void testGetHistory_SameTenantWithoutProjectAccessForbidden() throws Exception {
        mockMvc.perform(getWithApi("/cost-summary/" + PROJECT_ID + "/history")
                        .cookie(summaryViewerCookie(NO_PROJECT_ACCESS_USER_ID, TENANT_ID, List.of("COMMON_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @Order(9)
    @DisplayName("GET /cost-summary/{projectId} cross-tenant user -> project hidden")
    void testGetLatest_CrossTenantUserProjectHidden() throws Exception {
        mockMvc.perform(getWithApi("/cost-summary/" + PROJECT_ID)
                        .cookie(summaryViewerCookie(ADMIN_ID, 999L, List.of("ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    @Order(10)
    @DisplayName("GET /cost-summary/{projectId} ALL data-scope viewer -> 200")
    void testGetLatest_AllDataScopeViewerAllowed() throws Exception {
        mockMvc.perform(getWithApi("/cost-summary/" + PROJECT_ID)
                        .cookie(summaryViewerCookie(980000000000000023L, TENANT_ID, List.of("COMMERCIAL_MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @Order(11)
    @DisplayName("GET /cost-summary/{projectId} project manager -> 200")
    void testGetLatest_ProjectManagerAllowed() throws Exception {
        seedManagedProjectIfAbsent();

        mockMvc.perform(getWithApi("/cost-summary/" + MANAGED_PROJECT_ID)
                        .cookie(summaryViewerCookie(PROJECT_MANAGER_ID, TENANT_ID, List.of("COMMON_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.projectId").value(String.valueOf(MANAGED_PROJECT_ID)));
    }

    @Test
    @Order(12)
    @DisplayName("GET /cost-summary/{projectId}/history authenticated user without permission -> 403")
    void testGetHistory_WithoutPermissionForbidden() throws Exception {
        mockMvc.perform(getWithApi("/cost-summary/" + PROJECT_ID + "/history")
                        .cookie(authenticatedCookieWithoutPermissions(93063L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @Order(13)
    @DisplayName("GET /cost-summary/{projectId}/history cross-tenant user -> project hidden")
    void testGetHistory_CrossTenantUserProjectHidden() throws Exception {
        mockMvc.perform(getWithApi("/cost-summary/" + PROJECT_ID + "/history")
                        .cookie(summaryViewerCookie(ADMIN_ID, 999L, List.of("ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    @Order(14)
    @DisplayName("GET /cost-summary/{projectId}/history project manager with permission -> 200")
    void testGetHistory_ProjectManagerAllowed() throws Exception {
        seedManagedProjectIfAbsent();

        mockMvc.perform(getWithApi("/cost-summary/" + MANAGED_PROJECT_ID + "/history")
                        .cookie(summaryViewerCookie(PROJECT_MANAGER_ID, TENANT_ID, List.of("COMMON_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── helpers ──

    private MockHttpServletRequestBuilder getWithApi(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder postWithApi(String pathWithinContext) {
        return post("/api" + pathWithinContext).contextPath("/api");
    }

    private void seedManagedProjectIfAbsent() {
        if (projectMapper.selectById(MANAGED_PROJECT_ID) != null) {
            return;
        }
        PmProject project = new PmProject();
        project.setId(MANAGED_PROJECT_ID);
        project.setTenantId(TENANT_ID);
        project.setProjectCode("COST-SUMMARY-MANAGED");
        project.setProjectName("成本摘要项目经理权限测试项目");
        project.setProjectType("CONSTRUCTION");
        project.setContractAmount(new java.math.BigDecimal("1000000.00"));
        project.setTargetCost(new java.math.BigDecimal("800000.00"));
        project.setProjectManagerId(PROJECT_MANAGER_ID);
        project.setStatus("ACTIVE");
        project.setApprovalStatus("APPROVED");
        project.setCreatedBy(ADMIN_ID);
        projectMapper.insert(project);
    }
}
