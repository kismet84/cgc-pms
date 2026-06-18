-- V73__drop_contract_warranty_columns.sql (H2 version)
-- Drop warranty_rate and warranty_amount columns from ct_contract.
-- Mirrors db/migration/V73__drop_contract_warranty_columns.sql.
ALTER TABLE ct_contract DROP COLUMN warranty_rate;
ALTER TABLE ct_contract DROP COLUMN warranty_amount;
