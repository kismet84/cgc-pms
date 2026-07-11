package com.cgcpms.cashbook.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.cashbook.dto.CashJournalCreateRequest;
import com.cgcpms.cashbook.dto.CashJournalActionRequest;
import com.cgcpms.cashbook.dto.CashJournalQuery;
import com.cgcpms.cashbook.dto.CashJournalUpdateRequest;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.cashbook.vo.CashJournalEntryVO;
import com.cgcpms.cashbook.vo.CashJournalSummaryVO;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.common.result.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/cash-journal-entries")
@RequiredArgsConstructor
public class CashJournalController {

    private final CashJournalService cashJournalService;

    @GetMapping
    @PreAuthorize("hasAuthority('cashbook:journal:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PageResult<CashJournalEntryVO>> page(@ModelAttribute CashJournalQuery query) {
        IPage<CashJournalEntryVO> page = cashJournalService.page(query);
        return ApiResponse.success(PageResult.of(page));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('cashbook:journal:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<CashJournalSummaryVO> summary(@ModelAttribute CashJournalQuery query) {
        return ApiResponse.success(cashJournalService.summary(query));
    }

    @GetMapping("/export")
    @AuditedOperation(type = "DOWNLOAD", businessType = "CASH_JOURNAL_EXPORT")
    @PreAuthorize("hasAuthority('cashbook:journal:export') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> export(@ModelAttribute CashJournalQuery query) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename("cash-journal.csv").build());
        return ResponseEntity.ok().headers(headers).body(cashJournalService.exportCsv(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('cashbook:journal:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<CashJournalEntryVO> getById(@PathVariable Long id) {
        return ApiResponse.success(cashJournalService.getById(id));
    }

    @PostMapping
    @AuditedOperation(type = "CREATE", businessType = "CASH_JOURNAL")
    @PreAuthorize("hasAuthority('cashbook:journal:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<CashJournalEntryVO> create(@Valid @RequestBody CashJournalCreateRequest request) {
        return ApiResponse.success(cashJournalService.createManual(request));
    }

    @PutMapping("/{id}")
    @AuditedOperation(type = "UPDATE", businessType = "CASH_JOURNAL", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('cashbook:journal:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<CashJournalEntryVO> update(@PathVariable Long id,
                                                  @Valid @RequestBody CashJournalUpdateRequest request) {
        return ApiResponse.success(cashJournalService.updateDraft(id, request));
    }

    @PostMapping("/{id}/archive")
    @AuditedOperation(type = "UPDATE", businessType = "CASH_JOURNAL_ARCHIVE", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('cashbook:journal:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<CashJournalEntryVO> archive(@PathVariable Long id) {
        return ApiResponse.success(cashJournalService.archive(id));
    }

    @PostMapping("/{id}/reverse")
    @AuditedOperation(type = "UPDATE", businessType = "CASH_JOURNAL_REVERSE", businessIdExpression = "#id")
    @PreAuthorize("hasAuthority('cashbook:journal:maintain') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<CashJournalEntryVO> reverse(@PathVariable Long id,
                                                   @Valid @RequestBody CashJournalActionRequest request) {
        return ApiResponse.success(cashJournalService.reverse(id, request.getReason()));
    }

    @PostMapping("/{id}/reopen")
    @AuditedOperation(type = "UPDATE", businessType = "CASH_JOURNAL_REOPEN", businessIdExpression = "#id")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<CashJournalEntryVO> reopen(@PathVariable Long id,
                                                  @Valid @RequestBody CashJournalActionRequest request) {
        return ApiResponse.success(cashJournalService.reopen(id, request.getReason()));
    }
}
