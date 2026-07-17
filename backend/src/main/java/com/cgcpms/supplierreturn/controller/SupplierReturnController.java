package com.cgcpms.supplierreturn.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.supplierreturn.dto.SupplierReturnRequest;
import com.cgcpms.supplierreturn.entity.SupplierReturn;
import com.cgcpms.supplierreturn.entity.SupplierReturnItem;
import com.cgcpms.supplierreturn.service.SupplierReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/supplier-returns")
@RequiredArgsConstructor
public class SupplierReturnController {
    private final SupplierReturnService service;

    @PostMapping
    @AuditedOperation(type = "CREATE", businessType = "SUPPLIER_RETURN")
    @PreAuthorize("hasAuthority('receipt:return') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Long> create(@Valid @RequestBody SupplierReturnRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PostMapping("/{id}/confirm")
    @AuditedOperation(type = "CONFIRM", businessType = "SUPPLIER_RETURN", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('receipt:return') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> confirm(@PathVariable Long id) {
        service.confirm(id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('receipt:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<SupplierReturn> getById(@PathVariable Long id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAuthority('receipt:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<SupplierReturnItem>> getItems(@PathVariable Long id) {
        return ApiResponse.success(service.getItems(id));
    }
}
