package com.cgcpms.inventory.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.service.MatWarehouseService;
import com.cgcpms.inventory.vo.MatWarehouseVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/warehouses")
@RequiredArgsConstructor
public class MatWarehouseController {

    private final MatWarehouseService matWarehouseService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('inventory:warehouse:*')")
    public ApiResponse<PageResult<MatWarehouseVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String warehouseName,
            @RequestParam(required = false) String status) {
        PageResult<MatWarehouseVO> page = matWarehouseService.getPage(pageNo, pageSize, projectId, warehouseCode, warehouseName, status);
        return ApiResponse.success(page);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('inventory:warehouse:*')")
    public ApiResponse<MatWarehouseVO> getById(@PathVariable Long id) {
        return ApiResponse.success(matWarehouseService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('inventory:warehouse:*')")
    public ApiResponse<Long> create(@Valid @RequestBody MatWarehouse warehouse) {
        return ApiResponse.success(matWarehouseService.create(warehouse));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('inventory:warehouse:*')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody MatWarehouse warehouse) {
        warehouse.setId(id);
        matWarehouseService.update(warehouse);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('inventory:warehouse:*')")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        matWarehouseService.updateStatus(id, status);
        return ApiResponse.success();
    }
}
