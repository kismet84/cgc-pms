ALTER TABLE mat_requisition
    ADD COLUMN stock_out_by BIGINT NULL COMMENT '实际出库操作人' AFTER stock_out_flag,
    ADD COLUMN stock_out_at DATETIME NULL COMMENT '实际出库时间' AFTER stock_out_by;
