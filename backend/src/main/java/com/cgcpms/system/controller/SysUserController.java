package com.cgcpms.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.system.dto.UpdateUserStatusRequest;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.service.SysUserService;
import com.cgcpms.system.vo.SysUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/system/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:user:query')")
    public ApiResponse<PageResult<SysUserVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String status) {
        IPage<SysUserVO> page = sysUserService.getPage(pageNo, pageSize, username, realName, status);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:user:query')")
    public ApiResponse<SysUserVO> getById(@PathVariable Long id) {
        return ApiResponse.success(sysUserService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:user:add')")
    public ApiResponse<Long> create(@Valid @RequestBody SysUser user) {
        return ApiResponse.success(sysUserService.create(user));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:user:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody SysUser user) {
        user.setId(id);
        sysUserService.update(user);
        return ApiResponse.success();
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:user:edit')")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest request) {
        sysUserService.updateStatus(id, request.getStatus());
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:user:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:user:assign')")
    public ApiResponse<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody com.cgcpms.system.dto.AssignRolesRequest request) {
        sysUserService.assignRoles(id, request.getRoleIds());
        return ApiResponse.success();
    }
}
