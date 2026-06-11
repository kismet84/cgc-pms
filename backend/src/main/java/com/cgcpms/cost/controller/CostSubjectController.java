package com.cgcpms.cost.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.service.CostSubjectService;
import com.cgcpms.cost.vo.CostSubjectTreeNodeVO;
import com.cgcpms.cost.vo.CostSubjectVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cost-subjects")
@RequiredArgsConstructor
public class CostSubjectController {

    private final CostSubjectService costSubjectService;

    @GetMapping("/tree")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('cost:query')")
    public ApiResponse<List<CostSubjectTreeNodeVO>> getTree() {
        return ApiResponse.success(costSubjectService.getTree());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('cost:query')")
    public ApiResponse<List<CostSubjectVO>> getList() {
        return ApiResponse.success(costSubjectService.getList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('cost:query')")
    public ApiResponse<CostSubjectVO> getById(@PathVariable Long id) {
        return ApiResponse.success(costSubjectService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('cost:add')")
    public ApiResponse<Long> create(@Valid @RequestBody CostSubject subject) {
        return ApiResponse.success(costSubjectService.create(subject));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('cost:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody CostSubject subject) {
        subject.setId(id);
        costSubjectService.update(subject);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('cost:edit')")
    public ApiResponse<Void> toggleStatus(@PathVariable Long id) {
        costSubjectService.toggleStatus(id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('cost:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        costSubjectService.delete(id);
        return ApiResponse.success();
    }
}
