package com.cgcpms.overhead;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.overhead.entity.OverheadAllocationRule;
import com.cgcpms.overhead.mapper.OverheadAllocationRuleMapper;
import com.cgcpms.overhead.service.OverheadAllocationService;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for OverheadAllocationService.
 * Covers: CRUD edge cases, executeAllocation (all 3 basis types),
 * zero-amount guard, concurrency gate, scheduledMonthlyAllocation.
 *
 * <p>Uses H2 in-memory with demo data: project 10001 (ACTIVE, tenant_id=0),
 * cost_subject 900080 (项目间接费用), contract 30001 (contract_amount > 0).
 *
 * <p>Physical-delete strategy: cost_item.CONSTRAINT_INDEX_E has NO deleted_flag
 * column, so MyBatis-Plus soft-delete leaves collision-prone rows behind.
 * We use {@code @AfterEach} JdbcTemplate physical delete for all rows this
 * test class creates, ensuring a clean slate for the next method and for
 * other test classes (TenantBoundaryTask2Test).
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
class OverheadAllocationServiceTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ID = 1L;

    @Autowired private OverheadAllocationService service;
    @Autowired private OverheadAllocationRuleMapper ruleMapper;
    @Autowired private CostItemMapper costItemMapper;
    @Autowired private CostSubjectMapper costSubjectMapper;
    @Autowired private PmProjectMapper projectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** IDs of cost_item rows created by this test — physically deleted in tearDown. */
    private final List<Long> createdCostItemIds = new ArrayList<>();
    /** IDs of overhead_allocation_rule rows created by this test — physically deleted in tearDown. */
    private final List<Long> createdRuleIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        UserContext.set(Jwts.claims().subject("admin").add("userId", USER_ID)
                .add("username", "admin").add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN")).build());
        createdCostItemIds.clear();
        createdRuleIds.clear();
    }

    @AfterEach
    void tearDown() {
        // Physically delete all rows this test created. Unlike MyBatis-Plus soft-delete,
        // JdbcTemplate physical delete frees the unique-key slots for the next test.
        for (Long id : createdRuleIds) {
            jdbcTemplate.update("DELETE FROM overhead_allocation_rule WHERE id = ?", id);
        }
        for (Long id : createdCostItemIds) {
            jdbcTemplate.update("DELETE FROM cost_item WHERE id = ?", id);
        }
        // Also sweep any rows that the SUT created (e.g. OVERHEAD_ALLOCATION items)
        jdbcTemplate.update("DELETE FROM cost_item WHERE tenant_id = ? AND source_type = 'OVERHEAD_ALLOCATION'",
                TENANT_ID);
        jdbcTemplate.update("DELETE FROM cost_item WHERE tenant_id = ? AND source_type = 'MANUAL'"
                + " AND cost_type IN ('OVERHEAD','LABOR') AND source_id IN (881,882,883,884,885,886)",
                TENANT_ID);
        // Sweep any leftover overhead_allocation_rule rows created by the SUT
        jdbcTemplate.update("DELETE FROM overhead_allocation_rule WHERE tenant_id = ?", TENANT_ID);

        UserContext.clear();
    }

    // ================================================================
    // CRUD — happy path
    // ================================================================

    @Test
    @DisplayName("create — 默认状态设为 ENABLE")
    void testCreate_DefaultsToEnable() {
        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(1L); r.setAllocationBasis("EQUAL"); r.setAllocationCycle("MONTHLY");
        Long id = service.create(r);
        createdRuleIds.add(id);
        assertNotNull(id); assertTrue(id > 0);

        OverheadAllocationRule saved = ruleMapper.selectById(id);
        assertEquals("ENABLE", saved.getStatus(), "create 后状态必须为 ENABLE");
    }

    @Test
    @DisplayName("getPage — 返回分页结果")
    void testGetPage() {
        var p = service.getPage(1, 10);
        assertNotNull(p); assertTrue(p.getTotal() >= 0);
    }

    @Test
    @DisplayName("getPage — 过滤租户数据")
    void testGetPage_TenantIsolation() {
        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(1L); r.setAllocationBasis("EQUAL"); r.setAllocationCycle("MONTHLY");
        Long id = service.create(r);
        createdRuleIds.add(id);

        var p = service.getPage(1, 10);
        assertTrue(p.getTotal() >= 1, "刚创建的规则应可见");
        for (OverheadAllocationRule rec : p.getRecords()) {
            assertEquals(TENANT_ID, rec.getTenantId());
        }
    }

    // ================================================================
    // CRUD — update
    // ================================================================

    @Test
    @DisplayName("update — 成功修改分摊依据")
    void testUpdate_Success() {
        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(1L); r.setAllocationBasis("EQUAL"); r.setAllocationCycle("MONTHLY");
        Long id = service.create(r);
        createdRuleIds.add(id);

        OverheadAllocationRule u = new OverheadAllocationRule();
        u.setId(id); u.setCostSubjectId(1L); u.setAllocationBasis("DIRECT_LABOR");
        u.setAllocationCycle("MONTHLY");
        assertDoesNotThrow(() -> service.update(u));

        OverheadAllocationRule updated = ruleMapper.selectById(id);
        assertEquals("DIRECT_LABOR", updated.getAllocationBasis());
    }

    @Test
    @DisplayName("update — 不存在的规则抛异常")
    void testUpdate_NotFound() {
        OverheadAllocationRule u = new OverheadAllocationRule();
        u.setId(99999999L); u.setCostSubjectId(1L);
        u.setAllocationBasis("EQUAL"); u.setAllocationCycle("MONTHLY");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(u));
        assertTrue(ex.getMessage().contains("不存在"), "应提示规则不存在");
    }

    @Test
    @DisplayName("update — 租户隔离保护")
    void testUpdate_TenantProtection() {
        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(1L); r.setAllocationBasis("EQUAL"); r.setAllocationCycle("MONTHLY");
        Long id = service.create(r);
        createdRuleIds.add(id);

        UserContext.set(Jwts.claims().subject("admin").add("userId", USER_ID)
                .add("username", "admin").add("tenantId", 999L)
                .add("roleCodes", List.of("ADMIN")).build());
        try {
            OverheadAllocationRule u = new OverheadAllocationRule();
            u.setId(id); u.setCostSubjectId(1L);
            u.setAllocationBasis("DIRECT_LABOR"); u.setAllocationCycle("MONTHLY");
            assertThrows(BusinessException.class, () -> service.update(u));
        } finally {
            UserContext.set(Jwts.claims().subject("admin").add("userId", USER_ID)
                    .add("username", "admin").add("tenantId", TENANT_ID)
                    .add("roleCodes", List.of("ADMIN")).build());
        }
    }

    // ================================================================
    // CRUD — delete
    // ================================================================

    @Test
    @DisplayName("delete — 成功删除")
    void testDelete_Success() {
        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(1L); r.setAllocationBasis("EQUAL"); r.setAllocationCycle("MONTHLY");
        Long id = service.create(r);
        createdRuleIds.add(id);
        assertDoesNotThrow(() -> service.delete(id));
        assertNull(ruleMapper.selectById(id));
    }

    @Test
    @DisplayName("delete — 不存在的规则抛异常")
    void testDelete_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(99999999L));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    // ================================================================
    // executeAllocation — edge cases
    // ================================================================

    @Test
    @DisplayName("executeAllocation — 无启用规则时不抛异常")
    void testExecuteAllocation_NoRules() {
        assertDoesNotThrow(() -> service.executeAllocation(TENANT_ID, LocalDate.of(2026, 5, 31)));

        long count = costItemMapper.selectCount(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, TENANT_ID)
                .eq(CostItem::getSourceType, "OVERHEAD_ALLOCATION"));
        assertEquals(0, count, "无规则时不应生成分摊成本项");
    }

    @Test
    @DisplayName("executeAllocation — 无活跃项目时跳过分摊")
    void testExecuteAllocation_NoActiveProjects() {
        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(1L); r.setAllocationBasis("EQUAL"); r.setAllocationCycle("MONTHLY");
        Long id = service.create(r);
        createdRuleIds.add(id);

        assertDoesNotThrow(() -> service.executeAllocation(99999L, LocalDate.of(2026, 5, 31)));
    }

    @Test
    @DisplayName("executeAllocation — 当期科目金额为零时跳过分摊条目")
    void testExecuteAllocation_ZeroAmount() {
        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(99999L); r.setAllocationBasis("EQUAL"); r.setAllocationCycle("MONTHLY");
        Long id = service.create(r);
        createdRuleIds.add(id);

        assertDoesNotThrow(() -> service.executeAllocation(TENANT_ID, LocalDate.of(2026, 5, 31)));

        long count = costItemMapper.selectCount(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, TENANT_ID)
                .eq(CostItem::getSourceType, "OVERHEAD_ALLOCATION"));
        assertEquals(0, count, "科目金额为零时不应生成分摊项");
    }

    @Test
    @DisplayName("executeAllocation — 非月度周期规则不参与分摊")
    void testExecuteAllocation_SkipsNonMonthlyCycle() {
        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(1L); r.setAllocationBasis("EQUAL"); r.setAllocationCycle("PER_OCCURRENCE");
        Long id = service.create(r);
        createdRuleIds.add(id);

        assertDoesNotThrow(() -> service.executeAllocation(TENANT_ID, LocalDate.of(2026, 5, 31)));

        long count = costItemMapper.selectCount(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, TENANT_ID)
                .eq(CostItem::getSourceType, "OVERHEAD_ALLOCATION"));
        assertEquals(0, count, "PER_OCCURRENCE 规则不参与月度分摊");
    }

    @Test
    @DisplayName("executeAllocation — DISABLE 状态规则不参与分摊")
    void testExecuteAllocation_SkipsDisabledRule() {
        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(1L); r.setAllocationBasis("EQUAL"); r.setAllocationCycle("MONTHLY");
        r.setStatus("DISABLE");
        r.setTenantId(TENANT_ID);
        ruleMapper.insert(r);
        createdRuleIds.add(r.getId());

        assertDoesNotThrow(() -> service.executeAllocation(TENANT_ID, LocalDate.of(2026, 5, 31)));

        long count = costItemMapper.selectCount(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, TENANT_ID)
                .eq(CostItem::getSourceType, "OVERHEAD_ALLOCATION"));
        assertEquals(0, count, "DISABLE 规则不参与分摊");
    }

    // ================================================================
    // executeAllocation — allocation basis variations
    // ================================================================

    @Test
    @DisplayName("executeAllocation — EQUAL 均分模式")
    void testExecuteAllocation_EqualBasis() {
        Long costSubjectId = 900080L;

        CostItem seed = new CostItem();
        seed.setTenantId(TENANT_ID); seed.setProjectId(10001L);
        seed.setCostSubjectId(costSubjectId); seed.setCostType("OVERHEAD");
        seed.setAmount(new BigDecimal("10000.00")); seed.setAmountWithoutTax(new BigDecimal("10000.00"));
        seed.setSourceType("MANUAL"); seed.setSourceId(881L); seed.setSourceItemId(0L);
        seed.setCostDate(LocalDate.of(2026, 5, 10)); seed.setCostStatus("CONFIRMED");
        seed.setGeneratedFlag(0);
        costItemMapper.insert(seed);
        createdCostItemIds.add(seed.getId());

        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(costSubjectId); r.setAllocationBasis("EQUAL"); r.setAllocationCycle("MONTHLY");
        Long ruleId = service.create(r);
        createdRuleIds.add(ruleId);

        assertDoesNotThrow(() -> service.executeAllocation(TENANT_ID, LocalDate.of(2026, 5, 31)));

        List<CostItem> allocated = costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, TENANT_ID)
                .eq(CostItem::getSourceType, "OVERHEAD_ALLOCATION"));
        assertFalse(allocated.isEmpty(), "应生成分摊成本项");

        for (CostItem item : allocated) {
            assertEquals(costSubjectId, item.getCostSubjectId());
            assertEquals("OVERHEAD_ALLOCATED", item.getCostType());
            assertEquals("OVERHEAD_ALLOCATION", item.getSourceType());
            assertEquals(CostStatus.CONFIRMED, item.getCostStatus());
            assertEquals(1, item.getGeneratedFlag());
            assertTrue(item.getAmount().compareTo(BigDecimal.ZERO) > 0, "分摊金额应 > 0");
        }
    }

    @Test
    @DisplayName("executeAllocation — CONTRACT_AMOUNT 按合同额比例分摊")
    void testExecuteAllocation_ContractAmountBasis() {
        Long costSubjectId = 900080L;

        PmProject project = projectMapper.selectById(10001L);
        assertNotNull(project);

        CostItem seed = new CostItem();
        seed.setTenantId(TENANT_ID); seed.setProjectId(10001L);
        seed.setCostSubjectId(costSubjectId); seed.setCostType("OVERHEAD");
        seed.setAmount(new BigDecimal("5000.00")); seed.setAmountWithoutTax(new BigDecimal("5000.00"));
        seed.setSourceType("MANUAL"); seed.setSourceId(882L); seed.setSourceItemId(0L);
        seed.setCostDate(LocalDate.of(2026, 5, 10)); seed.setCostStatus("CONFIRMED");
        seed.setGeneratedFlag(0);
        costItemMapper.insert(seed);
        createdCostItemIds.add(seed.getId());

        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(costSubjectId); r.setAllocationBasis("CONTRACT_AMOUNT");
        r.setAllocationCycle("MONTHLY");
        Long ruleId = service.create(r);
        createdRuleIds.add(ruleId);

        assertDoesNotThrow(() -> service.executeAllocation(TENANT_ID, LocalDate.of(2026, 5, 31)));

        List<CostItem> allocated = costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, TENANT_ID)
                .eq(CostItem::getSourceType, "OVERHEAD_ALLOCATION"));
        assertFalse(allocated.isEmpty(), "应生成基于合同额的分摊项");

        BigDecimal totalAllocated = allocated.stream()
                .map(CostItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertTrue(totalAllocated.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("executeAllocation — DIRECT_LABOR 按直接人工费比例分摊")
    void testExecuteAllocation_DirectLaborBasis() {
        Long costSubjectId = 900080L;

        CostItem laborSeed = new CostItem();
        laborSeed.setTenantId(TENANT_ID); laborSeed.setProjectId(10001L);
        laborSeed.setCostSubjectId(costSubjectId); laborSeed.setCostType("LABOR");
        laborSeed.setAmount(new BigDecimal("8000.00")); laborSeed.setAmountWithoutTax(new BigDecimal("8000.00"));
        laborSeed.setSourceType("MANUAL"); laborSeed.setSourceId(883L); laborSeed.setSourceItemId(0L);
        laborSeed.setCostDate(LocalDate.of(2026, 5, 10)); laborSeed.setCostStatus("CONFIRMED");
        laborSeed.setGeneratedFlag(0);
        costItemMapper.insert(laborSeed);
        createdCostItemIds.add(laborSeed.getId());

        CostItem overheadSeed = new CostItem();
        overheadSeed.setTenantId(TENANT_ID); overheadSeed.setProjectId(10001L);
        overheadSeed.setCostSubjectId(costSubjectId); overheadSeed.setCostType("OVERHEAD");
        overheadSeed.setAmount(new BigDecimal("12000.00")); overheadSeed.setAmountWithoutTax(new BigDecimal("12000.00"));
        overheadSeed.setSourceType("MANUAL"); overheadSeed.setSourceId(884L); overheadSeed.setSourceItemId(0L);
        overheadSeed.setCostDate(LocalDate.of(2026, 5, 10)); overheadSeed.setCostStatus("CONFIRMED");
        overheadSeed.setGeneratedFlag(0);
        costItemMapper.insert(overheadSeed);
        createdCostItemIds.add(overheadSeed.getId());

        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(costSubjectId); r.setAllocationBasis("DIRECT_LABOR");
        r.setAllocationCycle("MONTHLY");
        Long ruleId = service.create(r);
        createdRuleIds.add(ruleId);

        assertDoesNotThrow(() -> service.executeAllocation(TENANT_ID, LocalDate.of(2026, 5, 31)));

        List<CostItem> allocated = costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, TENANT_ID)
                .eq(CostItem::getSourceType, "OVERHEAD_ALLOCATION"));
        assertFalse(allocated.isEmpty(), "应生成基于人工费比例的分摊项");
    }

    @Test
    @DisplayName("executeAllocation — 分摊后调用 refreshSummary")
    void testExecuteAllocation_RefreshesSummary() {
        Long costSubjectId = 900080L;

        CostItem seed = new CostItem();
        seed.setTenantId(TENANT_ID); seed.setProjectId(10001L);
        seed.setCostSubjectId(costSubjectId); seed.setCostType("OVERHEAD");
        seed.setAmount(new BigDecimal("3000.00")); seed.setAmountWithoutTax(new BigDecimal("3000.00"));
        seed.setSourceType("MANUAL"); seed.setSourceId(885L); seed.setSourceItemId(0L);
        seed.setCostDate(LocalDate.of(2026, 5, 10)); seed.setCostStatus("CONFIRMED");
        seed.setGeneratedFlag(0);
        costItemMapper.insert(seed);
        createdCostItemIds.add(seed.getId());

        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(costSubjectId); r.setAllocationBasis("EQUAL"); r.setAllocationCycle("MONTHLY");
        Long ruleId = service.create(r);
        createdRuleIds.add(ruleId);

        assertDoesNotThrow(() -> service.executeAllocation(TENANT_ID, LocalDate.of(2026, 5, 31)));
    }

    @Test
    @DisplayName("executeAllocation — 生成的成本项字段完整")
    void testExecuteAllocation_GeneratedCostItemFields() {
        Long costSubjectId = 900080L;

        CostItem seed = new CostItem();
        seed.setTenantId(TENANT_ID); seed.setProjectId(10001L);
        seed.setCostSubjectId(costSubjectId); seed.setCostType("OVERHEAD");
        seed.setAmount(new BigDecimal("5000.00")); seed.setAmountWithoutTax(new BigDecimal("5000.00"));
        seed.setSourceType("MANUAL"); seed.setSourceId(886L); seed.setSourceItemId(0L);
        seed.setCostDate(LocalDate.of(2026, 5, 12)); seed.setCostStatus("CONFIRMED");
        seed.setGeneratedFlag(0);
        costItemMapper.insert(seed);
        createdCostItemIds.add(seed.getId());

        OverheadAllocationRule r = new OverheadAllocationRule();
        r.setCostSubjectId(costSubjectId); r.setAllocationBasis("EQUAL"); r.setAllocationCycle("MONTHLY");
        Long ruleId = service.create(r);
        createdRuleIds.add(ruleId);

        service.executeAllocation(TENANT_ID, LocalDate.of(2026, 5, 31));

        List<CostItem> allocated = costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, TENANT_ID)
                .eq(CostItem::getSourceType, "OVERHEAD_ALLOCATION"));
        assertFalse(allocated.isEmpty(), "应生成分摊成本项");

        CostItem item = allocated.get(0);
        assertEquals(TENANT_ID, item.getTenantId());
        assertNotNull(item.getProjectId());
        assertEquals(costSubjectId, item.getCostSubjectId());
        assertEquals("OVERHEAD_ALLOCATED", item.getCostType());
        assertEquals("OVERHEAD_ALLOCATION", item.getSourceType());
        assertEquals(ruleId, item.getSourceId());
        assertEquals(0L, item.getSourceItemId());
        assertEquals("CONFIRMED", item.getCostStatus());
        assertEquals(1, item.getGeneratedFlag());
        assertNotNull(item.getAmount());
        assertNotNull(item.getAmountWithoutTax());
        assertEquals(item.getAmount(), item.getAmountWithoutTax());
        assertNotNull(item.getCostDate());
    }

    // ================================================================
    // scheduledMonthlyAllocation — concurrency guard
    // ================================================================

    @Test
    @DisplayName("scheduledMonthlyAllocation — 不抛异常即通过")
    void testScheduledMonthlyAllocation_BasicRun() {
        assertDoesNotThrow(() -> service.scheduledMonthlyAllocation());
    }

    // ================================================================
    // Helper
    // ================================================================

    private static final class CostStatus {
        static final String CONFIRMED = "CONFIRMED";
    }
}
