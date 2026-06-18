-- WARNING: Destructive migration — drops production data irreversibly.
-- Before running in production:
--   1. Take a full database backup.
--   2. Verify no code references warranty_rate/warranty_amount anymore.
--   3. Stage this migration one release AFTER code removal, not together.
--   4. If rollback is needed, restore from backup.
ALTER TABLE ct_contract DROP COLUMN warranty_rate;
ALTER TABLE ct_contract DROP COLUMN warranty_amount;
