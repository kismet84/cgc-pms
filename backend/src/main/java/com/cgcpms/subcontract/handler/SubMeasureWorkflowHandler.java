package com.cgcpms.subcontract.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.cost.service.CostGenerationService;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.SUB_MEASURE;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public void onApproved(WorkflowContext context) {
        Long measureId = resolveMeasureId(context.getInstance());
        log.info("分包计量审批通过，自动生成成本 measureId={}", measureId);

        subMeasureMapper.update(null, new LambdaUpdateWrapper<SubMeasure>()
                .eq(SubMeasure::getId, measureId)
                .set(SubMeasure::getApprovalStatus, "APPROVED")
                .set(SubMeasure::getStatus, "CONFIRMED"));

        costGenerationService.generateCost("SUB_MEASURE", measureId);
    }

    @Override
    public void onRejected(WorkflowContext context) {
        Long measureId = resolveMeasureId(context.getInstance());
        log.info("分包计量审批驳回 measureId={}", measureId);

        subMeasureMapper.update(null, new LambdaUpdateWrapper<SubMeasure>()
                .eq(SubMeasure::getId, measureId)
                .set(SubMeasure::getApprovalStatus, "REJECTED"));
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        Long measureId = resolveMeasureId(context.getInstance());
        log.info("分包计量审批撤回，恢复为草稿 measureId={}", measureId);

        subMeasureMapper.update(null, new LambdaUpdateWrapper<SubMeasure>()
                .eq(SubMeasure::getId, measureId)
                .set(SubMeasure::getApprovalStatus, "DRAFT"));
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
