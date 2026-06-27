package com.cgcpms.common.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * V88: H2 迁移 — 为 14 个表添加 deleted_token 列并将唯一约束从 deleted_flag 改为 deleted_token。
 * <p>
 * 对应 MySQL V88__soft_delete_token_based_unique.sql。
 * 模式：删除时写入 deleted_token = 记录 ID，允许无限次删除重建。
 * 仅活动行唯一（deleted_token IS NULL）。
 * </p>
 */
public class V88__h2_soft_delete_token_unique extends BaseJavaMigration {

    private static final String[][] TABLES = {
        {"sys_user",           "ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_username (tenant_id, username, deleted_token)"},
        {"sys_role",           "ALTER TABLE sys_role ADD UNIQUE KEY uk_sys_role_code (tenant_id, role_code, deleted_token)"},
        {"md_partner",         "ALTER TABLE md_partner ADD UNIQUE KEY uk_md_partner_code (tenant_id, partner_code, deleted_token)"},
        {"ct_contract",        "ALTER TABLE ct_contract ADD UNIQUE KEY uk_ct_contract_code (tenant_id, contract_code, deleted_token)"},
        {"ct_contract_change", "ALTER TABLE ct_contract_change ADD UNIQUE KEY uk_ct_change_code (tenant_id, change_code, deleted_token)"},
        {"org_company",        "ALTER TABLE org_company ADD UNIQUE KEY uk_oc_tenant_code (tenant_id, company_code, deleted_token)"},
        {"org_position",       "ALTER TABLE org_position ADD UNIQUE KEY uk_op_tenant_code (tenant_id, position_code, deleted_token)"},
        {"pay_application",    "ALTER TABLE pay_application ADD UNIQUE KEY uk_pay_application_code (tenant_id, apply_code, deleted_token)"},
        {"md_material",        "ALTER TABLE md_material ADD UNIQUE KEY uk_md_material_code (tenant_id, material_code, deleted_token)"},
        {"mat_purchase_order", "ALTER TABLE mat_purchase_order ADD UNIQUE KEY uk_mat_po_code (tenant_id, order_code, deleted_token)"},
        {"mat_receipt",        "ALTER TABLE mat_receipt ADD UNIQUE KEY uk_mat_receipt_code (tenant_id, receipt_code, deleted_token)"},
        {"mat_stock",          "ALTER TABLE mat_stock ADD UNIQUE KEY uk_ms_warehouse_material (warehouse_id, material_id, deleted_token)"},
        {"pm_project",         "ALTER TABLE pm_project ADD UNIQUE KEY uk_pm_project_code (tenant_id, project_code, deleted_token)"},
        {"cost_subject",       "ALTER TABLE cost_subject ADD UNIQUE KEY uk_cost_subject_code (tenant_id, subject_code, deleted_token)"}
    };

    @Override
    public void migrate(Context context) throws Exception {
        var conn = context.getConnection();

        // 1. 为所有 14 个表添加 deleted_token BIGINT 列
        for (String[] entry : TABLES) {
            String tableName = entry[0];
            String sql = "ALTER TABLE " + quoteIdentifier(tableName) + " ADD COLUMN deleted_token BIGINT DEFAULT NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.execute();
            }
        }

        // 2. 为每个表重建唯一约束（用 deleted_token 替换 deleted_flag）
        //    rebuildUniqueConstraints 会收集当前表所有 UNIQUE 约束 → 删除 → 重建新约束
        for (String[] entry : TABLES) {
            String tableName = entry[0];
            String addSql = entry[1];
            H2SoftDeleteUniqueMigration.rebuildUniqueConstraints(conn, tableName, addSql);
        }
    }

    private String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
