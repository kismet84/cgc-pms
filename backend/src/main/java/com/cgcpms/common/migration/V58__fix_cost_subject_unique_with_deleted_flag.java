package com.cgcpms.common.migration;

/**
 * V58: 将 cost_subject 唯一约束改为 (tenant_id, subject_code, deleted_flag)。
 */
public class V58__fix_cost_subject_unique_with_deleted_flag extends H2SoftDeleteUniqueMigration {
    public V58__fix_cost_subject_unique_with_deleted_flag() {
        super("cost_subject",
              "ALTER TABLE cost_subject ADD UNIQUE KEY uk_cost_subject_code (tenant_id, subject_code, deleted_flag)");
    }
}
