package com.cgcpms.common.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;

/** H2 counterpart of the MySQL V195 active-row uniqueness repair. */
public class V195__repair_active_soft_delete_uniqueness extends BaseJavaMigration {

    private static final String[][] TABLES = {
            {"sys_user", "uk_sys_user_username", "tenant_id, username, active_unique_token"},
            {"sys_role", "uk_sys_role_code", "tenant_id, role_code, active_unique_token"},
            {"md_partner", "uk_md_partner_code", "tenant_id, partner_code, active_unique_token"},
            {"ct_contract", "uk_ct_contract_code", "tenant_id, contract_code, active_unique_token"},
            {"ct_contract_change", "uk_ct_change_code", "tenant_id, change_code, active_unique_token"},
            {"org_company", "uk_oc_tenant_code", "tenant_id, company_code, active_unique_token"},
            {"org_position", "uk_op_tenant_code", "tenant_id, position_code, active_unique_token"},
            {"pay_application", "uk_pay_application_code", "tenant_id, apply_code, active_unique_token"},
            {"md_material", "uk_md_material_code", "tenant_id, material_code, active_unique_token"},
            {"mat_purchase_order", "uk_mat_po_code", "tenant_id, order_code, active_unique_token"},
            {"mat_receipt", "uk_mat_receipt_code", "tenant_id, receipt_code, active_unique_token"},
            {"pm_project", "uk_pm_project_code", "tenant_id, project_code, active_unique_token"},
            {"cost_subject", "uk_cost_subject_code", "tenant_id, subject_code, active_unique_token"}
    };

    @Override
    public void migrate(Context context) throws Exception {
        var conn = context.getConnection();
        disableLegacyCostSubjectSeeds(conn);
        for (String[] entry : TABLES) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "ALTER TABLE \"" + entry[0] + "\" ADD COLUMN active_unique_token BIGINT " +
                            "GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END)")) { // SQL-SAFETY: migration-ddl
                ps.execute();
            }
            H2SoftDeleteUniqueMigration.dropNamedUniqueConstraint(conn, entry[0], entry[1]);
            try (PreparedStatement ps = conn.prepareStatement(
                    "ALTER TABLE \"" + entry[0] + "\" ADD CONSTRAINT \"" + entry[1] +
                            "\" UNIQUE (" + entry[2] + ")")) { // SQL-SAFETY: migration-ddl
                ps.execute();
            }
        }
    }

    private void disableLegacyCostSubjectSeeds(java.sql.Connection conn) throws Exception {
        String[][] seeds = {
                {"900000", "COST_ROOT", "LEGACY_STANDARD_COST_ROOT"},
                {"900002", "6001", "LEGACY_CONTRACT_REVENUE"},
                {"900007", "6001.01", "LEGACY_CONSTRUCTION_REVENUE"}
        };
        for (String[] seed : seeds) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE cost_subject SET subject_code=?, status='DISABLE', updated_at=CURRENT_TIMESTAMP " +
                            "WHERE id=? AND tenant_id=0 AND subject_code=? AND deleted_flag=0")) {
                ps.setString(1, seed[2]);
                ps.setLong(2, Long.parseLong(seed[0]));
                ps.setString(3, seed[1]);
                ps.executeUpdate();
            }
        }
    }
}
