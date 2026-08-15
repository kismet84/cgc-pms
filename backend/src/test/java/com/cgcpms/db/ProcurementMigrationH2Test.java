package com.cgcpms.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ProcurementMigrationH2Test {

    private static final String ACTIVE = "classpath:db/migration-h2";
    private static final String LEGACY = "filesystem:src/main/resources/db/migration-h2-legacy";
    private static final String JAVA = "classpath:com/cgcpms/common/migration";

    @Test
    void multiTenantDictionaryRowsUseDistinctGlobalIds() {
        Flyway before = beforeProcurement("multi_tenant");
        execute(before, """
                INSERT INTO sys_dict_group
                    (id, tenant_id, group_code, group_name, order_num, status)
                VALUES (9262001, 9, 'CONTRACT', '商务合同', 40, 'ENABLE'),
                       (9262002, 9, 'SUPPLY_CHAIN', '供应链', 50, 'ENABLE')
                """);
        execute(before, """
                INSERT INTO sys_dict_type
                    (id, tenant_id, group_id, dict_code, dict_name, dict_class, status)
                VALUES (9262010, 9, 9262002, 'purchase_order_status', '采购订单状态', 'STATE_MACHINE', 'ENABLE')
                """);
        execute(before, """
                INSERT INTO cost_subject
                    (id, tenant_id, parent_id, subject_code, subject_name, subject_type,
                     account_category, level, sort_order, status, deleted_flag)
                VALUES (9262030, 9, 0, '5401.01', '租户人工成本', 'LABOR', 'COST', 2, 1, 'ENABLE', 0),
                       (9262031, 9, 0, '5401.03', '租户目标成本', 'OTHER', 'COST', 2, 9, 'DISABLE', 0),
                       (9262032, 9, 9262031, '5401.03.99', '租户旧目标科目', 'OTHER', 'COST', 3, 99, 'ENABLE', 0),
                       (9262033, 9, 0, '5401.04', '租户现场管理成本', 'OVERHEAD', 'COST', 2, 4, 'ENABLE', 0)
                """);

        Flyway current = current("multi_tenant");
        current.migrate();

        assertEquals("299", current.info().current().getVersion().getVersion());
        assertEquals(19, count(current, """
                SELECT COUNT(*) FROM cost_subject
                WHERE tenant_id=0 AND (subject_code='5401.01' OR subject_code LIKE '5401.01.%'
                   OR subject_code='5401.04' OR subject_code LIKE '5401.04.%')
                """));
        assertEquals(1, count(current, """
                SELECT COUNT(*) FROM cost_subject
                WHERE tenant_id=9 AND subject_code='5401.01' AND subject_name='租户人工成本'
                """));
        assertEquals(10, count(current, """
                SELECT COUNT(*) FROM cost_subject child
                JOIN cost_subject parent ON parent.id=child.parent_id AND parent.tenant_id=child.tenant_id
                WHERE parent.tenant_id=9 AND parent.subject_code='5401.03'
                  AND child.subject_code LIKE '5401.03.%' AND child.deleted_flag=0
                """));
        assertEquals(1, count(current, """
                SELECT COUNT(*) FROM cost_subject
                WHERE tenant_id=9 AND subject_code='5401.03' AND subject_name='项目目标成本'
                  AND subject_type='TARGET_COST' AND status='ENABLE'
                """));
        assertEquals(0, count(current, """
                SELECT COUNT(*) FROM cost_subject child
                JOIN cost_subject parent ON parent.id=child.parent_id AND parent.tenant_id=child.tenant_id
                WHERE parent.tenant_id=9 AND parent.subject_code='5401.04'
                  AND child.subject_code IN ('5401.04.06','5401.04.15')
                  AND child.deleted_flag=0
                """));
        assertEquals(0, count(current, """
                SELECT COUNT(*) FROM cost_subject
                WHERE subject_code IN ('5401.04.08','5401.04.09','5401.04.10','5401.04.11',
                                       '5401.04.12','5401.04.13','5401.04.14','5401.04.16',
                                       '5401.04.17','5401.04.18','5401.04.19')
                """));
        assertEquals(0, count(current, """
                SELECT COUNT(*) FROM cost_subject
                WHERE tenant_id IN (0, 9) AND subject_code='5401.04.15' AND deleted_flag=0
                """));
        assertEquals(2, count(current, """
                SELECT COUNT(*) FROM sys_dict_data d
                JOIN sys_dict_type t ON t.id=d.dict_type_id
                WHERE t.dict_code='purchase_order_status' AND d.dict_value='PARTIAL_RECEIVED'
                """));
        long tenantZeroId = scalar(current, """
                SELECT d.id FROM sys_dict_data d JOIN sys_dict_type t ON t.id=d.dict_type_id
                WHERE t.tenant_id=0 AND t.dict_code='purchase_order_status' AND d.dict_value='PARTIAL_RECEIVED'
                """);
        long tenantNineId = scalar(current, """
                SELECT d.id FROM sys_dict_data d JOIN sys_dict_type t ON t.id=d.dict_type_id
                WHERE t.tenant_id=9 AND t.dict_code='purchase_order_status' AND d.dict_value='PARTIAL_RECEIVED'
                """);
        assertNotEquals(tenantZeroId, tenantNineId);

        assertEquals(3, count(current, """
                SELECT COUNT(*) FROM biz_document_template
                WHERE tenant_id=0 AND business_type IN ('PURCHASE_REQUEST','PURCHASE_ORDER','MATERIAL_RECEIPT')
                  AND deleted_flag=0
                """));
        assertEquals(3, count(current, """
                SELECT COUNT(*) FROM biz_document_template
                WHERE tenant_id=9 AND business_type IN ('PURCHASE_REQUEST','PURCHASE_ORDER','MATERIAL_RECEIPT')
                  AND deleted_flag=0
                """));
        assertEquals(6, count(current, """
                SELECT COUNT(*) FROM biz_document_default_binding binding
                JOIN biz_document_template template
                  ON template.tenant_id=binding.tenant_id AND template.id=binding.template_id
                JOIN biz_document_template_version version
                  ON version.tenant_id=binding.tenant_id AND version.id=binding.template_version_id
                WHERE template.business_type IN ('PURCHASE_REQUEST','PURCHASE_ORDER','MATERIAL_RECEIPT')
                  AND version.status='PUBLISHED'
                """));
        assertEquals(2, count(current, """
                SELECT COUNT(*) FROM biz_document_template template
                JOIN biz_document_template_version version
                  ON version.tenant_id=template.tenant_id AND version.template_id=template.id
                WHERE template.template_code='SYSTEM_PURCHASE_ORDER_V1'
                  AND version.schema_version='purchase-order.v1'
                  AND version.content_hash='1813cad4088c22dea0616574c4fd24c3a03b00fe1a6cbd1a1957bec7b952f54b'
                  AND version.status='PUBLISHED'
                """));
        long tenantZeroOrderTemplateId = scalar(current, """
                SELECT id FROM biz_document_template
                WHERE tenant_id=0 AND template_code='SYSTEM_PURCHASE_ORDER_V1'
                """);
        long tenantNineOrderTemplateId = scalar(current, """
                SELECT id FROM biz_document_template
                WHERE tenant_id=9 AND template_code='SYSTEM_PURCHASE_ORDER_V1'
                """);
        assertNotEquals(tenantZeroOrderTemplateId, tenantNineOrderTemplateId);
    }

    @Test
    void uniqueHistoricalMaterialMatchBackfillsAndAmbiguousMatchStaysUnresolved() {
        Flyway before = beforeProcurement("material_match");
        execute(before, "SET REFERENTIAL_INTEGRITY FALSE");
        execute(before, """
                INSERT INTO ct_contract
                    (id, tenant_id, project_id, contract_code, contract_name, contract_type,
                     party_a_id, party_b_id, contract_amount, current_amount, paid_amount,
                     contract_status, approval_status, deleted_flag)
                VALUES (9262101, 9, 9262199, 'CT-UNIQUE', '唯一匹配合同', 'PURCHASE',
                        9262197, 9262198, 100, 100, 0, 'PERFORMING', 'APPROVED', 0),
                       (9262102, 9, 9262199, 'CT-AMBIGUOUS', '歧义匹配合同', 'PURCHASE',
                        9262197, 9262198, 100, 100, 0, 'PERFORMING', 'APPROVED', 0)
                """);
        execute(before, """
                INSERT INTO md_material
                    (id, tenant_id, material_code, material_name, specification, unit, status, deleted_flag)
                VALUES (9262111, 9, 'MAT-UNIQUE', '钢筋', 'HRB400', '吨', 'ENABLE', 0),
                       (9262112, 9, 'MAT-A1', '水泥', 'P.O42.5', '吨', 'ENABLE', 0),
                       (9262113, 9, 'MAT-A2', '水泥', 'P.O42.5', '吨', 'ENABLE', 0)
                """);
        execute(before, """
                INSERT INTO ct_contract_item
                    (id, tenant_id, contract_id, item_code, item_name, item_spec, unit, deleted_flag)
                VALUES (9262121, 9, 9262101, 'CI-UNIQUE', '钢筋', 'HRB400', '吨', 0),
                       (9262122, 9, 9262102, 'CI-AMBIGUOUS', '水泥', 'P.O42.5', '吨', 0)
                """);
        execute(before, "SET REFERENTIAL_INTEGRITY TRUE");

        Flyway current = current("material_match");
        current.migrate();

        assertEquals(1, count(current,
                "SELECT COUNT(*) FROM ct_contract_item WHERE id=9262121 AND material_id=9262111"));
        assertEquals(1, count(current,
                "SELECT COUNT(*) FROM ct_contract_item WHERE id=9262122 AND material_id IS NULL"));
    }

    private static Flyway beforeProcurement(String name) {
        Flyway flyway = Flyway.configure()
                .dataSource(url(name), "sa", "")
                .locations(ACTIVE, LEGACY, JAVA)
                .target(MigrationVersion.fromVersion("255"))
                .cleanDisabled(false)
                .load();
        flyway.migrate();
        return flyway;
    }

    private static Flyway current(String name) {
        return Flyway.configure()
                .dataSource(url(name), "sa", "")
                .locations(ACTIVE, LEGACY, JAVA)
                .cleanDisabled(false)
                .load();
    }

    private static String url(String name) {
        return "jdbc:h2:mem:cgc_procurement_" + name
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    }

    private static int count(Flyway flyway, String sql) {
        return Math.toIntExact(scalar(flyway, sql));
    }

    private static long scalar(Flyway flyway, String sql) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to query procurement migration facts", exception);
        }
    }

    private static void execute(Flyway flyway, String sql) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare procurement migration fixture", exception);
        }
    }
}
