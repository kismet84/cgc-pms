package com.cgcpms.project.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.project.service.PmProjectService;
import com.cgcpms.project.vo.ProjectContextOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/project-context")
@RequiredArgsConstructor
public class ProjectContextController {

    private final PmProjectService projectService;

    @GetMapping("/options")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<ProjectContextOptionVO>> options() {
        return ApiResponse.success(projectService.getContextOptions());
    }
}
