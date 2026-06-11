package com.cgcpms.cost.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.cost.service.CostSummaryService;
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
    @PreAuthorize("hasAuthority('cost:summary:view') or hasRole('ADMIN')")
    public ApiResponse<List<CostSummaryVO>> getLatest(@PathVariable Long projectId) {
        return ApiResponse.success(costSummaryService.getSummary(projectId));
    }

    @PostMapping("/{projectId}/refresh")
    @PreAuthorize("hasAuthority('cost:summary:view') or hasRole('ADMIN')")
    public ApiResponse<Void> refresh(@PathVariable Long projectId) {
        costSummaryService.refreshSummary(projectId);
        return ApiResponse.success();
    }

    @GetMapping("/{projectId}/history")
    @PreAuthorize("hasAuthority('cost:summary:view') or hasRole('ADMIN')")
    public ApiResponse<List<CostSummaryVO>> getHistory(@PathVariable Long projectId) {
        return ApiResponse.success(costSummaryService.getSummaryHistory(projectId));
    }
}
