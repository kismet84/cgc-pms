package com.cgcpms.common.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;

/** H2 counterpart of the MySQL V197 RBAC tenant integrity migration. */
public class V197__enforce_rbac_tenant_integrity extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var conn = context.getConnection();
        execute(conn, "ALTER TABLE sys_user_role ADD COLUMN tenant_id BIGINT DEFAULT 0 NOT NULL");
        execute(conn, "UPDATE sys_user_role SET tenant_id=(SELECT u.tenant_id FROM sys_user u WHERE u.id=sys_user_role.user_id)");
        execute(conn, "ALTER TABLE sys_role_menu ADD COLUMN tenant_id BIGINT DEFAULT 0 NOT NULL");
        execute(conn, "UPDATE sys_role_menu SET tenant_id=(SELECT r.tenant_id FROM sys_role r WHERE r.id=sys_role_menu.role_id)");

        execute(conn, "ALTER TABLE sys_user ADD CONSTRAINT uk_sys_user_tenant_id UNIQUE (tenant_id,id)");
        execute(conn, "ALTER TABLE sys_role ADD CONSTRAINT uk_sys_role_tenant_id UNIQUE (tenant_id,id)");
        execute(conn, "ALTER TABLE sys_menu ADD CONSTRAINT uk_sys_menu_tenant_id UNIQUE (tenant_id,id)");

        execute(conn, "DROP INDEX IF EXISTS idx_sys_user_role_role");
        H2SoftDeleteUniqueMigration.rebuildUniqueConstraints(conn, "sys_user_role",
                "ALTER TABLE sys_user_role ADD CONSTRAINT uk_sys_user_role UNIQUE (tenant_id,user_id,role_id)");
        execute(conn, "CREATE INDEX idx_sys_user_role_role ON sys_user_role (tenant_id,role_id)");
        execute(conn, "ALTER TABLE sys_user_role ADD CONSTRAINT fk_sys_user_role_user FOREIGN KEY (tenant_id,user_id) REFERENCES sys_user (tenant_id,id) ON DELETE CASCADE");
        execute(conn, "ALTER TABLE sys_user_role ADD CONSTRAINT fk_sys_user_role_role FOREIGN KEY (tenant_id,role_id) REFERENCES sys_role (tenant_id,id) ON DELETE RESTRICT");

        execute(conn, "DROP INDEX IF EXISTS idx_sys_role_menu_menu");
        H2SoftDeleteUniqueMigration.rebuildUniqueConstraints(conn, "sys_role_menu",
                "ALTER TABLE sys_role_menu ADD CONSTRAINT uk_sys_role_menu UNIQUE (tenant_id,role_id,menu_id)");
        execute(conn, "CREATE INDEX idx_sys_role_menu_menu ON sys_role_menu (tenant_id,menu_id)");
        execute(conn, "ALTER TABLE sys_role_menu ADD CONSTRAINT fk_sys_role_menu_role FOREIGN KEY (tenant_id,role_id) REFERENCES sys_role (tenant_id,id) ON DELETE CASCADE");
        execute(conn, "ALTER TABLE sys_role_menu ADD CONSTRAINT fk_sys_role_menu_menu FOREIGN KEY (tenant_id,menu_id) REFERENCES sys_menu (tenant_id,id) ON DELETE RESTRICT");
    }

    private void execute(java.sql.Connection conn, String sql) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) { // SQL-SAFETY: migration-ddl
            ps.execute();
        }
    }
}
