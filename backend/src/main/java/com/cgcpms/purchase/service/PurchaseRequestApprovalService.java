package com.cgcpms.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.purchase.dto.PurchaseRequestApprovalCommand;
import com.cgcpms.purchase.dto.PurchaseRequestApprovalItemCommand;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.vo.MatPurchaseRequestItemVO;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseRequestApprovalService {
    private final WfTaskMapper taskMapper;
    private final MatPurchaseRequestItemMapper itemMapper;
    private final WorkflowEngine workflowEngine;
    private final JdbcTemplate jdbcTemplate;
    private final MatPurchaseRequestService requestService;

    public List<MatPurchaseRequestItemVO> getItemsForApproval(Long requestId, Long taskId) {
        requireCurrentPendingTask(requestId, taskId);
        return requestService.getItems(requestId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void approve(Long requestId, Long taskId, PurchaseRequestApprovalCommand command) {
        WfTask task = requireCurrentPendingTask(requestId, taskId);
        Long tenantId = UserContext.getCurrentTenantId();
        Long userId = UserContext.getCurrentUserId();
        List<MatPurchaseRequestItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseRequestItem>()
                        .eq(MatPurchaseRequestItem::getTenantId, tenantId)
                        .eq(MatPurchaseRequestItem::getRequestId, requestId));
        Map<Long, PurchaseRequestApprovalItemCommand> submitted = command.items().stream()
                .collect(Collectors.toMap(PurchaseRequestApprovalItemCommand::itemId, Function.identity(),
                        (left, right) -> { throw new BusinessException("PURCHASE_REQUEST_APPROVAL_ITEMS_DUPLICATE", "审批明细不得重复"); }));
        Set<Long> expectedIds = items.stream().map(MatPurchaseRequestItem::getId).collect(Collectors.toSet());
        if (!submitted.keySet().equals(expectedIds)) {
            throw new BusinessException("PURCHASE_REQUEST_APPROVAL_ITEMS_INCOMPLETE", "审批数量必须完整覆盖采购申请明细");
        }

        for (MatPurchaseRequestItem item : items) {
            PurchaseRequestApprovalItemCommand change = submitted.get(item.getId());
            BigDecimal oldQuantity = item.getApprovedQuantity() == null ? item.getQuantity() : item.getApprovedQuantity();
            boolean changed = oldQuantity.compareTo(change.approvedQuantity()) != 0;
            if (changed && (change.changeReason() == null || change.changeReason().isBlank())) {
                throw new BusinessException("PURCHASE_REQUEST_APPROVAL_REASON_REQUIRED", "审批数量变化时必须填写原因");
            }
            int updated = itemMapper.update(null, new LambdaUpdateWrapper<MatPurchaseRequestItem>()
                    .eq(MatPurchaseRequestItem::getId, item.getId())
                    .eq(MatPurchaseRequestItem::getTenantId, tenantId)
                    .eq(MatPurchaseRequestItem::getApprovalVersion, change.approvalVersion())
                    .set(MatPurchaseRequestItem::getApprovedQuantity, change.approvedQuantity())
                    .set(MatPurchaseRequestItem::getApprovalVersion, change.approvalVersion() + 1));
            if (updated != 1) {
                throw new BusinessException("PURCHASE_REQUEST_APPROVAL_VERSION_CONFLICT", "审批数量已变化，请刷新后重试");
            }
            if (changed) {
                jdbcTemplate.update("""
                        INSERT INTO mat_purchase_request_item_approval_change
                        (id,tenant_id,request_id,request_item_id,workflow_instance_id,workflow_task_id,
                         old_quantity,new_quantity,change_reason,changed_by,changed_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?)
                        """, IdWorker.getId(), tenantId, requestId, item.getId(), task.getInstanceId(), taskId,
                        oldQuantity, change.approvedQuantity(), change.changeReason().trim(), userId,
                        Timestamp.valueOf(LocalDateTime.now()));
            }
        }

        workflowEngine.approvePurchaseRequest(taskId, userId, UserContext.getCurrentUsername(),
                command.comment(), command.idempotencyKey());
    }

    private WfTask requireCurrentPendingTask(Long requestId, Long taskId) {
        Long tenantId = UserContext.getCurrentTenantId();
        Long userId = UserContext.getCurrentUserId();
        WfTask task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getTenantId(), tenantId)
                || !WorkflowBusinessTypes.PURCHASE_REQUEST.equals(task.getBusinessType())
                || !Objects.equals(task.getBusinessId(), requestId)) {
            throw new BusinessException("PURCHASE_REQUEST_APPROVAL_TASK_MISMATCH", "审批任务不属于当前采购申请");
        }
        if (!WorkflowConstants.TASK_PENDING.equals(task.getTaskStatus())
                || !Objects.equals(task.getApproverId(), userId)) {
            throw new BusinessException("PURCHASE_REQUEST_APPROVAL_TASK_FORBIDDEN", "审批任务状态或审批人不匹配");
        }
        return task;
    }
}
