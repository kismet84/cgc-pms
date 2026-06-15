-- V49__add_seller_tax_no.sql
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
ALTER TABLE pay_invoice ADD COLUMN seller_tax_no VARCHAR(50) NULL COMMENT '卖方税号' AFTER buyer_tax_no;
SET FOREIGN_KEY_CHECKS = 1;
