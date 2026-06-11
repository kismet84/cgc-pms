package com.cgcpms.contract.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.service.CtContractItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/contracts/{contractId}/items")
@RequiredArgsConstructor
public class CtContractItemController {

    private final CtContractItemService ctContractItemService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('contract:item:query')")
    public ApiResponse<List<CtContractItem>> getByContractId(@PathVariable Long contractId) {
        return ApiResponse.success(ctContractItemService.getByContractId(contractId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('contract:item:add')")
    public ApiResponse<Long> create(@PathVariable Long contractId, @Valid @RequestBody CtContractItem item) {
        item.setContractId(contractId);
        return ApiResponse.success(ctContractItemService.create(item));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('contract:item:add')")
    public ApiResponse<Void> batchSave(@PathVariable Long contractId,
                                       @RequestBody @Valid List<@Valid CtContractItem> items) {
        ctContractItemService.batchSave(contractId, items);
        return ApiResponse.success();
    }

    @PutMapping("/{itemId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('contract:item:edit')")
    public ApiResponse<Void> update(@PathVariable Long contractId, @PathVariable Long itemId,
                                    @Valid @RequestBody CtContractItem item) {
        item.setId(itemId);
        item.setContractId(contractId);
        ctContractItemService.update(item);
        return ApiResponse.success();
    }

    @DeleteMapping("/{itemId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('contract:item:delete')")
    public ApiResponse<Void> delete(@PathVariable Long contractId, @PathVariable Long itemId) {
        ctContractItemService.delete(contractId, itemId);
        return ApiResponse.success();
    }
}
