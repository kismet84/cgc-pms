package com.cgcpms.contract.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.service.CtContractChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contract-changes")
@RequiredArgsConstructor
public class CtContractChangeController {

    private final CtContractChangeService ctContractChangeService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('contract:change:query')")
    public ApiResponse<PageResult<CtContractChange>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String changeCode) {
        IPage<CtContractChange> page = ctContractChangeService.getPage(
                pageNo, pageSize, projectId, contractId, changeType, approvalStatus, changeCode);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('contract:change:query')")
    public ApiResponse<CtContractChange> getById(@PathVariable Long id) {
        return ApiResponse.success(ctContractChangeService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('contract:change:add')")
    public ApiResponse<Long> create(@Valid @RequestBody CtContractChange change) {
        return ApiResponse.success(ctContractChangeService.create(change));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('contract:change:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody CtContractChange change) {
        change.setId(id);
        ctContractChangeService.update(change);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('contract:change:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ctContractChangeService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('contract:change:submit') or hasRole('ADMIN')")
    public ApiResponse<Void> submitForApproval(@PathVariable Long id) {
        ctContractChangeService.submitForApproval(id);
        return ApiResponse.success();
    }
}
