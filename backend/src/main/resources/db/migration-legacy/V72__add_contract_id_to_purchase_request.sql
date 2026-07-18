-- V72__add_contract_id_to_purchase_request.sql
-- 采购申请增加关联采购合同字段
SET NAMES utf8mb4;

ALTER TABLE mat_purchase_request
    ADD COLUMN contract_id BIGINT NULL COMMENT '关联采购合同' AFTER project_id;
