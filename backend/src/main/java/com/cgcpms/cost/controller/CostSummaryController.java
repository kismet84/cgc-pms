package com.cgcpms.cost.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.cost.vo.CostProjectSummaryVO;
import com.cgcpms.cost.vo.CostSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cost-summary")
@RequiredArgsConstructor
public class CostSummaryController {

    private final CostSummaryService costSummaryService;

    @GetMapping("/{projectId}")
    @PreAuthorize("hasAuthority('cost:summary:view') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<CostProjectSummaryVO> getLatest(@PathVariable Long projectId) {
        return ApiResponse.success(costSummaryService.getProjectSummary(projectId));
    }

    @PostMapping("/{projectId}/refresh")
    @PreAuthorize("hasAuthority('cost:summary:refresh') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    @AuditedOperation(type = "REFRESH", businessType = "COST_SUMMARY", businessIdExpression = "#projectId")
    public ApiResponse<CostProjectSummaryVO> refresh(@PathVariable Long projectId) {
        return ApiResponse.success(costSummaryService.refreshSummary(projectId));
    }

    @GetMapping("/{projectId}/history")
    @PreAuthorize("hasAuthority('cost:summary:view') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<CostSummaryVO>> getHistory(@PathVariable Long projectId) {
        return ApiResponse.success(costSummaryService.getSummaryHistory(projectId));
    }
}
