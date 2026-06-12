-- V10__add_missing_audit_columns.sql
-- H2-compatible version

ALTER TABLE ct_contract_item ADD COLUMN created_by BIGINT NULL;
ALTER TABLE ct_contract_item ADD COLUMN updated_by BIGINT NULL;
ALTER TABLE ct_contract_item ADD COLUMN remark VARCHAR(500) NULL;

ALTER TABLE ct_contract_payment_term ADD COLUMN created_by BIGINT NULL;
ALTER TABLE ct_contract_payment_term ADD COLUMN updated_by BIGINT NULL;
ALTER TABLE ct_contract_payment_term ADD COLUMN remark VARCHAR(500) NULL;
