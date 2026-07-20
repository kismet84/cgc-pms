-- Frozen legacy fixture chain schema shim. Historical normalization is covered by active V218 migration tests.
ALTER TABLE pay_record ADD COLUMN record_code VARCHAR(32) NULL;
CREATE UNIQUE INDEX uk_pay_record_code ON pay_record (tenant_id, record_code);
