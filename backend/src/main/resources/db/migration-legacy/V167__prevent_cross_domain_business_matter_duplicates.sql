ALTER TABLE ct_contract_change ADD COLUMN business_matter_key VARCHAR(100) NULL COMMENT '跨域业务事项唯一键';
ALTER TABLE var_order ADD COLUMN business_matter_key VARCHAR(100) NULL COMMENT '跨域业务事项唯一键';

CREATE TABLE business_matter_registry (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NULL,
    matter_key VARCHAR(100) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    active_token TINYINT NULL DEFAULT 1,
    resolved_at DATETIME NULL,
    resolved_by BIGINT NULL,
    resolution_note VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_matter_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_business_matter_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_business_matter_active (tenant_id, project_id, matter_key, active_token),
    UNIQUE KEY uk_business_matter_source (tenant_id, source_type, source_id, active_token),
    KEY idx_business_matter_contract (tenant_id, contract_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同变更与现场签证跨域事项唯一登记';

CREATE INDEX idx_ct_change_matter_key ON ct_contract_change(tenant_id, project_id, business_matter_key);
CREATE INDEX idx_var_order_matter_key ON var_order(tenant_id, project_id, business_matter_key);
