package com.cgcpms.material.controller;

import com.cgcpms.common.result.ApiResponse;
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
}
