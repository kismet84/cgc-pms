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
        assertEquals("217", flyway.info().current().getVersion().getVersion());
        assertTrue(Arrays.stream(flyway.info().applied())
                .anyMatch(info -> info.getType().name().contains("BASELINE")));
        assertEquals(196, count("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_type='BASE TABLE' "
                + "AND table_name<>'flyway_schema_history'"));

        assertEquals(9, count("SELECT COUNT(*) FROM sys_role WHERE deleted_flag=0"));
        assertTrue(count("SELECT COUNT(*) FROM sys_menu WHERE deleted_flag=0") > 0);
        assertTrue(count("SELECT COUNT(*) FROM sys_dict_type") > 0);
        assertTrue(count("SELECT COUNT(*) FROM cost_subject WHERE deleted_flag=0") > 0);
        assertTrue(count("SELECT COUNT(*) FROM wf_template WHERE deleted_flag=0") > 0);

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
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
