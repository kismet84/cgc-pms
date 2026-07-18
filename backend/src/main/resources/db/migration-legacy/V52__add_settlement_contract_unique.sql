-- V52__add_settlement_contract_unique.sql
-- P0-01: Prevent duplicate settlements for the same contract (TOCTOU fix)
-- Adds database-level UNIQUE constraint on (tenant_id, contract_id)
-- Works alongside application-level check-then-insert as safety net

ALTER TABLE stl_settlement ADD UNIQUE KEY uk_stl_settlement_contract (tenant_id, contract_id);
