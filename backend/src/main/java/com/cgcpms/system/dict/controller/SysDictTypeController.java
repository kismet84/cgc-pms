package com.cgcpms.system.dict.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.system.dict.entity.SysDictType;
import com.cgcpms.system.dict.service.SysDictTypeService;
import com.cgcpms.system.dict.vo.SysDictTypeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/dict/types")
@RequiredArgsConstructor
public class SysDictTypeController {

    private final SysDictTypeService sysDictTypeService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:dict:list')")
    public ApiResponse<PageResult<SysDictTypeVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String dictCode,
            @RequestParam(required = false) String dictName,
            @RequestParam(required = false) String status) {
        IPage<SysDictTypeVO> page = sysDictTypeService.getPage(pageNo, pageSize, dictCode, dictName, status);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:dict:list')")
    public ApiResponse<SysDictTypeVO> getById(@PathVariable Long id) {
        return ApiResponse.success(sysDictTypeService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:dict:add')")
    public ApiResponse<Long> create(@Valid @RequestBody SysDictType entity) {
        return ApiResponse.success(sysDictTypeService.create(entity));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:dict:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody SysDictType entity) {
        entity.setId(id);
        sysDictTypeService.update(entity);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:dict:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysDictTypeService.delete(id);
        return ApiResponse.success();
    }
}
