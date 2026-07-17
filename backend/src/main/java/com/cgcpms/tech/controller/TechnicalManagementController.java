package com.cgcpms.tech.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.tech.dto.TechnicalManagementModels.*;
import com.cgcpms.tech.service.TechnicalManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/technical-management")
@RequiredArgsConstructor
public class TechnicalManagementController {
    private final TechnicalManagementService service;

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:query')")
    public ApiResponse<Map<String, Object>> overview(@RequestParam Long projectId) {
        return ApiResponse.success(service.overview(projectId));
    }

    @PostMapping("/schemes")
    @AuditedOperation(type = "CREATE", businessType = "TECHNICAL_SCHEME")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:scheme:maintain')")
    public ApiResponse<Map<String, Object>> createScheme(@Valid @RequestBody SchemeCommand command) {
        return ApiResponse.success(service.createScheme(command));
    }

    @PostMapping("/schemes/{id}/submit")
    @AuditedOperation(type = "SUBMIT", businessType = "TECHNICAL_SCHEME", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:scheme:submit')")
    public ApiResponse<Map<String, Object>> submitScheme(@PathVariable Long id) {
        return ApiResponse.success(service.submitScheme(id));
    }

    @PostMapping("/drawings")
    @AuditedOperation(type = "RECEIVE_DRAWING", businessType = "TECH_DRAWING_VERSION")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:drawing:receive')")
    public ApiResponse<Map<String, Object>> receiveDrawing(@Valid @RequestBody DrawingReceiptCommand command) {
        return ApiResponse.success(service.receiveDrawing(command));
    }

    @PostMapping("/drawings/{drawingId}/versions")
    @AuditedOperation(type = "RECEIVE_VERSION", businessType = "TECH_DRAWING_VERSION")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:drawing:receive')")
    public ApiResponse<Map<String, Object>> receiveVersion(@PathVariable Long drawingId,
                                                            @Valid @RequestBody DrawingVersionCommand command) {
        return ApiResponse.success(service.receiveVersion(drawingId, command));
    }

    @PostMapping("/drawing-versions/{versionId}/reviews")
    @AuditedOperation(type = "CREATE_REVIEW", businessType = "TECH_DRAWING_REVIEW")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:drawing:review')")
    public ApiResponse<Map<String, Object>> createReview(@PathVariable Long versionId,
                                                          @Valid @RequestBody ReviewCommand command) {
        return ApiResponse.success(service.createReview(versionId, command));
    }

    @PostMapping("/reviews/{id}/confirm")
    @AuditedOperation(type = "CONFIRM_REVIEW", businessType = "TECH_DRAWING_REVIEW", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:drawing:review')")
    public ApiResponse<Map<String, Object>> confirmReview(@PathVariable Long id) {
        return ApiResponse.success(service.confirmReview(id));
    }

    @PostMapping("/reviews/{reviewId}/rfis")
    @AuditedOperation(type = "CREATE_RFI", businessType = "TECH_RFI")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:rfi:raise')")
    public ApiResponse<Map<String, Object>> createRfi(@PathVariable Long reviewId,
                                                       @Valid @RequestBody RfiCommand command) {
        return ApiResponse.success(service.createRfi(reviewId, command));
    }

    @PostMapping("/rfis/{id}/submit")
    @AuditedOperation(type = "SUBMIT_RFI", businessType = "TECH_RFI", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:rfi:raise')")
    public ApiResponse<Map<String, Object>> submitRfi(@PathVariable Long id) {
        return ApiResponse.success(service.submitRfi(id));
    }

    @PostMapping("/rfis/{id}/responses")
    @AuditedOperation(type = "RESPOND_RFI", businessType = "TECH_RFI_RESPONSE")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:rfi:respond')")
    public ApiResponse<Map<String, Object>> respondRfi(@PathVariable Long id,
                                                        @Valid @RequestBody RfiResponseCommand command) {
        return ApiResponse.success(service.respondRfi(id, command));
    }

    @PostMapping("/rfi-responses/{id}/review")
    @AuditedOperation(type = "REVIEW_RFI_RESPONSE", businessType = "TECH_RFI_RESPONSE", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:rfi:accept')")
    public ApiResponse<Map<String, Object>> reviewResponse(@PathVariable Long id,
                                                            @Valid @RequestBody ResponseReviewCommand command) {
        return ApiResponse.success(service.reviewResponse(id, command));
    }

    @PostMapping("/projects/{projectId}/disclosures")
    @AuditedOperation(type = "CREATE_DISCLOSURE", businessType = "TECH_DISCLOSURE")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:disclosure:maintain')")
    public ApiResponse<Map<String, Object>> createDisclosure(@PathVariable Long projectId,
                                                              @Valid @RequestBody DisclosureCommand command) {
        return ApiResponse.success(service.createDisclosure(projectId, command));
    }

    @PostMapping("/disclosures/{id}/confirm")
    @AuditedOperation(type = "CONFIRM_DISCLOSURE", businessType = "TECH_DISCLOSURE", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:disclosure:maintain')")
    public ApiResponse<Map<String, Object>> confirmDisclosure(@PathVariable Long id) {
        return ApiResponse.success(service.confirmDisclosure(id));
    }

    @PostMapping("/projects/{projectId}/construction-references")
    @AuditedOperation(type = "CREATE_CONSTRUCTION_REFERENCE", businessType = "TECH_CONSTRUCTION_REFERENCE")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:disclosure:maintain')")
    public ApiResponse<Map<String, Object>> createConstructionReference(@PathVariable Long projectId,
                                                                         @Valid @RequestBody ConstructionReferenceCommand command) {
        return ApiResponse.success(service.createConstructionReference(projectId, command));
    }

    @PostMapping("/projects/{projectId}/archives")
    @AuditedOperation(type = "CREATE_ARCHIVE", businessType = "TECH_ARCHIVE")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:archive:confirm')")
    public ApiResponse<Map<String, Object>> createArchive(@PathVariable Long projectId,
                                                           @Valid @RequestBody ArchiveCommand command) {
        return ApiResponse.success(service.createArchive(projectId, command));
    }

    @PostMapping("/archives/{id}/confirm")
    @AuditedOperation(type = "CONFIRM_ARCHIVE", businessType = "TECH_ARCHIVE", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:archive:confirm')")
    public ApiResponse<Map<String, Object>> confirmArchive(@PathVariable Long id) {
        return ApiResponse.success(service.confirmArchive(id));
    }

    @GetMapping("/drawings/{id}/trace")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('technical:query')")
    public ApiResponse<Map<String, Object>> trace(@PathVariable Long id) {
        return ApiResponse.success(service.trace(id));
    }
}
