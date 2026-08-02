package com.cgcpms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.budget.constant.BudgetStatusConstants;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.project.constant.ProjectStatusConstants;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project lifecycle gate shared by project, budget and target-cost approvals.
 * Project becomes ACTIVE only after both approved versions are active.
 */
@Service
@RequiredArgsConstructor
public class ProjectLifecycleService {
    private final PmProjectMapper projectMapper;
    private final ProjectBudgetMapper projectBudgetMapper;
    private final CostTargetMapper costTargetMapper;

    @Transactional(rollbackFor = Exception.class)
    public boolean activateIfReady(Long projectId, Long tenantId) {
        if (!isCostBudgetReady(projectId, tenantId)) return false;
        int rows = projectMapper.update(null, new LambdaUpdateWrapper<PmProject>()
                .eq(PmProject::getId, projectId)
                .eq(PmProject::getTenantId, tenantId)
                .eq(PmProject::getApprovalStatus, "APPROVED")
                .eq(PmProject::getStatus, ProjectStatusConstants.PREPARING)
                .set(PmProject::getStatus, ProjectStatusConstants.ACTIVE));
        return rows == 1;
    }

    public boolean isCostBudgetReady(Long projectId, Long tenantId) {
        CostTarget activeTarget = costTargetMapper.selectOne(new LambdaQueryWrapper<CostTarget>()
                .eq(CostTarget::getTenantId, tenantId)
                .eq(CostTarget::getProjectId, projectId)
                .eq(CostTarget::getApprovalStatus, "APPROVED")
                .eq(CostTarget::getStatus, "ACTIVE")
                .eq(CostTarget::getIsActive, 1));
        if (activeTarget == null) return false;

        long linkedBudgetCount = projectBudgetMapper.selectCount(new LambdaQueryWrapper<ProjectBudget>()
                .eq(ProjectBudget::getTenantId, tenantId)
                .eq(ProjectBudget::getProjectId, projectId)
                .eq(ProjectBudget::getSourceCostTargetId, activeTarget.getId())
                .eq(ProjectBudget::getApprovalStatus, BudgetStatusConstants.APPROVAL_APPROVED)
                .eq(ProjectBudget::getStatus, BudgetStatusConstants.STATUS_ACTIVE)
                .eq(ProjectBudget::getActiveFlag, 1));
        return linkedBudgetCount == 1;
    }
}
