package com.cgcpms.common.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * V83: repair H2 anonymous unique constraints left by earlier SQL migrations.
 * <p>
 * H2 assigns generated names to UNIQUE(...) constraints created inside CREATE
 * TABLE. Static SQL migrations V50/V82 can add the desired deleted_flag-aware
 * keys, but cannot reliably drop the generated old keys. This Java migration
 * rebuilds the affected constraints after all H2 SQL migrations have run.
 */
public class V83__repair_h2_anonymous_soft_delete_unique_constraints extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        java.sql.Connection conn = context.getConnection();
        H2SoftDeleteUniqueMigration.rebuildUniqueConstraints(conn, "mat_stock",
                "ALTER TABLE mat_stock ADD UNIQUE KEY uk_ms_warehouse_material (warehouse_id, material_id, deleted_flag)");
        H2SoftDeleteUniqueMigration.rebuildUniqueConstraints(conn, "pay_invoice",
                "ALTER TABLE pay_invoice ADD UNIQUE KEY uk_pi_tenant_invoice_no_del (tenant_id, invoice_no, deleted_flag)");
    }
}
