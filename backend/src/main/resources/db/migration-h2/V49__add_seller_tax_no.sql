-- V49__add_seller_tax_no.sql
ALTER TABLE pay_invoice ADD COLUMN seller_tax_no VARCHAR(50) NULL AFTER buyer_tax_no;
