package com.cgcpms.payment.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.payment.service.PaymentTraceService;
import com.cgcpms.payment.vo.PaymentTraceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payment-traces")
@RequiredArgsConstructor
public class PaymentTraceController {
    private final PaymentTraceService traceService;

    @GetMapping("/cash-journals/{id}")
    @PreAuthorize("hasAuthority('payment:trace:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PaymentTraceVO> byCashJournal(@PathVariable Long id) {
        return ApiResponse.success(traceService.byCashJournal(id));
    }

    @GetMapping("/pay-records/{id}")
    @PreAuthorize("hasAuthority('payment:trace:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PaymentTraceVO> byPayRecord(@PathVariable Long id) {
        return ApiResponse.success(traceService.byPayRecord(id));
    }

    @GetMapping("/applications/{id}")
    @PreAuthorize("hasAuthority('payment:trace:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PaymentTraceVO> byApplication(@PathVariable Long id) {
        return ApiResponse.success(traceService.byApplication(id));
    }
}
