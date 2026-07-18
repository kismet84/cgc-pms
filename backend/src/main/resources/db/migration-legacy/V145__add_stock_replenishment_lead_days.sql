-- Optional manual replenishment lead days; NULL keeps planned date unfilled.

SET NAMES utf8mb4;

ALTER TABLE mat_stock
    ADD COLUMN replenishment_lead_days INT NULL COMMENT '人工补货提前期（自然日）；NULL 不预填计划日期' AFTER replenishment_target_qty;
