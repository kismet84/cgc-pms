package com.cgcpms.quality;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.quality.entity.QualityInspectionPlan;
import com.cgcpms.quality.service.QualitySafetyQueryService;
import com.cgcpms.system.mapper.SysRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class QualitySafetyWorkspacePaginationTest {
    private CountingJdbcTemplate jdbc;
    private QualitySafetyQueryService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:quality_workspace_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbc = new CountingJdbcTemplate(dataSource);
        createSchema(jdbc);
        var access = new ProjectAccessChecker(
                mock(PmProjectMapper.class), mock(PmProjectMemberMapper.class), mock(SysRoleMapper.class));
        service = new QualitySafetyQueryService(jdbc, access);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void oneAndFiftyVisibleProjectsUseTwoQueriesAndStableBoundedPages() {
        grantRole(10, "ALL_ROLE", "ALL", 1);
        user(10, "ALL_ROLE");
        insertProjectWithFacts(1, 7, 99, 0);

        jdbc.reset();
        var single = service.workspace("plan", 1, 10, null, null);
        int oneProjectQueries = jdbc.queries();
        assertEquals(2, oneProjectQueries);
        assertEquals(1, single.page().getTotal());
        assertEquals(1, single.page().getRecords().size());

        for (int id = 2; id <= 50; id++) insertProjectWithFacts(id, 7, 99, 0);
        insertProjectWithFacts(900, 8, 99, 0);
        insertProjectWithFacts(51, 7, 99, 1);
        insertDeletedFacts(1);

        jdbc.reset();
        var first = service.workspace("plan", 1, 10, null, null);
        assertEquals(oneProjectQueries, jdbc.queries());
        assertEquals(50, first.page().getTotal());
        assertEquals(10, first.page().getRecords().size());
        assertEquals(50, first.counts().plan());
        assertEquals(1, first.counts().inspection());
        assertEquals(50, first.counts().rectification());
        assertEquals(50, first.counts().reinspection());
        assertEquals(50, first.counts().consequence());
        assertEquals("1050", first.selectedPlanRef().id());

        jdbc.reset();
        var second = service.workspace("plan", 2, 10, null, null);
        assertEquals(oneProjectQueries, jdbc.queries());
        assertEquals(10, second.page().getRecords().size());
        var firstIds = new HashSet<>(first.page().getRecords().stream()
                .map(row -> ((QualityInspectionPlan) row).getId()).toList());
        assertTrue(second.page().getRecords().stream()
                .map(row -> ((QualityInspectionPlan) row).getId())
                .noneMatch(firstIds::contains));

        jdbc.reset();
        var issuePage = service.workspace("rectification", 1, 10, null, null);
        assertEquals(oneProjectQueries, jdbc.queries());
        assertEquals(50, issuePage.page().getTotal());
        assertEquals(10, issuePage.page().getRecords().size());

        jdbc.reset();
        var crossTenant = service.workspace("plan", 1, 10, 900L, null);
        assertEquals(1, jdbc.queries());
        assertEquals(0, crossTenant.page().getTotal());
        assertTrue(crossTenant.page().getRecords().isEmpty());
        assertNull(crossTenant.selectedPlanRef());

        var mismatchedPlan = service.workspace("inspection", 1, 10, 1L, 1002L);
        assertEquals(0, mismatchedPlan.page().getTotal());
        assertTrue(mismatchedPlan.page().getRecords().isEmpty());
        assertNull(mismatchedPlan.selectedPlanRef());
    }

    @Test
    void selfAndMemberScopesRejectInactiveDeletedAndForgedGrants() {
        insertProjectWithFacts(1, 7, 20, 0);
        insertProjectWithFacts(2, 7, 99, 0);
        insertProjectWithFacts(3, 7, 99, 0);
        insertProjectWithFacts(4, 7, 99, 0);
        insertProjectWithFacts(5, 7, 20, 1);
        insertProjectWithFacts(900, 8, 20, 0);

        user(20);
        var self = service.workspace("plan", 1, 10, null, null);
        assertEquals(1, self.page().getTotal());
        assertEquals(1L, ((QualityInspectionPlan) self.page().getRecords().getFirst()).getProjectId());

        grantRole(30, "MEMBER_ROLE", "PROJECT_MEMBER", 2);
        jdbc.update("INSERT INTO pm_project_member VALUES(7,2,30,'ACTIVE',0)");
        jdbc.update("INSERT INTO pm_project_member VALUES(7,3,30,'INACTIVE',0)");
        jdbc.update("INSERT INTO pm_project_member VALUES(7,4,30,'ACTIVE',1)");
        jdbc.update("INSERT INTO pm_project_member VALUES(7,900,30,'ACTIVE',0)");
        user(30, "MEMBER_ROLE");
        var member = service.workspace("plan", 1, 10, null, null);
        assertEquals(1, member.page().getTotal());
        assertEquals(2L, ((QualityInspectionPlan) member.page().getRecords().getFirst()).getProjectId());

        var forgedProject = service.workspace("plan", 1, 10, 1L, null);
        assertEquals(0, forgedProject.page().getTotal());
        var forgedPlan = service.workspace("inspection", 1, 10, null, 1001L);
        assertEquals(0, forgedPlan.page().getTotal());
        assertNull(forgedPlan.selectedPlanRef());
    }

    private void user(long userId, String... roles) {
        UserContext.restore(new UserContext.Snapshot(userId, "tester", 7L, List.of(roles)));
    }

    private void grantRole(long userId, String roleCode, String dataScope, long roleId) {
        jdbc.update("INSERT INTO sys_role VALUES(7,?,?,?,'ENABLE',0)", roleId, roleCode, dataScope);
        jdbc.update("INSERT INTO sys_user_role VALUES(7,?,?)", userId, roleId);
    }

    private void insertProjectWithFacts(long projectId, long tenantId, long createdBy, int deleted) {
        jdbc.update("INSERT INTO pm_project VALUES(?,?,?,?)", projectId, tenantId, createdBy, deleted);
        long planId = 1000 + projectId;
        long inspectionId = 2000 + projectId;
        jdbc.update("""
                INSERT INTO qs_inspection_plan
                (id,tenant_id,project_id,plan_code,plan_name,inspection_type,frequency_type,start_date,end_date,
                 owner_user_id,status,version,created_at,deleted_flag,remark)
                VALUES(?,?,?,?,?,'QUALITY','SINGLE','2026-08-01','2026-08-31',?,'ACTIVE',0,?,0,NULL)
                """, planId, tenantId, projectId, "PLAN-" + projectId, "计划-" + projectId,
                createdBy, LocalDateTime.of(2026, 8, 1, 0, 0).plusSeconds(projectId));
        jdbc.update("""
                INSERT INTO qs_inspection_record
                (id,tenant_id,plan_id,project_id,inspection_code,inspection_date,location,inspector_user_id,
                 conclusion,summary,status,version,created_at,deleted_flag,remark)
                VALUES(?,?,?,?,?,'2026-08-12','现场',?,'ISSUES','检查','SUBMITTED',0,?,0,NULL)
                """, inspectionId, tenantId, planId, projectId, "INS-" + projectId, createdBy,
                LocalDateTime.of(2026, 8, 12, 0, 0).plusSeconds(projectId));
        insertIssue(3000 + projectId * 10, tenantId, planId, inspectionId, projectId,
                "OPEN", null, 0);
        insertIssue(3001 + projectId * 10, tenantId, planId, inspectionId, projectId,
                "PENDING_REINSPECTION", null, 0);
        insertIssue(3002 + projectId * 10, tenantId, planId, inspectionId, projectId,
                "CLOSED", 9000 + projectId, 0);
    }

    private void insertDeletedFacts(long projectId) {
        long planId = 9000 + projectId;
        jdbc.update("""
                INSERT INTO qs_inspection_plan
                (id,tenant_id,project_id,plan_code,plan_name,inspection_type,frequency_type,start_date,end_date,
                 owner_user_id,status,version,created_at,deleted_flag,remark)
                VALUES(?,?,?,'DELETED','已删除','QUALITY','SINGLE','2027-01-01','2027-01-31',1,'ACTIVE',0,
                       '2027-01-01 00:00:00',1,NULL)
                """, planId, 7, projectId);
        insertIssue(99001, 7, 1000 + projectId, 2000 + projectId, projectId,
                "OPEN", null, 1);
    }

    private void insertIssue(long id, long tenantId, long planId, long inspectionId, long projectId,
                             String status, Long partnerId, int deleted) {
        jdbc.update("""
                INSERT INTO qs_issue
                (id,tenant_id,plan_id,inspection_id,project_id,issue_code,issue_type,category,severity,title,
                 description,responsible_kind,responsible_partner_id,responsible_user_id,due_date,status,
                 version,created_at,deleted_flag,remark)
                VALUES(?,?,?,?,?,?,'QUALITY','现场','MEDIUM',?,'描述',?,?,1,'2026-08-20',?,0,?, ?,NULL)
                """, id, tenantId, planId, inspectionId, projectId, "ISS-" + id, "问题-" + id,
                partnerId == null ? "INTERNAL" : "PARTNER", partnerId, status,
                LocalDateTime.of(2026, 8, 10, 0, 0).plusSeconds(id), deleted);
    }

    private static void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE pm_project(id BIGINT PRIMARY KEY,tenant_id BIGINT,created_by BIGINT,deleted_flag INT)");
        jdbc.execute("CREATE TABLE pm_project_member(tenant_id BIGINT,project_id BIGINT,user_id BIGINT,status VARCHAR(16),deleted_flag INT)");
        jdbc.execute("CREATE TABLE sys_role(tenant_id BIGINT,id BIGINT,role_code VARCHAR(64),data_scope VARCHAR(32),status VARCHAR(16),deleted_flag INT)");
        jdbc.execute("CREATE TABLE sys_user_role(tenant_id BIGINT,user_id BIGINT,role_id BIGINT)");
        jdbc.execute("""
                CREATE TABLE qs_inspection_plan(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,plan_code VARCHAR(64),plan_name VARCHAR(200),
                  inspection_type VARCHAR(16),frequency_type VARCHAR(16),start_date DATE,end_date DATE,
                  owner_user_id BIGINT,status VARCHAR(16),version INT,created_at TIMESTAMP,deleted_flag INT,remark VARCHAR(500))
                """);
        jdbc.execute("""
                CREATE TABLE qs_inspection_record(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,plan_id BIGINT,project_id BIGINT,inspection_code VARCHAR(64),
                  inspection_date DATE,location VARCHAR(200),inspector_user_id BIGINT,conclusion VARCHAR(16),
                  summary VARCHAR(1000),status VARCHAR(16),version INT,created_at TIMESTAMP,deleted_flag INT,remark VARCHAR(500))
                """);
        jdbc.execute("""
                CREATE TABLE qs_issue(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,plan_id BIGINT,inspection_id BIGINT,project_id BIGINT,
                  issue_code VARCHAR(64),issue_type VARCHAR(16),category VARCHAR(100),severity VARCHAR(16),
                  title VARCHAR(200),description VARCHAR(2000),responsible_kind VARCHAR(16),
                  responsible_partner_id BIGINT,responsible_user_id BIGINT,due_date DATE,status VARCHAR(32),
                  version INT,created_at TIMESTAMP,deleted_flag INT,remark VARCHAR(500))
                """);
    }

    private static final class CountingJdbcTemplate extends JdbcTemplate {
        private int queries;

        private CountingJdbcTemplate(javax.sql.DataSource dataSource) {
            super(dataSource);
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
}
