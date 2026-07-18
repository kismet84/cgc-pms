package com.cgcpms.document.controller;

import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.document.dto.DocumentTemplateCreateRequest;
import com.cgcpms.document.dto.DocumentTemplateDraftRequest;
import com.cgcpms.document.dto.DocumentTemplateValidationRequest;
import com.cgcpms.document.entity.DocumentTemplateVersion;
import com.cgcpms.document.render.RenderedDocument;
import com.cgcpms.document.service.DocumentGenerationService;
import com.cgcpms.document.service.DocumentTemplateService;
import com.cgcpms.document.service.PaymentSystemTemplateService;
import com.cgcpms.document.service.SettlementSystemTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/document-templates")
@RequiredArgsConstructor
public class DocumentTemplateController {
    private final DocumentTemplateService service;
    private final DocumentGenerationService generationService;
    private final PaymentSystemTemplateService paymentSystemTemplateService;
    private final SettlementSystemTemplateService settlementSystemTemplateService;

    @PostMapping
    @AuditedOperation(type = "CREATE", businessType = "DOCUMENT_TEMPLATE", businessIdExpression = "0")
    @PreAuthorize("hasAuthority('document:template:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<DocumentTemplateVersion> create(@Valid @RequestBody DocumentTemplateCreateRequest request) {
        return ApiResponse.success(service.create(request.templateCode(), request.templateName(), request.businessType(),
                draft(request.schemaVersion(), request.templateContent(), request.fieldManifest(), request.remark())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('document:template:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<java.util.List<DocumentTemplateService.TemplateSummary>> list(
            @RequestParam(required = false) String businessType) {
        return ApiResponse.success(service.listTemplates(businessType));
    }

    @GetMapping("/{templateId}")
    @PreAuthorize("hasAuthority('document:template:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<DocumentTemplateService.TemplateDetail> getTemplate(@PathVariable Long templateId) {
        return ApiResponse.success(service.getTemplateDetail(templateId));
    }

    @GetMapping("/catalog")
    @PreAuthorize("hasAuthority('document:template:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<com.cgcpms.document.catalog.DocumentTemplateFieldCatalog.Catalog> catalog(
            @RequestParam String businessType) {
        return ApiResponse.success(service.getFieldCatalog(businessType));
    }

    @PostMapping("/{templateId}/versions")
    @AuditedOperation(type = "CREATE_VERSION", businessType = "DOCUMENT_TEMPLATE", businessIdExpression = "#templateId")
    @PreAuthorize("hasAuthority('document:template:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<DocumentTemplateVersion> createVersion(@PathVariable Long templateId,
                                                               @Valid @RequestBody DocumentTemplateDraftRequest request) {
        return ApiResponse.success(service.createNextDraft(templateId, draft(request)));
    }

    @PostMapping("/{templateId}/versions/{sourceVersionId}/copy")
    @AuditedOperation(type = "COPY_VERSION", businessType = "DOCUMENT_TEMPLATE", businessIdExpression = "#templateId")
    @PreAuthorize("hasAuthority('document:template:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<DocumentTemplateVersion> copyVersion(@PathVariable Long templateId,
                                                            @PathVariable Long sourceVersionId) {
        return ApiResponse.success(service.copyVersion(templateId, sourceVersionId));
    }

    @PutMapping("/versions/{versionId}")
    @AuditedOperation(type = "UPDATE_DRAFT", businessType = "DOCUMENT_TEMPLATE_VERSION", businessIdExpression = "#versionId")
    @PreAuthorize("hasAuthority('document:template:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> updateVersion(@PathVariable Long versionId,
                                           @Valid @RequestBody DocumentTemplateDraftRequest request) {
        service.updateDraft(versionId, draft(request));
        return ApiResponse.success();
    }

    @PostMapping("/validate")
    @AuditedOperation(type = "VALIDATE", businessType = "DOCUMENT_TEMPLATE", businessIdExpression = "0")
    @PreAuthorize("hasAuthority('document:template:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<DocumentTemplateService.TemplateValidationResult> validate(
            @Valid @RequestBody DocumentTemplateValidationRequest request) {
        return ApiResponse.success(service.validate(request.businessType(),
                draft(request.schemaVersion(), request.templateContent(), request.fieldManifest(), request.remark())));
    }

    @PostMapping("/import")
    @AuditedOperation(type = "IMPORT", businessType = "DOCUMENT_TEMPLATE", businessIdExpression = "0")
    @PreAuthorize("hasAuthority('document:template:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<DocumentTemplateVersion> importTemplate(@Valid @RequestBody DocumentTemplateCreateRequest request) {
        return ApiResponse.success(service.create(request.templateCode(), request.templateName(), request.businessType(),
                draft(request.schemaVersion(), request.templateContent(), request.fieldManifest(), request.remark())));
    }

    @GetMapping("/versions/{versionId}/export")
    @AuditedOperation(type = "EXPORT", businessType = "DOCUMENT_TEMPLATE_VERSION", businessIdExpression = "#versionId")
    @PreAuthorize("hasAuthority('document:template:query') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<DocumentTemplateService.TemplateExport> exportVersion(@PathVariable Long versionId) {
        return ApiResponse.success(service.exportVersion(versionId));
    }

    @PostMapping(value = "/versions/{versionId}/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    @AuditedOperation(type = "PREVIEW", businessType = "DOCUMENT_TEMPLATE_VERSION", businessIdExpression = "#versionId")
    @PreAuthorize("(hasAuthority('document:template:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')) and "
            + "(hasAuthority('document:generate') or hasAnyRole('ADMIN','SUPER_ADMIN'))")
    public ResponseEntity<byte[]> previewVersion(@PathVariable Long versionId, @RequestParam Long businessId) {
        RenderedDocument rendered = generationService.previewTemplateVersion(versionId, businessId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=template-preview.pdf")
                .body(rendered.content());
    }

    @PostMapping("/versions/{versionId}/publish")
    @AuditedOperation(type = "PUBLISH", businessType = "DOCUMENT_TEMPLATE_VERSION", businessIdExpression = "#versionId")
    @PreAuthorize("hasAuthority('document:template:publish') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<DocumentTemplateVersion> publish(@PathVariable Long versionId) {
        return ApiResponse.success(service.publish(versionId));
    }

    @PostMapping("/versions/{versionId}/disable")
    @AuditedOperation(type = "DISABLE", businessType = "DOCUMENT_TEMPLATE_VERSION", businessIdExpression = "#versionId")
    @PreAuthorize("hasAuthority('document:template:publish') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> disable(@PathVariable Long versionId) {
        service.disablePublishedVersion(versionId);
        return ApiResponse.success();
    }

    @PutMapping("/versions/{versionId}/default")
    @AuditedOperation(type = "BIND_DEFAULT", businessType = "DOCUMENT_TEMPLATE_VERSION", businessIdExpression = "#versionId")
    @PreAuthorize("hasAuthority('document:template:publish') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Void> bindDefault(@PathVariable Long versionId,
                                         @RequestParam(required = false) Integer expectedLockVersion) {
        service.bindDefault(versionId, expectedLockVersion);
        return ApiResponse.success();
    }

    @PostMapping("/system/payment")
    @AuditedOperation(type = "PROVISION_SYSTEM_PAYMENT", businessType = "DOCUMENT_TEMPLATE", businessIdExpression = "0")
    @PreAuthorize("hasAuthority('document:template:publish') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<DocumentTemplateVersion> provisionPaymentSystemTemplate() {
        return ApiResponse.success(paymentSystemTemplateService.ensureCurrentTenantTemplate());
    }

    @PostMapping("/system/settlement")
    @AuditedOperation(type = "PROVISION_SYSTEM_SETTLEMENT", businessType = "DOCUMENT_TEMPLATE", businessIdExpression = "0")
    @PreAuthorize("hasAuthority('document:template:publish') or hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<DocumentTemplateVersion> provisionSettlementSystemTemplate() {
        return ApiResponse.success(settlementSystemTemplateService.ensureCurrentTenantTemplate());
    }

    private DocumentTemplateService.DraftCommand draft(DocumentTemplateDraftRequest request) {
        return draft(request.schemaVersion(), request.templateContent(), request.fieldManifest(), request.remark());
    }

    private DocumentTemplateService.DraftCommand draft(String schemaVersion, String content, String manifest, String remark) {
        return new DocumentTemplateService.DraftCommand(schemaVersion, content, manifest, remark);
    }
}
