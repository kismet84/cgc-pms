-- V56__seed_contract_approval_template.sql
-- Compatibility marker for MySQL V56. H2 already seeds CONTRACT_APPROVAL in V9.

UPDATE wf_template
SET enabled = enabled
WHERE business_type = 'CONTRACT_APPROVAL';
