-- V18__add_contract_paid_amount.sql
-- 建筑工程总包项目全过程管理系统
-- 给 ct_contract 表新增 paid_amount 列，用于记录累计已付金额

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE ct_contract ADD COLUMN paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '累计已付金额' AFTER current_amount;

SET FOREIGN_KEY_CHECKS = 1;
