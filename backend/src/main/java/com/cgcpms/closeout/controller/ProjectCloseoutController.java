package com.cgcpms.closeout.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.closeout.dto.ProjectCloseoutModels.*;
import com.cgcpms.closeout.service.ProjectCloseoutService;
import com.cgcpms.common.result.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/project-closeouts")
@RequiredArgsConstructor
public class ProjectCloseoutController {
    private final ProjectCloseoutService service;

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:query')")
    public ApiResponse<Map<String, Object>> overview(@RequestParam Long projectId) {
        return ApiResponse.success(service.overview(projectId));
    }

    @PostMapping
    @AuditedOperation(type = "INITIATE", businessType = "PROJECT_CLOSEOUT")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:initiate')")
    public ApiResponse<Map<String, Object>> initiate(@Valid @RequestBody InitiateCommand command) {
        return ApiResponse.success(service.initiate(command));
    }

    @PostMapping("/{id}/section-acceptances")
    @AuditedOperation(type = "CREATE_SECTION_ACCEPTANCE", businessType = "CLOSEOUT_SECTION_ACCEPTANCE")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:section:maintain')")
    public ApiResponse<Map<String, Object>> createSectionAcceptance(@PathVariable Long id,
            @Valid @RequestBody SectionAcceptanceCommand command) {
        return ApiResponse.success(service.createSectionAcceptance(id, command));
    }

    @PostMapping("/section-acceptances/{id}/confirm")
    @AuditedOperation(type = "CONFIRM_SECTION_ACCEPTANCE", businessType = "CLOSEOUT_SECTION_ACCEPTANCE", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:section:maintain')")
    public ApiResponse<Map<String, Object>> confirmSectionAcceptance(@PathVariable Long id) {
        return ApiResponse.success(service.confirmSectionAcceptance(id));
    }

    @PostMapping("/{id}/final-acceptance")
    @AuditedOperation(type = "CREATE_FINAL_ACCEPTANCE", businessType = "CLOSEOUT_FINAL_ACCEPTANCE")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:acceptance:submit')")
    public ApiResponse<Map<String, Object>> createFinalAcceptance(@PathVariable Long id,
            @Valid @RequestBody FinalAcceptanceCommand command) {
        return ApiResponse.success(service.createFinalAcceptance(id, command));
    }

    @PostMapping("/final-acceptances/{id}/submit")
    @AuditedOperation(type = "SUBMIT_FINAL_ACCEPTANCE", businessType = "CLOSEOUT_FINAL_ACCEPTANCE", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:acceptance:submit')")
    public ApiResponse<Map<String, Object>> submitFinalAcceptance(@PathVariable Long id) {
        return ApiResponse.success(service.submitFinalAcceptance(id));
    }

    @PostMapping("/{id}/final-settlement")
    @AuditedOperation(type = "BIND_FINAL_SETTLEMENT", businessType = "PROJECT_CLOSEOUT", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:settlement:bind')")
    public ApiResponse<Map<String, Object>> bindFinalSettlement(@PathVariable Long id,
            @Valid @RequestBody SettlementBindingCommand command) {
        return ApiResponse.success(service.bindFinalSettlement(id, command));
    }

    @PostMapping("/{id}/verify-tail-collection")
    @AuditedOperation(type = "VERIFY_TAIL_COLLECTION", businessType = "PROJECT_CLOSEOUT", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:collection:verify')")
    public ApiResponse<Map<String, Object>> verifyTailCollection(@PathVariable Long id) {
        return ApiResponse.success(service.verifyTailCollection(id));
    }

    @PostMapping("/{id}/warranties")
    @AuditedOperation(type = "REGISTER_WARRANTY", businessType = "CLOSEOUT_WARRANTY")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:warranty:maintain')")
    public ApiResponse<Map<String, Object>> registerWarranty(@PathVariable Long id,
            @Valid @RequestBody WarrantyCommand command) {
        return ApiResponse.success(service.registerWarranty(id, command));
    }

    @PostMapping("/warranties/{id}/defects")
    @AuditedOperation(type = "CREATE_DEFECT", businessType = "CLOSEOUT_DEFECT")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:defect:maintain')")
    public ApiResponse<Map<String, Object>> createDefect(@PathVariable Long id,
            @Valid @RequestBody DefectCommand command) {
        return ApiResponse.success(service.createDefect(id, command));
    }

    @PostMapping("/defects/{id}/rectify")
    @AuditedOperation(type = "RECTIFY_DEFECT", businessType = "CLOSEOUT_DEFECT", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:defect:maintain')")
    public ApiResponse<Map<String, Object>> rectifyDefect(@PathVariable Long id,
            @Valid @RequestBody RectificationCommand command) {
        return ApiResponse.success(service.rectifyDefect(id, command));
    }

    @PostMapping("/defects/{id}/verify")
    @AuditedOperation(type = "VERIFY_DEFECT", businessType = "CLOSEOUT_DEFECT", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:defect:verify')")
    public ApiResponse<Map<String, Object>> verifyDefect(@PathVariable Long id,
            @Valid @RequestBody DefectVerificationCommand command) {
        return ApiResponse.success(service.verifyDefect(id, command));
    }

    @PostMapping("/warranties/{id}/release")
    @AuditedOperation(type = "RELEASE_WARRANTY", businessType = "CLOSEOUT_WARRANTY", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:warranty:maintain')")
    public ApiResponse<Map<String, Object>> releaseWarranty(@PathVariable Long id) {
        return ApiResponse.success(service.releaseWarranty(id));
    }

    @PostMapping("/{id}/archive-transfer")
    @AuditedOperation(type = "CREATE_ARCHIVE_TRANSFER", businessType = "CLOSEOUT_ARCHIVE_TRANSFER")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:archive:maintain')")
    public ApiResponse<Map<String, Object>> createArchiveTransfer(@PathVariable Long id,
            @Valid @RequestBody ArchiveTransferCommand command) {
        return ApiResponse.success(service.createArchiveTransfer(id, command));
    }

    @PostMapping("/archive-transfers/{id}/accept")
    @AuditedOperation(type = "ACCEPT_ARCHIVE_TRANSFER", businessType = "CLOSEOUT_ARCHIVE_TRANSFER", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:archive:maintain')")
    public ApiResponse<Map<String, Object>> acceptArchiveTransfer(@PathVariable Long id) {
        return ApiResponse.success(service.acceptArchiveTransfer(id));
    }

    @PostMapping("/{id}/close")
    @AuditedOperation(type = "CLOSE_PROJECT", businessType = "PROJECT_CLOSEOUT", businessIdExpression = "#id")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:close')")
    public ApiResponse<Map<String, Object>> closeProject(@PathVariable Long id,
            @Valid @RequestBody CloseProjectCommand command) {
        return ApiResponse.success(service.closeProject(id, command));
    }

    @GetMapping("/{id}/trace")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('closeout:query')")
    public ApiResponse<Map<String, Object>> trace(@PathVariable Long id) {
        return ApiResponse.success(service.trace(id));
    }
}
