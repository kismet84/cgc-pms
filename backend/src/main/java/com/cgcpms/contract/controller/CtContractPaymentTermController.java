package com.cgcpms.contract.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import com.cgcpms.contract.service.CtContractPaymentTermService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/contracts/{contractId}/payment-terms")
@RequiredArgsConstructor
public class CtContractPaymentTermController {

    private final CtContractPaymentTermService ctContractPaymentTermService;

    @GetMapping
    public ApiResponse<List<CtContractPaymentTerm>> getByContractId(@PathVariable Long contractId) {
        return ApiResponse.success(ctContractPaymentTermService.getByContractId(contractId));
    }

    @PostMapping
    public ApiResponse<Long> create(@PathVariable Long contractId, @RequestBody CtContractPaymentTerm term) {
        term.setContractId(contractId);
        return ApiResponse.success(ctContractPaymentTermService.create(term));
    }

    @PostMapping("/batch")
    public ApiResponse<Void> batchSave(@PathVariable Long contractId, @RequestBody List<CtContractPaymentTerm> terms) {
        ctContractPaymentTermService.batchSave(contractId, terms);
        return ApiResponse.success();
    }

    @PutMapping("/{termId}")
    public ApiResponse<Void> update(@PathVariable Long contractId, @PathVariable Long termId, @RequestBody CtContractPaymentTerm term) {
        term.setId(termId);
        term.setContractId(contractId);
        ctContractPaymentTermService.update(term);
        return ApiResponse.success();
    }

    @DeleteMapping("/{termId}")
    public ApiResponse<Void> delete(@PathVariable Long contractId, @PathVariable Long termId) {
        ctContractPaymentTermService.delete(termId);
        return ApiResponse.success();
    }
}
