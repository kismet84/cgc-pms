-- V19__fix_payment_tables_schema.sql
-- H2-compatible version

ALTER TABLE pay_application ADD COLUMN remark VARCHAR(500) NULL;

ALTER TABLE pay_record ADD COLUMN project_id BIGINT NULL;

ALTER TABLE pay_record ADD COLUMN remark VARCHAR(500) NULL;
