-- V52__add_settlement_contract_unique.sql
-- P0-01: Prevent duplicate settlements for the same contract (TOCTOU fix)
-- H2 version — same syntax as MySQL

ALTER TABLE stl_settlement ADD UNIQUE KEY uk_stl_settlement_contract (tenant_id, contract_id);
