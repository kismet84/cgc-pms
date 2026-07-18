-- V19__fix_payment_tables_schema.sql
-- Fix: pay_application and pay_record missing columns

ALTER TABLE pay_application
    ADD COLUMN remark VARCHAR(500) NULL COMMENT '备注';

ALTER TABLE pay_record
    ADD COLUMN project_id BIGINT NULL COMMENT '项目ID'
    AFTER tenant_id;

ALTER TABLE pay_record
    ADD COLUMN remark VARCHAR(500) NULL COMMENT '备注';
