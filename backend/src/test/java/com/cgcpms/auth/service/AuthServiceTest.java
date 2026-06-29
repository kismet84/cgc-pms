package com.cgcpms.auth.service;

import com.cgcpms.auth.dto.LoginRequest;
import com.cgcpms.auth.dto.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

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

    @BeforeEach
    void seedAdminUser() {
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark)
                SELECT 1, 0, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', '13800000000', 'admin@cgc-pms.com', 'ENABLE', 1, 1, 'test-seed'
                WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 1)
                """);
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
}
