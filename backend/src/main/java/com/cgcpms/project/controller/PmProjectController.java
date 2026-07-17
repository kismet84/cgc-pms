package com.cgcpms.project.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.dto.ProjectStatusTransitionRequest;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.project.service.PmProjectService;
import com.cgcpms.project.service.ProjectOverviewService;
import com.cgcpms.project.vo.PmProjectVO;
import com.cgcpms.project.vo.ProjectOverviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class PmProjectController {

    private final PmProjectService pmProjectService;
    private final ProjectOverviewService projectOverviewService;

    @GetMapping("/{projectId}/overview")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('project:query')")
    public ApiResponse<ProjectOverviewVO> overview(@PathVariable Long projectId) {
        return ApiResponse.success(projectOverviewService.getOverview(projectId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('project:query')")
    public ApiResponse<PageResult<PmProjectVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String projectType,
            @RequestParam(required = false) String status) {
        IPage<PmProjectVO> page = pmProjectService.getPage(pageNo, pageSize, keyword, projectCode, projectName, projectType, status);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('project:query')")
    public ApiResponse<PmProjectVO> getById(@PathVariable Long id) {
        return ApiResponse.success(pmProjectService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('project:add')")
    public ApiResponse<Long> create(@Valid @RequestBody PmProject project) {
        return ApiResponse.success(pmProjectService.create(project));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('project:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody PmProject project) {
        project.setId(id);
        pmProjectService.update(project);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    @AuditedOperation(type = "SUBMIT", businessType = "PROJECT", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('project:submit')")
    public ApiResponse<Long> submitApproval(@PathVariable Long id) {
        return ApiResponse.success(pmProjectService.submitApproval(id));
    }

    @PutMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('project:edit')")
    public ApiResponse<Void> archive(@PathVariable Long id) {
        pmProjectService.archive(id);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/status")
    @AuditedOperation(type = "STATUS_CHANGE", businessType = "PROJECT", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('project:status')")
    public ApiResponse<Void> transitionStatus(@PathVariable Long id,
                                              @Valid @RequestBody ProjectStatusTransitionRequest request) {
        pmProjectService.transitionStatus(id, request.getTargetStatus(), request.getReason());
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        pmProjectService.delete(id);
        return ApiResponse.success();
    }
}
