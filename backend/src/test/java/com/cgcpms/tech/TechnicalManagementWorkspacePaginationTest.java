package com.cgcpms.tech;

import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.tech.dto.TechnicalManagementModels.Workspace;
import com.cgcpms.tech.service.TechnicalManagementQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TechnicalManagementWorkspacePaginationTest {
    private CountingJdbcTemplate jdbc;
    private TechnicalManagementQueryService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:technical_workspace_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbc = new CountingJdbcTemplate(dataSource);
        createSchema(jdbc);
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        when(accessChecker.sqlScope()).thenReturn(new ProjectAccessChecker.ProjectSqlScope(
                "p.tenant_id = ? AND p.deleted_flag = 0", List.of(7L)));
        service = new TechnicalManagementQueryService(jdbc, accessChecker);
    }

    @Test
    void pagesAllProjectRowsInSqlWithStableFixedQueryBudget() {
        insertProjectFacts(1, 7L);

        jdbc.reset();
        Workspace oneProject = service.workspace("drawing", 1, 10, 1, null);
        int drawingQueries = jdbc.queries();
        assertEquals(3, drawingQueries);
        assertEquals(1, oneProject.primary().getTotal());
        assertEquals(1, oneProject.secondary().getTotal());

        for (long id = 2; id <= 50; id++) insertProjectFacts(id, 7L);
        insertProjectFacts(900, 8L);

        jdbc.reset();
        Workspace first = service.workspace("drawing", 1, 10, 1, null);
        assertEquals(drawingQueries, jdbc.queries());
        assertEquals(50, first.counts().drawing());
        assertEquals(10, first.primary().getRecords().size());
        assertEquals(10, first.secondary().getRecords().size());
        assertTrue(first.primary().getRecords().stream().noneMatch(row -> id(row) == 80_090_002L));
        assertTrue(value(first.primary().getRecords().getFirst(), "id") instanceof String);
        assertTrue(value(first.primary().getRecords().getFirst(), "projectId") instanceof String);
        assertTrue(value(first.primary().getRecords().getFirst(), "currentVersionId") instanceof String);
        assertTrue(value(first.secondary().getRecords().getFirst(), "drawingId") instanceof String);

        jdbc.reset();
        Workspace second = service.workspace("drawing", 2, 10, 2, null);
        assertEquals(drawingQueries, jdbc.queries());
        assertEquals(10, second.primary().getRecords().size());
        assertEquals(10, second.secondary().getRecords().size());
        var firstIds = new HashSet<>(first.primary().getRecords().stream()
                .map(TechnicalManagementWorkspacePaginationTest::id).toList());
        assertTrue(second.primary().getRecords().stream().noneMatch(row -> firstIds.contains(id(row))));
    }

    @Test
    void countsEveryViewOnceAndProjectIdCannotWidenScope() {
        insertProjectFacts(1, 7L);
        insertProjectFacts(2, 7L);
        insertProjectFacts(900, 8L);

        jdbc.reset();
        Workspace scheme = service.workspace("scheme", 1, 1, 1, null);
        assertEquals(2, jdbc.queries());
        assertNull(scheme.secondary());
        assertEquals(1, scheme.primary().getRecords().size());
        assertEquals(2, scheme.primary().getTotal());
        assertEquals(2, scheme.counts().scheme());
        assertEquals(2, scheme.counts().drawing());
        assertEquals(2, scheme.counts().review());
        assertEquals(2, scheme.counts().rfi());
        assertEquals(2, scheme.counts().disclosure());
        assertEquals(2, scheme.counts().archive());

        jdbc.reset();
        Workspace rfi = service.workspace("rfi", 1, 1, 1, 1L);
        assertEquals(3, jdbc.queries());
        assertEquals(1, rfi.primary().getTotal());
        assertEquals(1, rfi.secondary().getTotal());
        assertEquals(1, rfi.primary().getRecords().size());
        assertEquals(1, rfi.secondary().getRecords().size());

        jdbc.reset();
        Workspace forged = service.workspace("archive", 1, 10, 1, 900L);
        assertEquals(3, jdbc.queries());
        assertEquals(0, forged.primary().getTotal());
        assertEquals(0, forged.secondary().getTotal());
        assertTrue(forged.primary().getRecords().isEmpty());
        assertTrue(forged.secondary().getRecords().isEmpty());
    }

    @Test
    void locksEveryViewQueryBudgetAndStringIdentifierContract() {
        insertProjectFacts(1, 7L);
        for (String view : List.of("scheme", "review", "disclosure")) {
            jdbc.reset();
            Workspace result = service.workspace(view, 1, 10, 1, null);
            assertEquals(2, jdbc.queries(), view);
            assertNull(result.secondary(), view);
            assertIdentifierStrings(result.primary().getRecords().getFirst());
        }
        for (String view : List.of("drawing", "rfi", "archive")) {
            jdbc.reset();
            Workspace result = service.workspace(view, 1, 10, 1, null);
            assertEquals(3, jdbc.queries(), view);
            assertIdentifierStrings(result.primary().getRecords().getFirst());
            assertIdentifierStrings(result.secondary().getRecords().getFirst());
        }
    }

    private void insertProjectFacts(long id, long tenantId) {
        long base = tenantId * 10_000_000 + id * 100;
        jdbc.update("INSERT INTO pm_project VALUES(?,?,?,0)", id, tenantId, "项目-" + id);
        jdbc.update("INSERT INTO technical_scheme VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                base + 1, tenantId, id, "S-" + id, "方案-" + id, "SPECIAL", 1,
                "2026-01-01", "APPROVED", null, null, null, id);
        jdbc.update("INSERT INTO tech_drawing_version VALUES(?,?,?,?,?,?,?,?,?,?,?,0)",
                base + 3, tenantId, id, base + 2, "V1", null, null,
                "2026-01-01 08:00:00", "初版", "APPROVED", id);
        jdbc.update("INSERT INTO tech_drawing VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                base + 2, tenantId, id, "D-" + id, "图纸-" + id, "建筑", "设计院",
                base + 3, "ACTIVE", null, id, id, id);
        jdbc.update("INSERT INTO tech_drawing_review VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                base + 4, tenantId, id, base + 3, "RV-" + id, "2026-01-01", 1,
                "参会", "PASS", "通过", true, "CONFIRMED", id);
        jdbc.update("INSERT INTO tech_rfi VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                base + 5, tenantId, id, base + 3, base + 4, "RFI-" + id, "主题", "NORMAL",
                "2026-02-01", "SUBMITTED", "2026-01-01 08:00:00", null, id, id, id);
        jdbc.update("INSERT INTO tech_rfi_response VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                base + 6, tenantId, base + 5, "回复", false, "设计院", 1,
                "2026-01-02 08:00:00", "SUBMITTED", null, null, null, id);
        jdbc.update("INSERT INTO tech_disclosure VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                base + 7, tenantId, id, base + 3, base + 1, "DC-" + id, "交底-" + id,
                "2026-01-03", 1, "班组", "内容", "CONFIRMED", id);
        jdbc.update("INSERT INTO tech_construction_reference VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                base + 8, tenantId, id, base + 3, base + 7, 1, 1, "2026-01-04",
                "区域", "依据", "RECORDED", id, id);
        jdbc.update("INSERT INTO tech_construction_reference VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                base + 18, tenantId, id, base + 3, base + 7, 2, 2, "2026-01-05",
                "待归档区域", "待归档依据", "RECORDED", id + 1, id + 1);
        jdbc.update("INSERT INTO tech_acceptance_archive VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                base + 9, tenantId, id, base + 3, base + 8, 1, "AR-" + id,
                "2026-01-05", "PASS", "档案室", "ARCHIVED", "2026-01-06 08:00:00", id, id);
    }

    private static long id(Map<String, Object> row) {
        Object value = value(row, "id");
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static Object value(Map<String, Object> row, String name) {
        return row.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
    }

    private static void assertIdentifierStrings(Map<String, Object> row) {
        row.forEach((key, value) -> {
            String normalized = key.toLowerCase();
            if (value != null && ("id".equals(normalized)
                    || normalized.endsWith("id") || normalized.endsWith("by"))) {
                assertTrue(value instanceof String, key);
            }
        });
    }

    private static void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE pm_project(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_name VARCHAR(100),deleted_flag INT)");
        jdbc.execute("""
                CREATE TABLE technical_scheme(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,scheme_code VARCHAR(64),
                  scheme_name VARCHAR(200),scheme_type VARCHAR(50),responsible_user_id BIGINT,
                  planned_effective_date DATE,status VARCHAR(30),approval_instance_id BIGINT,
                  approved_at TIMESTAMP,remark VARCHAR(500),created_at BIGINT,deleted_flag INT)
                """);
        jdbc.execute("""
                CREATE TABLE tech_drawing(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,drawing_code VARCHAR(64),
                  drawing_name VARCHAR(200),specialty VARCHAR(50),source_organization VARCHAR(200),
                  current_version_id BIGINT,status VARCHAR(30),remark VARCHAR(500),created_at BIGINT,
                  updated_at BIGINT,created_by BIGINT,deleted_flag INT)
                """);
        jdbc.execute("""
                CREATE TABLE tech_drawing_version(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,drawing_id BIGINT,
                  version_no VARCHAR(30),previous_version_id BIGINT,source_rfi_id BIGINT,received_at TIMESTAMP,
                  change_summary VARCHAR(500),status VARCHAR(30),created_at BIGINT,deleted_flag INT)
                """);
        jdbc.execute("""
                CREATE TABLE tech_drawing_review(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,drawing_version_id BIGINT,
                  review_code VARCHAR(64),review_date DATE,chair_user_id BIGINT,participant_summary VARCHAR(500),
                  conclusion VARCHAR(30),review_summary VARCHAR(1000),requires_rfi BOOLEAN,status VARCHAR(30),
                  created_at BIGINT,deleted_flag INT)
                """);
        jdbc.execute("""
                CREATE TABLE tech_rfi(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,drawing_version_id BIGINT,
                  review_id BIGINT,rfi_code VARCHAR(64),subject VARCHAR(200),priority VARCHAR(30),
                  response_due_date DATE,status VARCHAR(30),raised_at TIMESTAMP,closed_at TIMESTAMP,
                  created_at BIGINT,updated_at BIGINT,created_by BIGINT,deleted_flag INT)
                """);
        jdbc.execute("""
                CREATE TABLE tech_rfi_response(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,rfi_id BIGINT,response_content VARCHAR(2000),
                  change_required BOOLEAN,responder_name VARCHAR(100),responded_by BIGINT,responded_at TIMESTAMP,
                  status VARCHAR(30),reviewed_by BIGINT,reviewed_at TIMESTAMP,review_comment VARCHAR(500),
                  created_at BIGINT)
                """);
        jdbc.execute("""
                CREATE TABLE tech_disclosure(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,drawing_version_id BIGINT,
                  scheme_id BIGINT,disclosure_code VARCHAR(64),disclosure_title VARCHAR(200),disclosure_date DATE,
                  presenter_user_id BIGINT,recipient_summary VARCHAR(500),disclosure_content VARCHAR(2000),
                  status VARCHAR(30),created_at BIGINT,deleted_flag INT)
                """);
        jdbc.execute("""
                CREATE TABLE tech_construction_reference(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,drawing_version_id BIGINT,
                  disclosure_id BIGINT,daily_log_id BIGINT,wbs_task_id BIGINT,reference_date DATE,
                  work_area VARCHAR(200),reference_description VARCHAR(1000),status VARCHAR(30),
                  created_at BIGINT,updated_at BIGINT,deleted_flag INT)
                """);
        jdbc.execute("""
                CREATE TABLE tech_acceptance_archive(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,drawing_version_id BIGINT,
                  construction_reference_id BIGINT,quality_inspection_id BIGINT,archive_code VARCHAR(64),
                  acceptance_date DATE,acceptance_conclusion VARCHAR(30),archive_location VARCHAR(300),
                  status VARCHAR(30),archived_at TIMESTAMP,created_at BIGINT,updated_at BIGINT,deleted_flag INT)
                """);
    }

    private static final class CountingJdbcTemplate extends JdbcTemplate {
        private int queries;

        private CountingJdbcTemplate(javax.sql.DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            queries++;
            return super.queryForObject(sql, rowMapper, args);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queries++;
            return super.queryForList(sql, args);
        }

        private void reset() {
            queries = 0;
        }

        private int queries() {
            return queries;
        }
    }
}
