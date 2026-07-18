-- Retire the nullable deleted_token compatibility columns superseded by
-- active_unique_token in V131/V195. No current index or runtime mapping uses them.
SET NAMES utf8mb4;

ALTER TABLE sys_user DROP COLUMN deleted_token;
ALTER TABLE sys_role DROP COLUMN deleted_token;
ALTER TABLE md_partner DROP COLUMN deleted_token;
ALTER TABLE ct_contract DROP COLUMN deleted_token;
ALTER TABLE ct_contract_change DROP COLUMN deleted_token;
ALTER TABLE org_company DROP COLUMN deleted_token;
ALTER TABLE org_position DROP COLUMN deleted_token;
ALTER TABLE pay_application DROP COLUMN deleted_token;
ALTER TABLE md_material DROP COLUMN deleted_token;
ALTER TABLE mat_purchase_order DROP COLUMN deleted_token;
ALTER TABLE mat_receipt DROP COLUMN deleted_token;
ALTER TABLE mat_stock DROP COLUMN deleted_token;
ALTER TABLE pm_project DROP COLUMN deleted_token;
ALTER TABLE cost_subject DROP COLUMN deleted_token;
