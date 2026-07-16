package com.cgcpms.overhead.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.overhead.dto.OverheadAllocationRuleCreateRequest;
import com.cgcpms.overhead.dto.OverheadAllocationRuleUpdateRequest;
import com.cgcpms.overhead.entity.OverheadAllocationRule;
import com.cgcpms.overhead.service.OverheadAllocationService;
import com.cgcpms.overhead.vo.OverheadAllocationExecutionResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/overhead-allocation")
@RequiredArgsConstructor
public class OverheadAllocationController {

    private final OverheadAllocationService service;

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('overhead:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PageResult<OverheadAllocationRule>> getRules(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {
        IPage<OverheadAllocationRule> page = service.getPage(pageNo, pageSize);
        return ApiResponse.success(PageResult.of(page));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('overhead:add') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Long> createRule(@Valid @RequestBody OverheadAllocationRuleCreateRequest request) {
        return ApiResponse.success(service.createValidated(
                request.getCostSubjectId(), request.getAllocationBasis(), request.getAllocationCycle()));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('overhead:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> updateRule(@PathVariable Long id,
                                        @Valid @RequestBody OverheadAllocationRuleUpdateRequest request) {
        service.updateValidated(id, request.getCostSubjectId(),
                request.getAllocationBasis(), request.getAllocationCycle());
        return ApiResponse.success();
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('overhead:delete') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/execute")
    @PreAuthorize("hasAuthority('overhead:execute') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    @AuditedOperation(type = "UPDATE", businessType = "OVERHEAD_ALLOCATION", businessIdExpression = "#period")
    public ApiResponse<OverheadAllocationExecutionResult> executeAllocation(
            @RequestParam(required = false) Long tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate period) {
        // 客户端 tenantId 仅为兼容旧调用，永远不能覆盖认证租户。
        Long effectiveTenantId = UserContext.getCurrentTenantId();
        if (effectiveTenantId == null) {
            throw new BusinessException("UNAUTHORIZED", "无法确定租户身份");
        }
        return ApiResponse.success(service.executeAllocation(effectiveTenantId, period));
    }
}
