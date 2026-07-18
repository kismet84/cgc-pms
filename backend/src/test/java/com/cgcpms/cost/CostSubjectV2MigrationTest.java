package com.cgcpms.cost;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class CostSubjectV2MigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsV2GovernanceAndPostingTables() {
        for (String table : new String[]{
                "cost_subject_mapping_version",
                "cost_subject_mapping_item",
                "cost_subject_assignment_rule",
                "project_cost_subject_scope",
                "bid_cost_target_transfer",
                "bid_cost_target_transfer_line",
                "finance_cost_allocation_batch",
                "finance_cost_allocation_line"
        }) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",
                    Integer.class, table);
            assertEquals(1, count, table);
        }
        Integer permissionCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE perms IN ('cost:subject:rule:query','cost:subject:scope:query','cost:subject:audit:query')",
                Integer.class);
        assertEquals(3, permissionCount);
    }

    @Test
    void removesLegacySubjectsAfterMigratingHistoricalReferences() {
        Integer legacyCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_subject
                WHERE id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
                """, Integer.class);
        assertEquals(0, legacyCount);

        Integer auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cost_subject_legacy_cleanup_audit", Integer.class);
        assertEquals(10, auditCount);

        Long migratedSubjectId = jdbc.queryForObject("""
                SELECT cost_subject_id FROM cost_item
                WHERE id=970000000000006901 AND deleted_flag=0
                """, Long.class);
        assertEquals(900045L, migratedSubjectId);
    }
}
