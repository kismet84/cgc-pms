ALTER TABLE mat_requisition
    ADD COLUMN IF NOT EXISTS stock_out_by BIGINT NULL;

ALTER TABLE mat_requisition
    ADD COLUMN IF NOT EXISTS stock_out_at TIMESTAMP NULL;
