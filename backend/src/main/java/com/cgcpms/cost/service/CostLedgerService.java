package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.vo.CostLedgerSummaryVO;
import com.cgcpms.cost.vo.CostLedgerVO;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.cgcpms.common.util.DateTimeUtils;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostLedgerService {

    private final CostItemMapper costItemMapper;
    private final PmProjectMapper pmProjectMapper;
    private final CtContractMapper ctContractMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final CostSubjectMapper costSubjectMapper;

    /**
     * Paginated cost ledger query with dynamic filters and batch name resolution.
     */
    public IPage<CostLedgerVO> getPage(long pageNo, long pageSize,
                                       Long projectId, Long contractId, Long partnerId, Long costSubjectId,
                                       String costType, String sourceType, String costStatus,
                                       LocalDate startDate, LocalDate endDate, String keyword) {
        log.info("Querying cost ledger: projectId={}, pageNo={}", projectId, pageNo);
        LambdaQueryWrapper<CostItem> wrapper = buildFilterWrapper(
                projectId, contractId, partnerId, costSubjectId,
                costType, sourceType, costStatus,
                startDate, endDate, keyword);

        Page<CostItem> page = costItemMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        // Batch resolve names
        List<CostItem> records = page.getRecords();
        Map<Long, String> projectNames = batchResolveProjectNames(records);
        Map<Long, String> contractNames = batchResolveContractNames(records);
        Map<Long, String> partnerNames = batchResolvePartnerNames(records);
        Map<Long, String> subjectNames = batchResolveSubjectNames(records);

        return page.convert(item -> toVO(item, projectNames, contractNames, partnerNames, subjectNames));
    }

    /**
     * Summary statistics for matching cost items.
     */
    public CostLedgerSummaryVO getSummary(Long projectId, Long contractId, Long partnerId, Long costSubjectId,
                                           String costType, String sourceType, String costStatus,
                                           LocalDate startDate, LocalDate endDate, String keyword) {
        LambdaQueryWrapper<CostItem> wrapper = buildFilterWrapper(
                projectId, contractId, partnerId, costSubjectId,
                costType, sourceType, costStatus,
                startDate, endDate, keyword);

        List<CostItem> items = costItemMapper.selectList(wrapper);

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalTaxAmount = BigDecimal.ZERO;
        Map<String, BigDecimal> bySourceType = new LinkedHashMap<>();
        Map<String, BigDecimal> byProjectRaw = new LinkedHashMap<>();
        Map<String, BigDecimal> byCostType = new LinkedHashMap<>();

        // Batch resolve project names for display
        Set<Long> projectIds = items.stream()
                .map(CostItem::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : pmProjectMapper.selectBatchIds(projectIds).stream()
                        .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));

        for (CostItem item : items) {
            BigDecimal amount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
            BigDecimal taxAmount = item.getTaxAmount() != null ? item.getTaxAmount() : BigDecimal.ZERO;

            totalAmount = totalAmount.add(amount);
            totalTaxAmount = totalTaxAmount.add(taxAmount);

            // By source type
            String st = item.getSourceType() != null ? item.getSourceType() : "UNKNOWN";
            bySourceType.merge(st, amount, BigDecimal::add);

            // By project (use project name if available, else projectId)
            String projKey = projectNames.getOrDefault(item.getProjectId(),
                    item.getProjectId() != null ? item.getProjectId().toString() : "UNKNOWN");
            byProjectRaw.merge(projKey, amount, BigDecimal::add);

            // By cost type
            String ct = item.getCostType() != null ? item.getCostType() : "UNKNOWN";
            byCostType.merge(ct, amount, BigDecimal::add);
        }

        CostLedgerSummaryVO summary = new CostLedgerSummaryVO();
        summary.setTotalAmount(totalAmount.toPlainString());
        summary.setTotalTaxAmount(totalTaxAmount.toPlainString());
        summary.setBySourceType(convertToStringMap(bySourceType));
        summary.setByProject(convertToStringMap(byProjectRaw));
        summary.setByCostType(convertToStringMap(byCostType));
        return summary;
    }

    /**
     * Single cost item detail with resolved names.
     */
    public CostLedgerVO getById(Long id) {
        CostItem item = costItemMapper.selectById(id);
        if (item == null || !item.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("COST_ITEM_NOT_FOUND", "成本记录不存在");
        }
        return toVO(item);
    }

    // ---- Private helpers ----

    private LambdaQueryWrapper<CostItem> buildFilterWrapper(
            Long projectId, Long contractId, Long partnerId, Long costSubjectId,
            String costType, String sourceType, String costStatus,
            LocalDate startDate, LocalDate endDate, String keyword) {
        LambdaQueryWrapper<CostItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostItem::getTenantId, UserContext.getCurrentTenantId());
        if (projectId != null) wrapper.eq(CostItem::getProjectId, projectId);
        if (contractId != null) wrapper.eq(CostItem::getContractId, contractId);
        if (partnerId != null) wrapper.eq(CostItem::getPartnerId, partnerId);
        if (costSubjectId != null) wrapper.eq(CostItem::getCostSubjectId, costSubjectId);
        if (StringUtils.hasText(costType)) wrapper.eq(CostItem::getCostType, costType);
        if (StringUtils.hasText(sourceType)) wrapper.eq(CostItem::getSourceType, sourceType);
        if (StringUtils.hasText(costStatus)) wrapper.eq(CostItem::getCostStatus, costStatus);
        if (startDate != null) wrapper.ge(CostItem::getCostDate, startDate);
        if (endDate != null) wrapper.le(CostItem::getCostDate, endDate);
        if (StringUtils.hasText(keyword)) wrapper.like(CostItem::getRemark, keyword);
        wrapper.orderByDesc(CostItem::getCostDate, CostItem::getCreatedAt);
        return wrapper;
    }

    private Map<Long, String> batchResolveProjectNames(List<CostItem> items) {
        Set<Long> ids = items.stream().map(CostItem::getProjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return pmProjectMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
    }

    private Map<Long, String> batchResolveContractNames(List<CostItem> items) {
        Set<Long> ids = items.stream().map(CostItem::getContractId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return ctContractMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));
    }

    private Map<Long, String> batchResolvePartnerNames(List<CostItem> items) {
        Set<Long> ids = items.stream().map(CostItem::getPartnerId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return mdPartnerMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));
    }

    private Map<Long, String> batchResolveSubjectNames(List<CostItem> items) {
        Set<Long> ids = items.stream().map(CostItem::getCostSubjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return costSubjectMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(CostSubject::getId, CostSubject::getSubjectName, (a, b) -> a));
    }

    private CostLedgerVO toVO(CostItem item) {
        return toVO(item, Map.of(), Map.of(), Map.of(), Map.of());
    }

    private CostLedgerVO toVO(CostItem item,
                              Map<Long, String> projectNames,
                              Map<Long, String> contractNames,
                              Map<Long, String> partnerNames,
                              Map<Long, String> subjectNames) {
        CostLedgerVO vo = new CostLedgerVO();
        vo.setId(item.getId() != null ? item.getId().toString() : null);
        vo.setProjectId(item.getProjectId() != null ? item.getProjectId().toString() : null);
        vo.setProjectName(projectNames.get(item.getProjectId()));
        vo.setContractId(item.getContractId() != null ? item.getContractId().toString() : null);
        vo.setContractName(contractNames.get(item.getContractId()));
        vo.setPartnerId(item.getPartnerId() != null ? item.getPartnerId().toString() : null);
        vo.setPartnerName(partnerNames.get(item.getPartnerId()));
        vo.setCostSubjectId(item.getCostSubjectId() != null ? item.getCostSubjectId().toString() : null);
        vo.setCostSubjectName(subjectNames.get(item.getCostSubjectId()));
        vo.setCostType(item.getCostType());
        vo.setAmount(item.getAmount() != null ? item.getAmount().toPlainString() : null);
        vo.setTaxAmount(item.getTaxAmount() != null ? item.getTaxAmount().toPlainString() : null);
        vo.setAmountWithoutTax(item.getAmountWithoutTax() != null ? item.getAmountWithoutTax().toPlainString() : null);
        vo.setSourceType(item.getSourceType());
        vo.setSourceId(item.getSourceId() != null ? item.getSourceId().toString() : null);
        vo.setSourceItemId(item.getSourceItemId() != null ? item.getSourceItemId().toString() : null);
        vo.setCostDate(item.getCostDate() != null ? DateTimeUtils.DATE_FMT.format(item.getCostDate()) : null);
        vo.setCostStatus(item.getCostStatus());
        vo.setGeneratedFlag(item.getGeneratedFlag() != null ? item.getGeneratedFlag().toString() : null);
        vo.setCreatedBy(item.getCreatedBy() != null ? item.getCreatedBy().toString() : null);
        vo.setCreatedAt(item.getCreatedAt() != null ? DateTimeUtils.DTF.format(item.getCreatedAt()) : null);
        vo.setRemark(item.getRemark());
        return vo;
    }

    private Map<String, String> convertToStringMap(Map<String, BigDecimal> bigDecimalMap) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : bigDecimalMap.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toPlainString());
        }
        return result;
    }
}
