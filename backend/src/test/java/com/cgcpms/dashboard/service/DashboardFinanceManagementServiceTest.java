package com.cgcpms.dashboard.service;

import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.mapper.ProjectBudgetLineMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.dashboard.vo.*;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.tech.entity.TechItem;
import com.cgcpms.tech.mapper.TechItemMapper;
import com.cgcpms.tech.vo.ChiefEngineerDashboardVO;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("Dashboard finance and management views")
class DashboardFinanceManagementServiceTest extends DashboardServiceTestSupport {

    @Autowired private SysRoleMapper sysRoleMapper;
    @Autowired private ProjectBudgetMapper projectBudgetMapper;
    @Autowired private ProjectBudgetLineMapper projectBudgetLineMapper;
    @Autowired private CashJournalEntryMapper cashJournalEntryMapper;
    @Autowired private CostSummaryService costSummaryService;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private WfTaskMapper countedWfTaskMapper;
    @MockitoSpyBean private WfInstanceMapper countedWfInstanceMapper;
    @MockitoSpyBean private AlertLogMapper countedAlertLogMapper;

    @Test
    @Transactional
    @DisplayName("报告期切换同步更新财务与管理层指标")
    void reportMonthChangesFinanceAndManagementMetrics() {
        SeedResult sr = seed("REPORT_MONTH");
        YearMonth current = YearMonth.now();
        YearMonth previous = current.minusMonths(1);

        assertEquals("0", dashboardService.getFinanceView(sr.projectId, previous.toString())
                .getTotalPaidAmount());
        assertEquals("100000.00", dashboardService.getFinanceView(sr.projectId, current.toString())
                .getTotalPaidAmount());
        ManagementDashboardVO previousManagement = dashboardService.getManagementView(
                sr.projectId, previous.toString());
        ManagementDashboardVO currentManagement = dashboardService.getManagementView(
                sr.projectId, current.toString());
        assertNull(previousManagement.getActiveProjectCount());
        assertNull(currentManagement.getActiveProjectCount());
        assertTrue(currentManagement.getUnavailableMetrics().contains("activeProjectCount"));
    }

    @Test
    @Transactional
    @DisplayName("历史管理视图不读取当前待办、实例或实时预警")
    void historicalManagementSkipsRealtimeRiskQueries() {
        SeedResult sr = seed("HISTORY_NO_REALTIME_RISK");
        clearInvocations(countedWfTaskMapper, countedWfInstanceMapper, countedAlertLogMapper);

        ManagementDashboardVO historical = dashboardService.getManagementView(
                sr.projectId, YearMonth.now().minusMonths(1).toString());

        assertNull(historical.getTotalPendingTaskCount());
        assertNull(historical.getTotalRiskCount());
        assertTrue(historical.getOverdueItems().isEmpty());
        assertTrue(historical.getMajorRisks().isEmpty());
        verify(countedWfTaskMapper, never()).selectList(any());
        verify(countedWfInstanceMapper, never()).selectList(any());
        verify(countedAlertLogMapper, never()).selectList(any());
    }

    @Test
    @Transactional
    @DisplayName("4.1 Finance view: single project payment analysis")
    void testFinanceView_SingleProject() {
        SeedResult sr = seed("FIN1");
        PayRecord paid = payRecordMapper.selectOne(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getProjectId, sr.projectId)
                .eq(PayRecord::getPayStatus, "SUCCESS"));
        paid.setPayAmount(new BigDecimal("6000000.00"));
        payRecordMapper.updateById(paid);
        FinanceDashboardVO vo = dashboardService.getFinanceView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertNotNull(vo.getPendingPaymentAmount());
        assertNotNull(vo.getPendingPaymentCount());
        assertNotNull(vo.getApprovedUnpaidAmount());
        assertNotNull(vo.getOverRatioAmount());
        assertNotNull(vo.getWarrantyExpiringAmount());
        assertNull(vo.getCashBalance());
        assertFalse(vo.getCashBalanceAvailable());
        assertNotNull(vo.getTrendPoints());
        assertNotNull(vo.getPendingPayments());
        assertEquals("1000000.00", vo.getOverRatioAmount());
        assertEquals(1, vo.getOverRatioPayments().size());
        assertEquals(paid.getId().toString(), vo.getOverRatioPayments().get(0).getPayRecordId());
        assertEquals(1, vo.getContractFundBreakdowns().size());
        FinanceDashboardVO.ContractFundBreakdown breakdown = vo.getContractFundBreakdowns().get(0);
        assertEquals("5500000.00", breakdown.getContractAmount());
        assertEquals("6000000.00", breakdown.getPaidAmount());
        assertEquals("0", breakdown.getRemainingAmount());
        assertEquals("109.09", breakdown.getPaymentRatio());
        assertEquals(2, breakdown.getPaymentRecords().size());
    }

    @Test
    @Transactional
    @DisplayName("实时超付沿用合同金额事实，不被合同当前履约状态改写")
    void realtimeOverRatioKeepsContractStatusIndependentSemantics() {
        SeedResult sr = seed("FIN_OVER_RATIO_STATUS");
        PayRecord paid = payRecordMapper.selectOne(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getProjectId, sr.projectId)
                .eq(PayRecord::getPayStatus, "SUCCESS"));
        paid.setPayAmount(new BigDecimal("6000000.00"));
        payRecordMapper.updateById(paid);
        CtContract contract = ctContractMapper.selectById(paid.getContractId());
        contract.setContractStatus("SETTLED");
        ctContractMapper.updateById(contract);

        FinanceDashboardVO vo = dashboardService.getFinanceView(sr.projectId);

        assertEquals("1000000.00", vo.getOverRatioAmount());
        assertEquals(1, vo.getOverRatioPayments().size());
        assertTrue(vo.getContractFundBreakdowns().isEmpty());
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
        assertNotNull(vo.getCashBalance());
        assertTrue(vo.getCashBalanceAvailable());
        assertNotNull(vo.getTrendPoints());
        assertFalse(vo.getContractFundBreakdowns().isEmpty());
    }

    @Test
    @Transactional
    @DisplayName("财务口径仅使用生效预算占用加消耗、已归档现金日记和确认收入减动态成本")
    void financeMetricsUseClosedLoopAuthorities() {
        SeedResult sr = seed("FIN_AUTHORITY");
        Long subjectId = costSubjectMapper.selectOne(new LambdaQueryWrapper<CostSubject>()
                .eq(CostSubject::getTenantId, TENANT_ID)
                .eq(CostSubject::getSubjectCode, "SUBJ-FIN_AUTHORITY")).getId();

        ProjectBudget budget = new ProjectBudget();
        budget.setTenantId(TENANT_ID);
        budget.setProjectId(sr.projectId);
        budget.setBudgetCode("BUD-FIN-AUTHORITY");
        budget.setVersionNo("V1");
        budget.setBudgetName("Finance authority budget");
        budget.setTotalAmount(new BigDecimal("1000.00"));
        budget.setApprovalStatus("APPROVED");
        budget.setStatus("ACTIVE");
        budget.setActiveFlag(1);
        budget.setActiveToken(sr.projectId);
        budget.setEffectiveAt(LocalDateTime.now());
        budget.setVersion(0);
        projectBudgetMapper.insert(budget);

        ProjectBudgetLine line = new ProjectBudgetLine();
        line.setTenantId(TENANT_ID);
        line.setBudgetId(budget.getId());
        line.setProjectId(sr.projectId);
        line.setCostSubjectId(subjectId);
        line.setBudgetAmount(new BigDecimal("1000.00"));
        line.setReservedAmount(new BigDecimal("200.00"));
        line.setConsumedAmount(new BigDecimal("300.00"));
        line.setVersion(0);
        projectBudgetLineMapper.insert(line);

        cashJournalEntryMapper.insert(journal(sr.projectId, "FIN-AUTH-ARCHIVED", 91001L,
                "ARCHIVED", new BigDecimal("400.00")));
        cashJournalEntryMapper.insert(journal(sr.projectId, "FIN-AUTH-DRAFT", 91002L,
                "DRAFT", new BigDecimal("900.00")));

        FinanceDashboardVO vo = dashboardService.getFinanceView(sr.projectId);
        var cost = costSummaryService.getProjectSummary(TENANT_ID, sr.projectId);

        assertEquals("1000.00", vo.getBudgetAmount());
        assertEquals("200.00", vo.getBudgetReservedAmount());
        assertEquals("300.00", vo.getBudgetConsumedAmount());
        assertEquals("50.00", vo.getBudgetExecutionRate());
        assertEquals("400.00", vo.getCashOutflowAmount());
        assertEquals("400.00", vo.getTrendPoints().getLast().getCashOutflowAmount());
        assertEquals(new BigDecimal(cost.getConfirmedRevenue())
                .subtract(new BigDecimal(cost.getDynamicCost())).toPlainString(), vo.getProjectProfit());
    }

    @Test
    @Transactional
    @DisplayName("财务合同金额只汇总审批通过且履约中的有效合同")
    void financeContractAmountExcludesDraftRejectedAndTerminatedContracts() {
        SeedResult sr = seed("FIN_VALID_CONTRACT");
        for (String[] state : List.of(
                new String[]{"DRAFT", "DRAFT"},
                new String[]{"REJECTED", "DRAFT"},
                new String[]{"APPROVED", "TERMINATED"})) {
            CtContract invalid = new CtContract();
            invalid.setTenantId(TENANT_ID);
            invalid.setProjectId(sr.projectId);
            invalid.setContractCode("CT-INVALID-" + state[0] + "-" + state[1]);
            invalid.setContractName("Invalid contract " + state[0] + " " + state[1]);
            invalid.setContractType("SUB");
            invalid.setContractAmount(new BigDecimal("9000000.00"));
            invalid.setCurrentAmount(new BigDecimal("9000000.00"));
            invalid.setApprovalStatus(state[0]);
            invalid.setContractStatus(state[1]);
            ctContractMapper.insert(invalid);
        }

        FinanceDashboardVO vo = dashboardService.getFinanceView(sr.projectId);

        assertEquals("5500000.00", vo.getTotalContractAmount());
    }

    @Test
    @Transactional
    @DisplayName("4.3 Finance view: contract breakdown respects SELF project scope")
    void testFinanceView_ContractBreakdownRespectsProjectScope() {
        SeedResult visible = seed("FIN_SELF_VISIBLE");
        SeedResult hidden = seed("FIN_SELF_HIDDEN");
        long scopedUserId = 88_101L;
        applySelfScope(visible.projectId, hidden.projectId, scopedUserId);

        FinanceDashboardVO vo = dashboardService.getFinanceView(null);

        assertEquals(Set.of(visible.projectId.toString()), vo.getContractFundBreakdowns().stream()
                .map(FinanceDashboardVO.ContractFundBreakdown::getProjectId)
                .collect(Collectors.toSet()));
        assertNull(vo.getCashBalance());
        assertFalse(vo.getCashBalanceAvailable());
        BusinessException denied = assertThrows(BusinessException.class,
                () -> dashboardService.getFinanceView(hidden.projectId));
        assertEquals("PROJECT_ACCESS_DENIED", denied.getCode());
    }

    @Test
    @Transactional
    @DisplayName("历史付款按不可变付款与冲销时间重算，当前合同状态不改写历史")
    void historicalPaymentSurvivesCurrentStatusDrift() {
        SeedResult sr = seed("FIN_HISTORY_DRIFT");
        YearMonth previous = YearMonth.now().minusMonths(1);
        PayRecord payment = payRecordMapper.selectOne(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getProjectId, sr.projectId)
                .eq(PayRecord::getPayStatus, "SUCCESS"));
        payment.setPayDate(previous.atDay(10));
        payment.setPaidAt(previous.atDay(10).atTime(10, 0));
        payment.setPayStatus("REVERSED");
        payment.setReversedAt(YearMonth.now().atDay(1).atStartOfDay());
        payRecordMapper.updateById(payment);
        CtContract contract = ctContractMapper.selectById(payment.getContractId());
        contract.setContractStatus("SETTLED");
        ctContractMapper.updateById(contract);

        FinanceDashboardVO historical = dashboardService.getFinanceView(sr.projectId, previous.toString());

        assertEquals("100000.00", historical.getTotalPaidAmount());
        assertNull(historical.getTotalContractAmount());
        assertTrue(historical.getUnavailableMetrics().contains("totalContractAmount"));
        assertNull(historical.getPendingPaymentAmount());
    }

    @Test
    @Transactional
    @DisplayName("历史全项目财务不受项目当前状态和可变计划日期影响")
    void historicalAllProjectsSurvivesCurrentProjectStatusDrift() {
        SeedResult sr = seed("FIN_HISTORY_ALL");
        YearMonth previous = YearMonth.now().minusMonths(1);
        PayRecord payment = payRecordMapper.selectOne(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getProjectId, sr.projectId)
                .eq(PayRecord::getPayStatus, "SUCCESS"));
        payment.setPayDate(previous.atDay(10));
        payment.setPaidAt(previous.atDay(10).atTime(10, 0));
        payRecordMapper.updateById(payment);
        PmProject project = projectMapper.selectById(sr.projectId);
        project.setStatus("CLOSED");
        project.setPlannedStartDate(YearMonth.now().plusMonths(2).atDay(1));
        projectMapper.updateById(project);
        jdbc.update("UPDATE pm_project SET created_at=? WHERE id=?", previous.atDay(1).atStartOfDay(), sr.projectId);

        FinanceDashboardVO historical = dashboardService.getFinanceView(null, previous.toString());

        assertEquals("100000.00", historical.getTotalPaidAmount());
        assertTrue(historical.getContractFundBreakdowns().isEmpty());
        assertTrue(historical.getUnavailableMetrics().contains("contractFundBreakdowns"));
    }

    @Test
    @Transactional
    @DisplayName("历史空项目集保持不可追溯指标为 unavailable，而非伪造零值")
    void historicalEmptyProjectSetKeepsUnavailableMetricContract() {
        SeedResult visible = seed("FIN_HISTORY_EMPTY_VISIBLE");
        SeedResult hidden = seed("FIN_HISTORY_EMPTY_HIDDEN");
        long scopedUserId = 88_301L;
        String roleCode = applySelfScope(visible.projectId, hidden.projectId, scopedUserId);
        TestUserContext.setUser(TENANT_ID, scopedUserId + 99, "finance-history-empty", List.of(roleCode));

        FinanceDashboardVO historical = dashboardService.getFinanceView(
                null, YearMonth.now().minusMonths(1).toString());

        assertNull(historical.getPendingPaymentAmount());
        assertNull(historical.getPendingPaymentCount());
        assertNull(historical.getApprovedUnpaidAmount());
        assertNull(historical.getTotalContractAmount());
        assertNull(historical.getBudgetAmount());
        assertNull(historical.getBudgetReservedAmount());
        assertNull(historical.getBudgetConsumedAmount());
        assertNull(historical.getBudgetExecutionRate());
        assertEquals("0.00", historical.getTotalPaidAmount());
        assertEquals("0.00", historical.getCashOutflowAmount());
        assertTrue(historical.getUnavailableMetrics().containsAll(List.of(
                "pendingPaymentAmount", "pendingPaymentCount", "approvedUnpaidAmount",
                "totalContractAmount", "budgetAmount", "budgetReservedAmount",
                "budgetConsumedAmount", "budgetExecutionRate", "contractFundBreakdowns")));
    }

    @Test
    @Transactional
    @DisplayName("历史管理视图按创建时间保留项目，不受计划开始日期漂移影响")
    void historicalManagementSurvivesPlannedStartDateDrift() {
        SeedResult sr = seed("MGMT_HISTORY_PROJECT_DRIFT");
        YearMonth previous = YearMonth.now().minusMonths(1);
        PmProject project = projectMapper.selectById(sr.projectId);
        project.setStatus("CLOSED");
        project.setPlannedStartDate(YearMonth.now().plusMonths(2).atDay(1));
        projectMapper.updateById(project);
        jdbc.update("UPDATE pm_project SET created_at=? WHERE id=?",
                previous.atDay(1).atStartOfDay(), sr.projectId);

        ManagementDashboardVO historical = dashboardService.getManagementView(
                sr.projectId, previous.toString());

        assertEquals(List.of(sr.projectId.toString()), historical.getProjectRankings().stream()
                .map(DashboardProjectSummaryVO::getProjectId).toList());
    }

    @Test
    @Transactional
    @DisplayName("历史月末使用下月起点排他边界，保留亚秒归档并排除月内冲销")
    void historicalMonthEndUsesExclusiveNextMonthBoundary() {
        SeedResult sr = seed("FIN_HISTORY_NANO_BOUNDARY");
        YearMonth previous = YearMonth.now().minusMonths(1);
        LocalDateTime monthEndSubsecond = previous.atEndOfMonth().atTime(23, 59, 59, 500_000_000);
        jdbc.update("UPDATE pm_project SET created_at=? WHERE id=?",
                previous.atDay(1).atStartOfDay(), sr.projectId);

        PayRecord payment = payRecordMapper.selectOne(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getProjectId, sr.projectId)
                .eq(PayRecord::getPayStatus, "SUCCESS"));
        payment.setPayDate(previous.atDay(10));
        payment.setPaidAt(previous.atDay(10).atTime(10, 0));
        payment.setPayStatus("REVERSED");
        payment.setReversedAt(monthEndSubsecond);
        payRecordMapper.updateById(payment);

        CashJournalEntry journal = journal(sr.projectId, "FIN-HISTORY-NANO", 91_401L,
                "ARCHIVED", new BigDecimal("12.34"));
        journal.setBusinessDate(previous.atEndOfMonth());
        journal.setArchivedAt(monthEndSubsecond);
        cashJournalEntryMapper.insert(journal);

        FinanceDashboardVO historical = dashboardService.getFinanceView(sr.projectId, previous.toString());

        assertEquals(0, new BigDecimal(historical.getTotalPaidAmount()).compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("12.34"), new BigDecimal(historical.getCashOutflowAmount()));
    }

    // ========================================================================
    // 5. Management View
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("5.1 Management view: returns project rankings and aggregates")
    void testManagementView() {
        SeedResult sr = seed("MGMT1");
        for (String severity : List.of("MEDIUM", "LOW", "INFO")) {
            AlertLog alert = new AlertLog();
            alert.setTenantId(TENANT_ID);
            alert.setProjectId(sr.projectId);
            alert.setRuleType("DYNAMIC_COST_EXCEEDS_TARGET");
            alert.setSeverity(severity);
            alert.setMessage(severity + " management risk");
            alert.setIsRead(0);
            alert.setTriggeredAt(LocalDateTime.now());
            alertLogMapper.insert(alert);
        }
        ManagementDashboardVO vo = dashboardService.getManagementView();

        assertNotNull(vo);
        assertTrue(vo.getActiveProjectCount() >= 1);
        assertNotNull(vo.getTotalContractAmount());
        assertNotNull(vo.getTotalDynamicCost());
        assertNotNull(vo.getTotalExpectedProfit());
        assertNotNull(vo.getTotalPaidAmount());
        assertNotNull(vo.getProjectRankings());
        assertTrue(vo.getProjectRankings().size() >= 1);
        assertNotNull(vo.getMetricSources());
        assertEquals(vo.getProjectRankings().size(), vo.getMetricSources().size());
        assertTrue(vo.getMetricSources().stream()
                        .anyMatch(source -> sr.projectId.toString().equals(source.getSourceId())
                                && "PROJECT_SUMMARY".equals(source.getSourceType())),
                "经营总览指标应能下钻到项目汇总来源");

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
        Set<String> severities = vo.getMajorRisks().stream()
                .map(DashboardAlertItemVO::getSeverity)
                .collect(Collectors.toSet());
        assertEquals(Set.of("HIGH", "MEDIUM", "LOW", "INFO"), severities);
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

    @Test
    @Transactional
    @DisplayName("5.3 Management view: selected project scopes rankings and totals")
    void testManagementView_SelectedProject() {
        SeedResult selected = seed("MGMT_SELECTED");
        seed("MGMT_OTHER");

        ManagementDashboardVO vo = dashboardService.getManagementView(selected.projectId);

        assertEquals(1L, vo.getActiveProjectCount());
        assertEquals(List.of(selected.projectId.toString()), vo.getProjectRankings().stream()
                .map(DashboardProjectSummaryVO::getProjectId).toList());
        DashboardProjectSummaryVO ranking = vo.getProjectRankings().getFirst();
        assertEquals(ranking.getContractIncome(), vo.getTotalContractAmount());
        assertEquals(ranking.getDynamicCost(), vo.getTotalDynamicCost());
        assertEquals(ranking.getExpectedProfit(), vo.getTotalExpectedProfit());
        assertEquals(ranking.getPaidAmount(), vo.getTotalPaidAmount());
    }

    @Test
    @Transactional
    @DisplayName("5.4 Management view: rankings, tasks and risks respect SELF project scope")
    void testManagementView_RespectsProjectDataScope() {
        SeedResult visible = seed("MGMT_SELF_VISIBLE");
        SeedResult hidden = seed("MGMT_SELF_HIDDEN");
        long scopedUserId = 88_201L;
        String roleCode = applySelfScope(visible.projectId, hidden.projectId, scopedUserId);

        ManagementDashboardVO vo = dashboardService.getManagementView();
        assertEquals(1L, vo.getActiveProjectCount());
        assertEquals(List.of(visible.projectId.toString()), vo.getProjectRankings().stream()
                .map(DashboardProjectSummaryVO::getProjectId).toList());
        DashboardProjectSummaryVO visibleRanking = vo.getProjectRankings().get(0);
        assertEquals(visibleRanking.getContractIncome(), vo.getTotalContractAmount());
        assertEquals(visibleRanking.getDynamicCost(), vo.getTotalDynamicCost());
        assertEquals(visibleRanking.getExpectedProfit(), vo.getTotalExpectedProfit());
        assertEquals(visibleRanking.getPaidAmount(), vo.getTotalPaidAmount());
        assertEquals(1L, vo.getTotalPendingTaskCount());
        assertEquals(1L, vo.getTotalRiskCount());

        TestUserContext.setUser(TENANT_ID, scopedUserId + 99, "management-self-empty", List.of(roleCode));
        ManagementDashboardVO empty = dashboardService.getManagementView();
        assertEquals(0L, empty.getActiveProjectCount());
        assertEquals("0", empty.getTotalContractAmount());
        assertEquals("0", empty.getTotalDynamicCost());
        assertEquals("0", empty.getTotalExpectedProfit());
        assertEquals("0", empty.getTotalPaidAmount());
        assertEquals(0L, empty.getTotalPendingTaskCount());
        assertEquals(0L, empty.getTotalRiskCount());
        assertTrue(empty.getProjectRankings().isEmpty());
        assertTrue(empty.getMetricSources().isEmpty());
        assertTrue(empty.getOverdueItems().isEmpty());
        assertTrue(empty.getMajorRisks().isEmpty());
    }

    @Test
    @Transactional
    @DisplayName("5.5 Management view: overdue tasks retain actionable business context")
    void testManagementView_OverdueTaskContext() {
        SeedResult seeded = seed("MGMT_OVERDUE_CONTEXT");
        WfInstance instance = wfInstanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getTenantId, TENANT_ID)
                .eq(WfInstance::getProjectId, seeded.projectId)
                .eq(WfInstance::getTitle, "审批-MGMT_OVERDUE_CONTEXT"));
        assertNotNull(instance);
        WfTask task = wfTaskMapper.selectOne(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTenantId, TENANT_ID)
                .eq(WfTask::getInstanceId, instance.getId()));
        assertNotNull(task);
        task.setReceivedAt(LocalDateTime.now().minusDays(9));
        wfTaskMapper.updateById(task);

        DashboardTaskItemVO overdue = dashboardService.getManagementView(seeded.projectId)
                .getOverdueItems().stream()
                .filter(item -> task.getId().toString().equals(item.getTaskId()))
                .findFirst()
                .orElseThrow();

        assertEquals("审批-MGMT_OVERDUE_CONTEXT", overdue.getTitle());
        assertEquals("合同审批摘要-MGMT_OVERDUE_CONTEXT", overdue.getItemSummary());
        assertEquals(seeded.projectId.toString(), overdue.getProjectId());
        assertEquals(seeded.projectName, overdue.getProjectName());
        assertEquals("成本经理", overdue.getOwnerName());
        assertTrue(overdue.getPendingDays() >= 9);
    }

    private String applySelfScope(Long visibleProjectId, Long hiddenProjectId, long scopedUserId) {
        PmProject visible = projectMapper.selectById(visibleProjectId);
        visible.setCreatedBy(scopedUserId);
        visible.setProjectManagerId(null);
        projectMapper.updateById(visible);
        PmProject hidden = projectMapper.selectById(hiddenProjectId);
        hidden.setCreatedBy(scopedUserId + 1);
        hidden.setProjectManagerId(null);
        projectMapper.updateById(hidden);

        String roleCode = "MGMT_DASH_SELF_" + System.nanoTime();
        SysRole role = new SysRole();
        role.setTenantId(TENANT_ID);
        role.setRoleCode(roleCode);
        role.setRoleName("Management dashboard SELF scope");
        role.setRoleType("CUSTOM");
        role.setStatus("ENABLE");
        role.setDataScope("SELF");
        sysRoleMapper.insert(role);
        TestUserContext.setUser(TENANT_ID, scopedUserId, "management-dashboard-self", List.of(roleCode));
        return roleCode;
    }

    private CashJournalEntry journal(
            Long projectId, String entryNo, Long sourceId, String status, BigDecimal amount) {
        CashJournalEntry journal = new CashJournalEntry();
        journal.setTenantId(TENANT_ID);
        journal.setEntryNo(entryNo);
        journal.setDirection("OUT");
        journal.setAmount(amount);
        journal.setBusinessDate(LocalDate.now());
        journal.setSummary(entryNo);
        journal.setProjectId(projectId);
        journal.setSourceType("MANUAL");
        journal.setSourceId(sourceId);
        journal.setStatus(status);
        journal.setClosureDueAt(LocalDateTime.now().plusDays(1));
        journal.setVersion(0);
        if ("ARCHIVED".equals(status)) {
            journal.setArchivedBy(1L);
            journal.setArchivedAt(LocalDateTime.now());
        }
        return journal;
    }

    // ========================================================================
    // 6. Cost Breakdown
    // ========================================================================
}
