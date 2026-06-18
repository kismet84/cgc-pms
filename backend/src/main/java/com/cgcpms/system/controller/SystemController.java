package com.cgcpms.system.controller;

import com.cgcpms.common.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * System-level management endpoints. Restricted to SUPER_ADMIN only.
 */
@Slf4j
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {

    private final JdbcTemplate jdbcTemplate;

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
    public ApiResponse<String> clearDatabase() {
        log.warn("SUPER_ADMIN clearing database...");

        // Disable FK checks for TRUNCATE — ensure re-enable in finally
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        int cleared = 0;
        try {
            List<String> tables = jdbcTemplate.queryForList(
                    "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()", String.class);

            for (String table : tables) {
                if (PROTECTED_TABLES.contains(table)) {
                    log.info("Skipping protected table: {}", table);
                    continue;
                }
                try {
                    jdbcTemplate.execute("TRUNCATE TABLE `" + table + "`");
                    cleared++;
                    log.info("Truncated table: {}", table);
                } catch (Exception e) {
                    log.warn("Failed to truncate table {}: {}", table, e.getMessage());
                }
            }
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }

        String msg = "已清空 " + cleared + " 张业务数据表，系统表已保留";
        log.info(msg);
        return ApiResponse.success(msg);
    }
}
