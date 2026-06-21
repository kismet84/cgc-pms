-- V82__fix_mat_stock_unique_with_deleted_flag.sql
-- Repair remaining soft-delete/schema conflicts found after V75.
-- 1) mat_stock uniqueness must include deleted_flag so a logically deleted
--    stock row does not block recreating the same warehouse/material balance.
-- 2) accounting_entry_line extends BaseEntity in Java, so the table must have
--    the BaseEntity audit/logical-delete columns on both fresh installs and upgrades.
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE mat_stock DROP INDEX uk_ms_warehouse_material;
ALTER TABLE mat_stock ADD UNIQUE KEY uk_ms_warehouse_material (warehouse_id, material_id, deleted_flag);

ALTER TABLE accounting_entry_line ADD COLUMN created_by BIGINT NULL;
ALTER TABLE accounting_entry_line ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE accounting_entry_line ADD COLUMN updated_by BIGINT NULL;
ALTER TABLE accounting_entry_line ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE accounting_entry_line ADD COLUMN deleted_flag TINYINT NOT NULL DEFAULT 0;
ALTER TABLE accounting_entry_line ADD COLUMN remark VARCHAR(500) NULL;

SET FOREIGN_KEY_CHECKS = 1;
