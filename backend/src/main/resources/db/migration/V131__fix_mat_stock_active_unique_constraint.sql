-- V131__fix_mat_stock_active_unique_constraint.sql
-- Repair V88 mat_stock uniqueness: MySQL UNIQUE permits multiple NULL values,
-- so (warehouse_id, material_id, deleted_token) did not protect active rows.
-- active_unique_token = 0 for active rows and id for deleted rows:
--   * one active stock row per tenant + warehouse + material
--   * repeated soft-delete and recreate remains possible
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE mat_stock DROP INDEX uk_ms_warehouse_material;

ALTER TABLE mat_stock
    ADD COLUMN active_unique_token BIGINT
        GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END) STORED
        COMMENT '库存活动行唯一键辅助列：活动行=0，删除行=id';

ALTER TABLE mat_stock
    ADD UNIQUE KEY uk_ms_warehouse_material
        (tenant_id, warehouse_id, material_id, active_unique_token);

SET FOREIGN_KEY_CHECKS = 1;
