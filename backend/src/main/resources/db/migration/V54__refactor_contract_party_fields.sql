-- V54: Refactor contract party fields
-- partnerType: SUPPLIER/SUB/DESIGN/SUPERVISOR -> OTHER
-- ct_contract: drop partner_id, party_a/b String -> party_a_id/party_b_id Long (names via JOIN)

-- 1. Migrate existing partnerType values
UPDATE md_partner SET partner_type = 'OTHER'
WHERE partner_type IN ('SUPPLIER', 'SUB', 'DESIGN', 'SUPERVISOR');

-- 2. Drop old partner_id column
ALTER TABLE ct_contract DROP COLUMN IF EXISTS partner_id;

-- 3. Drop old party_a text column and add party_a_id
ALTER TABLE ct_contract DROP COLUMN IF EXISTS party_a;
ALTER TABLE ct_contract ADD COLUMN party_a_id BIGINT NULL COMMENT '甲方合作方ID' AFTER contract_type;

-- 4. Drop old party_b text column and add party_b_id
ALTER TABLE ct_contract DROP COLUMN IF EXISTS party_b;
ALTER TABLE ct_contract ADD COLUMN party_b_id BIGINT NULL COMMENT '乙方合作方ID' AFTER party_a_id;
