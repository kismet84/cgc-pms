package com.cgcpms.system.controller;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.sql.ResultSet;

/**
 * System-level management endpoints. Restricted to SUPER_ADMIN only.
 */
@Slf4j
@RestController
@Profile("!prod")
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {

    private final JdbcTemplate jdbcTemplate;
    static final String CLEAR_DATABASE_CONFIRMATION = "CLEAR_NON_PROD_DATABASE";
    private static final Pattern MYSQL_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");

    /** Tables to KEEP (not cleared): system tables and Flyway history */
    private static final List<String> PROTECTED_TABLES = List.of(
            "flyway_schema_history",
            "sys_user",
            "sys_role",
            "sys_menu",
            "sys_user_role",
            "sys_role_menu",
            "sys_dict_type",
            "sys_dict_data",
            "sys_user_preference"
    );

    /**
     * Clear all business data. Preserves system tables (users, roles, menus, dicts).
     * Only SUPER_ADMIN can execute this.
     */
    @DeleteMapping("/clear-database")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<String> clearDatabase(@RequestParam String confirm) {
        if (!CLEAR_DATABASE_CONFIRMATION.equals(confirm)) {
            log.warn("Rejected clear database request because confirmation code did not match");
            throw new BusinessException("CONFIRM_REQUIRED", "需要显式确认清空非生产数据库");
        }

        log.warn("SUPER_ADMIN clearing database...");

        ClearResult result = jdbcTemplate.execute((ConnectionCallback<ClearResult>) connection -> {
            List<String> failures = new ArrayList<>();
            int cleared = 0;
            try (var statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                try (ResultSet rows = statement.executeQuery(
                        "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()")) {
                    while (rows.next()) {
                        String table = rows.getString(1);
                        if (PROTECTED_TABLES.contains(table)) continue;
                        try {
                            statement.execute("TRUNCATE TABLE " + quoteIdentifier(table));
                            cleared++;
                        } catch (Exception ex) {
                            failures.add(table);
                            log.warn("Failed to truncate table {}", table, ex);
                        }
                    }
                }
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
            return new ClearResult(cleared, failures);
        });
        if (!result.failures().isEmpty()) {
            throw new BusinessException("CLEAR_DATABASE_PARTIAL_FAILURE",
                    "数据库清理部分失败，已停止报告成功，失败表: " + String.join(",", result.failures()));
        }
        String msg = "已清空 " + result.cleared() + " 张业务数据表，系统表已保留";
        log.info(msg);
        return ApiResponse.success(msg);
    }

    private String quoteIdentifier(String table) {
        if (table == null || !MYSQL_IDENTIFIER.matcher(table).matches()) {
            throw new BusinessException("INVALID_TABLE_NAME", "数据库表名非法");
        }
        return "`" + table + "`";
    }

    private record ClearResult(int cleared, List<String> failures) { }
}
