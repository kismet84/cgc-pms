package com.cgcpms.document.service;

import com.cgcpms.document.entity.DocumentTemplateVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Compatibility facade for the original payment provisioning endpoint. */
@Service
@RequiredArgsConstructor
public class PaymentSystemTemplateService {
    public static final String TEMPLATE_CODE = "SYSTEM_PAYMENT_APPLICATION_V1";
    private final SystemDocumentTemplateService systemTemplateService;

    public DocumentTemplateVersion ensureCurrentTenantTemplate() {
        return systemTemplateService.installVersion("PAYMENT");
    }
}
