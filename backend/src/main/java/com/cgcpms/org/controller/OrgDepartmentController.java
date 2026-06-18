package com.cgcpms.org.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.org.entity.OrgDepartment;
import com.cgcpms.org.service.OrgDepartmentService;
import com.cgcpms.org.vo.OrgDepartmentTreeNodeVO;
import com.cgcpms.org.vo.OrgDepartmentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/org/departments")
@RequiredArgsConstructor
public class OrgDepartmentController {

    private final OrgDepartmentService orgDepartmentService;

    @GetMapping("/tree")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('org:list')")
    public ApiResponse<List<OrgDepartmentTreeNodeVO>> getTree(@RequestParam(required = false) Long companyId) {
        return ApiResponse.success(orgDepartmentService.getTree(companyId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('org:list')")
    public ApiResponse<PageResult<OrgDepartmentVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String deptCode,
            @RequestParam(required = false) String deptName,
            @RequestParam(required = false) String status) {
        IPage<OrgDepartmentVO> page = orgDepartmentService.getPage(pageNo, pageSize, companyId, deptCode, deptName, status);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('org:query')")
    public ApiResponse<OrgDepartmentVO> getById(@PathVariable Long id) {
        return ApiResponse.success(orgDepartmentService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('org:add')")
    public ApiResponse<Long> create(@Valid @RequestBody OrgDepartment dept) {
        return ApiResponse.success(orgDepartmentService.create(dept));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('org:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody OrgDepartment dept) {
        dept.setId(id);
        orgDepartmentService.update(dept);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('org:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        orgDepartmentService.delete(id);
        return ApiResponse.success();
    }
}
