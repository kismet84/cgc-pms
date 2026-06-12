package com.cgcpms.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.workflow.WorkflowBusinessTypes;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
        checkSubmitPermission(request.getBusinessType());
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

    /**
     * Validate that the current user has the required permission (or ADMIN role)
     * to submit a workflow of the given business type.
     */
    private void checkSubmitPermission(String businessType) {
        String requiredPermission = getRequiredPermission(businessType);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AccessDeniedException("未认证");
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String authStr = authority.getAuthority();
            if ("ROLE_ADMIN".equals(authStr) || requiredPermission.equals(authStr)) {
                return;
            }
        }
        throw new AccessDeniedException("缺少权限: " + requiredPermission);
    }

    /**
     * Map business type to the required authority/permission code for submission.
     */
    private String getRequiredPermission(String businessType) {
        return switch (businessType) {
            case WorkflowBusinessTypes.CONTRACT_APPROVAL -> "contract:submit";
            case WorkflowBusinessTypes.PURCHASE_ORDER -> "purchase:order:submit";
            case WorkflowBusinessTypes.MATERIAL_RECEIPT -> "receipt:submit";
            case WorkflowBusinessTypes.SUB_MEASURE -> "subcontract:measure:submit";
            case WorkflowBusinessTypes.PAY_REQUEST -> "payment:app:submit";
            case WorkflowBusinessTypes.VAR_ORDER -> "variation:order:submit";
            case WorkflowBusinessTypes.CT_CHANGE -> "contract:change:submit";
            case WorkflowBusinessTypes.SETTLEMENT -> "settlement:submit";
            case WorkflowBusinessTypes.COST_TARGET -> "cost:target:submit";
            default -> throw new IllegalArgumentException("不支持的业务类型: " + businessType);
        };
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
        Long tenantId = UserContext.getCurrentTenantId();
        IPage<WfTaskVO> page = workflowQueryService.getMyTodos(tenantId, userId, pageNo, pageSize);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/instances/{instanceId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<WfInstanceVO> instanceDetail(@PathVariable Long instanceId) {
        Long userId = UserContext.getCurrentUserId();
        Long tenantId = UserContext.getCurrentTenantId();
        WfInstanceVO detail = workflowQueryService.getInstanceDetail(tenantId, instanceId, userId);
        return ApiResponse.success(detail);
    }
}
