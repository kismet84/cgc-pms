-- V48__add_invoice_seller_buyer.sql
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
ALTER TABLE pay_invoice ADD COLUMN seller_name VARCHAR(200) NULL COMMENT '卖方名称' AFTER remark;
ALTER TABLE pay_invoice ADD COLUMN buyer_name VARCHAR(200) NULL COMMENT '买方名称' AFTER seller_name;
ALTER TABLE pay_invoice ADD COLUMN buyer_tax_no VARCHAR(50) NULL COMMENT '买方税号' AFTER buyer_name;
SET FOREIGN_KEY_CHECKS = 1;
