package com.cgcpms.auth.controller;

import com.cgcpms.auth.config.JwtProperties;
import com.cgcpms.auth.dto.LoginRequest;
import com.cgcpms.auth.dto.LoginResponse;
import com.cgcpms.auth.dto.UserInfo;
import com.cgcpms.auth.service.AuthService;
import com.cgcpms.auth.service.TokenBlacklistService;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.ratelimit.LoginLockoutStore;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
@ExtendWith(OutputCaptureExtension.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
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
    @DisplayName("JWT 已认证 GET 不应清掉现有 XSRF-TOKEN")
    void authenticatedGetDoesNotClearExistingCsrfCookie() throws Exception {
        var userInfo = new UserInfo();
        userInfo.setUsername("demo_dev_super_admin");
        when(authService.getUserInfo(1L)).thenReturn(userInfo);

        String token = jwtUtils.generateToken(1L, "demo_dev_super_admin", 0L,
                List.of("SUPER_ADMIN"), List.of("inventory:transaction:add"));

        mockMvc.perform(get("/api/auth/userinfo")
                        .servletPath("/auth/userinfo")
                        .contextPath("/api")
                        .cookie(new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token))
                        .cookie(new Cookie("XSRF-TOKEN", "stable-csrf-token")))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie",
                        not(hasItem(containsString("XSRF-TOKEN=;")))));
    }

    @Test
    @DisplayName("POST /auth/login 无效凭据 → 400(AUTH_FAILED)")
    void testLoginFail() throws Exception {
        double before = loginFailureCount("AUTH_FAILED");
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
        org.junit.jupiter.api.Assertions.assertEquals(before + 1,
                loginFailureCount("AUTH_FAILED"), 0.001);
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
    @DisplayName("GET /auth/dev-login 保留 cash-journal 站内跳转")
    void testDevLoginRedirectToCashJournal() throws Exception {
        var userInfo = new UserInfo();
        userInfo.setUsername("demo_dev_super_admin");
        var loginResponse = new LoginResponse("mock-token", "mock-refresh-token", userInfo);
        when(authService.loginByUsernameEnsuringDevAccount(
                eq("demo_dev_super_admin"),
                eq("demo_dev_super_admin"))).thenReturn(loginResponse);

        mockMvc.perform(get("/api/auth/dev-login")
                        .servletPath("/auth/dev-login")
                        .contextPath("/api")
                        .param("redirect", "/cash-journal"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/cash-journal"));
    }

    @Test
    @DisplayName("GET /auth/dev-login 保留产值计量站内跳转")
    void testDevLoginRedirectToProductionMeasurement() throws Exception {
        var userInfo = new UserInfo();
        userInfo.setUsername("demo_dev_super_admin");
        var loginResponse = new LoginResponse("mock-token", "mock-refresh-token", userInfo);
        when(authService.loginByUsernameEnsuringDevAccount(
                eq("demo_dev_super_admin"),
                eq("demo_dev_super_admin"))).thenReturn(loginResponse);

        mockMvc.perform(get("/api/auth/dev-login")
                        .servletPath("/auth/dev-login")
                        .contextPath("/api")
                        .param("redirect", "/production-measurement"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/production-measurement"));
    }

    @Test
    @DisplayName("GET /auth/dev-login 允许现场日报直达且拒绝不安全跳转")
    void testDevLoginRedirectToSiteDailyLogKeepsSecurityBoundary() throws Exception {
        when(authService.loginByUsernameEnsuringDevAccount(
                eq("demo_dev_super_admin"),
                eq("demo_dev_super_admin"))).thenAnswer(invocation -> {
                    var userInfo = new UserInfo();
                    userInfo.setUsername("demo_dev_super_admin");
                    return new LoginResponse("mock-token", "mock-refresh-token", userInfo);
                });

        mockMvc.perform(get("/api/auth/dev-login")
                        .servletPath("/auth/dev-login")
                        .contextPath("/api")
                        .param("redirect", "/site/daily-log"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/site/daily-log"));
        mockMvc.perform(get("/api/auth/dev-login")
                        .servletPath("/auth/dev-login")
                        .contextPath("/api")
                        .param("redirect", "//evil.example"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/"));
        mockMvc.perform(get("/api/auth/dev-login")
                        .servletPath("/auth/dev-login")
                        .contextPath("/api")
                        .param("redirect", "https://evil.example/path"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/"));
        mockMvc.perform(get("/api/auth/dev-login")
                        .servletPath("/auth/dev-login")
                        .contextPath("/api")
                        .param("redirect", "/site/../system"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/"));
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

    @Test
    @DisplayName("POST /auth/refresh prod 黑名单服务缺失 → fail-close")
    void refreshFailsClosedWhenBlacklistMissingInProd(CapturedOutput output) {
        AuthController controller = authControllerWithoutBlacklistInProd();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieUtils.REFRESH_TOKEN_COOKIE, jwtUtils.generateRefreshToken(1L)));

        var result = controller.refresh(request, new MockHttpServletResponse(), null);

        org.junit.jupiter.api.Assertions.assertEquals("AUTH_TOKEN_INVALID", result.getCode());
        org.junit.jupiter.api.Assertions.assertTrue(output.getOut().contains("BLACKLIST_UNAVAILABLE"));
        org.junit.jupiter.api.Assertions.assertFalse(output.getOut().contains("redis://"));
        org.junit.jupiter.api.Assertions.assertFalse(output.getOut().contains("REDIS_PASSWORD"));
        verify(authService, never()).loginById(any());
    }

    @Test
    @DisplayName("POST /auth/logout prod 黑名单服务缺失 → fail-close")
    void logoutFailsClosedWhenBlacklistMissingInProd() {
        AuthController controller = authControllerWithoutBlacklistInProd();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(1L, "admin", 0L, List.of("ADMIN"), List.of())));

        var result = controller.logout(request, new MockHttpServletResponse());

        org.junit.jupiter.api.Assertions.assertEquals("AUTH_TOKEN_INVALID", result.getCode());
    }

    @Test
    @DisplayName("POST /auth/refresh prod 黑名单写入失败 → fail-close")
    void refreshFailsClosedWhenBlacklistWriteFailsInProd(CapturedOutput output) {
        TokenBlacklistService blacklistService = mock(TokenBlacklistService.class);
        when(blacklistService.isBlacklisted(any())).thenReturn(false);
        when(blacklistService.blacklist(any(), anyLong())).thenReturn(false);
        AuthController controller = authControllerInProd(blacklistService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieUtils.REFRESH_TOKEN_COOKIE, jwtUtils.generateRefreshToken(1L)));

        var result = controller.refresh(request, new MockHttpServletResponse(), null);

        org.junit.jupiter.api.Assertions.assertEquals("AUTH_TOKEN_INVALID", result.getCode());
        org.junit.jupiter.api.Assertions.assertTrue(output.getOut().contains("TOKEN_BLACKLIST_WRITE_FAILED"));
        org.junit.jupiter.api.Assertions.assertFalse(output.getOut().contains("redis://"));
        org.junit.jupiter.api.Assertions.assertFalse(output.getOut().contains("REDIS_PASSWORD"));
        verify(authService, never()).loginById(any());
    }

    private AuthController authControllerWithoutBlacklistInProd() {
        return authControllerInProd(null);
    }

    @SuppressWarnings("unchecked")
    private AuthController authControllerInProd(TokenBlacklistService blacklistService) {
        ObjectProvider<TokenBlacklistService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(blacklistService);
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        ObjectProvider<MeterRegistry> metricsProvider = mock(ObjectProvider.class);
        when(metricsProvider.getIfAvailable()).thenReturn(meterRegistry);
        return new AuthController(authService, jwtUtils, jwtProperties, new CookieUtils(), provider, environment, metricsProvider);
    }

    private double loginFailureCount(String code) {
        var counter = meterRegistry.find("auth.login.failures").tag("code", code).counter();
        return counter == null ? 0 : counter.count();
    }
}
