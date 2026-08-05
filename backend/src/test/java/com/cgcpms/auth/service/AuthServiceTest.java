package com.cgcpms.auth.service;

import com.cgcpms.auth.dto.LoginRequest;
import com.cgcpms.auth.dto.LoginResponse;
import com.cgcpms.auth.util.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.main.allow-circular-references=true",
        "auth.csrf.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("AuthService — 权限集")
class AuthServiceTest {

    private static final long TENANT_1001 = 1001L;
    private static final long TENANT_USER_ID = 7_201_001L;
    private static final long TENANT_ROLE_ID = 7_201_002L;
    private static final long TENANT_MENU_ID = 7_201_003L;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void seedAdminUser() {
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE tenant_id = ? AND role_id = ?", TENANT_1001, TENANT_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE tenant_id = ? AND user_id = ?", TENANT_1001, TENANT_USER_ID);
        jdbcTemplate.update("DELETE FROM sys_menu WHERE tenant_id = ? AND id = ?", TENANT_1001, TENANT_MENU_ID);
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ? AND id = ?", TENANT_1001, TENANT_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND id = ?", TENANT_1001, TENANT_USER_ID);
        int restored = jdbcTemplate.update("""
                UPDATE sys_user
                SET tenant_id = 0,
                    username = 'admin',
                    password = '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
                    status = 'ENABLE',
                    deleted_flag = 0
                WHERE id = 1
                """);
        if (restored == 0) {
            jdbcTemplate.update("""
                INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark)
                VALUES (1, 0, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', '13800000000', 'admin@cgc-pms.com', 'ENABLE', 1, 1, 'test-seed')
                """);
        }
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (id, user_id, role_id)
                SELECT 1, 1, 1
                WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = 1 AND role_id = 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO sys_user
                    (id, tenant_id, username, password, real_name, status, is_admin, deleted_flag)
                VALUES (?, ?, 'admin', ?, '租户1001管理员', 'ENABLE', 0, 0)
                """, TENANT_USER_ID, TENANT_1001, passwordEncoder.encode("tenant1001-pass"));
        jdbcTemplate.update("""
                INSERT INTO sys_role
                    (id, tenant_id, role_code, role_name, role_type, status, data_scope, deleted_flag)
                VALUES (?, ?, 'TENANT_ADMIN', '租户管理员', 'CUSTOM', 'ENABLE', 'SELF', 0)
                """, TENANT_ROLE_ID, TENANT_1001);
        jdbcTemplate.update("""
                INSERT INTO sys_menu
                    (id, tenant_id, parent_id, menu_name, menu_type, perms, order_num, status, visible, deleted_flag)
                VALUES (?, ?, 0, '租户驾驶舱', 'BUTTON', 'tenant:dashboard:view', 1, 'ENABLE', 1, 0)
                """, TENANT_MENU_ID, TENANT_1001);
        jdbcTemplate.update("INSERT INTO sys_user_role (id, tenant_id, user_id, role_id) VALUES (?, ?, ?, ?)",
                7_201_004L, TENANT_1001, TENANT_USER_ID, TENANT_ROLE_ID);
        jdbcTemplate.update("INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id) VALUES (?, ?, ?, ?)",
                7_201_005L, TENANT_1001, TENANT_ROLE_ID, TENANT_MENU_ID);
    }

    @Test
    @DisplayName("admin 登录返回二阶段驾驶舱权限")
    void adminLoginReturnsPhase2DashboardPermissions() {
        LoginRequest request = new LoginRequest();
        request.setTenantId(0L);
        request.setUsername("admin");
        request.setPassword("admin123");

        LoginResponse response = authService.login(request);

        assertNotNull(response.getUserInfo());
        assertTrue(response.getUserInfo().getPermissions().contains("dashboard:purchase-manager:view"));
        assertTrue(response.getUserInfo().getPermissions().contains("dashboard:production-manager:view"));
    }

    @Test
    @DisplayName("access token 中角色或权限快照过期时拒绝继续授权")
    void staleAuthorizationSnapshotIsRejected() {
        LoginRequest request = new LoginRequest();
        request.setTenantId(0L);
        request.setUsername("admin");
        request.setPassword("admin123");

        LoginResponse response = authService.login(request);
        Claims current = jwtUtils.parseToken(response.getToken());
        assertTrue(authService.isCurrentAuthorization(current));

        String staleToken = jwtUtils.generateToken(
                1L,
                "admin",
                0L,
                List.of("STALE_ROLE"),
                List.of(),
                current.get(JwtUtils.CLAIM_CREDENTIAL_VERSION, String.class));

        assertFalse(authService.isCurrentAuthorization(jwtUtils.parseToken(staleToken)));
    }

    @Test
    @DisplayName("租户0与1001同名用户按显式租户登录并签发隔离快照")
    void sameUsernameAcrossTenantsUsesExplicitTenant() {
        LoginRequest request = new LoginRequest();
        request.setTenantId(TENANT_1001);
        request.setUsername("admin");
        request.setPassword("tenant1001-pass");

        LoginResponse response = authService.login(request);
        Claims claims = jwtUtils.parseToken(response.getToken());

        assertEquals(String.valueOf(TENANT_USER_ID), response.getUserInfo().getUserId());
        assertEquals(TENANT_1001, claims.get(JwtUtils.CLAIM_TENANT_ID, Long.class));
        assertEquals(List.of("TENANT_ADMIN"), response.getUserInfo().getRoles());
        assertEquals(List.of("tenant:dashboard:view"), response.getUserInfo().getPermissions());
        assertTrue(authService.isCurrentCredential(claims));
        assertTrue(authService.isCurrentAuthorization(claims));
    }

    @Test
    @DisplayName("凭据租户错配、禁用用户、密码变化及禁用角色均立即失效")
    void credentialAndAuthorizationChangesFailClosed() {
        LoginRequest request = new LoginRequest();
        request.setTenantId(TENANT_1001);
        request.setUsername("admin");
        request.setPassword("tenant1001-pass");
        Claims claims = jwtUtils.parseToken(authService.login(request).getToken());

        String mismatched = jwtUtils.generateToken(
                TENANT_USER_ID, "admin", 0L, List.of("TENANT_ADMIN"),
                List.of("tenant:dashboard:view"),
                claims.get(JwtUtils.CLAIM_CREDENTIAL_VERSION, String.class));
        assertFalse(authService.isCurrentCredential(jwtUtils.parseToken(mismatched)));

        jdbcTemplate.update("UPDATE sys_role SET status = 'DISABLE' WHERE tenant_id = ? AND id = ?",
                TENANT_1001, TENANT_ROLE_ID);
        assertFalse(authService.isCurrentAuthorization(claims));

        jdbcTemplate.update("UPDATE sys_user SET status = 'DISABLE' WHERE tenant_id = ? AND id = ?",
                TENANT_1001, TENANT_USER_ID);
        assertFalse(authService.isCurrentCredential(claims));
        jdbcTemplate.update("UPDATE sys_user SET status = 'ENABLE' WHERE tenant_id = ? AND id = ?",
                TENANT_1001, TENANT_USER_ID);

        jdbcTemplate.update("UPDATE sys_user SET password = ? WHERE tenant_id = ? AND id = ?",
                passwordEncoder.encode("changed-pass"), TENANT_1001, TENANT_USER_ID);
        assertFalse(authService.isCurrentCredential(claims));
    }

    @Test
    @DisplayName("租户1001通过真实Servlet链完成登录、刷新、受保护读取和注销")
    void tenantLifecycleRunsThroughHttpSecurityAndDatabase() throws Exception {
        MvcResult login = mockMvc.perform(postWithApiContext("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":1001,"username":"admin","password":"tenant1001-pass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.userInfo.userId").value(String.valueOf(TENANT_USER_ID)))
                .andExpect(jsonPath("$.data.userInfo.roles[0]").value("TENANT_ADMIN"))
                .andReturn();

        Cookie access = responseCookie(login, "access_token");
        Cookie refresh = responseCookie(login, "refresh_token");

        MvcResult userInfo = mockMvc.perform(getWithApiContext("/auth/userinfo").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(String.valueOf(TENANT_USER_ID)))
                .andExpect(jsonPath("$.data.permissions[0]").value("tenant:dashboard:view"))
                .andReturn();
        Cookie csrf = userInfo.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrf);

        MvcResult rotated = mockMvc.perform(postWithApiContext("/auth/refresh")
                        .cookie(access, refresh, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userInfo.userId").value(String.valueOf(TENANT_USER_ID)))
                .andReturn();
        Cookie rotatedAccess = responseCookie(rotated, "access_token");
        Cookie rotatedRefresh = responseCookie(rotated, "refresh_token");
        assertNotEquals(access.getValue(), rotatedAccess.getValue());
        assertNotEquals(refresh.getValue(), rotatedRefresh.getValue());

        mockMvc.perform(getWithApiContext("/auth/userinfo").cookie(rotatedAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(String.valueOf(TENANT_USER_ID)));

        MvcResult logout = mockMvc.perform(postWithApiContext("/auth/logout")
                        .cookie(rotatedAccess, rotatedRefresh, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(logout.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(value -> value.startsWith("access_token=;") && value.contains("Max-Age=0")));
        assertTrue(logout.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(value -> value.startsWith("refresh_token=;") && value.contains("Max-Age=0")));

        mockMvc.perform(postWithApiContext("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":0,"username":"admin","password":"tenant1001-pass"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"));
    }

    private Cookie responseCookie(MvcResult result, String name) {
        String prefix = name + "=";
        String header = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith(prefix))
                .findFirst()
                .orElseThrow();
        return new Cookie(name, header.substring(prefix.length(), header.indexOf(';')));
    }

    private MockHttpServletRequestBuilder getWithApiContext(String pathWithinContext) {
        return get("/api" + pathWithinContext)
                .contextPath("/api")
                .servletPath(pathWithinContext);
    }

    private MockHttpServletRequestBuilder postWithApiContext(String pathWithinContext) {
        return post("/api" + pathWithinContext)
                .contextPath("/api")
                .servletPath(pathWithinContext);
    }
}
