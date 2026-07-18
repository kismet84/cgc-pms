-- Repair V88 nullable deleted_token uniqueness.
-- MySQL permits multiple NULL values in a UNIQUE key, so active rows were not protected.
SET NAMES utf8mb4;

-- These three V89 legacy seed nodes collide with the canonical V42/V78 chart.
-- Preserve IDs and references, but move the legacy nodes into an explicit disabled namespace.
UPDATE cost_subject
SET subject_code = 'LEGACY_STANDARD_COST_ROOT', status = 'DISABLE', updated_at = CURRENT_TIMESTAMP
WHERE id = 900000 AND tenant_id = 0 AND subject_code = 'COST_ROOT' AND deleted_flag = 0;

UPDATE cost_subject
SET subject_code = 'LEGACY_CONTRACT_REVENUE', status = 'DISABLE', updated_at = CURRENT_TIMESTAMP
WHERE id = 900002 AND tenant_id = 0 AND subject_code = '6001' AND deleted_flag = 0;

UPDATE cost_subject
SET subject_code = 'LEGACY_CONSTRUCTION_REVENUE', status = 'DISABLE', updated_at = CURRENT_TIMESTAMP
WHERE id = 900007 AND tenant_id = 0 AND subject_code = '6001.01' AND deleted_flag = 0;

ALTER TABLE sys_user
    ADD COLUMN active_unique_token BIGINT GENERATED ALWAYS AS
        (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END) STORED
        COMMENT '活动行唯一键辅助列：活动行=0，删除行=id';
ALTER TABLE sys_role ADD COLUMN active_unique_token BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id';
ALTER TABLE md_partner ADD COLUMN active_unique_token BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id';
ALTER TABLE ct_contract ADD COLUMN active_unique_token BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id';
ALTER TABLE ct_contract_change ADD COLUMN active_unique_token BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id';
ALTER TABLE org_company ADD COLUMN active_unique_token BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id';
ALTER TABLE org_position ADD COLUMN active_unique_token BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id';
ALTER TABLE pay_application ADD COLUMN active_unique_token BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id';
ALTER TABLE md_material ADD COLUMN active_unique_token BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id';
ALTER TABLE mat_purchase_order ADD COLUMN active_unique_token BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id';
ALTER TABLE mat_receipt ADD COLUMN active_unique_token BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id';
ALTER TABLE pm_project ADD COLUMN active_unique_token BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id';
ALTER TABLE cost_subject ADD COLUMN active_unique_token BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END) STORED COMMENT '活动行唯一键辅助列：活动行=0，删除行=id';

ALTER TABLE sys_user DROP INDEX uk_sys_user_username, ADD UNIQUE KEY uk_sys_user_username (tenant_id, username, active_unique_token);
ALTER TABLE sys_role DROP INDEX uk_sys_role_code, ADD UNIQUE KEY uk_sys_role_code (tenant_id, role_code, active_unique_token);
ALTER TABLE md_partner DROP INDEX uk_md_partner_code, ADD UNIQUE KEY uk_md_partner_code (tenant_id, partner_code, active_unique_token);
ALTER TABLE ct_contract DROP INDEX uk_ct_contract_code, ADD UNIQUE KEY uk_ct_contract_code (tenant_id, contract_code, active_unique_token);
ALTER TABLE ct_contract_change DROP INDEX uk_ct_change_code, ADD UNIQUE KEY uk_ct_change_code (tenant_id, change_code, active_unique_token);
ALTER TABLE org_company DROP INDEX uk_oc_tenant_code, ADD UNIQUE KEY uk_oc_tenant_code (tenant_id, company_code, active_unique_token);
ALTER TABLE org_position DROP INDEX uk_op_tenant_code, ADD UNIQUE KEY uk_op_tenant_code (tenant_id, position_code, active_unique_token);
ALTER TABLE pay_application DROP INDEX uk_pay_application_code, ADD UNIQUE KEY uk_pay_application_code (tenant_id, apply_code, active_unique_token);
ALTER TABLE md_material DROP INDEX uk_md_material_code, ADD UNIQUE KEY uk_md_material_code (tenant_id, material_code, active_unique_token);
ALTER TABLE mat_purchase_order DROP INDEX uk_mat_po_code, ADD UNIQUE KEY uk_mat_po_code (tenant_id, order_code, active_unique_token);
ALTER TABLE mat_receipt DROP INDEX uk_mat_receipt_code, ADD UNIQUE KEY uk_mat_receipt_code (tenant_id, receipt_code, active_unique_token);
ALTER TABLE pm_project DROP INDEX uk_pm_project_code, ADD UNIQUE KEY uk_pm_project_code (tenant_id, project_code, active_unique_token);
ALTER TABLE cost_subject DROP INDEX uk_cost_subject_code, ADD UNIQUE KEY uk_cost_subject_code (tenant_id, subject_code, active_unique_token);
