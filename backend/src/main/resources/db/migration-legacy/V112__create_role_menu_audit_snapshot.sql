-- V112__create_role_menu_audit_snapshot.sql
-- 10C: minimal role-menu assignment audit snapshot.

CREATE TABLE sys_role_menu_audit_snapshot (
    id BIGINT NOT NULL COMMENT '快照ID，雪花ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    operator_id BIGINT NULL COMMENT '操作者用户ID',
    role_id BIGINT NOT NULL COMMENT '目标角色ID',
    before_menu_ids TEXT NULL COMMENT '变更前菜单ID快照',
    after_menu_ids TEXT NULL COMMENT '变更后菜单ID快照',
    success_flag TINYINT(1) NOT NULL COMMENT '是否成功：1成功，0失败',
    error_summary VARCHAR(500) NULL COMMENT '失败错误摘要',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_role_menu_audit_tenant_role_created (tenant_id, role_id, created_at),
    KEY idx_role_menu_audit_tenant_operator_created (tenant_id, operator_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单绑定变更审计快照';
