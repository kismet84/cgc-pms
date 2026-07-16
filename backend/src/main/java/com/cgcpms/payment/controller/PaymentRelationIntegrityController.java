package com.cgcpms.payment.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.payment.service.PaymentRelationIntegrityService;
import com.cgcpms.payment.vo.RelationIntegrityIssueVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system/data-integrity/payment-chain")
@RequiredArgsConstructor
public class PaymentRelationIntegrityController {
    private final PaymentRelationIntegrityService integrityService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<RelationIntegrityIssueVO>> scan() {
        return ApiResponse.success(integrityService.scan());
    }
}
