package com.cgcpms.cost.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.service.CostSubjectService;
import com.cgcpms.cost.vo.CostSubjectTreeNodeVO;
import com.cgcpms.cost.vo.CostSubjectVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

@RestController
@RequestMapping("/cost-subjects")
@RequiredArgsConstructor
public class CostSubjectController {

    private final CostSubjectService costSubjectService;

    @GetMapping("/tree")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:query')")
    public ApiResponse<List<CostSubjectTreeNodeVO>> getTree(
            @RequestParam(required = false) String category) {
        return ApiResponse.success(costSubjectService.getTree(category));
    }

    @GetMapping("/accounting-tree")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:query')")
    public ApiResponse<List<CostSubjectTreeNodeVO>> getAccountingTree(
            @RequestParam(required = false) String category) {
        return ApiResponse.success(costSubjectService.getAccountingTree(category));
    }

    @GetMapping("/accounting-overview")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:query')")
    public ApiResponse<Map<String, Object>> getAccountingOverview() {
        return ApiResponse.success(costSubjectService.getAccountingOverview());
    }

    @PutMapping("/accounting-legacy-reviews/{sourceSubjectCode}")
    @AuditedOperation(type = "UPDATE", businessType = "ACCOUNTING_SUBJECT_LEGACY_REVIEW",
            businessIdExpression = "#sourceSubjectCode")
    @PreAuthorize("hasAuthority('accounting:subject-review') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> reviewAccountingLegacySubject(
            @PathVariable String sourceSubjectCode,
            @Valid @RequestBody AccountingLegacyReviewCommand command) {
        costSubjectService.reviewAccountingLegacySubject(sourceSubjectCode, command.reviewStatus(), command.reviewNote());
        return ApiResponse.success();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('cost:query','cost:target:query','budget:query')")
    public ApiResponse<List<CostSubjectVO>> getList(
            @RequestParam(required = false) String category) {
        return ApiResponse.success(costSubjectService.getList(category));
    }

    @GetMapping("/bid-options")
    @PreAuthorize("hasAuthority('bid:cost:query') or hasRole('SUPER_ADMIN')")
    public ApiResponse<List<CostSubjectVO>> getBidOptions() {
        return ApiResponse.success(costSubjectService.getBidOptions());
    }

    @PutMapping("/target-ratios")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:edit')")
    public ApiResponse<List<CostSubjectVO>> updateTargetRatios(
            @Valid @RequestBody @Size(min = 10, max = 10) List<@Valid TargetRatioRequest> requests) {
        return ApiResponse.success(costSubjectService.updateTargetRatios(requests.stream()
                .map(request -> new CostSubjectService.TargetRatio(request.subjectCode(), request.ratio()))
                .toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:query')")
    public ApiResponse<CostSubjectVO> getById(@PathVariable Long id) {
        return ApiResponse.success(costSubjectService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:add')")
    public ApiResponse<Long> create(@Valid @RequestBody CostSubjectCommand command) {
        return ApiResponse.success(costSubjectService.create(command.toEntity()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody CostSubjectCommand command) {
        CostSubject subject = command.toEntity();
        subject.setId(id);
        costSubjectService.update(subject);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:edit')")
    public ApiResponse<Void> toggleStatus(@PathVariable Long id) {
        costSubjectService.toggleStatus(id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        costSubjectService.delete(id);
        return ApiResponse.success();
    }

    public record TargetRatioRequest(
            @NotBlank String subjectCode,
            @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000") BigDecimal ratio) {
    }

    public record AccountingLegacyReviewCommand(
            @NotBlank @Pattern(regexp = "CONFIRMED|IGNORED") String reviewStatus,
            @Size(max = 500) String reviewNote) {
    }

    public record CostSubjectCommand(
            @NotNull Long parentId,
            @NotBlank @Size(max = 64) String subjectCode,
            @NotBlank @Size(max = 128) String subjectName,
            @NotBlank @Size(max = 32) String subjectType,
            @NotBlank @Pattern(regexp = "ASSET|LIABILITY|EQUITY|COST|REVENUE|SETTLEMENT|RECEIVABLE")
            String accountCategory,
            @NotNull @Min(0) Integer sortOrder,
            @NotBlank @Pattern(regexp = "ENABLE|DISABLE") String status) {

        CostSubject toEntity() {
            CostSubject subject = new CostSubject();
            subject.setParentId(parentId);
            subject.setSubjectCode(subjectCode.trim());
            subject.setSubjectName(subjectName.trim());
            subject.setSubjectType(subjectType.trim());
            subject.setAccountCategory(accountCategory);
            subject.setSortOrder(sortOrder);
            subject.setStatus(status);
            return subject;
        }
    }
}
