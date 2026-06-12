package com.cgcpms.system.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.service.SysRoleService;
import com.cgcpms.system.vo.SysRoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:role:query')")
    public ApiResponse<List<SysRoleVO>> list() {
        return ApiResponse.success(sysRoleService.getList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:role:query')")
    public ApiResponse<SysRoleVO> getById(@PathVariable Long id) {
        return ApiResponse.success(sysRoleService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:role:add')")
    public ApiResponse<Long> create(@Valid @RequestBody SysRole role) {
        return ApiResponse.success(sysRoleService.create(role));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:role:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody SysRole role) {
        role.setId(id);
        sysRoleService.update(role);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:role:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysRoleService.delete(id);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:role:assign')")
    public ApiResponse<Void> assignMenus(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        sysRoleService.assignMenus(id, body.get("menuIds"));
        return ApiResponse.success();
    }
}
