package com.cgcpms.payment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayApplicationBasis;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.payment.service.PaymentApplicationSourceService;
import com.cgcpms.payment.entity.PaymentApplicationSource;
import com.cgcpms.payment.vo.PaymentApplicationSourceVO;
import com.cgcpms.payment.vo.PaymentSourceOptionVO;
import com.cgcpms.payment.vo.PayApplicationBasisVO;
import com.cgcpms.payment.vo.PayApplicationVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pay-applications")
@RequiredArgsConstructor
public class PayApplicationController {

    private final PayApplicationService payApplicationService;
    private final PaymentApplicationSourceService sourceService;

    @GetMapping
    @PreAuthorize("hasAuthority('payment:app:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
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

    @GetMapping("/source-options")
    @PreAuthorize("hasAuthority('payment:app:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<PaymentSourceOptionVO>> listSourceOptions(
            @RequestParam Long projectId,
            @RequestParam Long contractId,
            @RequestParam Long partnerId,
            @RequestParam String payType,
            @RequestParam(required = false) String expenseCategory) {
        return ApiResponse.success(sourceService.listOptions(projectId, contractId, partnerId,
                payType, expenseCategory));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('payment:app:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PayApplicationVO> getById(@PathVariable Long id) {
        return ApiResponse.success(payApplicationService.getById(id));
    }

    @PostMapping
    @AuditedOperation(type = "CREATE", businessType = "PAYMENT", businessIdExpression = "#app.id")
    @PreAuthorize("hasAuthority('payment:app:add') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Long> create(@Valid @RequestBody PayApplication app) {
        return ApiResponse.success(payApplicationService.create(app));
    }

    @PutMapping("/{id}")
    @AuditedOperation(type = "UPDATE", businessType = "PAYMENT", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('payment:app:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody PayApplication app) {
        app.setId(id);
        payApplicationService.update(app);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @AuditedOperation(type = "DELETE", businessType = "PAYMENT", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('payment:app:delete') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        payApplicationService.delete(id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/basis")
    @PreAuthorize("hasAuthority('payment:app:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<PayApplicationBasisVO>> listBasis(@PathVariable Long id) {
        return ApiResponse.success(payApplicationService.getBasisList(id));
    }

    @PostMapping("/{id}/basis/batch")
    @AuditedOperation(type = "UPDATE_BASIS", businessType = "PAYMENT", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('payment:app:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> batchSaveBasis(@PathVariable Long id,
                                            @Valid @Size(max = 200, message = "批量依据不能超过200条")
                                            @RequestBody List<PayApplicationBasis> basisList) {
        payApplicationService.saveBasis(id, basisList);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/sources")
    @PreAuthorize("hasAuthority('payment:app:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<PaymentApplicationSourceVO>> listSources(@PathVariable Long id) {
        return ApiResponse.success(sourceService.list(id));
    }

    @PostMapping("/{id}/sources/batch")
    @AuditedOperation(type = "UPDATE_SOURCES", businessType = "PAYMENT", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('payment:app:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> batchSaveSources(
            @PathVariable Long id,
            @Valid @Size(max = 200, message = "付款来源不能超过200条")
            @RequestBody List<PaymentApplicationSource> sources) {
        sourceService.save(id, sources);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/submit")
    @AuditedOperation(type = "SUBMIT", businessType = "PAYMENT", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('payment:app:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> submitForApproval(@PathVariable Long id) {
        payApplicationService.submitForApproval(id);
        return ApiResponse.success();
    }
}
