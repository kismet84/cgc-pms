package com.cgcpms.common.util;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class H2MigrationHelperTest {

    @Test
    void dropUniqueConstraintsRemovesUniqueIndexes() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:h2_migration_helper_unique;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE cost_table ("
                    + "id BIGINT NOT NULL PRIMARY KEY, "
                    + "tenant_id BIGINT NOT NULL, "
                    + "project_code VARCHAR(64) NOT NULL, "
                    + "deleted_flag SMALLINT NOT NULL DEFAULT 0, "
                    + "UNIQUE (tenant_id, project_code))");
            st.execute("INSERT INTO cost_table (id, tenant_id, project_code) VALUES (1, 1, 'PC001')");

            assertThrows(SQLException.class, () ->
                    st.execute("INSERT INTO cost_table (id, tenant_id, project_code) VALUES (2, 1, 'PC001')")
            );

            H2MigrationHelper.dropUniqueConstraints(conn, "cost_table");

            assertDoesNotThrow(() ->
                    st.execute("INSERT INTO cost_table (id, tenant_id, project_code) VALUES (2, 1, 'PC001')")
            );
        }
    }

    @Test
    void dropUniqueConstraintsAndIndexSupportsSafeIndexRemoval() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:h2_migration_helper_index;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE material_table (id BIGINT NOT NULL PRIMARY KEY, material_code VARCHAR(64) NOT NULL)");
            st.execute("CREATE UNIQUE INDEX uk_material_code ON material_table(material_code)");
            st.execute("INSERT INTO material_table (id, material_code) VALUES (1, 'M001')");

            H2MigrationHelper.dropUniqueConstraintsAndIndex(conn, "material_table", "uk_material_code");

            assertDoesNotThrow(() ->
                    st.execute("INSERT INTO material_table (id, material_code) VALUES (2, 'M001')")
            );
        }
    }

    @Test
    void dropUniqueConstraintsRejectsIllegalIdentifier() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:h2_migration_helper_illegal;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            assertThrows(IllegalArgumentException.class, () -> H2MigrationHelper.dropUniqueConstraints(conn, "cost_table;DROP TABLE x"));
        }
    }
}
