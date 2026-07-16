package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.entity.CostTargetItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.cost.mapper.CostTargetItemMapper;
import com.cgcpms.cost.vo.CostProjectSummaryVO;
import com.cgcpms.cost.vo.CostSummaryVO;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
// 已提取共享计算逻辑到 CostSummaryAssembler，后续可进一步拆分为 Query/Write 子服务
public class CostSummaryService {

    private final CostSummaryMapper costSummaryMapper;
    private final CostTargetMapper costTargetMapper;
    private final CostTargetItemMapper costTargetItemMapper;
    private final CostItemMapper costItemMapper;
    private final PmProjectMapper projectMapper;
    private final CostSubjectMapper costSubjectMapper;
    private final PayRecordMapper payRecordMapper;
    private final CtContractMapper ctContractMapper;
    private final SubMeasureMapper subMeasureMapper;
    private final MatReceiptMapper matReceiptMapper;
    private final VarOrderMapper varOrderMapper;
    private final CostSummaryAssembler assembler;
    private final ProjectAccessChecker projectAccessChecker;
    private final JdbcTemplate jdbc;

    /**
     * Prevents overlapping executions of the scheduled refresh task.
     * If a previous run is still in progress, the next cron trigger is skipped.
     */
    private final AtomicBoolean scheduledRefreshRunning = new AtomicBoolean(false);

    /**
     * Per-project locks to serialize concurrent {@link #refreshSummary(Long, Long)} calls
     * for the same project. The scheduled task and manual refresh endpoint can both fire
     * simultaneously; without this lock, two refreshes would interleave DELETE+INSERT
     * and produce incorrect or duplicate data.
     */
    private final ConcurrentHashMap<Long, ReentrantLock> refreshLocks = new ConcurrentHashMap<>();

    public CostProjectSummaryVO refreshSummary(Long projectId) {
        projectAccessChecker.checkAccess(projectId, "刷新成本摘要");
        // Use AOP proxy to ensure @Transactional(isolation=REPEATABLE_READ) on the overload is applied
        return ((CostSummaryService) AopContext.currentProxy()).refreshSummary(UserContext.getCurrentTenantId(), projectId);
    }

    /**
     * Refresh cost summary for a given project.
     * <p>
     * <b>Concurrency protection:</b>
     * <ul>
     *   <li>{@link Isolation#REPEATABLE_READ} ensures concurrent readers see a consistent
     *       snapshot — either all old rows or all new rows, never an empty/partial set.</li>
     *   <li>Per-project {@link ReentrantLock} serializes concurrent refresh calls for the
     *       same project (scheduled task + manual endpoint), preventing interleaved
     *       DELETE+INSERT sequences that would produce duplicate or incorrect data.</li>
     *   <li>If any INSERT fails, the entire transaction rolls back including the DELETE,
     *       so data integrity is preserved.</li>
     * </ul>
     *
     * @param tenantId  tenant ID
     * @param projectId project ID
     * @return refreshed project summary
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public CostProjectSummaryVO refreshSummary(Long tenantId, Long projectId) {
        // Serialize concurrent refresh for the same project
        ReentrantLock lock = refreshLocks.computeIfAbsent(projectId, k -> new ReentrantLock());
        lock.lock();
        try {
            return doRefreshSummary(tenantId, projectId);
        } finally {
            lock.unlock();
            // Clean up lock if no longer contended (avoid unbounded map growth)
            if (!lock.isLocked() && !lock.hasQueuedThreads()) {
                refreshLocks.remove(projectId, lock);
            }
        }
    }

    private CostProjectSummaryVO doRefreshSummary(Long tenantId, Long projectId) {
        log.info("Refreshing cost summary for projectId={}, tenantId={}", projectId, tenantId);

        PmProject project = assembler.requireProjectInTenant(tenantId, projectId);

        // 1. Remove today's snapshot rows for this project so re-inserts are idempotent
        costSummaryMapper.physicalDeleteByTenantProjectAndDate(tenantId, projectId, LocalDate.now());

        CostTarget activeTarget = costTargetMapper.selectOne(new LambdaQueryWrapper<CostTarget>()
                .eq(CostTarget::getTenantId, tenantId)
                .eq(CostTarget::getProjectId, projectId)
                .eq(CostTarget::getIsActive, 1)
                .eq(CostTarget::getApprovalStatus, "APPROVED")
                .eq(CostTarget::getStatus, "ACTIVE")
                .last("LIMIT 1")); // SQL-SAFETY: fixed-sql-fragment
        List<CostTargetItem> activeTargetItems = activeTarget == null ? Collections.emptyList()
                : costTargetItemMapper.selectList(new LambdaQueryWrapper<CostTargetItem>()
                    .eq(CostTargetItem::getTenantId, tenantId)
                    .eq(CostTargetItem::getTargetId, activeTarget.getId()));
        Map<Long, CostTargetItem> targetItemBySubject = activeTargetItems.stream()
                .collect(Collectors.toMap(CostTargetItem::getCostSubjectId, item -> item, (a, b) -> a));
        BigDecimal targetCost = activeTarget != null && activeTarget.getTotalTargetAmount() != null
                ? activeTarget.getTotalTargetAmount()
                : project.getTargetCost() != null ? project.getTargetCost() : BigDecimal.ZERO;
        log.debug("Project targetCost={}", targetCost);

        // 3. Query all cost items for this project, grouped by costSubjectId
        LambdaQueryWrapper<CostItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(CostItem::getTenantId, tenantId);
        itemWrapper.eq(CostItem::getProjectId, projectId);
        List<CostItem> allItems = costItemMapper.selectList(itemWrapper);
        Set<Long> mainContractIds = allItems.stream()
                .map(CostItem::getContractId)
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(Collectors.toSet(), ids -> {
                    if (ids.isEmpty()) {
                        return Collections.emptySet();
                    }
                    return ctContractMapper.selectByIds(ids).stream()
                            .filter(contract -> "MAIN".equals(contract.getContractType()))
                            .map(CtContract::getId)
                            .collect(Collectors.toSet());
                }));

        // Group cost items by costSubjectId
        Map<Long, List<CostItem>> itemsBySubject = allItems.stream()
                .filter(item -> item.getCostSubjectId() != null)
                .collect(Collectors.groupingBy(CostItem::getCostSubjectId));

        // 4. Build cost subject name map
        Set<Long> subjectIds = new HashSet<>(itemsBySubject.keySet());
        subjectIds.addAll(targetItemBySubject.keySet());
        Map<Long, String> subjectNameMap = Collections.emptyMap();
        if (!subjectIds.isEmpty()) {
            List<CostSubject> subjects = costSubjectMapper.selectByIds(subjectIds);
            subjectNameMap = subjects.stream()
                    .collect(Collectors.toMap(CostSubject::getId, CostSubject::getSubjectName, (a, b) -> a));
        }

        // 5. Compute project-level values (same for all subjects)
        BigDecimal projectEstimatedRemainingCost = assembler.computeProjectEstimatedRemainingCost(tenantId, projectId);
        BigDecimal projectContractIncome = assembler.computeProjectContractIncome(tenantId, projectId);
        BigDecimal projectConfirmedRevenue = assembler.computeProjectConfirmedRevenue(tenantId, projectId);
        BigDecimal projectPaidAmount = assembler.computeProjectPaidAmount(tenantId, projectId);
        Map<String, Object> latestForecast = latestConfirmedForecast(tenantId, projectId);

        // 6. For each cost subject, calculate and insert summary
        LocalDate today = LocalDate.now();
        List<CostSummary> summaries = new ArrayList<>();

        for (Long costSubjectId : subjectIds) {
            List<CostItem> subjectItems = itemsBySubject.getOrDefault(costSubjectId, Collections.emptyList());
            CostTargetItem targetItem = targetItemBySubject.get(costSubjectId);
            BigDecimal subjectTargetCost = activeTarget == null ? targetCost
                    : targetItem == null || targetItem.getTargetAmount() == null ? BigDecimal.ZERO : targetItem.getTargetAmount();
            BigDecimal subjectResponsibility = activeTarget == null ? targetCost
                    : targetItem == null || targetItem.getResponsibilityAmount() == null ? BigDecimal.ZERO : targetItem.getResponsibilityAmount();

            BigDecimal contractLockedCost = subjectItems.stream()
                    .filter(item -> "CT_CONTRACT".equals(item.getSourceType()))
                    .filter(item -> item.getContractId() == null || !mainContractIds.contains(item.getContractId()))
                    .map(CostItem::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Aggregate actualCost: include all cost source types plus bid_cost and overhead
            BigDecimal actualCost = subjectItems.stream()
                    .filter(assembler::isActualCostSource)
                    .map(CostItem::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal paidAmount = projectPaidAmount;
            BigDecimal estimatedRemainingCost = projectEstimatedRemainingCost;
            BigDecimal dynamicCost = actualCost.add(estimatedRemainingCost);
            BigDecimal costDeviation = dynamicCost.subtract(subjectTargetCost);
            BigDecimal confirmedRevenue = projectConfirmedRevenue;
            // Keep subject rows aligned with project/batch summaries and the V27 backfill contract.
            BigDecimal expectedProfit = projectContractIncome.subtract(dynamicCost);

            CostSummary summary = new CostSummary();
            summary.setTenantId(tenantId);
            summary.setProjectId(projectId);
            summary.setSummaryDate(today);
            summary.setCostSubjectId(costSubjectId);
            summary.setCostTargetId(activeTarget == null ? null : activeTarget.getId());
            summary.setCostForecastId(longNullable(latestForecast.get("id")));
            summary.setTargetCost(subjectTargetCost);
            summary.setContractLockedCost(contractLockedCost);
            summary.setActualCost(actualCost);
            summary.setPaidAmount(paidAmount);
            summary.setEstimatedRemainingCost(estimatedRemainingCost);
            summary.setDynamicCost(dynamicCost);
            summary.setContractIncome(projectContractIncome);
            summary.setConfirmedRevenue(projectConfirmedRevenue);
            summary.setExpectedProfit(expectedProfit);
            summary.setCostDeviation(costDeviation);
            summary.setResponsibilityCost(subjectResponsibility);
            summary.setForecastAtCompletionCost(decimal(latestForecast.get("forecast_at_completion_amount")));
            summary.setForecastProfit(decimal(latestForecast.get("forecast_profit_amount")));
            summary.setProfitMargin(decimal(latestForecast.get("profit_margin")));

            summaries.add(summary);
        }

        // 6. Batch insert
        for (CostSummary summary : summaries) {
            costSummaryMapper.insert(summary);
        }

        log.info("Cost summary refreshed for projectId={}: {} subject(s) updated", projectId, summaries.size());
        return getProjectSummary(tenantId, projectId);
    }

    public List<CostSummaryVO> getSummary(Long projectId) {
        return getSummary(UserContext.getCurrentTenantId(), projectId);
    }

    public List<CostSummaryVO> getSummary(Long tenantId, Long projectId) {

        // Find the latest summary_date for this project
        LambdaQueryWrapper<CostSummary> dateWrapper = new LambdaQueryWrapper<>();
        dateWrapper.eq(CostSummary::getTenantId, tenantId);
        dateWrapper.eq(CostSummary::getProjectId, projectId);
        dateWrapper.orderByDesc(CostSummary::getSummaryDate);
        Page<CostSummary> page = new Page<>(0, 1);
        Page<CostSummary> result = costSummaryMapper.selectPage(page, dateWrapper);
        CostSummary latest = result.getRecords().isEmpty() ? null : result.getRecords().get(0);

        if (latest == null) {
            return Collections.emptyList();
        }

        // Get all rows with that summary_date
        LambdaQueryWrapper<CostSummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSummary::getTenantId, tenantId);
        wrapper.eq(CostSummary::getProjectId, projectId);
        wrapper.eq(CostSummary::getSummaryDate, latest.getSummaryDate());
        wrapper.orderByAsc(CostSummary::getCostSubjectId);

        List<CostSummary> summaries = costSummaryMapper.selectList(wrapper);
        return assembler.toVOList(summaries);
    }

    public CostProjectSummaryVO getProjectSummary(Long projectId) {
        projectAccessChecker.checkAccess(projectId, "查看成本摘要");
        return getProjectSummary(UserContext.getCurrentTenantId(), projectId);
    }

    public CostProjectSummaryVO getProjectSummary(Long tenantId, Long projectId) {
        PmProject project = assembler.requireProjectInTenant(tenantId, projectId);
        List<CostSummaryVO> subjects = getSummary(tenantId, projectId);

        String projectName = project.getProjectName();
        CostTarget activeTarget = costTargetMapper.selectOne(new LambdaQueryWrapper<CostTarget>()
                .eq(CostTarget::getTenantId, tenantId).eq(CostTarget::getProjectId, projectId)
                .eq(CostTarget::getIsActive, 1).eq(CostTarget::getApprovalStatus, "APPROVED")
                .eq(CostTarget::getStatus, "ACTIVE").last("LIMIT 1")); // SQL-SAFETY: fixed-sql-fragment
        BigDecimal targetCost = activeTarget != null && activeTarget.getTotalTargetAmount() != null
                ? activeTarget.getTotalTargetAmount() : project.getTargetCost() != null ? project.getTargetCost() : BigDecimal.ZERO;

        BigDecimal contractLockedCost = subjects.stream()
                .map(s -> new BigDecimal(s.getContractLockedCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actualCost = subjects.stream()
                .map(s -> new BigDecimal(s.getActualCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // paidAmount is project-level (same value on every subject row) — take from first subject, not sum
        BigDecimal paidAmount = subjects.isEmpty() ? BigDecimal.ZERO
                : new BigDecimal(subjects.get(0).getPaidAmount());

        // Project-level fields: compute directly (not aggregated from subjects, to avoid N× duplication)
        BigDecimal estimatedRemainingCost = assembler.computeProjectEstimatedRemainingCost(tenantId, projectId);
        BigDecimal contractIncome = assembler.computeProjectContractIncome(tenantId, projectId);
        BigDecimal projectConfirmedRevenue = assembler.computeProjectConfirmedRevenue(tenantId, projectId);
        BigDecimal dynamicCost = actualCost.add(estimatedRemainingCost);
        BigDecimal expectedProfit = contractIncome.subtract(dynamicCost);
        BigDecimal costDeviation = dynamicCost.subtract(targetCost);

        CostProjectSummaryVO vo = new CostProjectSummaryVO();
        vo.setProjectId(projectId.toString());
        vo.setProjectName(projectName);
        vo.setTargetCost(targetCost.toPlainString());
        vo.setContractLockedCost(contractLockedCost.toPlainString());
        vo.setActualCost(actualCost.toPlainString());
        vo.setPaidAmount(paidAmount.toPlainString());
        vo.setEstimatedRemainingCost(estimatedRemainingCost.toPlainString());
        vo.setDynamicCost(dynamicCost.toPlainString());
        vo.setContractIncome(contractIncome.toPlainString());
        vo.setConfirmedRevenue(projectConfirmedRevenue.toPlainString());
        vo.setExpectedProfit(expectedProfit.toPlainString());
        vo.setCostDeviation(costDeviation.toPlainString());
        vo.setCostTargetId(activeTarget == null ? null : String.valueOf(activeTarget.getId()));
        Map<String, Object> forecast = latestConfirmedForecast(tenantId, projectId);
        vo.setCostForecastId(forecast.isEmpty() ? null : String.valueOf(forecast.get("id")));
        vo.setResponsibilityCost(decimal(forecast.getOrDefault("responsibility_amount", targetCost)).toPlainString());
        vo.setForecastAtCompletionCost(decimal(forecast.getOrDefault("forecast_at_completion_amount", dynamicCost)).toPlainString());
        vo.setForecastProfit(decimal(forecast.getOrDefault("forecast_profit_amount", expectedProfit)).toPlainString());
        vo.setProfitMargin(decimal(forecast.get("profit_margin")).toPlainString());
        vo.setSubjects(subjects);
        return vo;
    }

    /**
     * Batch-load project summaries for multiple projects at once.
     * Replaces N+1 pattern where callers loop over projects calling
     * {@link #getProjectSummary(Long, Long)} individually.
     * <p>
     * Reduces ~8 SQL queries per project to ~6 total queries regardless of project count.
     * Subjects list is returned empty — callers that need per-subject breakdowns
     * should use the single-project method instead.
     */
    public Map<Long, CostProjectSummaryVO> getBatchProjectSummaries(Long tenantId, List<Long> projectIds) {
        if (CollectionUtils.isEmpty(projectIds)) {
            return Collections.emptyMap();
        }

        // 1. Batch load projects
        List<PmProject> projects = projectMapper.selectByIds(projectIds);
        Map<Long, PmProject> projectMap = projects.stream()
                .filter(p -> Objects.equals(p.getTenantId(), tenantId))
                .collect(Collectors.toMap(PmProject::getId, p -> p, (a, b) -> a));

        if (projectMap.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> validProjectIds = new ArrayList<>(projectMap.keySet());

        // 2. Batch load cost_summary for all projects
        List<CostSummary> allSummaries = costSummaryMapper.selectList(
                new LambdaQueryWrapper<CostSummary>()
                        .eq(CostSummary::getTenantId, tenantId)
                        .in(CostSummary::getProjectId, validProjectIds));
        Map<Long, List<CostSummary>> summariesByProject = allSummaries.stream()
                .collect(Collectors.groupingBy(CostSummary::getProjectId));

        // 3. Batch load supporting data for project-level computations
        List<CtContract> allContracts = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .in(CtContract::getProjectId, validProjectIds));
        Map<Long, List<CtContract>> contractsByProject = allContracts.stream()
                .collect(Collectors.groupingBy(CtContract::getProjectId));

        List<SubMeasure> allSubMeasures = subMeasureMapper.selectList(
                new LambdaQueryWrapper<SubMeasure>()
                        .eq(SubMeasure::getTenantId, tenantId)
                        .in(SubMeasure::getProjectId, validProjectIds)
                        .eq(SubMeasure::getApprovalStatus, "APPROVED"));
        Map<Long, List<SubMeasure>> subMeasuresByProject = allSubMeasures.stream()
                .collect(Collectors.groupingBy(SubMeasure::getProjectId));

        List<MatReceipt> allMatReceipts = matReceiptMapper.selectList(
                new LambdaQueryWrapper<MatReceipt>()
                        .eq(MatReceipt::getTenantId, tenantId)
                        .in(MatReceipt::getProjectId, validProjectIds)
                        .eq(MatReceipt::getApprovalStatus, "APPROVED"));
        Map<Long, List<MatReceipt>> matReceiptsByProject = allMatReceipts.stream()
                .collect(Collectors.groupingBy(MatReceipt::getProjectId));

        // 4. Build result map
        Map<Long, CostProjectSummaryVO> result = new LinkedHashMap<>();
        for (Long projectId : validProjectIds) {
            PmProject project = projectMap.get(projectId);

            // Find latest summary_date for this project
            List<CostSummary> projectSummaries = summariesByProject.getOrDefault(projectId, Collections.emptyList());
            LocalDate latestDate = projectSummaries.stream()
                    .map(CostSummary::getSummaryDate)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);

            List<CostSummary> latestSummaries = latestDate != null
                    ? projectSummaries.stream()
                        .filter(s -> latestDate.equals(s.getSummaryDate()))
                        .collect(Collectors.toList())
                    : Collections.emptyList();

            BigDecimal targetCost = project.getTargetCost() != null ? project.getTargetCost() : BigDecimal.ZERO;
            BigDecimal contractLockedCost = latestSummaries.stream()
                    .map(s -> s.getContractLockedCost() != null ? s.getContractLockedCost() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal actualCost = latestSummaries.stream()
                    .map(s -> s.getActualCost() != null ? s.getActualCost() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // paidAmount is project-level (same value on every subject row) — take from first subject, not sum
            BigDecimal paidAmount = latestSummaries.isEmpty() ? BigDecimal.ZERO
                    : (latestSummaries.get(0).getPaidAmount() != null ? latestSummaries.get(0).getPaidAmount() : BigDecimal.ZERO);

            // Compute project-level values from batched data
            List<CtContract> projectContracts = contractsByProject.getOrDefault(projectId, Collections.emptyList());
            BigDecimal totalCurrentAmount = projectContracts.stream()
                    .filter(c -> !"MAIN".equals(c.getContractType()))
                    .map(c -> c.getCurrentAmount() != null ? c.getCurrentAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<SubMeasure> projectSubMeasures = subMeasuresByProject.getOrDefault(projectId, Collections.emptyList());
            BigDecimal confirmedMeasureAmount = projectSubMeasures.stream()
                    .map(m -> m.getApprovedAmount() != null ? m.getApprovedAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<MatReceipt> projectMatReceipts = matReceiptsByProject.getOrDefault(projectId, Collections.emptyList());
            BigDecimal confirmedReceiptAmount = projectMatReceipts.stream()
                    .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal estimatedRemainingCost = totalCurrentAmount
                    .subtract(confirmedMeasureAmount).subtract(confirmedReceiptAmount).max(BigDecimal.ZERO);
            BigDecimal contractIncome = projectContracts.stream()
                    .filter(c -> "MAIN".equals(c.getContractType()))
                    .map(c -> c.getCurrentAmount() != null ? c.getCurrentAmount()
                            : c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal projectConfirmedRevenue = assembler.computeBatchProjectConfirmedRevenue(tenantId, projectId);
            BigDecimal dynamicCost = actualCost.add(estimatedRemainingCost);
            BigDecimal expectedProfit = contractIncome.subtract(dynamicCost);
            BigDecimal costDeviation = dynamicCost.subtract(targetCost);

            CostProjectSummaryVO vo = new CostProjectSummaryVO();
            vo.setProjectId(projectId.toString());
            vo.setProjectName(project.getProjectName());
            vo.setTargetCost(targetCost.toPlainString());
            vo.setContractLockedCost(contractLockedCost.toPlainString());
            vo.setActualCost(actualCost.toPlainString());
            vo.setPaidAmount(paidAmount.toPlainString());
            vo.setEstimatedRemainingCost(estimatedRemainingCost.toPlainString());
            vo.setDynamicCost(dynamicCost.toPlainString());
            vo.setContractIncome(contractIncome.toPlainString());
            vo.setConfirmedRevenue(projectConfirmedRevenue.toPlainString());
            vo.setExpectedProfit(expectedProfit.toPlainString());
            vo.setCostDeviation(costDeviation.toPlainString());
            vo.setResponsibilityCost(targetCost.toPlainString());
            vo.setForecastAtCompletionCost(dynamicCost.toPlainString());
            vo.setForecastProfit(expectedProfit.toPlainString());
            vo.setProfitMargin(contractIncome.compareTo(BigDecimal.ZERO) == 0 ? "0.000000" : expectedProfit.divide(contractIncome, 6, java.math.RoundingMode.HALF_UP).toPlainString());
            vo.setSubjects(Collections.emptyList());

            result.put(projectId, vo);
        }

        return result;
    }

    public List<CostSummaryVO> getSummaryHistory(Long projectId) {
        Long tenantId = UserContext.getCurrentTenantId();
        projectAccessChecker.checkAccess(projectId, "查看成本摘要历史");

        LambdaQueryWrapper<CostSummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSummary::getTenantId, tenantId);
        wrapper.eq(CostSummary::getProjectId, projectId);
        wrapper.orderByDesc(CostSummary::getSummaryDate, CostSummary::getCostSubjectId);

        List<CostSummary> summaries = costSummaryMapper.selectList(wrapper);
        return assembler.toVOList(summaries);
    }

    /**
     * Scheduled task: refresh cost summary for all active projects every hour.
     * Placeholder — will be enhanced in later phases (e.g. event-driven refresh).
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scheduledRefresh() {
        if (!scheduledRefreshRunning.compareAndSet(false, true)) {
            log.warn("Previous scheduled cost summary refresh still running, skipping this trigger");
            return;
        }
        log.info("Starting scheduled cost summary refresh...");
        try {
            LambdaQueryWrapper<PmProject> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PmProject::getStatus, "ACTIVE");
            List<PmProject> activeProjects = projectMapper.selectList(wrapper);

            log.info("Found {} active projects for cost summary refresh", activeProjects.size());
            for (PmProject project : activeProjects) {
                try {
                    // M-004: Use AOP proxy to ensure @Transactional is applied
                    ((CostSummaryService) AopContext.currentProxy()).refreshSummary(project.getTenantId(), project.getId());
                } catch (Exception e) {
                    log.error("Failed to refresh summary for project {}", project.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Scheduled cost summary refresh failed", e);
        } finally {
            scheduledRefreshRunning.set(false);
        }
        log.info("Scheduled cost summary refresh completed");
    }

    /**
     * Update paidAmount in cost_summary for a given project.
     * Called by PayRecordService after pay record changes.
     * <p>
     * <b>Concurrency protection:</b> acquires the same per-project
     * {@link ReentrantLock} as {@link #refreshSummary(Long, Long)} so
     * the UPDATE neither races with a concurrent DELETE+INSERT (lost
     * update) nor misses a pay_record inserted after the snapshot of a
     * running refresh.  See M-006.
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePaidAmount(Long projectId) {
        updatePaidAmount(UserContext.getCurrentTenantId(), projectId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePaidAmount(Long tenantId, Long projectId) {
        ReentrantLock lock = refreshLocks.computeIfAbsent(projectId, k -> new ReentrantLock());
        lock.lock();
        try {
            doUpdatePaidAmount(tenantId, projectId);
        } finally {
            lock.unlock();
            if (!lock.isLocked() && !lock.hasQueuedThreads()) {
                refreshLocks.remove(projectId, lock);
            }
        }
    }

    private void doUpdatePaidAmount(Long tenantId, Long projectId) {
        List<PayRecord> records = payRecordMapper.selectList(
                new LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getTenantId, tenantId)
                        .eq(PayRecord::getProjectId, projectId)
                        .eq(PayRecord::getPayStatus, "SUCCESS"));
        BigDecimal totalPaid = records.stream()
                .map(r -> r.getPayAmount() == null ? BigDecimal.ZERO : r.getPayAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        costSummaryMapper.update(null, new LambdaUpdateWrapper<CostSummary>()
                .eq(CostSummary::getTenantId, tenantId)
                .eq(CostSummary::getProjectId, projectId)
                .set(CostSummary::getPaidAmount, totalPaid));
    }

    private Map<String, Object> latestConfirmedForecast(Long tenantId, Long projectId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM cost_forecast WHERE tenant_id=? AND project_id=? AND status IN('ACTION_REQUIRED','CONTROLLED') AND deleted_flag=0 ORDER BY version_no DESC LIMIT 1", tenantId, projectId);
        return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        return value instanceof BigDecimal amount ? amount : new BigDecimal(String.valueOf(value));
    }

    private static Long longNullable(Object value) {
        if (value == null) return null;
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }
}
