package com.cgcpms.system.dict.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.system.dict.dto.SysDictGroupRequest;
import com.cgcpms.system.dict.entity.SysDictGroup;
import com.cgcpms.system.dict.service.SysDictGroupService;
import com.cgcpms.system.dict.vo.SysDictGroupVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system/dict/groups")
@RequiredArgsConstructor
public class SysDictGroupController {

    private final SysDictGroupService groupService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:dict:list')")
    public ApiResponse<PageResult<SysDictGroupVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        IPage<SysDictGroupVO> page = groupService.getPage(pageNo, pageSize, keyword, status);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('system:dict:list')")
    public ApiResponse<SysDictGroupVO> getById(@PathVariable Long id) {
        return ApiResponse.success(groupService.getById(id));
    }

    @PostMapping
    @AuditedOperation(type = "CREATE", businessType = "DICT_GROUP")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Long> create(@Valid @RequestBody SysDictGroupRequest request) {
        return ApiResponse.success(groupService.create(toEntity(request)));
    }

    @PutMapping("/{id}")
    @AuditedOperation(type = "UPDATE", businessType = "DICT_GROUP", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody SysDictGroupRequest request) {
        SysDictGroup group = toEntity(request);
        group.setId(id);
        groupService.update(group);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @AuditedOperation(type = "DELETE", businessType = "DICT_GROUP", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        groupService.delete(id);
        return ApiResponse.success();
    }

    private SysDictGroup toEntity(SysDictGroupRequest request) {
        SysDictGroup group = new SysDictGroup();
        group.setGroupCode(request.getGroupCode());
        group.setGroupName(request.getGroupName());
        group.setOrderNum(request.getOrderNum());
        group.setStatus(request.getStatus());
        return group;
    }
}
