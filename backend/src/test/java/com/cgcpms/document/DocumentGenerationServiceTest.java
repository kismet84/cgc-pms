package com.cgcpms.document;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.entity.DocumentGeneration;
import com.cgcpms.document.entity.DocumentTemplate;
import com.cgcpms.document.entity.DocumentTemplateVersion;
import com.cgcpms.document.config.DocumentGenerationProperties;
import com.cgcpms.document.mapper.DocumentGenerationMapper;
import com.cgcpms.document.provider.DocumentDataProvider;
import com.cgcpms.document.provider.DocumentDataProviderRegistry;
import com.cgcpms.document.provider.DocumentDataSnapshot;
import com.cgcpms.document.render.DocumentRenderer;
import com.cgcpms.document.render.RenderedDocument;
import com.cgcpms.document.render.RestrictedTemplateEngine;
import com.cgcpms.document.service.DocumentGenerationPersistenceService;
import com.cgcpms.document.service.DocumentGenerationService;
import com.cgcpms.document.service.DocumentTemplateService;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.file.service.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentGenerationServiceTest {
    @Mock private DocumentGenerationMapper mapper;
    @Mock private DocumentTemplateService templateService;
    @Mock private DocumentDataProviderRegistry registry;
    @Mock private RestrictedTemplateEngine templateEngine;
    @Mock private DocumentRenderer renderer;
    @Mock private DocumentGenerationPersistenceService persistence;
    @Mock private BusinessObjectAuthorizer authorizer;
    @Mock private ObjectProvider<FileService> fileServiceProvider;
    @Mock private DocumentDataProvider provider;

    private DocumentGenerationService service;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        properties.setEnabled(true);
        properties.setPaymentEnabled(true);
        service = new DocumentGenerationService(mapper, templateService, registry, templateEngine, renderer,
                persistence, authorizer, new ObjectMapper(), fileServiceProvider, properties);
        when(registry.require("PAYMENT")).thenReturn(provider);
    }

    private void stubFormalGeneration() {
        when(provider.load(88L)).thenReturn(new DocumentDataSnapshot("payment.v1",
                Map.of("payment", Map.of("applyCode", "PAY-88"))));
        DocumentTemplateVersion version = new DocumentTemplateVersion();
        version.setId(701L);
        version.setTemplateId(700L);
        version.setSchemaVersion("payment.v1");
        version.setTemplateContent("<html>{{payment.applyCode}}</html>");
        when(templateService.requireDefaultVersion("PAYMENT")).thenReturn(version);
        when(templateEngine.render(any(), any())).thenReturn("<html>PAY-88</html>");
        when(renderer.rendererId()).thenReturn("openhtmltopdf");
        when(renderer.rendererVersion()).thenReturn("1.1.40/pdfbox-3.0.8");
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void persistsAuditableSuccessStateTransitions() {
        stubFormalGeneration();
        RenderedDocument rendered = new RenderedDocument("%PDF-test".getBytes(), "a".repeat(64), 1);
        when(renderer.render(any())).thenReturn(rendered);
        DocumentGeneration succeeded = new DocumentGeneration();
        succeeded.setId(999L);
        succeeded.setStatus("SUCCEEDED");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null, succeeded);

        DocumentGeneration result = service.generate("PAYMENT", 88L, "payment:88:approved:v1", null);

        assertEquals("SUCCEEDED", result.getStatus());
        verify(authorizer).checkGeneratedDocumentAccess("PAYMENT", 88L);
        verify(persistence).start(any(DocumentGeneration.class));
        verify(persistence).markRendering(anyLong(), eq(TestUserContext.TENANT_0));
        verify(persistence).succeed(any(DocumentGeneration.class), eq(rendered));
        verify(persistence, never()).fail(anyLong(), anyLong(), any());
    }

    @Test
    void persistsStableFailureCodeWhenRendererRejectsOutput() {
        stubFormalGeneration();
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
        doThrow(new BusinessException("DOCUMENT_RENDER_TIMEOUT", "timeout")).when(renderer).render(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.generate("PAYMENT", 88L, "payment:88:approved:v2", null));

        assertEquals("DOCUMENT_RENDER_TIMEOUT", error.getCode());
        verify(persistence).fail(anyLong(), eq(TestUserContext.TENANT_0), eq("DOCUMENT_RENDER_TIMEOUT"));
        verify(persistence, never()).succeed(any(), any());
    }

    @Test
    void previewingSavedDraftUsesBusinessAuthorizationAndDoesNotPersistGeneration() {
        DocumentTemplate template = new DocumentTemplate();
        template.setBusinessType("PAYMENT");
        DocumentTemplateVersion version = new DocumentTemplateVersion();
        version.setId(701L);
        version.setSchemaVersion("payment.v1");
        version.setTemplateContent("<html>{{payment.applyCode}}</html>");
        when(templateService.requirePreviewVersionContext(701L))
                .thenReturn(new DocumentTemplateService.TemplateVersionContext(template, version));
        when(provider.loadPreview(88L)).thenReturn(new DocumentDataSnapshot("payment.v1",
                Map.of("payment", Map.of("applyCode", "PAY-88"))));
        when(templateEngine.render(any(), any())).thenReturn("<html>PAY-88</html>");
        RenderedDocument rendered = new RenderedDocument("%PDF-preview".getBytes(), "b".repeat(64), 1);
        when(renderer.render(any())).thenReturn(rendered);

        RenderedDocument result = service.previewTemplateVersion(701L, 88L);

        assertEquals(rendered, result);
        verify(authorizer).checkGeneratedDocumentAccess("PAYMENT", 88L);
        verify(provider).loadPreview(88L);
        verify(renderer).render(contains("预览件 非正式文件"));
        verify(persistence, never()).start(any());
    }
}
