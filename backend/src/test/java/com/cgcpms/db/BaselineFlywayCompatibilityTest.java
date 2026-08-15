package com.cgcpms.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaselineFlywayCompatibilityTest {

    private static final String ACTIVE = "classpath:db/migration-h2";
    private static final String LEGACY = "filesystem:src/main/resources/db/migration-h2-legacy";
    private static final String JAVA = "classpath:com/cgcpms/common/migration";

    @Test
    void freshH2DatabaseUsesB215AndContainsNoBusinessDemoFacts() {
        Flyway flyway = flyway("fresh", ACTIVE, LEGACY, JAVA);
        flyway.migrate();

        assertEquals("299", flyway.info().current().getVersion().getVersion());
        assertUnifiedAuditColumns(flyway);
        assertEquals(1, count(flyway, "INFORMATION_SCHEMA.COLUMNS",
                "TABLE_NAME='var_order_item' AND COLUMN_NAME='wbs_task_id'"));
        assertEquals(1, count(flyway, "INFORMATION_SCHEMA.COLUMNS",
                "TABLE_NAME='revenue_audit_event' AND COLUMN_NAME='command_key'"));
        assertEquals(1, count(flyway, "INFORMATION_SCHEMA.COLUMNS",
                "TABLE_NAME='finance_audit_event' AND COLUMN_NAME='command_key'"));
        assertEquals(1, count(flyway, "INFORMATION_SCHEMA.TABLES",
                "TABLE_NAME='mandatory_audit_expectation'"));
        assertEquals(0, count(flyway, "v_business_audit_event", "1=1"));
        assertThrows(IllegalStateException.class, () -> execute(flyway, """
                INSERT INTO var_order_item(id,tenant_id,var_order_id,wbs_task_id)
                VALUES(289001,0,289010,999999999)
                """));
        execute(flyway, """
                INSERT INTO revenue_audit_event
                    (id,tenant_id,event_type,business_type,business_id,command_key,event_at,payload_json,payload_hash)
                VALUES(289002,0,'TEST','TEST',289020,'CONFIRM',CURRENT_TIMESTAMP,'{}',
                       'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855')
                """);
        assertThrows(IllegalStateException.class, () -> execute(flyway, """
                INSERT INTO revenue_audit_event
                    (id,tenant_id,event_type,business_type,business_id,command_key,event_at,payload_json,payload_hash)
                VALUES(289003,0,'TEST','TEST',289020,'CONFIRM',CURRENT_TIMESTAMP,'{}',
                       'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855')
                """));
        execute(flyway, """
                INSERT INTO finance_audit_event
                    (id,tenant_id,event_type,business_type,business_id,command_key,event_at,payload_json,payload_hash)
                VALUES(289004,0,'TEST','TEST',289021,'CONFIRM',CURRENT_TIMESTAMP,'{}',
                       'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855')
                """);
        assertThrows(IllegalStateException.class, () -> execute(flyway, """
                INSERT INTO finance_audit_event
                    (id,tenant_id,event_type,business_type,business_id,command_key,event_at,payload_json,payload_hash)
                VALUES(289005,0,'TEST','TEST',289021,'CONFIRM',CURRENT_TIMESTAMP,'{}',
                       'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855')
                """));
        execute(flyway, """
                INSERT INTO mandatory_audit_expectation
                    (id,tenant_id,audit_domain,event_type,business_type,business_id,command_key,expected_hash)
                VALUES(290001,0,'FINANCE','TEST','TEST',290020,'CONFIRM',
                       'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855')
                """);
        assertThrows(IllegalStateException.class, () -> execute(flyway, """
                INSERT INTO mandatory_audit_expectation
                    (id,tenant_id,audit_domain,event_type,business_type,business_id,command_key,expected_hash)
                VALUES(290002,0,'FINANCE','TEST','TEST',290020,'CONFIRM',
                       'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855')
                """));
        assertEquals(1, count(flyway, "INFORMATION_SCHEMA.TABLES", "TABLE_NAME='sys_file_object_task'"));
        assertEquals(1, count(flyway, "INFORMATION_SCHEMA.TABLES", "TABLE_NAME='project_file_catalog'"));
        assertEquals(1, count(flyway, "INFORMATION_SCHEMA.TABLES", "TABLE_NAME='project_file_version_link'"));
        assertEquals(10, count(flyway, "sys_dict_data",
                "dict_type_id=(SELECT id FROM sys_dict_type WHERE dict_code='file_category' AND tenant_id=0)"));
        assertEquals(2, count(flyway, "sys_menu",
                "perms IN ('project:file:query','project:file:manage') AND deleted_flag=0"));
        execute(flyway, """
                INSERT INTO sys_file
                    (id,tenant_id,business_type,business_id,file_name,original_name,file_size,
                     content_type,storage_path,bucket_name,deleted_flag)
                VALUES (277001,277,'CONTRACT',277010,'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.pdf','a.pdf',1,
                        'application/pdf','tenants/277/CONTRACT/277010/files/277001/same.pdf','files',0)
                """);
        assertThrows(IllegalStateException.class, () -> execute(flyway, """
                INSERT INTO sys_file
                    (id,tenant_id,business_type,business_id,file_name,original_name,file_size,
                     content_type,storage_path,bucket_name,deleted_flag)
                VALUES (277002,277,'contract',277010,'other.pdf','b.pdf',1,
                        'application/pdf','legacy/lowercase.pdf','files',0)
                """));
        execute(flyway, """
                INSERT INTO sys_file
                    (id,tenant_id,business_type,business_id,file_name,original_name,file_size,
                     content_type,storage_path,bucket_name,deleted_flag)
                VALUES (277004,277,'CONTRACT',277010,'legacy-generated.pdf','legacy.pdf',1,
                        'application/pdf','legacy/generated.pdf','files',0),
                       (277005,277,'CONTRACT',277010,'legacy-generated.pdf','legacy-2.pdf',1,
                        'application/pdf','legacy/generated-2.pdf','files',0)
                """);
        assertEquals(2, count(flyway, "sys_file", "id IN (277004,277005) AND active_content_sha256 IS NULL"));
        execute(flyway, """
                INSERT INTO sys_file
                    (id,tenant_id,business_type,business_id,file_name,original_name,file_size,
                     content_type,storage_path,bucket_name,deleted_flag)
                VALUES (277006,277,'CONTRACT',277010,'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA.pdf','upper.pdf',1,
                        'application/pdf','legacy/upper.pdf','files',0)
                """);
        assertEquals(1, count(flyway, "sys_file", "id=277006 AND active_content_sha256 IS NULL"));
        assertThrows(IllegalStateException.class, () -> execute(flyway, """
                INSERT INTO sys_file
                    (id,tenant_id,business_type,business_id,file_name,original_name,file_size,
                     content_type,storage_path,bucket_name,deleted_flag)
                VALUES (277003,277,'CONTRACT',277010,'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.txt','c.txt',1,
                        'application/pdf','tenants/277/CONTRACT/277010/files/277003/same.pdf','files',0)
                """));
        assertEquals(10, count(flyway, "cost_subject", "parent_id=(SELECT id FROM cost_subject WHERE subject_code='5401.03')"));
        assertEquals(0, count(flyway, "cost_subject", "subject_code='5401.02' OR subject_code LIKE '5401.02.%'"));
        assertEquals(19, count(flyway, "cost_subject", """
                subject_code='5401.01' OR subject_code LIKE '5401.01.%'
                OR subject_code='5401.04' OR subject_code LIKE '5401.04.%'
                """));
        assertEquals(0, count(flyway, "cost_subject", """
                subject_code IN ('5401.02.05','5401.02.06','5401.04.06','5401.04.10','5401.04.11',
                                 '5401.04.12','5401.04.13','5401.04.15','5401.04.16','5401.04.17','5401.04.18')
                """));
        assertEquals(9, count(flyway, "sys_menu", """
                perms IN ('variation:order:add','variation:order:edit','variation:order:delete',
                          'variation:order:item:edit','cost:target:add','cost:target:edit',
                          'cost:target:delete','cost:target:activate','cost:summary:refresh')
                AND deleted_flag=0
                """));
        execute(flyway, """
                INSERT INTO mat_warehouse
                    (id, tenant_id, project_id, warehouse_code, warehouse_name, status, deleted_flag)
                VALUES (235001, 1, 235010, 'WH-20260728-001', '仓库一', 'ENABLE', 0)
                """);
        assertThrows(IllegalStateException.class, () -> execute(flyway, """
                INSERT INTO mat_warehouse
                    (id, tenant_id, project_id, warehouse_code, warehouse_name, status, deleted_flag)
                VALUES (235002, 1, 235010, 'WH-20260728-001', '仓库二', 'ENABLE', 0)
                """));
        assertTrue(Arrays.stream(flyway.info().applied())
                .anyMatch(info -> info.getType().name().contains("BASELINE")));
        assertEquals(10, count(flyway, "sys_role", "status='ENABLE' AND deleted_flag=0"));
        assertEquals(9, count(flyway, "sys_role", """
                status='ENABLE' AND deleted_flag=0 AND role_code IN
                ('COMPANY_OWNER','COMPANY_FINANCE','PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD',
                 'SAFETY_LEAD','CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE')
                """));
        assertEquals(0, count(flyway, "sys_role", """
                status='ENABLE' AND deleted_flag=0 AND role_code NOT IN
                ('COMPANY_OWNER','COMPANY_FINANCE','PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD',
                 'SAFETY_LEAD','CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE','SUPER_ADMIN')
                """));
        assertEquals(0, count(flyway, "sys_user"));
        assertEquals(0, count(flyway, "pm_project"));
        assertEquals(0, count(flyway, "md_material"));
        assertEquals(0, count(flyway, "mat_stock"));
        assertEquals(0, count(flyway, "wf_instance"));
        assertEquals(1, count(flyway, "sys_bootstrap_state"));
        assertEquals(1, count(flyway, "sys_menu", "perms='payment:direct'"));
        assertEquals(1, count(flyway, "sys_menu", """
                perms='material:dict:delete' AND deleted_flag=0
                AND parent_id=(SELECT id FROM sys_menu
                    WHERE tenant_id=0 AND perms='material:dict:list' AND deleted_flag=0)
                """));
        assertEquals(8, count(flyway, "sys_role_menu", """
                role_id IN (SELECT id FROM sys_role WHERE role_code IN
                  ('COMPANY_OWNER','COMPANY_FINANCE','PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD',
                   'SAFETY_LEAD','CONSTRUCTION_LEAD','PROCUREMENT_LEAD'))
                AND menu_id=(SELECT id FROM sys_menu WHERE perms='business:amount:view' AND deleted_flag=0)
                """));
        assertEquals(0, count(flyway, "sys_role_menu", """
                role_id=(SELECT id FROM sys_role WHERE role_code='EMPLOYEE' AND deleted_flag=0)
                AND menu_id=(SELECT id FROM sys_menu WHERE perms='business:amount:view' AND deleted_flag=0)
                """));
        assertEquals(7, count(flyway, "sys_role_menu", """
                role_id=(SELECT id FROM sys_role WHERE role_code='TECHNICAL_LEAD' AND deleted_flag=0)
                AND menu_id IN (SELECT id FROM sys_menu WHERE deleted_flag=0 AND perms IN
                  ('technical:drawing:receive','technical:drawing:review','technical:rfi:raise',
                   'technical:rfi:respond','technical:rfi:accept','technical:disclosure:maintain',
                   'technical:archive:confirm'))
                """));
        assertEquals(2, count(flyway, "sys_role_menu", """
                role_id=(SELECT id FROM sys_role WHERE role_code='CONSTRUCTION_LEAD' AND deleted_flag=0)
                AND menu_id IN (SELECT id FROM sys_menu WHERE deleted_flag=0 AND perms IN
                  ('site:daily:self','schedule:daily-progress:self'))
                """));
        assertEquals(1, count(flyway, "INFORMATION_SCHEMA.COLUMNS",
                "TABLE_NAME='project_period_plan' AND COLUMN_NAME='replaces_period_plan_id'"));
        assertEquals(30, count(flyway, "wf_template", "enabled=1 AND deleted_flag=0 AND template_code LIKE 'M89-%'"));
        assertEquals(65, count(flyway, "wf_template_node", """
                deleted_flag=0 AND template_id IN
                    (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0 AND template_code LIKE 'M89-%')
                """));
        assertEquals(0, count(flyway, "wf_template_node", """
                deleted_flag=0 AND template_id IN
                    (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0 AND template_code LIKE 'M89-%')
                AND CAST(approver_config AS VARCHAR) NOT REGEXP
                    'COMPANY_OWNER|COMPANY_FINANCE|PROJECT_MANAGER|PROJECT_ACCOUNTANT|TECHNICAL_LEAD|SAFETY_LEAD|CONSTRUCTION_LEAD|PROCUREMENT_LEAD|EMPLOYEE'
                """));
        assertEquals(1, count(flyway, "INFORMATION_SCHEMA.COLUMNS",
                "TABLE_NAME='wf_instance' AND COLUMN_NAME='security_policy_json'"));
        assertEquals(5, count(flyway, "INFORMATION_SCHEMA.COLUMNS", """
                TABLE_NAME='wf_node_instance' AND COLUMN_NAME IN
                    ('node_type','approver_config','allow_transfer','allow_add_sign','timeout_hours')
                """));
        assertEquals(1, count(flyway, "INFORMATION_SCHEMA.TABLES", "TABLE_NAME='bid_cost_target_transfer_request'"));
        assertEquals(1, count(flyway, "INFORMATION_SCHEMA.TABLES", "TABLE_NAME='finance_cost_allocation_request'"));
    }

    @Test
    void existingV180H2DatabaseIgnoresBaselineAndUpgradesThroughLegacyChain() {
        Flyway old = Flyway.configure()
                .dataSource(url("upgrade"), "sa", "")
                .locations(LEGACY, JAVA)
                .target(MigrationVersion.fromVersion("180"))
                .cleanDisabled(false)
                .load();
        old.migrate();
        assertEquals("180", old.info().current().getVersion().getVersion());

        Flyway current = flyway("upgrade", ACTIVE, LEGACY, JAVA);
        current.migrate();
        var validation = current.validateWithResult();
        assertTrue(validation.validationSuccessful, String.join("\n", validation.getAllErrorMessages()));

        assertEquals("299", current.info().current().getVersion().getVersion());
        assertUnifiedAuditColumns(current);
        assertEquals(9, count(current, "sys_menu", """
                perms IN ('variation:order:add','variation:order:edit','variation:order:delete',
                          'variation:order:item:edit','cost:target:add','cost:target:edit',
                          'cost:target:delete','cost:target:activate','cost:summary:refresh')
                AND deleted_flag=0
                """));
        assertEquals(0, count(current, "cost_subject", """
                subject_code IN ('5401.02.05','5401.02.06','5401.04.06','5401.04.10','5401.04.11',
                                 '5401.04.12','5401.04.13','5401.04.15','5401.04.16','5401.04.17','5401.04.18')
                """));
        assertEquals(1, count(current, "sys_menu", """
                perms='material:dict:delete' AND deleted_flag=0
                AND parent_id=(SELECT id FROM sys_menu
                    WHERE tenant_id=0 AND perms='material:dict:list' AND deleted_flag=0)
                """));
        assertEquals(10, count(current, "sys_role", "status='ENABLE' AND deleted_flag=0"));
        assertEquals(7, count(current, "sys_role_menu", """
                role_id=(SELECT id FROM sys_role WHERE role_code='TECHNICAL_LEAD' AND deleted_flag=0)
                AND menu_id IN (SELECT id FROM sys_menu WHERE deleted_flag=0 AND perms IN
                  ('technical:drawing:receive','technical:drawing:review','technical:rfi:raise',
                   'technical:rfi:respond','technical:rfi:accept','technical:disclosure:maintain',
                   'technical:archive:confirm'))
                """));
        assertEquals(2, count(current, "sys_role_menu", """
                role_id=(SELECT id FROM sys_role WHERE role_code='CONSTRUCTION_LEAD' AND deleted_flag=0)
                AND menu_id IN (SELECT id FROM sys_menu WHERE deleted_flag=0 AND perms IN
                  ('site:daily:self','schedule:daily-progress:self'))
                """));
        assertEquals(1, count(current, "INFORMATION_SCHEMA.COLUMNS",
                "TABLE_NAME='project_period_plan' AND COLUMN_NAME='replaces_period_plan_id'"));
        assertEquals(30, count(current, "wf_template", "enabled=1 AND deleted_flag=0 AND template_code LIKE 'M89-%'"));
        assertEquals(65, count(current, "wf_template_node", """
                deleted_flag=0 AND template_id IN
                    (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0 AND template_code LIKE 'M89-%')
                """));
        assertFalse(Arrays.stream(current.info().applied())
                .anyMatch(info -> info.getType().name().contains("BASELINE")));
    }

    @Test
    void v276BackfillsLegacyPaymentCashJournalTrace() {
        Flyway old = Flyway.configure()
                .dataSource(url("payment_cash_trace"), "sa", "")
                .locations(ACTIVE, LEGACY, JAVA)
                .target(MigrationVersion.fromVersion("275"))
                .cleanDisabled(false)
                .load();
        old.migrate();
        execute(old, "SET REFERENTIAL_INTEGRITY FALSE");
        execute(old, """
                INSERT INTO wf_instance
                    (id, tenant_id, template_id, business_type, business_id, title,
                     instance_status, initiator_id, deleted_flag)
                VALUES (276004, 276, 276005, 'PAY_REQUEST', 276001, 'V276 approval',
                        'COMPLETED', 276006, 0)
                """);
        execute(old, """
                INSERT INTO pay_application
                    (id, tenant_id, project_id, apply_code, apply_amount, approved_amount,
                     actual_pay_amount, pay_type, pay_status, approval_status, approval_instance_id, deleted_flag)
                VALUES (276001, 276, 276010, 'PAY-V276', 100, 100, 100,
                        'DIRECT', 'PAID', 'APPROVED', 276004, 0)
                """);
        execute(old, """
                INSERT INTO pay_record
                    (id, tenant_id, project_id, pay_application_id, pay_amount, pay_date, pay_status, deleted_flag)
                VALUES (276002, 276, 276010, 276001, 100, CURRENT_DATE, 'SUCCESS', 0)
                """);
        execute(old, """
                INSERT INTO cash_journal_entry
                    (id, tenant_id, entry_no, direction, amount, business_date, summary, source_type,
                     source_id, status, closure_due_at, pay_record_id, deleted_flag)
                VALUES (276003, 276, 'CJ-V276', 'OUT', 100, CURRENT_DATE, 'V276', 'PAY_RECORD',
                        276002, 'PENDING_ARCHIVE', CURRENT_TIMESTAMP, 276002, 0)
                """);
        execute(old, "SET REFERENTIAL_INTEGRITY TRUE");

        Flyway current = flyway("payment_cash_trace", ACTIVE, LEGACY, JAVA);
        current.migrate();

        assertEquals(1, count(current, "cash_journal_entry",
                "id=276003 AND tenant_id=276 AND pay_application_id=276001 AND approval_instance_id=276004"));
    }

    @Test
    void v271H2RejectsNegativeTenderAmountsAndInvalidInitiationBasis() {
        Flyway flyway = Flyway.configure()
                .dataSource(url("v271_checks"), "sa", "")
                .locations(ACTIVE, LEGACY, JAVA)
                .target(MigrationVersion.fromVersion("271"))
                .cleanDisabled(false)
                .load();
        flyway.migrate();

        execute(flyway, """
                INSERT INTO bid_cost
                    (id, tenant_id, bid_code, bid_project_name, bid_status, ceiling_price, final_bid_price)
                VALUES (271001, 271, 'BID-V271-001', 'V271约束测试投标', 'PREPARING', 100, 90)
                """);
        assertThrows(IllegalStateException.class,
                () -> execute(flyway, "UPDATE bid_cost SET ceiling_price=-0.01 WHERE id=271001"));
        assertThrows(IllegalStateException.class,
                () -> execute(flyway, "UPDATE bid_cost SET final_bid_price=-0.01 WHERE id=271001"));

        execute(flyway, """
                INSERT INTO pm_project
                    (id, tenant_id, project_code, project_name, initiation_basis)
                VALUES (271002, 271, 'V271-CHECK', 'V271约束测试项目', 'BID_AWARD')
                """);
        assertThrows(IllegalStateException.class,
                () -> execute(flyway, "UPDATE pm_project SET initiation_basis='MANUAL' WHERE id=271002"));
    }

    @Test
    void v282H2EnforcesProjectFileCodeVersionAndFileIdentity() {
        Flyway flyway = flyway("v282_project_file_constraints", ACTIVE, LEGACY, JAVA);
        flyway.migrate();
        execute(flyway, """
                INSERT INTO pm_project(id,tenant_id,project_code,project_name)
                VALUES(282101,282,'P282','项目文件约束测试')
                """);
        execute(flyway, """
                INSERT INTO sys_file(id,tenant_id,business_type,business_id,file_name,original_name,
                                     file_size,content_type,storage_path,bucket_name,virus_scan_status,deleted_flag)
                VALUES(282201,282,'PROJECT',282101,'a.pdf','a.pdf',1,'application/pdf','p/a.pdf','files','CLEAN',0),
                      (282202,282,'PROJECT',282101,'b.pdf','b.pdf',1,'application/pdf','p/b.pdf','files','CLEAN',0)
                """);
        execute(flyway, """
                INSERT INTO project_file_catalog(id,tenant_id,project_id,file_code,display_name,category_code,
                    source_kind,maintain_mode,created_at,updated_at,deleted_flag)
                VALUES(282301,282,282101,'FILE-P282-20260805-001','约束文件','OTHER',
                    'MANAGED','MANAGED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """);
        execute(flyway, """
                INSERT INTO project_file_version_link(id,tenant_id,catalog_id,version_no,sys_file_id,
                    preview_status,created_at,updated_at,deleted_flag)
                VALUES(282401,282,282301,1,282201,'READY',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """);
        assertThrows(IllegalStateException.class, () -> execute(flyway, """
                INSERT INTO project_file_catalog(id,tenant_id,project_id,file_code,display_name,category_code,
                    source_kind,maintain_mode,created_at,updated_at,deleted_flag)
                VALUES(282302,282,282101,'FILE-P282-20260805-001','重复编号','OTHER',
                    'MANAGED','MANAGED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """));
        assertThrows(IllegalStateException.class, () -> execute(flyway, """
                INSERT INTO project_file_version_link(id,tenant_id,catalog_id,version_no,sys_file_id,
                    preview_status,created_at,updated_at,deleted_flag)
                VALUES(282402,282,282301,1,282202,'READY',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """));
        assertThrows(IllegalStateException.class, () -> execute(flyway, """
                INSERT INTO project_file_version_link(id,tenant_id,catalog_id,version_no,sys_file_id,
                    preview_status,created_at,updated_at,deleted_flag)
                VALUES(282403,282,282301,2,282201,'READY',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """));
    }

    @Test
    void paymentRelationOrphanBlocksV226Upgrade() {
        Flyway old = Flyway.configure()
                .dataSource(url("payment_orphan"), "sa", "")
                .locations(LEGACY, JAVA)
                .target(MigrationVersion.fromVersion("225"))
                .cleanDisabled(false)
                .load();
        old.migrate();
        execute(old, "SET REFERENTIAL_INTEGRITY FALSE");
        execute(old, """
                INSERT INTO invoice_payment_allocation
                    (id, tenant_id, invoice_id, pay_record_id, pay_application_id, allocated_amount)
                VALUES (226001, 1, 226002, 226003, 226004, 1.00)
                """);
        execute(old, "SET REFERENTIAL_INTEGRITY TRUE");

        Flyway current = flyway("payment_orphan", ACTIVE, LEGACY, JAVA);

        assertThrows(FlywayException.class, current::migrate);
    }

    @Test
    void v251AndV252RemoveUnusedIdempotencyColumnsAndReconcileContractCaches() {
        Flyway old = Flyway.configure()
                .dataSource(url("field_cleanup"), "sa", "")
                .locations(ACTIVE, LEGACY, JAVA)
                .target(MigrationVersion.fromVersion("250"))
                .cleanDisabled(false)
                .load();
        old.migrate();
        execute(old, "SET REFERENTIAL_INTEGRITY FALSE");
        execute(old, """
                INSERT INTO ct_contract
                    (id, tenant_id, project_id, contract_code, contract_name, contract_type,
                     party_a_id, party_b_id, contract_amount, current_amount, paid_amount, deleted_flag)
                VALUES (251001, 7, 251010, 'CT-V252', 'V252合同', 'PURCHASE',
                        251011, 251012, 100, 1, 99, 0)
                """);
        execute(old, """
                INSERT INTO ct_contract_change
                    (id, tenant_id, project_id, contract_id, change_code, change_name, change_type,
                     before_amount, change_amount, after_amount, approval_status, effective_flag, deleted_flag)
                VALUES (251002, 7, 251010, 251001, 'CH-V252', 'V252变更', 'AMOUNT',
                        100, 20, 120, 'APPROVED', 1, 0)
                """);
        execute(old, """
                INSERT INTO ct_contract_change
                    (id, tenant_id, project_id, contract_id, change_code, change_name, change_type,
                     before_amount, change_amount, after_amount, approval_status, effective_flag, deleted_flag)
                VALUES (251004, 7, 251010, 251001, 'CH-V252-PENDING', '未生效变更', 'AMOUNT',
                        120, 10, 130, 'APPROVED', 0, 0)
                """);
        execute(old, """
                INSERT INTO pay_record
                    (id, tenant_id, project_id, pay_application_id, contract_id,
                     pay_amount, pay_date, pay_status, deleted_flag)
                VALUES (251003, 7, 251010, 251020, 251001, 30, CURRENT_DATE, 'SUCCESS', 0)
                """);
        execute(old, "SET REFERENTIAL_INTEGRITY TRUE");

        Flyway current = flyway("field_cleanup", ACTIVE, LEGACY, JAVA);
        current.migrate();

        assertEquals(1, count(current, "ct_contract", "id=251001 AND current_amount=120 AND paid_amount=30"));
        assertEquals(0, count(current, "information_schema.columns", "table_name='wf_idempotency'"
                + " AND column_name IN ('business_type','business_id','request_hash','response_json')"));
        assertEquals(1, count(current, "information_schema.indexes", "table_name='mat_purchase_order'"
                + " AND index_name='idx_purchase_order_request_source'"));
    }

    @Test
    void v236AlignsNavigationWithoutChangingRolePermissions() {
        Flyway old = Flyway.configure()
                .dataSource(url("menu_alignment"), "sa", "")
                .locations(ACTIVE, LEGACY, JAVA)
                .target(MigrationVersion.fromVersion("235"))
                .cleanDisabled(false)
                .load();
        old.migrate();
        List<String> roleMenus = rows(old, """
                SELECT CONCAT(tenant_id, ':', role_id, ':', menu_id)
                FROM sys_role_menu
                WHERE role_id<>2690001 AND menu_id NOT IN (605,608,921,932,933,962,963,964,965,966,1090,1091,1092,
                                                           21901,21902,21903,21904,22001,22002,22003,22004,22101,
                                                           2690100,2690101,2690111,2690112,2690113,
                                                           27401,27402,27403,27404,28201,28202,28301,28302,28303)
                ORDER BY tenant_id, role_id, menu_id
                """);
        List<String> rolePermissions = rows(old, """
                SELECT DISTINCT CONCAT(rm.tenant_id, ':', rm.role_id, ':', m.perms)
                FROM sys_role_menu rm
                JOIN sys_menu m ON m.tenant_id = rm.tenant_id AND m.id = rm.menu_id
                WHERE m.perms IS NOT NULL AND m.perms <> ''
                  AND rm.role_id<>2690001 AND rm.menu_id NOT IN (605,608,921,932,933,962,963,964,965,966,1090,1091,1092,
                                                                 21901,21902,21903,21904,22001,22002,22003,22004,22101,
                                                                 2690100,2690101,2690111,2690112,2690113,
                                                                 27401,27402,27403,27404,28201,28202,28301,28302,28303)
                ORDER BY 1
                """);

        Flyway current = Flyway.configure()
                .dataSource(url("menu_alignment"), "sa", "")
                .locations(ACTIVE, LEGACY, JAVA)
                .target(MigrationVersion.fromVersion("236"))
                .cleanDisabled(false)
                .load();
        current.migrate();

        assertEquals(roleMenus, rows(current, """
                SELECT CONCAT(tenant_id, ':', role_id, ':', menu_id)
                FROM sys_role_menu
                WHERE role_id<>2690001 AND menu_id NOT IN (605,608,921,932,933,962,963,964,965,966,1090,1091,1092,
                                                           21901,21902,21903,21904,22001,22002,22003,22004,22101,
                                                           2690100,2690101,2690111,2690112,2690113,
                                                           27401,27402,27403,27404,28201,28202,28301,28302,28303)
                ORDER BY tenant_id, role_id, menu_id
                """));
        assertEquals(rolePermissions, rows(current, """
                SELECT DISTINCT CONCAT(rm.tenant_id, ':', rm.role_id, ':', m.perms)
                FROM sys_role_menu rm
                JOIN sys_menu m ON m.tenant_id = rm.tenant_id AND m.id = rm.menu_id
                WHERE m.perms IS NOT NULL AND m.perms <> ''
                  AND rm.role_id<>2690001 AND rm.menu_id NOT IN (605,608,921,932,933,962,963,964,965,966,1090,1091,1092,
                                                                 21901,21902,21903,21904,22001,22002,22003,22004,22101,
                                                                 2690100,2690101,2690111,2690112,2690113,
                                                                 27401,27402,27403,27404,28201,28202,28301,28302,28303)
                ORDER BY 1
                """));
        assertEquals(2, count(current, "sys_menu",
                "id IN (23601,23602) AND perms IS NULL AND menu_type='MENU'"));
        assertEquals(0, count(current, "sys_role_menu", "menu_id IN (23601,23602)"));
        assertEquals(1, count(current, "sys_menu",
                "id=503 AND parent_id=909 AND path='/system/permissions'"));
        assertEquals(3, count(current, "sys_menu",
                "id IN (2134,2138,2139) AND menu_type='MENU' AND path IS NOT NULL"));
    }

    private static Flyway flyway(String name, String... locations) {
        return Flyway.configure()
                .dataSource(url(name), "sa", "")
                .locations(locations)
                .cleanDisabled(false)
                .load();
    }

    private static void assertUnifiedAuditColumns(Flyway flyway) {
        for (String table : List.of(
                "pm_project_member", "ct_contract_change", "org_company", "org_department",
                "org_position", "mat_purchase_request", "mat_purchase_request_item",
                "cost_target", "cost_target_item", "mat_warehouse")) {
            assertEquals(2, count(flyway, "INFORMATION_SCHEMA.COLUMNS",
                    "TABLE_NAME='" + table + "' AND COLUMN_NAME IN ('created_at','updated_at')"),
                    table + " must use canonical audit columns");
            assertEquals(0, count(flyway, "INFORMATION_SCHEMA.COLUMNS",
                    "TABLE_NAME='" + table + "' AND COLUMN_NAME IN ('created_time','updated_time')"),
                    table + " must not retain legacy audit columns");
        }
    }

    private static String url(String name) {
        return "jdbc:h2:mem:cgc_m52_" + name
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    }

    private static int count(Flyway flyway, String table) {
        return count(flyway, table, null);
    }

    private static int count(Flyway flyway, String table, String where) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM " + table
                     + (where == null ? "" : " WHERE " + where))) {
            result.next();
            return result.getInt(1);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to count " + table, exception);
        }
    }

    private static List<String> rows(Flyway flyway, String sql) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            var rows = new ArrayList<String>();
            while (result.next()) rows.add(result.getString(1));
            return rows;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to query migration facts", exception);
        }
    }

    private static void execute(Flyway flyway, String sql) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare migration fixture", exception);
        }
    }
}
