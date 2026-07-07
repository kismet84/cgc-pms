package com.cgcpms.auth.controller;

import com.cgcpms.auth.dto.LoginRequest;
import com.cgcpms.auth.dto.LoginResponse;
import com.cgcpms.auth.dto.UserInfo;
import com.cgcpms.auth.service.AuthService;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.ratelimit.LoginLockoutStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.main.allow-circular-references=true",
        "auth.dev-login.enabled=true",
        "auth.dev-login.default-username=demo_dev_super_admin",
        "auth.csrf.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("AuthController — 登录端点测试")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private LoginLockoutStore lockoutStore;

    @Test
    @DisplayName("POST /auth/login 有效凭据 → 200")
    void testLoginSuccess() throws Exception {
        var userInfo = new UserInfo();
        userInfo.setUsername("admin");
        var loginResponse = new LoginResponse("mock-token", "mock-refresh-token", userInfo);
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);

        mockMvc.perform(post("/api/auth/login")
                        .servletPath("/auth/login")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.userInfo.username").value("admin"));
    }

    @Test
    @DisplayName("GET /auth/userinfo 未认证响应也下发 XSRF-TOKEN cookie")
    void testUnauthenticatedGetIssuesCsrfCookie() throws Exception {
        mockMvc.perform(get("/api/auth/userinfo")
                        .servletPath("/auth/userinfo")
                        .contextPath("/api"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().stringValues("Set-Cookie",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("XSRF-TOKEN="))));
    }

    @Test
    @DisplayName("POST /auth/login 无效凭据 → 400(AUTH_FAILED)")
    void testLoginFail() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException("AUTH_FAILED", "用户名或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .servletPath("/auth/login")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrongpassword"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"));
    }

    @Test
    @DisplayName("GET /auth/dev-login 默认走稳定演示超管账号并设置 HttpOnly cookie")
    void testDevLoginSuccessWithDefaultDevSuperAdmin() throws Exception {
        var userInfo = new UserInfo();
        userInfo.setUsername("demo_dev_super_admin");
        var loginResponse = new LoginResponse("mock-token", "mock-refresh-token", userInfo);
        when(authService.loginByUsernameEnsuringDevAccount(
                eq("demo_dev_super_admin"),
                eq("demo_dev_super_admin"))).thenReturn(loginResponse);

        mockMvc.perform(get("/api/auth/dev-login")
                        .servletPath("/auth/dev-login")
                        .contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.userInfo.username").value("demo_dev_super_admin"))
                .andExpect(jsonPath("$.data.token").value(nullValue()))
                .andExpect(jsonPath("$.data.refreshToken").value(nullValue()))
                .andExpect(header().stringValues("Set-Cookie",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString(CookieUtils.ACCESS_TOKEN_COOKIE + "=mock-token"))))
                .andExpect(header().stringValues("Set-Cookie",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("HttpOnly"))))
                .andExpect(header().stringValues("Set-Cookie",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString(CookieUtils.REFRESH_TOKEN_COOKIE + "=mock-refresh-token"))));
    }

    @Test
    @DisplayName("GET /auth/dev-login 默认账号支持 redirect 且继续设置 cookie")
    void testDevLoginRedirectWithDefaultDevSuperAdmin() throws Exception {
        var userInfo = new UserInfo();
        userInfo.setUsername("demo_dev_super_admin");
        var loginResponse = new LoginResponse("mock-token", "mock-refresh-token", userInfo);
        when(authService.loginByUsernameEnsuringDevAccount(
                eq("demo_dev_super_admin"),
                eq("demo_dev_super_admin"))).thenReturn(loginResponse);

        mockMvc.perform(get("/api/auth/dev-login")
                        .servletPath("/auth/dev-login")
                        .contextPath("/api")
                        .param("redirect", "/alert"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/alert"))
                .andExpect(content().string(""))
                .andExpect(header().stringValues("Set-Cookie",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString(CookieUtils.ACCESS_TOKEN_COOKIE + "=mock-token"))))
                .andExpect(header().stringValues("Set-Cookie",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString(CookieUtils.REFRESH_TOKEN_COOKIE + "=mock-refresh-token"))));
    }

    @Test
    @DisplayName("POST /auth/login 已锁定 IP 仍返回 RATE_LIMIT_EXCEEDED")
    void testFormalLoginStillBlockedByLockout() throws Exception {
        when(lockoutStore.getRemainingLockoutMillis("10.0.0.88")).thenReturn(120_000L);

        mockMvc.perform(post("/api/auth/login")
                        .servletPath("/auth/login")
                        .contextPath("/api")
                        .header("X-Real-IP", "10.0.0.88")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin123"}"""))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("登录失败次数过多")));
    }

    @Test
    @DisplayName("GET /auth/dev-login 已锁定 IP 也不应被登录锁定阻塞")
    void testDevLoginBypassesLockout() throws Exception {
        var userInfo = new UserInfo();
        userInfo.setUsername("demo_dev_super_admin");
        var loginResponse = new LoginResponse("mock-token", "mock-refresh-token", userInfo);
        when(lockoutStore.getRemainingLockoutMillis("10.0.0.88")).thenReturn(120_000L);
        when(authService.loginByUsernameEnsuringDevAccount(
                eq("demo_dev_super_admin"),
                eq("demo_dev_super_admin"))).thenReturn(loginResponse);

        mockMvc.perform(get("/api/auth/dev-login")
                        .servletPath("/auth/dev-login")
                        .contextPath("/api")
                        .header("X-Real-IP", "10.0.0.88")
                        .param("redirect", "/alert"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/alert"))
                .andExpect(header().stringValues("Set-Cookie",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString(CookieUtils.ACCESS_TOKEN_COOKIE + "=mock-token"))))
                .andExpect(header().stringValues("Set-Cookie",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString(CookieUtils.REFRESH_TOKEN_COOKIE + "=mock-refresh-token"))));

        verify(authService).loginByUsernameEnsuringDevAccount("demo_dev_super_admin", "demo_dev_super_admin");
        verify(lockoutStore, never()).clear(any());
        verify(lockoutStore, never()).recordFailure(any(), any(Integer.class), any(Long.class), any(Long.class));
        verifyNoMoreInteractions(lockoutStore);
    }

    @Test
    @DisplayName("dev-login 默认用户名配置已切换到稳定演示超管账号")
    void testDevLoginDefaultUsernameConfigSwitched() throws Exception {
        String devConfig = Files.readString(Path.of("src/main/resources/application-dev.yml"));
        String localConfig = Files.readString(Path.of("src/main/resources/application-local.yml"));

        org.junit.jupiter.api.Assertions.assertTrue(devConfig.contains("default-username: demo_dev_super_admin"));
        org.junit.jupiter.api.Assertions.assertTrue(localConfig.contains("default-username: demo_dev_super_admin"));
        org.junit.jupiter.api.Assertions.assertFalse(devConfig.contains("demo_alert_commercial"));
        org.junit.jupiter.api.Assertions.assertFalse(localConfig.contains("demo_alert_commercial"));
    }

    @Test
    @DisplayName("dev-login controller 仅声明在 dev/local profile 下启用")
    void testDevLoginControllerProfileScope() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/cgcpms/auth/controller/DevAuthController.java"));
        String securityConfigSource = Files.readString(Path.of("src/main/java/com/cgcpms/auth/config/SecurityConfig.java"));
        org.junit.jupiter.api.Assertions.assertTrue(source.contains("@Profile({\"dev\", \"local\"})"));
        org.junit.jupiter.api.Assertions.assertTrue(source.contains("@ConditionalOnProperty"));
        org.junit.jupiter.api.Assertions.assertTrue(source.contains("auth.dev-login.enabled"));
        org.junit.jupiter.api.Assertions.assertFalse(source.contains("@RateLimit"));
        org.junit.jupiter.api.Assertions.assertTrue(securityConfigSource.contains("environment.acceptsProfiles(Profiles.of(\"dev\", \"local\"))"));
        org.junit.jupiter.api.Assertions.assertTrue(securityConfigSource.contains("auth.dev-login.enabled:false"));
    }
}
