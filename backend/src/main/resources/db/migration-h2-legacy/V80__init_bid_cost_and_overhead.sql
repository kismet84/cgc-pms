-- V80: Init bid_cost and overhead (H2 compatible)
CREATE TABLE IF NOT EXISTS bid_cost (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NULL, bid_project_name VARCHAR(200) NOT NULL,
    bid_status VARCHAR(50) NOT NULL DEFAULT 'BIDDING',
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS bid_deposit (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    bid_cost_id BIGINT NOT NULL,
    deposit_type VARCHAR(50) NOT NULL,
    deposit_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    returned_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    deposit_status VARCHAR(50) NOT NULL DEFAULT 'PAID',
    paid_date DATE NULL, returned_date DATE NULL,
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS overhead_allocation_rule (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    cost_subject_id BIGINT NOT NULL,
    allocation_basis VARCHAR(50) NOT NULL,
    allocation_cycle VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0, remark TEXT NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_allocation_subject ON overhead_allocation_rule (tenant_id, cost_subject_id, deleted_flag);

CREATE TABLE IF NOT EXISTS overhead_allocation_record (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    rule_id BIGINT NOT NULL, source_project_id BIGINT NOT NULL,
    target_project_id BIGINT NOT NULL, cost_subject_id BIGINT NOT NULL,
    allocation_date DATE NOT NULL, source_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    allocated_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    allocation_ratio DECIMAL(5,4) NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) NULL,
    PRIMARY KEY (id)
);
