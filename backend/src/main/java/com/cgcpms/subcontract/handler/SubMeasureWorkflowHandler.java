package com.cgcpms.subcontract.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.service.CostGenerationService;
import com.cgcpms.cost.service.CostClassificationGuard;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.subcontract.service.SubMeasureIntegrityService;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business handler for sub-measure approval workflows.
 * On approval, auto-generates subcontract cost records via CostGenerationService.
 * Critical handler: callback failures roll back the entire approval transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubMeasureWorkflowHandler implements WorkflowBusinessHandler {

    private final SubMeasureMapper subMeasureMapper;
    private final CostGenerationService costGenerationService;
    private final CostClassificationGuard costClassificationGuard;
    private final SubMeasureIntegrityService integrityService;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.SUB_MEASURE;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        costClassificationGuard.requireClassified("SUB_MEASURE", resolveMeasureId(context.getInstance()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(WorkflowContext context) {
        Long measureId = resolveMeasureId(context.getInstance());
        log.info("分包计量审批通过，自动生成成本 measureId={}", measureId);

        SubMeasure measure = requireApproving(measureId);
        integrityService.validateForSubmit(measure);
        transition(measureId, "APPROVED", "CONFIRMED");

        costGenerationService.generateCost("SUB_MEASURE", measureId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(WorkflowContext context) {
        Long measureId = resolveMeasureId(context.getInstance());
        log.info("分包计量审批驳回 measureId={}", measureId);

        requireApproving(measureId);
        transition(measureId, "REJECTED", "REJECTED");
        costClassificationGuard.voidPending("SUB_MEASURE", measureId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onWithdrawn(WorkflowContext context) {
        Long measureId = resolveMeasureId(context.getInstance());
        log.info("分包计量审批撤回，恢复为草稿 measureId={}", measureId);

        requireApproving(measureId);
        transition(measureId, "DRAFT", "DRAFT");
        costClassificationGuard.voidPending("SUB_MEASURE", measureId);
    }

    private SubMeasure requireApproving(Long measureId) {
        SubMeasure measure = subMeasureMapper.selectByIdForUpdate(
                measureId, UserContext.getCurrentTenantId());
        if (measure == null) {
            throw new BusinessException("SUB_MEASURE_NOT_FOUND", "分包计量单不存在");
        }
        if (!"APPROVING".equals(measure.getApprovalStatus())
                || !"APPROVING".equals(measure.getStatus())) {
            throw new BusinessException("SUB_MEASURE_WORKFLOW_STATE_INVALID", "计量单不处于审批中状态");
        }
        return measure;
    }

    private void transition(Long measureId, String approvalStatus, String status) {
        int updated = subMeasureMapper.update(null, new LambdaUpdateWrapper<SubMeasure>()
                .eq(SubMeasure::getId, measureId)
                .eq(SubMeasure::getTenantId, UserContext.getCurrentTenantId())
                .eq(SubMeasure::getApprovalStatus, "APPROVING")
                .eq(SubMeasure::getStatus, "APPROVING")
                .set(SubMeasure::getApprovalStatus, approvalStatus)
                .set(SubMeasure::getStatus, status));
        if (updated != 1) {
            throw new BusinessException("SUB_MEASURE_CONCURRENT_UPDATE", "计量状态已变化，审批回调拒绝执行");
        }
    }

    private Long resolveMeasureId(WfInstance instance) {
        Long measureId = instance.getBusinessId();
        if (measureId == null) {
            throw new IllegalStateException(
                    "审批实例缺少业务ID（计量单ID），instanceId=" + instance.getId());
        }
        return measureId;
    }
}
