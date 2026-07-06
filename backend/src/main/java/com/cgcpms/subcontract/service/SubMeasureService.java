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
import com.cgcpms.subcontract.entity.SubTask;
import com.cgcpms.subcontract.mapper.SubMeasureItemMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import com.cgcpms.subcontract.vo.SubMeasureItemVO;
import com.cgcpms.subcontract.vo.SubMeasureVO;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.cgcpms.common.util.DateTimeUtils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubMeasureService {

    private static final int CODE_GENERATION_MAX_RETRIES = 3;

    private final SubMeasureMapper subMeasureMapper;
    private final SubMeasureItemMapper subMeasureItemMapper;
    private final SubTaskMapper subTaskMapper;
    private final PmProjectMapper pmProjectMapper;
    private final CtContractMapper ctContractMapper;
    private final MdPartnerMapper mdPartnerMapper;
    private final WorkflowEngine workflowEngine;
    private final WfInstanceMapper wfInstanceMapper;

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
        Set<Long> subTaskIds = records.stream()
                .map(SubMeasure::getSubTaskId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SubTask> subTaskMap = subTaskIds.isEmpty() ? Map.of()
                : subTaskMapper.selectBatchIds(subTaskIds).stream()
                        .collect(Collectors.toMap(SubTask::getId, t -> t, (a, b) -> a));

        return page.convert(t -> toVO(t, projectNames, contractNames, partnerNames, subTaskMap));
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

    @Transactional(rollbackFor = Exception.class)
    public Long create(SubMeasure measure) {
        // Validate subTaskId belongs to same project/contract/partner
        validateSubTaskBelongsToSameContext(measure);

        // Auto-generate measure code: SM-yyyyMMdd-XXX
        String prefix = "SM-" + LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";

        // Auto-calculate net amount
        calcNetAmount(measure);

        // Default status
        if (measure.getStatus() == null || measure.getStatus().isBlank()) {
            measure.setStatus("DRAFT");
        }

        measure.setTenantId(UserContext.getCurrentTenantId());
        for (int attempt = 0; attempt < CODE_GENERATION_MAX_RETRIES; attempt++) {
            measure.setMeasureCode(nextMeasureCode(prefix, attempt));
            try {
                subMeasureMapper.insert(measure);
                return measure.getId();
            } catch (DuplicateKeyException e) {
                log.warn("分包计量编号冲突，重试生成 measureCode={}", measure.getMeasureCode());
            }
        }
        throw new BusinessException("SUB_MEASURE_CODE_CONFLICT", "分包计量编号生成冲突，请重试");
    }

    private String nextMeasureCode(String prefix, int offset) {
        LambdaQueryWrapper<SubMeasure> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubMeasure::getTenantId, UserContext.getCurrentTenantId())
                .likeRight(SubMeasure::getMeasureCode, prefix)
                .orderByDesc(SubMeasure::getMeasureCode);
        Page<SubMeasure> page = new Page<>(0, 1);
        Page<SubMeasure> result = subMeasureMapper.selectPage(page, wrapper);
        SubMeasure last = result.getRecords().isEmpty() ? null : result.getRecords().get(0);

        int seq = 1 + offset;
        if (last != null && last.getMeasureCode() != null && last.getMeasureCode().startsWith(prefix)) {
            try {
                seq = Integer.parseInt(last.getMeasureCode().substring(last.getMeasureCode().lastIndexOf('-') + 1)) + 1 + offset;
            } catch (NumberFormatException e) {
                log.warn("Failed to parse sequence number: {}", last.getMeasureCode(), e);
            }
        }
        return prefix + String.format("%03d", seq);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SubMeasure measure) {
        SubMeasure existing = subMeasureMapper.selectById(measure.getId());
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("SUB_MEASURE_NOT_FOUND", "分包计量单不存在");

        if (!"DRAFT".equals(existing.getApprovalStatus()))
            throw new BusinessException("MEASURE_IN_APPROVAL", "计量单审批中或已审批，不可编辑");
        if (existing.getCostGeneratedFlag() != null && existing.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可编辑，请走冲销");

        // Validate subTaskId belongs to same project/contract/partner
        validateSubTaskBelongsToSameContext(measure);

        calcNetAmount(measure);

        subMeasureMapper.updateById(measure);
    }

    /**
     * 校验 subTaskId 的合法性：存在性 → 租户隔离 → 项目/合同/合作方一致性。
     * 当 subTaskId 为 null 时直接通过（本阶段兼容空值策略）。
     */
    private void validateSubTaskBelongsToSameContext(SubMeasure measure) {
        Long subTaskId = measure.getSubTaskId();
        if (subTaskId == null) return;

        SubTask task = subTaskMapper.selectById(subTaskId);
        if (task == null || task.getDeletedFlag() != null && task.getDeletedFlag() == 1)
            throw new BusinessException("SUB_TASK_NOT_FOUND", "关联的分包任务不存在");
        Long currentTenantId = UserContext.getCurrentTenantId();
        if (!currentTenantId.equals(task.getTenantId()))
            throw new BusinessException("SUB_TASK_TENANT_MISMATCH", "关联的分包任务不属于当前租户");
        Long projectId = measure.getProjectId();
        if (projectId != null && !projectId.equals(task.getProjectId()))
            throw new BusinessException("SUB_TASK_PROJECT_MISMATCH", "关联的分包任务不属于当前项目");
        Long contractId = measure.getContractId();
        if (contractId != null && !contractId.equals(task.getContractId()))
            throw new BusinessException("SUB_TASK_CONTRACT_MISMATCH", "关联的分包任务不属于当前合同");
        Long partnerId = measure.getPartnerId();
        if (partnerId != null && !partnerId.equals(task.getPartnerId()))
            throw new BusinessException("SUB_TASK_PARTNER_MISMATCH", "关联的分包任务不属于当前合作方");
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveItems(Long measureId, List<SubMeasureItem> items) {
        // Verify measure exists and belongs to tenant
        SubMeasure measure = subMeasureMapper.selectById(measureId);
        if (measure == null || !measure.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("SUB_MEASURE_NOT_FOUND", "分包计量单不存在");

        if (!"DRAFT".equals(measure.getApprovalStatus()))
            throw new BusinessException("MEASURE_IN_APPROVAL", "计量单审批中或已审批，不可编辑");
        if (measure.getCostGeneratedFlag() != null && measure.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可编辑，请走冲销");

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

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SubMeasure existing = subMeasureMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("SUB_MEASURE_NOT_FOUND", "分包计量单不存在");

        if (!"DRAFT".equals(existing.getApprovalStatus()))
            throw new BusinessException("MEASURE_IN_APPROVAL", "计量单审批中或已审批，不可删除");
        if (existing.getCostGeneratedFlag() != null && existing.getCostGeneratedFlag() == 1)
            throw new BusinessException("COST_GENERATED", "已生成成本，不可删除，请走冲销");

        subMeasureMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long measureId) {
        SubMeasure measure = subMeasureMapper.selectById(measureId);
        if (measure == null || !measure.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("SUB_MEASURE_NOT_FOUND", "分包计量单不存在");

        if (!"DRAFT".equals(measure.getApprovalStatus()))
            throw new BusinessException("SUB_MEASURE_ALREADY_SUBMITTED", "计量单已提交审批，不可重复提交");

        if (measure.getMeasureCode() == null || measure.getMeasureCode().isBlank())
            throw new BusinessException("SUB_MEASURE_NO_CODE", "计量单编号不能为空，无法提交审批");

        // Update submission state before starting workflow; transaction rolls back if workflow creation fails.
        subMeasureMapper.update(null, new LambdaUpdateWrapper<SubMeasure>()
                .eq(SubMeasure::getId, measureId)
                .set(SubMeasure::getApprovalStatus, "APPROVING")
                .set(SubMeasure::getStatus, "APPROVING"));

        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        Long tenantId = UserContext.getCurrentTenantId();
        WfInstance existingInstance = wfInstanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getTenantId, tenantId)
                .eq(WfInstance::getBusinessType, "SUB_MEASURE")
                .eq(WfInstance::getBusinessId, measureId)
                .orderByDesc(WfInstance::getCreatedAt)
                .last("LIMIT 1"));
        if (existingInstance != null) {
            String instanceStatus = existingInstance.getInstanceStatus();
            if (WorkflowConstants.INSTANCE_REJECTED.equals(instanceStatus)
                    || WorkflowConstants.INSTANCE_WITHDRAWN.equals(instanceStatus)) {
                workflowEngine.resubmit(existingInstance.getId(), userId, username);
                return;
            }
            throw new BusinessException("WORKFLOW_INSTANCE_EXISTS", "该业务已提交审批，请勿重复提交");
        }
        workflowEngine.submit(userId, username, tenantId,
                "SUB_MEASURE", measureId,
                measure.getMeasureCode(),
                measure.getNetAmount(),
                measure.getProjectId(),
                measure.getContractId(),
                null, null, null);
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
        if (m.getSubTaskId() != null) {
            SubTask task = subTaskMapper.selectById(m.getSubTaskId());
            if (task != null && task.getDeletedFlag() == 0) {
                vo.setSubTaskCode(task.getTaskCode());
                vo.setSubTaskName(task.getTaskName());
            }
        }
        return vo;
    }

    private SubMeasureVO toVO(SubMeasure m, Map<Long, String> projectNames,
                               Map<Long, String> contractNames, Map<Long, String> partnerNames,
                               Map<Long, SubTask> subTaskMap) {
        // Batch variant: use pre-fetched name maps
        SubMeasureVO vo = buildBaseVO(m);
        if (m.getProjectId() != null) vo.setProjectName(projectNames.get(m.getProjectId()));
        if (m.getContractId() != null) vo.setContractName(contractNames.get(m.getContractId()));
        if (m.getPartnerId() != null) vo.setPartnerName(partnerNames.get(m.getPartnerId()));
        if (m.getSubTaskId() != null) {
            SubTask task = subTaskMap.get(m.getSubTaskId());
            if (task != null) {
                vo.setSubTaskCode(task.getTaskCode());
                vo.setSubTaskName(task.getTaskName());
            }
        }
        return vo;
    }

    private SubMeasureVO buildBaseVO(SubMeasure m) {
        SubMeasureVO vo = new SubMeasureVO();
        vo.setId(m.getId() != null ? m.getId().toString() : null);
        vo.setTenantId(m.getTenantId() != null ? m.getTenantId().toString() : null);
        vo.setProjectId(m.getProjectId() != null ? m.getProjectId().toString() : null);
        vo.setContractId(m.getContractId() != null ? m.getContractId().toString() : null);
        vo.setPartnerId(m.getPartnerId() != null ? m.getPartnerId().toString() : null);
        vo.setSubTaskId(m.getSubTaskId() != null ? m.getSubTaskId().toString() : null);
        vo.setMeasureCode(m.getMeasureCode());
        vo.setMeasurePeriod(m.getMeasurePeriod());
        vo.setMeasureDate(m.getMeasureDate() != null ? m.getMeasureDate().format(DateTimeUtils.DATE_FMT) : null);
        vo.setReportedAmount(m.getReportedAmount() != null ? m.getReportedAmount().toPlainString() : null);
        vo.setApprovedAmount(m.getApprovedAmount() != null ? m.getApprovedAmount().toPlainString() : null);
        vo.setDeductionAmount(m.getDeductionAmount() != null ? m.getDeductionAmount().toPlainString() : null);
        vo.setNetAmount(m.getNetAmount() != null ? m.getNetAmount().toPlainString() : null);
        vo.setApprovalStatus(m.getApprovalStatus());
        vo.setCostGeneratedFlag(m.getCostGeneratedFlag());
        vo.setStatus(m.getStatus());
        vo.setCreatedBy(m.getCreatedBy() != null ? m.getCreatedBy().toString() : null);
        vo.setCreatedAt(m.getCreatedAt() != null ? m.getCreatedAt().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedAt(m.getUpdatedAt() != null ? m.getUpdatedAt().format(DateTimeUtils.DTF) : null);
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
        vo.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().format(DateTimeUtils.DTF) : null);
        vo.setRemark(item.getRemark());
        return vo;
    }
}
