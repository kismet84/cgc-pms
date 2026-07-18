-- V11__fix_sys_menu_audit_columns.sql
-- 建筑工程总包项目全过程管理系统
-- 补充 sys_menu 缺失的审计字段
-- sys_menu 在 V1 中缺少 created_by / updated_by / remark，但 SysMenu 实体继承 BaseEntity 包含这些字段

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE sys_menu ADD COLUMN created_by BIGINT NULL COMMENT '创建人' AFTER visible;
ALTER TABLE sys_menu ADD COLUMN updated_by BIGINT NULL COMMENT '更新人' AFTER created_by;
ALTER TABLE sys_menu ADD COLUMN remark VARCHAR(500) NULL COMMENT '备注' AFTER updated_by;

SET FOREIGN_KEY_CHECKS = 1;
