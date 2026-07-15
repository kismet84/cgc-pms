package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
        log.info("cost ledger query request: pageNo={}", pageNo);
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
        QueryWrapper<CostItem> totalWrapper = buildSummaryFilterWrapper(
                projectId, contractId, partnerId, costSubjectId,
                costType, sourceType, costStatus, startDate, endDate, keyword);
        totalWrapper.select("COALESCE(SUM(amount), 0) AS total_amount",
                "COALESCE(SUM(tax_amount), 0) AS total_tax_amount");
        Map<String, Object> totalRow = firstMap(costItemMapper.selectMaps(totalWrapper));

        Map<String, BigDecimal> bySourceType = selectGroupedAmounts(
                "COALESCE(source_type, 'UNKNOWN')", projectId, contractId, partnerId, costSubjectId,
                costType, sourceType, costStatus, startDate, endDate, keyword);
        Map<String, BigDecimal> byProjectId = selectGroupedAmounts(
                "project_id", projectId, contractId, partnerId, costSubjectId,
                costType, sourceType, costStatus, startDate, endDate, keyword);
        Map<String, BigDecimal> byCostType = selectGroupedAmounts(
                "COALESCE(cost_type, 'UNKNOWN')", projectId, contractId, partnerId, costSubjectId,
                costType, sourceType, costStatus, startDate, endDate, keyword);

        Set<Long> projectIds = byProjectId.keySet().stream()
                .map(this::parseLong)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : pmProjectMapper.selectByIds(projectIds).stream()
                        .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<String, BigDecimal> byProject = new LinkedHashMap<>();
        byProjectId.forEach((id, amount) -> {
            Long parsed = parseLong(id);
            String key = parsed == null ? "UNKNOWN" : projectNames.getOrDefault(parsed, id);
            byProject.merge(key, amount, BigDecimal::add);
        });

        CostLedgerSummaryVO summary = new CostLedgerSummaryVO();
        summary.setTotalAmount(toBigDecimal(totalRow, "totalAmount", "total_amount").toPlainString());
        summary.setTotalTaxAmount(toBigDecimal(totalRow, "totalTaxAmount", "total_tax_amount").toPlainString());
        summary.setBySourceType(convertToStringMap(bySourceType));
        summary.setByProject(convertToStringMap(byProject));
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

    private Long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String escapeLikeParameter(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

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
        if (StringUtils.hasText(keyword)) {
            String trimmedKeyword = keyword.trim();
            Long idMatch = parseLong(trimmedKeyword);
            String like = "%" + escapeLikeParameter(trimmedKeyword) + "%";
            wrapper.and(w -> {
                if (idMatch != null) {
                    w.eq(CostItem::getId, idMatch).or();
                }
                w.apply("cost_item.cost_type LIKE {0} ESCAPE '!'", like) // SQL-SAFETY: parameterized-like
                        .or().apply("cost_item.source_type LIKE {0} ESCAPE '!'", like) // SQL-SAFETY: parameterized-like
                        .or().apply("cost_item.cost_status LIKE {0} ESCAPE '!'", like) // SQL-SAFETY: parameterized-like
                        .or().apply("cost_item.remark LIKE {0} ESCAPE '!'", like) // SQL-SAFETY: parameterized-like
                        .or().apply("EXISTS (SELECT 1 FROM pm_project p WHERE p.id = cost_item.project_id AND p.project_name LIKE {0} ESCAPE '!')", like) // SQL-SAFETY: parameterized-exists
                        .or().apply("EXISTS (SELECT 1 FROM ct_contract c WHERE c.id = cost_item.contract_id AND (c.contract_name LIKE {0} ESCAPE '!' OR c.contract_code LIKE {0} ESCAPE '!'))", like) // SQL-SAFETY: parameterized-exists
                        .or().apply("EXISTS (SELECT 1 FROM md_partner mp WHERE mp.id = cost_item.partner_id AND mp.partner_name LIKE {0} ESCAPE '!')", like) // SQL-SAFETY: parameterized-exists
                        .or().apply("EXISTS (SELECT 1 FROM cost_subject cs WHERE cs.id = cost_item.cost_subject_id AND cs.subject_name LIKE {0} ESCAPE '!')", like); // SQL-SAFETY: parameterized-exists
            });
        }
        wrapper.orderByDesc(CostItem::getCostDate, CostItem::getCreatedAt);
        return wrapper;
    }

    private QueryWrapper<CostItem> buildSummaryFilterWrapper(
            Long projectId, Long contractId, Long partnerId, Long costSubjectId,
            String costType, String sourceType, String costStatus,
            LocalDate startDate, LocalDate endDate, String keyword) {
        QueryWrapper<CostItem> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", UserContext.getCurrentTenantId());
        if (projectId != null) wrapper.eq("project_id", projectId);
        if (contractId != null) wrapper.eq("contract_id", contractId);
        if (partnerId != null) wrapper.eq("partner_id", partnerId);
        if (costSubjectId != null) wrapper.eq("cost_subject_id", costSubjectId);
        if (StringUtils.hasText(costType)) wrapper.eq("cost_type", costType);
        if (StringUtils.hasText(sourceType)) wrapper.eq("source_type", sourceType);
        if (StringUtils.hasText(costStatus)) wrapper.eq("cost_status", costStatus);
        if (startDate != null) wrapper.ge("cost_date", startDate);
        if (endDate != null) wrapper.le("cost_date", endDate);
        if (StringUtils.hasText(keyword)) {
            String trimmedKeyword = keyword.trim();
            Long idMatch = parseLong(trimmedKeyword);
            String like = "%" + escapeLikeParameter(trimmedKeyword) + "%";
            wrapper.and(w -> {
                if (idMatch != null) {
                    w.eq("id", idMatch).or();
                }
                w.apply("cost_item.cost_type LIKE {0} ESCAPE '!'", like) // SQL-SAFETY: parameterized-like
                        .or().apply("cost_item.source_type LIKE {0} ESCAPE '!'", like) // SQL-SAFETY: parameterized-like
                        .or().apply("cost_item.cost_status LIKE {0} ESCAPE '!'", like) // SQL-SAFETY: parameterized-like
                        .or().apply("cost_item.remark LIKE {0} ESCAPE '!'", like) // SQL-SAFETY: parameterized-like
                        .or().apply("EXISTS (SELECT 1 FROM pm_project p WHERE p.id = cost_item.project_id AND p.project_name LIKE {0} ESCAPE '!')", like) // SQL-SAFETY: parameterized-exists
                        .or().apply("EXISTS (SELECT 1 FROM ct_contract c WHERE c.id = cost_item.contract_id AND (c.contract_name LIKE {0} ESCAPE '!' OR c.contract_code LIKE {0} ESCAPE '!'))", like) // SQL-SAFETY: parameterized-exists
                        .or().apply("EXISTS (SELECT 1 FROM md_partner mp WHERE mp.id = cost_item.partner_id AND mp.partner_name LIKE {0} ESCAPE '!')", like) // SQL-SAFETY: parameterized-exists
                        .or().apply("EXISTS (SELECT 1 FROM cost_subject cs WHERE cs.id = cost_item.cost_subject_id AND cs.subject_name LIKE {0} ESCAPE '!')", like); // SQL-SAFETY: parameterized-exists
            });
        }
        return wrapper;
    }

    private Map<String, BigDecimal> selectGroupedAmounts(
            String groupExpression, Long projectId, Long contractId, Long partnerId, Long costSubjectId,
            String costType, String sourceType, String costStatus,
            LocalDate startDate, LocalDate endDate, String keyword) {
        QueryWrapper<CostItem> wrapper = buildSummaryFilterWrapper(
                projectId, contractId, partnerId, costSubjectId,
                costType, sourceType, costStatus, startDate, endDate, keyword);
        wrapper.select(groupExpression + " AS group_key", "COALESCE(SUM(amount), 0) AS total_amount")
                .groupBy(groupExpression);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map<String, Object> row : costItemMapper.selectMaps(wrapper)) {
            Object key = value(row, "groupKey", "group_key");
            result.put(key == null ? "UNKNOWN" : key.toString(), toBigDecimal(row, "totalAmount", "total_amount"));
        }
        return result;
    }

    private Map<String, Object> firstMap(List<Map<String, Object>> rows) {
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private BigDecimal toBigDecimal(Map<String, Object> row, String... keys) {
        Object value = value(row, keys);
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal decimal) return decimal;
        return new BigDecimal(value.toString());
    }

    private Object value(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private Map<Long, String> batchResolveProjectNames(List<CostItem> items) {
        Set<Long> ids = items.stream().map(CostItem::getProjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return pmProjectMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
    }

    private Map<Long, String> batchResolveContractNames(List<CostItem> items) {
        Set<Long> ids = items.stream().map(CostItem::getContractId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return ctContractMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));
    }

    private Map<Long, String> batchResolvePartnerNames(List<CostItem> items) {
        Set<Long> ids = items.stream().map(CostItem::getPartnerId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return mdPartnerMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));
    }

    private Map<Long, String> batchResolveSubjectNames(List<CostItem> items) {
        Set<Long> ids = items.stream().map(CostItem::getCostSubjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return costSubjectMapper.selectByIds(ids).stream()
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
        vo.setProjectName(resolveName(projectNames, item.getProjectId()));
        vo.setContractId(item.getContractId() != null ? item.getContractId().toString() : null);
        vo.setContractName(resolveName(contractNames, item.getContractId()));
        vo.setPartnerId(item.getPartnerId() != null ? item.getPartnerId().toString() : null);
        vo.setPartnerName(resolveName(partnerNames, item.getPartnerId()));
        vo.setCostSubjectId(item.getCostSubjectId() != null ? item.getCostSubjectId().toString() : null);
        vo.setCostSubjectName(resolveName(subjectNames, item.getCostSubjectId()));
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

    private String resolveName(Map<Long, String> names, Long id) {
        return id == null ? null : names.get(id);
    }

    private Map<String, String> convertToStringMap(Map<String, BigDecimal> bigDecimalMap) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : bigDecimalMap.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toPlainString());
        }
        return result;
    }
}
