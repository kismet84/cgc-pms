package com.cgcpms.bid.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.bid.dto.BidDocumentCreateRequest;
import com.cgcpms.bid.dto.BidDocumentVoidRequest;
import com.cgcpms.bid.entity.BidDocumentVersion;
import com.cgcpms.bid.service.BidDocumentVersionService;
import com.cgcpms.common.result.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bid-cost/{bidCostId}/documents")
@RequiredArgsConstructor
public class BidDocumentVersionController {

    private final BidDocumentVersionService service;

    @GetMapping
    @PreAuthorize("hasAuthority('bid:query') or hasRole('SUPER_ADMIN')")
    public ApiResponse<List<BidDocumentVersion>> list(@PathVariable Long bidCostId) {
        return ApiResponse.success(service.list(bidCostId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('bid:file:manage') or hasRole('SUPER_ADMIN')")
    @AuditedOperation(type = "UPLOAD", businessType = "BID_DOCUMENT", businessIdExpression = "#bidCostId")
    public ApiResponse<BidDocumentVersion> append(@PathVariable Long bidCostId,
                                                  @Valid @RequestBody BidDocumentCreateRequest request) {
        return ApiResponse.success(service.append(bidCostId, request));
    }

    @PostMapping("/{versionId}/finalize")
    @PreAuthorize("hasAuthority('bid:file:manage') or hasRole('SUPER_ADMIN')")
    @AuditedOperation(type = "UPDATE", businessType = "BID_DOCUMENT", businessIdExpression = "#versionId")
    public ApiResponse<Void> finalizeVersion(@PathVariable Long bidCostId, @PathVariable Long versionId) {
        service.finalizeVersion(bidCostId, versionId);
        return ApiResponse.success();
    }

    @PostMapping("/{versionId}/void")
    @PreAuthorize("hasAuthority('bid:file:manage') or hasRole('SUPER_ADMIN')")
    @AuditedOperation(type = "UPDATE", businessType = "BID_DOCUMENT", businessIdExpression = "#versionId")
    public ApiResponse<Void> voidVersion(@PathVariable Long bidCostId, @PathVariable Long versionId,
                                         @Valid @RequestBody BidDocumentVoidRequest request) {
        service.voidVersion(bidCostId, versionId, request.reason());
        return ApiResponse.success();
    }
}
