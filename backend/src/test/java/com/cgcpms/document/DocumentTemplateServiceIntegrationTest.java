package com.cgcpms.document;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.entity.DocumentTemplateVersion;
import com.cgcpms.document.service.DocumentTemplateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "minio.enabled=false")
@ActiveProfiles("local")
@Transactional
class DocumentTemplateServiceIntegrationTest {
    @Autowired
    private DocumentTemplateService service;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void publishedVersionIsImmutableAndCanBecomeDefault() {
        DocumentTemplateService.DraftCommand command = new DocumentTemplateService.DraftCommand(
                "payment.v1",
                "<html><body>付款申请 {{payment.applyCode}}</body></html>",
                "[\"payment.applyCode\"]",
                "integration test");
        DocumentTemplateVersion draft = service.create(
                "PAYMENT_TEST_TEMPLATE", "付款测试模板", "PAYMENT", command);

        DocumentTemplateVersion published = service.publish(draft.getId());
        service.bindDefault(published.getId(), 0);

        assertEquals("PUBLISHED", published.getStatus());
        assertNotNull(published.getPublishedAt());
        assertEquals(published.getId(), service.requireDefaultVersion("PAYMENT").getId());
        BusinessException immutable = assertThrows(BusinessException.class,
                () -> service.updateDraft(published.getId(), command));
        assertEquals("DOCUMENT_TEMPLATE_VERSION_IMMUTABLE", immutable.getCode());
    }

    @Test
    void fieldCatalogBlocksUnknownAndOutOfLoopFieldsBeforePublish() {
        DocumentTemplateService.DraftCommand unknownField = new DocumentTemplateService.DraftCommand(
                "payment.v1", "<html><body>{{payment.unknownField}}</body></html>",
                "[\"payment.unknownField\"]", "unknown field");
        BusinessException unavailable = assertThrows(BusinessException.class,
                () -> service.create("PAYMENT_UNKNOWN_FIELD", "未知字段模板", "PAYMENT", unknownField));
        assertEquals("DOCUMENT_FIELD_UNAVAILABLE", unavailable.getCode());

        DocumentTemplateService.DraftCommand outOfLoop = new DocumentTemplateService.DraftCommand(
                "payment.v1", "<html><body>{{sources.amount}}</body></html>",
                "[\"sources.amount\"]", "collection context");
        BusinessException context = assertThrows(BusinessException.class,
                () -> service.create("PAYMENT_COLLECTION_CONTEXT", "集合上下文模板", "PAYMENT", outOfLoop));
        assertEquals("DOCUMENT_FIELD_CONTEXT_INVALID", context.getCode());
    }

    @Test
    void catalogValidationCopyAndExportStayWithinTheSameTenantTemplate() {
        DocumentTemplateService.DraftCommand command = new DocumentTemplateService.DraftCommand(
                "payment.v1", "<html><body>{{payment.applyCode}}</body></html>",
                "[\"payment.applyCode\"]", "catalog test");
        DocumentTemplateVersion first = service.create("PAYMENT_CATALOG_TEMPLATE", "字段目录模板", "PAYMENT", command);

        DocumentTemplateService.TemplateValidationResult validation = service.validate("PAYMENT", command);
        assertEquals("payment.v1", validation.schemaVersion());
        assertTrue(validation.referencedFields().contains("payment.applyCode"));
        assertTrue(service.getFieldCatalog("PAYMENT").fieldPaths().contains("payment.applyCode"));

        DocumentTemplateVersion copied = service.copyVersion(first.getTemplateId(), first.getId());
        assertEquals(2, copied.getVersionNo());
        assertEquals(first.getTemplateContent(), copied.getTemplateContent());
        assertEquals("PAYMENT_CATALOG_TEMPLATE", service.exportVersion(copied.getId()).templateCode());
        assertEquals(1, service.listTemplates("PAYMENT").stream()
                .filter(template -> "PAYMENT_CATALOG_TEMPLATE".equals(template.templateCode())).count());
    }

    @Test
    void defaultBindingRejectsStaleLockAndKeepsOneEffectiveVersion() {
        DocumentTemplateService.DraftCommand command = new DocumentTemplateService.DraftCommand(
                "payment.v1", "<html><body>{{payment.applyCode}}</body></html>",
                "[\"payment.applyCode\"]", "default binding CAS");
        DocumentTemplateVersion first = service.publish(service.create(
                "PAYMENT_DEFAULT_CAS_A", "默认绑定模板 A", "PAYMENT", command).getId());
        DocumentTemplateVersion second = service.publish(service.create(
                "PAYMENT_DEFAULT_CAS_B", "默认绑定模板 B", "PAYMENT", command).getId());

        service.bindDefault(first.getId(), 0);
        service.bindDefault(second.getId(), 0);

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> service.bindDefault(first.getId(), 0));
        assertEquals("DOCUMENT_DEFAULT_BINDING_CONFLICT", conflict.getCode());
        assertEquals(second.getId(), service.requireDefaultVersion("PAYMENT").getId());
    }
}
