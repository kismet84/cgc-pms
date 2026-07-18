-- V21__add_cost_subject_audit_columns.sql
-- Add audit columns required by CostSubject's BaseEntity mapping.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE cost_subject ADD COLUMN created_by BIGINT NULL COMMENT '创建人' AFTER status;
ALTER TABLE cost_subject ADD COLUMN updated_by BIGINT NULL COMMENT '更新人' AFTER created_by;
ALTER TABLE cost_subject ADD COLUMN remark VARCHAR(500) NULL COMMENT '备注' AFTER updated_by;

SET FOREIGN_KEY_CHECKS = 1;
