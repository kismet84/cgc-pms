CREATE TABLE mandatory_audit_expectation (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    audit_domain VARCHAR(16) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    business_id BIGINT NOT NULL,
    command_key VARCHAR(128) NOT NULL,
    project_id BIGINT NULL,
    expected_hash CHAR(64) NOT NULL,
    expected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mandatory_audit_expectation
        (tenant_id, audit_domain, event_type, business_type, business_id, command_key),
    KEY idx_mandatory_audit_expectation_project (tenant_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='V290 起关键领域审计精确命令键分母；不承载审计载荷';
