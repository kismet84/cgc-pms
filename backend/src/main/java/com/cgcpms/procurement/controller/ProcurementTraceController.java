package com.cgcpms.procurement.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.procurement.service.ProcurementTraceService;
import com.cgcpms.procurement.vo.ProcurementTraceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/procurement-traces")
@RequiredArgsConstructor
public class ProcurementTraceController {
    private final ProcurementTraceService traceService;

    @GetMapping("/stock-transactions/{id}")
    @PreAuthorize("hasAuthority('procurement:trace:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<ProcurementTraceVO> byStockTransaction(@PathVariable Long id) {
        return ApiResponse.success(traceService.byStockTransaction(id));
    }

    @GetMapping("/receipts/{id}")
    @PreAuthorize("hasAuthority('procurement:trace:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<ProcurementTraceVO> byReceipt(@PathVariable Long id) {
        return ApiResponse.success(traceService.byReceipt(id));
    }

    @GetMapping("/requisitions/{id}")
    @PreAuthorize("hasAuthority('procurement:trace:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<ProcurementTraceVO> byRequisition(@PathVariable Long id) {
        return ApiResponse.success(traceService.byRequisition(id));
    }

    @GetMapping("/costs/{id}")
    @PreAuthorize("hasAuthority('procurement:trace:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<ProcurementTraceVO> byCost(@PathVariable Long id) {
        return ApiResponse.success(traceService.byCost(id));
    }

    @GetMapping("/material-returns/{id}")
    @PreAuthorize("hasAuthority('procurement:trace:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<ProcurementTraceVO> byMaterialReturn(@PathVariable Long id) {
        return ApiResponse.success(traceService.byMaterialReturn(id));
    }

    @GetMapping("/supplier-returns/{id}")
    @PreAuthorize("hasAuthority('procurement:trace:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<ProcurementTraceVO> bySupplierReturn(@PathVariable Long id) {
        return ApiResponse.success(traceService.bySupplierReturn(id));
    }
}
