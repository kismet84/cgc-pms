package com.cgcpms.expense.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.expense.entity.ExpenseApplication;
import com.cgcpms.expense.service.ExpenseApplicationService;
import com.cgcpms.expense.vo.ExpenseApplicationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseApplicationController {
    private final ExpenseApplicationService expenseService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('expense:query')")
    public ApiResponse<PageResult<ExpenseApplicationVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String approvalStatus) {
        IPage<ExpenseApplicationVO> page = expenseService.getPage(pageNo, pageSize, projectId, contractId, approvalStatus);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('expense:query')")
    public ApiResponse<ExpenseApplicationVO> get(@PathVariable Long id) {
        return ApiResponse.success(expenseService.getById(id));
    }

    @PostMapping
    @AuditedOperation(type = "CREATE", businessType = "EXPENSE", businessIdExpression = "#expense.id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('expense:add')")
    public ApiResponse<String> create(@Valid @RequestBody ExpenseApplication expense) {
        return ApiResponse.success(String.valueOf(expenseService.create(expense)));
    }

    @PutMapping("/{id}")
    @AuditedOperation(type = "UPDATE", businessType = "EXPENSE", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('expense:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody ExpenseApplication expense) {
        expense.setId(id);
        expenseService.update(expense);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @AuditedOperation(type = "DELETE", businessType = "EXPENSE", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('expense:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        expenseService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    @AuditedOperation(type = "SUBMIT", businessType = "EXPENSE", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('expense:submit')")
    public ApiResponse<Void> submit(@PathVariable Long id) {
        expenseService.submit(id);
        return ApiResponse.success();
    }
}
