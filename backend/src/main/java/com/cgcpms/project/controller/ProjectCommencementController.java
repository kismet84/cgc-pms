package com.cgcpms.project.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.project.dto.ProjectCommencementSaveRequest;
import com.cgcpms.project.entity.ProjectCommencement;
import com.cgcpms.project.service.ProjectCommencementService;
import com.cgcpms.project.service.ProjectLifecycleService;
import com.cgcpms.project.vo.ProjectActivationReadinessVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/projects/{projectId}")
@RequiredArgsConstructor
public class ProjectCommencementController {
    private final ProjectCommencementService commencementService;
    private final ProjectLifecycleService lifecycleService;

    @GetMapping("/activation-readiness")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('project:commencement:query')")
    public ApiResponse<ProjectActivationReadinessVO> readiness(@PathVariable Long projectId) {
        return ApiResponse.success(lifecycleService.getActivationReadiness(projectId));
    }

    @GetMapping("/commencement")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('project:commencement:query')")
    public ApiResponse<ProjectCommencement> get(@PathVariable Long projectId) {
        return ApiResponse.success(commencementService.get(projectId));
    }

    @PostMapping("/commencement")
    @AuditedOperation(type = "SAVE", businessType = "PROJECT_COMMENCEMENT", businessIdExpression = "#projectId")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('project:commencement:add','project:commencement:edit')")
    public ApiResponse<ProjectCommencement> save(@PathVariable Long projectId,
                                                  @Valid @RequestBody ProjectCommencementSaveRequest request) {
        return ApiResponse.success(commencementService.save(projectId, request));
    }

    @PostMapping("/commencement/submit")
    @AuditedOperation(type = "SUBMIT", businessType = "PROJECT_COMMENCEMENT", businessIdExpression = "#projectId")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('project:commencement:submit')")
    public ApiResponse<ProjectCommencement> submit(@PathVariable Long projectId, @RequestParam Integer version) {
        return ApiResponse.success(commencementService.submit(projectId, version));
    }
}
