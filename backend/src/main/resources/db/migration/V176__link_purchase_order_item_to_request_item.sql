-- 采购申请审批只生成草稿采购订单，并建立申请明细到订单明细的逐行追溯关系。

ALTER TABLE mat_purchase_order_item
    ADD COLUMN request_item_id BIGINT NULL COMMENT '来源采购申请明细ID；手工订单允许为空' AFTER order_id;

CREATE INDEX idx_mat_poi_request_item
    ON mat_purchase_order_item (tenant_id, request_item_id, deleted_flag);
