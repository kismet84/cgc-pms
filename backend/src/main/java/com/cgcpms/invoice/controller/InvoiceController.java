package com.cgcpms.invoice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.service.InvoiceService;
import com.cgcpms.invoice.vo.InvoiceVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
            @RequestParam(required = false) Long payApplicationId) {
        IPage<InvoiceVO> page = invoiceService.getPage(pageNo, pageSize, payRecordId, payApplicationId);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('invoice:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<InvoiceVO> getById(@PathVariable Long id) {
        return ApiResponse.success(invoiceService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('invoice:add') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Long> create(@Valid @RequestBody PayInvoice invoice) {
        return ApiResponse.success(invoiceService.create(invoice));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('invoice:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody PayInvoice invoice) {
        invoice.setId(id);
        invoiceService.update(invoice);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('invoice:delete') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        invoiceService.delete(id);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/verify")
    @PreAuthorize("hasAuthority('invoice:verify') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> verify(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String targetStatus = body.get("verifyStatus");
        invoiceService.verify(id, targetStatus);
        return ApiResponse.success();
    }

    @PostMapping("/register")
    @PreAuthorize("hasAuthority('invoice:add') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Long> register(@Valid @RequestBody PayInvoice invoice) {
        return ApiResponse.success(invoiceService.register(invoice));
    }
}
