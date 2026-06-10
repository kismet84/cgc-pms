package com.cgcpms.system.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.system.entity.SysMenu;
import com.cgcpms.system.service.SysMenuService;
import com.cgcpms.system.vo.MenuTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/menus")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService sysMenuService;

    @GetMapping("/tree")
    public ApiResponse<List<MenuTreeVO>> tree() {
        return ApiResponse.success(sysMenuService.getTree());
    }

    @GetMapping
    public ApiResponse<List<SysMenu>> flatList() {
        return ApiResponse.success(sysMenuService.getFlatList());
    }

    @GetMapping("/{id}")
    public ApiResponse<SysMenu> getById(@PathVariable Long id) {
        return ApiResponse.success(sysMenuService.getById(id));
    }

    @PostMapping
    public ApiResponse<Long> create(@RequestBody SysMenu menu) {
        return ApiResponse.success(sysMenuService.create(menu));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody SysMenu menu) {
        menu.setId(id);
        sysMenuService.update(menu);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysMenuService.delete(id);
        return ApiResponse.success();
    }
}
