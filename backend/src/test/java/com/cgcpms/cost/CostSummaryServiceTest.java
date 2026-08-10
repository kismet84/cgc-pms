package com.cgcpms.cost;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.lazy-initialization=true"})
@ActiveProfiles("local")
@DisplayName("CostSummaryService — 成本汇总引擎测试")
class CostSummaryServiceTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ADMIN = 1L;
    private static final long USER_PROJECT_MANAGER = 91001L;
    private static final long USER_PROJECT_CREATOR = 91002L;

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

    @Autowired
    private CtContractMapper contractMapper;

    @Autowired
    private JdbcTemplate jdbc;

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
        project.setProjectManagerId(USER_PROJECT_MANAGER);
        project.setCreatedBy(USER_PROJECT_CREATOR);
        if (projectMapper.selectById(80001L) == null) {
            projectMapper.insert(project);
        } else {
            projectMapper.updateById(project);
        }
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
        costItemMapper.deleteById(80005L);
        costItemMapper.deleteById(80006L);
        costSummaryMapper.physicalDeleteByTenantAndProject(TENANT_ID, 80005L);
        jdbc.update("DELETE FROM cost_forecast_item WHERE tenant_id=? AND project_id=?", TENANT_ID, 80005L);
        jdbc.update("DELETE FROM cost_forecast WHERE tenant_id=? AND project_id=?", TENANT_ID, 80005L);
        jdbc.update("DELETE FROM cost_target WHERE tenant_id=? AND project_id=?", TENANT_ID, 80005L);
        costSubjectMapper.deleteById(80001L);
        costSubjectMapper.deleteById(80002L);
        costSubjectMapper.deleteById(80003L);
        costSubjectMapper.deleteById(80004L);
        costSubjectMapper.deleteById(80005L);
        payRecordMapper.deleteById(80001L);
        payRecordMapper.deleteById(80002L);
        payRecordMapper.deleteById(80003L);
        payApplicationMapper.deleteById(80001L);
        payApplicationMapper.deleteById(80003L);
        contractMapper.deleteById(80005L);
        projectMapper.deleteById(80005L);
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

    @Test
    @Transactional
    @DisplayName("TC4-1: refreshSummary — 项目经理可刷新本人项目")
    void testRefreshSummary_ProjectManagerAllowed() {
        TestUserContext.setUser(TENANT_ID, USER_PROJECT_MANAGER, "project_manager", List.of());

        CostProjectSummaryVO result = costSummaryService.refreshSummary(testProjectId);

        assertNotNull(result);
        assertEquals(String.valueOf(testProjectId), result.getProjectId());
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
        PayApplication app = new PayApplication();
        app.setId(80003L);
        app.setProjectId(testProjectId);
        app.setApplyCode("PAY-APP-TC9");
        app.setPayType("进度款");
        app.setApplyAmount(new BigDecimal("123.00"));
        app.setPayStatus("APPROVED");
        app.setApprovalStatus("APPROVED");
        app.setTenantId(TENANT_ID);
        payApplicationMapper.insert(app);
        PayRecord record = new PayRecord();
        record.setId(80003L);
        record.setProjectId(testProjectId);
        record.setPayApplicationId(80003L);
        record.setPayAmount(new BigDecimal("123.00"));
        record.setPayDate(LocalDate.now());
        record.setPayStatus("SUCCESS");
        record.setTenantId(TENANT_ID);
        payRecordMapper.insert(record);
        costSummaryService.refreshSummary(TENANT_ID, testProjectId);

        Map<Long, CostProjectSummaryVO> result = costSummaryService.getBatchProjectSummaries(TENANT_ID, List.of(testProjectId));
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey(testProjectId));

        CostProjectSummaryVO vo = result.get(testProjectId);
        assertEquals("成本汇总测试项目", vo.getProjectName());
        assertNotNull(vo.getTargetCost());
        assertEquals("123.00", vo.getPaidAmount());
    }

    @Test
    @Transactional
    @DisplayName("TC10: getBatchProjectSummaries — 租户不匹配项目被过滤")
    void testGetBatchProjectSummaries_WrongTenant() {
        Map<Long, CostProjectSummaryVO> result = costSummaryService.getBatchProjectSummaries(888L, List.of(testProjectId));
        assertNotNull(result);
        assertTrue(result.isEmpty(), "不同租户应返回空");
    }

    @Test
    @Transactional
    @DisplayName("TC10-1: 全部项目摘要缺少租户上下文时 fail-close")
    void testGetAccessibleProjectSummaries_TenantRequired() {
        TestUserContext.clear();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> costSummaryService.getAccessibleProjectSummaries());

        assertEquals("TENANT_CONTEXT_REQUIRED", ex.getCode());
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

    @Test
    @Transactional
    @DisplayName("TC12-1: getSummaryHistory — 创建人 SELF 数据范围可查看历史")
    void testGetSummaryHistory_CreatorAllowed() {
        TestUserContext.setUser(TENANT_ID, USER_PROJECT_CREATOR, "project_creator", List.of());

        List<CostSummaryVO> history = costSummaryService.getSummaryHistory(testProjectId);

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
    @DisplayName("TC13-1: updatePaidAmount 只更新今日快照，历史不可变")
    void testUpdatePaidAmountOnlyTouchesCurrentSnapshot() {
        CostSummary yesterday = summaryRow(8000101L, LocalDate.now().minusDays(1), "7.00");
        CostSummary today = summaryRow(8000102L, LocalDate.now(), "8.00");
        costSummaryMapper.insert(yesterday);
        costSummaryMapper.insert(today);

        PayApplication app = new PayApplication();
        app.setId(80001L); app.setTenantId(TENANT_ID); app.setProjectId(testProjectId);
        app.setApplyCode("PAY-HISTORY-IMMUTABLE"); app.setPayType("进度款");
        app.setApplyAmount(new BigDecimal("123.45")); app.setPayStatus("APPROVED"); app.setApprovalStatus("APPROVED");
        payApplicationMapper.insert(app);
        PayRecord record = new PayRecord();
        record.setId(80001L); record.setTenantId(TENANT_ID); record.setProjectId(testProjectId);
        record.setPayApplicationId(80001L); record.setPayAmount(new BigDecimal("123.45"));
        record.setPayDate(LocalDate.now()); record.setPayStatus("SUCCESS");
        payRecordMapper.insert(record);

        costSummaryService.updatePaidAmount(TENANT_ID, testProjectId);

        assertEquals(0, new BigDecimal("7.00").compareTo(costSummaryMapper.selectById(8000101L).getPaidAmount()));
        assertEquals(0, new BigDecimal("123.45").compareTo(costSummaryMapper.selectById(8000102L).getPaidAmount()));
    }

    @Test
    @Transactional
    @DisplayName("TC14: updatePaidAmount — projectId 不存在时也不抛异常")
    void testUpdatePaidAmount_ProjectNotExist() {
        // updatePaidAmount 只更新匹配条件行，不存在时 update count=0 不抛异常
        assertDoesNotThrow(() -> costSummaryService.updatePaidAmount(TENANT_ID, 999999L),
                "项目不存在时 updatePaidAmount 应优雅降级");
    }

    private CostSummary summaryRow(long id, LocalDate date, String paid) {
        CostSummary row = new CostSummary();
        row.setId(id); row.setTenantId(TENANT_ID); row.setProjectId(testProjectId); row.setSummaryDate(date);
        row.setTargetCost(BigDecimal.ZERO); row.setContractLockedCost(BigDecimal.ZERO); row.setActualCost(BigDecimal.ZERO);
        row.setPaidAmount(new BigDecimal(paid)); row.setEstimatedRemainingCost(BigDecimal.ZERO); row.setDynamicCost(BigDecimal.ZERO);
        row.setContractIncome(BigDecimal.ZERO); row.setConfirmedRevenue(BigDecimal.ZERO); row.setExpectedProfit(BigDecimal.ZERO);
        row.setCostDeviation(BigDecimal.ZERO); row.setResponsibilityCost(BigDecimal.ZERO);
        row.setForecastAtCompletionCost(BigDecimal.ZERO); row.setForecastProfit(BigDecimal.ZERO); row.setProfitMargin(BigDecimal.ZERO);
        return row;
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
        app.setCostSubjectId(80001L);
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

        PayApplication legacyApp = new PayApplication(); legacyApp.setId(80003L); legacyApp.setProjectId(testProjectId);
        legacyApp.setApplyCode("PAY-APP-TC18-LEGACY"); legacyApp.setPayType("历史付款");
        legacyApp.setApplyAmount(new BigDecimal("5000.00")); legacyApp.setPayStatus("APPROVED");
        legacyApp.setApprovalStatus("APPROVED"); legacyApp.setTenantId(TENANT_ID);
        payApplicationMapper.insert(legacyApp);
        PayRecord legacyPayment = new PayRecord(); legacyPayment.setId(80003L); legacyPayment.setProjectId(testProjectId);
        legacyPayment.setPayApplicationId(80003L); legacyPayment.setPayAmount(new BigDecimal("5000.00"));
        legacyPayment.setPayDate(LocalDate.now()); legacyPayment.setPayStatus("SUCCESS"); legacyPayment.setTenantId(TENANT_ID);
        payRecordMapper.insert(legacyPayment);

        // 3. refresh summary
        CostProjectSummaryVO vo = costSummaryService.refreshSummary(TENANT_ID, testProjectId);
        assertNotNull(vo);

        // 4. 关键断言：getProjectSummary 的 paidAmount 应为 25000 (项目级汇总),
        //    不是 25000 * 2科目 = 50000
        CostProjectSummaryVO result = costSummaryService.getProjectSummary(TENANT_ID, testProjectId);
        BigDecimal paidAmount = new BigDecimal(result.getPaidAmount());
        assertEquals(0, new BigDecimal("30000.00").compareTo(paidAmount),
                "项目级 paidAmount 应为 30000 (不随科目数倍增), 实际: " + paidAmount.toPlainString());

        // 5. 科目金额按付款申请绑定的成本科目归集，不能复制项目总额
        List<CostSummaryVO> subjects = result.getSubjects();
        assertNotNull(subjects);
        assertEquals("25000.00", subjects.stream().filter(s -> "材料费".equals(s.getCostSubjectName()))
                .findFirst().orElseThrow().getPaidAmount());
        assertEquals(0, new BigDecimal(subjects.stream().filter(s -> "人工费".equals(s.getCostSubjectName()))
                .findFirst().orElseThrow().getPaidAmount()).compareTo(BigDecimal.ZERO));
        assertEquals("5000.00", subjects.stream().filter(s -> "未归属".equals(s.getCostSubjectName()))
                .findFirst().orElseThrow().getPaidAmount());
        assertEquals(0, paidAmount.compareTo(subjects.stream().map(CostSummaryVO::getPaidAmount)
                .map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    @Test
    @Transactional
    @DisplayName("TC18-1: refreshSummary 固定回归目标成本、实际成本、动态成本和偏差金额")
    void testRefreshSummaryDynamicCostReportAmounts() {
        Long reportProjectId = 80005L;

        PmProject project = new PmProject();
        project.setId(reportProjectId);
        project.setProjectCode("COST-SUM-REPORT");
        project.setProjectName("成本动态汇总报表项目");
        project.setProjectType("CONSTRUCTION");
        project.setContractAmount(new BigDecimal("5000000.00"));
        project.setTargetCost(new BigDecimal("4000000.00"));
        project.setStatus("ACTIVE");
        project.setApprovalStatus("APPROVED");
        project.setTenantId(TENANT_ID);
        project.setProjectManagerId(USER_PROJECT_MANAGER);
        project.setCreatedBy(USER_PROJECT_CREATOR);
        projectMapper.insert(project);

        CtContract contract = new CtContract();
        contract.setId(80005L);
        contract.setTenantId(TENANT_ID);
        contract.setProjectId(reportProjectId);
        contract.setContractCode("CT-COST-SUM-REPORT");
        contract.setContractName("成本动态汇总报表合同");
        contract.setContractType("MAIN");
        contract.setContractAmount(new BigDecimal("5000000.00"));
        contract.setCurrentAmount(new BigDecimal("5000000.00"));
        contract.setContractStatus("PERFORMING");
        contractMapper.insert(contract);

        CostSubject subject = new CostSubject();
        subject.setId(80004L);
        subject.setSubjectName("报表回归科目");
        subject.setSubjectCode("REPORT");
        subject.setTenantId(TENANT_ID);
        costSubjectMapper.insert(subject);

        CostSubject forecastOnlySubject = new CostSubject();
        forecastOnlySubject.setId(80005L);
        forecastOnlySubject.setSubjectName("仅预测科目");
        forecastOnlySubject.setSubjectCode("FORECAST_ONLY");
        forecastOnlySubject.setTenantId(TENANT_ID);
        costSubjectMapper.insert(forecastOnlySubject);

        jdbc.update("INSERT INTO cost_target(id,tenant_id,project_id,version_no,total_target_amount,is_active,approval_status,status) VALUES(?,?,?,'FORECAST-BASE',0,0,'APPROVED','ACTIVE')",
                80005L, TENANT_ID, reportProjectId);
        jdbc.update("INSERT INTO cost_forecast(id,tenant_id,project_id,cost_target_id,forecast_code,forecast_name,version_no,forecast_date,bid_cost_amount,target_cost_amount,responsibility_amount,committed_cost_amount,actual_cost_amount,estimated_remaining_amount,forecast_at_completion_amount,contract_income_amount,forecast_profit_amount,cost_variance_amount,profit_margin,status) VALUES(?,?,?,?,'FC-COST-SUM-REPORT','科目ETC回归',1,CURRENT_DATE,0,4000000,0,0,200000,300000,500000,5000000,4500000,0,0.9,'CONTROLLED')",
                80005L, TENANT_ID, reportProjectId, 80005L);
        jdbc.update("INSERT INTO cost_forecast_item(id,tenant_id,forecast_id,project_id,cost_subject_id,bid_cost_amount,target_cost_amount,responsibility_amount,committed_cost_amount,actual_cost_amount,estimated_remaining_amount,forecast_at_completion_amount,cost_variance_amount) VALUES(?,?,?,?,?,0,0,0,0,200000,100000,300000,0)",
                80005L, TENANT_ID, 80005L, reportProjectId, 80004L);
        jdbc.update("INSERT INTO cost_forecast_item(id,tenant_id,forecast_id,project_id,cost_subject_id,bid_cost_amount,target_cost_amount,responsibility_amount,committed_cost_amount,actual_cost_amount,estimated_remaining_amount,forecast_at_completion_amount,cost_variance_amount) VALUES(?,?,?,?,?,0,0,0,0,0,200000,200000,0)",
                80006L, TENANT_ID, 80005L, reportProjectId, 80005L);

        CostItem materialCost = new CostItem();
        materialCost.setId(80005L);
        materialCost.setTenantId(TENANT_ID);
        materialCost.setProjectId(reportProjectId);
        materialCost.setCostSubjectId(80004L);
        materialCost.setSourceType("MAT_RECEIPT");
        materialCost.setSourceId(80005L);
        materialCost.setCostType("MATERIAL_COST");
        materialCost.setCostStatus("CONFIRMED");
        materialCost.setCostDate(java.time.LocalDate.now());
        materialCost.setAmount(new BigDecimal("125000.00"));
        costItemMapper.insert(materialCost);

        CostItem changeCost = new CostItem();
        changeCost.setId(80006L);
        changeCost.setTenantId(TENANT_ID);
        changeCost.setProjectId(reportProjectId);
        changeCost.setCostSubjectId(80004L);
        changeCost.setSourceType("CT_CHANGE");
        changeCost.setSourceId(80006L);
        changeCost.setCostType("CHANGE");
        changeCost.setCostStatus("CONFIRMED");
        changeCost.setCostDate(java.time.LocalDate.now());
        changeCost.setAmount(new BigDecimal("75000.00"));
        costItemMapper.insert(changeCost);

        CostProjectSummaryVO result = costSummaryService.refreshSummary(TENANT_ID, reportProjectId);

        assertEquals(0, new BigDecimal("4000000.00").compareTo(new BigDecimal(result.getTargetCost())));
        assertEquals(0, new BigDecimal("200000.00").compareTo(new BigDecimal(result.getActualCost())));
        assertEquals(0, new BigDecimal("200000.00").compareTo(new BigDecimal(result.getDynamicCost())));
        assertEquals(0, new BigDecimal("-3800000.00").compareTo(new BigDecimal(result.getCostDeviation())));

        CostSummaryVO subjectSummary = result.getSubjects().stream()
                .filter(s -> "报表回归科目".equals(s.getCostSubjectName()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("4000000.00").compareTo(new BigDecimal(subjectSummary.getTargetCost())));
        assertEquals(0, new BigDecimal("200000.00").compareTo(new BigDecimal(subjectSummary.getActualCost())));
        assertEquals(0, new BigDecimal("100000.00").compareTo(new BigDecimal(subjectSummary.getEstimatedRemainingCost())));
        assertEquals(0, new BigDecimal("300000.00").compareTo(new BigDecimal(subjectSummary.getDynamicCost())));
        assertEquals(0, BigDecimal.ZERO.compareTo(new BigDecimal(subjectSummary.getContractIncome())),
                "项目合同收入只能出现在项目汇总，不能复制到科目行");
        assertEquals(0, BigDecimal.ZERO.compareTo(new BigDecimal(subjectSummary.getConfirmedRevenue())),
                "项目确认收入只能出现在项目汇总，不能复制到科目行");
        assertEquals(0, BigDecimal.ZERO.compareTo(new BigDecimal(subjectSummary.getExpectedProfit())),
                "项目利润只能出现在项目汇总，不能复制到科目行");
        assertEquals(0, BigDecimal.ZERO.compareTo(new BigDecimal(subjectSummary.getForecastProfit())),
                "项目预测利润只能出现在项目汇总，不能复制到科目行");
        assertEquals(0, new BigDecimal("-3700000.00").compareTo(new BigDecimal(subjectSummary.getCostDeviation())));
        CostSummaryVO forecastOnlySummary = result.getSubjects().stream()
                .filter(s -> "仅预测科目".equals(s.getCostSubjectName()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("200000.00").compareTo(new BigDecimal(forecastOnlySummary.getEstimatedRemainingCost())));
        assertEquals(0, new BigDecimal("200000.00").compareTo(new BigDecimal(forecastOnlySummary.getDynamicCost())));
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
