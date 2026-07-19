package com.cgcpms.system.dict;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DictionaryMigrationH2Test {

    @Test
    void v216NormalizesCoreDictionaryCodes() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:dict_migration_v216;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                        + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        try (Connection connection = dataSource.getConnection()) {
            run(connection, "db/migration-h2/B215__cgc_pms_baseline.sql");
            connection.createStatement().executeUpdate("INSERT INTO sys_dict_type(id,tenant_id,dict_code,dict_name,status) VALUES(990001,99,'project_type','租户项目类型','ENABLE')");
            connection.createStatement().executeUpdate("INSERT INTO sys_dict_data(id,tenant_id,dict_type_id,dict_label,dict_value,order_num,status) VALUES(990002,99,990001,'租户伪值','TENANT_FAKE',1,'ENABLE')");
            run(connection, "db/migration-h2/V216__normalize_core_dictionary_codes.sql");
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals("ACTIVE", value(jdbc, 100102L));
        assertEquals("ARCHIVED", value(jdbc, 100103L));
        assertEquals("CONSTRUCTION", value(jdbc, 132001L));
        assertEquals("CUSTOMER", value(jdbc, 2160501L));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_type WHERE tenant_id=0 AND dict_code='cost_source_type'",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_data d JOIN sys_dict_type t ON t.id=d.dict_type_id "
                        + "WHERE t.dict_code='cost_status' AND d.dict_value='CONFIRMED'",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_data d JOIN sys_dict_type t ON t.id=d.dict_type_id "
                        + "WHERE t.tenant_id=0 AND t.dict_code='cost_status' AND d.dict_value='WRITE_OFF'",
                Integer.class));
        assertEquals("DISABLE", jdbc.queryForObject(
                "SELECT status FROM sys_dict_type WHERE id=990001", String.class));
    }

    private void run(Connection connection, String resource) throws Exception {
        var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        assertNotNull(stream, resource);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            RunScript.execute(connection, reader);
        }
    }

    private String value(JdbcTemplate jdbc, long id) {
        return jdbc.queryForObject("SELECT dict_value FROM sys_dict_data WHERE id=?", String.class, id);
    }
}
