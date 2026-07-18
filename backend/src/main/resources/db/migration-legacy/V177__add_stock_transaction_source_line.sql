ALTER TABLE mat_stock_txn
    ADD COLUMN source_line_id BIGINT NULL COMMENT '来源业务明细ID' AFTER source_id;

CREATE UNIQUE INDEX uk_stock_txn_source_line
    ON mat_stock_txn (tenant_id, txn_type, source_type, source_id, source_line_id);
