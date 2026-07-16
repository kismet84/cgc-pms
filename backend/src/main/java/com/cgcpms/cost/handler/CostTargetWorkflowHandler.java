package com.cgcpms.cost.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.entity.CostTargetItem;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.cost.mapper.CostTargetItemMapper;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.cost.service.CostTargetService;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowBusinessHandler;
import com.cgcpms.workflow.handler.WorkflowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business handler for cost target approval workflows.
 * On approval: sets approvalStatus=APPROVED, deactivates old versions,
 * activates the new version, and updates cost_summary.cost_target_id.
 * Critical handler: exceptions propagate to trigger full rollback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CostTargetWorkflowHandler implements WorkflowBusinessHandler {

    private final CostTargetMapper costTargetMapper;
    private final CostTargetItemMapper costTargetItemMapper;
    private final CostSummaryMapper costSummaryMapper;
    private final CostTargetService costTargetService;
    private final CostSummaryService costSummaryService;
    private final PmProjectMapper projectMapper;

    @Override
    public String supportBusinessType() {
        return WorkflowBusinessTypes.COST_TARGET;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public void beforeSubmit(WorkflowContext context) {
        Long targetId = resolveTargetId(context.getInstance());
        log.info("提交目标成本审批前校验 targetId={}", targetId);

        CostTarget target = costTargetMapper.selectById(targetId);
        if (target == null) {
            throw new BusinessException("COST_TARGET_NOT_FOUND", "目标成本不存在，targetId=" + targetId);
        }

        costTargetService.validateForSubmit(target);

        // 将状态改为审批中
        target.setApprovalStatus("APPROVING");
        costTargetMapper.updateById(target);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(WorkflowContext context) {
        Long targetId = resolveTargetId(context.getInstance());
        log.info("目标成本审批通过，激活版本并更新成本汇总 targetId={}", targetId);

        CostTarget target = costTargetMapper.selectById(targetId);
        if (target == null) {
            throw new IllegalStateException("目标成本不存在，targetId=" + targetId);
        }

        // 1. 设置审批状态为 APPROVED
        target.setApprovalStatus("APPROVED");
        target.setStatus("APPROVED");
        costTargetMapper.updateById(target);

        // 2. 版本切换：旧版本 is_active=0，新版本 is_active=1 + status=ACTIVE
        costTargetService.activate(targetId);

        // 3. 更新 cost_summary.cost_target_id 指向新激活的版本
        costSummaryMapper.update(null, new LambdaUpdateWrapper<CostSummary>()
                .eq(CostSummary::getProjectId, target.getProjectId())
                .eq(CostSummary::getTenantId, target.getTenantId())
                .set(CostSummary::getCostTargetId, targetId));

        PmProject project = projectMapper.selectById(target.getProjectId());
        if (project == null || !target.getTenantId().equals(project.getTenantId())) {
            throw new IllegalStateException("目标成本所属项目不存在或租户不一致，projectId=" + target.getProjectId());
        }
        if (target.getTotalTargetAmount() != null) {
            project.setTargetCost(target.getTotalTargetAmount());
            projectMapper.updateById(project);
        }
        costSummaryService.refreshSummary(target.getTenantId(), target.getProjectId());
    }

    @Override
    public void onRejected(WorkflowContext context) {
        Long targetId = resolveTargetId(context.getInstance());
        log.info("目标成本审批驳回 targetId={}", targetId);

        costTargetMapper.update(null, new LambdaUpdateWrapper<CostTarget>()
                .eq(CostTarget::getId, targetId)
                .set(CostTarget::getApprovalStatus, "REJECTED")
                .set(CostTarget::getStatus, "REJECTED"));
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        Long targetId = resolveTargetId(context.getInstance());
        log.info("目标成本审批撤回，恢复为草稿 targetId={}", targetId);

        costTargetMapper.update(null, new LambdaUpdateWrapper<CostTarget>()
                .eq(CostTarget::getId, targetId)
                .set(CostTarget::getApprovalStatus, "DRAFT")
                .set(CostTarget::getStatus, "DRAFT"));
    }

    private Long resolveTargetId(WfInstance instance) {
        Long targetId = instance.getBusinessId();
        if (targetId == null) {
            throw new IllegalStateException(
                    "审批实例缺少业务ID（目标成本ID），instanceId=" + instance.getId());
        }
        return targetId;
    }
}
