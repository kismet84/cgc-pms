CREATE TABLE project_budget (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    version_no VARCHAR(32) NOT NULL,
    budget_name VARCHAR(200) NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    approval_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    active_flag TINYINT NOT NULL DEFAULT 0,
    active_token BIGINT NULL,
    effective_at TIMESTAMP NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    CONSTRAINT fk_project_budget_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT uk_project_budget_version UNIQUE (tenant_id, project_id, version_no, deleted_flag),
    CONSTRAINT uk_project_budget_active UNIQUE (tenant_id, active_token)
);
CREATE INDEX idx_project_budget_project_status ON project_budget(tenant_id, project_id, status);

CREATE TABLE project_budget_line (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    budget_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    cost_subject_id BIGINT NOT NULL,
    budget_amount DECIMAL(18,2) NOT NULL,
    reserved_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    consumed_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    CONSTRAINT fk_budget_line_budget FOREIGN KEY (budget_id) REFERENCES project_budget(id) ON DELETE RESTRICT,
    CONSTRAINT fk_budget_line_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_budget_line_subject FOREIGN KEY (cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT uk_budget_line_subject UNIQUE (tenant_id, budget_id, cost_subject_id, deleted_flag)
);
CREATE INDEX idx_budget_line_project_subject ON project_budget_line(tenant_id, project_id, cost_subject_id);

CREATE TABLE budget_ledger (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    budget_id BIGINT NOT NULL,
    budget_line_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    business_id BIGINT NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    reserved_balance DECIMAL(18,2) NOT NULL,
    consumed_balance DECIMAL(18,2) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(500) NULL,
    CONSTRAINT fk_budget_ledger_budget FOREIGN KEY (budget_id) REFERENCES project_budget(id) ON DELETE RESTRICT,
    CONSTRAINT fk_budget_ledger_line FOREIGN KEY (budget_line_id) REFERENCES project_budget_line(id) ON DELETE RESTRICT,
    CONSTRAINT fk_budget_ledger_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT uk_budget_ledger_idempotency UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX idx_budget_ledger_business ON budget_ledger(tenant_id, business_type, business_id);
CREATE INDEX idx_budget_ledger_line_time ON budget_ledger(tenant_id, budget_line_id, created_at);

CREATE TABLE contract_budget_allocation (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    budget_line_id BIGINT NOT NULL,
    allocated_amount DECIMAL(18,2) NOT NULL,
    reserved_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    consumed_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    CONSTRAINT fk_contract_budget_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_contract_budget_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
    CONSTRAINT fk_contract_budget_line FOREIGN KEY (budget_line_id) REFERENCES project_budget_line(id) ON DELETE RESTRICT,
    CONSTRAINT uk_contract_budget_line UNIQUE (tenant_id, contract_id, budget_line_id, deleted_flag)
);
CREATE INDEX idx_contract_budget_project ON contract_budget_allocation(tenant_id, project_id);
