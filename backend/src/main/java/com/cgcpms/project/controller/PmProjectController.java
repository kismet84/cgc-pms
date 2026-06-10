package com.cgcpms.project.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.service.PmProjectService;
import com.cgcpms.project.vo.PmProjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class PmProjectController {

    private final PmProjectService pmProjectService;

    @GetMapping
    public ApiResponse<PageResult<PmProjectVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String projectType,
            @RequestParam(required = false) String status) {
        IPage<PmProjectVO> page = pmProjectService.getPage(pageNo, pageSize, projectCode, projectName, projectType, status);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    public ApiResponse<PmProjectVO> getById(@PathVariable Long id) {
        return ApiResponse.success(pmProjectService.getById(id));
    }

    @PostMapping
    public ApiResponse<Long> create(@RequestBody PmProject project) {
        return ApiResponse.success(pmProjectService.create(project));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody PmProject project) {
        project.setId(id);
        pmProjectService.update(project);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        pmProjectService.delete(id);
        return ApiResponse.success();
    }
}
