package com.cgcpms.closeout;

import com.cgcpms.closeout.service.ProjectCloseGateService;
import com.cgcpms.closeout.service.ProjectCloseoutService;
import com.cgcpms.common.util.BusinessCodeGenerator;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.workflow.service.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectCloseoutWorkspacePaginationTest {
    private static final List<String> DETAIL_KEYS = List.of(
            "sectionAcceptances", "finalAcceptances", "settlements", "receivables", "warranties",
            "defects", "archiveTransfers", "wbsTasks", "qualityInspections");
    private CountingJdbcTemplate jdbc;
    private ProjectCloseoutService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:closeout_workspace_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new CountingJdbcTemplate(dataSource);
        createSchema(jdbc);
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        when(accessChecker.sqlScope()).thenReturn(new ProjectAccessChecker.ProjectSqlScope(
                "p.tenant_id=? AND p.deleted_flag=0", List.of(7L)));
        service = new ProjectCloseoutService(
                jdbc,
                mock(BusinessCodeGenerator.class),
                mock(WorkflowEngine.class),
                accessChecker,
                mock(ProjectCloseGateService.class));
    }

    @Test
    void pageIsStableBoundedTenantSafeAndQueryCountDoesNotGrowWithProjects() {
        insertProjects(50, 7L);
        insertProject(900, 8L);

        jdbc.reset();
        var first = service.page(1, 10, null);
        int fiftyProjectQueries = jdbc.queries();
        assertEquals(2, fiftyProjectQueries);
        assertEquals(50, first.getTotal());
        assertEquals(10, first.getRecords().size());
        assertTrue(first.getRecords().stream()
                .allMatch(row -> Long.parseLong(row.projectId()) < 900));

        jdbc.reset();
        var second = service.page(2, 10, null);
        assertEquals(2, jdbc.queries());
        assertEquals(10, second.getRecords().size());
        var ids = new HashSet<>(first.getRecords().stream().map(row -> row.projectId()).toList());
        assertTrue(second.getRecords().stream().noneMatch(row -> ids.contains(row.projectId())));

        jdbc.update("DELETE FROM closeout_defect WHERE closeout_id<>1001");
        jdbc.update("DELETE FROM closeout_warranty WHERE closeout_id<>1001");
        jdbc.update("DELETE FROM closeout_final_acceptance WHERE closeout_id<>1001");
        jdbc.update("DELETE FROM closeout_section_acceptance WHERE closeout_id<>1001");
        jdbc.update("DELETE FROM project_closeout WHERE id<>1001 AND tenant_id=7");
        jdbc.update("DELETE FROM pm_project WHERE id<>1 AND tenant_id=7");
        jdbc.reset();
        var single = service.page(1, 10, null);
        assertEquals(fiftyProjectQueries, jdbc.queries());
        assertEquals(1, single.getTotal());
        assertEquals(1, single.getRecords().size());
        assertEquals("1", single.getRecords().getFirst().projectId());
        assertEquals("1001", single.getRecords().getFirst().closeoutId());
        assertEquals(1, single.getRecords().getFirst().sectionAcceptanceCount());
        assertEquals(1, single.getRecords().getFirst().finalAcceptanceCount());
        assertEquals(1, single.getRecords().getFirst().warrantyCount());
        assertEquals(1, single.getRecords().getFirst().defectCount());

        jdbc.reset();
        assertEquals(0, service.page(1, 10, 900L).getTotal());
        assertEquals(1, jdbc.queries());
    }

    @Test
    void overviewDetailsAreBoundedCountedAndStableAcrossPages() throws Exception {
        var boundedJdbc = new BoundedOverviewJdbcTemplate();
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        var boundedService = new ProjectCloseoutService(
                boundedJdbc,
                mock(BusinessCodeGenerator.class),
                mock(WorkflowEngine.class),
                accessChecker,
                mock(ProjectCloseGateService.class));

        Map<String, Object> first = overview(boundedService, 1, 1000);
        assertEquals(1, first.get("detailPageNo"));
        assertEquals(100, first.get("detailPageSize"));
        assertTrue(first.get("detailTotals") instanceof Map<?, ?>);
        for (String key : DETAIL_KEYS) {
            assertTrue(first.containsKey(key), "missing detail page: " + key);
        }
        assertTrue(boundedJdbc.detailSql().stream()
                .allMatch(sql -> sql.matches("(?is).*\\bLIMIT\\s+\\?\\s+OFFSET\\s+\\?.*")));

        Map<String, Object> pageOne = overview(boundedService, 1, 1);
        Map<String, Object> pageTwo = overview(boundedService, 2, 1);
        for (String key : DETAIL_KEYS) {
            assertTrue(pageOne.get(key) instanceof List<?>);
            assertTrue(pageTwo.get(key) instanceof List<?>);
            assertTrue(disjointIds((List<?>) pageOne.get(key), (List<?>) pageTwo.get(key)),
                    "detail pages overlap: " + key);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> overview(ProjectCloseoutService target, int pageNo, int pageSize) throws Exception {
        return (Map<String, Object>) target.getClass()
                .getMethod("overview", Long.class, int.class, int.class)
                .invoke(target, 1L, pageNo, pageSize);
    }

    private boolean disjointIds(List<?> first, List<?> second) {
        var ids = new HashSet<>(first.stream().map(row -> ((Map<?, ?>) row).get("id")).toList());
        return second.stream().map(row -> ((Map<?, ?>) row).get("id")).noneMatch(ids::contains);
    }

    private void insertProjects(int count, long tenantId) {
        for (int id = 1; id <= count; id++) insertProject(id, tenantId);
    }

    private void insertProject(long id, long tenantId) {
        long closeoutId = 1000 + id;
        jdbc.update("INSERT INTO pm_project VALUES(?,?,?,0)", id, tenantId, "项目-" + id);
        jdbc.update("INSERT INTO project_closeout VALUES(?,?,?,?,?,?,0)",
                closeoutId, tenantId, id, "CO-" + id, "INITIATED", id);
        jdbc.update("INSERT INTO closeout_section_acceptance VALUES(?,?,?,0)", 2000 + id, tenantId, closeoutId);
        jdbc.update("INSERT INTO closeout_final_acceptance VALUES(?,?,?,0)", 3000 + id, tenantId, closeoutId);
        jdbc.update("INSERT INTO closeout_warranty VALUES(?,?,?,0)", 4000 + id, tenantId, closeoutId);
        jdbc.update("INSERT INTO closeout_defect VALUES(?,?,?,0)", 5000 + id, tenantId, closeoutId);
    }

    private static void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE pm_project(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_name VARCHAR(100),deleted_flag INT)");
        jdbc.execute("""
                CREATE TABLE project_closeout(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,closeout_code VARCHAR(64),
                  status VARCHAR(30),created_at BIGINT,deleted_flag INT)
                """);
        jdbc.execute("CREATE TABLE closeout_section_acceptance(id BIGINT PRIMARY KEY,tenant_id BIGINT,closeout_id BIGINT,deleted_flag INT)");
        jdbc.execute("CREATE TABLE closeout_final_acceptance(id BIGINT PRIMARY KEY,tenant_id BIGINT,closeout_id BIGINT,deleted_flag INT)");
        jdbc.execute("CREATE TABLE closeout_warranty(id BIGINT PRIMARY KEY,tenant_id BIGINT,closeout_id BIGINT,deleted_flag INT)");
        jdbc.execute("CREATE TABLE closeout_defect(id BIGINT PRIMARY KEY,tenant_id BIGINT,closeout_id BIGINT,deleted_flag INT)");
    }

    private static final class CountingJdbcTemplate extends JdbcTemplate {
        private int queries;

        private CountingJdbcTemplate(javax.sql.DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            queries++;
            return super.queryForObject(sql, requiredType, args);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queries++;
            return super.query(sql, rowMapper, args);
        }

        private void reset() {
            queries = 0;
        }

        private int queries() {
            return queries;
        }
    }

    private static final class BoundedOverviewJdbcTemplate extends JdbcTemplate {
        private final List<String> detailSql = new java.util.ArrayList<>();

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("FROM project_closeout c")) {
                return List.of(Map.of("id", 1001L, "projectId", 1L));
            }
            detailSql.add(sql);
            long offset = args.length == 0 || !(args[args.length - 1] instanceof Number number)
                    ? 0 : number.longValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", offset + detailSql.size());
            return List.of(row);
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... args) {
            if (sql.contains("sectionAcceptances")) {
                Map<String, Object> totals = new LinkedHashMap<>();
                DETAIL_KEYS.forEach(key -> totals.put(key, 2L));
                return totals;
            }
            return Map.of("totalTasks", 1L, "incompleteTasks", 0L);
        }

        private List<String> detailSql() {
            return detailSql;
        }
    }
}
