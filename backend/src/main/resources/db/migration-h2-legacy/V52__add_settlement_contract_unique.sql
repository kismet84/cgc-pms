-- V52__add_settlement_contract_unique.sql
-- P0-01: Prevent duplicate settlements for the same contract (TOCTOU fix)
-- H2 version — uses H2-compatible CREATE UNIQUE INDEX syntax (MySQL ADD UNIQUE KEY is not valid H2)

CREATE UNIQUE INDEX IF NOT EXISTS uk_stl_settlement_contract ON stl_settlement (tenant_id, contract_id);
