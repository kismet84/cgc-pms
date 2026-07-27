package com.cgcpms.auth.service;

import com.cgcpms.auth.dto.LoginRequest;
import com.cgcpms.auth.dto.LoginResponse;
import com.cgcpms.auth.util.JwtUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("AuthService — 权限集")
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtUtils jwtUtils;

    @BeforeEach
    void seedAdminUser() {
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
    }

    @Test
    @DisplayName("admin 登录返回二阶段驾驶舱权限")
    void adminLoginReturnsPhase2DashboardPermissions() {
        LoginRequest request = new LoginRequest();
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
}
