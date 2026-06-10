package com.cgcpms.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.workflow.dto.WorkflowActionRequest;
import com.cgcpms.workflow.dto.WorkflowAddSignRequest;
import com.cgcpms.workflow.dto.WorkflowSubmitRequest;
import com.cgcpms.workflow.dto.WorkflowTransferRequest;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.workflow.service.WorkflowQueryService;
import com.cgcpms.workflow.vo.WfInstanceVO;
import com.cgcpms.workflow.vo.WfTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowEngine workflowEngine;
    private final WorkflowQueryService workflowQueryService;

    /** Submit a new approval */
    @PostMapping("/submit")
    public ApiResponse<String> submit(@Valid @RequestBody WorkflowSubmitRequest request) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        Long tenantId = UserContext.getCurrentTenantId();
        WfInstance instance = workflowEngine.submit(userId, username, tenantId,
                request.getBusinessType(), request.getBusinessId(),
                request.getTitle(), request.getAmount(),
                request.getProjectId(), request.getContractId(),
                request.getBusinessSummary(), request.getVariables());
        return ApiResponse.success(String.valueOf(instance.getId()));
    }

    /** Approve a task */
    @PostMapping("/tasks/{taskId}/approve")
    public ApiResponse<Void> approve(@PathVariable Long taskId,
                                      @Valid @RequestBody WorkflowActionRequest request) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.approve(taskId, userId, username,
                request.getComment(), request.getIdempotencyKey());
        return ApiResponse.success();
    }

    /** Reject a task */
    @PostMapping("/tasks/{taskId}/reject")
    public ApiResponse<Void> reject(@PathVariable Long taskId,
                                     @Valid @RequestBody WorkflowActionRequest request) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.reject(taskId, userId, username,
                request.getComment(), request.getIdempotencyKey());
        return ApiResponse.success();
    }

    /** Withdraw an instance */
    @PostMapping("/instances/{instanceId}/withdraw")
    public ApiResponse<Void> withdraw(@PathVariable Long instanceId) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.withdraw(instanceId, userId, username);
        return ApiResponse.success();
    }

    /** Resubmit a rejected/withdrawn instance */
    @PostMapping("/instances/{instanceId}/resubmit")
    public ApiResponse<Void> resubmit(@PathVariable Long instanceId) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.resubmit(instanceId, userId, username);
        return ApiResponse.success();
    }

    /** Transfer task to another user */
    @PostMapping("/tasks/{taskId}/transfer")
    public ApiResponse<Void> transfer(@PathVariable Long taskId,
                                       @Valid @RequestBody WorkflowTransferRequest request) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.transfer(taskId, request.getTargetUserId(),
                userId, username, request.getComment());
        return ApiResponse.success();
    }

    /** Add signers to current node */
    @PostMapping("/tasks/{taskId}/add-sign")
    public ApiResponse<Void> addSign(@PathVariable Long taskId,
                                      @Valid @RequestBody WorkflowAddSignRequest request) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.addSign(taskId, request.getAdditionalUserIds(),
                userId, username, request.getComment());
        return ApiResponse.success();
    }

    /** My pending tasks */
    @GetMapping("/tasks/todo")
    public ApiResponse<PageResult<WfTaskVO>> myTodos(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {
        Long userId = UserContext.getCurrentUserId();
        IPage<WfTaskVO> page = workflowQueryService.getMyTodos(userId, pageNo, pageSize);
        return ApiResponse.success(PageResult.of(page));
    }

    /** Instance detail */
    @GetMapping("/instances/{instanceId}")
    public ApiResponse<WfInstanceVO> instanceDetail(@PathVariable Long instanceId) {
        Long userId = UserContext.getCurrentUserId();
        WfInstanceVO detail = workflowQueryService.getInstanceDetail(instanceId, userId);
        return ApiResponse.success(detail);
    }
}
