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
    void v255RemovesRetiredSettlementStatusDictionary() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:dict_migration_v255;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                        + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        try (Connection connection = dataSource.getConnection()) {
            run(connection, "db/migration-h2/B215__cgc_pms_baseline.sql");
            run(connection, "db/migration-h2/V216__normalize_core_dictionary_codes.sql");
            connection.createStatement().executeUpdate(
                    "INSERT INTO sys_dict_type(id,tenant_id,dict_code,dict_name,status) "
                            + "VALUES(990013,99,'settlement_status','租户历史状态','DISABLE')");
            connection.createStatement().executeUpdate(
                    "INSERT INTO sys_dict_data(id,tenant_id,dict_type_id,dict_label,dict_value,order_num,status) "
                            + "VALUES(99001301,99,990013,'租户旧值','LEGACY',1,'ENABLE')");
            run(connection, "db/migration-h2/V254__group_and_govern_system_dictionaries.sql");
            run(connection, "db/migration-h2/V255__remove_retired_settlement_status_dictionary.sql");
        }

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(8, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_group WHERE tenant_id=0", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_type WHERE group_id IS NULL", Integer.class));
        assertEquals("SYSTEM", dictClass(jdbc, "project_type"));
        assertEquals("STATE_MACHINE", dictClass(jdbc, "project_status"));
        assertEquals("BUSINESS", dictClass(jdbc, "invoice_type"));
        assertEquals("SYSTEM", dictClass(jdbc, "partner_risk_level"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_type WHERE dict_code='settlement_status'", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_data WHERE dict_type_id IN (1013,990013)", Integer.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_type t JOIN sys_dict_group g ON g.id=t.group_id "
                        + "WHERE g.group_code='SETTLEMENT'", Integer.class));
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_data d JOIN sys_dict_type t ON t.id=d.dict_type_id "
                        + "WHERE t.dict_code='settlement_final_status'", Integer.class));
        assertEquals(5, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_data d JOIN sys_dict_type t ON t.id=d.dict_type_id "
                        + "WHERE t.dict_code='approval_status'", Integer.class));
    }

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

    @Test
    void v261AddsPreparingProjectStatusAndReordersLifecycle() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:dict_migration_v261;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                        + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        try (Connection connection = dataSource.getConnection()) {
            run(connection, "db/migration-h2/B215__cgc_pms_baseline.sql");
            run(connection, "db/migration-h2/V216__normalize_core_dictionary_codes.sql");
            run(connection, "db/migration-h2/V261__add_project_preparing_status.sql");
        }

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(6, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_data WHERE tenant_id=0 AND dict_type_id=1001", Integer.class));
        assertEquals("前期", jdbc.queryForObject(
                "SELECT dict_label FROM sys_dict_data WHERE id=100101", String.class));
        assertEquals("PREPARING", value(jdbc, 2610101L));
        assertEquals("筹备", jdbc.queryForObject(
                "SELECT dict_label FROM sys_dict_data WHERE id=2610101", String.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT order_num FROM sys_dict_data WHERE id=2610101", Integer.class));
        assertEquals(3, jdbc.queryForObject(
                "SELECT order_num FROM sys_dict_data WHERE id=100102", Integer.class));
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

    private String dictClass(JdbcTemplate jdbc, String dictCode) {
        return jdbc.queryForObject(
                "SELECT dict_class FROM sys_dict_type WHERE tenant_id=0 AND dict_code=?",
                String.class,
                dictCode);
    }
}
