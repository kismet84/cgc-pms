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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowEngine workflowEngine;
    private final WorkflowQueryService workflowQueryService;

    @PostMapping("/submit")
    @PreAuthorize("isAuthenticated()")
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

    @PostMapping("/tasks/{taskId}/approve")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> approve(@PathVariable Long taskId,
                                      @Valid @RequestBody WorkflowActionRequest request) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.approve(taskId, userId, username,
                request.getComment(), request.getIdempotencyKey());
        return ApiResponse.success();
    }

    @PostMapping("/tasks/{taskId}/reject")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> reject(@PathVariable Long taskId,
                                     @Valid @RequestBody WorkflowActionRequest request) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.reject(taskId, userId, username,
                request.getComment(), request.getIdempotencyKey());
        return ApiResponse.success();
    }

    @PostMapping("/instances/{instanceId}/withdraw")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> withdraw(@PathVariable Long instanceId) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.withdraw(instanceId, userId, username);
        return ApiResponse.success();
    }

    @PostMapping("/instances/{instanceId}/resubmit")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> resubmit(@PathVariable Long instanceId) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.resubmit(instanceId, userId, username);
        return ApiResponse.success();
    }

    @PostMapping("/tasks/{taskId}/transfer")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> transfer(@PathVariable Long taskId,
                                       @Valid @RequestBody WorkflowTransferRequest request) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.transfer(taskId, request.getTargetUserId(),
                userId, username, request.getComment());
        return ApiResponse.success();
    }

    @PostMapping("/tasks/{taskId}/add-sign")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> addSign(@PathVariable Long taskId,
                                      @Valid @RequestBody WorkflowAddSignRequest request) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.addSign(taskId, request.getAdditionalUserIds(),
                userId, username, request.getComment());
        return ApiResponse.success();
    }

    @GetMapping("/tasks/todo")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResult<WfTaskVO>> myTodos(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {
        Long userId = UserContext.getCurrentUserId();
        IPage<WfTaskVO> page = workflowQueryService.getMyTodos(userId, pageNo, pageSize);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/instances/{instanceId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<WfInstanceVO> instanceDetail(@PathVariable Long instanceId) {
        Long userId = UserContext.getCurrentUserId();
        WfInstanceVO detail = workflowQueryService.getInstanceDetail(instanceId, userId);
        return ApiResponse.success(detail);
    }
}
