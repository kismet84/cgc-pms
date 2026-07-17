package com.cgcpms.procurement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.constant.BudgetStatusConstants;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.mapper.ProjectBudgetLineMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.project.constant.ProjectStatusConstants;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.subcontract.entity.SubTask;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 采购材料链统一完整性校验。所有进入审批或实物移动的入口必须复用本服务，
 * 避免申请、订单、验收各自形成不同口径。
 */
@Service
@RequiredArgsConstructor
public class ProcurementIntegrityService {
    private final PmProjectMapper projectMapper;
    private final ProjectBudgetMapper budgetMapper;
    private final ProjectBudgetLineMapper budgetLineMapper;
    private final SubTaskMapper subTaskMapper;
    private final SysFileMapper fileMapper;

    public PmProject requireActiveProject(Long projectId, String action) {
        Long tenantId = UserContext.getCurrentTenantId();
        PmProject project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        if (!ProjectStatusConstants.ACTIVE.equals(project.getStatus())) {
            throw new BusinessException("PROJECT_NOT_ACTIVE", "项目非执行中状态，禁止" + action);
        }
        return project;
    }

    public ProjectBudgetLine requireActiveBudgetLine(Long projectId, Long budgetLineId) {
        if (budgetLineId == null) {
            throw new BusinessException("PURCHASE_BUDGET_LINE_REQUIRED", "采购明细必须绑定项目预算科目");
        }
        Long tenantId = UserContext.getCurrentTenantId();
        ProjectBudgetLine line = budgetLineMapper.selectById(budgetLineId);
        if (line == null || !Objects.equals(line.getTenantId(), tenantId)
                || !Objects.equals(line.getProjectId(), projectId)) {
            throw new BusinessException("PURCHASE_BUDGET_LINE_INVALID", "预算科目不存在或不属于当前项目");
        }
        ProjectBudget budget = budgetMapper.selectById(line.getBudgetId());
        if (budget == null || !Objects.equals(budget.getTenantId(), tenantId)
                || !Objects.equals(budget.getProjectId(), projectId)
                || !BudgetStatusConstants.STATUS_ACTIVE.equals(budget.getStatus())
                || !Integer.valueOf(1).equals(budget.getActiveFlag())) {
            throw new BusinessException("PURCHASE_BUDGET_NOT_ACTIVE", "采购明细必须绑定当前生效预算");
        }
        return line;
    }

    public void validateWbs(Long projectId, Long wbsId) {
        if (wbsId == null) return;
        SubTask task = subTaskMapper.selectById(wbsId);
        if (task == null || !Objects.equals(task.getTenantId(), UserContext.getCurrentTenantId())
                || !Objects.equals(task.getProjectId(), projectId)) {
            throw new BusinessException("PURCHASE_WBS_INVALID", "WBS任务不存在或不属于当前项目");
        }
        if ("CANCELLED".equals(task.getStatus()) || "COMPLETED".equals(task.getStatus())) {
            throw new BusinessException("PURCHASE_WBS_CLOSED", "已取消或已完成的WBS任务不能新增采购需求");
        }
    }

    public void requireCleanAttachment(String businessType, Long businessId) {
        Long count = fileMapper.selectCount(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTenantId, UserContext.getCurrentTenantId())
                .eq(SysFile::getBusinessType, businessType)
                .eq(SysFile::getBusinessId, businessId)
                .eq(SysFile::getVirusScanStatus, "CLEAN"));
        if (count == null || count == 0) {
            throw new BusinessException("PROCUREMENT_ATTACHMENT_REQUIRED", "至少上传一份已通过安全扫描的业务附件");
        }
    }
}
