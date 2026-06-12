-- V11__fix_sys_menu_audit_columns.sql
-- H2-compatible version

ALTER TABLE sys_menu ADD COLUMN created_by BIGINT NULL;
ALTER TABLE sys_menu ADD COLUMN updated_by BIGINT NULL;
ALTER TABLE sys_menu ADD COLUMN remark VARCHAR(500) NULL;
