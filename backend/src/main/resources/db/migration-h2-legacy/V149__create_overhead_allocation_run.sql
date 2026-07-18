-- H2 compatible: 间接费月度执行事实。
CREATE TABLE overhead_allocation_run (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL,
    period DATE NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    executed_by BIGINT NULL,
    run_status VARCHAR(30) NOT NULL,
    allocated_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    cost_item_count INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_overhead_run_period UNIQUE (tenant_id, rule_id, period, deleted_flag)
);
CREATE INDEX idx_overhead_run_tenant_period ON overhead_allocation_run(tenant_id, period);
CREATE INDEX idx_overhead_run_status ON overhead_allocation_run(run_status);
