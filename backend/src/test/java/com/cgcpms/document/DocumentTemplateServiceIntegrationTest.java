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
                "payment.v2",
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

        service.disablePublishedVersion(published.getId());
        BusinessException disabledDefault = assertThrows(BusinessException.class,
                () -> service.bindDefault(published.getId(), 0));
        assertEquals("DOCUMENT_TEMPLATE_NOT_PUBLISHED", disabledDefault.getCode());
        assertEquals("PUBLISHED", service.enableDisabledVersion(published.getId()).getStatus());
    }

    @Test
    void automaticallyGeneratesStableTemplateCode() {
        DocumentTemplateService.DraftCommand command = new DocumentTemplateService.DraftCommand(
                "payment.v2", "<html><body>{{payment.applyCode}}</body></html>",
                "[\"payment.applyCode\"]", "generated code");

        DocumentTemplateVersion draft = service.createAuto("自动编码模板", "PAYMENT", command);

        String code = service.getTemplateDetail(draft.getTemplateId()).template().getTemplateCode();
        assertTrue(code.matches("TPL-\\d{8}-\\d{3}"));
    }

    @Test
    void deletesDraftTemplateButPreservesPublishedHistory() {
        DocumentTemplateService.DraftCommand command = new DocumentTemplateService.DraftCommand(
                "payment.v2", "<html><body>{{payment.applyCode}}</body></html>",
                "[\"payment.applyCode\"]", "delete test");
        DocumentTemplateVersion draft = service.create(
                "PAYMENT_DELETE_DRAFT", "待删除模板", "PAYMENT", command);
        service.createNextDraft(draft.getTemplateId(), command);

        service.deleteTemplate(draft.getTemplateId());

        assertTrue(service.listTemplates("PAYMENT").stream()
                .noneMatch(template -> "PAYMENT_DELETE_DRAFT".equals(template.templateCode())));

        DocumentTemplateVersion published = service.publish(service.create(
                "PAYMENT_KEEP_PUBLISHED", "已发布模板", "PAYMENT", command).getId());
        BusinessException protectedHistory = assertThrows(BusinessException.class,
                () -> service.deleteTemplate(published.getTemplateId()));
        assertEquals("DOCUMENT_TEMPLATE_DELETE_FORBIDDEN", protectedHistory.getCode());
    }

    @Test
    void fieldCatalogBlocksUnknownAndOutOfLoopFieldsBeforePublish() {
        DocumentTemplateService.DraftCommand unknownField = new DocumentTemplateService.DraftCommand(
                "payment.v2", "<html><body>{{payment.unknownField}}</body></html>",
                "[\"payment.unknownField\"]", "unknown field");
        BusinessException unavailable = assertThrows(BusinessException.class,
                () -> service.create("PAYMENT_UNKNOWN_FIELD", "未知字段模板", "PAYMENT", unknownField));
        assertEquals("DOCUMENT_FIELD_UNAVAILABLE", unavailable.getCode());

        DocumentTemplateService.DraftCommand outOfLoop = new DocumentTemplateService.DraftCommand(
                "payment.v2", "<html><body>{{sources.amount}}</body></html>",
                "[\"sources.amount\"]", "collection context");
        BusinessException context = assertThrows(BusinessException.class,
                () -> service.create("PAYMENT_COLLECTION_CONTEXT", "集合上下文模板", "PAYMENT", outOfLoop));
        assertEquals("DOCUMENT_FIELD_CONTEXT_INVALID", context.getCode());
    }

    @Test
    void catalogValidationCopyAndExportStayWithinTheSameTenantTemplate() {
        DocumentTemplateService.DraftCommand command = new DocumentTemplateService.DraftCommand(
                "payment.v2", "<html><body>{{payment.applyCode}}</body></html>",
                "[\"payment.applyCode\"]", "catalog test");
        DocumentTemplateVersion first = service.create("PAYMENT_CATALOG_TEMPLATE", "字段目录模板", "PAYMENT", command);

        DocumentTemplateService.TemplateValidationResult validation = service.validate("PAYMENT", command);
        assertEquals("payment.v2", validation.schemaVersion());
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
                "payment.v2", "<html><body>{{payment.applyCode}}</body></html>",
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

    @Test
    void designSchemaOwnsCompiledContentManifestAndRemainsImmutableAfterPublish() {
        String designSchema = """
                {"schemaVersion":"payment.v2","page":{"size":"A4","orientation":"PORTRAIT",
                "marginMm":{"top":10,"right":10,"bottom":10,"left":10}},
                "elements":[{"id":"code","type":"FIELD","xMm":10,"yMm":10,"widthMm":60,"heightMm":10,
                "fieldPath":"payment.applyCode"}],"tables":[]}
                """;
        DocumentTemplateService.DraftCommand command = new DocumentTemplateService.DraftCommand(
                "payment.v2", "<script>ignored</script>", "[]", "canvas", designSchema);

        DocumentTemplateVersion draft = service.create(
                "PAYMENT_CANVAS_TEMPLATE", "付款画布模板", "PAYMENT", command);

        assertEquals(designSchema, draft.getDesignSchema());
        assertTrue(draft.getTemplateContent().contains("{{payment.applyCode}}"));
        assertEquals("[\"payment.applyCode\"]", draft.getFieldManifest());
        DocumentTemplateVersion published = service.publish(draft.getId());
        BusinessException immutable = assertThrows(BusinessException.class,
                () -> service.updateDraft(published.getId(), command));
        assertEquals("DOCUMENT_TEMPLATE_VERSION_IMMUTABLE", immutable.getCode());
    }
}
