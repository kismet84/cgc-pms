package com.cgcpms.inventory.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.inventory.vo.MatStockLedgerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/inventory/stock")
@RequiredArgsConstructor
public class MatStockController {

    private final MatStockService matStockService;

    @PostMapping("/in")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('inventory:transaction:add')")
    public ApiResponse<MatStock> stockIn(@RequestParam Long warehouseId,
                                          @RequestParam Long materialId,
                                          @RequestParam BigDecimal quantity) {
        return ApiResponse.success(matStockService.stockIn(warehouseId, materialId, quantity));
    }

    @PostMapping("/out")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('inventory:transaction:add')")
    public ApiResponse<MatStock> stockOut(@RequestParam Long warehouseId,
                                           @RequestParam Long materialId,
                                           @RequestParam BigDecimal quantity) {
        return ApiResponse.success(matStockService.stockOut(warehouseId, materialId, quantity));
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('inventory:stock:list')")
    public ApiResponse<MatStockLedgerVO> getLedger(@RequestParam Long warehouseId,
                                                    @RequestParam Long materialId,
                                                    @RequestParam(defaultValue = "1") long pageNo,
                                                    @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(matStockService.getLedger(warehouseId, materialId, pageNo, pageSize));
    }
}
