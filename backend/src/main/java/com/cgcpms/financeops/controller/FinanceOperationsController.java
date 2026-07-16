package com.cgcpms.financeops.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.financeops.dto.FinanceOperationsModels.*;
import com.cgcpms.financeops.service.FinanceAnalyticsService;
import com.cgcpms.financeops.service.FinanceIntegrationService;
import com.cgcpms.financeops.service.FinanceOperationsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/finance-operations")
@RequiredArgsConstructor
public class FinanceOperationsController {
    private final FinanceOperationsService operations;
    private final FinanceAnalyticsService analytics;
    private final FinanceIntegrationService integrations;

    @PostMapping("/budgets/adjust") @PreAuthorize("hasAuthority('finance:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> adjust(@Valid @RequestBody BudgetAdjustmentRequest r){return ApiResponse.success(operations.adjustBudget(r));}
    @PostMapping("/budgets/transfer") @PreAuthorize("hasAuthority('finance:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> transfer(@Valid @RequestBody BudgetTransferRequest r){return ApiResponse.success(operations.transferBudget(r));}
    @PostMapping("/budgets/contract-release") @PreAuthorize("hasAuthority('finance:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> release(@Valid @RequestBody ContractQuotaReleaseRequest r){return ApiResponse.success(operations.releaseContractQuota(r));}
    @GetMapping("/budgets/projects/{projectId}/versions") @PreAuthorize("hasAuthority('finance:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> versions(@PathVariable Long projectId){return ApiResponse.success(operations.budgetVersionComparison(projectId));}

    @PostMapping("/schedules") @PreAuthorize("hasAuthority('finance:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> schedule(@Valid @RequestBody PaymentScheduleRequest r){return ApiResponse.success(operations.createSchedule(r));}
    @GetMapping("/schedules") @PreAuthorize("hasAuthority('finance:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> schedules(@RequestParam(required=false)String status){return ApiResponse.success(operations.schedules(status));}
    @PostMapping("/reconciliations/run") @PreAuthorize("hasAuthority('finance:reconciliation:run') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> reconcile(@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate businessDate){return ApiResponse.success(operations.runReconciliation(businessDate));}
    @PostMapping("/alerts/generate") @PreAuthorize("hasAuthority('finance:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> generateAlerts(){return ApiResponse.success(operations.generateAlerts());}
    @GetMapping("/alerts") @PreAuthorize("hasAuthority('finance:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> alerts(@RequestParam(required=false)String status){return ApiResponse.success(operations.alerts(status));}
    @PostMapping("/alerts/{id}/handle") @PreAuthorize("hasAuthority('finance:operations:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> handleAlert(@PathVariable Long id,@Valid@RequestBody AlertHandleRequest r){operations.handleAlert(id,r);return ApiResponse.success();}
    @PostMapping("/invoices/{id}/exception") @PreAuthorize("hasAuthority('invoice:verify') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> invoiceException(@PathVariable Long id,@Valid@RequestBody InvoiceExceptionRequest r){operations.markInvoiceException(id,r);return ApiResponse.success();}
    @GetMapping("/invoices/{id}/write-off") @PreAuthorize("hasAuthority('invoice:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> writeOff(@PathVariable Long id){return ApiResponse.success(operations.invoiceWriteOffProgress(id));}
    @GetMapping(value="/audit/export",produces="text/csv") @PreAuthorize("hasAuthority('finance:audit:export') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> export(@RequestParam Long projectId,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=finance-audit.csv").contentType(new MediaType("text","csv",java.nio.charset.StandardCharsets.UTF_8)).body(operations.exportAudit(projectId,from,to));}

    @PostMapping("/snapshots/{projectId}/rebuild") @PreAuthorize("hasAuthority('finance:analytics:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> rebuild(@PathVariable Long projectId,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate date,@RequestParam(required=false)String mode){return ApiResponse.success(analytics.rebuildSnapshot(projectId,date,mode));}
    @GetMapping("/snapshots/{projectId}") @PreAuthorize("hasAuthority('finance:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> snapshots(@PathVariable Long projectId){return ApiResponse.success(analytics.snapshots(projectId));}
    @PostMapping("/ocr-reviews") @PreAuthorize("hasAuthority('finance:analytics:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> ocr(@Valid@RequestBody OcrReviewCreateRequest r){return ApiResponse.success(analytics.createOcrReview(r));}
    @PostMapping("/ocr-reviews/{id}/decision") @PreAuthorize("hasAuthority('finance:analytics:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> decideOcr(@PathVariable Long id,@Valid@RequestBody OcrReviewDecisionRequest r){return ApiResponse.success(analytics.decideOcrReview(id,r));}
    @GetMapping("/ocr-reviews") @PreAuthorize("hasAuthority('finance:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> ocrList(@RequestParam(required=false)String status){return ApiResponse.success(analytics.ocrWorkbench(status));}
    @PostMapping("/imports/preview") @PreAuthorize("hasAuthority('finance:analytics:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> previewImport(@Valid@RequestBody ImportPreviewRequest r){return ApiResponse.success(analytics.previewImport(r));}
    @PostMapping("/imports/{id}/apply") @PreAuthorize("hasAuthority('finance:analytics:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> applyImport(@PathVariable Long id){return ApiResponse.success(analytics.applyImport(id));}
    @GetMapping("/imports/{id}/rows") @PreAuthorize("hasAuthority('finance:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> importRows(@PathVariable Long id){return ApiResponse.success(analytics.importRows(id));}
    @PostMapping("/routing-rules") @PreAuthorize("hasAuthority('finance:analytics:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> routing(@Valid@RequestBody RoutingRuleRequest r){return ApiResponse.success(analytics.createRoutingRule(r));}
    @PostMapping("/routing-rules/match") @PreAuthorize("hasAuthority('finance:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> matchRouting(@Valid@RequestBody RoutingMatchRequest r){return ApiResponse.success(analytics.matchRouting(r));}
    @GetMapping("/audit/events") @PreAuthorize("hasAuthority('finance:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> audit(@RequestParam(required=false)String businessType,@RequestParam(required=false)Long businessId,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)LocalDateTime from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)LocalDateTime to,@RequestParam(required=false)String bucket){return ApiResponse.success(analytics.auditSearch(businessType,businessId,from,to,bucket));}
    @PostMapping("/audit/archive") @PreAuthorize("hasAuthority('finance:analytics:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> archiveAudit(@RequestParam@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)LocalDateTime before){return ApiResponse.success(analytics.archiveAuditBefore(before));}

    @PostMapping("/integrations/endpoints") @PreAuthorize("hasAuthority('finance:integration:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> endpoint(@Valid@RequestBody IntegrationEndpointRequest r){return ApiResponse.success(integrations.createEndpoint(r));}
    @GetMapping("/integrations/endpoints") @PreAuthorize("hasAuthority('finance:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> endpoints(){return ApiResponse.success(integrations.endpoints());}
    @PostMapping("/integrations/messages") @PreAuthorize("hasAuthority('finance:integration:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> message(@Valid@RequestBody IntegrationMessageRequest r){return ApiResponse.success(integrations.enqueue(r));}
    @PostMapping("/integrations/{endpointCode}/callbacks")
    public ApiResponse<Map<String,Object>> callback(@PathVariable String endpointCode,@RequestHeader("X-Callback-Secret")String secret,@Valid@RequestBody IntegrationCallbackRequest r){return ApiResponse.success(integrations.acceptCallback(endpointCode,secret,r));}
    @PostMapping("/integrations/endpoints/{id}/lease") @PreAuthorize("hasAuthority('finance:integration:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> lease(@PathVariable Long id,@RequestParam(defaultValue="20")int limit){return ApiResponse.success(integrations.leaseOutbound(id,limit));}
    @PostMapping("/integrations/messages/{id}/ack") @PreAuthorize("hasAuthority('finance:integration:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> ack(@PathVariable Long id,@RequestParam boolean success,@RequestBody(required=false)Map<String,Object> response,@RequestParam(required=false)String error){return ApiResponse.success(integrations.acknowledgeOutbound(id,success,response,error));}
    @PostMapping("/bank-receipts") @PreAuthorize("hasAuthority('finance:integration:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> receipt(@Valid@RequestBody BankReceiptRequest r){return ApiResponse.success(integrations.ingestBankReceipt(r));}
    @GetMapping("/bank-receipts") @PreAuthorize("hasAuthority('finance:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<Map<String,Object>>> receipts(@RequestParam(required=false)String status){return ApiResponse.success(integrations.bankReceipts(status));}
    @PostMapping("/cash-forecasts") @PreAuthorize("hasAuthority('finance:integration:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> forecast(@Valid@RequestBody CashForecastRequest r){return ApiResponse.success(integrations.createForecast(r));}
    @GetMapping("/cash-forecasts/summary") @PreAuthorize("hasAuthority('finance:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> forecastSummary(@RequestParam String scenario,@RequestParam@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,@RequestParam@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to){return ApiResponse.success(integrations.forecastSummary(scenario,from,to));}
    @PostMapping("/fund-pools") @PreAuthorize("hasAuthority('finance:pool:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> pool(@Valid@RequestBody FundPoolRequest r){return ApiResponse.success(integrations.createFundPool(r));}
    @PostMapping("/fund-pools/members") @PreAuthorize("hasAuthority('finance:pool:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> member(@Valid@RequestBody FundPoolMemberRequest r){return ApiResponse.success(integrations.addFundPoolMember(r));}
    @PostMapping("/fund-pools/transfers") @PreAuthorize("hasAuthority('finance:pool:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> poolTransfer(@Valid@RequestBody FundPoolTransferRequest r){return ApiResponse.success(integrations.transferFundPool(r));}
    @GetMapping("/fund-pools/{id}") @PreAuthorize("hasAuthority('finance:operations:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String,Object>> poolView(@PathVariable Long id){return ApiResponse.success(integrations.fundPoolView(id));}
}
