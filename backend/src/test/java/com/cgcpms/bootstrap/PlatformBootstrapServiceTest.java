package com.cgcpms.bootstrap;

import com.cgcpms.bootstrap.config.PlatformBootstrapProperties;
import com.cgcpms.bootstrap.service.PlatformBootstrapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(PlatformBootstrapServiceTest.Config.class)
class PlatformBootstrapServiceTest {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformBootstrapService service;
    private final PlatformBootstrapProperties properties;

    @Autowired
    PlatformBootstrapServiceTest(JdbcTemplate jdbcTemplate, PlatformBootstrapService service,
                                 PlatformBootstrapProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.service = service;
        this.properties = properties;
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("CREATE TABLE sys_bootstrap_state (bootstrap_key VARCHAR(64) PRIMARY KEY, bootstrap_version INT NOT NULL, status VARCHAR(20) NOT NULL, completed_at TIMESTAMP NULL)");
        jdbcTemplate.execute("CREATE TABLE sys_role (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, role_code VARCHAR(64) NOT NULL, status VARCHAR(50) NOT NULL, deleted_flag TINYINT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE org_company (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, company_code VARCHAR(50) NOT NULL, company_name VARCHAR(200) NOT NULL, status VARCHAR(50), deleted_flag TINYINT, remark VARCHAR(500))");
        jdbcTemplate.execute("CREATE TABLE org_department (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, company_id BIGINT NOT NULL, parent_id BIGINT, dept_code VARCHAR(50) NOT NULL, dept_name VARCHAR(200) NOT NULL, order_num INT, status VARCHAR(50), deleted_flag TINYINT, remark VARCHAR(500))");
        jdbcTemplate.execute("CREATE TABLE sys_user (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, username VARCHAR(64) NOT NULL, password VARCHAR(200) NOT NULL, real_name VARCHAR(100), email VARCHAR(128), org_id BIGINT, status VARCHAR(50), is_admin TINYINT, deleted_flag TINYINT, remark VARCHAR(500))");
        jdbcTemplate.execute("CREATE TABLE sys_user_role (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, UNIQUE(tenant_id,user_id,role_id))");
        jdbcTemplate.update("INSERT INTO sys_bootstrap_state VALUES ('PLATFORM_ADMIN',1,'PENDING',NULL)");
        jdbcTemplate.update("INSERT INTO sys_role VALUES (1,0,'SUPER_ADMIN','ENABLE',0)");
        properties.setEnabled(true);
        properties.getAdministrator().setUsername("admin");
        properties.getAdministrator().setPassword("Strong#Password123");
    }

    @Test
    void firstRunCreatesExactlyOnePlatformIdentityAndSecondRunIsNoOp() {
        assertEquals(PlatformBootstrapService.Result.CREATED, service.bootstrap());
        String passwordHash = jdbcTemplate.queryForObject("SELECT password FROM sys_user", String.class);
        assertNotEquals("Strong#Password123", passwordHash);
        assertEquals(1, count("org_company"));
        assertEquals(1, count("org_department"));
        assertEquals(1, count("sys_user"));
        assertEquals(1, count("sys_user_role"));

        assertEquals(PlatformBootstrapService.Result.ALREADY_COMPLETED, service.bootstrap());
        assertEquals(1, count("sys_user"));
        assertEquals(passwordHash, jdbcTemplate.queryForObject("SELECT password FROM sys_user", String.class));
    }

    @Test
    void existingOrdinaryUserFailsClosedAndTransactionRollsBack() {
        jdbcTemplate.update("INSERT INTO sys_user VALUES (9,0,'admin','hash','普通用户',NULL,NULL,'ENABLE',0,0,NULL)");

        assertThrows(IllegalStateException.class, service::bootstrap);

        assertEquals(0, count("org_company"));
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT status FROM sys_bootstrap_state WHERE bootstrap_key='PLATFORM_ADMIN'", String.class));
        assertEquals(0, count("sys_user_role"));
    }

    @Test
    void existingSuperAdminIsAdoptedWithoutPasswordChange() {
        jdbcTemplate.update("INSERT INTO sys_user VALUES (9,0,'admin','existing-hash','管理员',NULL,NULL,'ENABLE',1,0,NULL)");
        jdbcTemplate.update("INSERT INTO sys_user_role VALUES (10,0,9,1)");

        assertEquals(PlatformBootstrapService.Result.ADOPTED, service.bootstrap());
        assertEquals("existing-hash", jdbcTemplate.queryForObject("SELECT password FROM sys_user WHERE id=9", String.class));
    }

    @Test
    void concurrentCallsSerializeOnBootstrapState() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return service.bootstrap();
            });
            var second = executor.submit(() -> {
                start.await();
                return service.bootstrap();
            });
            start.countDown();

            Set<PlatformBootstrapService.Result> results = Set.of(
                    first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(Set.of(PlatformBootstrapService.Result.CREATED,
                    PlatformBootstrapService.Result.ALREADY_COMPLETED), results);
        }
        assertEquals(1, count("sys_user"));
        assertEquals(1, count("sys_user_role"));
    }

    @Test
    void missingOrDisabledRoleFailsBeforeWritingOrganization() {
        jdbcTemplate.update("DELETE FROM sys_role");
        assertThrows(IllegalStateException.class, service::bootstrap);
        assertEquals(0, count("org_company"));

        jdbcTemplate.update("INSERT INTO sys_role VALUES (1,0,'SUPER_ADMIN','DISABLE',0)");
        assertThrows(IllegalStateException.class, service::bootstrap);
        assertEquals(0, count("org_company"));
        assertTrue(count("sys_user") == 0);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Config {
        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .setName("platform-bootstrap;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
                    .build();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        PlatformBootstrapProperties platformBootstrapProperties() {
            return PlatformBootstrapPropertiesTest.validProperties();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        PlatformBootstrapService platformBootstrapService(JdbcTemplate jdbcTemplate,
                                                           PasswordEncoder passwordEncoder,
                                                           PlatformBootstrapProperties properties) {
            return new PlatformBootstrapService(jdbcTemplate, passwordEncoder, properties);
        }
    }
}
