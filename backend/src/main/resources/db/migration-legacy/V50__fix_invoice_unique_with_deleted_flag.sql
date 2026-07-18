-- V50__fix_invoice_unique_with_deleted_flag.sql
-- 逻辑删除后唯一约束应包含 deleted_flag，允许同名发票号在删除后重新创建
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
ALTER TABLE pay_invoice DROP INDEX uk_pi_tenant_invoice_no;
ALTER TABLE pay_invoice ADD UNIQUE KEY uk_pi_tenant_invoice_no_del (tenant_id, invoice_no, deleted_flag);
SET FOREIGN_KEY_CHECKS = 1;
