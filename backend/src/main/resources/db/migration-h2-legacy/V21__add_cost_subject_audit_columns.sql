-- V21__add_cost_subject_audit_columns.sql
-- H2-compatible version

ALTER TABLE cost_subject ADD COLUMN created_by BIGINT NULL;
ALTER TABLE cost_subject ADD COLUMN updated_by BIGINT NULL;
ALTER TABLE cost_subject ADD COLUMN remark VARCHAR(500) NULL;
