package com.cgcpms.subcontract.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.subcontract.entity.SubTask;
import com.cgcpms.subcontract.service.SubTaskService;
import com.cgcpms.subcontract.vo.SubTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sub-tasks")
@RequiredArgsConstructor
public class SubTaskController {

    private final SubTaskService subTaskService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('subtask:query')")
    public ApiResponse<PageResult<SubTaskVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskCode,
            @RequestParam(required = false) String taskName) {
        IPage<SubTaskVO> page = subTaskService.getPage(pageNo, pageSize, projectId, contractId,
                partnerId, status, taskCode, taskName);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('subtask:query')")
    public ApiResponse<SubTaskVO> getById(@PathVariable Long id) {
        return ApiResponse.success(subTaskService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('subtask:add')")
    public ApiResponse<Long> create(@Valid @RequestBody SubTask task) {
        return ApiResponse.success(subTaskService.create(task));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('subtask:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody SubTask task) {
        task.setId(id);
        subTaskService.update(task);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('subtask:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        subTaskService.delete(id);
        return ApiResponse.success();
    }
}
