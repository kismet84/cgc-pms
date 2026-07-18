-- H2 mirror: optional manual replenishment target.

ALTER TABLE mat_stock
    ADD COLUMN replenishment_target_qty DECIMAL(18,4) NULL;
