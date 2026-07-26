package com.cgcpms.budget.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.budget.entity.ContractBudgetAllocation;
import com.cgcpms.budget.service.ContractBudgetAllocationService;
import com.cgcpms.common.result.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/contracts/{contractId}/budget-allocations")
@RequiredArgsConstructor
public class ContractBudgetAllocationController {
    private final ContractBudgetAllocationService allocationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('budget:query')")
    public ApiResponse<List<ContractBudgetAllocation>> list(@PathVariable Long contractId) {
        return ApiResponse.success(allocationService.list(contractId));
    }

    @PutMapping
    @AuditedOperation(type = "UPDATE_BUDGET_ALLOCATIONS", businessType = "CONTRACT",
            businessIdExpression = "#contractId")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('budget:edit')")
    public ApiResponse<Void> save(
            @PathVariable Long contractId,
            @Valid @Size(min = 1, max = 100) @RequestBody List<@Valid ContractBudgetAllocation> rows) {
        allocationService.save(contractId, rows);
        return ApiResponse.success();
    }
}
