-- V54__refactor_contract_party_fields.sql
-- Keep H2 contract schema aligned with MySQL V54.

UPDATE md_partner SET partner_type = 'OTHER'
WHERE partner_type IN ('SUPPLIER', 'SUB', 'DESIGN', 'SUPERVISOR');

ALTER TABLE ct_contract DROP COLUMN IF EXISTS partner_id;
ALTER TABLE ct_contract DROP COLUMN IF EXISTS party_a;
ALTER TABLE ct_contract DROP COLUMN IF EXISTS party_b;
ALTER TABLE ct_contract ADD COLUMN IF NOT EXISTS party_a_id BIGINT NULL;
ALTER TABLE ct_contract ADD COLUMN IF NOT EXISTS party_b_id BIGINT NULL;
