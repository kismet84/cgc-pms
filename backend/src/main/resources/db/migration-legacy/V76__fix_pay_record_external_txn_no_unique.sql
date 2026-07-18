-- V76__fix_pay_record_external_txn_no_unique.sql
-- Fix: add tenant_id to the external_txn_no unique key so different tenants
-- can safely share the same external transaction number.
-- The old index without tenant_id causes cross-tenant unique key violations.
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE pay_record DROP INDEX uk_external_txn_no;
ALTER TABLE pay_record ADD UNIQUE KEY uk_external_txn_no (tenant_id, external_txn_no, deleted_flag);

SET FOREIGN_KEY_CHECKS = 1;
