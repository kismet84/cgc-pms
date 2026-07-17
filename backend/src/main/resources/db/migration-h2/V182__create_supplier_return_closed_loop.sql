CREATE TABLE mat_supplier_return (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    partner_id BIGINT NOT NULL,
    warehouse_id BIGINT,
    receipt_id BIGINT NOT NULL,
    return_code VARCHAR(50) NOT NULL,
    return_date DATE NOT NULL,
    return_kind VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    confirmed_by BIGINT,
    confirmed_at TIMESTAMP,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark TEXT,
    CONSTRAINT uk_supplier_return_code UNIQUE (tenant_id, return_code),
    CONSTRAINT uk_supplier_return_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_supplier_return_receipt FOREIGN KEY (receipt_id) REFERENCES mat_receipt (id)
);
CREATE INDEX idx_supplier_return_receipt ON mat_supplier_return (tenant_id, receipt_id, deleted_flag);

CREATE TABLE mat_supplier_return_item (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    return_id BIGINT NOT NULL,
    receipt_item_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    original_stock_txn_id BIGINT,
    original_cost_item_id BIGINT,
    material_id BIGINT NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    unit_cost DECIMAL(18,6) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark TEXT,
    CONSTRAINT fk_supplier_return_item_header FOREIGN KEY (return_id) REFERENCES mat_supplier_return (id),
    CONSTRAINT fk_supplier_return_item_receipt FOREIGN KEY (receipt_item_id) REFERENCES mat_receipt_item (id),
    CONSTRAINT fk_supplier_return_item_order FOREIGN KEY (order_item_id) REFERENCES mat_purchase_order_item (id)
);
CREATE INDEX idx_supplier_return_item_header ON mat_supplier_return_item (tenant_id, return_id);
CREATE INDEX idx_supplier_return_item_receipt ON mat_supplier_return_item (tenant_id, receipt_item_id);
