-- H2 mirror: optional manual replenishment lead days.

ALTER TABLE mat_stock
    ADD COLUMN replenishment_lead_days INT NULL;
