package com.cgcpms.cost;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

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

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                ADMIN_ID, ADMIN_USERNAME, TENANT_ID,
                List.of("ADMIN"),
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

    // ── helpers ──

    private MockHttpServletRequestBuilder getWithApi(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder postWithApi(String pathWithinContext) {
        return post("/api" + pathWithinContext).contextPath("/api");
    }
}
