package com.cgcpms.supplier;

import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.supplier.service.SupplierSourcingQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplierSourcingWorkspacePaginationTest {
    private CountingJdbcTemplate jdbc;
    private SupplierSourcingQueryService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:supplier_workspace_" + System.nanoTime()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new CountingJdbcTemplate(dataSource);
        createSchema(jdbc);
        ProjectAccessChecker accessChecker = mock(ProjectAccessChecker.class);
        when(accessChecker.sqlScope()).thenReturn(new ProjectAccessChecker.ProjectSqlScope(
                "p.tenant_id=? AND p.deleted_flag=0", List.of(7L)));
        service = new SupplierSourcingQueryService(jdbc, accessChecker);
    }

    @Test
    void workspaceIsOneBoundedRequestWithConstantQueriesAndTenantSafePages() {
        insertProjects(50, 7L);
        insertProject(900, 8L);

        jdbc.reset();
        var first = service.workspace(1, 1, 1, 10, null);
        assertEquals(6, jdbc.queries());
        assertEquals(50, first.events().getTotal());
        assertEquals(50, first.performance().getTotal());
        assertEquals(50, first.returns().getTotal());
        assertEquals(10, first.events().getRecords().size());
        assertEquals(10, first.performance().getRecords().size());
        assertEquals(10, first.returns().getRecords().size());
        assertTrue(first.performance().getRecords().stream()
                .allMatch(row -> row.partnerName().startsWith("供应商-")));

        jdbc.reset();
        var second = service.workspace(2, 2, 2, 10, null);
        assertEquals(6, jdbc.queries());
        var firstEventIds = new HashSet<>(first.events().getRecords().stream()
                .map(row -> row.id()).toList());
        assertTrue(second.events().getRecords().stream()
                .noneMatch(row -> firstEventIds.contains(row.id())));

        jdbc.reset();
        var foreign = service.workspace(1, 1, 1, 10, 900L);
        assertEquals(6, jdbc.queries());
        assertEquals(0, foreign.events().getTotal());
        assertEquals(0, foreign.performance().getTotal());
        assertEquals(0, foreign.returns().getTotal());
    }

    @Test
    void performanceCandidatesExcludeEvaluatedOrdersWithoutClientSideKnowledge() {
        insertProject(1, 7L);
        jdbc.update("""
                INSERT INTO mat_purchase_order
                VALUES(9001,7,1,1001,7001,'PO-CANDIDATE','APPROVED',9001,0)
                """);

        jdbc.reset();
        var candidates = service.performanceCandidates(1, 100, null);

        assertEquals(2, jdbc.queries());
        assertEquals(1, candidates.getTotal());
        assertEquals("9001", candidates.getRecords().getFirst().id());
        assertEquals("供应商-1", candidates.getRecords().getFirst().partnerName());
    }

    private void insertProjects(int count, long tenantId) {
        for (int id = 1; id <= count; id++) insertProject(id, tenantId);
    }

    private void insertProject(long id, long tenantId) {
        long partnerId = 1000 + id;
        long eventId = 2000 + id;
        long evaluationId = 3000 + id;
        long returnId = 4000 + id;
        long orderId = 6000 + id;
        jdbc.update("INSERT INTO pm_project VALUES(?,?,?,0)", id, tenantId, "项目-" + id);
        jdbc.update("INSERT INTO md_partner VALUES(?,?,?,?,0)",
                partnerId, tenantId, "SUP-" + id, "供应商-" + id);
        jdbc.update("""
                INSERT INTO sp_sourcing_event
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)
                """, eventId, tenantId, id, 5000 + id, "SRC-" + id, "招采-" + id,
                "INQUIRY", "2026-08-12 12:00:00", "CNY", "PUBLISHED",
                null, null, null, null, 0, id, id);
        jdbc.update("""
                INSERT INTO sp_performance_evaluation
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)
                """, evaluationId, tenantId, id, partnerId, 7000 + id, orderId,
                "PERF-" + id, "2026-07-01", "2026-07-31",
                "90.00", "91.00", "92.00", "93.00", "91.50", "A",
                "表现稳定", 0, "CONFIRMED", id, id);
        jdbc.update("""
                INSERT INTO sp_supplier_return
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)
                """, returnId, tenantId, id, partnerId, 7000 + id, orderId, 8000 + id,
                "RET-" + id, "2026-08-01", "2.0000", "20.00", "质量退货",
                "CONFIRMED", id, id);
        jdbc.update("INSERT INTO sp_supplier_return_item VALUES(?,?,?,0)",
                5000 + id, tenantId, returnId);
        jdbc.update("""
                INSERT INTO mat_purchase_order
                VALUES(?,?,?,?,?,?,?,?,0)
                """, orderId, tenantId, id, partnerId, 7000 + id,
                "PO-" + id, "APPROVED", id);
    }

    private static void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE pm_project(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_name VARCHAR(100),deleted_flag INT)");
        jdbc.execute("CREATE TABLE md_partner(id BIGINT PRIMARY KEY,tenant_id BIGINT,partner_code VARCHAR(64),partner_name VARCHAR(100),deleted_flag INT)");
        jdbc.execute("""
                CREATE TABLE sp_sourcing_event(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,purchase_request_id BIGINT,
                  sourcing_code VARCHAR(64),sourcing_title VARCHAR(200),sourcing_type VARCHAR(16),
                  deadline TIMESTAMP,currency_code VARCHAR(8),status VARCHAR(20),awarded_quote_id BIGINT,
                  awarded_partner_id BIGINT,contract_id BIGINT,award_reason VARCHAR(1000),version INT,
                  created_at BIGINT,updated_at BIGINT,deleted_flag INT)
                """);
        jdbc.execute("""
                CREATE TABLE sp_performance_evaluation(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,partner_id BIGINT,
                  contract_id BIGINT,purchase_order_id BIGINT,evaluation_code VARCHAR(64),
                  period_start DATE,period_end DATE,delivery_score DECIMAL(5,2),quality_score DECIMAL(5,2),
                  service_score DECIMAL(5,2),commercial_score DECIMAL(5,2),total_score DECIMAL(5,2),
                  grade VARCHAR(8),evaluation_comment VARCHAR(1000),recommend_blacklist INT,
                  status VARCHAR(16),created_at BIGINT,updated_at BIGINT,deleted_flag INT)
                """);
        jdbc.execute("""
                CREATE TABLE sp_supplier_return(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,partner_id BIGINT,
                  contract_id BIGINT,purchase_order_id BIGINT,receipt_id BIGINT,return_code VARCHAR(64),
                  return_date DATE,return_quantity DECIMAL(18,4),return_amount DECIMAL(18,2),
                  reason VARCHAR(1000),status VARCHAR(16),created_at BIGINT,updated_at BIGINT,deleted_flag INT)
                """);
        jdbc.execute("CREATE TABLE sp_supplier_return_item(id BIGINT PRIMARY KEY,tenant_id BIGINT,return_id BIGINT,deleted_flag INT)");
        jdbc.execute("""
                CREATE TABLE mat_purchase_order(
                  id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,partner_id BIGINT,
                  contract_id BIGINT,order_code VARCHAR(64),approval_status VARCHAR(20),
                  created_at BIGINT,deleted_flag INT)
                """);
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
}
