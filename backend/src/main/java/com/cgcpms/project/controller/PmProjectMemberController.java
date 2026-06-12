package com.cgcpms.project.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.service.PmProjectMemberService;
import com.cgcpms.project.vo.PmProjectMemberVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/projects/{projectId}/members")
@RequiredArgsConstructor
public class PmProjectMemberController {

    private final PmProjectMemberService memberService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('project:member:list')")
    public ApiResponse<PageResult<PmProjectMemberVO>> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String status) {
        IPage<PmProjectMemberVO> page = memberService.getPage(projectId, pageNo, pageSize, roleCode, status);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('project:member:list')")
    public ApiResponse<PmProjectMemberVO> getById(@PathVariable Long projectId, @PathVariable Long id) {
        return ApiResponse.success(memberService.getById(projectId, id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('project:member:add')")
    public ApiResponse<Long> create(@PathVariable Long projectId, @Valid @RequestBody PmProjectMember member) {
        return ApiResponse.success(memberService.create(projectId, member));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('project:member:edit')")
    public ApiResponse<Void> update(@PathVariable Long projectId, @PathVariable Long id,
                                    @Valid @RequestBody PmProjectMember member) {
        memberService.update(projectId, id, member);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('project:member:delete')")
    public ApiResponse<Void> delete(@PathVariable Long projectId, @PathVariable Long id) {
        memberService.delete(projectId, id);
        return ApiResponse.success();
    }
}
