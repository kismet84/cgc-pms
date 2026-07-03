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

        // 校验：cost_target_item 不能为空
        List<CostTargetItem> items = costTargetItemMapper.selectList(
                new LambdaQueryWrapper<CostTargetItem>()
                        .eq(CostTargetItem::getTargetId, targetId));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("COST_TARGET_NO_ITEMS", "目标成本明细为空，请至少添加一条科目");
        }

        // 校验：cost_target_item 不能有 null/0 必填科目
        for (CostTargetItem item : items) {
            if (item.getCostSubjectId() == null || item.getCostSubjectId() == 0) {
                throw new BusinessException("COST_TARGET_ITEM_INVALID",
                        "目标成本明细存在未指定科目的记录，请完善所有科目");
            }
        }

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
        costTargetMapper.updateById(target);

        // 2. 版本切换：旧版本 is_active=0，新版本 is_active=1 + status=ACTIVE
        costTargetService.activate(targetId);

        // 3. 更新 cost_summary.cost_target_id 指向新激活的版本
        costSummaryMapper.update(null, new LambdaUpdateWrapper<CostSummary>()
                .eq(CostSummary::getProjectId, target.getProjectId())
                .set(CostSummary::getCostTargetId, targetId));
    }

    @Override
    public void onRejected(WorkflowContext context) {
        Long targetId = resolveTargetId(context.getInstance());
        log.info("目标成本审批驳回 targetId={}", targetId);

        costTargetMapper.update(null, new LambdaUpdateWrapper<CostTarget>()
                .eq(CostTarget::getId, targetId)
                .set(CostTarget::getApprovalStatus, "REJECTED"));
    }

    @Override
    public void onWithdrawn(WorkflowContext context) {
        Long targetId = resolveTargetId(context.getInstance());
        log.info("目标成本审批撤回，恢复为草稿 targetId={}", targetId);

        costTargetMapper.update(null, new LambdaUpdateWrapper<CostTarget>()
                .eq(CostTarget::getId, targetId)
                .set(CostTarget::getApprovalStatus, "DRAFT"));
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
