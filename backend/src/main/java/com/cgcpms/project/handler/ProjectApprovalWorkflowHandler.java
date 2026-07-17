package com.cgcpms.project.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectApprovalWorkflowHandler implements WorkflowBusinessHandler {
    private final PmProjectMapper projectMapper;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.PROJECT_APPROVAL;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public void onApproved(WorkflowContext context) {
        update(context.getInstance(), "APPROVING", "APPROVED");
    }

    @Override
    public void onRejected(WorkflowContext context) {
        update(context.getInstance(), "APPROVING", "REJECTED");
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        update(context.getInstance(), "APPROVING", "DRAFT");
    }

    private void update(WfInstance instance, String expected, String target) {
        int rows = projectMapper.update(null, new LambdaUpdateWrapper<PmProject>()
                .eq(PmProject::getId, instance.getBusinessId())
                .eq(PmProject::getTenantId, instance.getTenantId())
                .eq(PmProject::getApprovalStatus, expected)
                .set(PmProject::getApprovalStatus, target));
        if (rows != 1) {
            throw new IllegalStateException("项目审批状态冲突，projectId=" + instance.getBusinessId());
        }
    }
}
