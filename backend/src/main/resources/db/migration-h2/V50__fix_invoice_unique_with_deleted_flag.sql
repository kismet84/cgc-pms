-- V50__fix_invoice_unique_with_deleted_flag.sql
ALTER TABLE pay_invoice DROP INDEX uk_pi_tenant_invoice_no;
ALTER TABLE pay_invoice ADD UNIQUE KEY uk_pi_tenant_invoice_no_del (tenant_id, invoice_no, deleted_flag);
