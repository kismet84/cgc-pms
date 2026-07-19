package com.cgcpms.contract;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ContractReferenceMigrationH2Test {

    @Test
    void v217AddsTenantAwareContractReferenceConstraints() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:contract_reference_v217;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                        + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        try (Connection connection = dataSource.getConnection()) {
            run(connection, "db/migration-h2/B215__cgc_pms_baseline.sql");
            run(connection, "db/migration-h2/V216__normalize_core_dictionary_codes.sql");
            run(connection, "db/migration-h2/V217__enforce_contract_reference_integrity.sql");
        }

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(4, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE UPPER(constraint_name) IN (
                    'CHK_CONTRACT_DISTINCT_PARTIES',
                    'FK_CONTRACT_PROJECT_TENANT',
                    'FK_CONTRACT_PARTY_A_TENANT',
                    'FK_CONTRACT_PARTY_B_TENANT')
                """, Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE UPPER(table_name)='CT_CONTRACT'
                  AND UPPER(column_name) IN ('PARTY_A_ID','PARTY_B_ID')
                  AND is_nullable='YES'
                """, Integer.class));
    }

    private void run(Connection connection, String resource) throws Exception {
        var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        assertNotNull(stream, resource);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            RunScript.execute(connection, reader);
        }
    }
}
