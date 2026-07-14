package com.cgcpms.overhead;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.overhead.entity.OverheadAllocationRule;
import com.cgcpms.overhead.mapper.OverheadAllocationRuleMapper;
import com.cgcpms.overhead.service.OverheadAllocationService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
class OverheadAllocationServiceTest {

    private static final long TENANT_ID = 940024L;
    private static final long OTHER_TENANT_ID = 940025L;
    private static final long USER_ID = 94002401L;
    private static final long PROJECT_1 = 94002411L;
    private static final long PROJECT_2 = 94002412L;
    private static final long OTHER_PROJECT = 94002511L;
    private static final long SUBJECT_EQUAL = 94002421L;
    private static final long SUBJECT_LABOR = 94002422L;
    private static final long SUBJECT_CONTRACT = 94002423L;
    private static final long SUBJECT_ZERO = 94002424L;
    private static final LocalDate PERIOD = YearMonth.now().minusMonths(1).atEndOfMonth();

    @Autowired private OverheadAllocationService service;
    @Autowired private OverheadAllocationRuleMapper ruleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean private CostItemMapper costItemMapper;
    @MockitoSpyBean private CostSummaryService costSummaryService;

    @BeforeEach
    void setUp() {
        setUserContext(TENANT_ID);
        cleanTenant(TENANT_ID);
        cleanTenant(OTHER_TENANT_ID);
        seedProject(PROJECT_1, TENANT_ID, "OH-P1", "100.00");
        seedProject(PROJECT_2, TENANT_ID, "OH-P2", "300.00");
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(costItemMapper, costSummaryService);
        cleanTenant(TENANT_ID);
        cleanTenant(OTHER_TENANT_ID);
        UserContext.clear();
    }

    @Test
    @DisplayName("CRUD 保持租户隔离，规则默认 ENABLE")
    void crudKeepsTenantIsolation() {
        Long id = service.create(rule(SUBJECT_EQUAL, "EQUAL", "MONTHLY"));
        assertNotNull(id);
        assertEquals("ENABLE", ruleMapper.selectById(id).getStatus());
        assertTrue(service.getPage(1, 10).getRecords().stream()
                .allMatch(value -> value.getTenantId().equals(TENANT_ID)));

        setUserContext(OTHER_TENANT_ID);
        assertThrows(BusinessException.class,
                () -> service.update(update(id, SUBJECT_EQUAL, "DIRECT_LABOR", "MONTHLY")));
        assertThrows(BusinessException.class, () -> service.delete(id));
    }

    @Test
    @DisplayName("当前月不得提前占用幂等键，历史完整月仍可执行")
    void periodMustBeCompletedMonthEndWithoutEarlyIdempotencyClaim() {
        assertThrows(BusinessException.class,
                () -> service.executeAllocation(TENANT_ID, LocalDate.of(2026, 6, 1)));
        assertThrows(BusinessException.class,
                () -> service.executeAllocation(TENANT_ID, YearMonth.now().plusMonths(1).atEndOfMonth()));
        assertThrows(BusinessException.class,
                () -> service.executeAllocation(TENANT_ID, YearMonth.now().atEndOfMonth()));
        assertEquals(0, runCount(TENANT_ID));

        service.create(rule(SUBJECT_EQUAL, "EQUAL", "MONTHLY"));
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "1.00", "PERIOD_SOURCE", 100L);

        var result = service.executeAllocation(TENANT_ID, PERIOD);

        assertEquals(1, result.createdRunCount());
        assertEquals(1, runCount(TENANT_ID));
        assertMoney("1.00", allocatedSum(TENANT_ID, SUBJECT_EQUAL));
    }

    @Test
    @DisplayName("来源排除既有分摊，零金额跳过，尾差按分守恒且重复执行幂等")
    void equalAllocationConservesCentsAndIsIdempotent() {
        service.create(rule(SUBJECT_EQUAL, "EQUAL", "MONTHLY"));
        service.create(rule(SUBJECT_ZERO, "EQUAL", "MONTHLY"));
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "100.01", "OH_SOURCE", 1L);
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "OVERHEAD_ALLOCATED", "999.99",
                "OVERHEAD_ALLOCATION", 2L);

        var first = service.executeAllocation(TENANT_ID, PERIOD);
        assertEquals(2, first.ruleCount());
        assertEquals(2, first.createdRunCount());
        assertEquals(2, first.costItemCount());
        assertEquals("100.01", first.allocatedAmount());
        assertFalse(first.idempotent());
        assertMoney("100.01", allocatedSum(TENANT_ID, SUBJECT_EQUAL));
        assertMoney("50.00", allocatedForProject(TENANT_ID, SUBJECT_EQUAL, PROJECT_1));
        assertMoney("50.01", allocatedForProject(TENANT_ID, SUBJECT_EQUAL, PROJECT_2));
        assertEquals("SKIPPED_ZERO", jdbcTemplate.queryForObject(
                "SELECT run_status FROM overhead_allocation_run WHERE tenant_id=? AND rule_id=(SELECT id FROM overhead_allocation_rule WHERE tenant_id=? AND cost_subject_id=?)",
                String.class, TENANT_ID, TENANT_ID, SUBJECT_ZERO));

        var repeated = service.executeAllocation(TENANT_ID, PERIOD);
        assertTrue(repeated.idempotent());
        assertEquals(0, repeated.createdRunCount());
        assertEquals(2, repeated.duplicateRunCount());
        assertEquals(2, allocatedCount(TENANT_ID));
    }

    @Test
    @DisplayName("EQUAL、DIRECT_LABOR、CONTRACT_AMOUNT 均保持原语义并逐规则守恒")
    void allExistingBasesKeepSemantics() {
        service.create(rule(SUBJECT_EQUAL, "EQUAL", "MONTHLY"));
        service.create(rule(SUBJECT_LABOR, "DIRECT_LABOR", "MONTHLY"));
        service.create(rule(SUBJECT_CONTRACT, "CONTRACT_AMOUNT", "MONTHLY"));

        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "1.01", "EQ_SOURCE", 11L);
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_LABOR, "LABOR", "10.00", "LABOR_SOURCE", 12L);
        insertCost(TENANT_ID, PROJECT_2, SUBJECT_LABOR, "LABOR", "30.00", "LABOR_SOURCE", 13L);
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_CONTRACT, "MATERIAL", "100.01", "CT_SOURCE", 14L);

        service.executeAllocation(TENANT_ID, PERIOD);

        assertMoney("1.01", allocatedSum(TENANT_ID, SUBJECT_EQUAL));
        assertMoney("0.50", allocatedForProject(TENANT_ID, SUBJECT_EQUAL, PROJECT_1));
        assertMoney("0.51", allocatedForProject(TENANT_ID, SUBJECT_EQUAL, PROJECT_2));
        assertMoney("40.00", allocatedSum(TENANT_ID, SUBJECT_LABOR));
        assertMoney("10.00", allocatedForProject(TENANT_ID, SUBJECT_LABOR, PROJECT_1));
        assertMoney("30.00", allocatedForProject(TENANT_ID, SUBJECT_LABOR, PROJECT_2));
        assertMoney("100.01", allocatedSum(TENANT_ID, SUBJECT_CONTRACT));
        assertMoney("25.00", allocatedForProject(TENANT_ID, SUBJECT_CONTRACT, PROJECT_1));
        assertMoney("75.01", allocatedForProject(TENANT_ID, SUBJECT_CONTRACT, PROJECT_2));
    }

    @Test
    @DisplayName("规则、项目、来源成本和执行事实均受认证租户隔离")
    void executionIsTenantScopedEndToEnd() {
        service.create(rule(SUBJECT_EQUAL, "EQUAL", "MONTHLY"));
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "10.00", "TENANT_SOURCE", 21L);

        seedProject(OTHER_PROJECT, OTHER_TENANT_ID, "OH-OTHER", "1000.00");
        insertRuleDirect(OTHER_TENANT_ID, SUBJECT_EQUAL, "EQUAL");
        insertCost(OTHER_TENANT_ID, OTHER_PROJECT, SUBJECT_EQUAL, "MATERIAL", "999.00", "TENANT_SOURCE", 22L);

        service.executeAllocation(TENANT_ID, PERIOD);

        assertMoney("10.00", allocatedSum(TENANT_ID, SUBJECT_EQUAL));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM overhead_allocation_run WHERE tenant_id=?", Integer.class, OTHER_TENANT_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cost_item WHERE tenant_id=? AND source_type='OVERHEAD_ALLOCATION'", Integer.class, OTHER_TENANT_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cost_item WHERE tenant_id=? AND source_type='OVERHEAD_ALLOCATION' AND project_id=?",
                Integer.class, TENANT_ID, OTHER_PROJECT));
    }

    @Test
    @DisplayName("公共定时入口可发现非零租户并保持其他租户隔离")
    void scheduledEntryDiscoversNonZeroTenant() {
        service.create(rule(SUBJECT_EQUAL, "EQUAL", "MONTHLY"));
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "6.01", "SCHEDULED_SOURCE", 30L);
        seedProject(OTHER_PROJECT, OTHER_TENANT_ID, "OH-OTHER-SCHEDULED", "100.00");
        insertCost(OTHER_TENANT_ID, OTHER_PROJECT, SUBJECT_EQUAL, "MATERIAL", "99.00",
                "SCHEDULED_SOURCE", 300L);

        UserContext.clear();
        service.scheduledMonthlyAllocation();

        assertEquals(1, runCount(TENANT_ID));
        assertEquals(2, allocatedCount(TENANT_ID));
        assertMoney("6.01", allocatedSum(TENANT_ID, SUBJECT_EQUAL));
        assertEquals(0, runCount(OTHER_TENANT_ID));
        assertEquals(0, allocatedCount(OTHER_TENANT_ID));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM cost_item
                WHERE tenant_id=? AND source_type='SCHEDULED_SOURCE' AND amount=99.00
                """, Integer.class, OTHER_TENANT_ID));
    }

    @Test
    @DisplayName("手工与定时并发执行只保留一条事实和一组有效成本")
    void concurrentExecutionUsesDatabaseIdempotencyGate() throws Exception {
        service.create(rule(SUBJECT_EQUAL, "EQUAL", "MONTHLY"));
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "8.01", "CONCURRENT_SOURCE", 31L);

        CompletableFuture<?> first = CompletableFuture.supplyAsync(() -> executeAsTenant());
        CompletableFuture<?> second = CompletableFuture.supplyAsync(this::executeScheduledAsTenant);
        CompletableFuture.allOf(first, second).get(20, TimeUnit.SECONDS);

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM overhead_allocation_run WHERE tenant_id=?", Integer.class, TENANT_ID));
        assertEquals(2, allocatedCount(TENANT_ID));
        assertMoney("8.01", allocatedSum(TENANT_ID, SUBJECT_EQUAL));
    }

    @Test
    @DisplayName("不同月份、规则、项目和租户不会错误去重")
    void idempotencyKeyDoesNotCollideAcrossDimensions() {
        LocalDate previousPeriod = YearMonth.from(PERIOD).minusMonths(1).atEndOfMonth();
        service.create(rule(SUBJECT_EQUAL, "EQUAL", "MONTHLY"));
        service.create(rule(SUBJECT_LABOR, "DIRECT_LABOR", "MONTHLY"));
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "2.01", "DIMENSION_SOURCE", 61L);
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_LABOR, "LABOR", "4.00", "DIMENSION_SOURCE", 62L);
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "3.01", "DIMENSION_SOURCE", 63L,
                previousPeriod);

        service.executeAllocation(TENANT_ID, PERIOD);
        service.executeAllocation(TENANT_ID, previousPeriod);

        seedProject(OTHER_PROJECT, OTHER_TENANT_ID, "OH-OTHER-DIM", "100.00");
        setUserContext(OTHER_TENANT_ID);
        service.create(rule(SUBJECT_EQUAL, "EQUAL", "MONTHLY"));
        insertCost(OTHER_TENANT_ID, OTHER_PROJECT, SUBJECT_EQUAL, "MATERIAL", "5.00",
                "DIMENSION_SOURCE", 64L);
        service.executeAllocation(OTHER_TENANT_ID, PERIOD);

        assertEquals(4, runCount(TENANT_ID));
        assertEquals(1, runCount(OTHER_TENANT_ID));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM overhead_allocation_run
                WHERE tenant_id=? AND period=?
                """, Integer.class, TENANT_ID, PERIOD));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM cost_item
                WHERE tenant_id=? AND source_type='OVERHEAD_ALLOCATION' AND cost_date=?
                """, Integer.class, TENANT_ID, previousPeriod));
        assertEquals(1, allocatedCount(OTHER_TENANT_ID));
    }

    @Test
    @DisplayName("任一成本写入失败时执行事实和全部成本回滚")
    void costWriteFailureRollsBackRunAndCosts() {
        service.create(rule(SUBJECT_EQUAL, "EQUAL", "MONTHLY"));
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "8.00", "ROLLBACK_SOURCE", 41L);
        doThrow(new IllegalStateException("injected cost failure"))
                .when(costItemMapper).insert(org.mockito.ArgumentMatchers.<CostItem>argThat(item ->
                        "OVERHEAD_ALLOCATION".equals(item.getSourceType())
                                && PROJECT_2 == item.getProjectId()));

        assertThrows(IllegalStateException.class, () -> service.executeAllocation(TENANT_ID, PERIOD));
        assertEquals(0, runCount(TENANT_ID));
        assertEquals(0, allocatedCount(TENANT_ID));
    }

    @Test
    @DisplayName("汇总刷新失败时执行事实和成本明细同事务回滚")
    void summaryFailureRollsBackRunAndCosts() {
        service.create(rule(SUBJECT_EQUAL, "EQUAL", "MONTHLY"));
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "8.00", "SUMMARY_SOURCE", 51L);
        doThrow(new IllegalStateException("injected summary failure"))
                .when(costSummaryService).refreshSummary(TENANT_ID, PROJECT_1);

        assertThrows(IllegalStateException.class, () -> service.executeAllocation(TENANT_ID, PERIOD));
        assertEquals(0, runCount(TENANT_ID));
        assertEquals(0, allocatedCount(TENANT_ID));
    }

    @Test
    @DisplayName("V149 唯一键直接阻断相同租户规则月份的第二条执行事实")
    void migrationUniqueKeyBlocksDuplicateFacts() {
        jdbcTemplate.update("""
                INSERT INTO overhead_allocation_run
                (id,tenant_id,rule_id,period,trigger_type,run_status,allocated_amount,cost_item_count,deleted_flag)
                VALUES (?,?,?,?,?,'SUCCESS',0,0,0)
                """, 940024901L, TENANT_ID, 940024902L, PERIOD, "MANUAL");
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO overhead_allocation_run
                (id,tenant_id,rule_id,period,trigger_type,run_status,allocated_amount,cost_item_count,deleted_flag)
                VALUES (?,?,?,?,?,'SUCCESS',0,0,0)
                """, 940024903L, TENANT_ID, 940024902L, PERIOD, "SCHEDULED"));
    }

    private void setUserContext(long tenantId) {
        UserContext.set(Jwts.claims().subject("overhead-test").add("userId", USER_ID)
                .add("username", "overhead-test").add("tenantId", tenantId)
                .add("roleCodes", List.of("ADMIN")).build());
    }

    private Object executeAsTenant() {
        try {
            setUserContext(TENANT_ID);
            return service.executeAllocation(TENANT_ID, PERIOD);
        } finally {
            UserContext.clear();
        }
    }

    private Object executeScheduledAsTenant() {
        UserContext.clear();
        service.scheduledMonthlyAllocation();
        return Boolean.TRUE;
    }

    private void cleanTenant(long tenantId) {
        jdbcTemplate.update("DELETE FROM cost_summary WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM cost_item WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM overhead_allocation_run WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM overhead_allocation_rule WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM pm_project WHERE tenant_id = ?", tenantId);
    }

    private void seedProject(long id, long tenantId, String code, String contractAmount) {
        jdbcTemplate.update("""
                INSERT INTO pm_project
                (id,tenant_id,project_code,project_name,contract_amount,target_cost,status,deleted_flag)
                VALUES (?,?,?,?,?,0,'ACTIVE',0)
                """, id, tenantId, code, code, new BigDecimal(contractAmount));
    }

    private void insertRuleDirect(long tenantId, long subjectId, String basis) {
        OverheadAllocationRule value = rule(subjectId, basis, "MONTHLY");
        value.setTenantId(tenantId);
        value.setStatus("ENABLE");
        ruleMapper.insert(value);
    }

    private void insertCost(long tenantId, long projectId, long subjectId, String costType,
                            String amount, String sourceType, long sourceId) {
        insertCost(tenantId, projectId, subjectId, costType, amount, sourceType, sourceId, PERIOD);
    }

    private void insertCost(long tenantId, long projectId, long subjectId, String costType,
                            String amount, String sourceType, long sourceId, LocalDate costDate) {
        long id = 940024000000L + sourceId;
        jdbcTemplate.update("""
                INSERT INTO cost_item
                (id,tenant_id,project_id,cost_subject_id,cost_type,amount,tax_amount,amount_without_tax,
                 source_type,source_id,source_item_id,cost_date,cost_status,generated_flag,deleted_flag)
                VALUES (?,?,?,?,?,?,0,?,?,?,?,?,'CONFIRMED',1,0)
                """, id, tenantId, projectId, subjectId, costType, new BigDecimal(amount),
                new BigDecimal(amount), sourceType, sourceId, sourceId, costDate);
    }

    private int runCount(long tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM overhead_allocation_run WHERE tenant_id=?", Integer.class, tenantId);
    }

    private int allocatedCount(long tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cost_item WHERE tenant_id=? AND source_type='OVERHEAD_ALLOCATION' AND source_id IN (SELECT id FROM overhead_allocation_run WHERE tenant_id=?)",
                Integer.class, tenantId, tenantId);
    }

    private BigDecimal allocatedSum(long tenantId, long subjectId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM cost_item
                WHERE tenant_id=? AND cost_subject_id=? AND source_type='OVERHEAD_ALLOCATION'
                  AND source_id IN (SELECT id FROM overhead_allocation_run WHERE tenant_id=?)
                """, BigDecimal.class, tenantId, subjectId, tenantId);
    }

    private BigDecimal allocatedForProject(long tenantId, long subjectId, long projectId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM cost_item
                WHERE tenant_id=? AND cost_subject_id=? AND project_id=? AND source_type='OVERHEAD_ALLOCATION'
                  AND source_id IN (SELECT id FROM overhead_allocation_run WHERE tenant_id=?)
                """, BigDecimal.class, tenantId, subjectId, projectId, tenantId);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected=" + expected + ", actual=" + actual);
    }

    private OverheadAllocationRule rule(long subjectId, String basis, String cycle) {
        OverheadAllocationRule value = new OverheadAllocationRule();
        value.setCostSubjectId(subjectId);
        value.setAllocationBasis(basis);
        value.setAllocationCycle(cycle);
        return value;
    }

    private OverheadAllocationRule update(long id, long subjectId, String basis, String cycle) {
        OverheadAllocationRule value = rule(subjectId, basis, cycle);
        value.setId(id);
        return value;
    }
}
