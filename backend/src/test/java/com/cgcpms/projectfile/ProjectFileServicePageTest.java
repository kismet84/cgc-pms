package com.cgcpms.projectfile;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.file.service.FileObjectTaskService;
import com.cgcpms.file.service.FileService;
import com.cgcpms.project.auth.ProjectAccessChecker;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectFileServicePageTest {

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void pageFiltersSourcesInSqlAndKeepsQueryCountConstant() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:project_file_page;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new CountingJdbcTemplate(dataSource);
        createSchema(jdbc);
        jdbc.update("INSERT INTO pm_project VALUES(1,7,'P1','项目一',0),(2,7,'P2','项目二',0)");
        jdbc.update("INSERT INTO ct_contract VALUES(300,7,1,0),(301,7,1,1),(302,7,2,0)");
        jdbc.update("INSERT INTO qs_inspection_record VALUES(400,7,1,0)");
        jdbc.update("INSERT INTO qs_issue VALUES(401,7,2,400,0)");

        List<Object[]> managed = new ArrayList<>(10_000);
        for (long id = 1; id <= 10_000; id++) {
            managed.add(new Object[]{id, 7, 1, "FILE-" + id, "name-" + id, "OTHER",
                    "MANAGED", "MANAGED", null, null, id});
        }
        jdbc.batchUpdate("""
                INSERT INTO project_file_catalog(
                    id,tenant_id,project_id,file_code,display_name,category_code,source_kind,maintain_mode,
                    source_business_type,source_business_id,updated_at,deleted_flag)
                VALUES(?,?,?,?,?,?,?,?,?,?,DATEADD('SECOND',?,TIMESTAMP '2026-01-01 00:00:00'),0)
                """, managed);
        jdbc.batchUpdate("""
                INSERT INTO project_file_catalog(
                    id,tenant_id,project_id,file_code,display_name,category_code,source_kind,maintain_mode,
                    source_business_type,source_business_id,updated_at,deleted_flag)
                VALUES(?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,0)
                """, List.of(
                new Object[]{20_001L, 7L, 1L, "C-OK", "contract-ok", "CONTRACT", "BUSINESS", "READ_ONLY", "CONTRACT", 300L},
                new Object[]{20_002L, 7L, 1L, "C-DELETED", "contract-deleted", "CONTRACT", "BUSINESS", "READ_ONLY", "CONTRACT", 301L},
                new Object[]{20_003L, 7L, 1L, "C-WRONG", "contract-wrong", "CONTRACT", "BUSINESS", "READ_ONLY", "CONTRACT", 302L},
                new Object[]{20_004L, 7L, 1L, "C-MISSING", "contract-missing", "CONTRACT", "BUSINESS", "READ_ONLY", "CONTRACT", 999L},
                new Object[]{20_005L, 7L, 1L, "P-OK", "project-ok", "OTHER", "BUSINESS", "READ_ONLY", "PROJECT", 1L},
                new Object[]{20_006L, 7L, 1L, "DENIED", "expense-denied", "FINANCE", "BUSINESS", "READ_ONLY", "EXPENSE", 600L},
                new Object[]{20_007L, 8L, 1L, "OTHER-TENANT", "other-tenant", "OTHER", "MANAGED", "MANAGED", null, null},
                new Object[]{20_008L, 7L, 1L, "QS-WRONG", "quality-wrong", "QUALITY_SAFETY", "BUSINESS", "READ_ONLY", "QS_ISSUE", 401L}));

        ProjectAccessChecker projects = mock(ProjectAccessChecker.class);
        when(projects.accessibleProjectIds()).thenReturn(List.of(1L));
        BusinessObjectAuthorizer authorizer = mock(BusinessObjectAuthorizer.class);
        when(authorizer.canReadProjectFileSource("CONTRACT")).thenReturn(true);
        when(authorizer.canReadProjectFileSource("PROJECT")).thenReturn(true);
        when(authorizer.canReadProjectFileSource("QS_ISSUE")).thenReturn(true);
        ProjectFileService service = new ProjectFileService(jdbc, projects, authorizer,
                mock(FileService.class), mock(FileObjectTaskService.class), mock(OfficePreviewClient.class));
        TestUserContext.setUser(7, 99, "reader", List.of());

        jdbc.resetQueryCount();
        var first = service.page(1, 100, null, null, null);
        assertEquals(10_002, first.getTotal());
        assertEquals(100, first.getRecords().size());
        assertEquals(3, jdbc.queryCount());

        jdbc.resetQueryCount();
        var hundredth = service.page(100, 100, null, null, null);
        assertEquals(10_002, hundredth.getTotal());
        assertEquals(100, hundredth.getRecords().size());
        assertEquals(3, jdbc.queryCount());

        jdbc.resetQueryCount();
        var last = service.page(101, 100, null, null, null);
        assertEquals(10_002, last.getTotal());
        assertEquals(2, last.getRecords().size());
        assertEquals(3, jdbc.queryCount());

        jdbc.resetQueryCount();
        var contract = service.page(1, 20, null, "contract-ok", null);
        assertEquals(1, contract.getTotal());
        assertEquals("20001", contract.getRecords().getFirst().id());
        assertEquals(3, jdbc.queryCount());

        jdbc.resetQueryCount();
        var project = service.page(1, 20, null, "project-ok", null);
        assertEquals(1, project.getTotal());
        assertEquals("20005", project.getRecords().getFirst().id());
        assertEquals(3, jdbc.queryCount());

        for (String denied : List.of("contract-deleted", "contract-wrong", "contract-missing",
                "expense-denied", "other-tenant", "quality-wrong")) {
            jdbc.resetQueryCount();
            assertEquals(0, service.page(1, 20, null, denied, null).getTotal());
            assertEquals(1, jdbc.queryCount());
        }
    }

    @Test
    void technicalResponseVisibilityUsesRealMigrationSchema() {
        Flyway flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:project_file_page_schema;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration-h2", "filesystem:src/main/resources/db/migration-h2-legacy",
                        "classpath:com/cgcpms/common/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        var jdbc = new CountingJdbcTemplate(flyway.getConfiguration().getDataSource());
        ProjectAccessChecker projects = mock(ProjectAccessChecker.class);
        when(projects.accessibleProjectIds()).thenReturn(List.of(1L));
        BusinessObjectAuthorizer authorizer = mock(BusinessObjectAuthorizer.class);
        when(authorizer.canReadProjectFileSource("TECH_RFI_RESPONSE")).thenReturn(true);
        ProjectFileService service = new ProjectFileService(jdbc, projects, authorizer,
                mock(FileService.class), mock(FileObjectTaskService.class), mock(OfficePreviewClient.class));
        TestUserContext.setUser(0, 99, "reader", List.of());

        jdbc.resetQueryCount();
        assertEquals(0, service.page(1, 20, null, null, null).getTotal());
        assertEquals(1, jdbc.queryCount());
    }

    private static void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE pm_project(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_code VARCHAR(50),project_name VARCHAR(100),deleted_flag INT)");
        jdbc.execute("CREATE TABLE ct_contract(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,deleted_flag INT)");
        jdbc.execute("CREATE TABLE qs_inspection_record(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,deleted_flag INT)");
        jdbc.execute("CREATE TABLE qs_issue(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,inspection_id BIGINT,deleted_flag INT)");
        jdbc.execute("""
                CREATE TABLE project_file_catalog(
                    id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,file_code VARCHAR(100),
                    display_name VARCHAR(200),category_code VARCHAR(50),source_kind VARCHAR(20),
                    maintain_mode VARCHAR(20),source_business_type VARCHAR(50),source_business_id BIGINT,
                    updated_at TIMESTAMP,deleted_flag INT)
                """);
        jdbc.execute("CREATE TABLE sys_dict_type(id BIGINT,tenant_id BIGINT,dict_code VARCHAR(50),status VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_dict_data(dict_type_id BIGINT,dict_value VARCHAR(50),dict_label VARCHAR(100),status VARCHAR(20))");
        jdbc.execute("CREATE TABLE project_file_version_link(id BIGINT,tenant_id BIGINT,catalog_id BIGINT,version_no INT,sys_file_id BIGINT,preview_status VARCHAR(20),deleted_flag INT)");
        jdbc.execute("CREATE TABLE sys_file(id BIGINT,tenant_id BIGINT,virus_scan_status VARCHAR(20),created_by BIGINT,created_at TIMESTAMP,deleted_flag INT)");
        jdbc.execute("CREATE TABLE sys_user(id BIGINT,tenant_id BIGINT,real_name VARCHAR(100),deleted_flag INT)");
    }

    private static final class CountingJdbcTemplate extends JdbcTemplate {
        private int queryCount;

        private CountingJdbcTemplate(javax.sql.DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            queryCount++;
            return super.queryForObject(sql, requiredType, args);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queryCount++;
            return super.queryForList(sql, args);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCount++;
            return super.query(sql, rowMapper, args);
        }

        private int queryCount() {
            return queryCount;
        }

        private void resetQueryCount() {
            queryCount = 0;
        }
    }
}
