package com.cgcpms.document;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.entity.DocumentTemplateVersion;
import com.cgcpms.document.service.DocumentTemplateService;
import com.cgcpms.document.service.SystemDocumentTemplateCatalog;
import com.cgcpms.document.service.SystemDocumentTemplateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "minio.enabled=false")
@ActiveProfiles("local")
@Transactional
class SystemDocumentTemplateServiceIntegrationTest {
    private static final long TEST_TENANT = 990220L;
    private static final long ROLLBACK_TEST_TENANT = 990221L;

    @Autowired private SystemDocumentTemplateService systemService;
    @Autowired private SystemDocumentTemplateCatalog catalog;
    @Autowired private DocumentTemplateService templateService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TEST_TENANT, TestUserContext.USER_ADMIN);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void firstInstallBindsAndSecondInstallIsIdempotent() {
        var first = systemService.install("PAYMENT");
        var second = systemService.install("PAYMENT");

        assertEquals(SystemDocumentTemplateService.InstallAction.CREATED, first.action());
        assertEquals(SystemDocumentTemplateService.BindingAction.BOUND, first.bindingAction());
        assertEquals(SystemDocumentTemplateService.InstallAction.UNCHANGED, second.action());
        assertEquals(SystemDocumentTemplateService.BindingAction.UNCHANGED_SYSTEM, second.bindingAction());
        assertEquals(first.templateId(), second.templateId());
        assertEquals(first.versionId(), second.versionId());
        assertEquals(first.versionId(), templateService.requireDefaultVersion("PAYMENT").getId());
    }

    @Test
    void unpublishedDraftDoesNotTriggerSystemUpgrade() {
        var first = systemService.install("PAYMENT");
        var definition = catalog.validate(catalog.require("PAYMENT"));
        templateService.createNextDraft(first.templateId(), new DocumentTemplateService.DraftCommand(
                definition.definition().schemaVersion(), null, null, "tenant draft", definition.designSchema()));

        var second = systemService.install("PAYMENT");

        assertEquals(SystemDocumentTemplateService.InstallAction.UNCHANGED, second.action());
        assertEquals(first.versionId(), second.versionId());
        assertEquals(first.versionId(), templateService.requireDefaultVersion("PAYMENT").getId());
    }

    @Test
    void preservesCustomDefaultDuringFirstSystemInstall() {
        var definition = catalog.validate(catalog.require("PROJECT_APPROVAL"));
        DocumentTemplateVersion customDraft = templateService.create("CUSTOM_PROJECT_APPROVAL", "租户自定义立项单",
                "PROJECT_APPROVAL", new DocumentTemplateService.DraftCommand(
                        definition.definition().schemaVersion(), null, null, "custom", definition.designSchema()));
        DocumentTemplateVersion customPublished = templateService.publish(customDraft.getId());
        templateService.bindDefault(customPublished.getId(), 0);

        var result = systemService.install("PROJECT_APPROVAL");

        assertEquals(SystemDocumentTemplateService.BindingAction.PRESERVED_CUSTOM, result.bindingAction());
        assertNotEquals(customPublished.getTemplateId(), result.templateId());
        assertEquals(customPublished.getId(), templateService.requireDefaultVersion("PROJECT_APPROVAL").getId());
    }

    @Test
    void upgradeAppendsPublishedVersionAndUpdatesOnlySystemDefault() {
        var first = systemService.install("SETTLEMENT");
        var definition = catalog.validate(catalog.require("SETTLEMENT"));
        String outdated = definition.designSchema().replace("工程结算单", "旧版工程结算单");
        DocumentTemplateVersion oldDraft = templateService.createNextDraft(first.templateId(),
                new DocumentTemplateService.DraftCommand(definition.definition().schemaVersion(), null, null,
                        "outdated", outdated));
        DocumentTemplateVersion oldPublished = templateService.publish(oldDraft.getId());
        templateService.bindDefault(oldPublished.getId(), 0);

        var upgraded = systemService.install("SETTLEMENT");

        assertEquals(SystemDocumentTemplateService.InstallAction.UPGRADED, upgraded.action());
        assertEquals(SystemDocumentTemplateService.BindingAction.UPDATED_SYSTEM, upgraded.bindingAction());
        assertTrue(upgraded.versionId() > oldPublished.getId());
        assertEquals(upgraded.versionId(), templateService.requireDefaultVersion("SETTLEMENT").getId());
        assertEquals("PUBLISHED", oldPublished.getStatus());
    }

    @Test
    void installAllValidatesAndInstallsExactlyTwentyEightDefinitions() {
        List<SystemDocumentTemplateService.InstallResult> results = systemService.installAll();

        assertEquals(28, results.size());
        assertEquals(28, results.stream().map(SystemDocumentTemplateService.InstallResult::businessType).distinct().count());
        assertTrue(results.stream().noneMatch(result ->
                SystemDocumentTemplateCatalog.EXCLUDED_BUSINESS_TYPE.equals(result.businessType())));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void installAllRollsBackEveryWriteWhenALateIdentityConflictOccurs() {
        TestUserContext.setAdmin(ROLLBACK_TEST_TENANT, TestUserContext.USER_ADMIN);
        cleanupRollbackTenant();
        var finalDefinition = catalog.definitions().get(catalog.definitions().size() - 1);
        jdbcTemplate.update("""
                INSERT INTO biz_document_template
                  (id, tenant_id, template_code, template_name, business_type, engine_type, enabled,
                   created_by, created_at, updated_by, updated_at, deleted_flag)
                VALUES (?, ?, ?, ?, ?, 'HTML_PDF', 1, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, 0)
                """, 990221001L, ROLLBACK_TEST_TENANT, finalDefinition.templateCode(), "冲突占位模板",
                "PAYMENT", TestUserContext.USER_ADMIN, TestUserContext.USER_ADMIN);
        try {
            BusinessException failure = assertThrows(BusinessException.class, systemService::installAll);
            assertEquals("SYSTEM_DOCUMENT_TEMPLATE_IDENTITY_CONFLICT", failure.getCode());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM biz_document_template WHERE tenant_id = ?",
                    Integer.class, ROLLBACK_TEST_TENANT));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM biz_document_template_version WHERE tenant_id = ?",
                    Integer.class, ROLLBACK_TEST_TENANT));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM biz_document_default_binding WHERE tenant_id = ?",
                    Integer.class, ROLLBACK_TEST_TENANT));
        } finally {
            cleanupRollbackTenant();
        }
    }

    private void cleanupRollbackTenant() {
        jdbcTemplate.update("DELETE FROM biz_document_default_binding WHERE tenant_id = ?", ROLLBACK_TEST_TENANT);
        jdbcTemplate.update("DELETE FROM biz_document_template_version WHERE tenant_id = ?", ROLLBACK_TEST_TENANT);
        jdbcTemplate.update("DELETE FROM biz_document_template WHERE tenant_id = ?", ROLLBACK_TEST_TENANT);
        jdbcTemplate.update("DELETE FROM document_template_code_scope WHERE tenant_id = ?", ROLLBACK_TEST_TENANT);
    }
}
