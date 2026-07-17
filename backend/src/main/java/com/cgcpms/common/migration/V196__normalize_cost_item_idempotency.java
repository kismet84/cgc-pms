package com.cgcpms.common.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;

/** H2 counterpart of the MySQL V196 cost fact idempotency repair. */
public class V196__normalize_cost_item_idempotency extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var conn = context.getConnection();
        try (PreparedStatement ps = conn.prepareStatement("""
                ALTER TABLE cost_item ADD COLUMN active_unique_token BIGINT
                GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END)
                """)) {
            ps.execute();
        }
        H2SoftDeleteUniqueMigration.dropNamedUniqueConstraint(conn, "cost_item", "uk_cost_source_item");
        H2SoftDeleteUniqueMigration.dropNamedUniqueConstraint(conn, "cost_item", "uk_cost_source");
        dropIndexIfPresent(conn, "idx_cost_item_tenant_source");
        try (PreparedStatement ps = conn.prepareStatement("""
                ALTER TABLE cost_item ADD CONSTRAINT uk_cost_source UNIQUE
                (tenant_id, source_type, source_id, source_item_id, cost_type, active_unique_token)
                """)) {
            ps.execute();
        }
    }

    private void dropIndexIfPresent(java.sql.Connection conn, String name) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("DROP INDEX IF EXISTS \"" + name + "\"")) { // SQL-SAFETY: migration-ddl
            ps.execute();
        }
    }
}
