-- Persist and enforce tenant ownership on RBAC association tables.
SET NAMES utf8mb4;

ALTER TABLE sys_user_role ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER id;
UPDATE sys_user_role ur JOIN sys_user u ON u.id = ur.user_id SET ur.tenant_id = u.tenant_id;

ALTER TABLE sys_role_menu ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER id;
UPDATE sys_role_menu rm JOIN sys_role r ON r.id = rm.role_id SET rm.tenant_id = r.tenant_id;

ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_tenant_id (tenant_id, id);
ALTER TABLE sys_role ADD UNIQUE KEY uk_sys_role_tenant_id (tenant_id, id);
ALTER TABLE sys_menu ADD UNIQUE KEY uk_sys_menu_tenant_id (tenant_id, id);

ALTER TABLE sys_user_role
    DROP INDEX uk_sys_user_role,
    DROP INDEX idx_sys_user_role_role,
    ADD UNIQUE KEY uk_sys_user_role (tenant_id, user_id, role_id),
    ADD KEY idx_sys_user_role_role (tenant_id, role_id),
    ADD CONSTRAINT fk_sys_user_role_user FOREIGN KEY (tenant_id, user_id)
        REFERENCES sys_user (tenant_id, id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_sys_user_role_role FOREIGN KEY (tenant_id, role_id)
        REFERENCES sys_role (tenant_id, id) ON DELETE RESTRICT;

ALTER TABLE sys_role_menu
    DROP INDEX uk_sys_role_menu,
    DROP INDEX idx_sys_role_menu_menu,
    ADD UNIQUE KEY uk_sys_role_menu (tenant_id, role_id, menu_id),
    ADD KEY idx_sys_role_menu_menu (tenant_id, menu_id),
    ADD CONSTRAINT fk_sys_role_menu_role FOREIGN KEY (tenant_id, role_id)
        REFERENCES sys_role (tenant_id, id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_sys_role_menu_menu FOREIGN KEY (tenant_id, menu_id)
        REFERENCES sys_menu (tenant_id, id) ON DELETE RESTRICT;
