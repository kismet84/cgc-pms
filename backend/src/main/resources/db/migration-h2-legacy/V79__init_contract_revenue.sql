-- V79: Init contract_revenue (H2 compatible)
CREATE TABLE IF NOT EXISTS contract_revenue (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    revenue_code VARCHAR(64) NOT NULL,
    revenue_date DATE NOT NULL,
    progress_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    progress_desc VARCHAR(500) NULL,
    revenue_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    revenue_tax DECIMAL(18,2) NOT NULL DEFAULT 0,
    revenue_amount_with_tax DECIMAL(18,2) NOT NULL DEFAULT 0,
    billed_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    billed_tax DECIMAL(18,2) NOT NULL DEFAULT 0,
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    cost_item_id BIGINT NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_revenue_code ON contract_revenue (tenant_id, revenue_code, deleted_flag);
CREATE INDEX IF NOT EXISTS idx_revenue_contract ON contract_revenue (contract_id);

ALTER TABLE cost_summary ADD COLUMN IF NOT EXISTS confirmed_revenue DECIMAL(18,2) DEFAULT 0 NOT NULL;
