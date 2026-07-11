-- Optional manual replenishment target; NULL falls back to safety_stock_qty.

SET NAMES utf8mb4;

ALTER TABLE mat_stock
    ADD COLUMN replenishment_target_qty DECIMAL(18,4) NULL COMMENT '人工补货目标量；NULL 回退安全库存阈值' AFTER safety_stock_qty;
