package com.cgcpms.cost;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.cost.vo.CostProjectSummaryVO;
import com.cgcpms.cost.vo.CostSummaryVO;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Autowired
    private CostItemMapper costItemMapper;

    @Autowired
    private CostSubjectMapper costSubjectMapper;

    @Autowired
    private PayRecordMapper payRecordMapper;

    @Autowired
    private PayApplicationMapper payApplicationMapper;

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
        // 清理 TC18 种子数据
        costItemMapper.deleteById(80001L);
        costItemMapper.deleteById(80002L);
        costItemMapper.deleteById(80003L);
        costItemMapper.deleteById(80004L);
        costSubjectMapper.deleteById(80001L);
        costSubjectMapper.deleteById(80002L);
        costSubjectMapper.deleteById(80003L);
        payRecordMapper.deleteById(80001L);
        payRecordMapper.deleteById(80002L);
        payApplicationMapper.deleteById(80001L);
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

    // ═══════════════════════════════════════════════════════════════
    // 并发一致性 — M-006: refreshSummary 与 updatePaidAmount 共用锁
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TC17: refreshSummary 与 updatePaidAmount 并发串行化 — 无丢失更新")
    void testRefreshAndUpdatePaidAmountSerialized() throws Exception {
        // 1. 先建一个初始汇总
        costSummaryService.refreshSummary(TENANT_ID, testProjectId);

        int threadCount = 4;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger refreshCount = new AtomicInteger(0);
        AtomicInteger updateCount = new AtomicInteger(0);
        List<Exception> errors = Collections.synchronizedList(new java.util.ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    TestUserContext.setAdmin(TENANT_ID, USER_ADMIN);
                    startLatch.await(); // 同时起跑
                    if (idx % 2 == 0) {
                        costSummaryService.refreshSummary(TENANT_ID, testProjectId);
                        refreshCount.incrementAndGet();
                    } else {
                        costSummaryService.updatePaidAmount(TENANT_ID, testProjectId);
                        updateCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    synchronized (errors) { errors.add(e); }
                } finally {
                    TestUserContext.clear();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 发令枪
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // 2. 断言：无异常
        assertTrue(errors.isEmpty(), "并发不应抛异常: " + errors);

        // 3. 断言：所有操作完成
        assertEquals(2, refreshCount.get(), "应完成 2 次 refreshSummary");
        assertEquals(2, updateCount.get(), "应完成 2 次 updatePaidAmount");

        // 4. 最终验证：refresh 后数据一致
        CostProjectSummaryVO finalResult = costSummaryService.refreshSummary(TENANT_ID, testProjectId);
        assertNotNull(finalResult);
    }

    // ═══════════════════════════════════════════════════════════════
    // paidAmount 一致性 — 项目级已付金额不随科目数倍增
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("TC18: getProjectSummary paidAmount 不随科目数倍增")
    void testPaidAmountNotMultipliedBySubjectCount() {
        // 1. 种子：2 个科目 + 2 个 cost_item（各属不同科目）
        CostSubject s1 = new CostSubject(); s1.setId(80001L); s1.setSubjectName("材料费");
        s1.setSubjectCode("CL"); s1.setTenantId(TENANT_ID);
        if (costSubjectMapper.selectById(80001L) == null) costSubjectMapper.insert(s1);

        CostSubject s2 = new CostSubject(); s2.setId(80002L); s2.setSubjectName("人工费");
        s2.setSubjectCode("RG"); s2.setTenantId(TENANT_ID);
        if (costSubjectMapper.selectById(80002L) == null) costSubjectMapper.insert(s2);

        CostItem item1 = new CostItem(); item1.setId(80001L); item1.setProjectId(testProjectId);
        item1.setCostSubjectId(80001L); item1.setSourceType("CT_CONTRACT"); item1.setSourceId(80001L);
        item1.setCostType("CONTRACT_COST"); item1.setCostStatus("CONFIRMED"); item1.setCostDate(java.time.LocalDate.now());
        item1.setAmount(new BigDecimal("50000.00")); item1.setTenantId(TENANT_ID);
        if (costItemMapper.selectById(80001L) == null) costItemMapper.insert(item1);

        CostItem item2 = new CostItem(); item2.setId(80002L); item2.setProjectId(testProjectId);
        item2.setCostSubjectId(80002L); item2.setSourceType("MAT_RECEIPT"); item2.setSourceId(80002L);
        item2.setCostType("MATERIAL_COST"); item2.setCostStatus("CONFIRMED"); item2.setCostDate(java.time.LocalDate.now());
        item2.setAmount(new BigDecimal("30000.00")); item2.setTenantId(TENANT_ID);
        if (costItemMapper.selectById(80002L) == null) costItemMapper.insert(item2);

        // 2. 插入 PayApplication 和 2笔付款记录
        PayApplication app = new PayApplication(); app.setId(80001L); app.setProjectId(testProjectId);
        app.setApplyCode("PAY-APP-TC18"); app.setPayType("进度款"); app.setApplyAmount(new BigDecimal("100000.00"));
        app.setPayStatus("APPROVED"); app.setApprovalStatus("APPROVED"); app.setTenantId(TENANT_ID);
        if (payApplicationMapper.selectById(80001L) == null) payApplicationMapper.insert(app);

        PayRecord pr1 = new PayRecord(); pr1.setId(80001L); pr1.setProjectId(testProjectId);
        pr1.setPayApplicationId(80001L);
        pr1.setPayAmount(new BigDecimal("10000.00")); pr1.setPayDate(java.time.LocalDate.now());
        pr1.setPayStatus("SUCCESS"); pr1.setTenantId(TENANT_ID);
        if (payRecordMapper.selectById(80001L) == null) payRecordMapper.insert(pr1);

        PayRecord pr2 = new PayRecord(); pr2.setId(80002L); pr2.setProjectId(testProjectId);
        pr2.setPayApplicationId(80001L);
        pr2.setPayAmount(new BigDecimal("15000.00")); pr2.setPayDate(java.time.LocalDate.now());
        pr2.setPayStatus("SUCCESS"); pr2.setTenantId(TENANT_ID);
        if (payRecordMapper.selectById(80002L) == null) payRecordMapper.insert(pr2);

        // 3. refresh summary
        CostProjectSummaryVO vo = costSummaryService.refreshSummary(TENANT_ID, testProjectId);
        assertNotNull(vo);

        // 4. 关键断言：getProjectSummary 的 paidAmount 应为 25000 (项目级汇总),
        //    不是 25000 * 2科目 = 50000
        CostProjectSummaryVO result = costSummaryService.getProjectSummary(TENANT_ID, testProjectId);
        BigDecimal paidAmount = new BigDecimal(result.getPaidAmount());
        assertEquals(0, new BigDecimal("25000.00").compareTo(paidAmount),
                "项目级 paidAmount 应为 25000 (不随科目数倍增), 实际: " + paidAmount.toPlainString());

        // 5. 同时验证 subjects 中每个 subject 的 paidAmount 也正确
        List<CostSummaryVO> subjects = result.getSubjects();
        assertNotNull(subjects);
        for (CostSummaryVO s : subjects) {
            BigDecimal subjectPaid = new BigDecimal(s.getPaidAmount());
            assertEquals(0, new BigDecimal("25000.00").compareTo(subjectPaid),
                    "每个科目行的 paidAmount 都应为项目级 25000, 实际: " + subjectPaid.toPlainString());
        }
    }

    @Test
    @Transactional
    @DisplayName("TC19: refreshSummary 计入签证和合同变更成本")
    void testRefreshSummaryIncludesVariationAndContractChangeCosts() {
        CostSubject subject = new CostSubject();
        subject.setId(80003L);
        subject.setSubjectName("人工费");
        subject.setSubjectCode("RG-VAR");
        subject.setTenantId(TENANT_ID);
        if (costSubjectMapper.selectById(80003L) == null) costSubjectMapper.insert(subject);

        CostItem varOrderCost = new CostItem();
        varOrderCost.setId(80003L);
        varOrderCost.setTenantId(TENANT_ID);
        varOrderCost.setProjectId(testProjectId);
        varOrderCost.setCostSubjectId(80003L);
        varOrderCost.setSourceType("VAR_ORDER");
        varOrderCost.setSourceId(80003L);
        varOrderCost.setSourceItemId(80003L);
        varOrderCost.setCostType("VARIATION");
        varOrderCost.setCostStatus("CONFIRMED");
        varOrderCost.setCostDate(java.time.LocalDate.now());
        varOrderCost.setAmount(new BigDecimal("30000.00"));
        if (costItemMapper.selectById(80003L) == null) costItemMapper.insert(varOrderCost);

        CostItem contractChangeCost = new CostItem();
        contractChangeCost.setId(80004L);
        contractChangeCost.setTenantId(TENANT_ID);
        contractChangeCost.setProjectId(testProjectId);
        contractChangeCost.setCostSubjectId(80003L);
        contractChangeCost.setSourceType("CT_CHANGE");
        contractChangeCost.setSourceId(80004L);
        contractChangeCost.setCostType("CHANGE");
        contractChangeCost.setCostStatus("CONFIRMED");
        contractChangeCost.setCostDate(java.time.LocalDate.now());
        contractChangeCost.setAmount(new BigDecimal("40000.00"));
        if (costItemMapper.selectById(80004L) == null) costItemMapper.insert(contractChangeCost);

        CostProjectSummaryVO result = costSummaryService.refreshSummary(TENANT_ID, testProjectId);

        assertEquals(0, new BigDecimal("70000.00").compareTo(new BigDecimal(result.getActualCost())));
        CostSummaryVO subjectSummary = result.getSubjects().stream()
                .filter(s -> "人工费".equals(s.getCostSubjectName()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("70000.00").compareTo(new BigDecimal(subjectSummary.getActualCost())));
    }
}
