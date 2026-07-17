package com.cgcpms.supplierreturn.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.supplierreturn.dto.SupplierReturnRequest;
import com.cgcpms.supplierreturn.dto.SupplierReturnReversalRequest;
import com.cgcpms.supplierreturn.entity.MatSupplierReturn;
import com.cgcpms.supplierreturn.entity.MatSupplierReturnItem;
import com.cgcpms.supplierreturn.service.MatSupplierReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/supplier-returns")
@RequiredArgsConstructor
public class MatSupplierReturnController {
    private final MatSupplierReturnService returnService;

    @PostMapping("/confirm")
    @AuditedOperation(type = "CONFIRM", businessType = "SUPPLIER_RETURN")
    @PreAuthorize("hasAuthority('receipt:update') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Long> confirm(@Valid @RequestBody SupplierReturnRequest request) {
        return ApiResponse.success(returnService.confirm(request));
    }

    @PostMapping("/{id}/reverse")
    @AuditedOperation(type = "REVERSE", businessType = "SUPPLIER_RETURN")
    @PreAuthorize("hasAuthority('receipt:update') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Long> reverse(@PathVariable Long id,
                                     @Valid @RequestBody SupplierReturnReversalRequest request) {
        return ApiResponse.success(returnService.reverse(id, request.reason()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('receipt:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<MatSupplierReturn> getById(@PathVariable Long id) {
        return ApiResponse.success(returnService.getById(id));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAuthority('receipt:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<MatSupplierReturnItem>> getItems(@PathVariable Long id) {
        return ApiResponse.success(returnService.getItems(id));
    }
}
