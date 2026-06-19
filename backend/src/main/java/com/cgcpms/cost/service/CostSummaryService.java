package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.cost.vo.CostProjectSummaryVO;
import com.cgcpms.cost.vo.CostSummaryVO;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.cgcpms.common.util.DateTimeUtils;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
// TODO: 拆分超大文件 (634行) — 拆分为 CostSummaryQueryService + CostSummaryWriteService + CostSummaryAssembler
public class CostSummaryService {

    private final CostSummaryMapper costSummaryMapper;
    private final CostItemMapper costItemMapper;
    private final PmProjectMapper projectMapper;
    private final CostSubjectMapper costSubjectMapper;
    private final PayRecordMapper payRecordMapper;
    private final CtContractMapper ctContractMapper;
    private final SubMeasureMapper subMeasureMapper;
    private final MatReceiptMapper matReceiptMapper;
    private final VarOrderMapper varOrderMapper;

    /**
     * Per-project locks to serialize concurrent {@link #refreshSummary(Long, Long)} calls
     * for the same project. The scheduled task and manual refresh endpoint can both fire
     * simultaneously; without this lock, two refreshes would interleave DELETE+INSERT
     * and produce incorrect or duplicate data.
     */
    private final ConcurrentHashMap<Long, ReentrantLock> refreshLocks = new ConcurrentHashMap<>();

    @Transactional
    public CostProjectSummaryVO refreshSummary(Long projectId) {
        return refreshSummary(UserContext.getCurrentTenantId(), projectId);
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
    @Transactional(isolation = Isolation.REPEATABLE_READ)
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

        PmProject project = requireProjectInTenant(tenantId, projectId);

        // 1. Physically replace generated snapshot rows; logical deletes keep the same unique key occupied.
        costSummaryMapper.physicalDeleteByTenantAndProject(tenantId, projectId);

        // 2. Get project targetCost
        BigDecimal targetCost = (project != null && project.getTargetCost() != null)
                ? project.getTargetCost() : BigDecimal.ZERO;
        log.debug("Project targetCost={}", targetCost);

        // 3. Query all cost items for this project, grouped by costSubjectId
        LambdaQueryWrapper<CostItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(CostItem::getTenantId, tenantId);
        itemWrapper.eq(CostItem::getProjectId, projectId);
        List<CostItem> allItems = costItemMapper.selectList(itemWrapper);

        if (CollectionUtils.isEmpty(allItems)) {
            log.info("No cost items found for projectId={}, summary cleared", projectId);
            return getProjectSummary(tenantId, projectId);
        }

        // Group cost items by costSubjectId
        Map<Long, List<CostItem>> itemsBySubject = allItems.stream()
                .filter(item -> item.getCostSubjectId() != null)
                .collect(Collectors.groupingBy(CostItem::getCostSubjectId));

        // 4. Build cost subject name map
        Set<Long> subjectIds = itemsBySubject.keySet();
        Map<Long, String> subjectNameMap = Collections.emptyMap();
        if (!subjectIds.isEmpty()) {
            List<CostSubject> subjects = costSubjectMapper.selectBatchIds(subjectIds);
            subjectNameMap = subjects.stream()
                    .collect(Collectors.toMap(CostSubject::getId, CostSubject::getSubjectName, (a, b) -> a));
        }

        // 5. Compute project-level values (same for all subjects)
        BigDecimal projectEstimatedRemainingCost = computeProjectEstimatedRemainingCost(tenantId, projectId);
        BigDecimal projectContractIncome = computeProjectContractIncome(tenantId, projectId);
        BigDecimal projectConfirmedRevenue = computeProjectConfirmedRevenue(tenantId, projectId);
        BigDecimal projectPaidAmount = computeProjectPaidAmount(tenantId, projectId);

        // 6. For each cost subject, calculate and insert summary
        LocalDate today = LocalDate.now();
        List<CostSummary> summaries = new ArrayList<>();

        for (Map.Entry<Long, List<CostItem>> entry : itemsBySubject.entrySet()) {
            Long costSubjectId = entry.getKey();
            List<CostItem> subjectItems = entry.getValue();

            BigDecimal contractLockedCost = subjectItems.stream()
                    .filter(item -> "CT_CONTRACT".equals(item.getSourceType()))
                    .map(CostItem::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Aggregate actualCost: include all cost source types plus bid_cost and overhead
            BigDecimal actualCost = subjectItems.stream()
                    .filter(item -> "MAT_RECEIPT".equals(item.getSourceType())
                            || "SUB_MEASURE".equals(item.getSourceType())
                            || "BID_COST".equals(item.getSourceType())
                            || "BID_COST_TRANSFERRED".equals(item.getSourceType())
                            || "OVERHEAD_ALLOCATION".equals(item.getSourceType()))
                    .map(CostItem::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal paidAmount = projectPaidAmount;
            BigDecimal estimatedRemainingCost = projectEstimatedRemainingCost;
            BigDecimal dynamicCost = actualCost.add(estimatedRemainingCost);
            BigDecimal costDeviation = dynamicCost.subtract(targetCost);
            BigDecimal confirmedRevenue = projectConfirmedRevenue;
            // expectedProfit = confirmedRevenue - dynamicCost (使用已确认收入)
            BigDecimal expectedProfit = confirmedRevenue.subtract(dynamicCost);

            CostSummary summary = new CostSummary();
            summary.setTenantId(tenantId);
            summary.setProjectId(projectId);
            summary.setSummaryDate(today);
            summary.setCostSubjectId(costSubjectId);
            summary.setTargetCost(targetCost);
            summary.setContractLockedCost(contractLockedCost);
            summary.setActualCost(actualCost);
            summary.setPaidAmount(paidAmount);
            summary.setEstimatedRemainingCost(estimatedRemainingCost);
            summary.setDynamicCost(dynamicCost);
            summary.setContractIncome(projectContractIncome);
            summary.setConfirmedRevenue(projectConfirmedRevenue);
            summary.setExpectedProfit(expectedProfit);
            summary.setCostDeviation(costDeviation);

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
        return toVOList(summaries);
    }

    public CostProjectSummaryVO getProjectSummary(Long projectId) {
        return getProjectSummary(UserContext.getCurrentTenantId(), projectId);
    }

    public CostProjectSummaryVO getProjectSummary(Long tenantId, Long projectId) {
        PmProject project = requireProjectInTenant(tenantId, projectId);
        List<CostSummaryVO> subjects = getSummary(tenantId, projectId);

        String projectName = project.getProjectName();
        BigDecimal targetCost = (project.getTargetCost() != null)
                ? project.getTargetCost() : BigDecimal.ZERO;

        BigDecimal contractLockedCost = subjects.stream()
                .map(s -> new BigDecimal(s.getContractLockedCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actualCost = subjects.stream()
                .map(s -> new BigDecimal(s.getActualCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidAmount = subjects.stream()
                .map(s -> new BigDecimal(s.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Project-level fields: compute directly (not aggregated from subjects, to avoid N× duplication)
        BigDecimal estimatedRemainingCost = computeProjectEstimatedRemainingCost(tenantId, projectId);
        BigDecimal contractIncome = computeProjectContractIncome(tenantId, projectId);
        BigDecimal projectConfirmedRevenue = computeProjectConfirmedRevenue(tenantId, projectId);
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
        List<PmProject> projects = projectMapper.selectBatchIds(projectIds);
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

        List<VarOrder> allVarOrders = varOrderMapper.selectList(
                new LambdaQueryWrapper<VarOrder>()
                        .eq(VarOrder::getTenantId, tenantId)
                        .in(VarOrder::getProjectId, validProjectIds)
                        .eq(VarOrder::getDirection, "INCOME")
                        .eq(VarOrder::getApprovalStatus, "APPROVED"));
        Map<Long, List<VarOrder>> varOrdersByProject = allVarOrders.stream()
                .collect(Collectors.groupingBy(VarOrder::getProjectId));

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
            BigDecimal paidAmount = latestSummaries.stream()
                    .map(s -> s.getPaidAmount() != null ? s.getPaidAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Compute project-level values from batched data
            List<CtContract> projectContracts = contractsByProject.getOrDefault(projectId, Collections.emptyList());
            BigDecimal totalCurrentAmount = projectContracts.stream()
                    .map(c -> c.getCurrentAmount() != null ? c.getCurrentAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalContractAmount = projectContracts.stream()
                    .map(c -> c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<SubMeasure> projectSubMeasures = subMeasuresByProject.getOrDefault(projectId, Collections.emptyList());
            BigDecimal confirmedMeasureAmount = projectSubMeasures.stream()
                    .map(m -> m.getApprovedAmount() != null ? m.getApprovedAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<MatReceipt> projectMatReceipts = matReceiptsByProject.getOrDefault(projectId, Collections.emptyList());
            BigDecimal confirmedReceiptAmount = projectMatReceipts.stream()
                    .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<VarOrder> projectVarOrders = varOrdersByProject.getOrDefault(projectId, Collections.emptyList());
            BigDecimal incomeVarAmount = projectVarOrders.stream()
                    .map(v -> v.getApprovedAmount() != null ? v.getApprovedAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal estimatedRemainingCost = totalCurrentAmount
                    .subtract(confirmedMeasureAmount).subtract(confirmedReceiptAmount);
            BigDecimal contractIncome = totalContractAmount.add(incomeVarAmount);
            BigDecimal projectConfirmedRevenue = BigDecimal.ZERO; // 批量汇总无明细查询
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
            vo.setSubjects(Collections.emptyList());

            result.put(projectId, vo);
        }

        return result;
    }

    public List<CostSummaryVO> getSummaryHistory(Long projectId) {
        Long tenantId = UserContext.getCurrentTenantId();

        LambdaQueryWrapper<CostSummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSummary::getTenantId, tenantId);
        wrapper.eq(CostSummary::getProjectId, projectId);
        wrapper.orderByDesc(CostSummary::getSummaryDate, CostSummary::getCostSubjectId);

        List<CostSummary> summaries = costSummaryMapper.selectList(wrapper);
        return toVOList(summaries);
    }

    /**
     * Scheduled task: refresh cost summary for all active projects every hour.
     * Placeholder — will be enhanced in later phases (e.g. event-driven refresh).
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scheduledRefresh() {
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
        }
        log.info("Scheduled cost summary refresh completed");
    }

    /**
     * Update paidAmount in cost_summary for a given project.
     * Called by PayRecordService after pay record changes.
     */
    @Transactional
    public void updatePaidAmount(Long projectId) {
        updatePaidAmount(UserContext.getCurrentTenantId(), projectId);
    }

    @Transactional
    public void updatePaidAmount(Long tenantId, Long projectId) {
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

    private List<CostSummaryVO> toVOList(List<CostSummary> summaries) {
        if (CollectionUtils.isEmpty(summaries)) {
            return Collections.emptyList();
        }

        // Batch load project names
        Set<Long> projectIds = summaries.stream()
                .map(CostSummary::getProjectId)
                .collect(Collectors.toSet());
        Map<Long, String> projectNameMap = Collections.emptyMap();
        if (!projectIds.isEmpty()) {
            List<PmProject> projects = projectMapper.selectBatchIds(projectIds);
            projectNameMap = projects.stream()
                    .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        }

        // Batch load cost subject names
        Set<Long> subjectIds = summaries.stream()
                .map(CostSummary::getCostSubjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> subjectNameMap = Collections.emptyMap();
        if (!subjectIds.isEmpty()) {
            List<CostSubject> subjects = costSubjectMapper.selectBatchIds(subjectIds);
            subjectNameMap = subjects.stream()
                    .collect(Collectors.toMap(CostSubject::getId, CostSubject::getSubjectName, (a, b) -> a));
        }

        Map<Long, String> finalProjectNameMap = projectNameMap;
        Map<Long, String> finalSubjectNameMap = subjectNameMap;
        return summaries.stream().map(s -> {
            CostSummaryVO vo = new CostSummaryVO();
            vo.setId(s.getId() != null ? s.getId().toString() : null);
            vo.setTenantId(s.getTenantId() != null ? s.getTenantId().toString() : null);
            vo.setProjectId(s.getProjectId() != null ? s.getProjectId().toString() : null);
            vo.setProjectName(s.getProjectId() != null ? finalProjectNameMap.getOrDefault(s.getProjectId(), "") : "");
            vo.setSummaryDate(s.getSummaryDate() != null ? s.getSummaryDate().toString() : null);
            vo.setCostSubjectId(s.getCostSubjectId() != null ? s.getCostSubjectId().toString() : null);
            vo.setCostSubjectName(s.getCostSubjectId() != null ? finalSubjectNameMap.getOrDefault(s.getCostSubjectId(), "") : "");
            vo.setTargetCost(s.getTargetCost() != null ? s.getTargetCost().toPlainString() : "0");
            vo.setContractLockedCost(s.getContractLockedCost() != null ? s.getContractLockedCost().toPlainString() : "0");
            vo.setActualCost(s.getActualCost() != null ? s.getActualCost().toPlainString() : "0");
            vo.setPaidAmount(s.getPaidAmount() != null ? s.getPaidAmount().toPlainString() : "0");
            vo.setEstimatedRemainingCost(s.getEstimatedRemainingCost() != null ? s.getEstimatedRemainingCost().toPlainString() : "0");
            vo.setDynamicCost(s.getDynamicCost() != null ? s.getDynamicCost().toPlainString() : "0");
            vo.setContractIncome(s.getContractIncome() != null ? s.getContractIncome().toPlainString() : "0");
            vo.setConfirmedRevenue(s.getConfirmedRevenue() != null ? s.getConfirmedRevenue().toPlainString() : "0");
            vo.setExpectedProfit(s.getExpectedProfit() != null ? s.getExpectedProfit().toPlainString() : "0");
            vo.setCostDeviation(s.getCostDeviation() != null ? s.getCostDeviation().toPlainString() : "0");
            vo.setCreatedBy(s.getCreatedBy() != null ? s.getCreatedBy().toString() : null);
            vo.setCreatedAt(s.getCreatedAt() != null ? DateTimeUtils.DTF.format(s.getCreatedAt()) : null);
            vo.setUpdatedAt(s.getUpdatedAt() != null ? DateTimeUtils.DTF.format(s.getUpdatedAt()) : null);
            vo.setRemark(s.getRemark());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * Compute project-level estimatedRemainingCost:
     * SUM(ct_contract.currentAmount) - SUM(sub_measure.approvedAmount WHERE approved)
     *                                 - SUM(mat_receipt.totalAmount WHERE approved)
     * <p>
     * 注意：getBatchProjectSummaries 中有等价的批量内联版本（使用预取数据避免逐项目查询）；
     * 修改本方法时必须同步更新 getBatchProjectSummaries 中的对应内联逻辑。
     */
    private BigDecimal computeProjectEstimatedRemainingCost(Long tenantId, Long projectId) {
        BigDecimal totalCurrentAmount = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .eq(CtContract::getProjectId, projectId))
                .stream()
                .map(c -> c.getCurrentAmount() != null ? c.getCurrentAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal confirmedMeasureAmount = subMeasureMapper.selectList(
                new LambdaQueryWrapper<SubMeasure>()
                        .eq(SubMeasure::getTenantId, tenantId)
                        .eq(SubMeasure::getProjectId, projectId)
                        .eq(SubMeasure::getApprovalStatus, "APPROVED"))
                .stream()
                .map(m -> m.getApprovedAmount() != null ? m.getApprovedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal confirmedReceiptAmount = matReceiptMapper.selectList(
                new LambdaQueryWrapper<MatReceipt>()
                        .eq(MatReceipt::getTenantId, tenantId)
                        .eq(MatReceipt::getProjectId, projectId)
                        .eq(MatReceipt::getApprovalStatus, "APPROVED"))
                .stream()
                .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalCurrentAmount.subtract(confirmedMeasureAmount).subtract(confirmedReceiptAmount);
    }

    /**
     * Compute project-level contractIncome:
     * SUM(ct_contract.contractAmount) + SUM(var_order.approvedAmount WHERE direction='INCOME' AND approved)
     * <p>
     * 注意：getBatchProjectSummaries 中有等价的批量内联版本（使用预取数据避免逐项目查询）；
     * 修改本方法时必须同步更新 getBatchProjectSummaries 中的对应内联逻辑。
     */
    private BigDecimal computeProjectContractIncome(Long tenantId, Long projectId) {
        BigDecimal totalContractAmount = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .eq(CtContract::getProjectId, projectId))
                .stream()
                .map(c -> c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal incomeVarAmount = varOrderMapper.selectList(
                new LambdaQueryWrapper<VarOrder>()
                        .eq(VarOrder::getTenantId, tenantId)
                        .eq(VarOrder::getProjectId, projectId)
                        .eq(VarOrder::getDirection, "INCOME")
                        .eq(VarOrder::getApprovalStatus, "APPROVED"))
                .stream()
                .map(v -> v.getApprovedAmount() != null ? v.getApprovedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalContractAmount.add(incomeVarAmount);
    }

    /**
     * Compute project-level paidAmount:
     * SUM(pay_record.payAmount WHERE payStatus='SUCCESS')
     */
    private BigDecimal computeProjectPaidAmount(Long tenantId, Long projectId) {
        List<PayRecord> records = payRecordMapper.selectList(
                new LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getTenantId, tenantId)
                        .eq(PayRecord::getProjectId, projectId)
                        .eq(PayRecord::getPayStatus, "SUCCESS"));
        return records.stream()
                .map(r -> r.getPayAmount() == null ? BigDecimal.ZERO : r.getPayAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Compute project-level confirmed revenue:
     * SUM(cost_item.amount WHERE cost_type='REVENUE_CONFIRMED')
     */
    private BigDecimal computeProjectConfirmedRevenue(Long tenantId, Long projectId) {
        return costItemMapper.selectList(
                new LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getTenantId, tenantId)
                        .eq(CostItem::getProjectId, projectId)
                        .eq(CostItem::getCostType, "REVENUE_CONFIRMED")
                        .eq(CostItem::getCostStatus, "CONFIRMED"))
                .stream()
                .map(CostItem::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PmProject requireProjectInTenant(Long tenantId, Long projectId) {
        if (tenantId == null || projectId == null) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        PmProject project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        return project;
    }
}
