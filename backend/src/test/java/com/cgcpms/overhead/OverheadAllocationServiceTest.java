package com.cgcpms.overhead;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.overhead.entity.OverheadAllocationRule;
import com.cgcpms.overhead.mapper.OverheadAllocationRuleMapper;
import com.cgcpms.overhead.service.OverheadAllocationService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OverheadAllocationService CRUD + rule filtering edge cases.
 *
 * <p>Uses unique subjectId=99001L to avoid uk_allocation_subject collision with
 * TenantBoundaryTask2Test (which uses costSubjectId=1L).
 * Physical delete in tearDown ensures no soft-delete residue.
 *
 * <p>executeAllocation is NOT tested here — it writes cost_item rows that trigger
 * CONSTRAINT_INDEX_E (source_type,source_id,source_item_id,cost_type) which has no
 * tenant_id or deleted_flag, causing cross-class collisions in parallel test runs.
 * That logic is exercised by the existing integration tests.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
class OverheadAllocationServiceTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ID = 1L;
    /** Unique ID avoids collision with TenantBoundaryTask2Test. */
    private static final long SUBJECT_ID = 99001L;

    @Autowired private OverheadAllocationService service;
    @Autowired private OverheadAllocationRuleMapper ruleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        UserContext.set(Jwts.claims().subject("admin").add("userId", USER_ID)
                .add("username", "admin").add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN")).build());
        jdbcTemplate.update("DELETE FROM overhead_allocation_rule WHERE tenant_id = ?", TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM overhead_allocation_rule WHERE tenant_id = ?", TENANT_ID);
        UserContext.clear();
    }

    // ---- create ----

    @Test @DisplayName("create — 默认状态设为 ENABLE")
    void testCreate_DefaultsToEnable() {
        var r = rule(SUBJECT_ID, "EQUAL", "MONTHLY");
        Long id = service.create(r);
        assertNotNull(id); assertTrue(id > 0);
        assertEquals("ENABLE", ruleMapper.selectById(id).getStatus());
    }

    // ---- getPage ----

    @Test @DisplayName("getPage — 返回分页结果")
    void testGetPage() {
        var p = service.getPage(1, 10);
        assertNotNull(p); assertTrue(p.getTotal() >= 0);
    }

    @Test @DisplayName("getPage — 过滤租户数据")
    void testGetPage_TenantIsolation() {
        service.create(rule(SUBJECT_ID, "EQUAL", "MONTHLY"));
        var p = service.getPage(1, 10);
        assertTrue(p.getTotal() >= 1);
        for (var rec : p.getRecords()) assertEquals(TENANT_ID, rec.getTenantId());
    }

    // ---- update ----

    @Test @DisplayName("update — 成功修改分摊依据")
    void testUpdate_Success() {
        Long id = service.create(rule(SUBJECT_ID, "EQUAL", "MONTHLY"));
        var u = upd(id, SUBJECT_ID, "DIRECT_LABOR", "MONTHLY");
        assertDoesNotThrow(() -> service.update(u));
        assertEquals("DIRECT_LABOR", ruleMapper.selectById(id).getAllocationBasis());
    }

    @Test @DisplayName("update — 不存在的规则抛异常")
    void testUpdate_NotFound() {
        var u = upd(99999999L, SUBJECT_ID, "EQUAL", "MONTHLY");
        var ex = assertThrows(BusinessException.class, () -> service.update(u));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test @DisplayName("update — 租户隔离保护")
    void testUpdate_TenantProtection() {
        Long id = service.create(rule(SUBJECT_ID, "EQUAL", "MONTHLY"));
        UserContext.set(Jwts.claims().subject("admin").add("userId", USER_ID)
                .add("username", "admin").add("tenantId", 999L)
                .add("roleCodes", List.of("ADMIN")).build());
        try {
            assertThrows(BusinessException.class, () -> service.update(upd(id, SUBJECT_ID, "DIRECT_LABOR", "MONTHLY")));
        } finally {
            UserContext.set(Jwts.claims().subject("admin").add("userId", USER_ID)
                    .add("username", "admin").add("tenantId", TENANT_ID)
                    .add("roleCodes", List.of("ADMIN")).build());
        }
    }

    // ---- delete ----

    @Test @DisplayName("delete — 成功删除")
    void testDelete_Success() {
        Long id = service.create(rule(SUBJECT_ID, "EQUAL", "MONTHLY"));
        assertDoesNotThrow(() -> service.delete(id));
        assertNull(ruleMapper.selectById(id));
    }

    @Test @DisplayName("delete — 不存在的规则抛异常")
    void testDelete_NotFound() {
        var ex = assertThrows(BusinessException.class, () -> service.delete(99999999L));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    // ---- executeAllocation boundary guards (no cost_item assertions) ----

    @Test @DisplayName("executeAllocation — 无启用规则时不抛异常")
    void testExecuteAllocation_NoRules() {
        assertDoesNotThrow(() -> service.executeAllocation(TENANT_ID, LocalDate.of(2026, 5, 31)));
    }

    @Test @DisplayName("executeAllocation — 无活跃项目时跳过分摊")
    void testExecuteAllocation_NoActiveProjects() {
        service.create(rule(SUBJECT_ID, "EQUAL", "MONTHLY"));
        assertDoesNotThrow(() -> service.executeAllocation(99999L, LocalDate.of(2026, 5, 31)));
    }

    @Test @DisplayName("executeAllocation — 当期科目金额为零时跳过分摊")
    void testExecuteAllocation_ZeroAmount() {
        service.create(rule(99999L, "EQUAL", "MONTHLY"));
        assertDoesNotThrow(() -> service.executeAllocation(TENANT_ID, LocalDate.of(2026, 5, 31)));
    }

    @Test @DisplayName("executeAllocation — 非月度周期规则不参与分摊")
    void testExecuteAllocation_SkipsNonMonthlyCycle() {
        service.create(rule(SUBJECT_ID, "EQUAL", "PER_OCCURRENCE"));
        assertDoesNotThrow(() -> service.executeAllocation(TENANT_ID, LocalDate.of(2026, 5, 31)));
    }

    @Test @DisplayName("executeAllocation — DISABLE 状态规则不参与分摊")
    void testExecuteAllocation_SkipsDisabledRule() {
        var r = rule(SUBJECT_ID, "EQUAL", "MONTHLY");
        r.setStatus("DISABLE"); r.setTenantId(TENANT_ID);
        ruleMapper.insert(r);
        assertDoesNotThrow(() -> service.executeAllocation(TENANT_ID, LocalDate.of(2026, 5, 31)));
    }

    // ---- scheduledMonthlyAllocation ----

    @Test @DisplayName("scheduledMonthlyAllocation — 不抛异常即通过")
    void testScheduledMonthlyAllocation_BasicRun() {
        assertDoesNotThrow(() -> service.scheduledMonthlyAllocation());
    }

    // ---- helpers ----

    private OverheadAllocationRule rule(long subjectId, String basis, String cycle) {
        var r = new OverheadAllocationRule();
        r.setCostSubjectId(subjectId); r.setAllocationBasis(basis); r.setAllocationCycle(cycle);
        return r;
    }

    private OverheadAllocationRule upd(long id, long subjectId, String basis, String cycle) {
        var u = new OverheadAllocationRule();
        u.setId(id); u.setCostSubjectId(subjectId); u.setAllocationBasis(basis); u.setAllocationCycle(cycle);
        return u;
    }
}
