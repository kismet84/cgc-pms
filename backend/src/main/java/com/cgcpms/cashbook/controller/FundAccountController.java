package com.cgcpms.cashbook.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.cashbook.dto.FundAccountCommand;
import com.cgcpms.cashbook.service.FundAccountService;
import com.cgcpms.cashbook.vo.FundAccountVO;
import com.cgcpms.common.result.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/fund-accounts")
@RequiredArgsConstructor
public class FundAccountController {

    private final FundAccountService fundAccountService;

    @GetMapping
    @PreAuthorize("hasAuthority('cashbook:journal:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<FundAccountVO>> list() {
        return ApiResponse.success(fundAccountService.list());
    }

    @GetMapping("/manage")
    @PreAuthorize("hasAuthority('cashbook:account:manage') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<FundAccountVO>> listForManagement() {
        return ApiResponse.success(fundAccountService.listForManagement());
    }

    @PostMapping
    @AuditedOperation(type = "CREATE", businessType = "FUND_ACCOUNT")
    @PreAuthorize("hasAuthority('cashbook:account:manage') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<FundAccountVO> create(@Valid @RequestBody FundAccountCommand command) {
        return ApiResponse.success(fundAccountService.createFundAccount(command));
    }

    @PutMapping("/{id}")
    @AuditedOperation(type = "UPDATE", businessType = "FUND_ACCOUNT", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('cashbook:account:manage') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<FundAccountVO> update(@PathVariable Long id,
                                             @Valid @RequestBody FundAccountCommand command) {
        return ApiResponse.success(fundAccountService.updateFundAccount(id, command));
    }

    @PutMapping("/{id}/enabled")
    @AuditedOperation(type = "UPDATE", businessType = "FUND_ACCOUNT", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('cashbook:account:manage') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<FundAccountVO> setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return ApiResponse.success(fundAccountService.setEnabled(id, enabled));
    }
}
