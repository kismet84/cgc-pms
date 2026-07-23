package com.cgcpms.variation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.variation.dto.VariationClaimModels.OwnerReviewRequest;
import com.cgcpms.variation.dto.VariationClaimModels.OwnerSubmissionRequest;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.entity.VarOrderItem;
import com.cgcpms.variation.service.VarOrderService;
import com.cgcpms.variation.vo.VarOrderItemVO;
import com.cgcpms.variation.vo.VarOrderVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/var-orders")
@RequiredArgsConstructor
public class VarOrderController {

    private final VarOrderService varOrderService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:order:query')")
    public ApiResponse<PageResult<VarOrderVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String varType,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String varCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        IPage<VarOrderVO> page = varOrderService.getPage(pageNo, pageSize, projectId, contractId,
                partnerId, varType, direction, varCode, startDate, endDate);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:order:query')")
    public ApiResponse<VarOrderVO> getById(@PathVariable Long id) {
        return ApiResponse.success(varOrderService.getById(id));
    }

    @PostMapping
    @AuditedOperation(type="CREATE", businessType="VAR_ORDER", businessIdExpression="#order.id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:order:add')")
    public ApiResponse<Long> create(@Valid @RequestBody VarOrder order) {
        return ApiResponse.success(varOrderService.create(order));
    }

    @PutMapping("/{id}")
    @AuditedOperation(type="UPDATE", businessType="VAR_ORDER", businessIdExpression="#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:order:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody VarOrder order) {
        order.setId(id);
        varOrderService.update(order);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @AuditedOperation(type="DELETE", businessType="VAR_ORDER", businessIdExpression="#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:order:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        varOrderService.delete(id, version);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    @AuditedOperation(type="SUBMIT", businessType="VAR_ORDER", businessIdExpression="#id")
    @PreAuthorize("hasAuthority('variation:order:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> submitForApproval(@PathVariable Long id, @RequestParam Integer version) {
        varOrderService.submitForApproval(id, version);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:order:query')")
    public ApiResponse<List<VarOrderItemVO>> listItems(@PathVariable Long id) {
        VarOrderVO vo = varOrderService.getById(id);
        return ApiResponse.success(vo.getItems());
    }

    @PostMapping("/{id}/items/batch")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:order:item:edit')")
    public ApiResponse<Void> batchSaveItems(@PathVariable Long id,
                                            @Valid @Size(max = 200, message = "批量明细不能超过200条")
                                            @RequestBody List<VarOrderItem> items,
                                            @RequestParam Integer version) {
        varOrderService.saveItems(id, items, version);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/owner-submissions")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:owner:submit')")
    @AuditedOperation(type="SUBMIT_OWNER", businessType="VARIATION_OWNER_SUBMISSION", businessIdExpression="#id")
    public ApiResponse<Map<String, Object>> submitToOwner(@PathVariable Long id,
                                                          @Valid @RequestBody OwnerSubmissionRequest request,
                                                          @RequestParam Integer version) {
        return ApiResponse.success(varOrderService.submitToOwner(id, request, version));
    }

    @PostMapping("/{id}/owner-submissions/{submissionId}/review")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:owner:review')")
    @AuditedOperation(type="OWNER_REVIEW", businessType="VARIATION_OWNER_SUBMISSION", businessIdExpression="#submissionId")
    public ApiResponse<Map<String, Object>> reviewOwnerSubmission(@PathVariable Long id,
                                                                  @PathVariable Long submissionId,
                                                                  @Valid @RequestBody OwnerReviewRequest request,
                                                                  @RequestParam Integer version) {
        return ApiResponse.success(varOrderService.reviewOwnerSubmission(id, submissionId, request, version));
    }

    @GetMapping("/{id}/trace")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('variation:trace')")
    public ApiResponse<Map<String, Object>> trace(@PathVariable Long id) {
        return ApiResponse.success(varOrderService.trace(id));
    }
}
