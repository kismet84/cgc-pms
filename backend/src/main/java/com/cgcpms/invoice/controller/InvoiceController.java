package com.cgcpms.invoice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.invoice.dto.InvoiceVerifyRequest;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.service.InvoiceService;
import com.cgcpms.invoice.vo.InvoiceRecognizeResultVO;
import com.cgcpms.invoice.vo.InvoiceVO;
import com.cgcpms.invoice.entity.InvoicePaymentAllocation;
import jakarta.validation.constraints.Size;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    @PreAuthorize("hasAuthority('invoice:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PageResult<InvoiceVO>> list(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long payRecordId,
            @RequestParam(required = false) Long payApplicationId,
            @RequestParam(required = false) String invoiceNo,
            @RequestParam(required = false) String verifyStatus) {
        IPage<InvoiceVO> page = invoiceService.getPage(pageNo, pageSize, payRecordId, payApplicationId, invoiceNo, verifyStatus);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('invoice:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<InvoiceVO> getById(@PathVariable Long id) {
        return ApiResponse.success(invoiceService.getById(id));
    }

    @PostMapping
    @AuditedOperation(type = "CREATE", businessType = "INVOICE", businessIdExpression = "#invoice.id")
    @PreAuthorize("hasAuthority('invoice:add') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Long> create(@Valid @RequestBody PayInvoice invoice) {
        return ApiResponse.success(invoiceService.create(invoice));
    }

    @PutMapping("/{id}")
    @AuditedOperation(type = "UPDATE", businessType = "INVOICE", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('invoice:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody PayInvoice invoice) {
        invoice.setId(id);
        invoiceService.update(invoice);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @AuditedOperation(type = "DELETE", businessType = "INVOICE", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('invoice:delete') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        invoiceService.delete(id);
        return ApiResponse.success();
    }

    @RequestMapping(value = "/{id}/verify", method = {RequestMethod.PUT, RequestMethod.POST})
    @AuditedOperation(type = "VERIFY", businessType = "INVOICE", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('invoice:verify') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> verify(@PathVariable Long id, @Valid @RequestBody InvoiceVerifyRequest request) {
        invoiceService.verify(id, request.getVerifyStatus());
        return ApiResponse.success();
    }

    @PostMapping("/register")
    @AuditedOperation(type = "REGISTER", businessType = "INVOICE", businessIdExpression = "#invoice.id")
    @PreAuthorize("hasAuthority('invoice:add') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Long> register(@Valid @RequestBody PayInvoice invoice) {
        return ApiResponse.success(invoiceService.register(invoice));
    }

    @GetMapping("/{id}/allocations")
    @PreAuthorize("hasAuthority('invoice:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<InvoicePaymentAllocation>> listAllocations(@PathVariable Long id) {
        return ApiResponse.success(invoiceService.listAllocations(id));
    }

    @PostMapping("/{id}/allocations/batch")
    @AuditedOperation(type = "ALLOCATE", businessType = "INVOICE", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('invoice:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> saveAllocations(
            @PathVariable Long id,
            @Valid @Size(max = 200, message = "发票付款分配不能超过200条")
            @RequestBody List<InvoicePaymentAllocation> allocations) {
        invoiceService.saveAllocations(id, allocations);
        return ApiResponse.success();
    }

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @PostMapping("/recognize")
    @AuditedOperation(type = "RECOGNIZE", businessType = "INVOICE")
    @PreAuthorize("hasAuthority('invoice:add') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<InvoiceRecognizeResultVO> recognize(@RequestParam MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("FILE_TOO_LARGE", "发票文件大小不能超过10MB");
        }
        return ApiResponse.success(invoiceService.recognize(file));
    }
}
