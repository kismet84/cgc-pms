package com.cgcpms.supplier.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.supplier.dto.SupplierSourcingModels.*;
import com.cgcpms.supplier.entity.*;
import com.cgcpms.supplier.service.SupplierSourcingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/supplier-sourcing")
@RequiredArgsConstructor
public class SupplierSourcingController {
    private final SupplierSourcingService service;

    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:query')")
    public ApiResponse<List<SourcingEvent>> listEvents(@RequestParam Long projectId) {
        return ApiResponse.success(service.listEvents(projectId));
    }

    @PostMapping("/events")
    @AuditedOperation(type = "CREATE", businessType = "SUPPLIER_SOURCING")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:maintain')")
    public ApiResponse<SourcingEvent> createEvent(@Valid @RequestBody EventCommand command) {
        return ApiResponse.success(service.createEvent(command));
    }

    @PostMapping("/events/{id}/suppliers")
    @AuditedOperation(type = "INVITE", businessType = "SUPPLIER_SOURCING", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:maintain')")
    public ApiResponse<List<SourcingSupplier>> addSuppliers(@PathVariable Long id, @Valid @RequestBody InvitationCommand command) {
        return ApiResponse.success(service.addSuppliers(id, command));
    }

    @GetMapping("/events/{id}/suppliers")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:query')")
    public ApiResponse<List<SourcingSupplier>> listSuppliers(@PathVariable Long id) {
        return ApiResponse.success(service.listSuppliers(id));
    }

    @PostMapping("/events/{id}/publish")
    @AuditedOperation(type = "PUBLISH", businessType = "SUPPLIER_SOURCING", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:maintain')")
    public ApiResponse<SourcingEvent> publish(@PathVariable Long id) {
        return ApiResponse.success(service.publish(id));
    }

    @PostMapping("/events/{eventId}/suppliers/{partnerId}/decline")
    @AuditedOperation(type = "DECLINE", businessType = "SUPPLIER_SOURCING", businessIdExpression = "#eventId")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:quote')")
    public ApiResponse<SourcingSupplier> decline(@PathVariable Long eventId, @PathVariable Long partnerId,
                                                  @Valid @RequestBody DeclineCommand command) {
        return ApiResponse.success(service.decline(eventId, partnerId, command));
    }

    @GetMapping("/events/{id}/quotes")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:query')")
    public ApiResponse<List<SupplierQuote>> listQuotes(@PathVariable Long id) {
        return ApiResponse.success(service.listQuotes(id));
    }

    @PostMapping("/quotes")
    @AuditedOperation(type = "CREATE", businessType = "SUPPLIER_QUOTE")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:quote')")
    public ApiResponse<SupplierQuote> createQuote(@Valid @RequestBody QuoteCommand command) {
        return ApiResponse.success(service.createQuote(command));
    }

    @PostMapping("/quotes/{id}/submit")
    @AuditedOperation(type = "SUBMIT", businessType = "SUPPLIER_QUOTE", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:quote')")
    public ApiResponse<SupplierQuote> submitQuote(@PathVariable Long id) {
        return ApiResponse.success(service.submitQuote(id));
    }

    @PostMapping("/events/{id}/start-evaluation")
    @AuditedOperation(type = "START_EVALUATION", businessType = "SUPPLIER_SOURCING", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:evaluate')")
    public ApiResponse<SourcingEvent> startEvaluation(@PathVariable Long id) {
        return ApiResponse.success(service.startEvaluation(id));
    }

    @PostMapping("/evaluations")
    @AuditedOperation(type = "EVALUATE", businessType = "SUPPLIER_BID_EVALUATION")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:evaluate')")
    public ApiResponse<BidEvaluation> evaluate(@Valid @RequestBody EvaluationCommand command) {
        return ApiResponse.success(service.evaluate(command));
    }

    @GetMapping("/events/{id}/evaluations")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:query')")
    public ApiResponse<List<BidEvaluation>> listEvaluations(@PathVariable Long id) {
        return ApiResponse.success(service.listEvaluations(id));
    }

    @PostMapping("/events/{id}/award")
    @AuditedOperation(type = "AWARD", businessType = "SUPPLIER_SOURCING", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:award')")
    public ApiResponse<SourcingEvent> award(@PathVariable Long id, @Valid @RequestBody AwardCommand command) {
        return ApiResponse.success(service.award(id, command));
    }

    @PostMapping("/events/{id}/link-contract")
    @AuditedOperation(type = "LINK_CONTRACT", businessType = "SUPPLIER_SOURCING", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:award')")
    public ApiResponse<SourcingEvent> linkContract(@PathVariable Long id, @Valid @RequestBody LinkContractCommand command) {
        return ApiResponse.success(service.linkContract(id, command));
    }

    @GetMapping("/performance")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:query')")
    public ApiResponse<List<SupplierPerformanceEvaluation>> listPerformance(@RequestParam Long projectId) {
        return ApiResponse.success(service.listPerformance(projectId));
    }

    @GetMapping("/returns")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:query')")
    public ApiResponse<List<SupplierReturn>> listSupplierReturns(@RequestParam Long projectId) {
        return ApiResponse.success(service.listSupplierReturns(projectId));
    }

    @PostMapping("/returns")
    @AuditedOperation(type = "CREATE", businessType = "SUPPLIER_RETURN")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:performance:evaluate')")
    public ApiResponse<SupplierReturn> createSupplierReturn(@Valid @RequestBody SupplierReturnCommand command) {
        return ApiResponse.success(service.createSupplierReturn(command));
    }

    @PostMapping("/returns/{id}/confirm")
    @AuditedOperation(type = "CONFIRM", businessType = "SUPPLIER_RETURN", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:performance:evaluate')")
    public ApiResponse<SupplierReturn> confirmSupplierReturn(@PathVariable Long id) {
        return ApiResponse.success(service.confirmSupplierReturn(id));
    }

    @PostMapping("/performance")
    @AuditedOperation(type = "CREATE", businessType = "SUPPLIER_PERFORMANCE")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:performance:evaluate')")
    public ApiResponse<SupplierPerformanceEvaluation> createPerformance(@Valid @RequestBody PerformanceCommand command) {
        return ApiResponse.success(service.createPerformance(command));
    }

    @PostMapping("/performance/{id}/confirm")
    @AuditedOperation(type = "CONFIRM", businessType = "SUPPLIER_PERFORMANCE", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:performance:evaluate')")
    public ApiResponse<SupplierPerformanceEvaluation> confirmPerformance(@PathVariable Long id) {
        return ApiResponse.success(service.confirmPerformance(id));
    }

    @PostMapping("/blacklists")
    @AuditedOperation(type = "CREATE", businessType = "SUPPLIER_BLACKLIST")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:performance:evaluate')")
    public ApiResponse<SupplierBlacklistRecord> createBlacklist(@Valid @RequestBody BlacklistCommand command) {
        return ApiResponse.success(service.createBlacklist(command));
    }

    @PostMapping("/blacklists/{id}/submit")
    @AuditedOperation(type = "SUBMIT", businessType = "SUPPLIER_BLACKLIST", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:performance:evaluate')")
    public ApiResponse<SupplierBlacklistRecord> submitBlacklist(@PathVariable Long id) {
        return ApiResponse.success(service.submitBlacklist(id));
    }

    @PostMapping("/blacklists/{id}/review")
    @AuditedOperation(type = "REVIEW", businessType = "SUPPLIER_BLACKLIST", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:blacklist:review')")
    public ApiResponse<SupplierBlacklistRecord> reviewBlacklist(@PathVariable Long id, @Valid @RequestBody ReviewCommand command) {
        return ApiResponse.success(service.reviewBlacklist(id, command));
    }

    @GetMapping("/events/{id}/trace")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('supplier:sourcing:query')")
    public ApiResponse<SourcingTrace> trace(@PathVariable Long id) {
        return ApiResponse.success(service.trace(id));
    }
}
