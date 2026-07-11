package com.cgcpms.inventory.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.inventory.dto.StockTransactionDTO;
import com.cgcpms.inventory.dto.SafetyStockThresholdDTO;
import com.cgcpms.inventory.dto.ReplenishmentSettingsDTO;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.inventory.vo.MatStockLedgerVO;
import com.cgcpms.inventory.vo.MatStockVO;
import com.cgcpms.inventory.vo.StockKpiVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory/stock")
@RequiredArgsConstructor
public class MatStockController {

    private final MatStockService matStockService;

    @PostMapping("/in")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('inventory:transaction:add')")
    public ApiResponse<MatStockVO> stockIn(@Valid @RequestBody StockTransactionDTO dto) {
        return ApiResponse.success(matStockService.toStockVO(matStockService.stockIn(
                dto.getWarehouseId(), dto.getMaterialId(), dto.getQuantity(),
                dto.getSourceType(), dto.getSourceId())));
    }

    @PostMapping("/out")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('inventory:transaction:add')")
    public ApiResponse<MatStockVO> stockOut(@Valid @RequestBody StockTransactionDTO dto) {
        return ApiResponse.success(matStockService.toStockVO(matStockService.stockOut(
                dto.getWarehouseId(), dto.getMaterialId(), dto.getQuantity(),
                dto.getSourceType(), dto.getSourceId())));
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('inventory:stock:list')")
    public ApiResponse<MatStockLedgerVO> getLedger(@RequestParam Long warehouseId,
                                                    @RequestParam Long materialId,
                                                    @RequestParam(required = false) Long projectId,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String sortField,
                                                    @RequestParam(required = false) String sortOrder,
                                                    @RequestParam(defaultValue = "1") long pageNo,
                                                    @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(matStockService.getLedger(
                warehouseId, materialId, projectId, keyword,
                sortField, sortOrder, pageNo, pageSize));
    }

    @GetMapping("/kpi")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('inventory:stock:list')")
    public ApiResponse<StockKpiVO> getKpi(@RequestParam(required = false) Long warehouseId,
                                           @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(matStockService.getKpi(warehouseId, projectId));
    }

    @PutMapping("/{id}/safety-threshold")
    @AuditedOperation(type = "UPDATE", businessType = "STOCK_SAFETY_THRESHOLD", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('inventory:stock:edit')")
    public ApiResponse<MatStockVO> updateSafetyThreshold(@PathVariable Long id,
                                                         @Valid @RequestBody SafetyStockThresholdDTO dto) {
        return ApiResponse.success(matStockService.toStockVO(
                matStockService.updateSafetyStockThreshold(id, dto.getSafetyStockQty())));
    }

    @PutMapping("/{id}/replenishment-settings")
    @AuditedOperation(type = "UPDATE", businessType = "STOCK_REPLENISHMENT_SETTINGS", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('inventory:stock:edit')")
    public ApiResponse<MatStockVO> updateReplenishmentSettings(
            @PathVariable Long id,
            @Valid @RequestBody ReplenishmentSettingsDTO dto) {
        return ApiResponse.success(matStockService.toStockVO(
                matStockService.updateReplenishmentSettings(
                        id, dto.getSafetyStockQty(), dto.getReplenishmentTargetQty())));
    }
}
