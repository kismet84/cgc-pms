-- V75__soft_delete_safe_unique_constraints.sql
-- H2 version: Drop auto-generated unique constraints and re-add with deleted_flag.
-- H2 auto-generates constraint names for UNIQUE(cols) in CREATE TABLE; we must
-- query INFORMATION_SCHEMA to find and drop them via Java alias (same as V51/V58).
-- NOTE: Table names must match H2's stored case (lowercase with DATABASE_TO_LOWER=TRUE).
SET MODE MySQL;

-- 1. sys_user
CREATE ALIAS IF NOT EXISTS DROP_SYS_USER_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='SYS_USER' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE SYS_USER DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_SYS_USER_UNIQUE();
DROP ALIAS DROP_SYS_USER_UNIQUE;
ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_username (tenant_id, username, deleted_flag);

-- 2. sys_role
CREATE ALIAS IF NOT EXISTS DROP_SYS_ROLE_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='SYS_ROLE' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE SYS_ROLE DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_SYS_ROLE_UNIQUE();
DROP ALIAS DROP_SYS_ROLE_UNIQUE;
ALTER TABLE sys_role ADD UNIQUE KEY uk_sys_role_code (tenant_id, role_code, deleted_flag);

-- 3. md_partner
CREATE ALIAS IF NOT EXISTS DROP_MD_PARTNER_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='MD_PARTNER' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE MD_PARTNER DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_MD_PARTNER_UNIQUE();
DROP ALIAS DROP_MD_PARTNER_UNIQUE;
ALTER TABLE md_partner ADD UNIQUE KEY uk_md_partner_code (tenant_id, partner_code, deleted_flag);

-- 4. ct_contract
CREATE ALIAS IF NOT EXISTS DROP_CT_CONTRACT_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='CT_CONTRACT' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE CT_CONTRACT DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_CT_CONTRACT_UNIQUE();
DROP ALIAS DROP_CT_CONTRACT_UNIQUE;
ALTER TABLE ct_contract ADD UNIQUE KEY uk_ct_contract_code (tenant_id, contract_code, deleted_flag);

-- 5. ct_contract_change
CREATE ALIAS IF NOT EXISTS DROP_CT_CHANGE_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='CT_CONTRACT_CHANGE' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE CT_CONTRACT_CHANGE DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_CT_CHANGE_UNIQUE();
DROP ALIAS DROP_CT_CHANGE_UNIQUE;
ALTER TABLE ct_contract_change ADD UNIQUE KEY uk_ct_change_code (tenant_id, change_code, deleted_flag);

-- 6. org_company
CREATE ALIAS IF NOT EXISTS DROP_ORG_COMPANY_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='ORG_COMPANY' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE ORG_COMPANY DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_ORG_COMPANY_UNIQUE();
DROP ALIAS DROP_ORG_COMPANY_UNIQUE;
ALTER TABLE org_company ADD UNIQUE KEY uk_oc_tenant_code (tenant_id, company_code, deleted_flag);

-- 7. org_position
CREATE ALIAS IF NOT EXISTS DROP_ORG_POSITION_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='ORG_POSITION' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE ORG_POSITION DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_ORG_POSITION_UNIQUE();
DROP ALIAS DROP_ORG_POSITION_UNIQUE;
ALTER TABLE org_position ADD UNIQUE KEY uk_op_tenant_code (tenant_id, position_code, deleted_flag);

-- 8. pay_application
CREATE ALIAS IF NOT EXISTS DROP_PAY_APP_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='PAY_APPLICATION' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE PAY_APPLICATION DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_PAY_APP_UNIQUE();
DROP ALIAS DROP_PAY_APP_UNIQUE;
ALTER TABLE pay_application ADD UNIQUE KEY uk_pay_application_code (tenant_id, apply_code, deleted_flag);

-- 9. md_material
CREATE ALIAS IF NOT EXISTS DROP_MD_MATERIAL_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='MD_MATERIAL' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE MD_MATERIAL DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_MD_MATERIAL_UNIQUE();
DROP ALIAS DROP_MD_MATERIAL_UNIQUE;
ALTER TABLE md_material ADD UNIQUE KEY uk_md_material_code (tenant_id, material_code, deleted_flag);

-- 10. mat_purchase_order
CREATE ALIAS IF NOT EXISTS DROP_MAT_PO_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='MAT_PURCHASE_ORDER' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE MAT_PURCHASE_ORDER DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_MAT_PO_UNIQUE();
DROP ALIAS DROP_MAT_PO_UNIQUE;
ALTER TABLE mat_purchase_order ADD UNIQUE KEY uk_mat_po_code (tenant_id, order_code, deleted_flag);

-- 11. mat_receipt
CREATE ALIAS IF NOT EXISTS DROP_MAT_RECEIPT_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='MAT_RECEIPT' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE MAT_RECEIPT DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_MAT_RECEIPT_UNIQUE();
DROP ALIAS DROP_MAT_RECEIPT_UNIQUE;
ALTER TABLE mat_receipt ADD UNIQUE KEY uk_mat_receipt_code (tenant_id, receipt_code, deleted_flag);

-- 12. sub_task
CREATE ALIAS IF NOT EXISTS DROP_SUB_TASK_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='SUB_TASK' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE SUB_TASK DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_SUB_TASK_UNIQUE();
DROP ALIAS DROP_SUB_TASK_UNIQUE;
ALTER TABLE sub_task ADD UNIQUE KEY uk_sub_task_code (tenant_id, task_code, deleted_flag);

-- 13. sub_measure
CREATE ALIAS IF NOT EXISTS DROP_SUB_MEASURE_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='SUB_MEASURE' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE SUB_MEASURE DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_SUB_MEASURE_UNIQUE();
DROP ALIAS DROP_SUB_MEASURE_UNIQUE;
ALTER TABLE sub_measure ADD UNIQUE KEY uk_sub_measure_code (tenant_id, measure_code, deleted_flag);

-- 14. var_order
CREATE ALIAS IF NOT EXISTS DROP_VAR_ORDER_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='VAR_ORDER' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE VAR_ORDER DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_VAR_ORDER_UNIQUE();
DROP ALIAS DROP_VAR_ORDER_UNIQUE;
ALTER TABLE var_order ADD UNIQUE KEY uk_var_order_code (tenant_id, var_code, deleted_flag);

-- 15. stl_settlement — has 2 UNIQUE constraints (V12 nameless + V52 named)
CREATE ALIAS IF NOT EXISTS DROP_STL_ALL_UNIQUES AS $$
String dropAllUniques(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='STL_SETTLEMENT' AND CONSTRAINT_TYPE='UNIQUE'");
    while (rs.next()) {
        conn.createStatement().execute("ALTER TABLE STL_SETTLEMENT DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_STL_ALL_UNIQUES();
DROP ALIAS DROP_STL_ALL_UNIQUES;
DROP INDEX IF EXISTS uk_stl_settlement_contract;
ALTER TABLE stl_settlement ADD UNIQUE KEY uk_stl_settlement_code_del (tenant_id, settlement_code, deleted_flag);
ALTER TABLE stl_settlement ADD UNIQUE KEY uk_stl_settlement_contract_del (tenant_id, contract_id, deleted_flag);

-- 16. wf_template
CREATE ALIAS IF NOT EXISTS DROP_WF_TEMPLATE_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='WF_TEMPLATE' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE WF_TEMPLATE DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_WF_TEMPLATE_UNIQUE();
DROP ALIAS DROP_WF_TEMPLATE_UNIQUE;
ALTER TABLE wf_template ADD UNIQUE KEY uk_wf_template_code (tenant_id, template_code, deleted_flag);

-- 17. wf_instance
CREATE ALIAS IF NOT EXISTS DROP_WF_INSTANCE_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME='WF_INSTANCE' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE wf_instance DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_WF_INSTANCE_UNIQUE();
DROP ALIAS DROP_WF_INSTANCE_UNIQUE;
ALTER TABLE wf_instance ADD UNIQUE KEY uk_wf_instance_business (business_type, business_id, deleted_flag);

-- 18. cost_summary
CREATE ALIAS IF NOT EXISTS DROP_COST_SUMMARY_UNIQUE AS $$
String dropUnique(java.sql.Connection conn) throws Exception {
    java.sql.ResultSet rs = conn.createStatement().executeQuery(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='COST_SUMMARY' AND CONSTRAINT_TYPE='UNIQUE'");
    if (rs.next()) {
        conn.createStatement().execute("ALTER TABLE COST_SUMMARY DROP CONSTRAINT " + rs.getString(1));
    }
    return null;
}
$$;
CALL DROP_COST_SUMMARY_UNIQUE();
DROP ALIAS DROP_COST_SUMMARY_UNIQUE;
ALTER TABLE cost_summary ADD UNIQUE KEY uk_cost_summary (project_id, summary_date, cost_subject_id, deleted_flag);

-- 19. pay_record — V74 created a named UNIQUE INDEX (not an auto-generated constraint)
DROP INDEX IF EXISTS uk_external_txn_no;
CREATE UNIQUE INDEX uk_external_txn_no ON pay_record(external_txn_no, deleted_flag);
