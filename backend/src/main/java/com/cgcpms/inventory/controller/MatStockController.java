package com.cgcpms.inventory.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.inventory.dto.StockTransactionDTO;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.inventory.vo.MatStockLedgerVO;
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
    public ApiResponse<MatStock> stockIn(@Valid @RequestBody StockTransactionDTO dto) {
        return ApiResponse.success(matStockService.stockIn(dto.getWarehouseId(), dto.getMaterialId(), dto.getQuantity()));
    }

    @PostMapping("/out")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('inventory:transaction:add')")
    public ApiResponse<MatStock> stockOut(@Valid @RequestBody StockTransactionDTO dto) {
        return ApiResponse.success(matStockService.stockOut(dto.getWarehouseId(), dto.getMaterialId(), dto.getQuantity()));
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('inventory:stock:list')")
    public ApiResponse<MatStockLedgerVO> getLedger(@RequestParam Long warehouseId,
                                                    @RequestParam Long materialId,
                                                    @RequestParam(defaultValue = "1") long pageNo,
                                                    @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(matStockService.getLedger(warehouseId, materialId, pageNo, pageSize));
    }
}
