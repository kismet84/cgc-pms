-- V112__create_role_menu_audit_snapshot.sql
-- H2 compatible: 10C minimal role-menu assignment audit snapshot.

CREATE TABLE sys_role_menu_audit_snapshot (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    operator_id BIGINT NULL,
    role_id BIGINT NOT NULL,
    before_menu_ids CLOB NULL,
    after_menu_ids CLOB NULL,
    success_flag TINYINT NOT NULL,
    error_summary VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_role_menu_audit_tenant_role_created
    ON sys_role_menu_audit_snapshot(tenant_id, role_id, created_at);
CREATE INDEX idx_role_menu_audit_tenant_operator_created
    ON sys_role_menu_audit_snapshot(tenant_id, operator_id, created_at);
