package com.cgcpms.project.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.constant.ProjectStatusConstants;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.ProjectCommencement;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.ProjectCommencementMapper;
import com.cgcpms.project.service.ProjectLifecycleService;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ProjectCommencementWorkflowHandler implements WorkflowBusinessHandler {
    private final ProjectCommencementMapper commencementMapper;
    private final PmProjectMapper projectMapper;
    private final ProjectLifecycleService lifecycleService;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.PROJECT_COMMENCEMENT;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        transition(context.getInstance(), java.util.List.of("DRAFT", "REJECTED", "APPROVING"), "APPROVING");
    }

    @Override
    public void onApproved(WorkflowContext context) {
        WfInstance instance = context.getInstance();
        ProjectCommencement current = require(instance);
        PmProject project = projectMapper.selectById(current.getProjectId());
        if ("APPROVED".equals(current.getApprovalStatus()) && project != null
                && ProjectStatusConstants.ACTIVE.equals(project.getStatus())) return;
        if (!"APPROVING".equals(current.getApprovalStatus())) {
            throw new BusinessException("PROJECT_COMMENCEMENT_STATUS_CONFLICT", "开工审批状态冲突");
        }
        LocalDate actualStart = LocalDate.now();
        int updated = commencementMapper.update(null, new LambdaUpdateWrapper<ProjectCommencement>()
                .eq(ProjectCommencement::getId, current.getId())
                .eq(ProjectCommencement::getTenantId, instance.getTenantId())
                .eq(ProjectCommencement::getVersion, current.getVersion())
                .eq(ProjectCommencement::getApprovalStatus, "APPROVING")
                .set(ProjectCommencement::getApprovalStatus, "APPROVED")
                .set(ProjectCommencement::getActualStartDate, actualStart)
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException("PROJECT_COMMENCEMENT_APPROVAL_CONFLICT", "开工审批已被并发处理");
        }
        ProjectCommencement reread = require(instance);
        if (!"APPROVED".equals(reread.getApprovalStatus())
                || !Objects.equals(actualStart, reread.getActualStartDate())) {
            throw new BusinessException("PROJECT_COMMENCEMENT_READBACK_FAILED", "开工审批写后回读不一致");
        }
        lifecycleService.activateFromCommencementApproval(reread.getProjectId(), instance.getTenantId(), reread.getId());
    }

    @Override
    public void onRejected(WorkflowContext context) {
        transition(context.getInstance(), java.util.List.of("APPROVING"), "REJECTED");
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        ProjectCommencement current = require(context.getInstance());
        PmProject project = projectMapper.selectById(current.getProjectId());
        if (project != null && ProjectStatusConstants.ACTIVE.equals(project.getStatus())) {
            throw new BusinessException("PROJECT_COMMENCEMENT_ACTIVE_WITHDRAW_FORBIDDEN", "项目已在建，禁止直接撤销开工准入");
        }
        transition(context.getInstance(), java.util.List.of("APPROVING"), "DRAFT");
    }

    private void transition(WfInstance instance, java.util.List<String> expected, String target) {
        ProjectCommencement current = require(instance);
        int updated = commencementMapper.update(null, new LambdaUpdateWrapper<ProjectCommencement>()
                .eq(ProjectCommencement::getId, current.getId())
                .eq(ProjectCommencement::getTenantId, instance.getTenantId())
                .eq(ProjectCommencement::getVersion, current.getVersion())
                .in(ProjectCommencement::getApprovalStatus, expected)
                .set(ProjectCommencement::getApprovalStatus, target));
        if (updated != 1) {
            throw new BusinessException("PROJECT_COMMENCEMENT_STATUS_CONFLICT", "开工审批状态冲突");
        }
    }

    private ProjectCommencement require(WfInstance instance) {
        if (instance.getBusinessId() == null) {
            throw new BusinessException("PROJECT_COMMENCEMENT_BUSINESS_ID_REQUIRED", "开工审批实例缺少业务ID");
        }
        ProjectCommencement row = commencementMapper.selectById(instance.getBusinessId());
        if (row == null || !Objects.equals(row.getTenantId(), instance.getTenantId())) {
            throw new BusinessException("PROJECT_COMMENCEMENT_NOT_FOUND", "开工准入单不存在");
        }
        return row;
    }
}
