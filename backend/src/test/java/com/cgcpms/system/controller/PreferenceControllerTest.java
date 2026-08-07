package com.cgcpms.system.controller;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.common.JwtHttpTestTokenFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TDD RED phase — tests for user preference CRUD APIs.
 * Tests MUST fail initially since PreferenceController and PreferenceService do not exist yet.
 */
@SpringBootTest(properties = {"auth.csrf.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("PreferenceController — user preference CRUD")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtHttpTestTokenFactory jwtUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;

    /** Generate a valid JWT token for the admin user and wrap as HttpOnly cookie. */
    private Cookie adminCookie() {
        return userCookie(ADMIN_ID, TENANT_ID);
    }

    private Cookie userCookie(long userId, long tenantId) {
        String token = jwtUtils.generateToken(
                userId, ADMIN_USERNAME, tenantId,
                List.of("ADMIN"),
                List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-1: Unauthenticated → 401
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("RED-1: GET /api/profile/preferences without JWT → 401")
    void testUnauthorized_Get() throws Exception {
        mockMvc.perform(getWithApiContext("/profile/preferences"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1)
    @DisplayName("RED-1b: PUT /api/profile/preferences without JWT → 401")
    void testUnauthorized_Put() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/preferences")
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"dark\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-2: GET returns default preferences when no record exists
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("RED-2: GET /api/profile/preferences with no saved record → 200 with defaults")
    void testGetPreferences_Default() throws Exception {
        mockMvc.perform(getWithApiContext("/profile/preferences")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.sidebarCollapsed").value(false))
                .andExpect(jsonPath("$.data.notificationEnabled").value(true))
                .andExpect(jsonPath("$.data.theme").value("light"))
                .andExpect(jsonPath("$.data.tableDensity").value("middle"));
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-3: PUT saves preferences, subsequent GET returns saved values
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("RED-3: PUT → GET returns saved preferences")
    void testPutPreferences_Update() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/preferences")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"theme":"dark","sidebarCollapsed":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.theme").value("dark"))
                .andExpect(jsonPath("$.data.sidebarCollapsed").value(true));

        // Subsequent GET returns saved values
        mockMvc.perform(getWithApiContext("/profile/preferences")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.theme").value("dark"))
                .andExpect(jsonPath("$.data.sidebarCollapsed").value(true));
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-4: Partial update — only theme, other fields keep values
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("RED-4: PUT only theme → other fields keep existing values")
    void testPutPreferences_Partial() throws Exception {
        // First save full preferences
        mockMvc.perform(putWithApiContext("/profile/preferences")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"theme":"dark","sidebarCollapsed":true,"notificationEnabled":false,"tableDensity":"small"}"""))
                .andExpect(status().isOk());

        // Partial update: only theme
        mockMvc.perform(putWithApiContext("/profile/preferences")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"theme":"light"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.theme").value("light"))
                .andExpect(jsonPath("$.data.sidebarCollapsed").value(true))
                .andExpect(jsonPath("$.data.notificationEnabled").value(false))
                .andExpect(jsonPath("$.data.tableDensity").value("small"));
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-5: GET returns updated values after PUT (end-to-end)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("RED-5: PUT then GET — full roundtrip verification")
    void testGetPreferences_Existing() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/preferences")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"theme":"dark","sidebarCollapsed":false,"notificationEnabled":true,"tableDensity":"middle"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(getWithApiContext("/profile/preferences")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.theme").value("dark"))
                .andExpect(jsonPath("$.data.sidebarCollapsed").value(false))
                .andExpect(jsonPath("$.data.notificationEnabled").value(true))
                .andExpect(jsonPath("$.data.tableDensity").value("middle"));
    }

    @Test
    @Order(6)
    @DisplayName("PUT rejects unknown preference keys")
    void testPutPreferences_UnknownKey() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/preferences")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"theme":"dark","tenantId":99}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFILE_PREFERENCES_INVALID"));
    }

    @Test
    @Order(7)
    @DisplayName("PUT rejects invalid preference types and enum values")
    void testPutPreferences_InvalidValue() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/preferences")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sidebarCollapsed":"true","theme":"system"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFILE_PREFERENCES_INVALID"));
    }

    @Test
    @Order(8)
    @DisplayName("Cookie JWT PUT without CSRF token returns 403")
    void testPutPreferences_WithoutCsrf() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/preferences")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"theme":"dark"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    @DisplayName("preferences are isolated by current user and tenant")
    void testPreferences_UserAndTenantIsolation() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/preferences")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"theme":"dark"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(getWithApiContext("/profile/preferences")
                        .cookie(userCookie(902L, TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.theme").value("light"));

        mockMvc.perform(getWithApiContext("/profile/preferences")
                        .cookie(userCookie(ADMIN_ID, 902L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.theme").value("light"));
    }

    @Test
    @Order(10)
    @DisplayName("GET normalizes unsupported legacy values to public defaults")
    void testGetPreferences_NormalizesLegacyValues() throws Exception {
        jdbcTemplate.update(
                "UPDATE sys_user_preference SET preferences = ? WHERE tenant_id = ? AND user_id = ?",
                """
                        {"theme":"system","tableDensity":"compact","sidebarCollapsed":"true","internalKey":{"enabled":true}}""",
                TENANT_ID,
                ADMIN_ID);

        mockMvc.perform(getWithApiContext("/profile/preferences")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sidebarCollapsed").value(false))
                .andExpect(jsonPath("$.data.notificationEnabled").value(true))
                .andExpect(jsonPath("$.data.theme").value("light"))
                .andExpect(jsonPath("$.data.tableDensity").value("middle"))
                .andExpect(jsonPath("$.data.internalKey").doesNotExist());
    }

    // ---- helpers ----

    private MockHttpServletRequestBuilder getWithApiContext(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder putWithApiContext(String pathWithinContext) {
        return put("/api" + pathWithinContext).contextPath("/api");
    }

    private Cookie csrfCookie() {
        return new Cookie("XSRF-TOKEN", "test-csrf-token");
    }
}
