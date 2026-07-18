-- V81: Init accounting_entry (H2 compatible)
CREATE TABLE IF NOT EXISTS accounting_entry (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    entry_code VARCHAR(64) NOT NULL, entry_date DATE NOT NULL,
    entry_type VARCHAR(50) NOT NULL, source_type VARCHAR(50) NOT NULL,
    source_id BIGINT NOT NULL,
    entry_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    total_debit DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_credit DECIMAL(18,2) NOT NULL DEFAULT 0,
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_entry_code ON accounting_entry (tenant_id, entry_code, deleted_flag);

CREATE TABLE IF NOT EXISTS accounting_entry_line (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    entry_id BIGINT NOT NULL, line_no INT NOT NULL DEFAULT 1,
    direction VARCHAR(10) NOT NULL,
    cost_subject_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    summary VARCHAR(500) NULL,
    PRIMARY KEY (id)
);
