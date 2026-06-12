package com.cgcpms.system.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.system.entity.SysMenu;
import com.cgcpms.system.service.SysMenuService;
import com.cgcpms.system.vo.MenuTreeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/menus")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService sysMenuService;

    @GetMapping("/tree")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:menu:query')")
    public ApiResponse<List<MenuTreeVO>> tree() {
        return ApiResponse.success(sysMenuService.getTree());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:menu:query')")
    public ApiResponse<List<SysMenu>> flatList() {
        return ApiResponse.success(sysMenuService.getFlatList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:menu:query')")
    public ApiResponse<SysMenu> getById(@PathVariable Long id) {
        return ApiResponse.success(sysMenuService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:menu:add')")
    public ApiResponse<Long> create(@Valid @RequestBody SysMenu menu) {
        return ApiResponse.success(sysMenuService.create(menu));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:menu:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody SysMenu menu) {
        menu.setId(id);
        sysMenuService.update(menu);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:menu:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysMenuService.delete(id);
        return ApiResponse.success();
    }
}
