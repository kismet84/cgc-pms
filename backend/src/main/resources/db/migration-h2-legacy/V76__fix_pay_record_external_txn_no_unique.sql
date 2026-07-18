-- V76__fix_pay_record_external_txn_no_unique.sql (H2 version)
-- Fix: add tenant_id to the external_txn_no unique key.
-- Mirrors db/migration/V76__fix_pay_record_external_txn_no_unique.sql.
ALTER TABLE pay_record DROP INDEX uk_external_txn_no;
ALTER TABLE pay_record ADD UNIQUE KEY uk_external_txn_no (tenant_id, external_txn_no, deleted_flag);
