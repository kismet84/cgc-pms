package com.cgcpms.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.cost.vo.CostProjectSummaryVO;
import com.cgcpms.cost.vo.CostSummaryVO;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import com.cgcpms.common.util.DateTimeUtils;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CostSummaryService costSummaryService;
    private final CostSummaryMapper costSummaryMapper;
    private final CostSubjectMapper costSubjectMapper;
    private final CostItemMapper costItemMapper;
    private final PmProjectMapper projectMapper;
    private final CtContractMapper ctContractMapper;
    private final WfTaskMapper wfTaskMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final PayRecordMapper payRecordMapper;
    private final StlSettlementMapper stlSettlementMapper;
    private final VarOrderMapper varOrderMapper;
    private final SubMeasureMapper subMeasureMapper;
    private final AlertLogMapper alertLogMapper;

    // ========================================================================
    // 1. Project Manager Dashboard
    // ========================================================================
    public ProjectManagerDashboardVO getProjectManagerView(Long projectId) {
        Long tenantId = UserContext.getCurrentTenantId();

        if (projectId == null) {
            return getProjectManagerViewAllProjects(tenantId);
        }

        PmProject project = requireProject(tenantId, projectId);

        ProjectManagerDashboardVO vo = new ProjectManagerDashboardVO();
        vo.setProjectId(projectId.toString());
        vo.setProjectName(project.getProjectName());

        // Pending tasks for current user
        Long currentUserId = UserContext.getCurrentUserId();
        List<WfTask> pendingTasks = wfTaskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getTenantId, tenantId)
                        .eq(WfTask::getApproverId, currentUserId)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                        .orderByDesc(WfTask::getReceivedAt));

        // Enrich with instance info (batch to avoid N+1)
        Map<Long, WfInstance> instanceMap = batchLoadInstances(pendingTasks);

        List<DashboardTaskItemVO> taskItems = pendingTasks.stream().map(t -> {
            DashboardTaskItemVO item = new DashboardTaskItemVO();
            item.setTaskId(String.valueOf(t.getId()));
            item.setInstanceId(String.valueOf(t.getInstanceId()));
            item.setBusinessType(t.getBusinessType());
            item.setBusinessId(String.valueOf(t.getBusinessId()));
            item.setTaskStatus(t.getTaskStatus());
            if (t.getReceivedAt() != null) item.setReceivedAt(DateTimeUtils.DTF.format(t.getReceivedAt()));
            WfInstance inst = instanceMap.get(t.getInstanceId());
            if (inst != null) {
                item.setTitle(inst.getTitle());
                item.setProjectId(inst.getProjectId() != null ? String.valueOf(inst.getProjectId()) : null);
            }
            return item;
        }).collect(Collectors.toList());

        vo.setPendingTasks(taskItems);
        vo.setPendingTaskCount((long) taskItems.size());

        // Lagging projects: planned end date in the past, not completed
        List<PmProject> tenantProjects = projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE"));
        List<DashboardProjectSummaryVO> lagging = tenantProjects.stream()
                .filter(p -> p.getPlannedEndDate() != null && p.getPlannedEndDate().isBefore(LocalDate.now())
                        && !"COMPLETED".equals(p.getStatus()))
                .map(this::toProjectSummary)
                .collect(Collectors.toList());
        vo.setLaggingProjects(lagging);
        vo.setLaggingProjectCount((long) lagging.size());

        // Pending approvals: wf_task count for the project (via wf_instance.projectId)
        List<WfInstance> projectInstances = wfInstanceMapper.selectList(
                new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getTenantId, tenantId)
                        .eq(WfInstance::getProjectId, projectId));
        Set<Long> instanceIds = projectInstances.stream().map(WfInstance::getId).collect(Collectors.toSet());
        List<DashboardTaskItemVO> pendingApprovals = Collections.emptyList();
        long pendingApprovalCount = 0;
        if (!instanceIds.isEmpty()) {
            List<WfTask> projectPendingTasks = wfTaskMapper.selectList(
                    new LambdaQueryWrapper<WfTask>()
                            .eq(WfTask::getTenantId, tenantId)
                            .in(WfTask::getInstanceId, instanceIds)
                            .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                            .orderByDesc(WfTask::getReceivedAt));
            pendingApprovalCount = projectPendingTasks.size();
            pendingApprovals = projectPendingTasks.stream().limit(10).map(t -> {
                DashboardTaskItemVO item = new DashboardTaskItemVO();
                item.setTaskId(String.valueOf(t.getId()));
                item.setInstanceId(String.valueOf(t.getInstanceId()));
                item.setBusinessType(t.getBusinessType());
                item.setTaskStatus(t.getTaskStatus());
                if (t.getReceivedAt() != null) item.setReceivedAt(DateTimeUtils.DTF.format(t.getReceivedAt()));
                WfInstance inst = instanceMap.get(t.getInstanceId());
                if (inst != null) {
                    item.setTitle(inst.getTitle());
                    item.setProjectId(inst.getProjectId() != null ? String.valueOf(inst.getProjectId()) : null);
                }
                return item;
            }).collect(Collectors.toList());
        }
        vo.setPendingApprovals(pendingApprovals);
        vo.setPendingApprovalCount(pendingApprovalCount);

        // Expiring contracts (end date within 30 days)
        LocalDate cutoff = LocalDate.now().plusDays(30);
        List<CtContract> expiringContracts = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .eq(CtContract::getProjectId, projectId)
                        .le(CtContract::getEndDate, cutoff)
                        .ge(CtContract::getEndDate, LocalDate.now())
                        .eq(CtContract::getContractStatus, "PERFORMING"));
        vo.setExpiringContracts(expiringContracts.stream().map(this::toContractItem).collect(Collectors.toList()));
        vo.setExpiringContractCount((long) expiringContracts.size());

        return vo;
    }

    // ========================================================================
    // 2. Business Manager Dashboard
    // ========================================================================
    public BusinessManagerDashboardVO getBusinessManagerView(Long projectId) {
        Long tenantId = UserContext.getCurrentTenantId();

        if (projectId == null) {
            return getBusinessManagerViewAllProjects(tenantId);
        }

        PmProject project = requireProject(tenantId, projectId);

        BusinessManagerDashboardVO vo = new BusinessManagerDashboardVO();
        vo.setProjectId(projectId.toString());
        vo.setProjectName(project.getProjectName());

        // Contract totals
        List<CtContract> contracts = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .eq(CtContract::getProjectId, projectId));
        BigDecimal totalContractAmount = contracts.stream()
                .map(c -> c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCurrentAmount = contracts.stream()
                .map(c -> c.getCurrentAmount() != null ? c.getCurrentAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaidAmount = contracts.stream()
                .map(c -> c.getPaidAmount() != null ? c.getPaidAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        vo.setTotalContractAmount(totalContractAmount.toPlainString());

        // Contract change amount = current - original
        BigDecimal changeAmount = totalCurrentAmount.subtract(totalContractAmount);
        vo.setContractChangeAmount(changeAmount.toPlainString());

        // Payment ratio
        if (totalContractAmount.compareTo(BigDecimal.ZERO) > 0) {
            vo.setPaidRatio(totalPaidAmount.divide(totalContractAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%");
        } else {
            vo.setPaidRatio("0%");
        }

        // Settlement progress
        List<StlSettlement> settlements = stlSettlementMapper.selectList(
                new LambdaQueryWrapper<StlSettlement>()
                        .eq(StlSettlement::getTenantId, tenantId)
                        .eq(StlSettlement::getProjectId, projectId));
        long finalizedCount = settlements.stream().filter(s -> "FINALIZED".equals(s.getSettlementStatus())).count();
        vo.setSettlementProgress(settlements.isEmpty() ? "0/0" : finalizedCount + "/" + settlements.size());

        // Var order amount: SUM(approvedAmount) WHERE approvalStatus='APPROVED'
        BigDecimal varOrderTotal = varOrderMapper.selectList(
                new LambdaQueryWrapper<VarOrder>()
                        .eq(VarOrder::getTenantId, tenantId)
                        .eq(VarOrder::getProjectId, projectId)
                        .eq(VarOrder::getApprovalStatus, "APPROVED"))
                .stream()
                .map(v -> v.getApprovedAmount() != null ? v.getApprovedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setVarOrderAmount(varOrderTotal.toPlainString());

        // Sub measure amount: SUM(approvedAmount) WHERE approvalStatus='APPROVED'
        BigDecimal subMeasureTotal = subMeasureMapper.selectList(
                new LambdaQueryWrapper<SubMeasure>()
                        .eq(SubMeasure::getTenantId, tenantId)
                        .eq(SubMeasure::getProjectId, projectId)
                        .eq(SubMeasure::getApprovalStatus, "APPROVED"))
                .stream()
                .map(s -> s.getApprovedAmount() != null ? s.getApprovedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setSubMeasureAmount(subMeasureTotal.toPlainString());

        // Recent changes (top 5 contracts by currentAmount)
        vo.setRecentChanges(contracts.stream()
                .sorted(Comparator.comparing(c -> c.getCurrentAmount() != null ? c.getCurrentAmount() : BigDecimal.ZERO,
                        Comparator.reverseOrder()))
                .limit(5)
                .map(this::toContractItem)
                .collect(Collectors.toList()));

        // Settlement items
        vo.setSettlementItems(settlements.stream().map(s -> {
            DashboardProjectSummaryVO item = new DashboardProjectSummaryVO();
            item.setProjectId(String.valueOf(s.getProjectId()));
            item.setProjectName(project.getProjectName());
            item.setStatus(s.getSettlementStatus());
            return item;
        }).collect(Collectors.toList()));

        return vo;
    }

    // ========================================================================
    // 3. Cost Manager Dashboard
    // ========================================================================
    public CostManagerDashboardVO getCostManagerView(Long projectId) {
        return getCostManagerView(projectId, null);
    }

    public CostManagerDashboardVO getCostManagerView(Long projectId, String month) {
        Long tenantId = UserContext.getCurrentTenantId();
        YearMonth selectedMonth = parseDashboardMonth(month);

        if (projectId == null) {
            return getCostManagerViewAllProjects(tenantId, selectedMonth);
        }

        PmProject project = requireProject(tenantId, projectId);

        // Use pre-aggregated cost_summary via existing service
        CostProjectSummaryVO summary = selectedMonth == null ? costSummaryService.getProjectSummary(tenantId, projectId) : null;
        CostSummary projectLevelSummary = findLatestProjectLevelSummary(tenantId, projectId, selectedMonth);

        CostManagerDashboardVO vo = new CostManagerDashboardVO();
        vo.setProjectId(projectId.toString());
        vo.setProjectName(project.getProjectName());
        applyCostSummaryKpis(vo, summary, projectLevelSummary);

        // Over-budget alerts from alert_log (cost-exceeds-target + material-exceeds-budget)
        LambdaQueryWrapper<AlertLog> alertQuery = new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, tenantId)
                .eq(AlertLog::getProjectId, projectId)
                .in(AlertLog::getRuleType, Set.of("DYNAMIC_COST_EXCEEDS_TARGET", "MATERIAL_EXCEEDS_BUDGET"));
        applyMonthDateTimeRange(alertQuery, selectedMonth, AlertLog::getTriggeredAt);
        List<AlertLog> overBudgetAlerts = alertLogMapper.selectList(
                alertQuery.orderByDesc(AlertLog::getTriggeredAt).last("limit 5"));
        vo.setOverBudgetAlerts(overBudgetAlerts.stream().map(alert -> {
            DashboardAlertItemVO item = toAlertItem(alert);
            item.setProjectName(project.getProjectName());
            return item;
        }).collect(Collectors.toList()));
        fillCostManagerDetails(vo, tenantId, List.of(project), List.of(projectId), selectedMonth);

        return vo;
    }

    // ========================================================================
    // 4. Finance Dashboard
    // ========================================================================
    public FinanceDashboardVO getFinanceView(Long projectId) {
        Long tenantId = UserContext.getCurrentTenantId();

        if (projectId == null) {
            return getFinanceViewAllProjects(tenantId);
        }

        PmProject project = requireProject(tenantId, projectId);

        FinanceDashboardVO vo = new FinanceDashboardVO();
        vo.setProjectId(projectId.toString());
        vo.setProjectName(project.getProjectName());

        // Pay records
        List<PayRecord> payRecords = payRecordMapper.selectList(
                new LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getTenantId, tenantId)
                        .eq(PayRecord::getProjectId, projectId));

        // Pending payments
        List<PayRecord> pendingPayments = payRecords.stream()
                .filter(p -> !"SUCCESS".equals(p.getPayStatus()))
                .collect(Collectors.toList());
        BigDecimal pendingAmount = pendingPayments.stream()
                .map(p -> p.getPayAmount() != null ? p.getPayAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setPendingPaymentAmount(pendingAmount.toPlainString());
        vo.setPendingPaymentCount((long) pendingPayments.size());

        // Approved unpaid (SUCCESS status = paid, so approved unpaid = non-SUCCESS)
        vo.setApprovedUnpaidAmount(pendingAmount.toPlainString());

        // Over-ratio payments: SUM of excess where SUCCESS paid > contract_amount
        BigDecimal overRatioTotal = BigDecimal.ZERO;
        List<PayRecord> successRecords = payRecords.stream()
                .filter(p -> "SUCCESS".equals(p.getPayStatus()) && p.getContractId() != null)
                .collect(Collectors.toList());
        if (!successRecords.isEmpty()) {
            Map<Long, BigDecimal> paidByContract = new HashMap<>();
            for (PayRecord r : successRecords) {
                paidByContract.merge(r.getContractId(),
                        r.getPayAmount() != null ? r.getPayAmount() : BigDecimal.ZERO, BigDecimal::add);
            }
            List<CtContract> relatedContracts = ctContractMapper.selectList(
                    new LambdaQueryWrapper<CtContract>()
                            .eq(CtContract::getTenantId, tenantId)
                            .eq(CtContract::getProjectId, projectId)
                            .in(CtContract::getId, paidByContract.keySet()));
            Map<Long, CtContract> contractMap = relatedContracts.stream()
                    .collect(Collectors.toMap(CtContract::getId, c -> c));
            for (Map.Entry<Long, BigDecimal> entry : paidByContract.entrySet()) {
                CtContract contract = contractMap.get(entry.getKey());
                if (contract == null) continue;
                BigDecimal contractAmount = contract.getContractAmount() != null ? contract.getContractAmount() : BigDecimal.ZERO;
                if (contractAmount.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (entry.getValue().compareTo(contractAmount) > 0) {
                    overRatioTotal = overRatioTotal.add(entry.getValue().subtract(contractAmount));
                }
            }
        }
        vo.setOverRatioAmount(overRatioTotal.toPlainString());

        // Warranty expiring: SUM contractAmount WHERE endDate within 30 days
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(30);
        BigDecimal warrantyExpiringTotal = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .eq(CtContract::getProjectId, projectId)
                        .eq(CtContract::getContractStatus, "PERFORMING")
                        .ge(CtContract::getEndDate, today)
                        .le(CtContract::getEndDate, threshold))
                .stream()
                .map(c -> c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setWarrantyExpiringAmount(warrantyExpiringTotal.toPlainString());

        // Detail lists
        vo.setPendingPayments(pendingPayments.stream().limit(20).map(p -> {
            DashboardPaymentItemVO item = new DashboardPaymentItemVO();
            item.setPayRecordId(String.valueOf(p.getId()));
            item.setContractId(p.getContractId() != null ? String.valueOf(p.getContractId()) : null);
            item.setPayAmount(p.getPayAmount() != null ? p.getPayAmount().toPlainString() : "0");
            item.setPayDate(p.getPayDate() != null ? p.getPayDate().toString() : null);
            item.setPayStatus(p.getPayStatus());
            item.setProjectId(String.valueOf(p.getProjectId()));
            item.setProjectName(project.getProjectName());
            return item;
        }).collect(Collectors.toList()));

        vo.setOverRatioPayments(Collections.emptyList());

        return vo;
    }

    // ========================================================================
    // 5. Management Dashboard (tenant-wide)
    // ========================================================================
    public ManagementDashboardVO getManagementView() {
        Long tenantId = UserContext.getCurrentTenantId();

        ManagementDashboardVO vo = new ManagementDashboardVO();

        // Active projects
        List<PmProject> activeProjects = projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE"));
        vo.setActiveProjectCount((long) activeProjects.size());

        // Aggregate totals across all active projects
        BigDecimal totalContractAmount = BigDecimal.ZERO;
        BigDecimal totalDynamicCost = BigDecimal.ZERO;
        BigDecimal totalExpectedProfit = BigDecimal.ZERO;
        BigDecimal totalPaidAmount = BigDecimal.ZERO;

        List<DashboardProjectSummaryVO> rankings = new ArrayList<>();

        // Batch load all project summaries to avoid N+1 per-project queries
        List<Long> projectIds = activeProjects.stream().map(PmProject::getId).collect(Collectors.toList());
        Map<Long, CostProjectSummaryVO> summaryMap = costSummaryService.getBatchProjectSummaries(tenantId, projectIds);

        for (PmProject project : activeProjects) {
            CostProjectSummaryVO summary = summaryMap.get(project.getId());
            if (summary == null) {
                log.warn("No summary found for project {}", project.getId());
                continue;
            }

            DashboardProjectSummaryVO rank = new DashboardProjectSummaryVO();
            rank.setProjectId(String.valueOf(project.getId()));
            rank.setProjectName(project.getProjectName());
            rank.setProjectCode(project.getProjectCode());
            rank.setStatus(project.getStatus());
            rank.setTargetCost(summary.getTargetCost());
            rank.setDynamicCost(summary.getDynamicCost());
            rank.setContractIncome(summary.getContractIncome());
            rank.setExpectedProfit(summary.getExpectedProfit());
            rank.setCostDeviation(summary.getCostDeviation());
            rank.setPaidAmount(summary.getPaidAmount());
            rank.setContractAmount(summary.getContractIncome());

            rankings.add(rank);

            totalContractAmount = totalContractAmount.add(
                    summary.getContractIncome() != null ? new BigDecimal(summary.getContractIncome()) : BigDecimal.ZERO);
            totalDynamicCost = totalDynamicCost.add(
                    summary.getDynamicCost() != null ? new BigDecimal(summary.getDynamicCost()) : BigDecimal.ZERO);
            totalExpectedProfit = totalExpectedProfit.add(
                    summary.getExpectedProfit() != null ? new BigDecimal(summary.getExpectedProfit()) : BigDecimal.ZERO);
            totalPaidAmount = totalPaidAmount.add(
                    summary.getPaidAmount() != null ? new BigDecimal(summary.getPaidAmount()) : BigDecimal.ZERO);
        }

        vo.setTotalContractAmount(totalContractAmount.toPlainString());
        vo.setTotalDynamicCost(totalDynamicCost.toPlainString());
        vo.setTotalExpectedProfit(totalExpectedProfit.toPlainString());
        vo.setTotalPaidAmount(totalPaidAmount.toPlainString());

        // Rank by expected profit descending
        rankings.sort(Comparator.comparing(
                r -> new BigDecimal(r.getExpectedProfit() != null ? r.getExpectedProfit() : "0"),
                Comparator.reverseOrder()));
        vo.setProjectRankings(rankings);

        // Pending tasks count (tenant-wide)
        Long currentUserId = UserContext.getCurrentUserId();
        List<WfTask> allPending = wfTaskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getTenantId, tenantId)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                        .orderByDesc(WfTask::getReceivedAt));
        vo.setTotalPendingTaskCount((long) allPending.size());

        // Overdue items: pending tasks older than 7 days
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<DashboardTaskItemVO> overdueItems = allPending.stream()
                .filter(t -> t.getReceivedAt() != null && t.getReceivedAt().isBefore(sevenDaysAgo))
                .limit(20)
                .map(t -> {
                    DashboardTaskItemVO item = new DashboardTaskItemVO();
                    item.setTaskId(String.valueOf(t.getId()));
                    item.setInstanceId(String.valueOf(t.getInstanceId()));
                    item.setBusinessType(t.getBusinessType());
                    item.setTaskStatus(t.getTaskStatus());
                    if (t.getReceivedAt() != null) item.setReceivedAt(DateTimeUtils.DTF.format(t.getReceivedAt()));
                    return item;
                }).collect(Collectors.toList());
        vo.setOverdueItems(overdueItems);

        // Risks from alert_log: HIGH severity unread alerts tenant-wide
        List<AlertLog> highAlerts = alertLogMapper.selectList(
                new LambdaQueryWrapper<AlertLog>()
                        .eq(AlertLog::getTenantId, tenantId)
                        .eq(AlertLog::getIsRead, 0)
                        .eq(AlertLog::getSeverity, "HIGH")
                        .orderByDesc(AlertLog::getTriggeredAt));
        vo.setTotalRiskCount((long) highAlerts.size());
        vo.setMajorRisks(highAlerts.stream().limit(10).map(this::toAlertItem).collect(Collectors.toList()));

        return vo;
    }

    // ========================================================================
    // 6. Cost Breakdown Drill-down (by cost subject, max 2 levels)
    // ========================================================================
    public CostBreakdownVO getCostBreakdown(Long projectId) {
        Long tenantId = UserContext.getCurrentTenantId();
        PmProject project = requireProject(tenantId, projectId);

        // Get project-level summary
        CostProjectSummaryVO projectSummary = costSummaryService.getProjectSummary(tenantId, projectId);

        CostBreakdownVO vo = new CostBreakdownVO();
        vo.setProjectId(projectId.toString());
        vo.setProjectName(project.getProjectName());
        vo.setTargetCost(projectSummary.getTargetCost());
        vo.setDynamicCost(projectSummary.getDynamicCost());
        vo.setExpectedProfit(projectSummary.getExpectedProfit());

        // Drill-down: group cost_summary by cost_subject_id (level 1 only)
        List<CostSummary> summaries = costSummaryMapper.selectList(
                new LambdaQueryWrapper<CostSummary>()
                        .eq(CostSummary::getTenantId, tenantId)
                        .eq(CostSummary::getProjectId, projectId)
                        .isNotNull(CostSummary::getCostSubjectId));

        if (CollectionUtils.isEmpty(summaries)) {
            vo.setSubjectBreakdowns(Collections.emptyList());
            return vo;
        }

        // Load cost subjects
        Set<Long> subjectIds = summaries.stream()
                .map(CostSummary::getCostSubjectId)
                .collect(Collectors.toSet());
        Map<Long, CostSubject> subjectMap = Collections.emptyMap();
        if (!subjectIds.isEmpty()) {
            List<CostSubject> subjects = costSubjectMapper.selectBatchIds(subjectIds);
            subjectMap = subjects.stream().collect(Collectors.toMap(CostSubject::getId, s -> s, (a, b) -> a));
        }

        // Build subject breakdowns (max level 2 from the tree)
        List<CostBreakdownVO.SubjectBreakdown> breakdowns = new ArrayList<>();
        for (CostSummary s : summaries) {
            CostBreakdownVO.SubjectBreakdown bd = new CostBreakdownVO.SubjectBreakdown();
            bd.setCostSubjectId(s.getCostSubjectId() != null ? String.valueOf(s.getCostSubjectId()) : null);
            CostSubject subject = subjectMap.get(s.getCostSubjectId());
            bd.setCostSubjectName(subject != null ? subject.getSubjectName() : "");
            bd.setLevel(subject != null ? subject.getLevel() : 1);
            bd.setParentSubjectId(subject != null && subject.getParentId() != null
                    ? String.valueOf(subject.getParentId()) : null);
            bd.setTargetCost(s.getTargetCost() != null ? s.getTargetCost().toPlainString() : "0");
            bd.setContractLockedCost(s.getContractLockedCost() != null ? s.getContractLockedCost().toPlainString() : "0");
            bd.setActualCost(s.getActualCost() != null ? s.getActualCost().toPlainString() : "0");
            bd.setDynamicCost(s.getDynamicCost() != null ? s.getDynamicCost().toPlainString() : "0");
            bd.setCostDeviation(s.getCostDeviation() != null ? s.getCostDeviation().toPlainString() : "0");
            breakdowns.add(bd);
        }

        // Enforce max 2 levels: only include level <= 2
        breakdowns = breakdowns.stream()
                .filter(bd -> bd.getLevel() != null && bd.getLevel() <= 2)
                .sorted(Comparator.comparingInt(bd -> bd.getLevel() != null ? bd.getLevel() : 99))
                .collect(Collectors.toList());

        vo.setSubjectBreakdowns(breakdowns);
        return vo;
    }

    // ========================================================================
    // All-projects aggregated views (projectId == null)
    // ========================================================================

    private ProjectManagerDashboardVO getProjectManagerViewAllProjects(Long tenantId) {
        ProjectManagerDashboardVO vo = new ProjectManagerDashboardVO();
        vo.setProjectId(null);
        vo.setProjectName("全部项目");

        List<PmProject> activeProjects = projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE"));

        // Pending tasks for current user (tenant-wide, already not scoped to project)
        Long currentUserId = UserContext.getCurrentUserId();
        List<WfTask> pendingTasks = wfTaskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getTenantId, tenantId)
                        .eq(WfTask::getApproverId, currentUserId)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                        .orderByDesc(WfTask::getReceivedAt));

        Map<Long, WfInstance> instanceMap = batchLoadInstances(pendingTasks);

        List<DashboardTaskItemVO> taskItems = pendingTasks.stream().map(t -> {
            DashboardTaskItemVO item = new DashboardTaskItemVO();
            item.setTaskId(String.valueOf(t.getId()));
            item.setInstanceId(String.valueOf(t.getInstanceId()));
            item.setBusinessType(t.getBusinessType());
            item.setBusinessId(String.valueOf(t.getBusinessId()));
            item.setTaskStatus(t.getTaskStatus());
            if (t.getReceivedAt() != null) item.setReceivedAt(DateTimeUtils.DTF.format(t.getReceivedAt()));
            WfInstance inst = instanceMap.get(t.getInstanceId());
            if (inst != null) {
                item.setTitle(inst.getTitle());
                item.setProjectId(inst.getProjectId() != null ? String.valueOf(inst.getProjectId()) : null);
            }
            return item;
        }).collect(Collectors.toList());
        vo.setPendingTasks(taskItems);
        vo.setPendingTaskCount((long) taskItems.size());

        // Lagging projects: all active projects with planned end date in the past
        List<DashboardProjectSummaryVO> lagging = activeProjects.stream()
                .filter(p -> p.getPlannedEndDate() != null && p.getPlannedEndDate().isBefore(LocalDate.now()))
                .map(this::toProjectSummary)
                .collect(Collectors.toList());
        vo.setLaggingProjects(lagging);
        vo.setLaggingProjectCount((long) lagging.size());

        // Pending approvals: tenant-wide pending tasks
        List<WfTask> allPendingApprovals = wfTaskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getTenantId, tenantId)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                        .orderByDesc(WfTask::getReceivedAt));
        long pendingApprovalCount = allPendingApprovals.size();
        List<DashboardTaskItemVO> pendingApprovals = allPendingApprovals.stream().limit(10).map(t -> {
            DashboardTaskItemVO item = new DashboardTaskItemVO();
            item.setTaskId(String.valueOf(t.getId()));
            item.setInstanceId(String.valueOf(t.getInstanceId()));
            item.setBusinessType(t.getBusinessType());
            item.setTaskStatus(t.getTaskStatus());
            if (t.getReceivedAt() != null) item.setReceivedAt(DateTimeUtils.DTF.format(t.getReceivedAt()));
            WfInstance inst = instanceMap.get(t.getInstanceId());
            if (inst != null) {
                item.setTitle(inst.getTitle());
                item.setProjectId(inst.getProjectId() != null ? String.valueOf(inst.getProjectId()) : null);
            }
            return item;
        }).collect(Collectors.toList());
        vo.setPendingApprovals(pendingApprovals);
        vo.setPendingApprovalCount(pendingApprovalCount);

        // Expiring contracts: tenant-wide within 30 days
        LocalDate cutoff = LocalDate.now().plusDays(30);
        List<CtContract> expiringContracts = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .le(CtContract::getEndDate, cutoff)
                        .ge(CtContract::getEndDate, LocalDate.now())
                        .eq(CtContract::getContractStatus, "PERFORMING"));
        vo.setExpiringContracts(expiringContracts.stream().map(this::toContractItem).collect(Collectors.toList()));
        vo.setExpiringContractCount((long) expiringContracts.size());

        return vo;
    }

    private BusinessManagerDashboardVO getBusinessManagerViewAllProjects(Long tenantId) {
        List<PmProject> activeProjects = projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE"));
        List<Long> projectIds = activeProjects.stream().map(PmProject::getId).collect(Collectors.toList());

        BusinessManagerDashboardVO vo = new BusinessManagerDashboardVO();
        vo.setProjectId(null);
        vo.setProjectName("全部项目");

        if (projectIds.isEmpty()) {
            vo.setTotalContractAmount("0");
            vo.setContractChangeAmount("0");
            vo.setVarOrderAmount("0");
            vo.setSubMeasureAmount("0");
            vo.setPaidRatio("0%");
            vo.setSettlementProgress("0/0");
            vo.setRecentChanges(Collections.emptyList());
            vo.setSettlementItems(Collections.emptyList());
            return vo;
        }

        // Contract totals — tenant-wide across all active projects
        List<CtContract> allContracts = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .in(CtContract::getProjectId, projectIds));
        BigDecimal totalContractAmount = BigDecimal.ZERO;
        BigDecimal totalCurrentAmount = BigDecimal.ZERO;
        BigDecimal totalPaidAmount = BigDecimal.ZERO;
        for (CtContract c : allContracts) {
            totalContractAmount = totalContractAmount.add(c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO);
            totalCurrentAmount = totalCurrentAmount.add(c.getCurrentAmount() != null ? c.getCurrentAmount() : BigDecimal.ZERO);
            totalPaidAmount = totalPaidAmount.add(c.getPaidAmount() != null ? c.getPaidAmount() : BigDecimal.ZERO);
        }
        vo.setTotalContractAmount(totalContractAmount.toPlainString());
        BigDecimal changeAmount = totalCurrentAmount.subtract(totalContractAmount);
        vo.setContractChangeAmount(changeAmount.toPlainString());
        if (totalContractAmount.compareTo(BigDecimal.ZERO) > 0) {
            vo.setPaidRatio(totalPaidAmount.divide(totalContractAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%");
        } else {
            vo.setPaidRatio("0%");
        }

        // Settlement progress — tenant-wide
        List<StlSettlement> allSettlements = stlSettlementMapper.selectList(
                new LambdaQueryWrapper<StlSettlement>()
                        .eq(StlSettlement::getTenantId, tenantId)
                        .in(StlSettlement::getProjectId, projectIds));
        long finalizedCount = allSettlements.stream().filter(s -> "FINALIZED".equals(s.getSettlementStatus())).count();
        vo.setSettlementProgress(allSettlements.isEmpty() ? "0/0" : finalizedCount + "/" + allSettlements.size());

        // Var order amount — tenant-wide
        BigDecimal varOrderTotal = varOrderMapper.selectList(
                new LambdaQueryWrapper<VarOrder>()
                        .eq(VarOrder::getTenantId, tenantId)
                        .in(VarOrder::getProjectId, projectIds)
                        .eq(VarOrder::getApprovalStatus, "APPROVED"))
                .stream()
                .map(v -> v.getApprovedAmount() != null ? v.getApprovedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setVarOrderAmount(varOrderTotal.toPlainString());

        // Sub measure amount — tenant-wide
        BigDecimal subMeasureTotal = subMeasureMapper.selectList(
                new LambdaQueryWrapper<SubMeasure>()
                        .eq(SubMeasure::getTenantId, tenantId)
                        .in(SubMeasure::getProjectId, projectIds)
                        .eq(SubMeasure::getApprovalStatus, "APPROVED"))
                .stream()
                .map(s -> s.getApprovedAmount() != null ? s.getApprovedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setSubMeasureAmount(subMeasureTotal.toPlainString());

        // Recent changes: top 5 contracts by currentAmount
        vo.setRecentChanges(allContracts.stream()
                .sorted(Comparator.comparing(c -> c.getCurrentAmount() != null ? c.getCurrentAmount() : BigDecimal.ZERO,
                        Comparator.reverseOrder()))
                .limit(5)
                .map(this::toContractItem)
                .collect(Collectors.toList()));

        // Settlement items
        vo.setSettlementItems(allSettlements.stream().map(s -> {
            DashboardProjectSummaryVO item = new DashboardProjectSummaryVO();
            item.setProjectId(String.valueOf(s.getProjectId()));
            item.setStatus(s.getSettlementStatus());
            return item;
        }).collect(Collectors.toList()));

        return vo;
    }

    private CostManagerDashboardVO getCostManagerViewAllProjects(Long tenantId, YearMonth selectedMonth) {
        List<PmProject> activeProjects = projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE"));
        List<Long> projectIds = activeProjects.stream().map(PmProject::getId).collect(Collectors.toList());

        CostManagerDashboardVO vo = new CostManagerDashboardVO();
        vo.setProjectId(null);
        vo.setProjectName("全部项目");

        if (projectIds.isEmpty()) {
            vo.setTargetCost("0");
            vo.setDynamicCost("0");
            vo.setCostDeviation("0");
            vo.setContractLockedCost("0");
            vo.setActualCost("0");
            vo.setEstimatedRemainingCost("0");
            vo.setExpectedProfit("0");
            vo.setContractIncome("0");
            applyEmptyCostManagerLists(vo);
            return vo;
        }

        Map<Long, CostProjectSummaryVO> summaryMap = selectedMonth == null
                ? costSummaryService.getBatchProjectSummaries(tenantId, projectIds)
                : Collections.emptyMap();
        Map<Long, CostSummary> projectLevelSummaryMap = loadLatestProjectLevelSummaries(tenantId, projectIds, selectedMonth);

        BigDecimal totalTargetCost = BigDecimal.ZERO;
        BigDecimal totalDynamicCost = BigDecimal.ZERO;
        BigDecimal totalContractLockedCost = BigDecimal.ZERO;
        BigDecimal totalActualCost = BigDecimal.ZERO;
        BigDecimal totalEstimatedRemainingCost = BigDecimal.ZERO;
        BigDecimal totalExpectedProfit = BigDecimal.ZERO;
        BigDecimal totalContractIncome = BigDecimal.ZERO;

        for (Long activeProjectId : projectIds) {
            CostSummary projectLevel = projectLevelSummaryMap.get(activeProjectId);
            CostProjectSummaryVO s = summaryMap.get(activeProjectId);
            totalTargetCost = totalTargetCost.add(projectLevel != null && projectLevel.getTargetCost() != null ? projectLevel.getTargetCost() : toBigDecimal(s != null ? s.getTargetCost() : null));
            totalDynamicCost = totalDynamicCost.add(projectLevel != null && projectLevel.getDynamicCost() != null ? projectLevel.getDynamicCost() : toBigDecimal(s != null ? s.getDynamicCost() : null));
            totalContractLockedCost = totalContractLockedCost.add(projectLevel != null && projectLevel.getContractLockedCost() != null ? projectLevel.getContractLockedCost() : toBigDecimal(s != null ? s.getContractLockedCost() : null));
            totalActualCost = totalActualCost.add(projectLevel != null && projectLevel.getActualCost() != null ? projectLevel.getActualCost() : toBigDecimal(s != null ? s.getActualCost() : null));
            totalEstimatedRemainingCost = totalEstimatedRemainingCost.add(projectLevel != null && projectLevel.getEstimatedRemainingCost() != null ? projectLevel.getEstimatedRemainingCost() : toBigDecimal(s != null ? s.getEstimatedRemainingCost() : null));
            totalExpectedProfit = totalExpectedProfit.add(projectLevel != null && projectLevel.getExpectedProfit() != null ? projectLevel.getExpectedProfit() : toBigDecimal(s != null ? s.getExpectedProfit() : null));
            totalContractIncome = totalContractIncome.add(projectLevel != null && projectLevel.getContractIncome() != null ? projectLevel.getContractIncome() : toBigDecimal(s != null ? s.getContractIncome() : null));
        }

        BigDecimal costDeviation = totalDynamicCost.subtract(totalTargetCost);

        vo.setTargetCost(totalTargetCost.toPlainString());
        vo.setDynamicCost(totalDynamicCost.toPlainString());
        vo.setCostDeviation(costDeviation.toPlainString());
        vo.setContractLockedCost(totalContractLockedCost.toPlainString());
        vo.setActualCost(totalActualCost.toPlainString());
        vo.setEstimatedRemainingCost(totalEstimatedRemainingCost.toPlainString());
        vo.setExpectedProfit(totalExpectedProfit.toPlainString());
        vo.setContractIncome(totalContractIncome.toPlainString());

        // Over-budget alerts — tenant-wide
        LambdaQueryWrapper<AlertLog> alertQuery = new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, tenantId)
                .in(AlertLog::getRuleType, Set.of("DYNAMIC_COST_EXCEEDS_TARGET", "MATERIAL_EXCEEDS_BUDGET"));
        applyMonthDateTimeRange(alertQuery, selectedMonth, AlertLog::getTriggeredAt);
        List<AlertLog> overBudgetAlerts = alertLogMapper.selectList(
                alertQuery.orderByDesc(AlertLog::getTriggeredAt).last("limit 5"));
        Map<Long, String> activeProjectNames = activeProjects.stream()
                .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        vo.setOverBudgetAlerts(overBudgetAlerts.stream().map(alert -> {
            DashboardAlertItemVO item = toAlertItem(alert);
            item.setProjectName(activeProjectNames.get(alert.getProjectId()));
            return item;
        }).collect(Collectors.toList()));
        fillCostManagerDetails(vo, tenantId, activeProjects, projectIds, selectedMonth);

        return vo;
    }

    private FinanceDashboardVO getFinanceViewAllProjects(Long tenantId) {
        List<PmProject> activeProjects = projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE"));
        List<Long> projectIds = activeProjects.stream().map(PmProject::getId).collect(Collectors.toList());

        FinanceDashboardVO vo = new FinanceDashboardVO();
        vo.setProjectId(null);
        vo.setProjectName("全部项目");

        if (projectIds.isEmpty()) {
            vo.setPendingPaymentAmount("0");
            vo.setPendingPaymentCount(0L);
            vo.setApprovedUnpaidAmount("0");
            vo.setOverRatioAmount("0");
            vo.setWarrantyExpiringAmount("0");
            vo.setPendingPayments(Collections.emptyList());
            vo.setOverRatioPayments(Collections.emptyList());
            return vo;
        }

        // Pay records — tenant-wide
        List<PayRecord> allPayRecords = payRecordMapper.selectList(
                new LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getTenantId, tenantId)
                        .in(PayRecord::getProjectId, projectIds));

        // Pending payments
        List<PayRecord> pendingPayments = allPayRecords.stream()
                .filter(p -> !"SUCCESS".equals(p.getPayStatus()))
                .collect(Collectors.toList());
        BigDecimal pendingAmount = pendingPayments.stream()
                .map(p -> p.getPayAmount() != null ? p.getPayAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setPendingPaymentAmount(pendingAmount.toPlainString());
        vo.setPendingPaymentCount((long) pendingPayments.size());
        vo.setApprovedUnpaidAmount(pendingAmount.toPlainString());

        // Over-ratio payments
        BigDecimal overRatioTotal = BigDecimal.ZERO;
        List<PayRecord> successRecords = allPayRecords.stream()
                .filter(p -> "SUCCESS".equals(p.getPayStatus()) && p.getContractId() != null)
                .collect(Collectors.toList());
        if (!successRecords.isEmpty()) {
            Map<Long, BigDecimal> paidByContract = new HashMap<>();
            for (PayRecord r : successRecords) {
                paidByContract.merge(r.getContractId(),
                        r.getPayAmount() != null ? r.getPayAmount() : BigDecimal.ZERO, BigDecimal::add);
            }
            List<CtContract> relatedContracts = ctContractMapper.selectList(
                    new LambdaQueryWrapper<CtContract>()
                            .eq(CtContract::getTenantId, tenantId)
                            .in(CtContract::getProjectId, projectIds)
                            .in(CtContract::getId, paidByContract.keySet()));
            Map<Long, CtContract> contractMap = relatedContracts.stream()
                    .collect(Collectors.toMap(CtContract::getId, c -> c));
            for (Map.Entry<Long, BigDecimal> entry : paidByContract.entrySet()) {
                CtContract contract = contractMap.get(entry.getKey());
                if (contract == null) continue;
                BigDecimal contractAmount = contract.getContractAmount() != null ? contract.getContractAmount() : BigDecimal.ZERO;
                if (contractAmount.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (entry.getValue().compareTo(contractAmount) > 0) {
                    overRatioTotal = overRatioTotal.add(entry.getValue().subtract(contractAmount));
                }
            }
        }
        vo.setOverRatioAmount(overRatioTotal.toPlainString());

        // Warranty expiring
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(30);
        BigDecimal warrantyExpiringTotal = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .in(CtContract::getProjectId, projectIds)
                        .eq(CtContract::getContractStatus, "PERFORMING")
                        .ge(CtContract::getEndDate, today)
                        .le(CtContract::getEndDate, threshold))
                .stream()
                .map(c -> c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setWarrantyExpiringAmount(warrantyExpiringTotal.toPlainString());

        // Detail lists
        Map<Long, String> projectNameMap = activeProjects.stream()
                .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        vo.setPendingPayments(pendingPayments.stream().limit(20).map(p -> {
            DashboardPaymentItemVO item = new DashboardPaymentItemVO();
            item.setPayRecordId(String.valueOf(p.getId()));
            item.setContractId(p.getContractId() != null ? String.valueOf(p.getContractId()) : null);
            item.setPayAmount(p.getPayAmount() != null ? p.getPayAmount().toPlainString() : "0");
            item.setPayDate(p.getPayDate() != null ? p.getPayDate().toString() : null);
            item.setPayStatus(p.getPayStatus());
            item.setProjectId(String.valueOf(p.getProjectId()));
            item.setProjectName(projectNameMap.getOrDefault(p.getProjectId(), ""));
            return item;
        }).collect(Collectors.toList()));
        vo.setOverRatioPayments(Collections.emptyList());

        return vo;
    }

    private void fillCostManagerDetails(CostManagerDashboardVO vo, Long tenantId, List<PmProject> projects, List<Long> projectIds, YearMonth selectedMonth) {
        if (CollectionUtils.isEmpty(projectIds)) {
            applyEmptyCostManagerLists(vo);
            return;
        }

        Map<Long, String> projectNameMap = projects.stream()
                .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));

        LambdaQueryWrapper<CostSummary> summaryQuery = new LambdaQueryWrapper<CostSummary>()
                .eq(CostSummary::getTenantId, tenantId)
                .in(CostSummary::getProjectId, projectIds);
        if (selectedMonth != null) {
            summaryQuery.le(CostSummary::getSummaryDate, selectedMonth.atEndOfMonth());
        }
        List<CostSummary> summaries = costSummaryMapper.selectList(summaryQuery);

        List<CostSummary> projectSummaries = summaries.stream()
                .filter(s -> s.getCostSubjectId() == null)
                .collect(Collectors.toList());
        vo.setTrendPoints(toCostTrendPoints(projectSummaries));

        List<CostSummary> subjectSummaries = summaries.stream()
                .filter(s -> s.getCostSubjectId() != null)
                .collect(Collectors.toList());
        if (selectedMonth != null) {
            subjectSummaries = latestSubjectSummaries(subjectSummaries);
        }
        Map<Long, CostSubject> subjectMap = loadCostSubjects(subjectSummaries);
        List<CostManagerDashboardVO.SubjectRanking> subjectRankings = toSubjectRankings(subjectSummaries, subjectMap);
        if (CollectionUtils.isEmpty(subjectRankings) && selectedMonth == null) {
            subjectRankings = toSubjectRankingsFromCostItems(tenantId, projectIds);
        }
        vo.setSubjectRankings(subjectRankings);

        LambdaQueryWrapper<WfTask> taskQuery = new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTenantId, tenantId)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                .le(WfTask::getReceivedAt, LocalDateTime.now().minusDays(7));
        applyMonthDateTimeRange(taskQuery, selectedMonth, WfTask::getReceivedAt);
        List<WfTask> tasks = wfTaskMapper.selectList(taskQuery);
        Map<Long, WfInstance> instanceMap = batchLoadInstances(tasks);
        vo.setOverdueItems(tasks.stream()
                .filter(t -> {
                    WfInstance instance = instanceMap.get(t.getInstanceId());
                    return instance != null && projectIds.contains(instance.getProjectId());
                })
                .sorted(Comparator.comparing(WfTask::getReceivedAt))
                .limit(5)
                .map(t -> toCostOverdueItem(t, instanceMap.get(t.getInstanceId()), projectNameMap))
                .collect(Collectors.toList()));

        LambdaQueryWrapper<PayRecord> pendingPayQuery = new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, tenantId)
                .in(PayRecord::getProjectId, projectIds)
                .ne(PayRecord::getPayStatus, "SUCCESS");
        applyMonthDateRange(pendingPayQuery, selectedMonth, PayRecord::getPayDate);
        List<PayRecord> pendingPays = payRecordMapper.selectList(
                pendingPayQuery.orderByDesc(PayRecord::getPayDate));
        Map<Long, CtContract> contractMap = loadContracts(pendingPays);
        vo.setPendingPayments(pendingPays.stream()
                .limit(5)
                .map(p -> toCostPendingPayment(p, contractMap.get(p.getContractId()), projectNameMap))
                .collect(Collectors.toList()));

        Map<String, ContractDisplay> subjectContractMap = loadSubjectContractDisplays(tenantId, projectIds, selectedMonth);
        List<CostManagerDashboardVO.LedgerRow> ledgerRows = new ArrayList<>();
        ledgerRows.addAll(subjectRankings.stream()
                .limit(20)
                .map(ranking -> toCostLedgerRow(ranking, subjectContractMap.get(ranking.getCostSubjectId())))
                .collect(Collectors.toList()));
        LambdaQueryWrapper<CtContract> contractQuery = new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getTenantId, tenantId)
                .in(CtContract::getProjectId, projectIds);
        applyMonthDateTimeRange(contractQuery, selectedMonth, CtContract::getCreatedAt);
        List<CtContract> projectContracts = ctContractMapper.selectList(
                contractQuery.orderByDesc(CtContract::getCreatedAt));
        ledgerRows.addAll(projectContracts.stream()
                .limit(20)
                .map(this::toContractLedgerRow)
                .collect(Collectors.toList()));
        ledgerRows.addAll(pendingPays.stream()
                .limit(20)
                .map(p -> toFundLedgerRow(p, contractMap.get(p.getContractId()), projectNameMap))
                .collect(Collectors.toList()));
        vo.setLedgerRows(ledgerRows);
        vo.setLedgerTotal((long) ledgerRows.size());
    }

    private void applyCostSummaryKpis(CostManagerDashboardVO vo, CostProjectSummaryVO summary, CostSummary projectLevelSummary) {
        if (projectLevelSummary != null) {
            vo.setTargetCost(projectLevelSummary.getTargetCost() != null ? projectLevelSummary.getTargetCost().toPlainString() : "0");
            vo.setDynamicCost(projectLevelSummary.getDynamicCost() != null ? projectLevelSummary.getDynamicCost().toPlainString() : "0");
            vo.setCostDeviation(projectLevelSummary.getCostDeviation() != null ? projectLevelSummary.getCostDeviation().toPlainString() : "0");
            vo.setContractLockedCost(projectLevelSummary.getContractLockedCost() != null ? projectLevelSummary.getContractLockedCost().toPlainString() : "0");
            vo.setActualCost(projectLevelSummary.getActualCost() != null ? projectLevelSummary.getActualCost().toPlainString() : "0");
            vo.setEstimatedRemainingCost(projectLevelSummary.getEstimatedRemainingCost() != null ? projectLevelSummary.getEstimatedRemainingCost().toPlainString() : "0");
            vo.setExpectedProfit(projectLevelSummary.getExpectedProfit() != null ? projectLevelSummary.getExpectedProfit().toPlainString() : "0");
            vo.setContractIncome(projectLevelSummary.getContractIncome() != null ? projectLevelSummary.getContractIncome().toPlainString() : "0");
            return;
        }
        if (summary == null) {
            vo.setTargetCost("0");
            vo.setDynamicCost("0");
            vo.setCostDeviation("0");
            vo.setContractLockedCost("0");
            vo.setActualCost("0");
            vo.setEstimatedRemainingCost("0");
            vo.setExpectedProfit("0");
            vo.setContractIncome("0");
            return;
        }
        vo.setTargetCost(summary.getTargetCost());
        vo.setDynamicCost(summary.getDynamicCost());
        vo.setCostDeviation(summary.getCostDeviation());
        vo.setContractLockedCost(summary.getContractLockedCost());
        vo.setActualCost(summary.getActualCost());
        vo.setEstimatedRemainingCost(summary.getEstimatedRemainingCost());
        vo.setExpectedProfit(summary.getExpectedProfit());
        vo.setContractIncome(summary.getContractIncome());
    }

    private CostSummary findLatestProjectLevelSummary(Long tenantId, Long projectId, YearMonth selectedMonth) {
        LambdaQueryWrapper<CostSummary> query = new LambdaQueryWrapper<CostSummary>()
                .eq(CostSummary::getTenantId, tenantId)
                .eq(CostSummary::getProjectId, projectId)
                .isNull(CostSummary::getCostSubjectId);
        if (selectedMonth != null) {
            query.le(CostSummary::getSummaryDate, selectedMonth.atEndOfMonth());
        }
        return costSummaryMapper.selectList(
                query.orderByDesc(CostSummary::getSummaryDate).last("limit 1"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private Map<Long, CostSummary> loadLatestProjectLevelSummaries(Long tenantId, List<Long> projectIds, YearMonth selectedMonth) {
        if (CollectionUtils.isEmpty(projectIds)) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<CostSummary> query = new LambdaQueryWrapper<CostSummary>()
                .eq(CostSummary::getTenantId, tenantId)
                .in(CostSummary::getProjectId, projectIds)
                .isNull(CostSummary::getCostSubjectId);
        if (selectedMonth != null) {
            query.le(CostSummary::getSummaryDate, selectedMonth.atEndOfMonth());
        }
        List<CostSummary> summaries = costSummaryMapper.selectList(
                query.orderByDesc(CostSummary::getSummaryDate));
        Map<Long, CostSummary> result = new HashMap<>();
        for (CostSummary summary : summaries) {
            result.putIfAbsent(summary.getProjectId(), summary);
        }
        return result;
    }

    private void applyEmptyCostManagerLists(CostManagerDashboardVO vo) {
        vo.setTrendPoints(Collections.emptyList());
        vo.setSubjectRankings(Collections.emptyList());
        vo.setOverBudgetAlerts(Collections.emptyList());
        vo.setOverdueItems(Collections.emptyList());
        vo.setPendingPayments(Collections.emptyList());
        vo.setLedgerRows(Collections.emptyList());
        vo.setLedgerTotal(0L);
    }

    private YearMonth parseDashboardMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessException("INVALID_DASHBOARD_MONTH", "月份格式必须为 yyyy-MM", e);
        }
    }

    private List<CostSummary> latestSubjectSummaries(List<CostSummary> summaries) {
        Map<String, CostSummary> latest = new HashMap<>();
        for (CostSummary summary : summaries) {
            String key = summary.getProjectId() + ":" + summary.getCostSubjectId();
            CostSummary current = latest.get(key);
            if (current == null
                    || (summary.getSummaryDate() != null
                    && (current.getSummaryDate() == null || summary.getSummaryDate().isAfter(current.getSummaryDate())))) {
                latest.put(key, summary);
            }
        }
        return new ArrayList<>(latest.values());
    }

    private <T> void applyMonthDateRange(
            LambdaQueryWrapper<T> query,
            YearMonth month,
            SFunction<T, LocalDate> column
    ) {
        if (month == null) {
            return;
        }
        query.ge(column, month.atDay(1))
                .le(column, month.atEndOfMonth());
    }

    private <T> void applyMonthDateTimeRange(
            LambdaQueryWrapper<T> query,
            YearMonth month,
            SFunction<T, LocalDateTime> column
    ) {
        if (month == null) {
            return;
        }
        query.ge(column, month.atDay(1).atStartOfDay())
                .lt(column, month.plusMonths(1).atDay(1).atStartOfDay());
    }

    private List<CostManagerDashboardVO.TrendPoint> toCostTrendPoints(List<CostSummary> projectSummaries) {
        Map<String, CostSummaryAccumulator> byMonth = new TreeMap<>();
        projectSummaries.stream()
                .filter(s -> s.getSummaryDate() != null)
                .forEach(s -> {
                    String month = s.getSummaryDate().withDayOfMonth(1).toString().substring(0, 7);
                    byMonth.computeIfAbsent(month, key -> new CostSummaryAccumulator()).add(s);
                });
        return byMonth.entrySet().stream()
                .skip(Math.max(0, byMonth.size() - 12))
                .map(entry -> {
                    CostSummaryAccumulator acc = entry.getValue();
                    CostManagerDashboardVO.TrendPoint point = new CostManagerDashboardVO.TrendPoint();
                    point.setMonth(entry.getKey());
                    point.setTargetCost(acc.targetCost.toPlainString());
                    point.setDynamicCost(acc.dynamicCost.toPlainString());
                    point.setCostDeviation(acc.costDeviation.toPlainString());
                    return point;
                })
                .collect(Collectors.toList());
    }

    private Map<Long, CostSubject> loadCostSubjects(List<CostSummary> subjectSummaries) {
        Set<Long> subjectIds = subjectSummaries.stream()
                .map(CostSummary::getCostSubjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return loadCostSubjects(subjectIds);
    }

    private Map<Long, CostSubject> loadCostSubjects(Collection<Long> subjectIds) {
        Set<Long> normalizedSubjectIds = subjectIds == null
                ? Collections.emptySet()
                : subjectIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (normalizedSubjectIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return costSubjectMapper.selectBatchIds(normalizedSubjectIds).stream()
                .collect(Collectors.toMap(CostSubject::getId, s -> s, (a, b) -> a));
    }

    private List<CostManagerDashboardVO.SubjectRanking> toSubjectRankings(List<CostSummary> subjectSummaries,
                                                                         Map<Long, CostSubject> subjectMap) {
        Map<Long, CostSummaryAccumulator> bySubject = new HashMap<>();
        subjectSummaries.forEach(s -> bySubject.computeIfAbsent(s.getCostSubjectId(), key -> new CostSummaryAccumulator()).add(s));
        BigDecimal actualTotal = bySubject.values().stream()
                .map(acc -> acc.actualCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return bySubject.entrySet().stream()
                .sorted((a, b) -> b.getValue().actualCost.compareTo(a.getValue().actualCost))
                .limit(10)
                .map(entry -> {
                    CostSummaryAccumulator acc = entry.getValue();
                    CostSubject subject = subjectMap.get(entry.getKey());
                    CostManagerDashboardVO.SubjectRanking ranking = new CostManagerDashboardVO.SubjectRanking();
                    ranking.setCostSubjectId(String.valueOf(entry.getKey()));
                    ranking.setCostSubjectName(subject != null ? subject.getSubjectName() : "");
                    ranking.setTargetCost(acc.targetCost.toPlainString());
                    ranking.setActualCost(acc.actualCost.toPlainString());
                    ranking.setDynamicCost(acc.dynamicCost.toPlainString());
                    ranking.setCostDeviation(acc.costDeviation.toPlainString());
                    ranking.setRatio(actualTotal.compareTo(BigDecimal.ZERO) == 0
                            ? "0"
                            : acc.actualCost.multiply(BigDecimal.valueOf(100)).divide(actualTotal, 2, RoundingMode.HALF_UP).toPlainString());
                    return ranking;
                })
                .collect(Collectors.toList());
    }

    private List<CostManagerDashboardVO.SubjectRanking> toSubjectRankingsFromCostItems(Long tenantId, List<Long> projectIds) {
        if (CollectionUtils.isEmpty(projectIds)) {
            return Collections.emptyList();
        }
        List<CostItem> items = costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, tenantId)
                .in(CostItem::getProjectId, projectIds));
        if (CollectionUtils.isEmpty(items)) {
            return Collections.emptyList();
        }

        Map<Long, CostSummaryAccumulator> bySubject = new HashMap<>();
        for (CostItem item : items) {
            Long subjectId = item.getCostSubjectId();
            BigDecimal amount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
            CostSummaryAccumulator acc = bySubject.computeIfAbsent(subjectId, key -> new CostSummaryAccumulator());
            if (isActualCostItem(item)) {
                acc.actualCost = acc.actualCost.add(amount);
            }
        }

        Map<Long, CostSubject> subjectMap = loadCostSubjects(bySubject.keySet());
        BigDecimal actualTotal = bySubject.values().stream()
                .map(acc -> acc.actualCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return bySubject.entrySet().stream()
                .sorted((a, b) -> b.getValue().actualCost.compareTo(a.getValue().actualCost))
                .limit(10)
                .map(entry -> {
                    Long subjectId = entry.getKey();
                    CostSummaryAccumulator acc = entry.getValue();
                    acc.dynamicCost = acc.actualCost;
                    acc.targetCost = acc.actualCost;
                    acc.costDeviation = BigDecimal.ZERO;
                    BigDecimal subjectActual = acc.actualCost;

                    CostManagerDashboardVO.SubjectRanking ranking = new CostManagerDashboardVO.SubjectRanking();
                    ranking.setCostSubjectId(subjectId == null ? "UNASSIGNED" : String.valueOf(subjectId));
                    ranking.setCostSubjectName(subjectId == null
                            ? "未分配科目"
                            : (subjectMap.get(subjectId) != null ? subjectMap.get(subjectId).getSubjectName() : ""));
                    ranking.setTargetCost(acc.targetCost.toPlainString());
                    ranking.setActualCost(subjectActual.toPlainString());
                    ranking.setDynamicCost(acc.dynamicCost.toPlainString());
                    ranking.setCostDeviation(acc.costDeviation.toPlainString());
                    ranking.setRatio(actualTotal.compareTo(BigDecimal.ZERO) == 0
                            ? "0"
                            : subjectActual.multiply(BigDecimal.valueOf(100))
                            .divide(actualTotal, 2, RoundingMode.HALF_UP)
                            .toPlainString());
                    return ranking;
                })
                .collect(Collectors.toList());
    }

    private boolean isActualCostItem(CostItem item) {
        if (item == null) {
            return false;
        }
        return "MAT_RECEIPT".equals(item.getSourceType())
                || "SUB_MEASURE".equals(item.getSourceType())
                || "BID_COST".equals(item.getSourceType())
                || "BID_COST_TRANSFERRED".equals(item.getSourceType())
                || "OVERHEAD_ALLOCATION".equals(item.getSourceType());
    }

    private Map<Long, CtContract> loadContracts(List<PayRecord> payRecords) {
        Set<Long> contractIds = payRecords.stream()
                .map(PayRecord::getContractId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (contractIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return ctContractMapper.selectBatchIds(contractIds).stream()
                .collect(Collectors.toMap(CtContract::getId, c -> c, (a, b) -> a));
    }

    private CostManagerDashboardVO.OverdueItem toCostOverdueItem(WfTask task,
                                                                 WfInstance instance,
                                                                 Map<Long, String> projectNameMap) {
        CostManagerDashboardVO.OverdueItem item = new CostManagerDashboardVO.OverdueItem();
        item.setTaskId(String.valueOf(task.getId()));
        item.setInstanceId(String.valueOf(task.getInstanceId()));
        item.setBusinessType(task.getBusinessType());
        item.setBusinessId(task.getBusinessId() != null ? String.valueOf(task.getBusinessId()) : null);
        item.setTitle(instance != null ? instance.getTitle() : task.getBusinessType());
        item.setOverdueDays(task.getReceivedAt() == null ? 0L : Math.max(0L, ChronoUnit.DAYS.between(task.getReceivedAt(), LocalDateTime.now())));
        item.setOwnerName(task.getApproverName());
        item.setPlannedAt(task.getReceivedAt() != null ? DateTimeUtils.DTF.format(task.getReceivedAt().plusDays(7)) : null);
        if (instance != null && instance.getProjectId() != null) {
            item.setProjectId(String.valueOf(instance.getProjectId()));
            item.setProjectName(projectNameMap.get(instance.getProjectId()));
        }
        return item;
    }

    private CostManagerDashboardVO.PendingPayment toCostPendingPayment(PayRecord payRecord,
                                                                       CtContract contract,
                                                                       Map<Long, String> projectNameMap) {
        CostManagerDashboardVO.PendingPayment item = new CostManagerDashboardVO.PendingPayment();
        item.setPayRecordId(String.valueOf(payRecord.getId()));
        item.setContractId(payRecord.getContractId() != null ? String.valueOf(payRecord.getContractId()) : null);
        item.setContractName(contract != null ? contract.getContractName() : "");
        item.setPartnerName("");
        item.setPayAmount(payRecord.getPayAmount() != null ? payRecord.getPayAmount().toPlainString() : "0");
        item.setPayDate(payRecord.getPayDate() != null ? payRecord.getPayDate().toString() : null);
        item.setPayStatus(payRecord.getPayStatus());
        item.setProjectId(payRecord.getProjectId() != null ? String.valueOf(payRecord.getProjectId()) : null);
        item.setProjectName(projectNameMap.get(payRecord.getProjectId()));
        return item;
    }

    private Map<String, ContractDisplay> loadSubjectContractDisplays(Long tenantId, List<Long> projectIds, YearMonth selectedMonth) {
        LambdaQueryWrapper<CostItem> itemQuery = new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, tenantId)
                .in(CostItem::getProjectId, projectIds)
                .isNotNull(CostItem::getCostSubjectId)
                .isNotNull(CostItem::getContractId);
        if (selectedMonth != null) {
            itemQuery.le(CostItem::getCostDate, selectedMonth.atEndOfMonth());
        }
        List<CostItem> items = costItemMapper.selectList(itemQuery);
        if (CollectionUtils.isEmpty(items)) {
            return Collections.emptyMap();
        }

        Set<Long> contractIds = items.stream()
                .map(CostItem::getContractId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (contractIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, CtContract> contracts = ctContractMapper.selectBatchIds(contractIds).stream()
                .collect(Collectors.toMap(CtContract::getId, c -> c, (a, b) -> a));

        Map<Long, Set<Long>> contractIdsBySubject = items.stream()
                .collect(Collectors.groupingBy(CostItem::getCostSubjectId,
                        Collectors.mapping(CostItem::getContractId, Collectors.toCollection(LinkedHashSet::new))));
        return contractIdsBySubject.entrySet().stream()
                .collect(Collectors.toMap(entry -> String.valueOf(entry.getKey()), entry -> toContractDisplay(entry.getValue(), contracts)));
    }

    private ContractDisplay toContractDisplay(Set<Long> contractIds, Map<Long, CtContract> contracts) {
        List<CtContract> matchedContracts = contractIds.stream()
                .map(contracts::get)
                .filter(Objects::nonNull)
                .toList();
        if (matchedContracts.isEmpty()) {
            return new ContractDisplay("", "");
        }
        if (matchedContracts.size() == 1) {
            CtContract contract = matchedContracts.get(0);
            return new ContractDisplay(contract.getContractCode(), contract.getContractName());
        }
        return new ContractDisplay("多合同汇总", matchedContracts.size() + "个合同");
    }

    private CostManagerDashboardVO.LedgerRow toCostLedgerRow(CostManagerDashboardVO.SubjectRanking ranking, ContractDisplay contractDisplay) {
        BigDecimal target = toBigDecimal(ranking.getTargetCost());
        BigDecimal actual = toBigDecimal(ranking.getActualCost());
        BigDecimal deviation = toBigDecimal(ranking.getCostDeviation());
        BigDecimal completionRatio = target.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : actual.multiply(BigDecimal.valueOf(100)).divide(target, 2, RoundingMode.HALF_UP);
        BigDecimal deviationRatio = target.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : deviation.multiply(BigDecimal.valueOf(100)).divide(target, 2, RoundingMode.HALF_UP);

        CostManagerDashboardVO.LedgerRow row = new CostManagerDashboardVO.LedgerRow();
        row.setRowType("cost");
        row.setCostSubjectId(ranking.getCostSubjectId());
        row.setCostSubjectName(ranking.getCostSubjectName());
        row.setContractCode(contractDisplay != null ? contractDisplay.code() : "");
        row.setContractName(contractDisplay != null ? contractDisplay.name() : "");
        row.setBudgetAmount(target.toPlainString());
        row.setActualAmount(actual.toPlainString());
        row.setCompletionRatio(completionRatio.toPlainString() + "%");
        row.setDeviationAmount(deviation.toPlainString());
        row.setDeviationRatio(deviationRatio.toPlainString() + "%");
        row.setStatus(costLedgerStatus(deviationRatio));
        row.setOwnerName("");
        return row;
    }

    private record ContractDisplay(String code, String name) {
    }

    private CostManagerDashboardVO.LedgerRow toContractLedgerRow(CtContract contract) {
        BigDecimal contractAmount = contract.getContractAmount() != null ? contract.getContractAmount() : BigDecimal.ZERO;
        BigDecimal currentAmount = contract.getCurrentAmount() != null ? contract.getCurrentAmount() : BigDecimal.ZERO;
        BigDecimal paidAmount = contract.getPaidAmount() != null ? contract.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal deviation = currentAmount.subtract(contractAmount);
        BigDecimal completionRatio = contractAmount.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : paidAmount.multiply(BigDecimal.valueOf(100)).divide(contractAmount, 2, RoundingMode.HALF_UP);
        BigDecimal deviationRatio = contractAmount.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : deviation.multiply(BigDecimal.valueOf(100)).divide(contractAmount, 2, RoundingMode.HALF_UP);

        CostManagerDashboardVO.LedgerRow row = new CostManagerDashboardVO.LedgerRow();
        row.setRowType("contract");
        row.setCostSubjectId("");
        row.setCostSubjectName(contract.getContractType());
        row.setContractCode(contract.getContractCode());
        row.setContractName(contract.getContractName());
        row.setBudgetAmount(contractAmount.toPlainString());
        row.setActualAmount(paidAmount.toPlainString());
        row.setCompletionRatio(completionRatio.toPlainString() + "%");
        row.setDeviationAmount(deviation.toPlainString());
        row.setDeviationRatio(deviationRatio.toPlainString() + "%");
        row.setStatus(contract.getContractStatus());
        row.setOwnerName("");
        return row;
    }

    private CostManagerDashboardVO.LedgerRow toFundLedgerRow(PayRecord payRecord,
                                                            CtContract contract,
                                                            Map<Long, String> projectNameMap) {
        BigDecimal payAmount = payRecord.getPayAmount() != null ? payRecord.getPayAmount() : BigDecimal.ZERO;

        CostManagerDashboardVO.LedgerRow row = new CostManagerDashboardVO.LedgerRow();
        row.setRowType("fund");
        row.setCostSubjectId("");
        row.setCostSubjectName("资金支付");
        row.setContractCode(payRecord.getVoucherNo() != null ? payRecord.getVoucherNo() : String.valueOf(payRecord.getId()));
        row.setContractName(contract != null ? contract.getContractName() : "");
        row.setBudgetAmount(payAmount.toPlainString());
        row.setActualAmount(payAmount.toPlainString());
        row.setCompletionRatio("100.00%");
        row.setDeviationAmount("0");
        row.setDeviationRatio("0.00%");
        row.setStatus(payRecord.getPayStatus());
        row.setOwnerName(projectNameMap.get(payRecord.getProjectId()));
        return row;
    }

    private String costLedgerStatus(BigDecimal deviationRatio) {
        if (deviationRatio.compareTo(BigDecimal.TEN) > 0) {
            return "超预算";
        }
        if (deviationRatio.compareTo(BigDecimal.valueOf(5)) > 0) {
            return "关注";
        }
        return "正常";
    }

    private BigDecimal toBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    private static class CostSummaryAccumulator {
        private BigDecimal targetCost = BigDecimal.ZERO;
        private BigDecimal actualCost = BigDecimal.ZERO;
        private BigDecimal dynamicCost = BigDecimal.ZERO;
        private BigDecimal costDeviation = BigDecimal.ZERO;

        private void add(CostSummary summary) {
            targetCost = targetCost.add(summary.getTargetCost() != null ? summary.getTargetCost() : BigDecimal.ZERO);
            actualCost = actualCost.add(summary.getActualCost() != null ? summary.getActualCost() : BigDecimal.ZERO);
            dynamicCost = dynamicCost.add(summary.getDynamicCost() != null ? summary.getDynamicCost() : BigDecimal.ZERO);
            costDeviation = costDeviation.add(summary.getCostDeviation() != null ? summary.getCostDeviation() : BigDecimal.ZERO);
        }
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    private PmProject requireProject(Long tenantId, Long projectId) {
        if (projectId == null) {
            throw new BusinessException("PROJECT_NOT_FOUND", "请指定项目");
        }
        PmProject project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        return project;
    }

    private Map<Long, WfInstance> batchLoadInstances(List<WfTask> tasks) {
        if (CollectionUtils.isEmpty(tasks)) {
            return Collections.emptyMap();
        }
        Set<Long> instanceIds = tasks.stream()
                .map(WfTask::getInstanceId)
                .collect(Collectors.toSet());
        List<WfInstance> instances = wfInstanceMapper.selectBatchIds(instanceIds);
        return instances.stream().collect(Collectors.toMap(WfInstance::getId, i -> i, (a, b) -> a));
    }

    private DashboardProjectSummaryVO toProjectSummary(PmProject project) {
        DashboardProjectSummaryVO vo = new DashboardProjectSummaryVO();
        vo.setProjectId(String.valueOf(project.getId()));
        vo.setProjectName(project.getProjectName());
        vo.setProjectCode(project.getProjectCode());
        vo.setStatus(project.getStatus());
        vo.setTargetCost(project.getTargetCost() != null ? project.getTargetCost().toPlainString() : "0");
        return vo;
    }

    private DashboardContractItemVO toContractItem(CtContract contract) {
        DashboardContractItemVO vo = new DashboardContractItemVO();
        vo.setContractId(String.valueOf(contract.getId()));
        vo.setContractCode(contract.getContractCode());
        vo.setContractName(contract.getContractName());
        vo.setContractType(contract.getContractType());
        vo.setContractAmount(contract.getContractAmount() != null ? contract.getContractAmount().toPlainString() : "0");
        vo.setCurrentAmount(contract.getCurrentAmount() != null ? contract.getCurrentAmount().toPlainString() : "0");
        vo.setPaidAmount(contract.getPaidAmount() != null ? contract.getPaidAmount().toPlainString() : "0");
        vo.setEndDate(contract.getEndDate() != null ? contract.getEndDate().toString() : null);
        vo.setProjectId(contract.getProjectId() != null ? String.valueOf(contract.getProjectId()) : null);
        vo.setContractStatus(contract.getContractStatus());
        return vo;
    }

    private DashboardAlertItemVO toAlertItem(AlertLog alert) {
        DashboardAlertItemVO vo = new DashboardAlertItemVO();
        vo.setAlertType(alert.getRuleType());
        vo.setSeverity(alert.getSeverity());
        vo.setMessage(alert.getMessage());
        vo.setProjectId(alert.getProjectId() != null ? String.valueOf(alert.getProjectId()) : null);
        if (alert.getTriggeredAt() != null) {
            vo.setTriggeredAt(DateTimeUtils.DTF.format(alert.getTriggeredAt()));
        }
        return vo;
    }
}
