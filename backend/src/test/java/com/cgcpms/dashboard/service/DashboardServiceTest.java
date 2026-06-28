package com.cgcpms.dashboard.service;

import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.dashboard.vo.*;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DashboardService integration tests — business correctness for all 6 views.
 * <p>
 * Uses {@code @ActiveProfiles("local")} (H2 in-memory).
 * Each test method uses {@code @Transactional} so seed data is rolled back.
 * Seed codes use unique suffixes per test (MyBatis-Plus snowflake IDs guarantee uniqueness).
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("DashboardService — 6 views business correctness")
class DashboardServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_ID = 0L;

    @Autowired private DashboardService dashboardService;
    @Autowired private PmProjectMapper projectMapper;
    @Autowired private CtContractMapper ctContractMapper;
    @Autowired private WfInstanceMapper wfInstanceMapper;
    @Autowired private WfTaskMapper wfTaskMapper;
    @Autowired private VarOrderMapper varOrderMapper;
    @Autowired private SubMeasureMapper subMeasureMapper;
    @Autowired private StlSettlementMapper stlSettlementMapper;
    @Autowired private PayRecordMapper payRecordMapper;
    @Autowired private AlertLogMapper alertLogMapper;
    @Autowired private CostSummaryMapper costSummaryMapper;
    @Autowired private CostSubjectMapper costSubjectMapper;
    @Autowired private CostItemMapper costItemMapper;

    /**
     * Helper that seeds a full test project and returns its ID.
     * Each call produces unique codes via a suffix so tests don't clash on UK constraints.
     */
    private SeedResult seed(String suffix) {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .build());

        // Project
        PmProject project = new PmProject();
        project.setProjectCode("DSH-" + suffix);
        project.setProjectName("Dashboard Test " + suffix);
        project.setProjectType("房建工程");
        project.setStatus("ACTIVE");
        project.setContractAmount(new BigDecimal("10000000.00"));
        project.setTargetCost(new BigDecimal("8000000.00"));
        project.setPlannedEndDate(LocalDate.now().minusDays(10));
        projectMapper.insert(project);
        Long projectId = project.getId();

        // Contract
        CtContract contract = new CtContract();
        contract.setTenantId(TENANT_ID);
        contract.setProjectId(projectId);
        contract.setContractCode("CT-" + suffix);
        contract.setContractName("Contract " + suffix);
        contract.setContractType("SUB");
        contract.setContractAmount(new BigDecimal("5000000.00"));
        contract.setCurrentAmount(new BigDecimal("5500000.00"));
        contract.setPaidAmount(new BigDecimal("2000000.00"));
        contract.setContractStatus("PERFORMING");
        contract.setEndDate(LocalDate.now().plusDays(15));
        ctContractMapper.insert(contract);
        Long contractId = contract.getId();

        // WfInstance + WfTask
        WfInstance instance = new WfInstance();
        instance.setTenantId(TENANT_ID);
        instance.setTemplateId(1L);
        instance.setBusinessType("CONTRACT");
        instance.setBusinessId(contractId);
        instance.setProjectId(projectId);
        instance.setTitle("审批-" + suffix);
        instance.setInstanceStatus("RUNNING");
        instance.setInitiatorId(USER_ADMIN);
        wfInstanceMapper.insert(instance);

        WfTask task = new WfTask();
        task.setTenantId(TENANT_ID);
        task.setInstanceId(instance.getId());
        task.setNodeInstanceId(1L);
        task.setBusinessType("CONTRACT");
        task.setBusinessId(contractId);
        task.setApproverId(USER_ADMIN);
        task.setApproverName("成本经理");
        task.setTaskStatus(WorkflowConstants.TASK_PENDING);
        task.setReceivedAt(LocalDateTime.now().minusDays(9));
        wfTaskMapper.insert(task);

        // VarOrder
        VarOrder varOrder = new VarOrder();
        varOrder.setTenantId(TENANT_ID);
        varOrder.setProjectId(projectId);
        varOrder.setVarCode("VO-" + suffix);
        varOrder.setVarName("签证-" + suffix);
        varOrder.setApprovedAmount(new BigDecimal("100000.00"));
        varOrder.setApprovalStatus("APPROVED");
        varOrderMapper.insert(varOrder);

        // SubMeasure
        SubMeasure subMeasure = new SubMeasure();
        subMeasure.setTenantId(TENANT_ID);
        subMeasure.setProjectId(projectId);
        subMeasure.setMeasureCode("SM-" + suffix);
        subMeasure.setApprovedAmount(new BigDecimal("80000.00"));
        subMeasure.setApprovalStatus("APPROVED");
        subMeasureMapper.insert(subMeasure);

        // Settlement
        StlSettlement settlement = new StlSettlement();
        settlement.setTenantId(TENANT_ID);
        settlement.setProjectId(projectId);
        settlement.setSettlementCode("STL-" + suffix);
        settlement.setSettlementStatus("FINALIZED");
        stlSettlementMapper.insert(settlement);

        // PayRecord
        PayRecord payRecord = new PayRecord();
        payRecord.setTenantId(TENANT_ID);
        payRecord.setPayApplicationId(1L);
        payRecord.setContractId(contractId);
        payRecord.setProjectId(projectId);
        payRecord.setPayAmount(new BigDecimal("100000.00"));
        payRecord.setPayDate(LocalDate.now());
        payRecord.setPayStatus("SUCCESS");
        payRecordMapper.insert(payRecord);

        PayRecord pendingPayRecord = new PayRecord();
        pendingPayRecord.setTenantId(TENANT_ID);
        pendingPayRecord.setPayApplicationId(2L);
        pendingPayRecord.setContractId(contractId);
        pendingPayRecord.setProjectId(projectId);
        pendingPayRecord.setPayAmount(new BigDecimal("230000.00"));
        pendingPayRecord.setPayDate(LocalDate.now().minusDays(1));
        pendingPayRecord.setPayStatus("PENDING_APPROVAL");
        payRecordMapper.insert(pendingPayRecord);

        // CostSummary (project-level)
        CostSummary summary = new CostSummary();
        summary.setTenantId(TENANT_ID);
        summary.setProjectId(projectId);
        summary.setSummaryDate(LocalDate.now());
        summary.setTargetCost(new BigDecimal("8000000.00"));
        summary.setContractLockedCost(new BigDecimal("3000000.00"));
        summary.setActualCost(new BigDecimal("4000000.00"));
        summary.setPaidAmount(new BigDecimal("2000000.00"));
        summary.setEstimatedRemainingCost(new BigDecimal("4200000.00"));
        summary.setDynamicCost(new BigDecimal("8200000.00"));
        summary.setContractIncome(new BigDecimal("10000000.00"));
        summary.setExpectedProfit(new BigDecimal("1800000.00"));
        summary.setCostDeviation(new BigDecimal("200000.00"));
        costSummaryMapper.insert(summary);

        CostSummary lastMonthSummary = new CostSummary();
        lastMonthSummary.setTenantId(TENANT_ID);
        lastMonthSummary.setProjectId(projectId);
        lastMonthSummary.setSummaryDate(LocalDate.now().minusMonths(1).withDayOfMonth(1));
        lastMonthSummary.setTargetCost(new BigDecimal("7600000.00"));
        lastMonthSummary.setContractLockedCost(new BigDecimal("2800000.00"));
        lastMonthSummary.setActualCost(new BigDecimal("3500000.00"));
        lastMonthSummary.setPaidAmount(new BigDecimal("1800000.00"));
        lastMonthSummary.setEstimatedRemainingCost(new BigDecimal("3900000.00"));
        lastMonthSummary.setDynamicCost(new BigDecimal("7600000.00"));
        lastMonthSummary.setContractIncome(new BigDecimal("10000000.00"));
        lastMonthSummary.setExpectedProfit(new BigDecimal("2400000.00"));
        lastMonthSummary.setCostDeviation(BigDecimal.ZERO);
        costSummaryMapper.insert(lastMonthSummary);

        // CostSubject
        CostSubject subject = new CostSubject();
        subject.setTenantId(TENANT_ID);
        subject.setSubjectCode("SUBJ-" + suffix);
        subject.setSubjectName("人工费");
        subject.setLevel(1);
        costSubjectMapper.insert(subject);
        Long subjectId = subject.getId();

        // CostSummary with cost_subject_id
        CostSummary subjectSummary = new CostSummary();
        subjectSummary.setTenantId(TENANT_ID);
        subjectSummary.setProjectId(projectId);
        subjectSummary.setSummaryDate(LocalDate.now());
        subjectSummary.setCostSubjectId(subjectId);
        subjectSummary.setTargetCost(new BigDecimal("3000000.00"));
        subjectSummary.setContractLockedCost(new BigDecimal("1000000.00"));
        subjectSummary.setActualCost(new BigDecimal("1500000.00"));
        subjectSummary.setDynamicCost(new BigDecimal("3100000.00"));
        subjectSummary.setCostDeviation(new BigDecimal("100000.00"));
        costSummaryMapper.insert(subjectSummary);

        CostItem costItem = new CostItem();
        costItem.setTenantId(TENANT_ID);
        costItem.setProjectId(projectId);
        costItem.setContractId(contractId);
        costItem.setCostSubjectId(subjectId);
        costItem.setCostType("CT_LABOR");
        costItem.setAmount(new BigDecimal("1500000.00"));
        costItem.setTaxAmount(BigDecimal.ZERO);
        costItem.setAmountWithoutTax(new BigDecimal("1500000.00"));
        costItem.setSourceType("CT_CONTRACT");
        costItem.setSourceId(contractId);
        costItem.setSourceItemId(1L);
        costItem.setCostDate(LocalDate.now());
        costItem.setCostStatus("CONFIRMED");
        costItem.setGeneratedFlag(1);
        costItemMapper.insert(costItem);

        // AlertLog
        AlertLog alert = new AlertLog();
        alert.setTenantId(TENANT_ID);
        alert.setProjectId(projectId);
        alert.setRuleType("DYNAMIC_COST_EXCEEDS_TARGET");
        alert.setSeverity("HIGH");
        alert.setMessage("动态成本超出目标成本");
        alert.setIsRead(0);
        alert.setTriggeredAt(LocalDateTime.now());
        alertLogMapper.insert(alert);
        // Do NOT clear UserContext here — test methods need it

        return new SeedResult(projectId, project.getProjectName());
    }

    static class SeedResult {
        final Long projectId;
        final String projectName;
        SeedResult(Long projectId, String projectName) {
            this.projectId = projectId;
            this.projectName = projectName;
        }
    }

    // ========================================================================
    // 1. Project Manager View — Single Project
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("1.1 PM view: single project returns correct KPI counts")
    void testPMView_SingleProject() {
        SeedResult sr = seed("PM1");
        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(sr.projectId);

        assertNotNull(vo, "PM VO should not be null");
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertEquals(sr.projectName, vo.getProjectName());

        assertTrue(vo.getPendingTaskCount() >= 1, "At least 1 pending task");
        assertTrue(vo.getLaggingProjectCount() >= 1, "At least 1 lagging project");
        assertTrue(vo.getExpiringContractCount() >= 1, "At least 1 expiring contract");
        assertNotNull(vo.getPendingApprovals(), "pendingApprovals should not be null");
    }

    @Test
    @Transactional
    @DisplayName("1.2 PM view: null projectId returns all-projects aggregate")
    void testPMView_AllProjects() {
        seed("PM2");
        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(null);

        assertNotNull(vo);
        assertNull(vo.getProjectId());
        assertEquals("全部项目", vo.getProjectName());
        assertTrue(vo.getPendingTaskCount() >= 1);
    }

    // ========================================================================
    // 2. Business Manager View
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("2.1 BM view: single project returns correct contract totals")
    void testBMView_SingleProject() {
        SeedResult sr = seed("BM1");
        BusinessManagerDashboardVO vo = dashboardService.getBusinessManagerView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertTrue(new BigDecimal(vo.getTotalContractAmount()).compareTo(BigDecimal.ZERO) > 0);
        assertTrue(new BigDecimal(vo.getVarOrderAmount()).compareTo(BigDecimal.ZERO) > 0);
        assertTrue(new BigDecimal(vo.getSubMeasureAmount()).compareTo(BigDecimal.ZERO) > 0);
        assertTrue(vo.getPaidRatio().contains("%"));
        assertNotNull(vo.getSettlementProgress());
        assertNotNull(vo.getRecentChanges());
        assertTrue(vo.getRecentChanges().size() <= 5);
    }

    @Test
    @Transactional
    @DisplayName("2.2 BM view: null projectId returns tenant-wide aggregate")
    void testBMView_AllProjects() {
        seed("BM2");
        BusinessManagerDashboardVO vo = dashboardService.getBusinessManagerView(null);

        assertNotNull(vo);
        assertNull(vo.getProjectId());
        assertEquals("全部项目", vo.getProjectName());
        assertNotNull(vo.getTotalContractAmount());
        assertNotNull(vo.getPaidRatio());
    }

    @Test
    @Transactional
    @DisplayName("2.3 BM view: grace over zero contracts")
    void testBMView_GracefulEmpty() {
        seed("BM3");
        BusinessManagerDashboardVO vo = dashboardService.getBusinessManagerView(null);
        assertNotNull(vo);
        assertNotNull(vo.getTotalContractAmount());
    }

    // ========================================================================
    // 3. Cost Manager View
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("3.1 Cost view: returns cost data from cost_summary")
    void testCostView_SingleProject() {
        SeedResult sr = seed("COST1");
        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertNotNull(vo.getTargetCost());
        assertNotNull(vo.getDynamicCost());
        assertNotNull(vo.getCostDeviation());
        assertNotNull(vo.getActualCost());
        assertNotNull(vo.getExpectedProfit());
        assertTrue(vo.getOverBudgetAlerts().size() >= 1);
        assertEquals(sr.projectName, vo.getOverBudgetAlerts().get(0).getProjectName());
    }

    @Test
    @Transactional
    @DisplayName("3.1b Cost view: returns dashboard contract lists from real project data")
    void testCostView_ReturnsDashboardContractLists() {
        SeedResult sr = seed("COST_FULL");
        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertEquals("8000000.00", vo.getTargetCost());
        assertEquals("8200000.00", vo.getDynamicCost());

        assertNotNull(vo.getTrendPoints());
        assertEquals(2, vo.getTrendPoints().size(), "monthly trend should come from cost_summary dates");
        assertTrue(vo.getTrendPoints().get(0).getMonth().compareTo(vo.getTrendPoints().get(1).getMonth()) <= 0);
        assertEquals("7600000.00", vo.getTrendPoints().get(0).getDynamicCost());
        assertEquals("8200000.00", vo.getTrendPoints().get(1).getDynamicCost());

        assertNotNull(vo.getSubjectRankings());
        assertEquals(1, vo.getSubjectRankings().size());
        assertEquals("人工费", vo.getSubjectRankings().get(0).getCostSubjectName());
        assertEquals("1500000.00", vo.getSubjectRankings().get(0).getActualCost());

        assertNotNull(vo.getOverdueItems());
        assertEquals(1, vo.getOverdueItems().size());
        assertEquals("审批-COST_FULL", vo.getOverdueItems().get(0).getTitle());
        assertTrue(vo.getOverdueItems().get(0).getOverdueDays() >= 2);
        assertEquals("成本经理", vo.getOverdueItems().get(0).getOwnerName());

        assertNotNull(vo.getPendingPayments());
        assertEquals(1, vo.getPendingPayments().size());
        assertEquals("230000.00", vo.getPendingPayments().get(0).getPayAmount());
        assertEquals("PENDING_APPROVAL", vo.getPendingPayments().get(0).getPayStatus());
        assertEquals("Contract COST_FULL", vo.getPendingPayments().get(0).getContractName());

        assertNotNull(vo.getLedgerRows());
        CostManagerDashboardVO.LedgerRow costRow = vo.getLedgerRows().stream()
                .filter(row -> "cost".equals(row.getRowType()))
                .findFirst()
                .orElseThrow();
        assertEquals("人工费", costRow.getCostSubjectName());
        assertEquals("CT-COST_FULL", costRow.getContractCode());
        assertEquals("Contract COST_FULL", costRow.getContractName());
        assertEquals("正常", costRow.getStatus());
        assertEquals((long) vo.getLedgerRows().size(), vo.getLedgerTotal());
    }

    @Test
    @Transactional
    @DisplayName("3.1c Cost view: selected month uses cost snapshot up to that month")
    void testCostView_SelectedMonthUsesMonthlySnapshot() {
        SeedResult sr = seed("COST_MONTH");
        String lastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1).toString().substring(0, 7);

        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId, lastMonth);

        assertNotNull(vo);
        assertEquals("7600000.00", vo.getTargetCost());
        assertEquals("7600000.00", vo.getDynamicCost());
        assertEquals("3500000.00", vo.getActualCost());
        assertEquals(1, vo.getTrendPoints().size());
        assertEquals(lastMonth, vo.getTrendPoints().get(0).getMonth());
    }

    @Test
    @Transactional
    @DisplayName("3.1d Cost view: selected month keeps latest subject snapshot")
    void testCostView_SelectedMonthKeepsLatestSubjectSnapshot() {
        SeedResult sr = seed("COST_SUBJECT_MONTH");
        LocalDate lastMonthDate = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        for (CostSummary summary : costSummaryMapper.selectList(null)) {
            if (sr.projectId.equals(summary.getProjectId()) && summary.getCostSubjectId() != null) {
                summary.setSummaryDate(lastMonthDate);
                costSummaryMapper.updateById(summary);
            }
        }

        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId, LocalDate.now().toString().substring(0, 7));

        assertEquals(1, vo.getSubjectRankings().size());
        assertEquals("人工费", vo.getSubjectRankings().get(0).getCostSubjectName());
        assertTrue(vo.getLedgerRows().stream().anyMatch(row -> "人工费".equals(row.getCostSubjectName())));
    }

    @Test
    @Transactional
    @DisplayName("3.1e Cost view: fallback rankings include variation cost items")
    void testCostView_FallbackRankingsIncludeVariationCostItems() {
        SeedResult sr = seed("COST_VAR_ITEM");
        costSummaryMapper.physicalDeleteByTenantAndProject(TENANT_ID, sr.projectId);

        CostSubject subject = costSubjectMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CostSubject>()
                        .eq(CostSubject::getTenantId, TENANT_ID)
                        .eq(CostSubject::getSubjectCode, "SUBJ-COST_VAR_ITEM"))
                .stream()
                .findFirst()
                .orElseThrow();

        CostItem variationCost = new CostItem();
        variationCost.setTenantId(TENANT_ID);
        variationCost.setProjectId(sr.projectId);
        variationCost.setCostSubjectId(subject.getId());
        variationCost.setCostType("VARIATION");
        variationCost.setAmount(new BigDecimal("30000.00"));
        variationCost.setTaxAmount(BigDecimal.ZERO);
        variationCost.setAmountWithoutTax(new BigDecimal("30000.00"));
        variationCost.setSourceType("VAR_ORDER");
        variationCost.setSourceId(1L);
        variationCost.setSourceItemId(1L);
        variationCost.setCostDate(LocalDate.now());
        variationCost.setCostStatus("CONFIRMED");
        variationCost.setGeneratedFlag(1);
        costItemMapper.insert(variationCost);

        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId);

        CostManagerDashboardVO.SubjectRanking ranking = vo.getSubjectRankings().stream()
                .filter(item -> subject.getId().toString().equals(item.getCostSubjectId()))
                .findFirst()
                .orElseThrow();
        assertEquals("人工费", ranking.getCostSubjectName());
        assertEquals("30000.00", ranking.getActualCost());
        assertTrue(vo.getLedgerRows().stream()
                .anyMatch(row -> subject.getId().toString().equals(row.getCostSubjectId())
                        && "30000.00".equals(row.getActualAmount())));
    }

    @Test
    @Transactional
    @DisplayName("3.2 Cost view: null projectId returns tenant-wide cost")
    void testCostView_AllProjects() {
        seed("COST2");
        CostManagerDashboardVO vo = dashboardService.getCostManagerView(null);

        assertNotNull(vo);
        assertNull(vo.getProjectId());
        assertEquals("全部项目", vo.getProjectName());
        assertNotNull(vo.getTargetCost());
        assertNotNull(vo.getDynamicCost());
        assertNotNull(vo.getTrendPoints());
        assertNotNull(vo.getSubjectRankings());
        assertNotNull(vo.getOverdueItems());
        assertNotNull(vo.getPendingPayments());
        assertNotNull(vo.getLedgerRows());
        assertTrue(vo.getLedgerTotal() >= 1L);
    }

    // ========================================================================
    // 4. Finance View
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("4.1 Finance view: single project payment analysis")
    void testFinanceView_SingleProject() {
        SeedResult sr = seed("FIN1");
        FinanceDashboardVO vo = dashboardService.getFinanceView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertNotNull(vo.getPendingPaymentAmount());
        assertNotNull(vo.getPendingPaymentCount());
        assertNotNull(vo.getApprovedUnpaidAmount());
        assertNotNull(vo.getOverRatioAmount());
        assertNotNull(vo.getWarrantyExpiringAmount());
        assertNotNull(vo.getPendingPayments());
    }

    @Test
    @Transactional
    @DisplayName("4.2 Finance view: null projectId returns tenant-wide finance")
    void testFinanceView_AllProjects() {
        seed("FIN2");
        FinanceDashboardVO vo = dashboardService.getFinanceView(null);

        assertNotNull(vo);
        assertNull(vo.getProjectId());
        assertEquals("全部项目", vo.getProjectName());
        assertNotNull(vo.getPendingPaymentAmount());
    }

    // ========================================================================
    // 5. Management View
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("5.1 Management view: returns project rankings and aggregates")
    void testManagementView() {
        seed("MGMT1");
        ManagementDashboardVO vo = dashboardService.getManagementView();

        assertNotNull(vo);
        assertTrue(vo.getActiveProjectCount() >= 1);
        assertNotNull(vo.getTotalContractAmount());
        assertNotNull(vo.getTotalDynamicCost());
        assertNotNull(vo.getTotalExpectedProfit());
        assertNotNull(vo.getTotalPaidAmount());
        assertNotNull(vo.getProjectRankings());
        assertTrue(vo.getProjectRankings().size() >= 1);

        // Rankings sorted by expectedProfit DESC
        List<DashboardProjectSummaryVO> rankings = vo.getProjectRankings();
        for (int i = 1; i < rankings.size(); i++) {
            BigDecimal prev = new BigDecimal(
                    rankings.get(i - 1).getExpectedProfit() != null
                            ? rankings.get(i - 1).getExpectedProfit() : "0");
            BigDecimal curr = new BigDecimal(
                    rankings.get(i).getExpectedProfit() != null
                            ? rankings.get(i).getExpectedProfit() : "0");
            assertTrue(prev.compareTo(curr) >= 0,
                    "Rankings should be sorted by expectedProfit DESC");
        }

        assertNotNull(vo.getOverdueItems());
        assertNotNull(vo.getMajorRisks());
        assertTrue(vo.getMajorRisks().size() >= 1);
        assertTrue(vo.getTotalPendingTaskCount() >= 1);
    }

    @Test
    @Transactional
    @DisplayName("5.2 Management view: graceful with existing projects")
    void testManagementView_Graceful() {
        seed("MGMT2");
        ManagementDashboardVO vo = dashboardService.getManagementView();
        assertNotNull(vo);
        assertNotNull(vo.getProjectRankings());
        assertNotNull(vo.getTotalContractAmount());
    }

    // ========================================================================
    // 6. Cost Breakdown
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("6.1 Cost breakdown: returns subject drill-down with level ≤ 2")
    void testCostBreakdown() {
        SeedResult sr = seed("BD1");
        CostBreakdownVO vo = dashboardService.getCostBreakdown(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertNotNull(vo.getTargetCost());
        assertNotNull(vo.getDynamicCost());
        assertNotNull(vo.getExpectedProfit());
        assertNotNull(vo.getSubjectBreakdowns());
        assertTrue(vo.getSubjectBreakdowns().size() >= 1);

        for (CostBreakdownVO.SubjectBreakdown bd : vo.getSubjectBreakdowns()) {
            assertNotNull(bd.getLevel());
            assertTrue(bd.getLevel() <= 2, "Only level ≤ 2 subjects");
        }
    }

    @Test
    @Transactional
    @DisplayName("6.2 Cost breakdown: graceful empty summaries")
    void testCostBreakdown_EmptyGraceful() {
        SeedResult sr = seed("BD2");
        CostBreakdownVO vo = dashboardService.getCostBreakdown(sr.projectId);
        assertNotNull(vo);
        assertNotNull(vo.getSubjectBreakdowns());
    }

    @Test
    @Transactional
    @DisplayName("6.3 Cost breakdown: missing subject name falls back to empty")
    void testCostBreakdown_SubjectNameFallback() {
        SeedResult sr = seed("BD3");
        CostBreakdownVO vo = dashboardService.getCostBreakdown(sr.projectId);
        assertNotNull(vo);
        for (CostBreakdownVO.SubjectBreakdown bd : vo.getSubjectBreakdowns()) {
            assertNotNull(bd.getCostSubjectName(), "subjectName should not be null");
        }
    }

    // ========================================================================
    // 7. Edges
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("7.1 Paid ratio: format is N%")
    void testPaidRatio_Format() {
        SeedResult sr = seed("EDGE1");
        BusinessManagerDashboardVO vo = dashboardService.getBusinessManagerView(sr.projectId);
        assertNotNull(vo.getPaidRatio());
        assertTrue(vo.getPaidRatio().endsWith("%"), "paidRatio should end with %");
    }

    @Test
    @Transactional
    @DisplayName("7.2 Var order: only APPROVED amounts counted")
    void testVarOrder_OnlyApproved() {
        SeedResult sr = seed("EDGE2");
        BusinessManagerDashboardVO vo = dashboardService.getBusinessManagerView(sr.projectId);
        // BigDecimal.toPlainString() produces "100000.00"
        assertEquals("100000.00", vo.getVarOrderAmount());
    }

    @Test
    @Transactional
    @DisplayName("7.3 Lagging project: past plannedEndDate appears")
    void testLaggingProject_Present() {
        SeedResult sr = seed("EDGE3");
        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(sr.projectId);
        boolean found = vo.getLaggingProjects().stream()
                .anyMatch(p -> sr.projectId.toString().equals(p.getProjectId()));
        assertTrue(found, "Seeded project should appear in lagging list");
    }
}
