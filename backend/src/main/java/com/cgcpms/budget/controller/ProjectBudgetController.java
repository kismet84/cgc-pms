package com.cgcpms.budget.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.service.ProjectBudgetService;
import com.cgcpms.budget.vo.BudgetAvailabilityVO;
import com.cgcpms.budget.vo.ProjectBudgetVO;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/project-budgets")
@RequiredArgsConstructor
public class ProjectBudgetController {
    private final ProjectBudgetService budgetService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('budget:query')")
    public ApiResponse<PageResult<ProjectBudgetVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        IPage<ProjectBudgetVO> page = budgetService.getPage(pageNo, pageSize, projectId, status, startDate, endDate);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('budget:query')")
    public ApiResponse<ProjectBudgetVO> get(@PathVariable Long id) {
        return ApiResponse.success(budgetService.getById(id));
    }

    @PostMapping
    @AuditedOperation(type = "CREATE", businessType = "PROJECT_BUDGET", businessIdExpression = "#budget.id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('budget:add')")
    public ApiResponse<String> create(@Valid @RequestBody ProjectBudget budget) {
        return ApiResponse.success(String.valueOf(budgetService.create(budget)));
    }

    @PutMapping("/{id}")
    @AuditedOperation(type = "UPDATE", businessType = "PROJECT_BUDGET", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('budget:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestParam Integer version,
                                    @Valid @RequestBody ProjectBudget budget) {
        budget.setId(id);
        budgetService.update(budget, version);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/lines")
    @AuditedOperation(type = "UPDATE_LINES", businessType = "PROJECT_BUDGET", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('budget:edit')")
    public ApiResponse<Void> saveLines(
            @PathVariable Long id,
            @RequestParam Integer version,
            @Valid @Size(min = 1, max = 500, message = "预算科目数量必须为1到500条")
            @RequestBody List<@Valid ProjectBudgetLine> lines) {
        budgetService.saveLines(id, version, lines);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @AuditedOperation(type = "DELETE", businessType = "PROJECT_BUDGET", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('budget:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        budgetService.delete(id, version);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    @AuditedOperation(type = "SUBMIT", businessType = "PROJECT_BUDGET", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('budget:submit')")
    public ApiResponse<Void> submit(@PathVariable Long id, @RequestParam Integer version) {
        budgetService.submit(id, version);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('budget:query')")
    public ApiResponse<List<BudgetAvailabilityVO>> availability(@PathVariable Long id) {
        return ApiResponse.success(budgetService.getAvailability(id));
    }
}
