-- V10__add_missing_audit_columns.sql
-- 建筑工程总包项目全过程管理系统
-- 补充 ct_contract_item / ct_contract_payment_term 缺失的审计字段
-- 这些表在 V2 中缺少 created_by / updated_by / remark，但实体继承 BaseEntity 包含这些字段

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE ct_contract_item ADD COLUMN created_by BIGINT NULL COMMENT '创建人' AFTER sort_order;
ALTER TABLE ct_contract_item ADD COLUMN updated_by BIGINT NULL COMMENT '更新人' AFTER created_by;
ALTER TABLE ct_contract_item ADD COLUMN remark VARCHAR(500) NULL COMMENT '备注' AFTER updated_by;

ALTER TABLE ct_contract_payment_term ADD COLUMN created_by BIGINT NULL COMMENT '创建人' AFTER sort_order;
ALTER TABLE ct_contract_payment_term ADD COLUMN updated_by BIGINT NULL COMMENT '更新人' AFTER created_by;
ALTER TABLE ct_contract_payment_term ADD COLUMN remark VARCHAR(500) NULL COMMENT '备注' AFTER updated_by;

SET FOREIGN_KEY_CHECKS = 1;
