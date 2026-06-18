package com.cgcpms.common.migration;

/**
 * V51: 将 pm_project 唯一约束改为 (tenant_id, project_code, deleted_flag)。
 */
public class V51__fix_project_unique_with_deleted_flag extends H2SoftDeleteUniqueMigration {
    public V51__fix_project_unique_with_deleted_flag() {
        super("pm_project",
              "ALTER TABLE pm_project ADD UNIQUE KEY uk_pm_project_code (tenant_id, project_code, deleted_flag)");
    }
}
