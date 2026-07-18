-- V88__soft_delete_token_based_unique.sql
-- 将布尔 deleted_flag 唯一键替换为 token-based 方案
-- 模式：删除时写入 deleted_token = 记录 ID，允许无限次删除重建
-- 仅活动行唯一（deleted_token IS NULL）
-- 覆盖 V75 修复过的所有表

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 先加列
ALTER TABLE sys_user ADD COLUMN deleted_token BIGINT DEFAULT NULL;
ALTER TABLE sys_role ADD COLUMN deleted_token BIGINT DEFAULT NULL;
ALTER TABLE md_partner ADD COLUMN deleted_token BIGINT DEFAULT NULL;
ALTER TABLE ct_contract ADD COLUMN deleted_token BIGINT DEFAULT NULL;
ALTER TABLE ct_contract_change ADD COLUMN deleted_token BIGINT DEFAULT NULL;
ALTER TABLE org_company ADD COLUMN deleted_token BIGINT DEFAULT NULL;
ALTER TABLE org_position ADD COLUMN deleted_token BIGINT DEFAULT NULL;
ALTER TABLE pay_application ADD COLUMN deleted_token BIGINT DEFAULT NULL;
ALTER TABLE md_material ADD COLUMN deleted_token BIGINT DEFAULT NULL;
ALTER TABLE mat_purchase_order ADD COLUMN deleted_token BIGINT DEFAULT NULL;
ALTER TABLE mat_receipt ADD COLUMN deleted_token BIGINT DEFAULT NULL;
ALTER TABLE mat_stock ADD COLUMN deleted_token BIGINT DEFAULT NULL;
ALTER TABLE pm_project ADD COLUMN deleted_token BIGINT DEFAULT NULL;
ALTER TABLE cost_subject ADD COLUMN deleted_token BIGINT DEFAULT NULL;

-- 2. 回填已删除记录（deleted_flag=1 时把记录 ID 写入 deleted_token）
UPDATE sys_user SET deleted_token = id WHERE deleted_flag = 1 AND deleted_token IS NULL;
UPDATE sys_role SET deleted_token = id WHERE deleted_flag = 1 AND deleted_token IS NULL;
UPDATE md_partner SET deleted_token = id WHERE deleted_flag = 1 AND deleted_token IS NULL;
UPDATE ct_contract SET deleted_token = id WHERE deleted_flag = 1 AND deleted_token IS NULL;
UPDATE ct_contract_change SET deleted_token = id WHERE deleted_flag = 1 AND deleted_token IS NULL;
UPDATE org_company SET deleted_token = id WHERE deleted_flag = 1 AND deleted_token IS NULL;
UPDATE org_position SET deleted_token = id WHERE deleted_flag = 1 AND deleted_token IS NULL;
UPDATE pay_application SET deleted_token = id WHERE deleted_flag = 1 AND deleted_token IS NULL;
UPDATE md_material SET deleted_token = id WHERE deleted_flag = 1 AND deleted_token IS NULL;
UPDATE mat_purchase_order SET deleted_token = id WHERE deleted_flag = 1 AND deleted_token IS NULL;
UPDATE mat_receipt SET deleted_token = id WHERE deleted_flag = 1 AND deleted_token IS NULL;
UPDATE mat_stock SET deleted_token = id WHERE deleted_flag = 1 AND deleted_token IS NULL;
UPDATE pm_project SET deleted_token = id WHERE deleted_flag = 1 AND deleted_token IS NULL;
UPDATE cost_subject SET deleted_token = id WHERE deleted_flag = 1 AND deleted_token IS NULL;

-- 3. 删除旧 (deleted_flag) 唯一键并在 (columns, deleted_token) 上重建
-- Pattern: DROP uk_x, ADD uk_x (tenant_id, code, deleted_token)
-- NULL deleted_token 行之间唯一约束生效；非 NULL 行之间无唯一冲突

ALTER TABLE sys_user DROP INDEX uk_sys_user_username;
ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_username (tenant_id, username, deleted_token);

ALTER TABLE sys_role DROP INDEX uk_sys_role_code;
ALTER TABLE sys_role ADD UNIQUE KEY uk_sys_role_code (tenant_id, role_code, deleted_token);

ALTER TABLE md_partner DROP INDEX uk_md_partner_code;
ALTER TABLE md_partner ADD UNIQUE KEY uk_md_partner_code (tenant_id, partner_code, deleted_token);

ALTER TABLE ct_contract DROP INDEX uk_ct_contract_code;
ALTER TABLE ct_contract ADD UNIQUE KEY uk_ct_contract_code (tenant_id, contract_code, deleted_token);

ALTER TABLE ct_contract_change DROP INDEX uk_ct_change_code;
ALTER TABLE ct_contract_change ADD UNIQUE KEY uk_ct_change_code (tenant_id, change_code, deleted_token);

ALTER TABLE org_company DROP INDEX uk_oc_tenant_code;
ALTER TABLE org_company ADD UNIQUE KEY uk_oc_tenant_code (tenant_id, company_code, deleted_token);

ALTER TABLE org_position DROP INDEX uk_op_tenant_code;
ALTER TABLE org_position ADD UNIQUE KEY uk_op_tenant_code (tenant_id, position_code, deleted_token);

ALTER TABLE pay_application DROP INDEX uk_pay_application_code;
ALTER TABLE pay_application ADD UNIQUE KEY uk_pay_application_code (tenant_id, apply_code, deleted_token);

ALTER TABLE md_material DROP INDEX uk_md_material_code;
ALTER TABLE md_material ADD UNIQUE KEY uk_md_material_code (tenant_id, material_code, deleted_token);

ALTER TABLE mat_purchase_order DROP INDEX uk_mat_po_code;
ALTER TABLE mat_purchase_order ADD UNIQUE KEY uk_mat_po_code (tenant_id, order_code, deleted_token);

ALTER TABLE mat_receipt DROP INDEX uk_mat_receipt_code;
ALTER TABLE mat_receipt ADD UNIQUE KEY uk_mat_receipt_code (tenant_id, receipt_code, deleted_token);

ALTER TABLE mat_stock DROP INDEX uk_ms_warehouse_material;
ALTER TABLE mat_stock ADD UNIQUE KEY uk_ms_warehouse_material (warehouse_id, material_id, deleted_token);

ALTER TABLE pm_project DROP INDEX uk_pm_project_code;
ALTER TABLE pm_project ADD UNIQUE KEY uk_pm_project_code (tenant_id, project_code, deleted_token);

ALTER TABLE cost_subject DROP INDEX uk_cost_subject_code;
ALTER TABLE cost_subject ADD UNIQUE KEY uk_cost_subject_code (tenant_id, subject_code, deleted_token);

SET FOREIGN_KEY_CHECKS = 1;
