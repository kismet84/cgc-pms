-- V18__add_contract_paid_amount.sql
-- H2-compatible version

ALTER TABLE ct_contract ADD COLUMN paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0;
