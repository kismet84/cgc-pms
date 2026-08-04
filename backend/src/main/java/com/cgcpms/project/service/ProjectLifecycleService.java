package com.cgcpms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.constant.BudgetStatusConstants;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.constant.ProjectStatusConstants;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.ProjectCommencement;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.ProjectCommencementMapper;
import com.cgcpms.project.vo.ProjectActivationReadinessVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectLifecycleService {
    private final PmProjectMapper projectMapper;
    private final ProjectBudgetMapper projectBudgetMapper;
    private final CostTargetMapper costTargetMapper;
    private final ProjectCommencementMapper commencementMapper;
    private final SysFileMapper fileMapper;
    private final OwnerContractFactService ownerContractFactService;
    private final ProjectAccessChecker projectAccessChecker;
    private final JdbcTemplate jdbc;

    /** Legacy approval callbacks must never activate projects. */
    public boolean activateIfReady(Long projectId, Long tenantId) {
        return false;
    }

    public ProjectActivationReadinessVO getActivationReadiness(Long projectId) {
        PmProject project = projectMapper.selectById(projectId);
        projectAccessChecker.checkAccess(project, "查看开工准入");
        return readiness(project);
    }

    public boolean isCostBudgetReady(Long projectId, Long tenantId) {
        PmProject project = ownedProject(projectId, tenantId, false);
        ProjectActivationReadinessVO result = readiness(project);
        return result.blockers().stream().noneMatch(code -> code.startsWith("PROJECT_OWNER_CONTRACT_")
                || code.startsWith("COST_TARGET_") || code.startsWith("PROJECT_BUDGET_"));
    }

    public boolean isActivationReady(Long projectId, Long tenantId) {
        return readiness(ownedProject(projectId, tenantId, false)).ready();
    }

    @Transactional(rollbackFor = Exception.class)
    public PmProject activateFromCommencementApproval(Long projectId, Long tenantId, Long commencementId) {
        PmProject project = ownedProject(projectId, tenantId, true);
        if (ProjectStatusConstants.ACTIVE.equals(project.getStatus())) return project;
        if (!ProjectStatusConstants.PREPARING.equals(project.getStatus())) {
            throw new BusinessException("PROJECT_COMMENCEMENT_STATE_CONFLICT", "仅筹备项目可由开工审批启用");
        }
        ProjectActivationReadinessVO result = readiness(project);
        if (!result.ready() || !Objects.equals(result.commencementId(), String.valueOf(commencementId))) {
            throw new BusinessException("PROJECT_ACTIVE_GATE_REQUIRED", "开工准入条件未满足: " + String.join(",", result.blockers()));
        }
        ProjectCommencement commencement = commencementMapper.selectById(commencementId);
        if (commencement == null || commencement.getActualStartDate() == null
                || !Objects.equals(commencement.getTenantId(), tenantId)
                || !Objects.equals(commencement.getProjectId(), projectId)) {
            throw new BusinessException("PROJECT_COMMENCEMENT_INVALID", "开工准入单不存在、跨租户或缺少实际开工日期");
        }
        int updated = projectMapper.update(null, new LambdaUpdateWrapper<PmProject>()
                .eq(PmProject::getId, projectId)
                .eq(PmProject::getTenantId, tenantId)
                .eq(PmProject::getStatus, ProjectStatusConstants.PREPARING)
                .eq(PmProject::getApprovalStatus, "APPROVED")
                .set(PmProject::getStatus, ProjectStatusConstants.ACTIVE)
                .set(PmProject::getActualStartDate, commencement.getActualStartDate()));
        if (updated != 1) {
            throw new BusinessException("PROJECT_ACTIVATION_CONFLICT", "项目已被并发启用或状态已变化");
        }
        PmProject reread = projectMapper.selectById(projectId);
        if (reread == null || !ProjectStatusConstants.ACTIVE.equals(reread.getStatus())
                || !Objects.equals(reread.getActualStartDate(), commencement.getActualStartDate())) {
            throw new BusinessException("PROJECT_ACTIVATION_READBACK_FAILED", "项目启用写后回读不一致");
        }
        return reread;
    }

    private ProjectActivationReadinessVO readiness(PmProject project) {
        List<String> blockers = new ArrayList<>();
        if (!"APPROVED".equals(project.getApprovalStatus())
                || !Set.of(ProjectStatusConstants.PREPARING, ProjectStatusConstants.ACTIVE,
                ProjectStatusConstants.SUSPENDED).contains(project.getStatus())) {
            blockers.add("PROJECT_STATE_NOT_READY");
        }
        if (!"BID_AWARD".equals(project.getInitiationBasis())
                && !"DIRECT_APPROVAL".equals(project.getInitiationBasis())) {
            blockers.add("PROJECT_INITIATION_BASIS_INVALID");
        }

        OwnerContractFactService.OwnerContractFact owner = null;
        try {
            owner = ownerContractFactService.requireApprovedMain(project);
        } catch (BusinessException e) {
            blockers.add(e.getCode());
        }

        List<CostTarget> targets = costTargetMapper.selectList(new LambdaQueryWrapper<CostTarget>()
                .eq(CostTarget::getTenantId, project.getTenantId())
                .eq(CostTarget::getProjectId, project.getId())
                .eq(CostTarget::getApprovalStatus, "APPROVED")
                .eq(CostTarget::getStatus, "ACTIVE")
                .eq(CostTarget::getIsActive, 1));
        CostTarget target = targets.size() == 1 ? targets.getFirst() : null;
        if (target == null) blockers.add("COST_TARGET_ACTIVE_UNIQUE_REQUIRED");
        if (target != null && owner != null) {
            if (!Objects.equals(target.getSourceContractId(), owner.contractId())) {
                blockers.add("COST_TARGET_SOURCE_CONTRACT_MISMATCH");
            }
            if (target.getSourceContractAmount() == null
                    || target.getSourceContractAmount().compareTo(owner.currentAmount()) != 0) {
                blockers.add("COST_TARGET_CONTRACT_AMOUNT_MISMATCH");
            }
        }

        List<ProjectBudget> budgets = projectBudgetMapper.selectList(
                new LambdaQueryWrapper<ProjectBudget>()
                        .eq(ProjectBudget::getTenantId, project.getTenantId())
                        .eq(ProjectBudget::getProjectId, project.getId())
                        .eq(ProjectBudget::getApprovalStatus, BudgetStatusConstants.APPROVAL_APPROVED)
                        .eq(ProjectBudget::getStatus, BudgetStatusConstants.STATUS_ACTIVE)
                        .eq(ProjectBudget::getActiveFlag, 1));
        ProjectBudget budget = budgets.size() == 1 ? budgets.getFirst() : null;
        if (budget == null) blockers.add("PROJECT_BUDGET_ACTIVE_UNIQUE_REQUIRED");
        if (budget != null && (target == null || !Objects.equals(budget.getSourceCostTargetId(), target.getId()))) {
            blockers.add("PROJECT_BUDGET_SOURCE_MISMATCH");
        }

        List<Long> schedules = jdbc.queryForList("""
                SELECT id FROM project_schedule_plan
                WHERE tenant_id=? AND project_id=? AND status='ACTIVE' AND deleted_flag=0
                """, Long.class, project.getTenantId(), project.getId());
        if (schedules.size() != 1) blockers.add("PROJECT_WBS_ACTIVE_UNIQUE_REQUIRED");

        List<ProjectCommencement> commencements = commencementMapper.selectList(
                new LambdaQueryWrapper<ProjectCommencement>()
                        .eq(ProjectCommencement::getTenantId, project.getTenantId())
                        .eq(ProjectCommencement::getProjectId, project.getId()));
        ProjectCommencement commencement = commencements.size() == 1 ? commencements.getFirst() : null;
        if (commencement == null) {
            blockers.add("PROJECT_COMMENCEMENT_REQUIRED");
        } else {
            if (!"APPROVED".equals(commencement.getApprovalStatus())) {
                blockers.add("PROJECT_COMMENCEMENT_NOT_APPROVED");
            }
            long cleanFiles = fileMapper.selectCount(new LambdaQueryWrapper<SysFile>()
                    .eq(SysFile::getTenantId, project.getTenantId())
                    .eq(SysFile::getBusinessType, "PROJECT_COMMENCEMENT")
                    .eq(SysFile::getBusinessId, commencement.getId())
                    .eq(SysFile::getDocumentType, "COMMENCEMENT_BASIS")
                    .eq(SysFile::getVirusScanStatus, "CLEAN"));
            if (cleanFiles < 1) blockers.add("PROJECT_COMMENCEMENT_BASIS_FILE_REQUIRED");
        }

        return new ProjectActivationReadinessVO(String.valueOf(project.getId()), project.getInitiationBasis(),
                owner == null ? null : String.valueOf(owner.contractId()), owner == null ? null : owner.contractCode(),
                owner == null ? null : owner.currentAmount(), target == null ? null : String.valueOf(target.getId()),
                budget == null ? null : String.valueOf(budget.getId()),
                schedules.size() == 1 ? String.valueOf(schedules.getFirst()) : null,
                commencement == null ? null : String.valueOf(commencement.getId()),
                commencement == null ? null : commencement.getApprovalStatus(), blockers.isEmpty(), List.copyOf(blockers));
    }

    private PmProject ownedProject(Long projectId, Long tenantId, boolean lock) {
        PmProject project = lock ? projectMapper.selectOne(new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getId, projectId).eq(PmProject::getTenantId, tenantId)
                .last("FOR UPDATE")) : projectMapper.selectOne(new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getId, projectId).eq(PmProject::getTenantId, tenantId));
        if (project == null) throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        return project;
    }
}
