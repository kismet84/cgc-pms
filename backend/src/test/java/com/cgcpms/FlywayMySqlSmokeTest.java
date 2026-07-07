package com.cgcpms;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "jdbc:mysql:.*")
class FlywayMySqlSmokeTest {

    @Autowired
    private Flyway flyway;

    @Test
    void shouldApplyMysqlMigrationsOnFreshDatabase() {
        assertNotNull(flyway.info().current(), "Flyway should report the current migration version");
    }
}
