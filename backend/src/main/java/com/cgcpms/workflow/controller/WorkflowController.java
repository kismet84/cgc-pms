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
import com.cgcpms.workflow.vo.WfCcVO;
import com.cgcpms.workflow.vo.WfInstanceVO;
import com.cgcpms.workflow.vo.WfMyInstanceVO;
import com.cgcpms.workflow.vo.WfRecordVO;
import com.cgcpms.workflow.vo.WfTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowEngine workflowEngine;
    private final WorkflowQueryService workflowQueryService;

    @PostMapping("/submit")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<String> submit(@Valid @RequestBody WorkflowSubmitRequest request) {
        workflowEngine.checkSubmitPermission(request.getBusinessType());
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        Long tenantId = UserContext.getCurrentTenantId();
        WfInstance instance = workflowEngine.submit(userId, username, tenantId,
                request.getBusinessType(), request.getBusinessId(),
                request.getTitle(), request.getAmount(),
                request.getProjectId(), request.getContractId(),
                request.getBusinessSummary(), request.getVariables(),
                request.getCcUserIds());
        return ApiResponse.success(String.valueOf(instance.getId()));
    }

    @PostMapping("/tasks/{taskId}/approve")
    @PreAuthorize("hasAuthority('workflow:approve') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> approve(@PathVariable Long taskId,
                                      @Valid @RequestBody WorkflowActionRequest request) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.approve(taskId, userId, username,
                request.getComment(), request.getIdempotencyKey());
        return ApiResponse.success();
    }

    @PostMapping("/tasks/{taskId}/reject")
    @PreAuthorize("hasAuthority('workflow:reject') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> reject(@PathVariable Long taskId,
                                     @Valid @RequestBody WorkflowActionRequest request) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.reject(taskId, userId, username,
                request.getComment(), request.getIdempotencyKey());
        return ApiResponse.success();
    }

    @PostMapping("/instances/{instanceId}/withdraw")
    @PreAuthorize("hasAuthority('workflow:withdraw') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> withdraw(@PathVariable Long instanceId) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.withdraw(instanceId, userId, username);
        return ApiResponse.success();
    }

    @PostMapping("/instances/{instanceId}/resubmit")
    @PreAuthorize("hasAuthority('workflow:resubmit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> resubmit(@PathVariable Long instanceId) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.resubmit(instanceId, userId, username);
        return ApiResponse.success();
    }

    @PostMapping("/tasks/{taskId}/transfer")
    @PreAuthorize("hasAuthority('workflow:transfer') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> transfer(@PathVariable Long taskId,
                                       @Valid @RequestBody WorkflowTransferRequest request) {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        workflowEngine.transfer(taskId, request.getTargetUserId(),
                userId, username, request.getComment());
        return ApiResponse.success();
    }

    @PostMapping("/tasks/{taskId}/add-sign")
    @PreAuthorize("hasAuthority('workflow:add-sign') or hasAnyRole('ADMIN','SUPER_ADMIN')")
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
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String instanceStatus,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        Long userId = UserContext.getCurrentUserId();
        Long tenantId = UserContext.getCurrentTenantId();
        IPage<WfTaskVO> page = workflowQueryService.getMyTodos(tenantId, userId,
                keyword, businessType, instanceStatus, startTime, endTime, pageNo, pageSize);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/instances/mine")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResult<WfMyInstanceVO>> myStarted(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String instanceStatus,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        Long userId = UserContext.getCurrentUserId();
        Long tenantId = UserContext.getCurrentTenantId();
        IPage<WfMyInstanceVO> page = workflowQueryService.getMyStarted(tenantId, userId,
                keyword, businessType, instanceStatus, startTime, endTime, pageNo, pageSize);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/tasks/done")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResult<WfRecordVO>> myDone(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String instanceStatus,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        Long userId = UserContext.getCurrentUserId();
        Long tenantId = UserContext.getCurrentTenantId();
        IPage<WfRecordVO> page = workflowQueryService.getMyDone(userId, tenantId,
                keyword, businessType, instanceStatus, startTime, endTime, pageNo, pageSize);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/tasks/cc")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResult<WfCcVO>> myCc(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String instanceStatus,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        Long userId = UserContext.getCurrentUserId();
        Long tenantId = UserContext.getCurrentTenantId();
        IPage<WfCcVO> page = workflowQueryService.getMyCc(userId, tenantId,
                keyword, businessType, instanceStatus, startTime, endTime, pageNo, pageSize);
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
