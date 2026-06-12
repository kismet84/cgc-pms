package com.cgcpms.purchase.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.service.MatPurchaseOrderService;
import com.cgcpms.purchase.vo.MatPurchaseOrderItemVO;
import com.cgcpms.purchase.vo.MatPurchaseOrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/purchase-orders")
@RequiredArgsConstructor
public class MatPurchaseOrderController {

    private final MatPurchaseOrderService matPurchaseOrderService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('purchase:order:query')")
    public ApiResponse<PageResult<MatPurchaseOrderVO>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) String orderCode) {
        IPage<MatPurchaseOrderVO> page = matPurchaseOrderService.getPage(pageNum, pageSize, projectId, contractId,
                partnerId, orderStatus, orderType, orderCode);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('purchase:order:query')")
    public ApiResponse<MatPurchaseOrderVO> getById(@PathVariable Long id) {
        return ApiResponse.success(matPurchaseOrderService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('purchase:order:add')")
    public ApiResponse<Long> create(@Valid @RequestBody MatPurchaseOrder order) {
        return ApiResponse.success(matPurchaseOrderService.create(order));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('purchase:order:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody MatPurchaseOrder order) {
        order.setId(id);
        matPurchaseOrderService.update(order);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('purchase:order:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        matPurchaseOrderService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('purchase:order:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> submitForApproval(@PathVariable Long id) {
        matPurchaseOrderService.submitForApproval(id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('purchase:order:query')")
    public ApiResponse<List<MatPurchaseOrderItemVO>> getItems(@PathVariable Long id) {
        return ApiResponse.success(matPurchaseOrderService.getItems(id));
    }

    @PostMapping("/{id}/items/batch")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('purchase:order:edit')")
    public ApiResponse<Void> saveItemsBatch(@PathVariable Long id,
                                             @Valid @RequestBody List<MatPurchaseOrderItem> items) {
        matPurchaseOrderService.saveItemsBatch(id, items);
        return ApiResponse.success();
    }
}
