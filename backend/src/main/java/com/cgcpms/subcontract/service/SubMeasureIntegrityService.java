package com.cgcpms.subcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.constant.ProjectStatusConstants;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubMeasureItem;
import com.cgcpms.subcontract.entity.SubTask;
import com.cgcpms.subcontract.mapper.SubMeasureItemMapper;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 分包计量进入审批前的唯一完整性门禁。 */
@Service
@RequiredArgsConstructor
public class SubMeasureIntegrityService {
    private static final Set<String> SUBCONTRACT_TYPES = Set.of("SUB", "SUBCONTRACT");
    private static final Set<String> MEASURABLE_TASK_STATUSES = Set.of("IN_PROGRESS", "COMPLETED");

    private final PmProjectMapper projectMapper;
    private final CtContractMapper contractMapper;
    private final SubTaskMapper taskMapper;
    private final SubMeasureItemMapper itemMapper;
    private final SysFileMapper fileMapper;
    private final ProjectAccessChecker projectAccessChecker;

    public void validateForSubmit(SubMeasure measure) {
        Long tenantId = UserContext.getCurrentTenantId();
        if (measure.getProjectId() == null || measure.getContractId() == null
                || measure.getPartnerId() == null || measure.getSubTaskId() == null) {
            throw new BusinessException("SUB_MEASURE_CONTEXT_REQUIRED", "分包计量必须绑定项目、分包合同、分包商和分包任务");
        }
        projectAccessChecker.checkAccess(measure.getProjectId(), "提交分包计量");
        PmProject project = projectMapper.selectById(measure.getProjectId());
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)
                || !ProjectStatusConstants.ACTIVE.equals(project.getStatus())) {
            throw new BusinessException("SUB_MEASURE_PROJECT_NOT_ACTIVE", "只有进行中的本租户项目可以提交分包计量");
        }

        CtContract contract = contractMapper.selectById(measure.getContractId());
        String contractType = contract == null || contract.getContractType() == null
                ? "" : contract.getContractType().trim().toUpperCase();
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId)
                || !Objects.equals(contract.getProjectId(), measure.getProjectId())
                || !SUBCONTRACT_TYPES.contains(contractType)
                || !ContractStatusConstants.APPROVAL_APPROVED.equals(contract.getApprovalStatus())
                || !ContractStatusConstants.STATUS_PERFORMING.equals(contract.getContractStatus())) {
            throw new BusinessException("SUB_MEASURE_CONTRACT_INVALID", "计量必须关联已审批且履约中的分包合同");
        }
        if (!Objects.equals(contract.getPartyBId(), measure.getPartnerId())) {
            throw new BusinessException("SUB_MEASURE_PARTNER_MISMATCH", "计量分包商必须等于分包合同乙方");
        }

        SubTask task = taskMapper.selectById(measure.getSubTaskId());
        if (task == null || !Objects.equals(task.getTenantId(), tenantId)
                || !Objects.equals(task.getProjectId(), measure.getProjectId())
                || !Objects.equals(task.getContractId(), measure.getContractId())
                || !Objects.equals(task.getPartnerId(), measure.getPartnerId())
                || !MEASURABLE_TASK_STATUSES.contains(task.getStatus())) {
            throw new BusinessException("SUB_MEASURE_TASK_INVALID", "分包任务不存在、上下文不一致或尚不可计量");
        }
        if (!StringUtils.hasText(measure.getMeasurePeriod()) || measure.getMeasureDate() == null) {
            throw new BusinessException("SUB_MEASURE_PERIOD_REQUIRED", "计量期次和计量日期不能为空");
        }

        List<SubMeasureItem> items = itemMapper.selectList(new LambdaQueryWrapper<SubMeasureItem>()
                .eq(SubMeasureItem::getTenantId, tenantId)
                .eq(SubMeasureItem::getMeasureId, measure.getId()));
        if (items.isEmpty()) {
            throw new BusinessException("SUB_MEASURE_ITEMS_REQUIRED", "分包计量必须至少包含一条合同清单明细");
        }
        BigDecimal itemTotal = items.stream().map(SubMeasureItem::getAmount)
                .map(value -> value == null ? BigDecimal.ZERO : value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (itemTotal.compareTo(BigDecimal.ZERO) <= 0
                || money(measure.getReportedAmount()).compareTo(money(itemTotal)) != 0
                || money(measure.getApprovedAmount()).compareTo(BigDecimal.ZERO) <= 0
                || money(measure.getApprovedAmount()).compareTo(money(measure.getReportedAmount())) > 0
                || money(measure.getDeductionAmount()).compareTo(BigDecimal.ZERO) < 0
                || money(measure.getNetAmount()).compareTo(
                    money(measure.getApprovedAmount()).subtract(money(measure.getDeductionAmount()))) != 0
                || money(measure.getNetAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("SUB_MEASURE_AMOUNT_INVALID", "计量明细、申报、审定、扣款和净额不守恒或金额无效");
        }

        long cleanAttachmentCount = fileMapper.selectCount(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTenantId, tenantId)
                .eq(SysFile::getBusinessType, "SUBCONTRACT")
                .eq(SysFile::getBusinessId, measure.getId())
                .eq(SysFile::getVirusScanStatus, "CLEAN"));
        if (cleanAttachmentCount == 0) {
            throw new BusinessException("SUB_MEASURE_ATTACHMENT_REQUIRED", "分包计量必须上传至少一份已通过安全扫描的计量附件");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
