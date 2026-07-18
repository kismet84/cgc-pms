-- V48__add_invoice_seller_buyer.sql
ALTER TABLE pay_invoice ADD COLUMN seller_name VARCHAR(200) NULL AFTER remark;
ALTER TABLE pay_invoice ADD COLUMN buyer_name VARCHAR(200) NULL AFTER seller_name;
ALTER TABLE pay_invoice ADD COLUMN buyer_tax_no VARCHAR(50) NULL AFTER buyer_name;
