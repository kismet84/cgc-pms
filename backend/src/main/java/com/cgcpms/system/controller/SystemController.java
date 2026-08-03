package com.cgcpms.system.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.system.dto.DataMaintenancePreview;
import com.cgcpms.system.service.DataMaintenancePreviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only system maintenance endpoints. */
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {

    private final DataMaintenancePreviewService previewService;

    @GetMapping("/data-maintenance/preview")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<DataMaintenancePreview> previewDataMaintenance() {
        return ApiResponse.success(previewService.preview());
    }
}
