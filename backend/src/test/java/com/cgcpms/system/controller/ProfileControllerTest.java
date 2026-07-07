package com.cgcpms.system.controller;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TDD RED phase — tests for self-service profile update and password change APIs.
 * Tests MUST fail initially since ProfileController and ProfileService do not exist yet.
 */
@SpringBootTest(properties = {
        "spring.main.allow-circular-references=true",
        "auth.csrf.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("ProfileController — self-service profile & password management")
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;

    /** Generate a valid JWT token for the admin user and wrap as HttpOnly cookie. */
    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                ADMIN_ID, ADMIN_USERNAME, TENANT_ID,
                List.of("ADMIN"),
                List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    /**
     * Seed admin user because V85 migration removes the default admin.
     * Required for MockMvc tests that authenticate via JWT and then look up the user in DB.
     */
    @BeforeEach
    void seedAdminUser() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ?", Integer.class, ADMIN_ID);
        if (count != null && count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    ADMIN_ID, TENANT_ID, "admin",
                    "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2",
                    "系统管理员", "13800000000", "admin@cgc-pms.com",
                    "ENABLE", 1, ADMIN_ID, "测试种子数据");
        } else {
            jdbcTemplate.update(
                    "UPDATE sys_user SET password = ?, real_name = ?, phone = ?, email = ?, status = ?, is_admin = ? WHERE id = ?",
                    "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2",
                    "系统管理员", "13800000000", "admin@cgc-pms.com", "ENABLE", 1, ADMIN_ID);
        }
        // Also seed the SUPER_ADMIN role and user-role mapping (needed by ProfileService.buildUserInfo)
        Integer roleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role WHERE id = 1", Integer.class);
        if (roleCount != null && roleCount == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sys_role (id, tenant_id, role_code, role_name, role_type, status, data_scope, created_by, remark) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    1L, 0L, "SUPER_ADMIN", "超级管理员", "SYSTEM", "ENABLE", "ALL", 1L, "测试种子数据");
        }
        Integer userRoleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role WHERE user_id = ? AND role_id = 1", Integer.class, ADMIN_ID);
        if (userRoleCount != null && userRoleCount == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sys_user_role (id, user_id, role_id) VALUES (?, ?, ?)",
                    1L, ADMIN_ID, 1L);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-1: Unauthenticated → 401
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RED-1: PUT /api/profile without JWT → 401")
    void testUnauthorized_ProfileUpdate() throws Exception {
        mockMvc.perform(putWithApiContext("/profile")
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"realName":"Test","phone":"13800000001"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RED-1b: PUT /api/profile/password without JWT → 401")
    void testUnauthorized_PasswordChange() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/password")
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"admin123","newPassword":"newPass123"}"""))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-2: Update profile successfully (happy path)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RED-2: PUT /api/profile updates realName/phone/email/avatar → 200 with updated UserInfo")
    void testUpdateProfile_Success() throws Exception {
        mockMvc.perform(putWithApiContext("/profile")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "realName":"管理员已更新",
                                    "phone":"13900000000",
                                    "email":"admin-updated@cgc.com",
                                    "avatar":"https://avatar.example.com/new.png"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.realName").value("管理员已更新"))
                .andExpect(jsonPath("$.data.phone").value("13900000000"))
                .andExpect(jsonPath("$.data.email").value("admin-updated@cgc.com"))
                .andExpect(jsonPath("$.data.avatar").value("https://avatar.example.com/new.png"));
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-3: Restricted fields are ignored server-side
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RED-3: PUT /api/profile ignores restricted fields (username, roles, status, isAdmin)")
    void testUpdateProfile_IgnoresRestrictedFields() throws Exception {
        mockMvc.perform(putWithApiContext("/profile")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username":"hacker",
                                    "realName":"正常名称",
                                    "isAdmin":1,
                                    "status":"DISABLE"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                // username must NOT be changed to "hacker"
                .andExpect(jsonPath("$.data.username").value("admin"))
                // realName IS allowed
                .andExpect(jsonPath("$.data.realName").value("正常名称"));
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-4: Change password successfully
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RED-4: PUT /api/profile/password with correct old password → 200")
    void testChangePassword_Success() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/password")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"admin123","newPassword":"NewSecure1!"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("Cookie JWT 写请求缺少 CSRF token → 403")
    void testCookieAuthenticatedWriteWithoutCsrfForbidden() throws Exception {
        mockMvc.perform(putWithApiContext("/profile")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"realName":"csrf-blocked"}"""))
                .andExpect(status().isForbidden());
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-5: Wrong old password → 400
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RED-5: PUT /api/profile/password with wrong oldPassword → 400")
    void testChangePassword_WrongOldPassword() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/password")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"wrongPassword","newPassword":"NewSecure1!"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_OLD_INVALID"))
                .andExpect(jsonPath("$.message").value("旧密码不正确"));
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-6: New password too short → 400 validation error
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RED-6: PUT /api/profile/password with newPassword < 8 chars → 400")
    void testChangePassword_ShortNewPassword() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/password")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"admin123","newPassword":"1234567"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("RED-7: PUT /api/profile/password with letters-only password → 400")
    void testChangePassword_PasswordWithoutDigit() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/password")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"admin123","newPassword":"abcdefgh"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("RED-8: PUT /api/profile/password with digits-only password → 400")
    void testChangePassword_PasswordWithoutLetter() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/password")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"admin123","newPassword":"12345678"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("RED-9: PUT /api/profile/password without uppercase → 400")
    void testChangePassword_PasswordWithoutUppercase() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/password")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"admin123","newPassword":"newsecure1!"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("RED-10: PUT /api/profile/password without lowercase → 400")
    void testChangePassword_PasswordWithoutLowercase() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/password")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"admin123","newPassword":"NEWSECURE1!"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("RED-11: PUT /api/profile/password without special char → 400")
    void testChangePassword_PasswordWithoutSpecialChar() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/password")
                        .cookie(adminCookie())
                        .cookie(csrfCookie())
                        .header("X-XSRF-TOKEN", "test-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"admin123","newPassword":"Newsecure12"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- helpers ----

    private MockHttpServletRequestBuilder putWithApiContext(String pathWithinContext) {
        return put("/api" + pathWithinContext).contextPath("/api");
    }

    private Cookie csrfCookie() {
        return new Cookie("XSRF-TOKEN", "test-csrf-token");
    }
}
