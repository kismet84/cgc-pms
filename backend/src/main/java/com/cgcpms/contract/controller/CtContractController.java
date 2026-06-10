package com.cgcpms.contract.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.service.CtContractService;
import com.cgcpms.contract.vo.CtContractVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contracts")
@RequiredArgsConstructor
public class CtContractController {

    private final CtContractService ctContractService;

    @GetMapping
    public ApiResponse<PageResult<CtContractVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String contractCode,
            @RequestParam(required = false) String contractName,
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) String contractStatus,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long partnerId) {
        IPage<CtContractVO> page = ctContractService.getPage(pageNo, pageSize, contractCode, contractName,
                contractType, contractStatus, approvalStatus, projectId, partnerId);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    public ApiResponse<CtContractVO> getById(@PathVariable Long id) {
        return ApiResponse.success(ctContractService.getById(id));
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody CtContract contract) {
        return ApiResponse.success(ctContractService.create(contract));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody CtContract contract) {
        contract.setId(id);
        ctContractService.update(contract);
        return ApiResponse.success();
    }
}
