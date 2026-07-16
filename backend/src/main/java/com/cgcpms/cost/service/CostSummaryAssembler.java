package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.cost.vo.CostProjectSummaryVO;
import com.cgcpms.cost.vo.CostSummaryVO;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
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
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure computation and VO assembly for cost summary, shared by
 * {@link CostSummaryQueryService} and {@link CostSummaryWriteService}.
 */
@Component
@RequiredArgsConstructor
class CostSummaryAssembler {

    private final CostSummaryMapper costSummaryMapper;
    private final CostItemMapper costItemMapper;
    private final CostSubjectMapper costSubjectMapper;
    private final PmProjectMapper projectMapper;
    private final CtContractMapper ctContractMapper;
    private final SubMeasureMapper subMeasureMapper;
    private final MatReceiptMapper matReceiptMapper;
    private final VarOrderMapper varOrderMapper;
    private final PayRecordMapper payRecordMapper;

    boolean isActualCostSource(CostItem item) {
        if (item == null) return false;
        String sourceType = item.getSourceType();
        return "MAT_RECEIPT".equals(sourceType)
                || "MAT_REQUISITION".equals(sourceType)
                || "SUB_MEASURE".equals(sourceType)
                || "VAR_ORDER".equals(sourceType)
                || "CT_CHANGE".equals(sourceType)
                || "BID_COST".equals(sourceType)
                || "BID_COST_TRANSFERRED".equals(sourceType)
                || "OVERHEAD_ALLOCATION".equals(sourceType);
    }

    PmProject requireProjectInTenant(Long tenantId, Long projectId) {
        if (tenantId == null || projectId == null) {
            throw new com.cgcpms.common.exception.BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        PmProject project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw new com.cgcpms.common.exception.BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        return project;
    }

    List<CostSummaryVO> toVOList(List<CostSummary> summaries) {
        if (CollectionUtils.isEmpty(summaries)) {
            return Collections.emptyList();
        }
        Set<Long> projectIds = summaries.stream()
                .map(CostSummary::getProjectId).collect(Collectors.toSet());
        Map<Long, String> projectNameMap = Collections.emptyMap();
        if (!projectIds.isEmpty()) {
            List<PmProject> projects = projectMapper.selectByIds(projectIds);
            projectNameMap = projects.stream()
                    .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        }
        Set<Long> subjectIds = summaries.stream()
                .map(CostSummary::getCostSubjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> subjectNameMap = Collections.emptyMap();
        if (!subjectIds.isEmpty()) {
            List<CostSubject> subjects = costSubjectMapper.selectByIds(subjectIds);
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

    BigDecimal computeProjectEstimatedRemainingCost(Long tenantId, Long projectId) {
        BigDecimal totalCurrentAmount = ctContractMapper.selectList(
                        new LambdaQueryWrapper<CtContract>()
                                .eq(CtContract::getTenantId, tenantId)
                                .eq(CtContract::getProjectId, projectId)
                                .ne(CtContract::getContractType, "MAIN"))
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

    BigDecimal computeProjectContractIncome(Long tenantId, Long projectId) {
        return ctContractMapper.selectList(
                        new LambdaQueryWrapper<CtContract>()
                                .eq(CtContract::getTenantId, tenantId)
                                .eq(CtContract::getProjectId, projectId)
                                .eq(CtContract::getContractType, "MAIN"))
                .stream()
                .map(c -> c.getCurrentAmount() != null ? c.getCurrentAmount()
                        : c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    BigDecimal computeProjectPaidAmount(Long tenantId, Long projectId) {
        List<PayRecord> records = payRecordMapper.selectList(
                new LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getTenantId, tenantId)
                        .eq(PayRecord::getProjectId, projectId)
                        .eq(PayRecord::getPayStatus, "SUCCESS"));
        return records.stream()
                .map(r -> r.getPayAmount() == null ? BigDecimal.ZERO : r.getPayAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    BigDecimal computeProjectConfirmedRevenue(Long tenantId, Long projectId) {
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

    BigDecimal computeBatchProjectConfirmedRevenue(Long tenantId, Long projectId) {
        return costItemMapper.selectList(
                        new LambdaQueryWrapper<CostItem>()
                                .eq(CostItem::getTenantId, tenantId)
                                .eq(CostItem::getProjectId, projectId)
                                .eq(CostItem::getCostStatus, "CONFIRMED")
                                .eq(CostItem::getSourceType, "REVENUE"))
                .stream()
                .map(CostItem::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
