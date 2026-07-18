-- V75__soft_delete_safe_unique_constraints.sql
-- Add deleted_flag to unique constraints on key business tables to allow
-- soft-deleted records to coexist with active re-creations of the same code.
-- Pattern: DROP old unique key, ADD new unique key with (columns..., deleted_flag).
-- Follows existing V50/V51/V58 pattern.
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. sys_user: tenant-scoped username uniqueness
ALTER TABLE sys_user DROP INDEX uk_sys_user_username;
ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_username (tenant_id, username, deleted_flag);

-- 2. sys_role: tenant-scoped role code uniqueness
ALTER TABLE sys_role DROP INDEX uk_sys_role_code;
ALTER TABLE sys_role ADD UNIQUE KEY uk_sys_role_code (tenant_id, role_code, deleted_flag);

-- 3. md_partner: tenant-scoped partner code uniqueness
ALTER TABLE md_partner DROP INDEX uk_md_partner_code;
ALTER TABLE md_partner ADD UNIQUE KEY uk_md_partner_code (tenant_id, partner_code, deleted_flag);

-- 4. ct_contract: tenant-scoped contract code uniqueness
ALTER TABLE ct_contract DROP INDEX uk_ct_contract_code;
ALTER TABLE ct_contract ADD UNIQUE KEY uk_ct_contract_code (tenant_id, contract_code, deleted_flag);

-- 5. ct_contract_change: tenant-scoped change code uniqueness
ALTER TABLE ct_contract_change DROP INDEX uk_ct_change_code;
ALTER TABLE ct_contract_change ADD UNIQUE KEY uk_ct_change_code (tenant_id, change_code, deleted_flag);

-- 6. org_company: tenant-scoped company code uniqueness
ALTER TABLE org_company DROP INDEX uk_oc_tenant_code;
ALTER TABLE org_company ADD UNIQUE KEY uk_oc_tenant_code (tenant_id, company_code, deleted_flag);

-- 7. org_position: tenant-scoped position code uniqueness
ALTER TABLE org_position DROP INDEX uk_op_tenant_code;
ALTER TABLE org_position ADD UNIQUE KEY uk_op_tenant_code (tenant_id, position_code, deleted_flag);

-- 8. pay_application: tenant-scoped application code uniqueness
ALTER TABLE pay_application DROP INDEX uk_pay_application_code;
ALTER TABLE pay_application ADD UNIQUE KEY uk_pay_application_code (tenant_id, apply_code, deleted_flag);

-- 9. md_material: tenant-scoped material code uniqueness
ALTER TABLE md_material DROP INDEX uk_md_material_code;
ALTER TABLE md_material ADD UNIQUE KEY uk_md_material_code (tenant_id, material_code, deleted_flag);

-- 10. mat_purchase_order: tenant-scoped order code uniqueness
ALTER TABLE mat_purchase_order DROP INDEX uk_mat_po_code;
ALTER TABLE mat_purchase_order ADD UNIQUE KEY uk_mat_po_code (tenant_id, order_code, deleted_flag);

-- 11. mat_receipt: tenant-scoped receipt code uniqueness
ALTER TABLE mat_receipt DROP INDEX uk_mat_receipt_code;
ALTER TABLE mat_receipt ADD UNIQUE KEY uk_mat_receipt_code (tenant_id, receipt_code, deleted_flag);

-- 12. sub_task: tenant-scoped task code uniqueness
ALTER TABLE sub_task DROP INDEX uk_sub_task_code;
ALTER TABLE sub_task ADD UNIQUE KEY uk_sub_task_code (tenant_id, task_code, deleted_flag);

-- 13. sub_measure: tenant-scoped measure code uniqueness
ALTER TABLE sub_measure DROP INDEX uk_sub_measure_code;
ALTER TABLE sub_measure ADD UNIQUE KEY uk_sub_measure_code (tenant_id, measure_code, deleted_flag);

-- 14. var_order: tenant-scoped variation code uniqueness
ALTER TABLE var_order DROP INDEX uk_var_order_code;
ALTER TABLE var_order ADD UNIQUE KEY uk_var_order_code (tenant_id, var_code, deleted_flag);

-- 15. stl_settlement: tenant-scoped settlement code uniqueness
ALTER TABLE stl_settlement DROP INDEX uk_stl_settlement_code;
ALTER TABLE stl_settlement ADD UNIQUE KEY uk_stl_settlement_code (tenant_id, settlement_code, deleted_flag);

-- 16. stl_settlement: tenant-scoped settlement-contract uniqueness
ALTER TABLE stl_settlement DROP INDEX uk_stl_settlement_contract;
ALTER TABLE stl_settlement ADD UNIQUE KEY uk_stl_settlement_contract (tenant_id, contract_id, deleted_flag);

-- 17. wf_template: tenant-scoped template code uniqueness
ALTER TABLE wf_template DROP INDEX uk_wf_template_code;
ALTER TABLE wf_template ADD UNIQUE KEY uk_wf_template_code (tenant_id, template_code, deleted_flag);

-- 18. wf_instance: business-type+business-id uniqueness (eliminates need for hardDeleteById)
ALTER TABLE wf_instance DROP INDEX uk_wf_instance_business;
ALTER TABLE wf_instance ADD UNIQUE KEY uk_wf_instance_business (business_type, business_id, deleted_flag);

-- 19. cost_summary: project-date-subject uniqueness
ALTER TABLE cost_summary DROP INDEX uk_cost_summary;
ALTER TABLE cost_summary ADD UNIQUE KEY uk_cost_summary (project_id, summary_date, cost_subject_id, deleted_flag);

-- 20. pay_record: external transaction number uniqueness (V74 added index)
-- V75 originally missed tenant_id in the unique key; V76 adds it back.
-- This statement is kept for environments that already applied V75 without V76.
-- V76 must be applied after V75 to add tenant_id to the unique key.
DROP INDEX uk_external_txn_no ON pay_record;
CREATE UNIQUE INDEX uk_external_txn_no ON pay_record(external_txn_no, deleted_flag);

SET FOREIGN_KEY_CHECKS = 1;
