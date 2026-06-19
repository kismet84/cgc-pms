package com.cgcpms.revenue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.CodeGenerationService;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.revenue.entity.ContractRevenue;
import com.cgcpms.revenue.mapper.ContractRevenueMapper;
import com.cgcpms.revenue.vo.ContractRevenueBalanceVO;
import com.cgcpms.revenue.vo.ContractRevenueVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 业主收入确认服务。
 * <p>
 * 核心流程：创建收入确认单 → 提交审批 → 审批通过 → 生成 cost_item（REVENUE_CONFIRMED） → 刷新 cost_summary。
 * 与 stl_settlement（分包结算）完全独立，数据流不交叉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractRevenueService {

    private static final String SOURCE_TYPE_CT_REVENUE = "CT_REVENUE";
    private static final String COST_TYPE_REVENUE_CONFIRMED = "REVENUE_CONFIRMED";

    private final ContractRevenueMapper mapper;
    private final CostItemMapper costItemMapper;
    private final CostSubjectMapper costSubjectMapper;
    private final PmProjectMapper projectMapper;
    private final CtContractMapper contractMapper;
    private final CostSummaryService costSummaryService;
    private final CodeGenerationService codeGenerationService;

    // ================================================================
    // Query
    // ================================================================

    public IPage<ContractRevenueVO> getPage(long pageNo, long pageSize,
                                             Long projectId, Long contractId,
                                             String startDate, String endDate,
                                             String approvalStatus) {
        Long tenantId = UserContext.getCurrentTenantId();
        LambdaQueryWrapper<ContractRevenue> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContractRevenue::getTenantId, tenantId);
        if (projectId != null) wrapper.eq(ContractRevenue::getProjectId, projectId);
        if (contractId != null) wrapper.eq(ContractRevenue::getContractId, contractId);
        if (StringUtils.hasText(approvalStatus)) wrapper.eq(ContractRevenue::getApprovalStatus, approvalStatus);
        if (StringUtils.hasText(startDate)) wrapper.ge(ContractRevenue::getRevenueDate, LocalDate.parse(startDate));
        if (StringUtils.hasText(endDate)) wrapper.le(ContractRevenue::getRevenueDate, LocalDate.parse(endDate));
        wrapper.orderByDesc(ContractRevenue::getCreatedAt);

        Page<ContractRevenue> page = mapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(r -> toVO(r, resolveNameMaps(page.getRecords())));
    }

    public ContractRevenueVO getById(Long id) {
        ContractRevenue revenue = mapper.selectById(id);
        if (revenue == null) throw new BusinessException("REVENUE_NOT_FOUND", "收入确认单不存在");
        if (!revenue.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("REVENUE_NOT_FOUND", "收入确认单不存在");
        return toVO(revenue, resolveNameMaps(List.of(revenue)));
    }

    /**
     * 查询合同资产/负债余额（动态计算）。
     */
    public ContractRevenueBalanceVO getBalance(Long contractId) {
        Long tenantId = UserContext.getCurrentTenantId();
        List<ContractRevenue> list = mapper.selectList(
                new LambdaQueryWrapper<ContractRevenue>()
                        .eq(ContractRevenue::getTenantId, tenantId)
                        .eq(ContractRevenue::getContractId, contractId)
                        .eq(ContractRevenue::getApprovalStatus, "APPROVED")
                        .eq(ContractRevenue::getDeletedFlag, 0));

        BigDecimal totalRevenue = list.stream()
                .map(ContractRevenue::getRevenueAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBilled = list.stream()
                .map(ContractRevenue::getBilledAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ContractRevenueBalanceVO vo = new ContractRevenueBalanceVO();
        vo.setContractId(contractId.toString());
        vo.setTotalConfirmedRevenue(totalRevenue.toPlainString());
        vo.setTotalBilled(totalBilled.toPlainString());
        vo.setContractAsset(totalRevenue.subtract(totalBilled).max(BigDecimal.ZERO).toPlainString());
        vo.setContractLiability(totalBilled.subtract(totalRevenue).max(BigDecimal.ZERO).toPlainString());
        return vo;
    }

    // ================================================================
    // CRUD
    // ================================================================

    @Transactional
    public Long create(ContractRevenue revenue) {
        revenue.setTenantId(UserContext.getCurrentTenantId());
        revenue.setApprovalStatus("DRAFT");
        if (!StringUtils.hasText(revenue.getRevenueCode())) {
            revenue.setRevenueCode(codeGenerationService.nextCode(
                mapper, ContractRevenue::getRevenueCode, "RV-", revenue.getTenantId()));
        }
        // 计算含税金额
        if (revenue.getRevenueAmount() != null && revenue.getRevenueTax() != null) {
            revenue.setRevenueAmountWithTax(revenue.getRevenueAmount().add(revenue.getRevenueTax()));
        }
        mapper.insert(revenue);
        return revenue.getId();
    }

    @Transactional
    public void update(ContractRevenue revenue) {
        ContractRevenue existing = requireExisting(revenue.getId());
        if (!"DRAFT".equals(existing.getApprovalStatus())) {
            throw new BusinessException("REVENUE_STATUS_NOT_EDITABLE", "仅草稿状态可编辑");
        }
        if (revenue.getRevenueAmount() != null && revenue.getRevenueTax() != null) {
            revenue.setRevenueAmountWithTax(revenue.getRevenueAmount().add(revenue.getRevenueTax()));
        }
        mapper.updateById(revenue);
    }

    @Transactional
    public void delete(Long id) {
        ContractRevenue existing = requireExisting(id);
        if (!"DRAFT".equals(existing.getApprovalStatus())) {
            throw new BusinessException("REVENUE_STATUS_NOT_DELETABLE", "仅草稿状态可删除");
        }
        mapper.deleteById(id);
    }

    /**
     * 提交审批：状态从 DRAFT -> PENDING。
     * 审批通过后由 WorkflowHandler.onApproved() 驱动后续流程。
     */
    @Transactional
    public void submitForApproval(Long id) {
        ContractRevenue existing = requireExisting(id);
        if (!"DRAFT".equals(existing.getApprovalStatus())) {
            throw new BusinessException("REVENUE_SUBMIT_INVALID", "仅草稿状态可提交审批");
        }
        existing.setApprovalStatus("PENDING");
        mapper.updateById(existing);
    }

    // ================================================================
    // Approval workflow callback
    // ================================================================

    /**
     * 审批通过后回调：生成 cost_item（REVENUE_CONFIRMED）并刷新汇总。
     * 由 ContractRevenueWorkflowHandler.onApproved() 调用。
     */
    @Transactional
    public void onApproved(Long id) {
        ContractRevenue revenue = requireExisting(id);

        // 状态守卫：防止重复审批
        if (!"PENDING".equals(revenue.getApprovalStatus())) {
            throw new BusinessException("REVENUE_STATUS_CONFLICT",
                    "收入确认单状态冲突：已被并发操作，请刷新后重试");
        }

        // 1. 生成 cost_item（收入记录，用于 cost_summary 聚合）
        CostItem item = buildRevenueCostItem(revenue);
        costItemMapper.insert(item);

        // 2. 回写关联
        revenue.setApprovalStatus("APPROVED");
        revenue.setCostItemId(item.getId());
        mapper.updateById(revenue);

        // 3. 刷新成本汇总
        costSummaryService.refreshSummary(revenue.getTenantId(), revenue.getProjectId());

        log.info("收入确认审批通过 revenueId={} revenueCode={} costItemId={}",
                revenue.getId(), revenue.getRevenueCode(), item.getId());
    }

    /**
     * 审批驳回后回调。
     */
    @Transactional
    public void onRejected(Long id) {
        ContractRevenue revenue = requireExisting(id);
        revenue.setApprovalStatus("REJECTED");
        mapper.updateById(revenue);
        log.info("收入确认审批驳回 revenueId={}", id);
    }

    // ================================================================
    // Helpers
    // ================================================================

    private ContractRevenue requireExisting(Long id) {
        ContractRevenue revenue = mapper.selectById(id);
        if (revenue == null) throw new BusinessException("REVENUE_NOT_FOUND", "收入确认单不存在");
        if (!revenue.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("REVENUE_NOT_FOUND", "收入确认单不存在");
        return revenue;
    }

    /**
     * 构建收入 cost_item。
     * 科目映射到 6001.01 合同建造收入（通过 subject_code 精确匹配）。
     */
    private CostItem buildRevenueCostItem(ContractRevenue revenue) {
        Long subjectId = resolveRevenueSubjectId(revenue.getTenantId());

        CostItem item = new CostItem();
        item.setTenantId(revenue.getTenantId());
        item.setProjectId(revenue.getProjectId());
        item.setContractId(revenue.getContractId());
        item.setCostSubjectId(subjectId);
        item.setCostType(COST_TYPE_REVENUE_CONFIRMED);
        item.setAmount(revenue.getRevenueAmount() != null ? revenue.getRevenueAmount() : BigDecimal.ZERO);
        item.setTaxAmount(revenue.getRevenueTax() != null ? revenue.getRevenueTax() : BigDecimal.ZERO);
        item.setAmountWithoutTax(revenue.getRevenueAmount() != null ? revenue.getRevenueAmount() : BigDecimal.ZERO);
        item.setSourceType(SOURCE_TYPE_CT_REVENUE);
        item.setSourceId(revenue.getId());
        item.setSourceItemId(0L);
        item.setCostDate(revenue.getRevenueDate());
        item.setCostStatus("CONFIRMED");
        item.setGeneratedFlag(1);
        return item;
    }

    /**
     * 解析收入确认对应的 cost_subject。
     * 优先按 subject_code='6001.01' 精确匹配（合同建造收入），
     * 兜底按 accountCategory='REVENUE' 查找第一个启用的收入科目。
     */
    private Long resolveRevenueSubjectId(Long tenantId) {
        // 1. 精确匹配 6001.01
        LambdaQueryWrapper<CostSubject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, tenantId);
        wrapper.eq(CostSubject::getSubjectCode, "6001.01");
        wrapper.eq(CostSubject::getDeletedFlag, 0);
        CostSubject subject = costSubjectMapper.selectOne(wrapper);
        if (subject != null) return subject.getId();

        // 2. 兜底：任意 REVENUE 类别启用科目
        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostSubject::getTenantId, tenantId);
        wrapper.eq(CostSubject::getAccountCategory, "REVENUE");
        wrapper.eq(CostSubject::getStatus, "ENABLE");
        wrapper.eq(CostSubject::getDeletedFlag, 0);
        wrapper.orderByAsc(CostSubject::getLevel, CostSubject::getSortOrder);
        subject = costSubjectMapper.selectOne(wrapper);
        if (subject != null) {
            log.warn("未找到 6001.01 科目，兜底使用 subjectId={}", subject.getId());
            return subject.getId();
        }

        throw new BusinessException("REVENUE_SUBJECT_NOT_FOUND", "未找到收入科目，请先初始化科目数据");
    }

    // ================================================================
    // Name resolution
    // ================================================================

    private Map<Long, String> resolveNameMaps(List<ContractRevenue> records) {
        Set<Long> projectIds = records.stream().map(ContractRevenue::getProjectId).collect(Collectors.toSet());
        Set<Long> contractIds = records.stream().map(ContractRevenue::getContractId).collect(Collectors.toSet());

        Map<Long, String> projectNames = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<Long, String> contractNames = contractMapper.selectBatchIds(contractIds).stream()
                .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));

        Map<Long, String> all = new java.util.HashMap<>();
        all.putAll(projectNames);
        all.putAll(contractNames);
        return all;
    }

    private ContractRevenueVO toVO(ContractRevenue r, Map<Long, String> names) {
        ContractRevenueVO vo = new ContractRevenueVO();
        vo.setId(r.getId() != null ? r.getId().toString() : null);
        vo.setTenantId(r.getTenantId() != null ? r.getTenantId().toString() : null);
        vo.setProjectId(r.getProjectId() != null ? r.getProjectId().toString() : null);
        vo.setProjectName(r.getProjectId() != null ? names.getOrDefault(r.getProjectId(), "") : "");
        vo.setContractId(r.getContractId() != null ? r.getContractId().toString() : null);
        vo.setContractName(r.getContractId() != null ? names.getOrDefault(r.getContractId(), "") : "");
        vo.setRevenueCode(r.getRevenueCode());
        vo.setRevenueDate(r.getRevenueDate() != null ? r.getRevenueDate().toString() : null);
        vo.setProgressPercent(r.getProgressPercent() != null ? r.getProgressPercent().toPlainString() : "0");
        vo.setProgressDesc(r.getProgressDesc());
        vo.setRevenueAmount(r.getRevenueAmount() != null ? r.getRevenueAmount().toPlainString() : "0");
        vo.setRevenueTax(r.getRevenueTax() != null ? r.getRevenueTax().toPlainString() : "0");
        vo.setRevenueAmountWithTax(r.getRevenueAmountWithTax() != null ? r.getRevenueAmountWithTax().toPlainString() : "0");
        vo.setBilledAmount(r.getBilledAmount() != null ? r.getBilledAmount().toPlainString() : "0");
        vo.setBilledTax(r.getBilledTax() != null ? r.getBilledTax().toPlainString() : "0");
        vo.setApprovalStatus(r.getApprovalStatus());
        vo.setCostItemId(r.getCostItemId() != null ? r.getCostItemId().toString() : null);
        vo.setCreatedBy(r.getCreatedBy() != null ? r.getCreatedBy().toString() : null);
        vo.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        vo.setUpdatedAt(r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : null);
        return vo;
    }
}
