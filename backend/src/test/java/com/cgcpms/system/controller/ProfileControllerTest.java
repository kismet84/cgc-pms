package com.cgcpms.system.controller;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * TDD RED phase — tests for self-service profile update and password change APIs.
 * Tests MUST fail initially since ProfileController and ProfileService do not exist yet.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("ProfileController — self-service profile & password management")
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

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

    // ═══════════════════════════════════════════════════════════════
    // RED-1: Unauthenticated → 401
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RED-1: PUT /api/profile without JWT → 401")
    void testUnauthorized_ProfileUpdate() throws Exception {
        mockMvc.perform(putWithApiContext("/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"realName":"Test","phone":"13800000001"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RED-1b: PUT /api/profile/password without JWT → 401")
    void testUnauthorized_PasswordChange() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/password")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"admin123","newPassword":"newSecure123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-5: Wrong old password → 400
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RED-5: PUT /api/profile/password with wrong oldPassword → 400")
    void testChangePassword_WrongOldPassword() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/password")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"wrongPassword","newPassword":"newSecure123"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_OLD_INVALID"))
                .andExpect(jsonPath("$.message").value("旧密码不正确"));
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-6: New password too short → 400 validation error
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RED-6: PUT /api/profile/password with newPassword < 6 chars → 400")
    void testChangePassword_ShortNewPassword() throws Exception {
        mockMvc.perform(putWithApiContext("/profile/password")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"admin123","newPassword":"1234"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- helpers ----

    private MockHttpServletRequestBuilder putWithApiContext(String pathWithinContext) {
        return put("/api" + pathWithinContext).contextPath("/api");
    }
}
