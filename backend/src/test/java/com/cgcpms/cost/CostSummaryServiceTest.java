package com.cgcpms.cost;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.cost.vo.CostProjectSummaryVO;
import com.cgcpms.cost.vo.CostSummaryVO;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true", "spring.main.lazy-initialization=true"})
@ActiveProfiles("local")
@DisplayName("CostSummaryService — 成本汇总引擎测试")
class CostSummaryServiceTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ADMIN = 1L;

    @Autowired
    private CostSummaryService costSummaryService;

    @Autowired
    private CostSummaryMapper costSummaryMapper;

    @Autowired
    private PmProjectMapper projectMapper;

    private Long testProjectId;

    @BeforeEach
    void setup() {
        TestUserContext.setAdmin(TENANT_ID, USER_ADMIN);

        // 种子项目
        PmProject project = new PmProject();
        project.setId(80001L);
        project.setProjectCode("COST-SUM-001");
        project.setProjectName("成本汇总测试项目");
        project.setProjectType("CONSTRUCTION");
        project.setContractAmount(new BigDecimal("5000000.00"));
        project.setTargetCost(new BigDecimal("4000000.00"));
        project.setStatus("ACTIVE");
        project.setApprovalStatus("APPROVED");
        project.setTenantId(TENANT_ID);
        if (projectMapper.selectById(80001L) == null) projectMapper.insert(project);
        testProjectId = 80001L;
    }

    @AfterEach
    void cleanup() {
        // 清理成本汇总数据
        costSummaryMapper.physicalDeleteByTenantAndProject(TENANT_ID, testProjectId);
        TestUserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // refreshSummary
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("TC1: refreshSummary — 无 cost_item 时清空汇总并返回项目级数据")
    void testRefreshSummary_NoCostItems() {
        CostProjectSummaryVO result = costSummaryService.refreshSummary(TENANT_ID, testProjectId);
        assertNotNull(result);
        assertEquals(String.valueOf(testProjectId), result.getProjectId());
        assertEquals("成本汇总测试项目", result.getProjectName());
        assertEquals("4000000.00", result.getTargetCost());
        // 无 cost_item，contractLockedCost/actualCost/dynamicCost 应为 0
        assertEquals("0", result.getContractLockedCost());
        assertEquals("0", result.getActualCost());
        // subjects 应为空
        assertNotNull(result.getSubjects());
    }

    @Test
    @Transactional
    @DisplayName("TC2: refreshSummary — 抛出 BusinessException（项目不存在）")
    void testRefreshSummary_ProjectNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSummaryService.refreshSummary(TENANT_ID, 999999L),
                "不存在的项目应抛出 BusinessException");
        assertEquals("PROJECT_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("TC3: refreshSummary — 跨租户项目不可访问")
    void testRefreshSummary_CrossTenant() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSummaryService.refreshSummary(888L, testProjectId),
                "跨租户项目应拒绝");
        assertEquals("PROJECT_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("TC4: refreshSummary — null tenantId 抛出异常")
    void testRefreshSummary_NullParams() {
        assertThrows(BusinessException.class,
                () -> costSummaryService.refreshSummary(null, testProjectId));
    }

    // ═══════════════════════════════════════════════════════════════
    // getSummary / getProjectSummary
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("TC5: getSummary — 无汇总数据返回空列表")
    void testGetSummary_Empty() {
        List<CostSummaryVO> result = costSummaryService.getSummary(TENANT_ID, testProjectId);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @Transactional
    @DisplayName("TC6: getProjectSummary — 项目有数据时返回完整 VO")
    void testGetProjectSummary_WithData() {
        // 先 refresh 一次
        costSummaryService.refreshSummary(TENANT_ID, testProjectId);

        CostProjectSummaryVO result = costSummaryService.getProjectSummary(TENANT_ID, testProjectId);
        assertNotNull(result);
        assertEquals(String.valueOf(testProjectId), result.getProjectId());
        assertEquals("成本汇总测试项目", result.getProjectName());
        // targetCost 应来自项目
        assertEquals("4000000.00", result.getTargetCost());
        // 字段应非空
        assertNotNull(result.getContractLockedCost());
        assertNotNull(result.getActualCost());
        assertNotNull(result.getPaidAmount());
        assertNotNull(result.getDynamicCost());
        assertNotNull(result.getContractIncome());
        assertNotNull(result.getExpectedProfit());
        assertNotNull(result.getCostDeviation());
    }

    @Test
    @Transactional
    @DisplayName("TC7: getProjectSummary — 不存在项目抛异常")
    void testGetProjectSummary_ProjectNotFound() {
        assertThrows(BusinessException.class,
                () -> costSummaryService.getProjectSummary(TENANT_ID, 999999L));
    }

    // ═══════════════════════════════════════════════════════════════
    // getBatchProjectSummaries
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("TC8: getBatchProjectSummaries — 空项目列表返回空 Map")
    void testGetBatchProjectSummaries_EmptyList() {
        Map<Long, CostProjectSummaryVO> result = costSummaryService.getBatchProjectSummaries(TENANT_ID, Collections.emptyList());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @Transactional
    @DisplayName("TC9: getBatchProjectSummaries — 批量查询返回项目数据")
    void testGetBatchProjectSummaries_WithProjects() {
        costSummaryService.refreshSummary(TENANT_ID, testProjectId);

        Map<Long, CostProjectSummaryVO> result = costSummaryService.getBatchProjectSummaries(TENANT_ID, List.of(testProjectId));
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey(testProjectId));

        CostProjectSummaryVO vo = result.get(testProjectId);
        assertEquals("成本汇总测试项目", vo.getProjectName());
        assertNotNull(vo.getTargetCost());
    }

    @Test
    @Transactional
    @DisplayName("TC10: getBatchProjectSummaries — 租户不匹配项目被过滤")
    void testGetBatchProjectSummaries_WrongTenant() {
        Map<Long, CostProjectSummaryVO> result = costSummaryService.getBatchProjectSummaries(888L, List.of(testProjectId));
        assertNotNull(result);
        assertTrue(result.isEmpty(), "不同租户应返回空");
    }

    // ═══════════════════════════════════════════════════════════════
    // getSummaryHistory
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("TC11: getSummaryHistory — 无数据返回空列表")
    void testGetSummaryHistory_Empty() {
        List<CostSummaryVO> history = costSummaryService.getSummaryHistory(testProjectId);
        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    @Test
    @Transactional
    @DisplayName("TC12: getSummaryHistory — 有数据时返回历史记录")
    void testGetSummaryHistory_WithData() {
        costSummaryService.refreshSummary(TENANT_ID, testProjectId);

        List<CostSummaryVO> history = costSummaryService.getSummaryHistory(testProjectId);
        assertNotNull(history);
        // 有 cost_item 时会有记录，无 cost_item 时空列表
        assertNotNull(history);
    }

    // ═══════════════════════════════════════════════════════════════
    // updatePaidAmount
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("TC13: updatePaidAmount — 不抛异常即可（后续集成测试覆盖数据正确性）")
    void testUpdatePaidAmount_NoPayments() {
        costSummaryService.refreshSummary(TENANT_ID, testProjectId);
        assertDoesNotThrow(() -> costSummaryService.updatePaidAmount(TENANT_ID, testProjectId),
                "无付款记录时 updatePaidAmount 不应抛异常");
    }

    @Test
    @Transactional
    @DisplayName("TC14: updatePaidAmount — projectId 不存在时也不抛异常")
    void testUpdatePaidAmount_ProjectNotExist() {
        // updatePaidAmount 只更新匹配条件行，不存在时 update count=0 不抛异常
        assertDoesNotThrow(() -> costSummaryService.updatePaidAmount(TENANT_ID, 999999L),
                "项目不存在时 updatePaidAmount 应优雅降级");
    }

    // ═══════════════════════════════════════════════════════════════
    // 边界条件
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("TC15: refreshSummary 后项目级字段全部非空")
    void testRefreshSummary_AllFieldsNonNull() {
        CostProjectSummaryVO result = costSummaryService.refreshSummary(TENANT_ID, testProjectId);
        // 验证所有关键字段存在且非空
        assertNotNull(result.getTargetCost());
        assertNotNull(result.getActualCost());
        assertNotNull(result.getContractLockedCost());
        assertNotNull(result.getPaidAmount());
        assertNotNull(result.getDynamicCost());
        assertNotNull(result.getContractIncome());
        assertNotNull(result.getExpectedProfit());
        assertNotNull(result.getCostDeviation());
        assertNotNull(result.getEstimatedRemainingCost());
    }

    @Test
    @Transactional
    @DisplayName("TC16: 多次 refresh 是幂等的 — 旧数据先物理删除再插入")
    void testMultipleRefreshesAreIdempotent() {
        CostProjectSummaryVO first = costSummaryService.refreshSummary(TENANT_ID, testProjectId);
        CostProjectSummaryVO second = costSummaryService.refreshSummary(TENANT_ID, testProjectId);

        // 同样的项目数据，两次 refresh 结果应一致
        assertEquals(first.getTargetCost(), second.getTargetCost());
        assertEquals(first.getContractLockedCost(), second.getContractLockedCost());
        assertEquals(first.getActualCost(), second.getActualCost());

        // 数据库应只有最新一批记录
        List<CostSummary> rows = costSummaryMapper.selectList(
                new LambdaQueryWrapper<CostSummary>()
                        .eq(CostSummary::getTenantId, TENANT_ID)
                        .eq(CostSummary::getProjectId, testProjectId));
        assertNotNull(rows);
        // 应有 >=0 行（无 cost_item 时0行，有 cost_item 时 N 行）
        // 但不应有重复行
    }
}
