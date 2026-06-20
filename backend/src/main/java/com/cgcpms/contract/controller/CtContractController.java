package com.cgcpms.contract.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.contract.dto.ContractSaveRequest;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.service.CtContractService;
import com.cgcpms.contract.vo.ContractApprovalRecordVO;
import com.cgcpms.contract.vo.CtContractVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/contracts")
@RequiredArgsConstructor
public class CtContractController {

    private final CtContractService ctContractService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('contract:query')")
    public ApiResponse<PageResult<CtContractVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String contractCode,
            @RequestParam(required = false) String contractName,
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) String contractStatus,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long partyAId,
            @RequestParam(required = false) Long partyBId) {
        IPage<CtContractVO> page = ctContractService.getPage(pageNo, pageSize, keyword, contractCode, contractName,
                contractType, contractStatus, approvalStatus, projectId, partyAId, partyBId);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/kpi")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('contract:query')")
    public ApiResponse<Map<String, Object>> kpi(
            @RequestParam(required = false) String contractCode,
            @RequestParam(required = false) String contractName,
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) String contractStatus,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long partyAId,
            @RequestParam(required = false) Long partyBId) {
        return ApiResponse.success(ctContractService.getKpi(contractCode, contractName,
                contractType, contractStatus, approvalStatus, projectId, partyAId, partyBId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('contract:query')")
    public ApiResponse<CtContractVO> getById(@PathVariable Long id) {
        return ApiResponse.success(ctContractService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('contract:add')")
    public ApiResponse<String> create(@Valid @RequestBody CtContract contract) {
        return ApiResponse.success(String.valueOf(ctContractService.create(contract)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('contract:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody CtContract contract) {
        contract.setId(id);
        ctContractService.update(contract);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('contract:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> submitForApproval(@PathVariable Long id) {
        ctContractService.submitForApproval(id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('contract:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ctContractService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/composite")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('contract:add')")
    public ApiResponse<String> compositeCreate(@Valid @RequestBody ContractSaveRequest request) {
        return ApiResponse.success(String.valueOf(ctContractService.compositeSave(request)));
    }

    /**
     * Upsert endpoint: creates a new contract if the id does not exist,
     * or fully replaces the existing contract (including items and payment terms) if it does.
     * This is an intentional design choice — POST for create, PUT for create-or-update (upsert).
     */
    @PutMapping("/{id}/composite")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('contract:edit')")
    public ApiResponse<Void> compositeUpdate(@PathVariable Long id, @Valid @RequestBody ContractSaveRequest request) {
        request.getContract().setId(id);
        ctContractService.compositeSave(request);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/approval-records")
    @PreAuthorize("hasAuthority('contract:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<ContractApprovalRecordVO>> getApprovalRecords(@PathVariable Long id) {
        return ApiResponse.success(ctContractService.getApprovalRecords(id));
    }
}
