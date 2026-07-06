package com.cgcpms.common.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * H2 counterpart of V131 MySQL migration.
 * <p>
 * H2 test schema reaches V88 with UNIQUE(warehouse_id, material_id, deleted_token),
 * which still permits duplicate active rows because deleted_token is NULL.
 * Rebuild mat_stock uniqueness with a generated active token matching MySQL.
 */
public class V131__fix_mat_stock_active_unique_constraint extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        var conn = context.getConnection();

        if (!columnExists(conn)) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    ALTER TABLE mat_stock
                    ADD COLUMN active_unique_token BIGINT
                    GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END)
                    """)) {
                ps.execute();
            }
        }

        H2SoftDeleteUniqueMigration.rebuildUniqueConstraints(conn, "mat_stock",
                "ALTER TABLE mat_stock ADD UNIQUE KEY uk_ms_warehouse_material " +
                        "(tenant_id, warehouse_id, material_id, active_unique_token)");
    }

    private boolean columnExists(java.sql.Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT 1
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_SCHEMA) = 'PUBLIC'
                  AND UPPER(TABLE_NAME) = 'MAT_STOCK'
                  AND UPPER(COLUMN_NAME) = 'ACTIVE_UNIQUE_TOKEN'
                """);
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }
}
