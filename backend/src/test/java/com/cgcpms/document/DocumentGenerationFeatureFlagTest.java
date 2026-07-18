package com.cgcpms.document;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.config.DocumentGenerationProperties;
import com.cgcpms.document.mapper.DocumentGenerationMapper;
import com.cgcpms.document.provider.DocumentDataProviderRegistry;
import com.cgcpms.document.render.DocumentRenderer;
import com.cgcpms.document.render.RestrictedTemplateEngine;
import com.cgcpms.document.service.DocumentGenerationPersistenceService;
import com.cgcpms.document.service.DocumentGenerationService;
import com.cgcpms.document.service.DocumentTemplateService;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.file.service.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DocumentGenerationFeatureFlagTest {
    @Test
    void rejectsGenerationBeforeDataReadWhenGlobalFeatureFlagIsOff() {
        DocumentGenerationMapper mapper = mock(DocumentGenerationMapper.class);
        DocumentTemplateService templateService = mock(DocumentTemplateService.class);
        DocumentDataProviderRegistry registry = mock(DocumentDataProviderRegistry.class);
        RestrictedTemplateEngine templateEngine = mock(RestrictedTemplateEngine.class);
        DocumentRenderer renderer = mock(DocumentRenderer.class);
        DocumentGenerationPersistenceService persistence = mock(DocumentGenerationPersistenceService.class);
        BusinessObjectAuthorizer authorizer = mock(BusinessObjectAuthorizer.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<FileService> fileServiceProvider = mock(ObjectProvider.class);
        DocumentGenerationService service = new DocumentGenerationService(mapper, templateService, registry,
                templateEngine, renderer, persistence, authorizer, new ObjectMapper(), fileServiceProvider,
                new DocumentGenerationProperties());
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        try {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.generate("PAYMENT", 88L, "payment:88:disabled", null));

            assertEquals("DOCUMENT_GENERATION_DISABLED", error.getCode());
            verify(registry, never()).require("PAYMENT");
            verify(persistence, never()).start(org.mockito.ArgumentMatchers.any());
        } finally {
            TestUserContext.clear();
        }
    }

    @Test
    void rejectsPaymentGenerationWhenBusinessFeatureFlagIsOff() {
        DocumentGenerationMapper mapper = mock(DocumentGenerationMapper.class);
        DocumentTemplateService templateService = mock(DocumentTemplateService.class);
        DocumentDataProviderRegistry registry = mock(DocumentDataProviderRegistry.class);
        RestrictedTemplateEngine templateEngine = mock(RestrictedTemplateEngine.class);
        DocumentRenderer renderer = mock(DocumentRenderer.class);
        DocumentGenerationPersistenceService persistence = mock(DocumentGenerationPersistenceService.class);
        BusinessObjectAuthorizer authorizer = mock(BusinessObjectAuthorizer.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<FileService> fileServiceProvider = mock(ObjectProvider.class);
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        properties.setEnabled(true);
        DocumentGenerationService service = new DocumentGenerationService(mapper, templateService, registry,
                templateEngine, renderer, persistence, authorizer, new ObjectMapper(), fileServiceProvider, properties);
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        try {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.generate("PAYMENT", 88L, "payment:88:disabled", null));

            assertEquals("DOCUMENT_PAYMENT_GENERATION_DISABLED", error.getCode());
            verify(registry, never()).require("PAYMENT");
            verify(persistence, never()).start(org.mockito.ArgumentMatchers.any());
        } finally {
            TestUserContext.clear();
        }
    }
}
