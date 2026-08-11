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
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import com.cgcpms.common.util.DateTimeUtils;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DashboardCostService extends DashboardSharedSupport {

    private final CostSubjectMapper costSubjectMapper;
    private final CostItemMapper costItemMapper;
    private final ProjectAccessChecker projectAccessChecker;

    public DashboardCostService(
            CostSummaryService costSummaryService,
            CostSummaryMapper costSummaryMapper,
            CostSubjectMapper costSubjectMapper,
            CostItemMapper costItemMapper,
            PmProjectMapper projectMapper,
            CtContractMapper ctContractMapper,
            WfTaskMapper wfTaskMapper,
            WfInstanceMapper wfInstanceMapper,
            PayRecordMapper payRecordMapper,
            AlertLogMapper alertLogMapper,
            ProjectAccessChecker projectAccessChecker) {
        super(costSummaryService, costSummaryMapper, projectMapper, ctContractMapper,
                wfTaskMapper, wfInstanceMapper, payRecordMapper, alertLogMapper);
        this.costSubjectMapper = costSubjectMapper;
        this.costItemMapper = costItemMapper;
        this.projectAccessChecker = projectAccessChecker;
    }

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
        projectAccessChecker.checkAccess(project, "查看成本驾驶舱");

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
                alertQuery.orderByDesc(AlertLog::getTriggeredAt).last("limit 5")); // SQL-SAFETY: fixed-sql-fragment
        vo.setOverBudgetAlerts(overBudgetAlerts.stream().map(alert -> {
            DashboardAlertItemVO item = toAlertItem(alert);
            item.setProjectName(project.getProjectName());
            return item;
        }).collect(Collectors.toList()));
        fillCostManagerDetails(vo, tenantId, List.of(project), List.of(projectId), selectedMonth);

        return vo;
    }

    public CostBreakdownVO getCostBreakdown(Long projectId) {
        return getCostBreakdown(projectId, null);
    }

    public CostBreakdownVO getCostBreakdown(Long projectId, String month) {
        Long tenantId = UserContext.getCurrentTenantId();
        YearMonth selectedMonth = parseDashboardMonth(month);
        PmProject project = requireProject(tenantId, projectId);
        projectAccessChecker.checkAccess(project, "查看成本分解");

        CostProjectSummaryVO projectSummary = selectedMonth == null
                ? costSummaryService.getProjectSummary(tenantId, projectId)
                : null;
        CostSummary projectLevelSummary = findLatestProjectLevelSummary(tenantId, projectId, selectedMonth);

        CostBreakdownVO vo = new CostBreakdownVO();
        vo.setProjectId(projectId.toString());
        vo.setProjectName(project.getProjectName());
        vo.setTargetCost(projectLevelSummary != null && projectLevelSummary.getTargetCost() != null
                ? projectLevelSummary.getTargetCost().toPlainString()
                : projectSummary != null ? projectSummary.getTargetCost() : "0");
        vo.setDynamicCost(projectLevelSummary != null && projectLevelSummary.getDynamicCost() != null
                ? projectLevelSummary.getDynamicCost().toPlainString()
                : projectSummary != null ? projectSummary.getDynamicCost() : "0");
        vo.setExpectedProfit(projectLevelSummary != null && projectLevelSummary.getExpectedProfit() != null
                ? projectLevelSummary.getExpectedProfit().toPlainString()
                : projectSummary != null ? projectSummary.getExpectedProfit() : "0");

        LambdaQueryWrapper<CostSummary> summaryQuery = new LambdaQueryWrapper<CostSummary>()
                .eq(CostSummary::getTenantId, tenantId)
                .eq(CostSummary::getProjectId, projectId)
                .isNotNull(CostSummary::getCostSubjectId);
        if (selectedMonth != null) {
            summaryQuery.le(CostSummary::getSummaryDate, selectedMonth.atEndOfMonth());
        }
        List<CostSummary> summaries = latestSubjectSummaries(costSummaryMapper.selectList(summaryQuery));

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
            List<CostSubject> subjects = costSubjectMapper.selectByIds(subjectIds);
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


    private CostManagerDashboardVO getCostManagerViewAllProjects(Long tenantId, YearMonth selectedMonth) {
        List<PmProject> activeProjects = projectAccessChecker.filterAccessible(projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE")));
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
                .in(AlertLog::getProjectId, projectIds)
                .in(AlertLog::getRuleType, Set.of("DYNAMIC_COST_EXCEEDS_TARGET", "MATERIAL_EXCEEDS_BUDGET"));
        applyMonthDateTimeRange(alertQuery, selectedMonth, AlertLog::getTriggeredAt);
        List<AlertLog> overBudgetAlerts = alertLogMapper.selectList(
                alertQuery.orderByDesc(AlertLog::getTriggeredAt).last("limit 5")); // SQL-SAFETY: fixed-sql-fragment
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

        LocalDateTime overdueAsOf = selectedMonth == null
                ? LocalDateTime.now()
                : selectedMonth.atEndOfMonth().atTime(LocalTime.MAX);
        LambdaQueryWrapper<WfTask> taskQuery = new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTenantId, tenantId)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                .le(WfTask::getReceivedAt, overdueAsOf.minusDays(7));
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
                .map(t -> toCostOverdueItem(t, instanceMap.get(t.getInstanceId()), projectNameMap, overdueAsOf))
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
                query.orderByDesc(CostSummary::getSummaryDate, CostSummary::getId).last("limit 1")) // SQL-SAFETY: fixed-sql-fragment
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
                query.orderByDesc(CostSummary::getSummaryDate, CostSummary::getId));
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

    protected List<CostSummary> latestSubjectSummaries(List<CostSummary> summaries) {
        Map<String, CostSummary> latest = new HashMap<>();
        for (CostSummary summary : summaries) {
            String key = summary.getProjectId() + ":" + summary.getCostSubjectId();
            CostSummary current = latest.get(key);
            if (isLaterSummary(summary, current)) {
                latest.put(key, summary);
            }
        }
        return new ArrayList<>(latest.values());
    }

    protected <T> void applyMonthDateRange(
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

    protected <T> void applyMonthDateTimeRange(
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
        Map<String, Map<Long, CostSummary>> latestByProjectAndMonth = new TreeMap<>();
        projectSummaries.stream()
                .filter(s -> s.getSummaryDate() != null)
                .forEach(s -> {
                    String month = s.getSummaryDate().withDayOfMonth(1).toString().substring(0, 7);
                    latestByProjectAndMonth.computeIfAbsent(month, key -> new HashMap<>())
                            .merge(s.getProjectId(), s,
                                    (current, candidate) -> isLaterSummary(candidate, current) ? candidate : current);
                });
        return latestByProjectAndMonth.entrySet().stream()
                .skip(Math.max(0, latestByProjectAndMonth.size() - 12))
                .map(entry -> {
                    CostSummaryAccumulator acc = new CostSummaryAccumulator();
                    entry.getValue().values().forEach(acc::add);
                    CostManagerDashboardVO.TrendPoint point = new CostManagerDashboardVO.TrendPoint();
                    point.setMonth(entry.getKey());
                    point.setTargetCost(acc.targetCost.toPlainString());
                    point.setDynamicCost(acc.dynamicCost.toPlainString());
                    point.setCostDeviation(acc.costDeviation.toPlainString());
                    return point;
                })
                .collect(Collectors.toList());
    }

    private static boolean isLaterSummary(CostSummary candidate, CostSummary current) {
        if (current == null) {
            return true;
        }
        Comparator<CostSummary> comparator = Comparator
                .comparing(CostSummary::getSummaryDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(CostSummary::getId, Comparator.nullsFirst(Comparator.naturalOrder()));
        return comparator.compare(candidate, current) > 0;
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
        return costSubjectMapper.selectByIds(normalizedSubjectIds).stream()
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
                || "VAR_ORDER".equals(item.getSourceType())
                || "CT_CHANGE".equals(item.getSourceType())
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
        return ctContractMapper.selectByIds(contractIds).stream()
                .collect(Collectors.toMap(CtContract::getId, c -> c, (a, b) -> a));
    }

    private CostManagerDashboardVO.OverdueItem toCostOverdueItem(WfTask task,
                                                                 WfInstance instance,
                                                                 Map<Long, String> projectNameMap,
                                                                 LocalDateTime overdueAsOf) {
        CostManagerDashboardVO.OverdueItem item = new CostManagerDashboardVO.OverdueItem();
        item.setTaskId(String.valueOf(task.getId()));
        item.setInstanceId(String.valueOf(task.getInstanceId()));
        item.setBusinessType(task.getBusinessType());
        item.setBusinessId(task.getBusinessId() != null ? String.valueOf(task.getBusinessId()) : null);
        item.setTitle(instance != null ? instance.getTitle() : task.getBusinessType());
        item.setOverdueDays(task.getReceivedAt() == null ? 0L : Math.max(0L, ChronoUnit.DAYS.between(task.getReceivedAt(), overdueAsOf)));
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
        Map<Long, CtContract> contracts = ctContractMapper.selectByIds(contractIds).stream()
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
            return new ContractDisplay("", "", null);
        }
        if (matchedContracts.size() == 1) {
            CtContract contract = matchedContracts.get(0);
            return new ContractDisplay(contract.getContractCode(), contract.getContractName(), contract.getId());
        }
        return new ContractDisplay("多合同汇总", matchedContracts.size() + "个合同", null);
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
        row.setSourceType(contractDisplay != null && contractDisplay.id() != null ? "CONTRACT" : "COST_SUBJECT");
        row.setSourceId(contractDisplay != null && contractDisplay.id() != null
                ? String.valueOf(contractDisplay.id())
                : ranking.getCostSubjectId());
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

    private record ContractDisplay(String code, String name, Long id) {
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
        row.setSourceType("CONTRACT");
        row.setSourceId(String.valueOf(contract.getId()));
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
        row.setSourceType("PAY_RECORD");
        row.setSourceId(String.valueOf(payRecord.getId()));
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
}
