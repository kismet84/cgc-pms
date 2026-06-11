package com.cgcpms.cost.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.cost.service.CostLedgerService;
import com.cgcpms.cost.vo.CostLedgerSummaryVO;
import com.cgcpms.cost.vo.CostLedgerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/cost-ledger")
@RequiredArgsConstructor
public class CostLedgerController {

    private final CostLedgerService costLedgerService;

    @GetMapping
    @PreAuthorize("hasAuthority('cost:ledger:query') or hasRole('ADMIN')")
    public ApiResponse<PageResult<CostLedgerVO>> getPage(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) Long costSubjectId,
            @RequestParam(required = false) String costType,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String costStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword) {
        IPage<CostLedgerVO> page = costLedgerService.getPage(pageNum, pageSize,
                projectId, contractId, partnerId, costSubjectId,
                costType, sourceType, costStatus,
                startDate, endDate, keyword);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('cost:ledger:query') or hasRole('ADMIN')")
    public ApiResponse<CostLedgerSummaryVO> getSummary(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) Long costSubjectId,
            @RequestParam(required = false) String costType,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String costStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword) {
        CostLedgerSummaryVO summary = costLedgerService.getSummary(
                projectId, contractId, partnerId, costSubjectId,
                costType, sourceType, costStatus,
                startDate, endDate, keyword);
        return ApiResponse.success(summary);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('cost:ledger:query') or hasRole('ADMIN')")
    public ApiResponse<CostLedgerVO> getById(@PathVariable Long id) {
        return ApiResponse.success(costLedgerService.getById(id));
    }
}
