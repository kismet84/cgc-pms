ALTER TABLE mat_receipt
    ADD COLUMN receipt_mode VARCHAR(30) NOT NULL DEFAULT 'INVENTORY' COMMENT 'INVENTORY入库；DIRECT_CONSUMPTION直耗' AFTER receiver_id;

ALTER TABLE mat_stock
    ADD COLUMN inventory_value DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '库存价值' AFTER available_qty,
    ADD COLUMN average_unit_cost DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '移动加权平均单价' AFTER inventory_value;

ALTER TABLE mat_stock_txn
    ADD COLUMN unit_cost DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '本次移动单位成本' AFTER available_after,
    ADD COLUMN amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '本次移动库存价值' AFTER unit_cost;
