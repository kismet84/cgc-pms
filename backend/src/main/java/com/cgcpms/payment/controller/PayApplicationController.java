package com.cgcpms.payment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayApplicationBasis;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.payment.vo.PayApplicationBasisVO;
import com.cgcpms.payment.vo.PayApplicationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pay-applications")
@RequiredArgsConstructor
public class PayApplicationController {

    private final PayApplicationService payApplicationService;

    @GetMapping
    @PreAuthorize("hasAuthority('payment:app:query') or hasRole('ADMIN')")
    public ApiResponse<PageResult<PayApplicationVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String payStatus,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String applyCode) {
        IPage<PayApplicationVO> page = payApplicationService.getPage(pageNo, pageSize, projectId, contractId,
                partnerId, payStatus, approvalStatus, applyCode);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('payment:app:query') or hasRole('ADMIN')")
    public ApiResponse<PayApplicationVO> getById(@PathVariable Long id) {
        return ApiResponse.success(payApplicationService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('payment:app:add') or hasRole('ADMIN')")
    public ApiResponse<Long> create(@RequestBody PayApplication app) {
        return ApiResponse.success(payApplicationService.create(app));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('payment:app:edit') or hasRole('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody PayApplication app) {
        app.setId(id);
        payApplicationService.update(app);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('payment:app:delete') or hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        payApplicationService.delete(id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/basis")
    @PreAuthorize("hasAuthority('payment:app:query') or hasRole('ADMIN')")
    public ApiResponse<List<PayApplicationBasisVO>> listBasis(@PathVariable Long id) {
        return ApiResponse.success(payApplicationService.getBasisList(id));
    }

    @PostMapping("/{id}/basis/batch")
    @PreAuthorize("hasAuthority('payment:app:edit') or hasRole('ADMIN')")
    public ApiResponse<Void> batchSaveBasis(@PathVariable Long id, @RequestBody List<PayApplicationBasis> basisList) {
        payApplicationService.saveBasis(id, basisList);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('payment:app:submit') or hasRole('ADMIN')")
    public ApiResponse<Void> submitForApproval(@PathVariable Long id) {
        payApplicationService.submitForApproval(id);
        return ApiResponse.success();
    }
}
