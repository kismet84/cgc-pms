package com.cgcpms.cost.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.cost.service.CostSubjectV2Service;
import com.cgcpms.cost.service.CostSubjectV2Service.FinanceAllocationCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.MappingVersionCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.RuleCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.ScopeCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.TransferCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/cost-subject-v2")
@RequiredArgsConstructor
public class CostSubjectV2Controller {

    private final CostSubjectV2Service service;

    @GetMapping("/mapping-versions")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('cost:subject:mapping:query','cost:subject:rule:query')")
    public ApiResponse<List<Map<String, Object>>> mappingVersions() {
        return ApiResponse.success(service.mappingVersions());
    }

    @GetMapping("/mapping-versions/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:mapping:query')")
    public ApiResponse<List<Map<String, Object>>> mappingItems(@PathVariable Long id) {
        return ApiResponse.success(service.mappingItems(id));
    }

    @PostMapping("/mapping-versions")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:mapping:edit')")
    public ApiResponse<String> createMappingVersion(@Valid @RequestBody MappingVersionCommand command) {
        return ApiResponse.success(String.valueOf(service.createMappingVersion(command)));
    }

    @PostMapping("/mapping-versions/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:mapping:activate')")
    public ApiResponse<Void> activateMappingVersion(@PathVariable Long id, @RequestParam Long approvalInstanceId) {
        service.activateMappingVersion(id, approvalInstanceId);
        return ApiResponse.success();
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:rule:query')")
    public ApiResponse<List<Map<String, Object>>> rules() {
        return ApiResponse.success(service.rules());
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:rule:edit')")
    public ApiResponse<String> createRule(@Valid @RequestBody RuleCommand command) {
        return ApiResponse.success(String.valueOf(service.createRule(command)));
    }

    @GetMapping("/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:rule:query')")
    public ApiResponse<String> resolve(@RequestParam String sourceType,
                                       @RequestParam(required = false) String businessCategory,
                                       @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(String.valueOf(service.resolveRule(sourceType, businessCategory, projectId)));
    }

    @GetMapping("/scopes")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:scope:query')")
    public ApiResponse<List<Map<String, Object>>> scopes(@RequestParam Long projectId) {
        return ApiResponse.success(service.scopes(projectId));
    }

    @PostMapping("/scopes")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:scope:edit')")
    public ApiResponse<String> upsertScope(@Valid @RequestBody ScopeCommand command) {
        return ApiResponse.success(String.valueOf(service.upsertScope(command)));
    }

    @GetMapping("/impact/{subjectId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:audit:query')")
    public ApiResponse<Map<String, Object>> impact(@PathVariable Long subjectId) {
        return ApiResponse.success(service.impact(subjectId));
    }

    @GetMapping("/bid-transfers")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('cost:query','cost:subject:audit:query')")
    public ApiResponse<List<Map<String, Object>>> bidTransfers() {
        return ApiResponse.success(service.transfers());
    }

    @PostMapping("/bid-transfers")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:bid-transfer')")
    public ApiResponse<String> transferBidCost(@Valid @RequestBody TransferCommand command) {
        return ApiResponse.success(String.valueOf(service.transferBidCost(command)));
    }

    @PostMapping("/bid-transfers/{id}/reverse")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:bid-transfer')")
    public ApiResponse<String> reverseBidTransfer(@PathVariable Long id,
                                                  @RequestParam Long approvalInstanceId,
                                                  @RequestParam String idempotencyKey,
                                                  @RequestParam(required = false) String remark) {
        return ApiResponse.success(String.valueOf(service.reverseBidTransfer(id, approvalInstanceId, idempotencyKey, remark)));
    }

    @GetMapping("/finance-allocations")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('cost:query','cost:subject:audit:query')")
    public ApiResponse<List<Map<String, Object>>> financeAllocations() {
        return ApiResponse.success(service.financeAllocations());
    }

    @PostMapping("/finance-allocations")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:finance-allocate')")
    public ApiResponse<String> allocateFinanceCost(@Valid @RequestBody FinanceAllocationCommand command) {
        return ApiResponse.success(String.valueOf(service.allocateFinanceCost(command)));
    }

    @PostMapping("/finance-allocations/{id}/reverse")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:finance-allocate')")
    public ApiResponse<String> reverseFinanceAllocation(@PathVariable Long id,
                                                        @RequestParam Long approvalInstanceId,
                                                        @RequestParam String idempotencyKey,
                                                        @RequestParam(required = false) String remark) {
        return ApiResponse.success(String.valueOf(service.reverseFinanceAllocation(id, approvalInstanceId, idempotencyKey, remark)));
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:audit:query')")
    public ApiResponse<Map<String, Object>> reconciliation(@RequestParam Long projectId) {
        return ApiResponse.success(service.reconciliation(projectId));
    }
}
