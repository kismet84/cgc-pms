ALTER TABLE mat_stock_txn
    ADD COLUMN IF NOT EXISTS source_line_id BIGINT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_stock_txn_source_line
    ON mat_stock_txn (tenant_id, txn_type, source_type, source_id, source_line_id);
