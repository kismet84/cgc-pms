package com.cgcpms.common;

import com.cgcpms.auth.util.JwtUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JwtHttpTestTokenFactory {

    private static final String PASSWORD = "jwt-http-test-password";
    private static final long SURROGATE_BASE = 7_900_000_000_000_000_000L;

    private final JdbcTemplate jdbcTemplate;
    private final JwtUtils jwtUtils;

    public JwtHttpTestTokenFactory(JdbcTemplate jdbcTemplate, JwtUtils jwtUtils) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtUtils = jwtUtils;
    }

    public String generateToken(Long userId, String username, Long tenantId,
                                List<String> roleCodes, List<String> permissions) {
        long effectiveUserId = resolveUserId(userId, tenantId);
        List<String> passwords = jdbcTemplate.query(
                "SELECT password FROM sys_user WHERE id = ? AND tenant_id = ? AND deleted_flag = 0",
                (resultSet, rowNum) -> resultSet.getString(1),
                effectiveUserId, tenantId);
        if (passwords.isEmpty()) {
            jdbcTemplate.update("""
                    INSERT INTO sys_user
                      (id, tenant_id, username, password, real_name, status, is_admin, created_by, deleted_flag, remark)
                    VALUES (?, ?, ?, ?, 'JWT HTTP测试身份', 'ENABLE', 0, 0, 0, 'JWT_HTTP_TEST_FIXTURE')
                    """, effectiveUserId, tenantId, "jwt-http-" + effectiveUserId, PASSWORD);
            passwords = List.of(PASSWORD);
        }
        return jwtUtils.generateToken(effectiveUserId, username, tenantId, roleCodes, permissions,
                jwtUtils.credentialVersion(passwords.getFirst()));
    }

    private long resolveUserId(Long requestedUserId, Long tenantId) {
        List<Long> tenants = jdbcTemplate.query(
                "SELECT tenant_id FROM sys_user WHERE id = ? AND deleted_flag = 0",
                (resultSet, rowNum) -> resultSet.getLong(1),
                requestedUserId);
        if (tenants.isEmpty() || tenants.getFirst().equals(tenantId)) {
            return requestedUserId;
        }
        return SURROGATE_BASE + Math.abs(tenantId % 100_000L) * 1_000_000L
                + Math.abs(requestedUserId % 1_000_000L);
    }
}
