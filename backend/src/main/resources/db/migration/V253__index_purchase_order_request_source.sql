CREATE INDEX idx_purchase_order_request_source
    ON mat_purchase_order (tenant_id, request_id, deleted_flag);
