package com.cgcpms.common.migration;

import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * V75: 将 19 个表的唯一约束全部加入 deleted_flag（软删除安全）。
 * <p>
 * 为每个旧约束收集 → 删除 → 重建。Java 迁移直接操作 JDBC Connection，
 * 纯 SQL 方案中的 H2 CREATE ALIAS + javac 被完全消除。
 * </p>
 *
 * <p><b>幂等性: 此迁移设计为在全新/一致状态下运行。</b>
 * 如果中途失败，已 DROP 的约束不会自动回滚（DDL 自动提交于每个循环）。
 * 失败后请手动检查受影响的表并重新运行迁移。</p>
 */
public class V75__soft_delete_safe_unique_constraints extends BaseJavaMigration {

    private static final String[][] TABLES = {
        {"sys_user",          "ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_username (tenant_id, username, deleted_flag)"},
        {"sys_role",          "ALTER TABLE sys_role ADD UNIQUE KEY uk_sys_role_code (tenant_id, role_code, deleted_flag)"},
        {"md_partner",        "ALTER TABLE md_partner ADD UNIQUE KEY uk_md_partner_code (tenant_id, partner_code, deleted_flag)"},
        {"ct_contract",       "ALTER TABLE ct_contract ADD UNIQUE KEY uk_ct_contract_code (tenant_id, contract_code, deleted_flag)"},
        {"ct_contract_change","ALTER TABLE ct_contract_change ADD UNIQUE KEY uk_ct_change_code (tenant_id, change_code, deleted_flag)"},
        {"org_company",       "ALTER TABLE org_company ADD UNIQUE KEY uk_oc_tenant_code (tenant_id, company_code, deleted_flag)"},
        {"org_position",      "ALTER TABLE org_position ADD UNIQUE KEY uk_op_tenant_code (tenant_id, position_code, deleted_flag)"},
        {"pay_application",   "ALTER TABLE pay_application ADD UNIQUE KEY uk_pay_application_code (tenant_id, apply_code, deleted_flag)"},
        {"md_material",       "ALTER TABLE md_material ADD UNIQUE KEY uk_md_material_code (tenant_id, material_code, deleted_flag)"},
        {"mat_purchase_order","ALTER TABLE mat_purchase_order ADD UNIQUE KEY uk_mat_po_code (tenant_id, order_code, deleted_flag)"},
        {"mat_receipt",       "ALTER TABLE mat_receipt ADD UNIQUE KEY uk_mat_receipt_code (tenant_id, receipt_code, deleted_flag)"},
        {"sub_task",          "ALTER TABLE sub_task ADD UNIQUE KEY uk_sub_task_code (tenant_id, task_code, deleted_flag)"},
        {"sub_measure",       "ALTER TABLE sub_measure ADD UNIQUE KEY uk_sub_measure_code (tenant_id, measure_code, deleted_flag)"},
        {"var_order",         "ALTER TABLE var_order ADD UNIQUE KEY uk_var_order_code (tenant_id, var_code, deleted_flag)"},
        {"wf_template",       "ALTER TABLE wf_template ADD UNIQUE KEY uk_wf_template_code (tenant_id, template_code, deleted_flag)"},
        {"wf_instance",       "ALTER TABLE wf_instance ADD UNIQUE KEY uk_wf_instance_business (business_type, business_id, deleted_flag)"},
        {"cost_summary",      "ALTER TABLE cost_summary ADD UNIQUE KEY uk_cost_summary (project_id, summary_date, cost_subject_id, deleted_flag)"},
    };

    @Override
    public void migrate(Context context) throws Exception {
        java.sql.Connection conn = context.getConnection();

        // 批量表：每个表收集 → 删除 → 重建单个唯一约束
        for (String[] entry : TABLES) {
            String tableName = entry[0];
            String addSql = entry[1];
            H2SoftDeleteUniqueMigration.rebuildUniqueConstraints(conn, tableName, addSql);
        }

        // stl_settlement 特殊处理: 有多余的 named index
        try (Statement st = conn.createStatement()) {
            st.execute("DROP INDEX IF EXISTS uk_stl_settlement_contract");
        }
        H2SoftDeleteUniqueMigration.rebuildUniqueConstraints(conn, "stl_settlement",
            "ALTER TABLE stl_settlement ADD UNIQUE KEY uk_stl_settlement_code_del (tenant_id, settlement_code, deleted_flag)",
            "ALTER TABLE stl_settlement ADD UNIQUE KEY uk_stl_settlement_contract_del (tenant_id, contract_id, deleted_flag)"
        );

        // pay_record: named UNIQUE INDEX
        try (Statement st = conn.createStatement()) {
            st.execute("DROP INDEX IF EXISTS uk_external_txn_no");
            st.execute("CREATE UNIQUE INDEX uk_external_txn_no ON pay_record(external_txn_no, deleted_flag)");
        }
    }
}
