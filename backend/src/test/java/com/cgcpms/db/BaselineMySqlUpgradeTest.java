package com.cgcpms.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "CGCPMS_M52_MYSQL_UPGRADE", matches = "true")
class BaselineMySqlUpgradeTest {

    private static final String LEGACY = "classpath:db/migration-legacy";
    private static final String ACTIVE = "classpath:db/migration";

    @Test
    void existingV180MySqlIgnoresBaselineAndUpgradesToLatestVersion() {
        String url = required("SPRING_DATASOURCE_URL");
        String username = required("SPRING_DATASOURCE_USERNAME");
        String password = required("SPRING_DATASOURCE_PASSWORD");

        Flyway old = Flyway.configure()
                .dataSource(url, username, password)
                .locations(LEGACY)
                .target(MigrationVersion.fromVersion("180"))
                .load();
        old.migrate();
        assertEquals("180", old.info().current().getVersion().getVersion());

        Flyway beforeCleanup = Flyway.configure()
                .dataSource(url, username, password)
                .locations(ACTIVE, LEGACY)
                .target(MigrationVersion.fromVersion("250"))
                .load();
        beforeCleanup.migrate();
        execute(beforeCleanup,
                "SET FOREIGN_KEY_CHECKS=0",
                """
                INSERT INTO ct_contract
                    (id, tenant_id, project_id, contract_code, contract_name, contract_type,
                     party_a_id, party_b_id, contract_amount, current_amount, paid_amount, deleted_flag)
                VALUES (9251001, 7, 9251010, 'CT-MYSQL-V252', 'MySQL V252合同', 'PURCHASE',
                        9251011, 9251012, 100, 1, 99, 0)
                """,
                """
                INSERT INTO ct_contract_change
                    (id, tenant_id, project_id, contract_id, change_code, change_name, change_type,
                     before_amount, change_amount, after_amount, approval_status, effective_flag, deleted_flag)
                VALUES (9251002, 7, 9251010, 9251001, 'CH-MYSQL-V252', '已生效变更', 'AMOUNT',
                        100, 20, 120, 'APPROVED', 1, 0),
                       (9251003, 7, 9251010, 9251001, 'CH-MYSQL-PENDING', '未生效变更', 'AMOUNT',
                        120, 10, 130, 'APPROVED', 0, 0)
                """,
                """
                INSERT INTO pay_record
                    (id, tenant_id, project_id, pay_application_id, contract_id,
                     pay_amount, pay_date, pay_status, deleted_flag)
                VALUES (9251004, 7, 9251010, 9251020, 9251001, 30, CURRENT_DATE, 'SUCCESS', 0),
                       (9251005, 7, 9251010, 9251021, 9251001, 40, CURRENT_DATE, 'FAILED', 0)
                """,
                "SET FOREIGN_KEY_CHECKS=1");

        Flyway beforePaymentTrace = Flyway.configure()
                .dataSource(url, username, password)
                .locations(ACTIVE, LEGACY)
                .target(MigrationVersion.fromVersion("275"))
                .load();
        beforePaymentTrace.migrate();
        execute(beforePaymentTrace,
                "SET FOREIGN_KEY_CHECKS=0",
                """
                INSERT INTO wf_instance
                    (id, tenant_id, template_id, business_type, business_id, title,
                     instance_status, initiator_id, deleted_flag)
                VALUES (9276004, 7, 9276005, 'PAY_REQUEST', 9276001, 'MySQL V276 approval',
                        'COMPLETED', 9276006, 0)
                """,
                """
                INSERT INTO pay_application
                    (id, tenant_id, project_id, apply_code, apply_amount, approved_amount,
                     actual_pay_amount, pay_type, pay_status, approval_status, approval_instance_id, deleted_flag)
                VALUES (9276001, 7, 9276010, 'PAY-MYSQL-V276', 100, 100, 100,
                        'DIRECT', 'PAID', 'APPROVED', 9276004, 0)
                """,
                """
                INSERT INTO pay_record
                    (id, tenant_id, project_id, pay_application_id, pay_amount, pay_date, pay_status, deleted_flag)
                VALUES (9276002, 7, 9276010, 9276001, 100, CURRENT_DATE, 'SUCCESS', 0)
                """,
                """
                INSERT INTO cash_journal_entry
                    (id, tenant_id, entry_no, direction, amount, business_date, summary, source_type,
                     source_id, status, closure_due_at, pay_record_id, deleted_flag)
                VALUES (9276003, 7, 'CJ-MYSQL-V276', 'OUT', 100, CURRENT_DATE, 'V276', 'PAY_RECORD',
                        9276002, 'PENDING_ARCHIVE', CURRENT_TIMESTAMP, 9276002, 0)
                """,
                "SET FOREIGN_KEY_CHECKS=1");

        Flyway current = Flyway.configure()
                .dataSource(url, username, password)
                .locations(ACTIVE, LEGACY)
                .load();
        current.migrate();

        assertEquals("307", current.info().current().getVersion().getVersion());
        assertEquals(20, count(current, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema=DATABASE()
                  AND table_name IN ('pm_project_member','ct_contract_change','org_company','org_department',
                                     'org_position','mat_purchase_request','mat_purchase_request_item',
                                     'cost_target','cost_target_item','mat_warehouse')
                  AND column_name IN ('created_at','updated_at')
                """));
        assertEquals(0, count(current, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema=DATABASE()
                  AND table_name IN ('pm_project_member','ct_contract_change','org_company','org_department',
                                     'org_position','mat_purchase_request','mat_purchase_request_item',
                                     'cost_target','cost_target_item','mat_warehouse')
                  AND column_name IN ('created_time','updated_time')
                """));
        assertEquals(1, count(current, """
                SELECT COUNT(*) FROM information_schema.views
                WHERE table_schema=DATABASE() AND table_name='v_business_audit_event'
                  AND security_type='INVOKER'
                """));
        assertEquals(0, count(current, "SELECT COUNT(*) FROM v_business_audit_event"));
        assertEquals(1, count(current, """
                SELECT COUNT(*) FROM ct_contract
                WHERE id=9251001 AND current_amount=120 AND paid_amount=30
                """));
        assertEquals(1, count(current, """
                SELECT COUNT(*) FROM cash_journal_entry
                WHERE id=9276003 AND tenant_id=7
                  AND pay_application_id=9276001 AND approval_instance_id=9276004
                """));
        assertEquals(10, count(current,
                "SELECT COUNT(*) FROM sys_role WHERE status='ENABLE' AND deleted_flag=0"));
        assertEquals(29, count(current,
                "SELECT COUNT(*) FROM wf_template WHERE enabled=1 AND deleted_flag=0 AND template_code LIKE 'M89-%'"));
        assertEquals(60, count(current, """
                SELECT COUNT(*) FROM wf_template_node n JOIN wf_template t ON t.id=n.template_id
                WHERE t.enabled=1 AND t.deleted_flag=0 AND t.template_code LIKE 'M89-%' AND n.deleted_flag=0
                """));
        assertEquals(10, count(current, """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema=DATABASE() AND table_name IN (
                  'cost_classification_snapshot','cost_classification_override','cost_unclassified_case',
                  'cost_project_config_request','cost_project_config_request_line','project_cost_subject_scope_history',
                  'cost_recalculation_batch','cost_recalculation_line','cost_recalculation_fact_reservation',
                  'cost_reversal_request')
                """));
        assertEquals(11, count(current, """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name='cost_item'
                  AND column_name IN ('original_cost_subject_id','mapping_version_id','assignment_rule_id',
                    'classification_snapshot_id','classification_override_id','classification_status',
                    'recognition_role','root_source_type','classification_business_category',
                    'adjustment_batch_id','original_cost_item_id')
                """));
        assertEquals(4, count(current, """
                SELECT COUNT(*) FROM sys_role_menu rm
                JOIN sys_role r ON r.tenant_id=rm.tenant_id AND r.id=rm.role_id
                JOIN sys_menu m ON m.tenant_id=rm.tenant_id AND m.id=rm.menu_id
                WHERE r.role_code='COMPANY_FINANCE' AND r.deleted_flag=0 AND m.deleted_flag=0
                  AND m.perms IN ('overhead:query','overhead:add','overhead:edit','overhead:execute')
                """));
        var validation = current.validateWithResult();
        assertTrue(validation.validationSuccessful, String.join("\n", validation.getAllErrorMessages()));
        assertFalse(Arrays.stream(current.info().applied())
                .anyMatch(info -> info.getType().name().contains("BASELINE")));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + "_REQUIRED");
        }
        return value;
    }

    private static int count(Flyway flyway, String sql) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to verify upgraded role permissions", exception);
        }
    }

    private static void execute(Flyway flyway, String... statements) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare MySQL upgrade fixture", exception);
        }
    }
}
