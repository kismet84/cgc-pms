package com.cgcpms.material.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.material.entity.MdMaterialCategory;
import com.cgcpms.material.service.MdMaterialCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/material-categories")
@RequiredArgsConstructor
public class MdMaterialCategoryController {
    private final MdMaterialCategoryService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('material:dict:list')")
    public ApiResponse<List<MdMaterialCategory>> list() { return ApiResponse.success(service.list()); }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('material:dict:add')")
    public ApiResponse<Long> create(@Valid @RequestBody MdMaterialCategory category) { return ApiResponse.success(service.create(category)); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('material:dict:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody MdMaterialCategory category) {
        service.update(id, category); return ApiResponse.success();
    }

    @PatchMapping("/{id}/status")
    @AuditedOperation(type = "UPDATE_STATUS", businessType = "MATERIAL_CATEGORY", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('material:dict:edit')")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        service.updateStatus(id, status); return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @AuditedOperation(type = "DELETE", businessType = "MATERIAL_CATEGORY", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('material:dict:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id); return ApiResponse.success();
    }
}
