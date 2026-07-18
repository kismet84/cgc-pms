CREATE TABLE mat_material_return (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    requisition_id BIGINT NOT NULL,
    return_code VARCHAR(50) NOT NULL,
    return_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    confirmed_by BIGINT NOT NULL,
    confirmed_at TIMESTAMP NOT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark TEXT NULL,
    CONSTRAINT uk_material_return_code UNIQUE (tenant_id, return_code),
    CONSTRAINT uk_material_return_idempotency UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX idx_material_return_requisition ON mat_material_return (tenant_id, requisition_id);

CREATE TABLE mat_material_return_item (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    return_id BIGINT NOT NULL,
    requisition_item_id BIGINT NOT NULL,
    original_stock_txn_id BIGINT NOT NULL,
    original_cost_item_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    unit_cost DECIMAL(18,6) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark TEXT NULL
);
CREATE INDEX idx_material_return_item_header ON mat_material_return_item (tenant_id, return_id);
CREATE INDEX idx_material_return_item_requisition ON mat_material_return_item (tenant_id, requisition_item_id);
CREATE INDEX idx_material_return_item_txn ON mat_material_return_item (tenant_id, original_stock_txn_id);
