package com.cgcpms.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostSummary;
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
        Long tenantId = UserContext.getCurrentTenantId();

        if (projectId == null) {
            return getCostManagerViewAllProjects(tenantId);
        }

        PmProject project = requireProject(tenantId, projectId);

        // Use pre-aggregated cost_summary via existing service
        CostProjectSummaryVO summary = costSummaryService.getProjectSummary(tenantId, projectId);

        CostManagerDashboardVO vo = new CostManagerDashboardVO();
        vo.setProjectId(projectId.toString());
        vo.setProjectName(project.getProjectName());
        vo.setTargetCost(summary.getTargetCost());
        vo.setDynamicCost(summary.getDynamicCost());
        vo.setCostDeviation(summary.getCostDeviation());
        vo.setContractLockedCost(summary.getContractLockedCost());
        vo.setActualCost(summary.getActualCost());
        vo.setEstimatedRemainingCost(summary.getEstimatedRemainingCost());
        vo.setExpectedProfit(summary.getExpectedProfit());
        vo.setContractIncome(summary.getContractIncome());

        // Over-budget alerts from alert_log (cost-exceeds-target + material-exceeds-budget)
        List<AlertLog> overBudgetAlerts = alertLogMapper.selectList(
                new LambdaQueryWrapper<AlertLog>()
                        .eq(AlertLog::getTenantId, tenantId)
                        .eq(AlertLog::getProjectId, projectId)
                        .in(AlertLog::getRuleType, Set.of("DYNAMIC_COST_EXCEEDS_TARGET", "MATERIAL_EXCEEDS_BUDGET"))
                        .orderByDesc(AlertLog::getTriggeredAt));
        vo.setOverBudgetAlerts(overBudgetAlerts.stream().map(this::toAlertItem).collect(Collectors.toList()));

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

    private CostManagerDashboardVO getCostManagerViewAllProjects(Long tenantId) {
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
            vo.setOverBudgetAlerts(Collections.emptyList());
            return vo;
        }

        Map<Long, CostProjectSummaryVO> summaryMap = costSummaryService.getBatchProjectSummaries(tenantId, projectIds);

        BigDecimal totalTargetCost = BigDecimal.ZERO;
        BigDecimal totalDynamicCost = BigDecimal.ZERO;
        BigDecimal totalContractLockedCost = BigDecimal.ZERO;
        BigDecimal totalActualCost = BigDecimal.ZERO;
        BigDecimal totalEstimatedRemainingCost = BigDecimal.ZERO;
        BigDecimal totalExpectedProfit = BigDecimal.ZERO;
        BigDecimal totalContractIncome = BigDecimal.ZERO;

        for (CostProjectSummaryVO s : summaryMap.values()) {
            totalTargetCost = totalTargetCost.add(s.getTargetCost() != null ? new BigDecimal(s.getTargetCost()) : BigDecimal.ZERO);
            totalDynamicCost = totalDynamicCost.add(s.getDynamicCost() != null ? new BigDecimal(s.getDynamicCost()) : BigDecimal.ZERO);
            totalContractLockedCost = totalContractLockedCost.add(s.getContractLockedCost() != null ? new BigDecimal(s.getContractLockedCost()) : BigDecimal.ZERO);
            totalActualCost = totalActualCost.add(s.getActualCost() != null ? new BigDecimal(s.getActualCost()) : BigDecimal.ZERO);
            totalEstimatedRemainingCost = totalEstimatedRemainingCost.add(s.getEstimatedRemainingCost() != null ? new BigDecimal(s.getEstimatedRemainingCost()) : BigDecimal.ZERO);
            totalExpectedProfit = totalExpectedProfit.add(s.getExpectedProfit() != null ? new BigDecimal(s.getExpectedProfit()) : BigDecimal.ZERO);
            totalContractIncome = totalContractIncome.add(s.getContractIncome() != null ? new BigDecimal(s.getContractIncome()) : BigDecimal.ZERO);
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
        List<AlertLog> overBudgetAlerts = alertLogMapper.selectList(
                new LambdaQueryWrapper<AlertLog>()
                        .eq(AlertLog::getTenantId, tenantId)
                        .in(AlertLog::getRuleType, Set.of("DYNAMIC_COST_EXCEEDS_TARGET", "MATERIAL_EXCEEDS_BUDGET"))
                        .orderByDesc(AlertLog::getTriggeredAt));
        vo.setOverBudgetAlerts(overBudgetAlerts.stream().map(this::toAlertItem).collect(Collectors.toList()));

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
