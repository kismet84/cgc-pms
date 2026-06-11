package com.cgcpms.subcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubMeasureItem;
import com.cgcpms.subcontract.mapper.SubMeasureItemMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.subcontract.vo.SubMeasureItemVO;
import com.cgcpms.subcontract.vo.SubMeasureVO;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubMeasureService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SubMeasureMapper subMeasureMapper;
    private final SubMeasureItemMapper subMeasureItemMapper;
    private final PmProjectMapper pmProjectMapper;
    private final CtContractMapper ctContractMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final WorkflowEngine workflowEngine;

    public IPage<SubMeasureVO> getPage(long pageNo, long pageSize, Long projectId, Long contractId,
                                        Long partnerId, String status, String measureCode) {
        LambdaQueryWrapper<SubMeasure> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubMeasure::getTenantId, UserContext.getCurrentTenantId());
        if (projectId != null) wrapper.eq(SubMeasure::getProjectId, projectId);
        if (contractId != null) wrapper.eq(SubMeasure::getContractId, contractId);
        if (partnerId != null) wrapper.eq(SubMeasure::getPartnerId, partnerId);
        if (StringUtils.hasText(status)) wrapper.eq(SubMeasure::getStatus, status);
        if (StringUtils.hasText(measureCode)) wrapper.like(SubMeasure::getMeasureCode, measureCode);
        wrapper.orderByDesc(SubMeasure::getCreatedAt);

        Page<SubMeasure> page = subMeasureMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        // Batch-prefetch related project/contract/partner names to avoid N+1 queries
        List<SubMeasure> records = page.getRecords();
        Set<Long> projectIds = records.stream()
                .map(SubMeasure::getProjectId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> contractIds = records.stream()
                .map(SubMeasure::getContractId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> partnerIds = records.stream()
                .map(SubMeasure::getPartnerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : pmProjectMapper.selectBatchIds(projectIds).stream()
                        .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        Map<Long, String> contractNames = contractIds.isEmpty() ? Map.of()
                : ctContractMapper.selectBatchIds(contractIds).stream()
                        .collect(Collectors.toMap(CtContract::getId, CtContract::getContractName, (a, b) -> a));
        Map<Long, String> partnerNames = partnerIds.isEmpty() ? Map.of()
                : mdPartnerMapper.selectBatchIds(partnerIds).stream()
                        .collect(Collectors.toMap(MdPartner::getId, MdPartner::getPartnerName, (a, b) -> a));

        return page.convert(t -> toVO(t, projectNames, contractNames, partnerNames));
    }

    public SubMeasureVO getById(Long id) {
        SubMeasure measure = subMeasureMapper.selectById(id);
        if (measure == null || !measure.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("SUB_MEASURE_NOT_FOUND", "分包计量单不存在");

        SubMeasureVO vo = toVO(measure);

        // Load items
        List<SubMeasureItem> items = subMeasureItemMapper.selectList(
                new LambdaQueryWrapper<SubMeasureItem>()
                        .eq(SubMeasureItem::getMeasureId, id));
        vo.setItems(items.stream().map(this::toItemVO).collect(Collectors.toList()));

        return vo;
    }

    @Transactional
    public Long create(SubMeasure measure) {
        // Auto-generate measure code: SM-yyyyMMdd-XXX
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "SM-" + today + "-";

        LambdaQueryWrapper<SubMeasure> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(SubMeasure::getMeasureCode, prefix)
                .orderByDesc(SubMeasure::getMeasureCode)
                .last("LIMIT 1");
        SubMeasure last = subMeasureMapper.selectOne(wrapper);

        int seq = 1;
        if (last != null && last.getMeasureCode() != null && last.getMeasureCode().startsWith(prefix)) {
            try {
                seq = Integer.parseInt(last.getMeasureCode().substring(last.getMeasureCode().lastIndexOf('-') + 1)) + 1;
            } catch (NumberFormatException ignored) {
            }
        }
        measure.setMeasureCode(prefix + String.format("%03d", seq));

        // Auto-calculate net amount
        calcNetAmount(measure);

        // Default status
        if (measure.getStatus() == null || measure.getStatus().isBlank()) {
            measure.setStatus("DRAFT");
        }

        measure.setTenantId(UserContext.getCurrentTenantId());
        subMeasureMapper.insert(measure);
        return measure.getId();
    }

    @Transactional
    public void update(SubMeasure measure) {
        SubMeasure existing = subMeasureMapper.selectById(measure.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("SUB_MEASURE_NOT_FOUND", "分包计量单不存在");

        // Auto-calculate net amount
        calcNetAmount(measure);

        subMeasureMapper.updateById(measure);
    }

    @Transactional
    public void saveItems(Long measureId, List<SubMeasureItem> items) {
        // Verify measure exists and belongs to tenant
        SubMeasure measure = subMeasureMapper.selectById(measureId);
        if (measure == null || !measure.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("SUB_MEASURE_NOT_FOUND", "分包计量单不存在");

        // Delete old items
        subMeasureItemMapper.delete(new LambdaQueryWrapper<SubMeasureItem>()
                .eq(SubMeasureItem::getMeasureId, measureId));

        // Batch insert new items and calculate total amount
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (items != null) {
            for (SubMeasureItem item : items) {
                item.setMeasureId(measureId);
                item.setTenantId(UserContext.getCurrentTenantId());
                item.setId(null);
                subMeasureItemMapper.insert(item);
                totalAmount = totalAmount.add(item.getAmount() == null ? BigDecimal.ZERO : item.getAmount());
            }
        }

        // Update header reported amount
        measure.setReportedAmount(totalAmount);
        subMeasureMapper.updateById(measure);
    }

    @Transactional
    public void delete(Long id) {
        SubMeasure existing = subMeasureMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("SUB_MEASURE_NOT_FOUND", "分包计量单不存在");

        subMeasureMapper.deleteById(id);
    }

    @Transactional
    public void submitForApproval(Long measureId) {
        SubMeasure measure = subMeasureMapper.selectById(measureId);
        if (measure == null || !measure.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("SUB_MEASURE_NOT_FOUND", "分包计量单不存在");

        if (!"DRAFT".equals(measure.getApprovalStatus()))
            throw new BusinessException("SUB_MEASURE_ALREADY_SUBMITTED", "计量单已提交审批，不可重复提交");

        if (measure.getMeasureCode() == null || measure.getMeasureCode().isBlank())
            throw new BusinessException("SUB_MEASURE_NO_CODE", "计量单编号不能为空，无法提交审批");

        // Update approval status to APPROVING
        subMeasureMapper.update(null, new LambdaUpdateWrapper<SubMeasure>()
                .eq(SubMeasure::getId, measureId)
                .set(SubMeasure::getApprovalStatus, "APPROVING"));

        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        Long tenantId = UserContext.getCurrentTenantId();
        workflowEngine.submit(userId, username, tenantId,
                "SUB_MEASURE", measureId,
                measure.getMeasureCode(),
                measure.getNetAmount(),
                measure.getProjectId(),
                measure.getContractId(),
                null, null);
    }

    // ---- VO conversion helpers ----

    private void calcNetAmount(SubMeasure measure) {
        BigDecimal approved = measure.getApprovedAmount() == null ? BigDecimal.ZERO : measure.getApprovedAmount();
        BigDecimal deduction = measure.getDeductionAmount() == null ? BigDecimal.ZERO : measure.getDeductionAmount();
        measure.setNetAmount(approved.subtract(deduction));
    }

    private SubMeasureVO toVO(SubMeasure m) {
        // Single-record variant: fetch project/contract/partner individually (for getById)
        SubMeasureVO vo = buildBaseVO(m);
        if (m.getProjectId() != null) {
            PmProject project = pmProjectMapper.selectById(m.getProjectId());
            if (project != null) vo.setProjectName(project.getProjectName());
        }
        if (m.getContractId() != null) {
            CtContract contract = ctContractMapper.selectById(m.getContractId());
            if (contract != null) vo.setContractName(contract.getContractName());
        }
        if (m.getPartnerId() != null) {
            MdPartner partner = mdPartnerMapper.selectById(m.getPartnerId());
            if (partner != null) vo.setPartnerName(partner.getPartnerName());
        }
        return vo;
    }

    private SubMeasureVO toVO(SubMeasure m, Map<Long, String> projectNames,
                               Map<Long, String> contractNames, Map<Long, String> partnerNames) {
        // Batch variant: use pre-fetched name maps
        SubMeasureVO vo = buildBaseVO(m);
        if (m.getProjectId() != null) vo.setProjectName(projectNames.get(m.getProjectId()));
        if (m.getContractId() != null) vo.setContractName(contractNames.get(m.getContractId()));
        if (m.getPartnerId() != null) vo.setPartnerName(partnerNames.get(m.getPartnerId()));
        return vo;
    }

    private SubMeasureVO buildBaseVO(SubMeasure m) {
        SubMeasureVO vo = new SubMeasureVO();
        vo.setId(m.getId() != null ? m.getId().toString() : null);
        vo.setTenantId(m.getTenantId() != null ? m.getTenantId().toString() : null);
        vo.setProjectId(m.getProjectId() != null ? m.getProjectId().toString() : null);
        vo.setContractId(m.getContractId() != null ? m.getContractId().toString() : null);
        vo.setPartnerId(m.getPartnerId() != null ? m.getPartnerId().toString() : null);
        vo.setMeasureCode(m.getMeasureCode());
        vo.setMeasurePeriod(m.getMeasurePeriod());
        vo.setMeasureDate(m.getMeasureDate() != null ? m.getMeasureDate().format(DATE_FMT) : null);
        vo.setReportedAmount(m.getReportedAmount() != null ? m.getReportedAmount().toPlainString() : null);
        vo.setApprovedAmount(m.getApprovedAmount() != null ? m.getApprovedAmount().toPlainString() : null);
        vo.setDeductionAmount(m.getDeductionAmount() != null ? m.getDeductionAmount().toPlainString() : null);
        vo.setNetAmount(m.getNetAmount() != null ? m.getNetAmount().toPlainString() : null);
        vo.setApprovalStatus(m.getApprovalStatus());
        vo.setCostGeneratedFlag(m.getCostGeneratedFlag());
        vo.setStatus(m.getStatus());
        vo.setCreatedBy(m.getCreatedBy() != null ? m.getCreatedBy().toString() : null);
        vo.setCreatedAt(m.getCreatedAt() != null ? m.getCreatedAt().format(DTF) : null);
        vo.setUpdatedAt(m.getUpdatedAt() != null ? m.getUpdatedAt().format(DTF) : null);
        vo.setRemark(m.getRemark());
        return vo;
    }

    private SubMeasureItemVO toItemVO(SubMeasureItem item) {
        SubMeasureItemVO vo = new SubMeasureItemVO();
        vo.setId(item.getId() != null ? item.getId().toString() : null);
        vo.setTenantId(item.getTenantId() != null ? item.getTenantId().toString() : null);
        vo.setMeasureId(item.getMeasureId() != null ? item.getMeasureId().toString() : null);
        vo.setContractItemId(item.getContractItemId() != null ? item.getContractItemId().toString() : null);
        vo.setItemName(item.getItemName());
        vo.setUnit(item.getUnit());
        vo.setContractQuantity(item.getContractQuantity() != null ? item.getContractQuantity().toPlainString() : null);
        vo.setCurrentQuantity(item.getCurrentQuantity() != null ? item.getCurrentQuantity().toPlainString() : null);
        vo.setCumulativeQuantity(item.getCumulativeQuantity() != null ? item.getCumulativeQuantity().toPlainString() : null);
        vo.setUnitPrice(item.getUnitPrice() != null ? item.getUnitPrice().toPlainString() : null);
        vo.setAmount(item.getAmount() != null ? item.getAmount().toPlainString() : null);
        vo.setCreatedBy(item.getCreatedBy() != null ? item.getCreatedBy().toString() : null);
        vo.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt().format(DTF) : null);
        vo.setUpdatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().format(DTF) : null);
        vo.setRemark(item.getRemark());
        return vo;
    }
}
