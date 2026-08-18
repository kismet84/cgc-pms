package com.cgcpms.document.service;

import com.cgcpms.document.entity.DocumentTemplateVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Compatibility facade for the original procurement provisioning endpoints. */
@Service
@RequiredArgsConstructor
public class ProcurementSystemTemplateService {
    private final SystemDocumentTemplateService systemTemplateService;

    public DocumentTemplateVersion ensureCurrentTenantTemplate(String businessType) {
        return systemTemplateService.installVersion(businessType);
    }
}
