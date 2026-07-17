package com.cgcpms.cashforecast.controller;

import com.cgcpms.cashforecast.dto.CashForecastModels.*;
import com.cgcpms.cashforecast.service.ProjectCashForecastService;
import com.cgcpms.common.result.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/cash-forecasts")
@RequiredArgsConstructor
public class ProjectCashForecastController {
    private final ProjectCashForecastService service;
    @PostMapping("/cycles") @PreAuthorize("hasAuthority('finance:forecast:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')") public ApiResponse<Map<String,Object>> create(@Valid@RequestBody CycleRequest r){return ApiResponse.success(service.createCycle(r));}
    @GetMapping("/cycles") @PreAuthorize("hasAuthority('finance:forecast:query') or hasAnyRole('ADMIN','SUPER_ADMIN')") public ApiResponse<List<Map<String,Object>>> list(@RequestParam Long projectId){return ApiResponse.success(service.cycles(projectId));}
    @GetMapping("/cycles/{id}/trace") @PreAuthorize("hasAuthority('finance:forecast:query') or hasAnyRole('ADMIN','SUPER_ADMIN')") public ApiResponse<Map<String,Object>> trace(@PathVariable Long id){return ApiResponse.success(service.trace(id));}
    @PostMapping("/cycles/{id}/regenerate") @PreAuthorize("hasAuthority('finance:forecast:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')") public ApiResponse<Map<String,Object>> regenerate(@PathVariable Long id){return ApiResponse.success(service.regenerate(id));}
    @PostMapping("/cycles/{id}/submit") @PreAuthorize("hasAuthority('finance:forecast:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')") public ApiResponse<Map<String,Object>> submit(@PathVariable Long id){return ApiResponse.success(service.submit(id));}
    @PostMapping("/cycles/{id}/approve") @PreAuthorize("hasAuthority('finance:forecast:approve') or hasAnyRole('ADMIN','SUPER_ADMIN')") public ApiResponse<Map<String,Object>> approve(@PathVariable Long id,@Valid@RequestBody ApprovalRequest r){return ApiResponse.success(service.approve(id,r));}
    @PostMapping("/cycles/{id}/actuals/refresh") @PreAuthorize("hasAuthority('finance:forecast:refresh') or hasAnyRole('ADMIN','SUPER_ADMIN')") public ApiResponse<Map<String,Object>> refresh(@PathVariable Long id){return ApiResponse.success(service.refreshActual(id));}
    @PostMapping("/cycles/{id}/roll") @PreAuthorize("hasAuthority('finance:forecast:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')") public ApiResponse<Map<String,Object>> roll(@PathVariable Long id,@Valid@RequestBody RollRequest r){return ApiResponse.success(service.roll(id,r));}
    @PostMapping("/cycles/{id}/actions") @PreAuthorize("hasAuthority('finance:forecast:action') or hasAnyRole('ADMIN','SUPER_ADMIN')") public ApiResponse<Map<String,Object>> action(@PathVariable Long id,@Valid@RequestBody FundingActionRequest r){return ApiResponse.success(service.createAction(id,r));}
    @PostMapping("/actions/{id}/submit") @PreAuthorize("hasAuthority('finance:forecast:action') or hasAnyRole('ADMIN','SUPER_ADMIN')") public ApiResponse<Map<String,Object>> submitAction(@PathVariable Long id){return ApiResponse.success(service.submitAction(id));}
    @PostMapping("/actions/{id}/approve") @PreAuthorize("hasAuthority('finance:forecast:action:approve') or hasAnyRole('ADMIN','SUPER_ADMIN')") public ApiResponse<Map<String,Object>> approveAction(@PathVariable Long id,@Valid@RequestBody FundingActionApprovalRequest r){return ApiResponse.success(service.approveAction(id,r));}
    @PostMapping("/actions/{id}/complete") @PreAuthorize("hasAuthority('finance:forecast:action') or hasAnyRole('ADMIN','SUPER_ADMIN')") public ApiResponse<Map<String,Object>> completeAction(@PathVariable Long id,@Valid@RequestBody FundingActionCompletionRequest r){return ApiResponse.success(service.completeAction(id,r));}
}
