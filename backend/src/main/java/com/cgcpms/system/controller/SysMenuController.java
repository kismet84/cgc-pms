package com.cgcpms.system.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.system.entity.SysMenu;
import com.cgcpms.system.service.SysMenuService;
import com.cgcpms.system.vo.MenuTreeVO;
import com.cgcpms.system.vo.SysMenuVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

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
    public ApiResponse<List<SysMenuVO>> flatList() {
        return ApiResponse.success(sysMenuService.getFlatList().stream()
                .map(this::toVO)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:menu:query')")
    public ApiResponse<SysMenuVO> getById(@PathVariable Long id) {
        return ApiResponse.success(toVO(sysMenuService.getById(id)));
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

    private SysMenuVO toVO(SysMenu menu) {
        SysMenuVO vo = new SysMenuVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setMenuName(menu.getMenuName());
        vo.setMenuType(menu.getMenuType());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setPerms(menu.getPerms());
        vo.setIcon(menu.getIcon());
        vo.setOrderNum(menu.getOrderNum());
        vo.setStatus(menu.getStatus());
        vo.setVisible(menu.getVisible());
        return vo;
    }
}
