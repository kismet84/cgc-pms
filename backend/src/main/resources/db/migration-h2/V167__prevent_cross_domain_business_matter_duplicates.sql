ALTER TABLE ct_contract_change ADD COLUMN business_matter_key VARCHAR(100);
ALTER TABLE var_order ADD COLUMN business_matter_key VARCHAR(100);

CREATE TABLE business_matter_registry (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    contract_id BIGINT,
    matter_key VARCHAR(100) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    active_token TINYINT DEFAULT 1,
    resolved_at TIMESTAMP,
    resolved_by BIGINT,
    resolution_note VARCHAR(500),
    version INT NOT NULL DEFAULT 0,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_matter_project FOREIGN KEY (project_id) REFERENCES pm_project(id),
    CONSTRAINT fk_business_matter_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id),
    CONSTRAINT uk_business_matter_active UNIQUE (tenant_id, project_id, matter_key, active_token),
    CONSTRAINT uk_business_matter_source UNIQUE (tenant_id, source_type, source_id, active_token)
);

CREATE INDEX idx_business_matter_contract ON business_matter_registry(tenant_id, contract_id, status);
CREATE INDEX idx_ct_change_matter_key ON ct_contract_change(tenant_id, project_id, business_matter_key);
CREATE INDEX idx_var_order_matter_key ON var_order(tenant_id, project_id, business_matter_key);
