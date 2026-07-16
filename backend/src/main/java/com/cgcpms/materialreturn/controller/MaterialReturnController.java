package com.cgcpms.materialreturn.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.materialreturn.dto.MaterialReturnRequest;
import com.cgcpms.materialreturn.entity.MaterialReturn;
import com.cgcpms.materialreturn.entity.MaterialReturnItem;
import com.cgcpms.materialreturn.service.MaterialReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/material-returns")
@RequiredArgsConstructor
public class MaterialReturnController {
    private final MaterialReturnService returnService;

    @PostMapping("/confirm")
    @AuditedOperation(type = "CONFIRM", businessType = "MATERIAL_RETURN")
    @PreAuthorize("hasAuthority('requisition:return') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Long> confirm(@Valid @RequestBody MaterialReturnRequest request) {
        return ApiResponse.success(returnService.confirm(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('requisition:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<MaterialReturn> getById(@PathVariable Long id) {
        return ApiResponse.success(returnService.getById(id));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAuthority('requisition:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<MaterialReturnItem>> getItems(@PathVariable Long id) {
        return ApiResponse.success(returnService.getItems(id));
    }
}
