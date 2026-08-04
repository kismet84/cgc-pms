package com.cgcpms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.constant.ProjectStatusConstants;
import com.cgcpms.project.dto.ProjectCommencementSaveRequest;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.ProjectCommencement;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.ProjectCommencementMapper;
import com.cgcpms.project.vo.ProjectActivationReadinessVO;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectCommencementService {
    private final ProjectCommencementMapper commencementMapper;
    private final PmProjectMapper projectMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final ProjectLifecycleService lifecycleService;
    private final WorkflowEngine workflowEngine;

    public ProjectCommencement get(Long projectId) {
        PmProject project = requireProject(projectId, false, "查看开工准入");
        return find(project.getId(), project.getTenantId(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectCommencement save(Long projectId, ProjectCommencementSaveRequest request) {
        PmProject project = requireProject(projectId, true, "保存开工准入");
        requirePreparingProject(project);
        if (project.getPlannedEndDate() != null && request.plannedStartDate().isAfter(project.getPlannedEndDate())) {
            throw new BusinessException("PROJECT_COMMENCEMENT_DATE_INVALID", "拟开工日期不能晚于项目计划结束日期");
        }
        ProjectCommencement existing = find(projectId, project.getTenantId(), true);
        if (existing == null) {
            if (request.version() != null && request.version() != 0) {
                throw new BusinessException("PROJECT_COMMENCEMENT_VERSION_CONFLICT", "开工准入版本冲突，请刷新后重试");
            }
            ProjectCommencement created = new ProjectCommencement();
            created.setTenantId(project.getTenantId());
            created.setProjectId(projectId);
            created.setPlannedStartDate(request.plannedStartDate());
            created.setBasisType(request.basisType().trim());
            created.setApprovalStatus("DRAFT");
            created.setVersion(0);
            created.setRemark(blankToNull(request.remark()));
            try {
                commencementMapper.insert(created);
            } catch (DuplicateKeyException e) {
                throw new BusinessException("PROJECT_COMMENCEMENT_ALREADY_EXISTS", "项目开工准入单已存在，请刷新后重试");
            }
            return reread(created.getId(), project.getTenantId());
        }
        if (!Set.of("DRAFT", "REJECTED").contains(existing.getApprovalStatus())) {
            throw new BusinessException("PROJECT_COMMENCEMENT_NOT_EDITABLE", "仅草稿或驳回的开工准入单可编辑");
        }
        assertVersion(request.version(), existing.getVersion());
        int updated = commencementMapper.update(null, new LambdaUpdateWrapper<ProjectCommencement>()
                .eq(ProjectCommencement::getId, existing.getId())
                .eq(ProjectCommencement::getTenantId, project.getTenantId())
                .eq(ProjectCommencement::getProjectId, projectId)
                .eq(ProjectCommencement::getVersion, existing.getVersion())
                .set(ProjectCommencement::getPlannedStartDate, request.plannedStartDate())
                .set(ProjectCommencement::getBasisType, request.basisType().trim())
                .set(ProjectCommencement::getRemark, blankToNull(request.remark()))
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException("PROJECT_COMMENCEMENT_VERSION_CONFLICT", "开工准入已被其他用户修改，请刷新后重试");
        }
        return reread(existing.getId(), project.getTenantId());
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectCommencement submit(Long projectId, Integer version) {
        PmProject project = requireProject(projectId, true, "提交开工准入审批");
        requirePreparingProject(project);
        ProjectCommencement commencement = find(projectId, project.getTenantId(), true);
        if (commencement == null) {
            throw new BusinessException("PROJECT_COMMENCEMENT_REQUIRED", "请先创建开工准入单");
        }
        assertVersion(version, commencement.getVersion());
        if (!Set.of("DRAFT", "REJECTED").contains(commencement.getApprovalStatus())) {
            throw new BusinessException("PROJECT_COMMENCEMENT_ALREADY_SUBMITTED", "开工准入单已提交审批");
        }
        ProjectActivationReadinessVO readiness = lifecycleService.getActivationReadiness(projectId);
        java.util.List<String> blocking = readiness.blockers().stream()
                .filter(code -> !"PROJECT_COMMENCEMENT_NOT_APPROVED".equals(code))
                .toList();
        if (!blocking.isEmpty()) {
            throw new BusinessException("PROJECT_COMMENCEMENT_GATE_REQUIRED", "开工审批前置条件未满足: " + String.join(",", blocking));
        }

        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        WfInstance instance = "REJECTED".equals(commencement.getApprovalStatus())
                && commencement.getApprovalInstanceId() != null
                ? workflowEngine.resubmit(commencement.getApprovalInstanceId(), userId, username)
                : workflowEngine.submit(userId, username, project.getTenantId(),
                WorkflowBusinessTypes.PROJECT_COMMENCEMENT, commencement.getId(),
                project.getProjectName() + "开工准入", null, projectId, null, null, null, null);
        int updated = commencementMapper.update(null, new LambdaUpdateWrapper<ProjectCommencement>()
                .eq(ProjectCommencement::getId, commencement.getId())
                .eq(ProjectCommencement::getTenantId, project.getTenantId())
                .eq(ProjectCommencement::getVersion, commencement.getVersion())
                .eq(ProjectCommencement::getApprovalStatus, "APPROVING")
                .set(ProjectCommencement::getApprovalInstanceId, instance.getId())
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException("PROJECT_COMMENCEMENT_VERSION_CONFLICT", "开工准入提交发生并发冲突，请刷新后重试");
        }
        return reread(commencement.getId(), project.getTenantId());
    }

    private PmProject requireProject(Long projectId, boolean lock, String action) {
        Long tenantId = UserContext.getCurrentTenantId();
        PmProject project = lock ? projectMapper.selectOne(new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getId, projectId).eq(PmProject::getTenantId, tenantId)
                .last("FOR UPDATE")) : projectMapper.selectOne(new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getId, projectId).eq(PmProject::getTenantId, tenantId));
        projectAccessChecker.checkAccess(project, action);
        return project;
    }

    private void requirePreparingProject(PmProject project) {
        if (!ProjectStatusConstants.PREPARING.equals(project.getStatus())
                || !"APPROVED".equals(project.getApprovalStatus())
                || !Set.of("BID_AWARD", "DIRECT_APPROVAL").contains(project.getInitiationBasis())) {
            throw new BusinessException("PROJECT_COMMENCEMENT_PROJECT_NOT_READY", "仅来源明确且已批准的筹备项目可办理开工准入");
        }
    }

    private ProjectCommencement find(Long projectId, Long tenantId, boolean lock) {
        LambdaQueryWrapper<ProjectCommencement> query = new LambdaQueryWrapper<ProjectCommencement>()
                .eq(ProjectCommencement::getTenantId, tenantId)
                .eq(ProjectCommencement::getProjectId, projectId);
        if (lock) query.last("FOR UPDATE");
        return commencementMapper.selectOne(query);
    }

    private ProjectCommencement reread(Long id, Long tenantId) {
        ProjectCommencement row = commencementMapper.selectById(id);
        if (row == null || !Objects.equals(row.getTenantId(), tenantId)) {
            throw new BusinessException("PROJECT_COMMENCEMENT_READBACK_FAILED", "开工准入写后回读失败");
        }
        return row;
    }

    private void assertVersion(Integer requestVersion, Integer actualVersion) {
        if (requestVersion == null) {
            throw new BusinessException("PROJECT_COMMENCEMENT_VERSION_REQUIRED", "请求必须携带最新版本号");
        }
        if (!Objects.equals(requestVersion, actualVersion)) {
            throw new BusinessException("PROJECT_COMMENCEMENT_VERSION_CONFLICT", "开工准入版本冲突，请刷新后重试");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
