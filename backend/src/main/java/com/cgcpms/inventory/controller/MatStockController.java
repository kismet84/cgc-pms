package com.cgcpms.inventory.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.inventory.dto.StockTransactionDTO;
import com.cgcpms.inventory.dto.StockTransferDTO;
import com.cgcpms.inventory.dto.SafetyStockThresholdDTO;
import com.cgcpms.inventory.dto.ReplenishmentSettingsDTO;
import com.cgcpms.inventory.service.MatStockService;
import com.cgcpms.inventory.vo.MatStockLedgerVO;
import com.cgcpms.inventory.vo.MatStockVO;
import com.cgcpms.inventory.vo.StockKpiVO;
import com.cgcpms.inventory.vo.StockConsumptionBaselineVO;
import com.cgcpms.inventory.vo.StockIncomingSupplyVO;
import com.cgcpms.inventory.vo.StockTransferCandidateVO;
import com.cgcpms.inventory.vo.StockTransferVO;
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
        throw new BusinessException("MANUAL_STOCK_MOVEMENT_DISABLED", "手工入库已停用，请从验收或库存调整审批单过账");
    }

    @PostMapping("/out")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('inventory:transaction:add')")
    public ApiResponse<MatStockVO> stockOut(@Valid @RequestBody StockTransactionDTO dto) {
        throw new BusinessException("MANUAL_STOCK_MOVEMENT_DISABLED", "手工出库已停用，请从领料实发或库存调整审批单过账");
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

    @GetMapping("/{id}/transfer-candidates")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('inventory:stock:list')")
    public ApiResponse<java.util.List<StockTransferCandidateVO>> getTransferCandidates(
            @PathVariable Long id) {
        return ApiResponse.success(matStockService.getTransferCandidates(id));
    }

    @GetMapping("/{id}/consumption-baseline")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('inventory:stock:list')")
    public ApiResponse<StockConsumptionBaselineVO> getConsumptionBaseline(@PathVariable Long id) {
        return ApiResponse.success(matStockService.getConsumptionBaseline(id));
    }

    @PostMapping("/transfers")
    @AuditedOperation(type = "CREATE", businessType = "STOCK_TRANSFER", businessIdExpression = "#dto.idempotencyKey")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or "
            + "(hasAuthority('inventory:stock:edit') and hasAuthority('inventory:transaction:add'))")
    public ApiResponse<StockTransferVO> transfer(@Valid @RequestBody StockTransferDTO dto) {
        return ApiResponse.success(matStockService.transfer(dto));
    }

    @GetMapping("/{id}/incoming-supplies")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('inventory:stock:list')")
    public ApiResponse<java.util.List<StockIncomingSupplyVO>> getIncomingSupplies(
            @PathVariable Long id) {
        return ApiResponse.success(matStockService.getIncomingSupplies(id));
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
                        id, dto.getSafetyStockQty(), dto.getReplenishmentTargetQty(),
                        dto.getReplenishmentLeadDays() == null
                                ? null : dto.getReplenishmentLeadDays().intValueExact(),
                        dto.isReplenishmentLeadDaysSpecified())));
    }
}
