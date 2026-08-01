package com.cgcpms.system.dict.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.system.dict.entity.SysDictData;
import com.cgcpms.system.dict.dto.SysDictDataRequest;
import com.cgcpms.system.dict.service.SysDictDataService;
import com.cgcpms.system.dict.vo.SysDictDataVO;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/dict/data")
@RequiredArgsConstructor
public class SysDictDataController {

    private final SysDictDataService sysDictDataService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:dict:list')")
    public ApiResponse<PageResult<SysDictDataVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) String dictLabel,
            @RequestParam(required = false) String status) {
        IPage<SysDictDataVO> page = sysDictDataService.getPage(pageNo, pageSize, typeId, dictLabel, status);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:dict:list')")
    public ApiResponse<SysDictDataVO> getById(@PathVariable Long id) {
        return ApiResponse.success(sysDictDataService.getById(id));
    }

    @PostMapping
    @AuditedOperation(type = "CREATE", businessType = "DICT_DATA")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Long> create(@Valid @RequestBody SysDictDataRequest request) {
        return ApiResponse.success(sysDictDataService.create(toEntity(request)));
    }

    @PutMapping("/{id}")
    @AuditedOperation(type = "UPDATE", businessType = "DICT_DATA", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody SysDictDataRequest request) {
        SysDictData entity = toEntity(request);
        entity.setId(id);
        sysDictDataService.update(entity);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @AuditedOperation(type = "DELETE", businessType = "DICT_DATA", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysDictDataService.delete(id);
        return ApiResponse.success();
    }
    @GetMapping("/by-code/{dictCode}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<SysDictDataVO>> getByDictCode(@PathVariable String dictCode) {
        return ApiResponse.success(sysDictDataService.getByDictCode(dictCode));
    }

    private SysDictData toEntity(SysDictDataRequest request) {
        SysDictData data = new SysDictData();
        data.setDictTypeId(request.getDictTypeId());
        data.setDictLabel(request.getDictLabel());
        data.setDictValue(request.getDictValue());
        data.setCssClass(request.getCssClass());
        data.setListClass(request.getListClass());
        data.setOrderNum(request.getOrderNum());
        data.setStatus(request.getStatus());
        return data;
    }
}
