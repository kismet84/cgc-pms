package com.cgcpms.report.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.report.dto.ReportCatalogItem;
import com.cgcpms.report.service.ReportCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportCatalogController {

    private final ReportCatalogService reportCatalogService;

    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<ReportCatalogItem>> catalog() {
        return ApiResponse.success(reportCatalogService.listVisibleCatalog());
    }
}
