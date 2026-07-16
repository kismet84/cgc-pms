package com.cgcpms.revenue.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.revenue.dto.RevenueOperationsModels.*;
import com.cgcpms.revenue.service.RevenueOperationsService;
import com.cgcpms.revenue.service.RevenueAdvancedService;
import com.cgcpms.audit.annotation.AuditedOperation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;

@RestController
@RequestMapping("/revenue-operations")
@RequiredArgsConstructor
public class RevenueOperationsController {
    private final RevenueOperationsService service;
    private final RevenueAdvancedService advanced;

    @PostMapping("/settlements")
    @AuditedOperation(type="CREATE",businessType="OWNER_SETTLEMENT",businessIdExpression="#request.projectId")
    @PreAuthorize("hasAuthority('revenue:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> createSettlement(@Valid @RequestBody OwnerSettlementRequest request) { return ApiResponse.success(service.createSettlement(request)); }

    @PostMapping("/settlements/{id}/submit")
    @AuditedOperation(type="SUBMIT",businessType="OWNER_SETTLEMENT",businessIdExpression="#id")
    @PreAuthorize("hasAuthority('revenue:settlement:submit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> submitSettlement(@PathVariable Long id) { return ApiResponse.success(service.submitSettlement(id)); }

    @GetMapping("/settlements")
    @PreAuthorize("hasAuthority('revenue:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> settlements(@RequestParam(required=false) Long projectId,@RequestParam(required=false) String status) { return ApiResponse.success(service.settlements(projectId,status)); }

    @GetMapping("/receivables")
    @PreAuthorize("hasAuthority('revenue:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> receivables(@RequestParam(required=false) Long projectId,@RequestParam(required=false) String status) { return ApiResponse.success(service.receivables(projectId,status)); }

    @PostMapping("/sales-invoices")
    @AuditedOperation(type="CREATE",businessType="SALES_INVOICE",businessIdExpression="#request.projectId")
    @PreAuthorize("hasAuthority('revenue:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> createInvoice(@Valid @RequestBody SalesInvoiceRequest request) { return ApiResponse.success(service.createSalesInvoice(request)); }

    @GetMapping("/sales-invoices")
    @PreAuthorize("hasAuthority('revenue:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> invoices(@RequestParam(required=false) Long projectId) { return ApiResponse.success(service.invoices(projectId)); }

    @PostMapping("/collections")
    @AuditedOperation(type="CREATE",businessType="COLLECTION_RECORD",businessIdExpression="#request.projectId")
    @PreAuthorize("hasAuthority('revenue:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> createCollection(@Valid @RequestBody CollectionRequest request) { return ApiResponse.success(service.createCollection(request)); }

    @GetMapping("/collections")
    @PreAuthorize("hasAuthority('revenue:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> collections(@RequestParam(required=false) Long projectId,@RequestParam(required=false) String status) { return ApiResponse.success(service.collections(projectId,status)); }

    @GetMapping("/dashboard/{projectId}")
    @PreAuthorize("hasAuthority('revenue:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> dashboard(@PathVariable Long projectId) { return ApiResponse.success(service.dashboard(projectId)); }

    @GetMapping("/trace/cash-journals/{journalId}")
    @PreAuthorize("hasAuthority('revenue:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> trace(@PathVariable Long journalId) { return ApiResponse.success(service.traceByCashJournal(journalId)); }

    @PostMapping("/receivables/{id}/credit")
    @AuditedOperation(type="ADJUST",businessType="ACCOUNT_RECEIVABLE",businessIdExpression="#id")
    @PreAuthorize("hasAuthority('revenue:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> credit(@PathVariable Long id,@Valid@RequestBody ReceivableCreditRequest request){return ApiResponse.success(advanced.creditReceivable(id,request));}

    @PostMapping("/collections/{id}/reverse")
    @AuditedOperation(type="REVERSE",businessType="COLLECTION_RECORD",businessIdExpression="#id")
    @PreAuthorize("hasAuthority('revenue:collection:reverse') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> reverse(@PathVariable Long id,@Valid@RequestBody CollectionReverseRequest request){return ApiResponse.success(advanced.reverseCollection(id,request));}

    @PostMapping("/schedules")
    @AuditedOperation(type="CREATE",businessType="COLLECTION_SCHEDULE",businessIdExpression="#request.projectId")
    @PreAuthorize("hasAuthority('revenue:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> schedule(@Valid@RequestBody CollectionScheduleRequest request){return ApiResponse.success(advanced.createSchedule(request));}
    @GetMapping("/schedules")
    @PreAuthorize("hasAuthority('revenue:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> schedules(@RequestParam(required=false)String status){return ApiResponse.success(advanced.schedules(status));}
    @GetMapping("/aging/{projectId}")
    @PreAuthorize("hasAuthority('revenue:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> aging(@PathVariable Long projectId){return ApiResponse.success(advanced.aging(projectId));}
    @PostMapping("/reconciliations/run")
    @AuditedOperation(type="RECONCILE",businessType="REVENUE_COLLECTION")
    @PreAuthorize("hasAuthority('revenue:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> reconcile(@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate date){return ApiResponse.success(advanced.reconcile(date));}

    @PostMapping("/snapshots/{projectId}/rebuild")
    @AuditedOperation(type="REBUILD",businessType="REVENUE_DASHBOARD",businessIdExpression="#projectId")
    @PreAuthorize("hasAuthority('revenue:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> snapshot(@PathVariable Long projectId,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate date,@RequestParam(required=false)String mode){return ApiResponse.success(advanced.rebuildSnapshot(projectId,date,mode));}
    @PostMapping("/sales-invoice-reviews")
    @AuditedOperation(type="CREATE",businessType="SALES_INVOICE_REVIEW",businessIdExpression="#request.invoiceId")
    @PreAuthorize("hasAuthority('revenue:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> review(@Valid@RequestBody SalesInvoiceReviewRequest request){return ApiResponse.success(advanced.createInvoiceReview(request));}
    @PostMapping("/sales-invoice-reviews/{id}/decision")
    @AuditedOperation(type="REVIEW",businessType="SALES_INVOICE_REVIEW",businessIdExpression="#id")
    @PreAuthorize("hasAuthority('revenue:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> decide(@PathVariable Long id,@Valid@RequestBody ReviewDecisionRequest request){return ApiResponse.success(advanced.decideReview(id,request));}
    @PostMapping("/imports/preview")
    @AuditedOperation(type="IMPORT_PREVIEW",businessType="REVENUE_COLLECTION",businessIdExpression="#request.projectId")
    @PreAuthorize("hasAuthority('revenue:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> preview(@Valid@RequestBody RevenueImportRequest request){return ApiResponse.success(advanced.previewImport(request));}

    @PostMapping("/forecasts")
    @AuditedOperation(type="CREATE",businessType="COLLECTION_FORECAST",businessIdExpression="#projectId")
    @PreAuthorize("hasAuthority('revenue:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> forecast(@RequestParam Long projectId,@RequestParam(required=false)Long contractId,@RequestParam@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate date,@RequestParam String scenario,@RequestParam BigDecimal amount,@RequestParam(required=false)BigDecimal confidence,@RequestParam String sourceType,@RequestParam(required=false)Long sourceId){return ApiResponse.success(advanced.createForecast(projectId,contractId,date,scenario,amount,confidence,sourceType,sourceId));}
    @PostMapping("/customers/{customerId}/credit/refresh")
    @AuditedOperation(type="REBUILD",businessType="CUSTOMER_CREDIT",businessIdExpression="#customerId")
    @PreAuthorize("hasAuthority('revenue:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> creditProfile(@PathVariable Long customerId){return ApiResponse.success(advanced.refreshCustomerCredit(customerId));}
    @PostMapping("/integrations/messages")
    @AuditedOperation(type="SYNC",businessType="REVENUE_INTEGRATION",businessIdExpression="#request.businessId")
    @PreAuthorize("hasAuthority('finance:integration:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> integration(@Valid@RequestBody RevenueIntegrationRequest request){return ApiResponse.success(advanced.enqueueIntegration(request));}
    @GetMapping("/audit/events")
    @PreAuthorize("hasAuthority('revenue:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> audit(@RequestParam(required=false)String businessType,@RequestParam(required=false)Long businessId){return ApiResponse.success(advanced.auditEvents(businessType,businessId));}
    @GetMapping(value="/audit/export",produces="text/csv")
    @PreAuthorize("hasAuthority('revenue:audit:export') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> export(@RequestParam Long projectId){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=revenue-audit.csv").contentType(new MediaType("text","csv",java.nio.charset.StandardCharsets.UTF_8)).body(advanced.exportAudit(projectId));}
}
