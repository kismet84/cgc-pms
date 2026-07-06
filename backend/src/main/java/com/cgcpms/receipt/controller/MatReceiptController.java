package com.cgcpms.receipt.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.service.MatReceiptService;
import com.cgcpms.receipt.vo.MatReceiptItemVO;
import com.cgcpms.receipt.vo.MatReceiptVO;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/receipts")
@RequiredArgsConstructor
public class MatReceiptController {

    private final MatReceiptService matReceiptService;
    private final Validator validator;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('receipt:query')")
    public ApiResponse<PageResult<MatReceiptVO>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String receiptCode,
            @RequestParam(required = false) String qualityStatus) {
        IPage<MatReceiptVO> page = matReceiptService.getPage(pageNum, pageSize, projectId,
                orderId, contractId, partnerId, receiptCode, qualityStatus);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('receipt:query')")
    public ApiResponse<MatReceiptVO> getById(@PathVariable Long id) {
        return ApiResponse.success(matReceiptService.getById(id));
    }

    @PostMapping
    @AuditedOperation(type = "CREATE", businessType = "RECEIPT", businessIdExpression = "#receipt.id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('receipt:add')")
    public ApiResponse<Long> create(@Valid @RequestBody MatReceipt receipt) {
        return ApiResponse.success(matReceiptService.create(receipt));
    }

    @PutMapping("/{id}")
    @AuditedOperation(type = "UPDATE", businessType = "RECEIPT", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('receipt:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody MatReceipt receipt) {
        receipt.setId(id);
        matReceiptService.update(receipt);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @AuditedOperation(type = "DELETE", businessType = "RECEIPT", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('receipt:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        matReceiptService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    @AuditedOperation(type = "SUBMIT", businessType = "RECEIPT", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('receipt:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> submitForApproval(@PathVariable Long id) {
        matReceiptService.submitForApproval(id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('receipt:query')")
    public ApiResponse<List<MatReceiptItemVO>> getItems(@PathVariable Long id) {
        return ApiResponse.success(matReceiptService.getItems(id));
    }

    @PostMapping("/{id}/items/batch")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('receipt:edit')")
    public ApiResponse<Void> saveItemsBatch(@PathVariable Long id,
                                             @Valid @Size(max = 200, message = "批量明细不能超过200条")
                                             @RequestBody List<MatReceiptItem> items) {
        for (int i = 0; i < items.size(); i++) {
            var violations = validator.validate(items.get(i));
            if (!violations.isEmpty()) {
                return ApiResponse.fail("400", "第" + (i + 1) + "条记录校验失败: " +
                        violations.iterator().next().getMessage());
            }
        }
        matReceiptService.saveItemsBatch(id, items);
        return ApiResponse.success();
    }

    @GetMapping("/orders/{orderId}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('receipt:query')")
    public ApiResponse<List<MatReceiptItemVO>> getOrderItemsForReceipt(@PathVariable Long orderId) {
        return ApiResponse.success(matReceiptService.getOrderItemsForReceipt(orderId));
    }
}
