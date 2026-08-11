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
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.budget.mapper.ProjectBudgetLineMapper;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.FundAccountMapper;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.auth.ProjectAccessChecker;
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
import com.cgcpms.tech.entity.TechItem;
import com.cgcpms.tech.mapper.TechItemMapper;
import com.cgcpms.tech.vo.ChiefEngineerDashboardVO;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

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
import java.util.stream.Stream;

@Slf4j
@Service
@Transactional(readOnly = true)
public class DashboardFinanceManagementService extends DashboardSharedSupport {

    private final ProjectAccessChecker projectAccessChecker;
    private final PayApplicationMapper payApplicationMapper;
    private final ProjectBudgetMapper projectBudgetMapper;
    private final ProjectBudgetLineMapper projectBudgetLineMapper;
    private final FundAccountMapper fundAccountMapper;
    private final CashJournalEntryMapper cashJournalEntryMapper;
    private final SysRoleMapper sysRoleMapper;

    public DashboardFinanceManagementService(
            CostSummaryService costSummaryService,
            CostSummaryMapper costSummaryMapper,
            CostSubjectMapper costSubjectMapper,
            CostItemMapper costItemMapper,
            PmProjectMapper projectMapper,
            CtContractMapper ctContractMapper,
            WfTaskMapper wfTaskMapper,
            WfInstanceMapper wfInstanceMapper,
            PayRecordMapper payRecordMapper,
            StlSettlementMapper stlSettlementMapper,
            VarOrderMapper varOrderMapper,
            SubMeasureMapper subMeasureMapper,
            AlertLogMapper alertLogMapper,
            MatPurchaseRequestMapper purchaseRequestMapper,
            MatPurchaseRequestItemMapper purchaseRequestItemMapper,
            MatPurchaseOrderMapper purchaseOrderMapper,
            MatPurchaseOrderItemMapper purchaseOrderItemMapper,
            MatReceiptMapper receiptMapper,
            MatReceiptItemMapper receiptItemMapper,
            MatRequisitionMapper requisitionMapper,
            MatWarehouseMapper warehouseMapper,
            MatStockMapper stockMapper,
            TechItemMapper techItemMapper,
            MdPartnerMapper partnerMapper,
            MdMaterialMapper materialMapper,
            SysUserMapper userMapper,
            ProjectAccessChecker projectAccessChecker,
            PayApplicationMapper payApplicationMapper,
            ProjectBudgetMapper projectBudgetMapper,
            ProjectBudgetLineMapper projectBudgetLineMapper,
            FundAccountMapper fundAccountMapper,
            CashJournalEntryMapper cashJournalEntryMapper,
            SysRoleMapper sysRoleMapper) {
        super(costSummaryService, costSummaryMapper, costSubjectMapper, costItemMapper, projectMapper, ctContractMapper, wfTaskMapper, wfInstanceMapper, payRecordMapper, stlSettlementMapper, varOrderMapper, subMeasureMapper, alertLogMapper, purchaseRequestMapper, purchaseRequestItemMapper, purchaseOrderMapper, purchaseOrderItemMapper, receiptMapper, receiptItemMapper, requisitionMapper, warehouseMapper, stockMapper, techItemMapper, partnerMapper, materialMapper, userMapper);
        this.projectAccessChecker = projectAccessChecker;
        this.payApplicationMapper = payApplicationMapper;
        this.projectBudgetMapper = projectBudgetMapper;
        this.projectBudgetLineMapper = projectBudgetLineMapper;
        this.fundAccountMapper = fundAccountMapper;
        this.cashJournalEntryMapper = cashJournalEntryMapper;
        this.sysRoleMapper = sysRoleMapper;
    }

    public FinanceDashboardVO getFinanceView(Long projectId) {
        return getFinanceView(projectId, null);
    }

    public FinanceDashboardVO getFinanceView(Long projectId, String month) {
        Long tenantId = UserContext.getCurrentTenantId();
        YearMonth selectedMonth = parseDashboardMonth(month);

        if (projectId == null) {
            return getFinanceViewAllProjects(tenantId, selectedMonth);
        }

        PmProject project = requireProject(tenantId, projectId);
        projectAccessChecker.checkAccess(project, "查看财务驾驶舱");

        FinanceDashboardVO vo = new FinanceDashboardVO();
        vo.setProjectId(projectId.toString());
        vo.setProjectName(project.getProjectName());

        ClosedLoopSnapshot snapshot = applyClosedLoopMetrics(
                vo, tenantId, List.of(project), selectedMonth, false);
        applyRealtimeFinanceRisks(vo, snapshot, selectedMonth);

        return vo;
    }

    // ========================================================================
    // 5. Management Dashboard (tenant-wide)
    // ========================================================================
    public ManagementDashboardVO getManagementView() {
        return getManagementView(null);
    }

    public ManagementDashboardVO getManagementView(Long projectId) {
        return getManagementView(projectId, null);
    }

    public ManagementDashboardVO getManagementView(Long projectId, String month) {
        Long tenantId = UserContext.getCurrentTenantId();
        YearMonth selectedMonth = parseDashboardMonth(month);

        ManagementDashboardVO vo = new ManagementDashboardVO();
        List<String> unavailable = new ArrayList<>();
        vo.setUnavailableMetrics(unavailable);

        // Active projects
        LambdaQueryWrapper<PmProject> projectQuery = new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getTenantId, tenantId);
        if (selectedMonth == null) {
            projectQuery.eq(PmProject::getStatus, "ACTIVE");
        }
        if (projectId != null) {
            projectQuery.eq(PmProject::getId, projectId);
        }
        List<PmProject> activeProjects = projectAccessChecker.filterAccessible(
                projectMapper.selectList(projectQuery)).stream()
                .filter(project -> existedBy(null, project.getCreatedAt(), selectedMonth))
                .collect(Collectors.toList());
        if (selectedMonth == null) {
            vo.setActiveProjectCount((long) activeProjects.size());
        } else {
            vo.setActiveProjectCount(null);
            unavailable.add("activeProjectCount");
        }

        if (activeProjects.isEmpty()) {
            if (selectedMonth == null) {
                vo.setTotalContractAmount("0");
                vo.setTotalDynamicCost("0");
                vo.setTotalExpectedProfit("0");
                vo.setTotalPaidAmount("0");
                vo.setTotalPendingTaskCount(0L);
                vo.setTotalRiskCount(0L);
            } else {
                unavailable.addAll(List.of("totalContractAmount", "totalDynamicCost",
                        "totalExpectedProfit", "totalPaidAmount", "totalPendingTaskCount", "totalRiskCount"));
            }
            vo.setProjectRankings(Collections.emptyList());
            vo.setMetricSources(Collections.emptyList());
            vo.setOverdueItems(Collections.emptyList());
            vo.setMajorRisks(Collections.emptyList());
            return vo;
        }

        // Aggregate totals across all active projects
        BigDecimal totalContractAmount = BigDecimal.ZERO;
        BigDecimal totalDynamicCost = BigDecimal.ZERO;
        BigDecimal totalExpectedProfit = BigDecimal.ZERO;
        BigDecimal totalPaidAmount = BigDecimal.ZERO;

        List<DashboardProjectSummaryVO> rankings = new ArrayList<>();
        boolean missingPeriodSummary = false;

        // Batch load all project summaries to avoid N+1 per-project queries
        List<Long> projectIds = activeProjects.stream().map(PmProject::getId).collect(Collectors.toList());
        Set<Long> visibleProjectIds = new HashSet<>(projectIds);
        Map<Long, CostProjectSummaryVO> summaryMap = selectedMonth == null
                ? costSummaryService.getBatchProjectSummaries(tenantId, projectIds)
                : Collections.emptyMap();
        Map<Long, CostSummary> periodSummaryMap = selectedMonth == null
                ? Collections.emptyMap()
                : costSummaryMapper.selectList(new LambdaQueryWrapper<CostSummary>()
                        .eq(CostSummary::getTenantId, tenantId)
                        .in(CostSummary::getProjectId, projectIds)
                        .isNull(CostSummary::getCostSubjectId)
                        .le(CostSummary::getSummaryDate, selectedMonth.atEndOfMonth())
                        .orderByDesc(CostSummary::getSummaryDate))
                        .stream()
                        .collect(Collectors.toMap(
                                CostSummary::getProjectId,
                                summary -> summary,
                                (latest, ignored) -> latest,
                                LinkedHashMap::new));

        for (PmProject project : activeProjects) {
            CostProjectSummaryVO summary = summaryMap.get(project.getId());
            CostSummary periodSummary = periodSummaryMap.get(project.getId());
            if ((selectedMonth == null && summary == null)
                    || (selectedMonth != null && periodSummary == null)) {
                log.warn("No summary found for project {}", project.getId());
                missingPeriodSummary = missingPeriodSummary || selectedMonth != null;
                continue;
            }

            DashboardProjectSummaryVO rank = new DashboardProjectSummaryVO();
            rank.setProjectId(String.valueOf(project.getId()));
            rank.setProjectName(project.getProjectName());
            rank.setProjectCode(project.getProjectCode());
            rank.setStatus(project.getStatus());
            rank.setTargetCost(selectedMonth == null ? summary.getTargetCost() : nz(periodSummary.getTargetCost()).toPlainString());
            rank.setDynamicCost(selectedMonth == null ? summary.getDynamicCost() : nz(periodSummary.getDynamicCost()).toPlainString());
            rank.setContractIncome(selectedMonth == null ? summary.getContractIncome() : nz(periodSummary.getContractIncome()).toPlainString());
            rank.setExpectedProfit(selectedMonth == null ? summary.getExpectedProfit() : nz(periodSummary.getExpectedProfit()).toPlainString());
            rank.setCostDeviation(selectedMonth == null ? summary.getCostDeviation() : nz(periodSummary.getCostDeviation()).toPlainString());
            rank.setPaidAmount(selectedMonth == null ? summary.getPaidAmount() : nz(periodSummary.getPaidAmount()).toPlainString());
            rank.setContractAmount(rank.getContractIncome());

            rankings.add(rank);

            totalContractAmount = totalContractAmount.add(
                    rank.getContractIncome() != null ? new BigDecimal(rank.getContractIncome()) : BigDecimal.ZERO);
            totalDynamicCost = totalDynamicCost.add(
                    rank.getDynamicCost() != null ? new BigDecimal(rank.getDynamicCost()) : BigDecimal.ZERO);
            totalExpectedProfit = totalExpectedProfit.add(
                    rank.getExpectedProfit() != null ? new BigDecimal(rank.getExpectedProfit()) : BigDecimal.ZERO);
            totalPaidAmount = totalPaidAmount.add(
                    rank.getPaidAmount() != null ? new BigDecimal(rank.getPaidAmount()) : BigDecimal.ZERO);
        }

        if (missingPeriodSummary) {
            vo.setTotalContractAmount(null);
            vo.setTotalDynamicCost(null);
            vo.setTotalExpectedProfit(null);
            vo.setTotalPaidAmount(null);
            unavailable.addAll(List.of("totalContractAmount", "totalDynamicCost",
                    "totalExpectedProfit", "totalPaidAmount"));
        } else {
            vo.setTotalContractAmount(totalContractAmount.toPlainString());
            vo.setTotalDynamicCost(totalDynamicCost.toPlainString());
            vo.setTotalExpectedProfit(totalExpectedProfit.toPlainString());
            vo.setTotalPaidAmount(totalPaidAmount.toPlainString());
        }

        // Rank by expected profit descending
        rankings.sort(Comparator.comparing(
                r -> new BigDecimal(r.getExpectedProfit() != null ? r.getExpectedProfit() : "0"),
                Comparator.reverseOrder()));
        vo.setProjectRankings(rankings);
        vo.setMetricSources(rankings.stream()
                .map(this::toManagementMetricSource)
                .collect(Collectors.toList()));

        if (selectedMonth == null) {
            List<WfTask> allPending = wfTaskMapper.selectList(
                    new LambdaQueryWrapper<WfTask>()
                            .eq(WfTask::getTenantId, tenantId)
                            .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                            .orderByDesc(WfTask::getReceivedAt));
            Map<Long, WfInstance> pendingInstanceMap = batchLoadInstances(allPending);
            List<WfTask> visiblePending = allPending.stream()
                    .filter(task -> {
                        WfInstance instance = pendingInstanceMap.get(task.getInstanceId());
                        return instance != null
                                && Objects.equals(tenantId, instance.getTenantId())
                                && instance.getProjectId() != null
                                && visibleProjectIds.contains(instance.getProjectId());
                    })
                    .toList();
            vo.setTotalPendingTaskCount((long) visiblePending.size());
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            vo.setOverdueItems(visiblePending.stream()
                    .filter(task -> task.getReceivedAt() != null
                            && task.getReceivedAt().isBefore(sevenDaysAgo))
                    .limit(20)
                    .map(task -> {
                        DashboardTaskItemVO item = new DashboardTaskItemVO();
                        item.setTaskId(String.valueOf(task.getId()));
                        item.setInstanceId(String.valueOf(task.getInstanceId()));
                        item.setBusinessType(task.getBusinessType());
                        item.setTaskStatus(task.getTaskStatus());
                        if (task.getReceivedAt() != null) {
                            item.setReceivedAt(DateTimeUtils.DTF.format(task.getReceivedAt()));
                        }
                        return item;
                    })
                    .toList());
            List<AlertLog> unreadAlerts = alertLogMapper.selectList(
                    new LambdaQueryWrapper<AlertLog>()
                            .eq(AlertLog::getTenantId, tenantId)
                            .in(AlertLog::getProjectId, visibleProjectIds)
                            .eq(AlertLog::getIsRead, 0)
                            .orderByDesc(AlertLog::getTriggeredAt));
            vo.setTotalRiskCount((long) unreadAlerts.size());
            vo.setMajorRisks(unreadAlerts.stream().limit(10).map(this::toAlertItem).toList());
        } else {
            vo.setTotalPendingTaskCount(null);
            vo.setOverdueItems(List.of());
            vo.setTotalRiskCount(null);
            vo.setMajorRisks(List.of());
            unavailable.addAll(List.of("totalPendingTaskCount", "overdueItems", "totalRiskCount", "majorRisks"));
        }

        return vo;
    }

    // ========================================================================

    private ManagementDashboardVO.MetricSourceVO toManagementMetricSource(DashboardProjectSummaryVO project) {
        ManagementDashboardVO.MetricSourceVO source = new ManagementDashboardVO.MetricSourceVO();
        source.setProjectId(project.getProjectId());
        source.setProjectName(project.getProjectName());
        source.setSourceType("PROJECT_SUMMARY");
        source.setSourceId(project.getProjectId());
        source.setContractAmount(project.getContractAmount());
        source.setDynamicCost(project.getDynamicCost());
        source.setExpectedProfit(project.getExpectedProfit());
        source.setPaidAmount(project.getPaidAmount());
        return source;
    }

    private FinanceDashboardVO getFinanceViewAllProjects(Long tenantId, YearMonth selectedMonth) {
        LambdaQueryWrapper<PmProject> projectQuery = new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getTenantId, tenantId);
        if (selectedMonth == null) projectQuery.eq(PmProject::getStatus, "ACTIVE");
        List<PmProject> visibleProjects = projectAccessChecker.filterAccessible(
                projectMapper.selectList(projectQuery)).stream()
                .filter(project -> existedBy(null, project.getCreatedAt(), selectedMonth))
                .collect(Collectors.toList());
        FinanceDashboardVO vo = new FinanceDashboardVO();
        vo.setProjectId(null);
        vo.setProjectName("全部项目");

        ClosedLoopSnapshot snapshot = applyClosedLoopMetrics(
                vo, tenantId, visibleProjects, selectedMonth, true);
        applyRealtimeFinanceRisks(vo, snapshot, selectedMonth);

        return vo;
    }

    private DashboardPaymentItemVO toPaymentItem(PayRecord payment, String projectName) {
        DashboardPaymentItemVO item = new DashboardPaymentItemVO();
        item.setPayRecordId(String.valueOf(payment.getId()));
        item.setRecordCode(payment.getRecordCode());
        item.setContractId(payment.getContractId() != null ? String.valueOf(payment.getContractId()) : null);
        item.setPayAmount(payment.getPayAmount() != null ? payment.getPayAmount().toPlainString() : "0");
        item.setPayDate(payment.getPayDate() != null ? payment.getPayDate().toString() : null);
        item.setPayStatus(payment.getPayStatus());
        item.setProjectId(String.valueOf(payment.getProjectId()));
        item.setProjectName(projectName);
        return item;
    }

    private ClosedLoopSnapshot applyClosedLoopMetrics(FinanceDashboardVO vo, Long tenantId,
                                                       List<PmProject> projects, YearMonth selectedMonth,
                                                       boolean enterpriseView) {
        List<Long> projectIds = projects.stream().map(PmProject::getId).toList();
        List<String> unavailable = new ArrayList<>();
        vo.setUnavailableMetrics(unavailable);
        vo.setPendingPayments(List.of());
        unavailable.add("pendingPayments");
        if (selectedMonth != null) {
            vo.setOverRatioAmount(null);
            vo.setWarrantyExpiringAmount(null);
            vo.setPendingPayments(List.of());
            vo.setOverRatioPayments(List.of());
            unavailable.addAll(List.of("overRatioAmount", "warrantyExpiringAmount", "overRatioPayments"));
        }
        if (projectIds.isEmpty()) {
            if (selectedMonth == null) {
                vo.setPendingPaymentAmount("0.00");
                vo.setPendingPaymentCount(0L);
                vo.setApprovedUnpaidAmount("0.00");
                vo.setTotalContractAmount("0.00");
                vo.setBudgetAmount("0.00");
                vo.setBudgetReservedAmount("0.00");
                vo.setBudgetConsumedAmount("0.00");
                vo.setBudgetExecutionRate("0.00");
            } else {
                vo.setPendingPaymentAmount(null);
                vo.setPendingPaymentCount(null);
                vo.setApprovedUnpaidAmount(null);
                vo.setTotalContractAmount(null);
                vo.setBudgetAmount(null);
                vo.setBudgetReservedAmount(null);
                vo.setBudgetConsumedAmount(null);
                vo.setBudgetExecutionRate(null);
                unavailable.addAll(List.of(
                        "pendingPaymentAmount", "pendingPaymentCount", "approvedUnpaidAmount",
                        "totalContractAmount", "budgetAmount", "budgetReservedAmount",
                        "budgetConsumedAmount", "budgetExecutionRate"));
            }
            vo.setTotalPaidAmount("0.00");
            vo.setCashOutflowAmount("0.00");
            applyCashBalance(vo, tenantId, selectedMonth, enterpriseView, unavailable);
            vo.setProjectProfit("0.00");
            vo.setMetricFormulaVersion(selectedMonth == null
                    ? "PAYMENT_CLOSED_LOOP_V1" : "PAYMENT_CLOSED_LOOP_HISTORY_V1");
            vo.setTrendPoints(Collections.emptyList());
            vo.setContractFundBreakdowns(Collections.emptyList());
            if (selectedMonth != null) unavailable.add("contractFundBreakdowns");
            return ClosedLoopSnapshot.empty();
        }
        List<PayApplication> applications = selectedMonth == null
                ? payApplicationMapper.selectList(
                        new LambdaQueryWrapper<PayApplication>().eq(PayApplication::getTenantId, tenantId)
                                .in(PayApplication::getProjectId, projectIds))
                : List.of();
        List<PayApplication> approving = applications.stream()
                .filter(a -> "APPROVING".equals(a.getApprovalStatus())).toList();
        BigDecimal pending = approving.stream().map(PayApplication::getApplyAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal approvedUnpaid = applications.stream()
                .filter(a -> "APPROVED".equals(a.getApprovalStatus()))
                .map(a -> nz(a.getApplyAmount()).subtract(nz(a.getActualPayAmount())))
                .filter(v -> v.compareTo(BigDecimal.ZERO) > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (selectedMonth == null) {
            vo.setPendingPaymentAmount(pending.toPlainString());
            vo.setPendingPaymentCount((long) approving.size());
            vo.setApprovedUnpaidAmount(approvedUnpaid.toPlainString());
        } else {
            vo.setPendingPaymentAmount(null);
            vo.setPendingPaymentCount(null);
            vo.setApprovedUnpaidAmount(null);
            unavailable.addAll(List.of("pendingPaymentAmount", "pendingPaymentCount", "approvedUnpaidAmount"));
        }

        List<CtContract> allContracts = selectedMonth == null
                ? ctContractMapper.selectList(new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .in(CtContract::getProjectId, projectIds))
                : List.of();
        List<CtContract> contracts = allContracts.stream()
                .filter(contract -> "APPROVED".equals(contract.getApprovalStatus()))
                .filter(contract -> "PERFORMING".equals(contract.getContractStatus()))
                .toList();
        BigDecimal contractAmount = contracts.stream().map(CtContract::getCurrentAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PayRecord> allPayRecords = payRecordMapper.selectList(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, tenantId).in(PayRecord::getProjectId, projectIds));
        allPayRecords = allPayRecords.stream()
                .filter(record -> existedBy(record.getPayDate(), record.getCreatedAt(), selectedMonth))
                .collect(Collectors.toList());
        List<PayRecord> paidRecords = allPayRecords.stream()
                .filter(record -> paidAsOf(record, selectedMonth)).toList();
        BigDecimal paid = paidRecords.stream().map(PayRecord::getPayAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ProjectBudget> activeBudgets = selectedMonth == null
                ? projectBudgetMapper.selectList(new LambdaQueryWrapper<ProjectBudget>()
                        .eq(ProjectBudget::getTenantId, tenantId).in(ProjectBudget::getProjectId, projectIds)
                        .eq(ProjectBudget::getStatus, "ACTIVE").eq(ProjectBudget::getActiveFlag, 1))
                : List.of();
        BigDecimal budgetAmount = activeBudgets.stream().map(ProjectBudget::getTotalAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Long> budgetIds = activeBudgets.stream().map(ProjectBudget::getId).toList();
        List<ProjectBudgetLine> budgetLines = budgetIds.isEmpty() ? List.of() : projectBudgetLineMapper.selectList(
                new LambdaQueryWrapper<ProjectBudgetLine>().eq(ProjectBudgetLine::getTenantId, tenantId)
                        .in(ProjectBudgetLine::getBudgetId, budgetIds));
        BigDecimal reserved = budgetLines.stream().map(ProjectBudgetLine::getReservedAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal consumed = budgetLines.stream().map(ProjectBudgetLine::getConsumedAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal executionRate = budgetAmount.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : reserved.add(consumed).multiply(new BigDecimal("100"))
                        .divide(budgetAmount, 2, RoundingMode.HALF_UP);

        Map<Long, String> projectNames = projects.stream().collect(Collectors.toMap(
                PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        List<CashJournalEntry> archivedJournals = cashJournalEntryMapper.selectList(
                new LambdaQueryWrapper<CashJournalEntry>().eq(CashJournalEntry::getTenantId, tenantId)
                        .in(CashJournalEntry::getProjectId, projectIds)).stream()
                .filter(journal -> archivedAsOf(journal, selectedMonth))
                .toList();
        BigDecimal cashOutflow = archivedJournals.stream()
                .map(journal -> "OUT".equals(journal.getDirection()) ? nz(journal.getAmount())
                        : nz(journal.getAmount()).negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Long, CostProjectSummaryVO> currentSummaries = selectedMonth == null
                ? costSummaryService.getBatchProjectSummaries(tenantId, projectIds)
                : Map.of();
        Map<Long, CostSummary> periodSummaries = selectedMonth == null ? Map.of()
                : costSummaryMapper.selectList(new LambdaQueryWrapper<CostSummary>()
                        .eq(CostSummary::getTenantId, tenantId)
                        .in(CostSummary::getProjectId, projectIds)
                        .isNull(CostSummary::getCostSubjectId)
                        .le(CostSummary::getSummaryDate, selectedMonth.atEndOfMonth())
                        .orderByDesc(CostSummary::getSummaryDate)).stream()
                .collect(Collectors.toMap(CostSummary::getProjectId, summary -> summary,
                        (latest, ignored) -> latest, LinkedHashMap::new));
        BigDecimal confirmedRevenue = selectedMonth == null
                ? currentSummaries.values().stream()
                        .map(summary -> new BigDecimal(summary.getConfirmedRevenue()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : periodSummaries.values().stream().map(CostSummary::getConfirmedRevenue)
                        .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dynamicCost = selectedMonth == null
                ? currentSummaries.values().stream()
                        .map(summary -> new BigDecimal(summary.getDynamicCost()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : periodSummaries.values().stream().map(CostSummary::getDynamicCost)
                        .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (selectedMonth == null) {
            vo.setTotalContractAmount(contractAmount.toPlainString());
        } else {
            vo.setTotalContractAmount(null);
            unavailable.add("totalContractAmount");
        }
        vo.setTotalPaidAmount(paid.toPlainString());
        if (selectedMonth == null) {
            vo.setBudgetAmount(budgetAmount.toPlainString());
            vo.setBudgetReservedAmount(reserved.toPlainString());
            vo.setBudgetConsumedAmount(consumed.toPlainString());
            vo.setBudgetExecutionRate(executionRate.toPlainString());
        } else {
            vo.setBudgetAmount(null);
            vo.setBudgetReservedAmount(null);
            vo.setBudgetConsumedAmount(null);
            vo.setBudgetExecutionRate(null);
            unavailable.addAll(List.of("budgetAmount", "budgetReservedAmount",
                    "budgetConsumedAmount", "budgetExecutionRate"));
        }
        vo.setCashOutflowAmount(cashOutflow.toPlainString());
        applyCashBalance(vo, tenantId, selectedMonth, enterpriseView, unavailable);
        if (selectedMonth != null && periodSummaries.size() < projectIds.size()) {
            vo.setProjectProfit(null);
            unavailable.add("projectProfit");
        } else {
            vo.setProjectProfit(confirmedRevenue.subtract(dynamicCost).toPlainString());
        }
        vo.setMetricFormulaVersion(selectedMonth == null
                ? "PAYMENT_CLOSED_LOOP_V1" : "PAYMENT_CLOSED_LOOP_HISTORY_V1");
        vo.setTrendPoints(buildFinanceTrendPoints(archivedJournals));
        if (selectedMonth == null) {
            vo.setContractFundBreakdowns(buildContractFundBreakdowns(
                    contracts, applications, allPayRecords, projectNames));
        } else {
            vo.setContractFundBreakdowns(List.of());
            unavailable.add("contractFundBreakdowns");
        }
        return new ClosedLoopSnapshot(allContracts, allPayRecords, projectNames);
    }

    private void applyRealtimeFinanceRisks(FinanceDashboardVO vo, ClosedLoopSnapshot snapshot,
                                           YearMonth selectedMonth) {
        if (selectedMonth != null) return;

        OverRatioResult overRatio = calculateOverRatio(snapshot.payRecords(), snapshot.contracts());
        vo.setOverRatioAmount(overRatio.amount().toPlainString());
        vo.setOverRatioPayments(snapshot.payRecords().stream()
                .filter(record -> "SUCCESS".equals(record.getPayStatus()))
                .filter(record -> overRatio.contractIds().contains(record.getContractId()))
                .limit(20)
                .map(record -> toPaymentItem(
                        record, snapshot.projectNames().getOrDefault(record.getProjectId(), "")))
                .toList());

        LocalDate windowStart = LocalDate.now();
        LocalDate threshold = windowStart.plusDays(30);
        BigDecimal warrantyExpiring = snapshot.contracts().stream()
                .filter(contract -> "PERFORMING".equals(contract.getContractStatus()))
                .filter(contract -> contract.getEndDate() != null
                        && !contract.getEndDate().isBefore(windowStart)
                        && !contract.getEndDate().isAfter(threshold))
                .map(CtContract::getContractAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setWarrantyExpiringAmount(warrantyExpiring.toPlainString());
    }

    private OverRatioResult calculateOverRatio(List<PayRecord> records, List<CtContract> contracts) {
        Map<Long, BigDecimal> paidByContract = records.stream()
                .filter(record -> "SUCCESS".equals(record.getPayStatus()))
                .filter(record -> record.getContractId() != null)
                .collect(Collectors.toMap(PayRecord::getContractId,
                        record -> nz(record.getPayAmount()), BigDecimal::add));
        Map<Long, CtContract> contractsById = contracts.stream()
                .collect(Collectors.toMap(CtContract::getId, contract -> contract, (left, ignored) -> left));
        BigDecimal amount = BigDecimal.ZERO;
        Set<Long> contractIds = new LinkedHashSet<>();
        for (Map.Entry<Long, BigDecimal> entry : paidByContract.entrySet()) {
            CtContract contract = contractsById.get(entry.getKey());
            BigDecimal contractAmount = contract == null ? BigDecimal.ZERO : nz(contract.getContractAmount());
            if (contractAmount.compareTo(BigDecimal.ZERO) > 0
                    && entry.getValue().compareTo(contractAmount) > 0) {
                amount = amount.add(entry.getValue().subtract(contractAmount));
                contractIds.add(entry.getKey());
            }
        }
        return new OverRatioResult(amount, Set.copyOf(contractIds));
    }

    private record ClosedLoopSnapshot(List<CtContract> contracts,
                                      List<PayRecord> payRecords,
                                      Map<Long, String> projectNames) {
        private static ClosedLoopSnapshot empty() {
            return new ClosedLoopSnapshot(List.of(), List.of(), Map.of());
        }
    }

    private record OverRatioResult(BigDecimal amount, Set<Long> contractIds) {
    }

    private List<FinanceDashboardVO.ContractFundBreakdown> buildContractFundBreakdowns(
            List<CtContract> contracts,
            List<PayApplication> applications,
            List<PayRecord> records,
            Map<Long, String> projectNames) {
        Map<Long, List<PayApplication>> applicationsByContract = applications.stream()
                .filter(item -> item.getContractId() != null)
                .collect(Collectors.groupingBy(PayApplication::getContractId));
        Map<Long, List<PayRecord>> recordsByContract = records.stream()
                .filter(item -> item.getContractId() != null)
                .collect(Collectors.groupingBy(PayRecord::getContractId));

        return contracts.stream()
                .sorted(Comparator.comparing(CtContract::getContractCode,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CtContract::getId))
                .map(contract -> {
                    List<PayApplication> contractApplications = applicationsByContract
                            .getOrDefault(contract.getId(), Collections.emptyList());
                    List<PayRecord> contractRecords = recordsByContract
                            .getOrDefault(contract.getId(), Collections.emptyList());
                    BigDecimal contractAmount = contract.getCurrentAmount() != null
                            ? contract.getCurrentAmount() : nz(contract.getContractAmount());
                    BigDecimal paidAmount = contractRecords.stream()
                            .filter(record -> "SUCCESS".equals(record.getPayStatus()))
                            .map(PayRecord::getPayAmount).filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal approvingAmount = contractApplications.stream()
                            .filter(application -> "APPROVING".equals(application.getApprovalStatus()))
                            .map(PayApplication::getApplyAmount).filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal approvedUnpaidAmount = contractApplications.stream()
                            .filter(application -> "APPROVED".equals(application.getApprovalStatus()))
                            .map(application -> nz(application.getApplyAmount())
                                    .subtract(nz(application.getActualPayAmount())))
                            .filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal remainingAmount = contractAmount.subtract(paidAmount).max(BigDecimal.ZERO);
                    BigDecimal paymentRatio = contractAmount.compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ZERO
                            : paidAmount.multiply(new BigDecimal("100"))
                                    .divide(contractAmount, 2, RoundingMode.HALF_UP);

                    FinanceDashboardVO.ContractFundBreakdown item = new FinanceDashboardVO.ContractFundBreakdown();
                    item.setContractId(String.valueOf(contract.getId()));
                    item.setProjectId(String.valueOf(contract.getProjectId()));
                    item.setProjectName(projectNames.getOrDefault(contract.getProjectId(), ""));
                    item.setContractCode(contract.getContractCode());
                    item.setContractName(contract.getContractName());
                    item.setContractAmount(contractAmount.toPlainString());
                    item.setPaidAmount(paidAmount.toPlainString());
                    item.setApprovingAmount(approvingAmount.toPlainString());
                    item.setApprovedUnpaidAmount(approvedUnpaidAmount.toPlainString());
                    item.setRemainingAmount(remainingAmount.toPlainString());
                    item.setPaymentRatio(paymentRatio.toPlainString());
                    item.setPaymentRecords(contractRecords.stream()
                            .sorted(Comparator.comparing(PayRecord::getPayDate,
                                            Comparator.nullsLast(Comparator.reverseOrder()))
                                    .thenComparing(PayRecord::getId, Comparator.reverseOrder()))
                            .map(record -> {
                                DashboardPaymentItemVO payment = toPaymentItem(
                                        record, projectNames.getOrDefault(record.getProjectId(), ""));
                                payment.setContractName(contract.getContractName());
                                return payment;
                            }).collect(Collectors.toList()));
                    return item;
                }).collect(Collectors.toList());
    }

    private List<FinanceDashboardVO.TrendPoint> buildFinanceTrendPoints(List<CashJournalEntry> records) {
        List<CashJournalEntry> datedRecords = records.stream()
                .filter(record -> record.getBusinessDate() != null)
                .toList();
        if (datedRecords.isEmpty()) return Collections.emptyList();

        YearMonth latest = datedRecords.stream().map(record -> YearMonth.from(trendDate(record)))
                .max(Comparator.naturalOrder()).orElseThrow();
        YearMonth earliest = datedRecords.stream().map(record -> YearMonth.from(trendDate(record)))
                .min(Comparator.naturalOrder()).orElse(latest);
        YearMonth first = earliest.isBefore(latest.minusMonths(11)) ? latest.minusMonths(11) : earliest;
        Map<YearMonth, BigDecimal> paidByMonth = new HashMap<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        for (CashJournalEntry record : datedRecords) {
            BigDecimal amount = "OUT".equals(record.getDirection())
                    ? nz(record.getAmount()) : nz(record.getAmount()).negate();
            YearMonth month = YearMonth.from(trendDate(record));
            if (month.isBefore(first)) cumulative = cumulative.add(amount);
            else paidByMonth.merge(month, amount, BigDecimal::add);
        }

        List<FinanceDashboardVO.TrendPoint> points = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(latest); month = month.plusMonths(1)) {
            BigDecimal monthlyPaid = paidByMonth.getOrDefault(month, BigDecimal.ZERO);
            cumulative = cumulative.add(monthlyPaid);
            FinanceDashboardVO.TrendPoint point = new FinanceDashboardVO.TrendPoint();
            point.setMonth(month.toString());
            point.setCashOutflowAmount(monthlyPaid.toPlainString());
            point.setCumulativePaidAmount(cumulative.toPlainString());
            point.setPendingPaymentAmount("0");
            points.add(point);
        }
        return points;
    }

    private BigDecimal companyCashBalance(Long tenantId) {
        return fundAccountMapper.selectBalancesByType(tenantId, null, true).stream()
                .map(FundAccountMapper.AccountTypeBalance::getBalance)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void applyCashBalance(FinanceDashboardVO vo, Long tenantId, YearMonth selectedMonth,
                                  boolean enterpriseView, List<String> unavailable) {
        boolean available = selectedMonth == null && enterpriseView && hasEnterpriseCashScope(tenantId);
        vo.setCashBalanceAvailable(available);
        vo.setCashBalance(available ? companyCashBalance(tenantId).toPlainString() : null);
        if (!available) unavailable.add("cashBalance");
    }

    private boolean hasEnterpriseCashScope(Long tenantId) {
        List<String> roleCodes = UserContext.getCurrentRoles();
        if (roleCodes.stream().anyMatch(code -> "ADMIN".equalsIgnoreCase(code)
                || "SUPER_ADMIN".equalsIgnoreCase(code))) return true;
        if (roleCodes.isEmpty()) return false;
        List<SysRole> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, tenantId).in(SysRole::getRoleCode, roleCodes));
        return !roles.isEmpty() && roles.stream().allMatch(role -> "ALL".equals(role.getDataScope()));
    }

    private boolean paidAsOf(PayRecord record, YearMonth selectedMonth) {
        if (selectedMonth == null) return "SUCCESS".equals(record.getPayStatus());
        if (!Set.of("SUCCESS", "REVERSED").contains(record.getPayStatus())) return false;
        LocalDateTime nextMonthStart = selectedMonth.plusMonths(1).atDay(1).atStartOfDay();
        return record.getReversedAt() == null || !record.getReversedAt().isBefore(nextMonthStart);
    }

    private boolean archivedAsOf(CashJournalEntry journal, YearMonth selectedMonth) {
        if (journal.getArchivedAt() == null) return false;
        if (selectedMonth == null) return Set.of("ARCHIVED", "REVERSED").contains(journal.getStatus());
        LocalDateTime nextMonthStart = selectedMonth.plusMonths(1).atDay(1).atStartOfDay();
        return journal.getArchivedAt().isBefore(nextMonthStart);
    }

    private LocalDate trendDate(CashJournalEntry journal) {
        return journal.getReverseOfEntryId() != null && journal.getArchivedAt() != null
                ? journal.getArchivedAt().toLocalDate() : journal.getBusinessDate();
    }

    private boolean existedBy(LocalDate businessDate, LocalDateTime createdAt, YearMonth selectedMonth) {
        if (selectedMonth == null) return true;
        LocalDate date = businessDate != null
                ? businessDate
                : createdAt == null ? null : createdAt.toLocalDate();
        return date != null && !date.isAfter(selectedMonth.atEndOfMonth());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
