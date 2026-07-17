package com.cgcpms.financeclose.controller;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.financeclose.dto.FinancialCloseModels.AdjustmentRequest;
import com.cgcpms.financeclose.dto.FinancialCloseModels.BankResolveRequest;
import com.cgcpms.financeclose.dto.FinancialCloseModels.CloseRequest;
import com.cgcpms.financeclose.dto.FinancialCloseModels.PeriodRequest;
import com.cgcpms.financeclose.dto.FinancialCloseModels.ReopenRequest;
import com.cgcpms.financeclose.service.FinancialCloseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/financial-close")
@RequiredArgsConstructor
public class FinancialCloseController {
    private final FinancialCloseService service;

    @GetMapping("/periods")
    @PreAuthorize("hasAuthority('finance:close:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> periods(@RequestParam(required=false) Integer year){return ApiResponse.success(service.periods(year));}
    @PostMapping("/periods")
    @PreAuthorize("hasAuthority('finance:close:check') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> create(@Valid@RequestBody PeriodRequest r){return ApiResponse.success(service.ensurePeriod(r.fiscalYear(),r.fiscalMonth()));}
    @PostMapping("/periods/{year}/{month}/checks")
    @PreAuthorize("hasAuthority('finance:close:check') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> check(@PathVariable int year,@PathVariable int month){return ApiResponse.success(service.runChecks(year,month));}
    @PostMapping("/periods/{year}/{month}/close")
    @PreAuthorize("hasAuthority('finance:close:close') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> close(@PathVariable int year,@PathVariable int month,@RequestBody(required=false)CloseRequest r){return ApiResponse.success(service.close(year,month,r==null?null:r.comment()));}
    @PostMapping("/periods/{year}/{month}/reopen")
    @PreAuthorize("hasAuthority('finance:close:reopen') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> reopen(@PathVariable int year,@PathVariable int month,@Valid@RequestBody ReopenRequest r){return ApiResponse.success(service.reopen(year,month,r.reason()));}
    @GetMapping("/periods/{id}/trace")
    @PreAuthorize("hasAuthority('finance:close:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> trace(@PathVariable Long id){return ApiResponse.success(service.trace(id));}
    @GetMapping("/periods/{year}/{month}/statements")
    @PreAuthorize("hasAuthority('finance:close:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> statements(@PathVariable int year,@PathVariable int month){return ApiResponse.success(service.statements(year,month));}
    @PostMapping("/bank-reconciliations/{id}/resolve")
    @PreAuthorize("hasAuthority('finance:close:reconcile') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> resolve(@PathVariable Long id,@Valid@RequestBody BankResolveRequest r){return ApiResponse.success(service.resolveBank(id,r));}
    @PostMapping("/adjustments")
    @PreAuthorize("hasAuthority('accounting:adjustment:add') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<AccountingEntry> adjustment(@Valid@RequestBody AdjustmentRequest r){return ApiResponse.success(service.createAdjustment(r));}
}
