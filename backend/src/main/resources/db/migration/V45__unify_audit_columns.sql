-- V45__unify_audit_columns.sql
-- 建筑工程总包项目全过程管理系统 - 审计列统一
-- 数据库：MySQL 8.0+
-- 说明：将 V22+ 表的 created_time/updated_time 统一重命名为 created_at/updated_at，
--       对齐 V1-V21 表及 BaseEntity 的命名约定。
--       仅重命名列，不修改数据。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 同时有 created_time 和 updated_time 的表（14 张）
-- ============================================================

-- V22: cost_target
ALTER TABLE cost_target RENAME COLUMN created_time TO created_at;
ALTER TABLE cost_target RENAME COLUMN updated_time TO updated_at;

-- V22: cost_target_item
ALTER TABLE cost_target_item RENAME COLUMN created_time TO created_at;
ALTER TABLE cost_target_item RENAME COLUMN updated_time TO updated_at;

-- V23: ct_contract_change
ALTER TABLE ct_contract_change RENAME COLUMN created_time TO created_at;
ALTER TABLE ct_contract_change RENAME COLUMN updated_time TO updated_at;

-- V24: alert_log
ALTER TABLE alert_log RENAME COLUMN created_time TO created_at;
ALTER TABLE alert_log RENAME COLUMN updated_time TO updated_at;

-- V33: org_company
ALTER TABLE org_company RENAME COLUMN created_time TO created_at;
ALTER TABLE org_company RENAME COLUMN updated_time TO updated_at;

-- V33: org_department
ALTER TABLE org_department RENAME COLUMN created_time TO created_at;
ALTER TABLE org_department RENAME COLUMN updated_time TO updated_at;

-- V33: org_position
ALTER TABLE org_position RENAME COLUMN created_time TO created_at;
ALTER TABLE org_position RENAME COLUMN updated_time TO updated_at;

-- V34: pm_project_member
ALTER TABLE pm_project_member RENAME COLUMN created_time TO created_at;
ALTER TABLE pm_project_member RENAME COLUMN updated_time TO updated_at;

-- V35: mat_warehouse
ALTER TABLE mat_warehouse RENAME COLUMN created_time TO created_at;
ALTER TABLE mat_warehouse RENAME COLUMN updated_time TO updated_at;

-- V35: mat_stock
ALTER TABLE mat_stock RENAME COLUMN created_time TO created_at;
ALTER TABLE mat_stock RENAME COLUMN updated_time TO updated_at;

-- V35: mat_stock_txn
ALTER TABLE mat_stock_txn RENAME COLUMN created_time TO created_at;
ALTER TABLE mat_stock_txn RENAME COLUMN updated_time TO updated_at;

-- V35: mat_purchase_request
ALTER TABLE mat_purchase_request RENAME COLUMN created_time TO created_at;
ALTER TABLE mat_purchase_request RENAME COLUMN updated_time TO updated_at;

-- V35: mat_purchase_request_item
ALTER TABLE mat_purchase_request_item RENAME COLUMN created_time TO created_at;
ALTER TABLE mat_purchase_request_item RENAME COLUMN updated_time TO updated_at;

-- V36: pay_invoice
ALTER TABLE pay_invoice RENAME COLUMN created_time TO created_at;
ALTER TABLE pay_invoice RENAME COLUMN updated_time TO updated_at;

-- ============================================================
-- 仅有 created_time 的表（2 张，无 updated_time）
-- ============================================================

-- V37: sys_notification（无 updated_time/updated_by/deleted_flag）
ALTER TABLE sys_notification RENAME COLUMN created_time TO created_at;

-- V38: wf_cc（无 updated_time/updated_by/deleted_flag）
ALTER TABLE wf_cc RENAME COLUMN created_time TO created_at;

SET FOREIGN_KEY_CHECKS = 1;
