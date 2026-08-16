package com.cgcpms.cost.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.service.CostSubjectV2Service;
import com.cgcpms.cost.service.CostSubjectV2Service.BidTransferRequestCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.ClassificationOverrideCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.FinanceAllocationCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.MappingVersionCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.ProjectConfigCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.RecalculationCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.ReversalCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.RuleCommand;
import com.cgcpms.cost.service.CostSubjectV2Service.TransferCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
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

    @GetMapping("/mapping-versions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('cost:subject:mapping:query','cost:subject:rule:query')")
    public ApiResponse<Map<String, Object>> mappingVersion(@PathVariable Long id) {
        return ApiResponse.success(service.mappingVersionDetail(id));
    }

    @PostMapping("/mapping-versions")
    @PreAuthorize("hasAuthority('cost:subject:mapping:edit')")
    public ApiResponse<String> createMappingVersion(@Valid @RequestBody MappingVersionCommand command) {
        return ApiResponse.success(String.valueOf(service.createMappingVersion(command)));
    }

    @PostMapping("/mapping-versions/generate-initial")
    @PreAuthorize("hasAuthority('cost:subject:mapping:edit')")
    public ApiResponse<Map<String, Object>> generateInitialMappingVersion() {
        return ApiResponse.success(service.generateInitialPlan());
    }

    @PostMapping("/mapping-versions/{id}/validate")
    @PreAuthorize("hasAuthority('cost:subject:mapping:edit')")
    public ApiResponse<Map<String, Object>> validateMappingVersion(@PathVariable Long id) {
        return ApiResponse.success(service.validateMappingVersion(id));
    }

    @PostMapping("/mapping-versions/{id}/submit")
    @PreAuthorize("hasAuthority('cost:rule-plan:submit')")
    public ApiResponse<Map<String, Object>> submitMappingVersion(@PathVariable Long id) {
        return ApiResponse.success(service.submitMappingVersion(id));
    }

    @GetMapping("/mapping-versions/{id}/diff")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('cost:subject:mapping:query','cost:subject:rule:query')")
    public ApiResponse<Map<String, Object>> mappingVersionDiff(@PathVariable Long id,
                                                               @RequestParam Long baseId) {
        return ApiResponse.success(service.mappingVersionDiff(id, baseId));
    }

    @GetMapping("/mapping-versions/{id}/trial")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('cost:subject:mapping:query','cost:subject:rule:query')")
    public ApiResponse<Map<String, Object>> trialMappingVersion(@PathVariable Long id,
                                                                @RequestParam String sourceType,
                                                                @RequestParam(required = false) String businessCategory,
                                                                @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(service.trialMappingVersion(id, sourceType, businessCategory, projectId));
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:rule:query')")
    public ApiResponse<List<Map<String, Object>>> rules() {
        return ApiResponse.success(service.rules());
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('cost:subject:rule:edit')")
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
    @PreAuthorize("hasAuthority('cost:subject:bid-transfer')")
    public ApiResponse<String> transferBidCost(@Valid @RequestBody TransferCommand command) {
        throw new BusinessException("WORKFLOW_REQUIRED", "投标成本移交必须先创建申请并完成审批");
    }

    @GetMapping("/bid-transfer-requests")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('cost:query','cost:subject:bid-transfer','cost:subject:audit:query')")
    public ApiResponse<List<Map<String, Object>>> bidTransferRequests() {
        return ApiResponse.success(service.bidTransferRequests());
    }

    @PostMapping("/bid-transfer-requests")
    @PreAuthorize("hasAuthority('cost:subject:bid-transfer')")
    public ApiResponse<Map<String, Object>> createBidTransferRequest(@Valid @RequestBody BidTransferRequestCommand command) {
        return ApiResponse.success(service.createBidTransferRequest(command));
    }

    @PostMapping("/bid-transfer-requests/{id}/submit")
    @PreAuthorize("hasAuthority('cost:subject:transfer:submit')")
    public ApiResponse<Map<String, Object>> submitBidTransferRequest(@PathVariable Long id) {
        return ApiResponse.success(service.submitBidTransferRequest(id));
    }

    @PostMapping("/bid-transfer-requests/{id}/cancel")
    @PreAuthorize("hasAuthority('cost:subject:bid-transfer')")
    public ApiResponse<Map<String, Object>> cancelBidTransferRequest(@PathVariable Long id) {
        return ApiResponse.success(service.cancelBidTransferRequest(id));
    }

    @GetMapping("/finance-allocations")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('cost:query','cost:subject:audit:query')")
    public ApiResponse<List<Map<String, Object>>> financeAllocations() {
        return ApiResponse.success(service.financeAllocations());
    }

    @PostMapping("/finance-allocations")
    @PreAuthorize("hasAuthority('cost:subject:finance-allocate')")
    public ApiResponse<String> allocateFinanceCost(@Valid @RequestBody FinanceAllocationCommand command) {
        throw new BusinessException("WORKFLOW_REQUIRED", "财务成本分摊必须先创建申请并完成审批");
    }

    @GetMapping("/finance-allocation-requests")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('cost:query','cost:subject:finance-allocate','cost:subject:audit:query')")
    public ApiResponse<List<Map<String, Object>>> financeAllocationRequests() {
        return ApiResponse.success(service.financeAllocationRequests());
    }

    @PostMapping("/finance-allocation-requests")
    @PreAuthorize("hasAuthority('cost:subject:finance-allocate') and hasAuthority('cost:classification:override')")
    public ApiResponse<Map<String, Object>> createFinanceAllocationRequest(@Valid @RequestBody FinanceAllocationCommand command) {
        return ApiResponse.success(service.createFinanceAllocationRequest(command));
    }

    @PostMapping("/finance-allocation-requests/{id}/submit")
    @PreAuthorize("hasAuthority('cost:subject:allocation:submit')")
    public ApiResponse<Map<String, Object>> submitFinanceAllocationRequest(@PathVariable Long id) {
        return ApiResponse.success(service.submitFinanceAllocationRequest(id));
    }

    @PostMapping("/finance-allocation-requests/{id}/cancel")
    @PreAuthorize("hasAuthority('cost:subject:finance-allocate')")
    public ApiResponse<Map<String, Object>> cancelFinanceAllocationRequest(@PathVariable Long id) {
        return ApiResponse.success(service.cancelFinanceAllocationRequest(id));
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:audit:query')")
    public ApiResponse<Map<String, Object>> reconciliation(@RequestParam Long projectId) {
        return ApiResponse.success(service.reconciliation(projectId));
    }

    @PostMapping("/classification-overrides")
    @PreAuthorize("hasAuthority('cost:classification:override')")
    public ApiResponse<String> overrideClassification(
            @Valid @RequestBody ClassificationOverrideCommand command) {
        return ApiResponse.success(String.valueOf(service.overrideClassification(command)));
    }

    @GetMapping("/form-options")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAnyAuthority('cost:query','cost:subject:mapping:query','cost:subject:scope:query','cost:subject:audit:query')")
    public ApiResponse<Map<String, Object>> formOptions() {
        return ApiResponse.success(service.governanceFormOptions());
    }

    @GetMapping("/project-config")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:scope:query')")
    public ApiResponse<Map<String, Object>> projectConfiguration(@RequestParam Long projectId) {
        return ApiResponse.success(service.projectConfiguration(projectId));
    }

    @PostMapping("/project-config-requests")
    @PreAuthorize("hasAuthority('cost:project-config:edit')")
    public ApiResponse<Map<String, Object>> createProjectConfig(@Valid @RequestBody ProjectConfigCommand command) {
        return ApiResponse.success(service.createProjectConfig(command));
    }

    @PostMapping("/project-config-requests/{id}/submit")
    @PreAuthorize("hasAuthority('cost:project-config:submit')")
    public ApiResponse<Map<String, Object>> submitProjectConfig(@PathVariable Long id) {
        return ApiResponse.success(service.submitProjectConfig(id));
    }

    @PostMapping("/project-config-requests/{id}/cancel")
    @PreAuthorize("hasAuthority('cost:project-config:edit')")
    public ApiResponse<Map<String, Object>> cancelProjectConfig(@PathVariable Long id) {
        return ApiResponse.success(service.cancelProjectConfig(id));
    }

    @GetMapping("/recalculation-batches")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:audit:query')")
    public ApiResponse<List<Map<String, Object>>> recalculationBatches() {
        return ApiResponse.success(service.recalculationBatches());
    }

    @GetMapping("/recalculation-batches/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:audit:query')")
    public ApiResponse<Map<String, Object>> recalculationBatch(@PathVariable Long id) {
        return ApiResponse.success(service.recalculationBatch(id));
    }

    @PostMapping("/recalculation-batches")
    @PreAuthorize("(#command.batchType == 'POST_CLOSE_ADJUSTMENT' and hasAuthority('cost:post-close:edit'))"
            + " or ((#command.batchType == null or #command.batchType == 'HISTORY_RECALCULATION')"
            + " and hasAuthority('cost:recalculation:edit'))")
    public ApiResponse<Map<String, Object>> createRecalculation(@Valid @RequestBody RecalculationCommand command) {
        return ApiResponse.success(service.createRecalculation(command));
    }

    @PostMapping("/recalculation-batches/{id}/submit")
    @PreAuthorize("hasAnyAuthority('cost:recalculation:submit','cost:post-close:submit')")
    public ApiResponse<Map<String, Object>> submitRecalculation(@PathVariable Long id, Authentication authentication) {
        requireRecalculationPermission(service.recalculationBatch(id), authentication,
                "cost:recalculation:submit", "cost:post-close:submit");
        return ApiResponse.success(service.submitRecalculation(id));
    }

    @PostMapping("/recalculation-batches/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('cost:recalculation:edit','cost:post-close:edit')")
    public ApiResponse<Void> cancelRecalculation(@PathVariable Long id, Authentication authentication) {
        requireRecalculationPermission(service.recalculationBatch(id), authentication,
                "cost:recalculation:edit", "cost:post-close:edit");
        service.cancelRecalculation(id);
        return ApiResponse.success();
    }

    private static void requireRecalculationPermission(Map<String, Object> batch, Authentication authentication,
                                                       String historyPermission, String postClosePermission) {
        String required = "POST_CLOSE_ADJUSTMENT".equals(batch.get("batchType"))
                ? postClosePermission : historyPermission;
        boolean allowed = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> required.equals(authority.getAuthority()));
        if (!allowed) throw new AccessDeniedException("缺少当前重算类型所需权限：" + required);
    }

    @GetMapping("/reversal-requests")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:audit:query')")
    public ApiResponse<List<Map<String, Object>>> reversalRequests() {
        return ApiResponse.success(service.reversalRequests());
    }

    @GetMapping("/reversal-requests/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('cost:subject:audit:query')")
    public ApiResponse<Map<String, Object>> reversalRequest(@PathVariable Long id) {
        return ApiResponse.success(service.reversalRequest(id));
    }

    @PostMapping("/reversal-requests")
    @PreAuthorize("hasAuthority('cost:reversal:edit')")
    public ApiResponse<Map<String, Object>> createReversal(@Valid @RequestBody ReversalCommand command) {
        return ApiResponse.success(service.createReversal(command));
    }

    @PostMapping("/reversal-requests/{id}/submit")
    @PreAuthorize("hasAuthority('cost:reversal:submit')")
    public ApiResponse<Map<String, Object>> submitReversal(@PathVariable Long id) {
        return ApiResponse.success(service.submitReversal(id));
    }

    @PostMapping("/reversal-requests/{id}/cancel")
    @PreAuthorize("hasAuthority('cost:reversal:edit')")
    public ApiResponse<Map<String, Object>> cancelReversal(@PathVariable Long id) {
        return ApiResponse.success(service.cancelReversal(id));
    }
}
