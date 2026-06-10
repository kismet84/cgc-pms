package com.cgcpms.contract.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.service.CtContractItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/contracts/{contractId}/items")
@RequiredArgsConstructor
public class CtContractItemController {

    private final CtContractItemService ctContractItemService;

    @GetMapping
    public ApiResponse<List<CtContractItem>> getByContractId(@PathVariable Long contractId) {
        return ApiResponse.success(ctContractItemService.getByContractId(contractId));
    }

    @PostMapping
    public ApiResponse<Long> create(@PathVariable Long contractId, @RequestBody CtContractItem item) {
        item.setContractId(contractId);
        return ApiResponse.success(ctContractItemService.create(item));
    }

    @PostMapping("/batch")
    public ApiResponse<Void> batchSave(@PathVariable Long contractId, @RequestBody List<CtContractItem> items) {
        ctContractItemService.batchSave(contractId, items);
        return ApiResponse.success();
    }

    @PutMapping("/{itemId}")
    public ApiResponse<Void> update(@PathVariable Long contractId, @PathVariable Long itemId,
                                    @RequestBody CtContractItem item) {
        item.setId(itemId);
        item.setContractId(contractId);
        ctContractItemService.update(item);
        return ApiResponse.success();
    }

    @DeleteMapping("/{itemId}")
    public ApiResponse<Void> delete(@PathVariable Long contractId, @PathVariable Long itemId) {
        ctContractItemService.delete(itemId);
        return ApiResponse.success();
    }
}
