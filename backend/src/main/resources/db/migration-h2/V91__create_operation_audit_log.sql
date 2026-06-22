-- V91__create_operation_audit_log.sql
-- H2 compatible: 操作审计日志表
CREATE TABLE sys_operation_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT,
    operation_type VARCHAR(50) NOT NULL,
    business_type VARCHAR(50),
    business_id VARCHAR(100),
    http_method VARCHAR(10),
    request_path VARCHAR(500),
    success_flag TINYINT(1) NOT NULL,
    error_code VARCHAR(50),
    source_ip VARCHAR(50),
    duration_ms INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tenant_created ON sys_operation_audit_log(tenant_id, created_at);
CREATE INDEX idx_tenant_biz ON sys_operation_audit_log(tenant_id, business_type, business_id);
CREATE INDEX idx_tenant_user_created ON sys_operation_audit_log(tenant_id, user_id, created_at);
