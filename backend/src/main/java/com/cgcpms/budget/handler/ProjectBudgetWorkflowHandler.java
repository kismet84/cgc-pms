package com.cgcpms.budget.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.budget.constant.BudgetStatusConstants;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ProjectBudgetWorkflowHandler implements WorkflowBusinessHandler {
    private final ProjectBudgetMapper budgetMapper;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.PROJECT_BUDGET;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public void onApproved(WorkflowContext context) {
        Long budgetId = resolveId(context.getInstance());
        ProjectBudget budget = budgetMapper.selectByIdForUpdate(budgetId, context.getInstance().getTenantId());
        if (budget == null) throw new IllegalStateException("项目预算不存在，budgetId=" + budgetId);
        if (BudgetStatusConstants.STATUS_ACTIVE.equals(budget.getStatus())
                && Integer.valueOf(1).equals(budget.getActiveFlag())) return;

        budgetMapper.update(null, new LambdaUpdateWrapper<ProjectBudget>()
                .eq(ProjectBudget::getTenantId, budget.getTenantId())
                .eq(ProjectBudget::getProjectId, budget.getProjectId())
                .eq(ProjectBudget::getActiveFlag, 1)
                .ne(ProjectBudget::getId, budgetId)
                .set(ProjectBudget::getActiveFlag, 0)
                .set(ProjectBudget::getActiveToken, null)
                .set(ProjectBudget::getStatus, BudgetStatusConstants.STATUS_SUPERSEDED));

        int rows = budgetMapper.update(null, new LambdaUpdateWrapper<ProjectBudget>()
                .eq(ProjectBudget::getId, budgetId)
                .eq(ProjectBudget::getApprovalStatus, BudgetStatusConstants.APPROVAL_APPROVING)
                .set(ProjectBudget::getApprovalStatus, BudgetStatusConstants.APPROVAL_APPROVED)
                .set(ProjectBudget::getStatus, BudgetStatusConstants.STATUS_ACTIVE)
                .set(ProjectBudget::getActiveFlag, 1)
                .set(ProjectBudget::getActiveToken, budget.getProjectId())
                .set(ProjectBudget::getEffectiveAt, LocalDateTime.now()));
        if (rows != 1) throw new IllegalStateException("预算审批状态冲突，budgetId=" + budgetId);
    }

    @Override
    public void onRejected(WorkflowContext context) {
        budgetMapper.update(null, new LambdaUpdateWrapper<ProjectBudget>()
                .eq(ProjectBudget::getId, resolveId(context.getInstance()))
                .set(ProjectBudget::getApprovalStatus, BudgetStatusConstants.APPROVAL_REJECTED));
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        budgetMapper.update(null, new LambdaUpdateWrapper<ProjectBudget>()
                .eq(ProjectBudget::getId, resolveId(context.getInstance()))
                .set(ProjectBudget::getApprovalStatus, BudgetStatusConstants.APPROVAL_DRAFT));
    }

    private static Long resolveId(WfInstance instance) {
        if (instance.getBusinessId() == null) throw new IllegalStateException("审批实例缺少项目预算ID");
        return instance.getBusinessId();
    }
}
