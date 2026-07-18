-- H2 mirror of V211__add_stock_transfer_posting.sql.
CREATE TABLE mat_stock_transfer (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    source_stock_id BIGINT NOT NULL,
    target_stock_id BIGINT NOT NULL,
    source_warehouse_id BIGINT NOT NULL,
    target_warehouse_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    unit_cost DECIMAL(18,6) NOT NULL DEFAULT 0,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    completed_at TIMESTAMP NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_transfer_tenant_key UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_stock_transfer_source_stock FOREIGN KEY (source_stock_id) REFERENCES mat_stock (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_transfer_target_stock FOREIGN KEY (target_stock_id) REFERENCES mat_stock (id) ON DELETE RESTRICT,
    CONSTRAINT ck_stock_transfer_route CHECK (source_stock_id <> target_stock_id AND source_warehouse_id <> target_warehouse_id),
    CONSTRAINT ck_stock_transfer_values CHECK (quantity > 0 AND unit_cost >= 0 AND amount >= 0),
    CONSTRAINT ck_stock_transfer_status CHECK (status IN ('PENDING', 'COMPLETED'))
);

CREATE INDEX idx_stock_transfer_project_created ON mat_stock_transfer (tenant_id, project_id, created_at);
CREATE INDEX idx_stock_transfer_source_stock ON mat_stock_transfer (tenant_id, source_stock_id);
CREATE INDEX idx_stock_transfer_target_stock ON mat_stock_transfer (tenant_id, target_stock_id);
