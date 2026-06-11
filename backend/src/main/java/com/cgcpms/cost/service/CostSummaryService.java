package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostSummaryService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CostSummaryMapper costSummaryMapper;
    private final CostItemMapper costItemMapper;
    private final PmProjectMapper projectMapper;
    private final CostSubjectMapper costSubjectMapper;
    private final PayRecordMapper payRecordMapper;

    @Transactional
    public CostProjectSummaryVO refreshSummary(Long projectId) {
        return refreshSummary(UserContext.getCurrentTenantId(), projectId);
    }

    @Transactional
    public CostProjectSummaryVO refreshSummary(Long tenantId, Long projectId) {
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

        // 5. For each cost subject, calculate and insert summary
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

            BigDecimal actualCost = subjectItems.stream()
                    .filter(item -> "MAT_RECEIPT".equals(item.getSourceType())
                            || "SUB_MEASURE".equals(item.getSourceType()))
                    .map(CostItem::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal paidAmount = BigDecimal.ZERO;
            BigDecimal estimatedRemainingCost = BigDecimal.ZERO;
            BigDecimal dynamicCost = contractLockedCost.add(actualCost).add(estimatedRemainingCost);
            BigDecimal costDeviation = dynamicCost.subtract(targetCost);
            BigDecimal contractIncome = BigDecimal.ZERO;
            BigDecimal expectedProfit = BigDecimal.ZERO;

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
            summary.setContractIncome(contractIncome);
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
        dateWrapper.last("LIMIT 1");
        CostSummary latest = costSummaryMapper.selectOne(dateWrapper);

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
        BigDecimal dynamicCost = contractLockedCost.add(actualCost);
        BigDecimal costDeviation = dynamicCost.subtract(targetCost);

        CostProjectSummaryVO vo = new CostProjectSummaryVO();
        vo.setProjectId(projectId.toString());
        vo.setProjectName(projectName);
        vo.setTargetCost(targetCost.toPlainString());
        vo.setContractLockedCost(contractLockedCost.toPlainString());
        vo.setActualCost(actualCost.toPlainString());
        vo.setPaidAmount(paidAmount.toPlainString());
        vo.setDynamicCost(dynamicCost.toPlainString());
        vo.setCostDeviation(costDeviation.toPlainString());
        vo.setSubjects(subjects);
        return vo;
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
                    refreshSummary(project.getTenantId(), project.getId());
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
            vo.setExpectedProfit(s.getExpectedProfit() != null ? s.getExpectedProfit().toPlainString() : "0");
            vo.setCostDeviation(s.getCostDeviation() != null ? s.getCostDeviation().toPlainString() : "0");
            vo.setCreatedBy(s.getCreatedBy() != null ? s.getCreatedBy().toString() : null);
            vo.setCreatedAt(s.getCreatedAt() != null ? DTF.format(s.getCreatedAt()) : null);
            vo.setUpdatedAt(s.getUpdatedAt() != null ? DTF.format(s.getUpdatedAt()) : null);
            vo.setRemark(s.getRemark());
            return vo;
        }).collect(Collectors.toList());
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
