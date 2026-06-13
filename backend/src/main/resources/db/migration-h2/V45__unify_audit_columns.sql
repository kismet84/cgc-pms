-- V45__unify_audit_columns.sql
-- 建筑工程总包项目全过程管理系统 - 审计列统一
-- 数据库：H2（与 MySQL V45 同步）
-- 说明：将 V22+ 表的 created_time/updated_time 统一重命名为 created_at/updated_at

-- ============================================================
-- 同时有 created_time 和 updated_time 的表（14 张）
-- ============================================================

ALTER TABLE cost_target RENAME COLUMN created_time TO created_at;
ALTER TABLE cost_target RENAME COLUMN updated_time TO updated_at;

ALTER TABLE cost_target_item RENAME COLUMN created_time TO created_at;
ALTER TABLE cost_target_item RENAME COLUMN updated_time TO updated_at;

ALTER TABLE ct_contract_change RENAME COLUMN created_time TO created_at;
ALTER TABLE ct_contract_change RENAME COLUMN updated_time TO updated_at;

ALTER TABLE alert_log RENAME COLUMN created_time TO created_at;
ALTER TABLE alert_log RENAME COLUMN updated_time TO updated_at;

ALTER TABLE org_company RENAME COLUMN created_time TO created_at;
ALTER TABLE org_company RENAME COLUMN updated_time TO updated_at;

ALTER TABLE org_department RENAME COLUMN created_time TO created_at;
ALTER TABLE org_department RENAME COLUMN updated_time TO updated_at;

ALTER TABLE org_position RENAME COLUMN created_time TO created_at;
ALTER TABLE org_position RENAME COLUMN updated_time TO updated_at;

ALTER TABLE pm_project_member RENAME COLUMN created_time TO created_at;
ALTER TABLE pm_project_member RENAME COLUMN updated_time TO updated_at;

ALTER TABLE mat_warehouse RENAME COLUMN created_time TO created_at;
ALTER TABLE mat_warehouse RENAME COLUMN updated_time TO updated_at;

ALTER TABLE mat_stock RENAME COLUMN created_time TO created_at;
ALTER TABLE mat_stock RENAME COLUMN updated_time TO updated_at;

ALTER TABLE mat_stock_txn RENAME COLUMN created_time TO created_at;
ALTER TABLE mat_stock_txn RENAME COLUMN updated_time TO updated_at;

ALTER TABLE mat_purchase_request RENAME COLUMN created_time TO created_at;
ALTER TABLE mat_purchase_request RENAME COLUMN updated_time TO updated_at;

ALTER TABLE mat_purchase_request_item RENAME COLUMN created_time TO created_at;
ALTER TABLE mat_purchase_request_item RENAME COLUMN updated_time TO updated_at;

ALTER TABLE pay_invoice RENAME COLUMN created_time TO created_at;
ALTER TABLE pay_invoice RENAME COLUMN updated_time TO updated_at;

-- ============================================================
-- 仅有 created_time 的表（2 张，无 updated_time）
-- ============================================================

ALTER TABLE sys_notification RENAME COLUMN created_time TO created_at;

ALTER TABLE wf_cc RENAME COLUMN created_time TO created_at;
