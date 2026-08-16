package com.cgcpms.overhead;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.cost.strategy.CostSubjectResolver;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cgcpms_overhead_v301;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;LOCK_TIMEOUT=300000",
        "spring.flyway.locations=classpath:db/migration-h2,filesystem:src/main/resources/db/migration-h2-legacy,classpath:com/cgcpms/common/migration,classpath:com/cgcpms/common/migration/**/*.class"
})
@ActiveProfiles("local")
class OverheadAllocationServiceTest {

    private static final long TENANT_ID = 940024L;
    private static final long OTHER_TENANT_ID = 940025L;
    private static final long USER_ID = 94002401L;
    private static final long OTHER_USER_ID = 94002501L;
    private static final long FINANCE_ROLE_ID = 94002402L;
    private static final long OTHER_FINANCE_ROLE_ID = 94002502L;
    private static final long PROJECT_1 = 94002411L;
    private static final long PROJECT_2 = 94002412L;
    private static final long OTHER_PROJECT = 94002511L;
    private static final long SUBJECT_EQUAL = 94002421L;
    private static final long SUBJECT_LABOR = 94002422L;
    private static final long SUBJECT_CONTRACT = 94002423L;
    private static final long SUBJECT_ZERO = 94002424L;
    private static final long SUBJECT_VALIDATED = 94002431L;
    private static final long SUBJECT_OTHER_TENANT = 94002432L;
    private static final long SUBJECT_DISABLED = 94002433L;
    private static final long SUBJECT_NOT_OVERHEAD = 94002434L;
    private static final LocalDate PERIOD = YearMonth.now().minusMonths(1).atEndOfMonth();

    @Autowired private OverheadAllocationService service;
    @Autowired private OverheadAllocationRuleMapper ruleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean private CostItemMapper costItemMapper;
    @MockitoSpyBean private CostSummaryService costSummaryService;
    @MockitoBean private CostSubjectResolver costSubjectResolver;

    @BeforeEach
    void setUp() {
        cleanTenant(TENANT_ID);
        cleanTenant(OTHER_TENANT_ID);
        seedCompanyFinance(TENANT_ID, USER_ID, FINANCE_ROLE_ID);
        seedCompanyFinance(OTHER_TENANT_ID, OTHER_USER_ID, OTHER_FINANCE_ROLE_ID);
        setUserContext(TENANT_ID);
        lenient().when(costSubjectResolver.resolveForFact(anyLong(), anyLong(), anyString(), anyString(),
                        anyLong(), anyLong(), anyLong(), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    Long original = invocation.getArgument(6);
                    return new CostSubjectResolver.Decision(original, null, null, null, null,
                            "CLASSIFIED", null);
                });
        seedSubject(SUBJECT_EQUAL, TENANT_ID, "5401.04.21", "OVERHEAD", "COST", "ENABLE");
        seedSubject(SUBJECT_LABOR, TENANT_ID, "5401.04.22", "OVERHEAD", "COST", "ENABLE");
        seedSubject(SUBJECT_CONTRACT, TENANT_ID, "5401.04.23", "OVERHEAD", "COST", "ENABLE");
        seedSubject(SUBJECT_ZERO, TENANT_ID, "5401.04.24", "OVERHEAD", "COST", "ENABLE");
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
        Long id = service.create(rule(SUBJECT_EQUAL, "CONTRACT_AMOUNT", "MONTHLY"));
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
    @DisplayName("删除仅允许无执行事实的当前租户规则")
    void deleteRejectsReferencedRuleAndKeepsTenantIsolation() {
        Long removableId = service.create(rule(SUBJECT_EQUAL, "USAGE", "MONTHLY"));
        service.delete(removableId);
        assertEquals(null, ruleMapper.selectById(removableId));

        Long protectedId = service.create(rule(SUBJECT_LABOR, "DIRECT_LABOR", "MONTHLY"));
        jdbcTemplate.update("""
                INSERT INTO overhead_allocation_run
                (id,tenant_id,rule_id,period,trigger_type,run_status,allocated_amount,cost_item_count,deleted_flag)
                VALUES (?,?,?,?,?,'SUCCESS',0,0,0)
                """, 940024904L, TENANT_ID, protectedId, PERIOD, "MANUAL");

        BusinessException referenced = assertThrows(BusinessException.class,
                () -> service.delete(protectedId));
        assertEquals("RULE_ALREADY_EXECUTED", referenced.getCode());
        assertNotNull(ruleMapper.selectById(protectedId));

        setUserContext(OTHER_TENANT_ID);
        BusinessException hidden = assertThrows(BusinessException.class,
                () -> service.delete(protectedId));
        assertEquals("RULE_NOT_FOUND", hidden.getCode());
    }

    @Test
    @DisplayName("已形成执行事实的间接费规则不可再修改")
    void executedRuleCannotBeUpdated() {
        seedSubject(SUBJECT_VALIDATED, TENANT_ID, "5401.04.31", "OVERHEAD", "COST", "ENABLE");
        try {
            Long id = service.createValidated(SUBJECT_VALIDATED, "DIRECT_LABOR", "MONTHLY");
            jdbcTemplate.update("""
                    INSERT INTO overhead_allocation_run
                    (id,tenant_id,rule_id,period,trigger_type,run_status,allocated_amount,cost_item_count,deleted_flag)
                    VALUES (?,?,?,?,?,'SUCCESS',0,0,0)
                    """, 940024905L, TENANT_ID, id, PERIOD, "MANUAL");

            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.updateValidated(id, SUBJECT_VALIDATED, "DIRECT_LABOR", "MONTHLY"));
            assertEquals("RULE_ALREADY_EXECUTED", error.getCode());
            assertEquals("DIRECT_LABOR", ruleMapper.selectById(id).getAllocationBasis());

            service.setStatus(id, "DISABLE");
            OverheadAllocationRule disabled = ruleMapper.selectById(id);
            assertEquals("DISABLE", disabled.getStatus());
            assertEquals("DIRECT_LABOR", disabled.getAllocationBasis());
            assertEquals(SUBJECT_VALIDATED, disabled.getCostSubjectId());
            service.executeAllocation(TENANT_ID, PERIOD.minusMonths(1).withDayOfMonth(1)
                    .plusMonths(1).minusDays(1));
            assertEquals(1, runCount(TENANT_ID), "停用后不得继续生成下一期间分摊事实");
        } finally {
            jdbcTemplate.update("DELETE FROM overhead_allocation_run WHERE id=?", 940024905L);
            jdbcTemplate.update("DELETE FROM cost_subject WHERE id=?", SUBJECT_VALIDATED);
        }
    }

    @Test
    @DisplayName("同期间跨项目正负事实净额为零时逐项目清算并允许停用规则")
    void zeroNetPendingFactsAreClearedPerProjectBeforeRuleDisable() {
        Long id = service.create(rule(SUBJECT_ZERO, "CONTRACT_AMOUNT", "MONTHLY"));
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_ZERO, "OVERHEAD", "100.00", "ZERO_NET_POSITIVE", 9061L);
        insertCost(TENANT_ID, PROJECT_2, SUBJECT_ZERO, "OVERHEAD", "-100.00", "ZERO_NET_NEGATIVE", 9062L);

        BusinessException pending = assertThrows(BusinessException.class,
                () -> service.setStatus(id, "DISABLE"));
        assertEquals("OVERHEAD_RULE_PENDING_ALLOCATION", pending.getCode());
        assertEquals("ENABLE", ruleMapper.selectById(id).getStatus());

        var execution = service.executeAllocation(TENANT_ID, PERIOD);
        assertEquals(1, execution.createdRunCount());
        assertEquals("SUCCESS", jdbcTemplate.queryForObject(
                "SELECT run_status FROM overhead_allocation_run WHERE tenant_id=? AND rule_id=?",
                String.class, TENANT_ID, id));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM cost_item
                WHERE tenant_id=? AND source_type='OVERHEAD_ALLOCATION_CLEARING'
                  AND original_cost_item_id IN (?,?)
                """, Integer.class, TENANT_ID, 940024000000L + 9061L, 940024000000L + 9062L));
        assertEquals(0, allocatedCount(TENANT_ID));
        assertMoney("0.00", projectActualNet(PROJECT_1, SUBJECT_ZERO));
        assertMoney("0.00", projectActualNet(PROJECT_2, SUBJECT_ZERO));

        service.setStatus(id, "DISABLE");
        assertEquals("DISABLE", ruleMapper.selectById(id).getStatus());
    }

    @Test
    @DisplayName("受控新建只接受当前租户启用的间接费成本科目")
    void validatedCreateRequiresTenantEnabledOverheadCostSubject() {
        seedSubject(SUBJECT_VALIDATED, TENANT_ID, "5401.04.31", "OVERHEAD", "COST", "ENABLE");
        seedSubject(SUBJECT_OTHER_TENANT, OTHER_TENANT_ID, "5401.04.32", "OVERHEAD", "COST", "ENABLE");
        seedSubject(SUBJECT_DISABLED, TENANT_ID, "5401.04.33", "OVERHEAD", "COST", "DISABLE");
        seedSubject(SUBJECT_NOT_OVERHEAD, TENANT_ID, "5001.01.34", "MATERIAL", "COST", "ENABLE");
        try {
            Long id = service.createValidated(SUBJECT_VALIDATED, "DIRECT_LABOR", "MONTHLY");
            OverheadAllocationRule saved = ruleMapper.selectById(id);
            assertEquals(TENANT_ID, saved.getTenantId());
            assertEquals("ENABLE", saved.getStatus());
            assertEquals(SUBJECT_VALIDATED, saved.getCostSubjectId());
            assertThrows(BusinessException.class,
                    () -> service.createValidated(SUBJECT_OTHER_TENANT, "DIRECT_LABOR", "MONTHLY"));
            assertThrows(BusinessException.class,
                    () -> service.createValidated(SUBJECT_DISABLED, "DIRECT_LABOR", "MONTHLY"));
            assertThrows(BusinessException.class,
                    () -> service.createValidated(SUBJECT_NOT_OVERHEAD, "DIRECT_LABOR", "MONTHLY"));
            assertThrows(BusinessException.class,
                    () -> service.createValidated(999999999L, "DIRECT_LABOR", "MONTHLY"));
            BusinessException unsupported = assertThrows(BusinessException.class,
                    () -> service.createValidated(SUBJECT_VALIDATED, "USAGE", "PER_OCCURRENCE"));
            assertEquals("OVERHEAD_RULE_UNSUPPORTED", unsupported.getCode());
        } finally {
            jdbcTemplate.update("DELETE FROM cost_subject WHERE id IN (?,?,?,?)",
                    SUBJECT_VALIDATED, SUBJECT_OTHER_TENANT, SUBJECT_DISABLED, SUBJECT_NOT_OVERHEAD);
        }
    }

    @Test
    @DisplayName("历史未实现规则执行时失败关闭且不生成运行记录或成本事实")
    void legacyUnsupportedRuleFailsClosedBeforePosting() {
        service.create(rule(SUBJECT_EQUAL, "USAGE", "MONTHLY"));
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "10.00", "LEGACY_USAGE", 901L);

        BusinessException unsupported = assertThrows(BusinessException.class,
                () -> service.executeAllocation(TENANT_ID, PERIOD));

        assertEquals("OVERHEAD_RULE_UNSUPPORTED", unsupported.getCode());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM overhead_allocation_run WHERE tenant_id=?", Integer.class, TENANT_ID));
        assertMoney("0.00", allocatedSum(TENANT_ID, SUBJECT_EQUAL));
    }

    @Test
    @DisplayName("受控修改校验规则与科目租户并保留服务端状态")
    void validatedUpdateRequiresTenantRuleAndEnabledOverheadCostSubject() {
        seedSubject(SUBJECT_VALIDATED, TENANT_ID, "5401.04.31", "OVERHEAD", "COST", "ENABLE");
        seedSubject(SUBJECT_OTHER_TENANT, OTHER_TENANT_ID, "5401.04.32", "OVERHEAD", "COST", "ENABLE");
        seedSubject(SUBJECT_DISABLED, TENANT_ID, "5401.04.33", "OVERHEAD", "COST", "DISABLE");
        seedSubject(SUBJECT_NOT_OVERHEAD, TENANT_ID, "5001.01.34", "MATERIAL", "COST", "ENABLE");
        Long id = service.create(rule(SUBJECT_EQUAL, "CONTRACT_AMOUNT", "MONTHLY"));
        Long otherId;
        setUserContext(OTHER_TENANT_ID);
        otherId = service.create(rule(SUBJECT_OTHER_TENANT, "USAGE", "MONTHLY"));
        setUserContext(TENANT_ID);
        jdbcTemplate.update("UPDATE overhead_allocation_rule SET status='DISABLE' WHERE id=?", id);
        try {
            service.updateValidated(id, SUBJECT_VALIDATED, "CONTRACT_AMOUNT", "MONTHLY");
            OverheadAllocationRule saved = ruleMapper.selectById(id);
            assertEquals(TENANT_ID, saved.getTenantId());
            assertEquals("DISABLE", saved.getStatus());
            assertEquals(SUBJECT_VALIDATED, saved.getCostSubjectId());
            assertEquals("CONTRACT_AMOUNT", saved.getAllocationBasis());
            assertEquals("MONTHLY", saved.getAllocationCycle());
            assertThrows(BusinessException.class,
                    () -> service.updateValidated(otherId, SUBJECT_VALIDATED, "DIRECT_LABOR", "MONTHLY"));
            assertThrows(BusinessException.class,
                    () -> service.updateValidated(999999999L, SUBJECT_VALIDATED, "DIRECT_LABOR", "MONTHLY"));
            assertThrows(BusinessException.class,
                    () -> service.updateValidated(id, SUBJECT_OTHER_TENANT, "DIRECT_LABOR", "MONTHLY"));
            assertThrows(BusinessException.class,
                    () -> service.updateValidated(id, SUBJECT_DISABLED, "DIRECT_LABOR", "MONTHLY"));
            assertThrows(BusinessException.class,
                    () -> service.updateValidated(id, SUBJECT_NOT_OVERHEAD, "DIRECT_LABOR", "MONTHLY"));
            BusinessException unsupported = assertThrows(BusinessException.class,
                    () -> service.updateValidated(id, SUBJECT_VALIDATED, "USAGE", "PER_OCCURRENCE"));
            assertEquals("OVERHEAD_RULE_UNSUPPORTED", unsupported.getCode());
        } finally {
            jdbcTemplate.update("DELETE FROM cost_subject WHERE id IN (?,?,?,?)",
                    SUBJECT_VALIDATED, SUBJECT_OTHER_TENANT, SUBJECT_DISABLED, SUBJECT_NOT_OVERHEAD);
        }
    }

    @Test
    @DisplayName("间接费治理写入口实时要求当前租户公司财务角色")
    void governanceWritesRequireLiveCompanyFinanceRole() {
        seedSubject(SUBJECT_VALIDATED, TENANT_ID, "5401.04.31", "OVERHEAD", "COST", "ENABLE");
        try {
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE tenant_id=? AND user_id=?", TENANT_ID, USER_ID);

            BusinessException createDenied = assertThrows(BusinessException.class,
                    () -> service.createValidated(SUBJECT_VALIDATED, "DIRECT_LABOR", "MONTHLY"));
            assertEquals("COST_COMPANY_FINANCE_REQUIRED", createDenied.getCode());
            BusinessException executeDenied = assertThrows(BusinessException.class,
                    () -> service.executeAllocation(TENANT_ID, PERIOD));
            assertEquals("COST_COMPANY_FINANCE_REQUIRED", executeDenied.getCode());
            assertEquals(0, ruleMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OverheadAllocationRule>()
                    .eq(OverheadAllocationRule::getTenantId, TENANT_ID)));
            assertEquals(0, runCount(TENANT_ID));
        } finally {
            jdbcTemplate.update("DELETE FROM cost_subject WHERE id=?", SUBJECT_VALIDATED);
        }
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

        service.create(rule(SUBJECT_EQUAL, "CONTRACT_AMOUNT", "MONTHLY"));
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "1.00", "PERIOD_SOURCE", 100L);

        var result = service.executeAllocation(TENANT_ID, PERIOD);

        assertEquals(1, result.createdRunCount());
        assertEquals(1, runCount(TENANT_ID));
        assertMoney("1.00", allocatedSum(TENANT_ID, SUBJECT_EQUAL));
    }

    @Test
    @DisplayName("历史重算派生事实不得进入不可逆的间接费分摊")
    void recalculationDerivedFactsFailClosedBeforeOverheadPosting() {
        long mappingVersionId = 94002480L;
        long batchId = 94002481L;
        long originalSourceId = 179L;
        long negativeSourceId = 180L;
        long positiveSourceId = 181L;
        service.create(rule(SUBJECT_EQUAL, "CONTRACT_AMOUNT", "MONTHLY"));
        jdbcTemplate.update("""
                INSERT INTO cost_subject_mapping_version
                (id,tenant_id,version_code,version_name,status,effective_date,created_by)
                VALUES (?,?,'OH-RECALC-V1','间接费重算隔离','DRAFT',CURRENT_DATE,?)
                """, mappingVersionId, TENANT_ID, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO cost_recalculation_batch
                (id,tenant_id,batch_code,batch_type,project_id,scope_key,cutoff_at,source_snapshot_hash,
                 idempotency_key,rule_version_id,status,old_snapshot,difference_report,created_by,reason)
                VALUES (?,?,'OH-RECALC-B1','HISTORY_RECALCULATION',?,'PROJECT:test',CURRENT_TIMESTAMP,
                        'source-hash','oh-recalc-b1',?,'POSTED','{}','{}',?,'间接费不可逆来源隔离')
                """, batchId, TENANT_ID, PROJECT_1, mappingVersionId, USER_ID);
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "10.00",
                "OH_RECALC_ORIGINAL", originalSourceId);
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "-10.00",
                "COST_RECALCULATION_NEGATIVE", negativeSourceId);
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "10.00",
                "COST_RECALCULATION_POSITIVE", positiveSourceId);
        long originalFactId = 940024000000L + originalSourceId;
        jdbcTemplate.update("""
                UPDATE cost_item SET adjustment_batch_id=?,original_cost_item_id=?
                WHERE id IN (?,?)
                """, batchId, originalFactId,
                940024000000L + negativeSourceId, 940024000000L + positiveSourceId);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.executeAllocation(TENANT_ID, PERIOD));

        assertEquals("OVERHEAD_RECALCULATION_SOURCE_NOT_SUPPORTED", error.getCode());
        assertEquals(0, allocatedCount(TENANT_ID));
        assertEquals(0, runCount(TENANT_ID));

        jdbcTemplate.update("UPDATE cost_recalculation_batch SET status='REVERSED' WHERE id=?", batchId);
        var restored = service.executeAllocation(TENANT_ID, PERIOD);
        assertEquals(1, restored.createdRunCount());
        assertMoney("10.00", allocatedSum(TENANT_ID, SUBJECT_EQUAL));
    }

    @Test
    @DisplayName("来源排除既有分摊，零金额跳过，尾差按分守恒且重复执行幂等")
    void equalAllocationConservesCentsAndIsIdempotent() {
        service.create(rule(SUBJECT_EQUAL, "CONTRACT_AMOUNT", "MONTHLY"));
        service.create(rule(SUBJECT_ZERO, "CONTRACT_AMOUNT", "MONTHLY"));
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "100.01", "OH_SOURCE", 1L);
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "OVERHEAD_ALLOCATED", "999.99",
                "OVERHEAD_ALLOCATION", 2L);

        var first = service.executeAllocation(TENANT_ID, PERIOD);
        assertEquals(2, first.ruleCount());
        assertEquals(2, first.createdRunCount());
        assertEquals(3, first.costItemCount());
        assertEquals("100.01", first.allocatedAmount());
        assertFalse(first.idempotent());
        assertMoney("100.01", allocatedSum(TENANT_ID, SUBJECT_EQUAL));
        assertMoney("25.00", allocatedForProject(TENANT_ID, SUBJECT_EQUAL, PROJECT_1));
        assertMoney("75.01", allocatedForProject(TENANT_ID, SUBJECT_EQUAL, PROJECT_2));
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
    @DisplayName("同月迟到成本按既有执行批次增量分摊且不重复清算")
    void lateFactsAreIncrementallyAllocatedInExistingMonthlyRun() {
        service.create(rule(SUBJECT_EQUAL, "CONTRACT_AMOUNT", "MONTHLY"));
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "100.00", "LATE_SOURCE", 81L);

        var first = service.executeAllocation(TENANT_ID, PERIOD);
        assertEquals(1, first.createdRunCount());
        assertMoney("100.00", allocatedSum(TENANT_ID, SUBJECT_EQUAL));

        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "50.00", "LATE_SOURCE", 82L);
        var incremental = service.executeAllocation(TENANT_ID, PERIOD);

        assertEquals(0, incremental.createdRunCount());
        assertEquals(1, incremental.duplicateRunCount());
        assertFalse(incremental.idempotent());
        assertEquals(1, runCount(TENANT_ID));
        assertMoney("150.00", allocatedSum(TENANT_ID, SUBJECT_EQUAL));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM cost_item
                WHERE tenant_id=? AND source_type='OVERHEAD_ALLOCATION_CLEARING'
                """, Integer.class, TENANT_ID));
    }

    @Test
    @DisplayName("已关闭项目或已结账期间不得生成间接费批次与成本事实")
    void closedProjectOrFinancePeriodFailsBeforePosting() {
        service.create(rule(SUBJECT_EQUAL, "CONTRACT_AMOUNT", "MONTHLY"));
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "10.00", "CLOSE_GUARD", 83L);
        jdbcTemplate.update("UPDATE pm_project SET status='CLOSED' WHERE id=?", PROJECT_1);

        BusinessException closedProject = assertThrows(BusinessException.class,
                () -> service.executeAllocation(TENANT_ID, PERIOD));
        assertEquals("OVERHEAD_PROJECT_NOT_ACTIVE", closedProject.getCode());
        assertEquals(0, runCount(TENANT_ID));
        assertEquals(0, allocatedCount(TENANT_ID));

        jdbcTemplate.update("UPDATE pm_project SET status='ACTIVE' WHERE id=?", PROJECT_1);
        jdbcTemplate.update("""
                INSERT INTO finance_period
                (id,tenant_id,period_code,fiscal_year,fiscal_month,start_date,end_date,status,issue_count,version)
                VALUES (?,?,?,?,?,?,?,'CLOSED',0,0)
                """, 940024990L, TENANT_ID, YearMonth.from(PERIOD).toString(), PERIOD.getYear(),
                PERIOD.getMonthValue(), PERIOD.withDayOfMonth(1), PERIOD);

        BusinessException closedPeriod = assertThrows(BusinessException.class,
                () -> service.executeAllocation(TENANT_ID, PERIOD));
        assertEquals("FINANCE_PERIOD_CLOSED", closedPeriod.getCode());
        assertEquals(0, runCount(TENANT_ID));
        assertEquals(0, allocatedCount(TENANT_ID));
    }

    @Test
    @DisplayName("DIRECT_LABOR、CONTRACT_AMOUNT 均按权威依据分摊并逐规则守恒")
    void allExistingBasesKeepSemantics() {
        service.create(rule(SUBJECT_EQUAL, "CONTRACT_AMOUNT", "MONTHLY"));
        service.create(rule(SUBJECT_LABOR, "DIRECT_LABOR", "MONTHLY"));
        service.create(rule(SUBJECT_CONTRACT, "CONTRACT_AMOUNT", "MONTHLY"));

        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "1.01", "EQ_SOURCE", 11L);
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_LABOR, "LABOR", "10.00", "LABOR_SOURCE", 12L);
        insertCost(TENANT_ID, PROJECT_2, SUBJECT_LABOR, "LABOR", "30.00", "LABOR_SOURCE", 13L);
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_CONTRACT, "MATERIAL", "100.01", "CT_SOURCE", 14L);

        service.executeAllocation(TENANT_ID, PERIOD);

        assertMoney("1.01", allocatedSum(TENANT_ID, SUBJECT_EQUAL));
        assertMoney("0.25", allocatedForProject(TENANT_ID, SUBJECT_EQUAL, PROJECT_1));
        assertMoney("0.76", allocatedForProject(TENANT_ID, SUBJECT_EQUAL, PROJECT_2));
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
        service.create(rule(SUBJECT_EQUAL, "CONTRACT_AMOUNT", "MONTHLY"));
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "10.00", "TENANT_SOURCE", 21L);

        seedProject(OTHER_PROJECT, OTHER_TENANT_ID, "OH-OTHER", "1000.00");
        insertRuleDirect(OTHER_TENANT_ID, SUBJECT_EQUAL, "CONTRACT_AMOUNT");
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
        service.create(rule(SUBJECT_EQUAL, "CONTRACT_AMOUNT", "MONTHLY"));
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
        service.create(rule(SUBJECT_EQUAL, "CONTRACT_AMOUNT", "MONTHLY"));
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
        service.create(rule(SUBJECT_EQUAL, "CONTRACT_AMOUNT", "MONTHLY"));
        service.create(rule(SUBJECT_LABOR, "DIRECT_LABOR", "MONTHLY"));
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "2.01", "DIMENSION_SOURCE", 61L);
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_LABOR, "LABOR", "4.00", "DIMENSION_SOURCE", 62L);
        insertCost(TENANT_ID, PROJECT_1, SUBJECT_EQUAL, "MATERIAL", "3.01", "DIMENSION_SOURCE", 63L,
                previousPeriod);

        service.executeAllocation(TENANT_ID, PERIOD);
        service.executeAllocation(TENANT_ID, previousPeriod);

        seedProject(OTHER_PROJECT, OTHER_TENANT_ID, "OH-OTHER-DIM", "100.00");
        seedSubject(SUBJECT_OTHER_TENANT, OTHER_TENANT_ID, "5401.04.32", "OVERHEAD", "COST", "ENABLE");
        setUserContext(OTHER_TENANT_ID);
        service.create(rule(SUBJECT_OTHER_TENANT, "CONTRACT_AMOUNT", "MONTHLY"));
        insertCost(OTHER_TENANT_ID, OTHER_PROJECT, SUBJECT_OTHER_TENANT, "MATERIAL", "5.00",
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
        service.create(rule(SUBJECT_EQUAL, "CONTRACT_AMOUNT", "MONTHLY"));
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
        service.create(rule(SUBJECT_EQUAL, "CONTRACT_AMOUNT", "MONTHLY"));
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
        long userId = tenantId == TENANT_ID ? USER_ID : OTHER_USER_ID;
        UserContext.set(Jwts.claims().subject("overhead-test").add("userId", userId)
                .add("username", "overhead-test").add("tenantId", tenantId)
                .add("roleCodes", List.of("COMPANY_FINANCE")).build());
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
        jdbcTemplate.update("DELETE FROM cost_item WHERE tenant_id = ? AND original_cost_item_id IS NOT NULL", tenantId);
        jdbcTemplate.update("DELETE FROM cost_item WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM cost_recalculation_fact_reservation WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM cost_recalculation_line WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM cost_recalculation_batch WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM finance_period_check WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM finance_period WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM overhead_allocation_run WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM overhead_allocation_rule WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM pm_project WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM cost_subject_mapping_version WHERE tenant_id = ? AND version_code='OH-RECALC-V1'", tenantId);
        jdbcTemplate.update("DELETE FROM cost_subject WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ?", tenantId);
    }

    private void seedCompanyFinance(long tenantId, long userId, long roleId) {
        jdbcTemplate.update("""
                INSERT INTO sys_role
                (id,tenant_id,role_code,role_name,role_type,status,data_scope,deleted_flag)
                VALUES (?,?,'COMPANY_FINANCE','公司财务','SYSTEM','ENABLE','ALL',0)
                """, roleId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO sys_user
                (id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag)
                VALUES (?,?,?,'x','间接费测试财务','ENABLE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """, userId, tenantId, "overhead-finance-" + tenantId);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role(id,tenant_id,user_id,role_id)
                VALUES (?,?,?,?)
                """, roleId + 100L, tenantId, userId, roleId);
    }

    private void seedProject(long id, long tenantId, String code, String contractAmount) {
        jdbcTemplate.update("""
                INSERT INTO pm_project
                (id,tenant_id,project_code,project_name,contract_amount,target_cost,status,deleted_flag)
                VALUES (?,?,?,?,?,0,'ACTIVE',0)
                """, id, tenantId, code, code, new BigDecimal(contractAmount));
    }

    private void seedSubject(long id, long tenantId, String code, String type,
                             String category, String status) {
        jdbcTemplate.update("""
                INSERT INTO cost_subject
                (id,tenant_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,deleted_flag)
                VALUES (?,?,?,?,?,?,3,1,?,0)
                """, id, tenantId, code, "测试间接费科目" + id, type, category, status);
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
                (id,tenant_id,project_id,cost_subject_id,classification_status,cost_type,amount,tax_amount,amount_without_tax,
                 source_type,source_id,source_item_id,cost_date,cost_status,generated_flag,deleted_flag)
                VALUES (?,?,?,?,'LEGACY_CLASSIFIED',?,?,0,?,?,?,?,?,'CONFIRMED',1,0)
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

    private BigDecimal projectActualNet(long projectId, long subjectId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM cost_item
                WHERE tenant_id=? AND project_id=? AND cost_subject_id=?
                  AND source_type<>'OVERHEAD_ALLOCATION'
                """, BigDecimal.class, TENANT_ID, projectId, subjectId);
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
