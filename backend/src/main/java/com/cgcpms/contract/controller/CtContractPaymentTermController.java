package com.cgcpms.contract.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import com.cgcpms.contract.service.CtContractPaymentTermService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/contracts/{contractId}/payment-terms")
@RequiredArgsConstructor
public class CtContractPaymentTermController {

    private final CtContractPaymentTermService ctContractPaymentTermService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('contract:term:query')")
    public ApiResponse<List<CtContractPaymentTerm>> getByContractId(@PathVariable Long contractId) {
        return ApiResponse.success(ctContractPaymentTermService.getByContractId(contractId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('contract:term:add')")
    public ApiResponse<Long> create(@PathVariable Long contractId, @Valid @RequestBody CtContractPaymentTerm term) {
        term.setContractId(contractId);
        return ApiResponse.success(ctContractPaymentTermService.create(term));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('contract:term:add')")
    public ApiResponse<Void> batchSave(@PathVariable Long contractId,
                                       @RequestBody @Valid List<@Valid CtContractPaymentTerm> terms) {
        ctContractPaymentTermService.batchSave(contractId, terms);
        return ApiResponse.success();
    }

    @PutMapping("/{termId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('contract:term:edit')")
    public ApiResponse<Void> update(@PathVariable Long contractId, @PathVariable Long termId,
                                    @Valid @RequestBody CtContractPaymentTerm term) {
        term.setId(termId);
        term.setContractId(contractId);
        ctContractPaymentTermService.update(term);
        return ApiResponse.success();
    }

    @DeleteMapping("/{termId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('contract:term:delete')")
    public ApiResponse<Void> delete(@PathVariable Long contractId, @PathVariable Long termId) {
        ctContractPaymentTermService.delete(contractId, termId);
        return ApiResponse.success();
    }
}
