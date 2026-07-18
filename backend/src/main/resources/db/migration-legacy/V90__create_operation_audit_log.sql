-- V90__create_operation_audit_log.sql
-- 操作审计日志表
CREATE TABLE sys_operation_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT,
    operation_type VARCHAR(50) NOT NULL COMMENT 'LOGIN/LOGOUT/CREATE/UPDATE/DELETE/SUBMIT/APPROVE/UPLOAD/DOWNLOAD',
    business_type VARCHAR(50) COMMENT 'SETTLEMENT/CONTRACT/RECEIPT/INVOICE etc',
    business_id VARCHAR(100),
    http_method VARCHAR(10),
    request_path VARCHAR(500),
    success_flag TINYINT(1) NOT NULL,
    error_code VARCHAR(50),
    source_ip VARCHAR(50),
    duration_ms INT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_created (tenant_id, created_at),
    INDEX idx_tenant_biz (tenant_id, business_type, business_id),
    INDEX idx_tenant_user_created (tenant_id, user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';
