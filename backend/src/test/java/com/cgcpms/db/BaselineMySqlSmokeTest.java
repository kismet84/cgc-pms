package com.cgcpms.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "CGCPMS_M52_MYSQL_BASELINE", matches = "true")
class BaselineMySqlSmokeTest {

    private static final String BOOTSTRAP_TEST_PASSWORD = "Aa9!" + UUID.randomUUID();

    @DynamicPropertySource
    static void bootstrapProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.locations",
                () -> "classpath:db/migration,classpath:db/migration-legacy");
        registry.add("cgc-pms.bootstrap.enabled", () -> true);
        registry.add("cgc-pms.bootstrap.administrator.password", () -> BOOTSTRAP_TEST_PASSWORD);
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void freshMySqlUsesBaselineAndBootstrapsWithoutBusinessFacts() {
        assertEquals("288", flyway.info().current().getVersion().getVersion());
        assertTrue(Arrays.stream(flyway.info().applied())
                .anyMatch(info -> info.getType().name().contains("BASELINE")));
        assertEquals(210, count("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_type='BASE TABLE' "
                + "AND table_name<>'flyway_schema_history'"));
        assertEquals(0, count("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='wf_idempotency' "
                + "AND column_name IN ('business_type','business_id','request_hash','response_json')"));

        assertEquals(13, count("SELECT COUNT(*) FROM sys_role WHERE deleted_flag=0"));
        assertTrue(count("SELECT COUNT(*) FROM sys_menu WHERE deleted_flag=0") > 0);
        assertTrue(count("SELECT COUNT(*) FROM sys_dict_type") > 0);
        assertTrue(count("SELECT COUNT(*) FROM cost_subject WHERE deleted_flag=0") > 0);
        assertTrue(count("SELECT COUNT(*) FROM wf_template WHERE deleted_flag=0") > 0);
        assertEquals(5, count("""
                SELECT COUNT(*) FROM sys_role_menu rm
                JOIN sys_role r ON r.tenant_id=rm.tenant_id AND r.id=rm.role_id
                JOIN sys_menu m ON m.tenant_id=rm.tenant_id AND m.id=rm.menu_id
                WHERE r.role_code IN
                  ('PROJECT_MANAGER','COST_MANAGER','DEPARTMENT_MANAGER','GENERAL_MANAGER','FINANCE')
                  AND r.deleted_flag=0 AND m.deleted_flag=0 AND m.perms='project:query'
                """));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM sys_role_menu rm
                JOIN sys_role r ON r.tenant_id=rm.tenant_id AND r.id=rm.role_id
                JOIN sys_menu m ON m.tenant_id=rm.tenant_id AND m.id=rm.menu_id
                WHERE r.role_code='PROJECT_MANAGER' AND r.deleted_flag=0
                  AND m.deleted_flag=0 AND m.perms='workflow:resubmit'
                """));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM sys_role_menu rm
                JOIN sys_role r ON r.tenant_id=rm.tenant_id AND r.id=rm.role_id
                JOIN sys_menu m ON m.tenant_id=rm.tenant_id AND m.id=rm.menu_id
                WHERE r.role_code='SUPER_ADMIN' AND r.deleted_flag=0
                  AND m.deleted_flag=0 AND m.perms='audit:query'
                """));

        assertEquals(0, count("SELECT COUNT(*) FROM pm_project WHERE deleted_flag=0"));
        assertEquals(0, count("SELECT COUNT(*) FROM md_material WHERE deleted_flag=0"));
        assertEquals(0, count("SELECT COUNT(*) FROM mat_stock WHERE deleted_flag=0"));
        assertEquals(0, count("SELECT COUNT(*) FROM wf_instance WHERE deleted_flag=0"));
        assertEquals(0, count("SELECT COUNT(*) FROM pay_record WHERE deleted_flag=0"));

        assertEquals(1, count("SELECT COUNT(*) FROM org_company WHERE tenant_id=0 AND deleted_flag=0"));
        assertEquals(1, count("SELECT COUNT(*) FROM org_department WHERE tenant_id=0 AND deleted_flag=0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user WHERE tenant_id=0 AND username='admin' "
                + "AND is_admin=1 AND deleted_flag=0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id "
                + "WHERE ur.tenant_id=0 AND r.role_code='SUPER_ADMIN' AND r.deleted_flag=0"));
        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "SELECT status FROM sys_bootstrap_state WHERE bootstrap_key='PLATFORM_ADMIN'", String.class));

        String stored = jdbcTemplate.queryForObject(
                "SELECT password FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0",
                String.class);
        assertTrue(stored != null && stored.startsWith("$2"));
        assertNotEquals(BOOTSTRAP_TEST_PASSWORD, stored);

        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbcTemplate.update("""
                INSERT INTO sys_file(
                    id,tenant_id,business_type,business_id,file_name,original_name,file_size,
                    content_type,storage_path,bucket_name,deleted_flag)
                VALUES (278001,278,'contract',278010,?, 'lower.txt',1,
                    'text/plain','tenants/278/contract/278010/files/278001/lower.txt','files',0)
                """, "a".repeat(64) + ".txt"));

        jdbcTemplate.update("""
                INSERT INTO sys_file(
                    id,tenant_id,business_type,business_id,file_name,original_name,file_size,
                    content_type,storage_path,bucket_name,deleted_flag)
                VALUES (279001,279,'CONTRACT',279010,'legacy-generated.pdf','legacy.pdf',1,
                    'application/pdf','legacy/generated.pdf','files',0),
                   (279002,279,'CONTRACT',279010,'legacy-generated.pdf','legacy-2.pdf',1,
                    'application/pdf','legacy/generated-2.pdf','files',0)
                """);
        assertEquals(2, count("SELECT COUNT(*) FROM sys_file WHERE id IN (279001,279002) "
                + "AND active_content_sha256 IS NULL"));
        jdbcTemplate.update("""
                INSERT INTO sys_file(
                    id,tenant_id,business_type,business_id,file_name,original_name,file_size,
                    content_type,storage_path,bucket_name,deleted_flag)
                VALUES (279003,277,'CONTRACT',277010,?, 'upper.pdf',1,
                    'application/pdf','legacy/upper.pdf','files',0)
                """, "A".repeat(64) + ".pdf");
        assertEquals(1, count("SELECT COUNT(*) FROM sys_file WHERE id=279003 "
                + "AND active_content_sha256 IS NULL"));

        jdbcTemplate.update("""
                INSERT INTO sys_file_object_task(
                    id,tenant_id,operation,source_bucket,source_path,idempotency_key,status,
                    attempt_count,next_retry_at,created_at,updated_at)
                VALUES (278101,278,'DELETE','files','Legacy/Foo','DELETE:files:Legacy/Foo',
                    'PENDING',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                   (278102,278,'DELETE','files','legacy/foo','DELETE:files:legacy/foo',
                    'PENDING',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        assertEquals(2, count("SELECT COUNT(*) FROM sys_file_object_task WHERE tenant_id=278"));
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
