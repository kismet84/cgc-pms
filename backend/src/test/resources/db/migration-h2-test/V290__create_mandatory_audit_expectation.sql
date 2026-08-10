CREATE TABLE mandatory_audit_expectation (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    audit_domain VARCHAR(16) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    business_id BIGINT NOT NULL,
    command_key VARCHAR(128) NOT NULL,
    project_id BIGINT NULL,
    expected_hash CHAR(64) NOT NULL,
    expected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_mandatory_audit_expectation ON mandatory_audit_expectation
    (tenant_id, audit_domain, event_type, business_type, business_id, command_key);
CREATE INDEX idx_mandatory_audit_expectation_project ON mandatory_audit_expectation
    (tenant_id, project_id);
