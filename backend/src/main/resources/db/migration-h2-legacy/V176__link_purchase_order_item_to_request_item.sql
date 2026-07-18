-- H2 测试环境：采购订单明细关联来源采购申请明细。

ALTER TABLE mat_purchase_order_item
    ADD COLUMN request_item_id BIGINT NULL;

CREATE INDEX idx_mat_poi_request_item
    ON mat_purchase_order_item (tenant_id, request_item_id, deleted_flag);
