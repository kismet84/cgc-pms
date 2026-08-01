package com.cgcpms.system.dict.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.system.dict.entity.SysDictType;
import com.cgcpms.system.dict.dto.SysDictTypeRequest;
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
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:dict:list')")
    public ApiResponse<PageResult<SysDictTypeVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String dictCode,
            @RequestParam(required = false) String dictName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dictClass) {
        IPage<SysDictTypeVO> page = sysDictTypeService.getPage(
                pageNo, pageSize, groupId, dictCode, dictName, status, dictClass);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:dict:list')")
    public ApiResponse<SysDictTypeVO> getById(@PathVariable Long id) {
        return ApiResponse.success(sysDictTypeService.getById(id));
    }

    @PostMapping
    @AuditedOperation(type = "CREATE", businessType = "DICT_TYPE")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Long> create(@Valid @RequestBody SysDictTypeRequest request) {
        return ApiResponse.success(sysDictTypeService.create(toEntity(request)));
    }

    @PutMapping("/{id}")
    @AuditedOperation(type = "UPDATE", businessType = "DICT_TYPE", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody SysDictTypeRequest request) {
        SysDictType entity = toEntity(request);
        entity.setId(id);
        sysDictTypeService.update(entity);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @AuditedOperation(type = "DELETE", businessType = "DICT_TYPE", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysDictTypeService.delete(id);
        return ApiResponse.success();
    }

    private SysDictType toEntity(SysDictTypeRequest request) {
        SysDictType type = new SysDictType();
        type.setGroupId(request.getGroupId());
        type.setDictCode(request.getDictCode());
        type.setDictName(request.getDictName());
        type.setDictClass(request.getDictClass());
        type.setStatus(request.getStatus());
        return type;
    }
}
