package com.cgcpms.project.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.service.ProjectLifecycleService;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectApprovalWorkflowHandler implements WorkflowBusinessHandler {
    private final PmProjectMapper projectMapper;
    private final ProjectLifecycleService projectLifecycleService;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.PROJECT_APPROVAL;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        update(context.getInstance(), List.of("DRAFT", "REJECTED", "WITHDRAWN", "APPROVING"), "APPROVING", "DRAFT");
    }

    @Override
    public void onApproved(WorkflowContext context) {
        WfInstance instance = context.getInstance();
        update(instance, approvalStates(instance), "APPROVED", "PREPARING");
        projectLifecycleService.activateIfReady(instance.getBusinessId(), instance.getTenantId());
    }

    @Override
    public void onRejected(WorkflowContext context) {
        update(context.getInstance(), approvalStates(context.getInstance()), "REJECTED", "DRAFT");
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        update(context.getInstance(), approvalStates(context.getInstance()), "DRAFT", "DRAFT");
    }

    private List<String> approvalStates(WfInstance instance) {
        return instance.getCurrentRound() != null && instance.getCurrentRound() > 1
                ? List.of("APPROVING", "REJECTED", "WITHDRAWN")
                : List.of("APPROVING");
    }

    private void update(WfInstance instance, List<String> expected, String targetApproval, String targetStatus) {
        int rows = projectMapper.update(null, new LambdaUpdateWrapper<PmProject>()
                .eq(PmProject::getId, instance.getBusinessId())
                .eq(PmProject::getTenantId, instance.getTenantId())
                .in(PmProject::getApprovalStatus, expected)
                .set(PmProject::getApprovalStatus, targetApproval)
                .set(PmProject::getStatus, targetStatus));
        if (rows != 1) {
            throw new IllegalStateException("项目审批状态冲突，projectId=" + instance.getBusinessId());
        }
    }
}
