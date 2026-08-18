package com.cgcpms.document;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.document.provider.DocumentDataProvider;
import com.cgcpms.document.provider.DocumentDataProviderRegistry;
import com.cgcpms.document.provider.DocumentDataSnapshot;
import com.cgcpms.document.render.DocumentRenderer;
import com.cgcpms.document.render.RestrictedTemplateEngine;
import com.cgcpms.document.service.SystemDocumentTemplateCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "minio.enabled=false")
@ActiveProfiles("local")
class DocumentProviderCoverageTest {
    private static final Set<String> FORBIDDEN_ROOTS = Set.of(
            "workflow", "approval", "approvalRecords", "signatures", "attachments");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DocumentDataProviderRegistry registry;
    @Autowired private SystemDocumentTemplateCatalog systemTemplateCatalog;
    @Autowired private RestrictedTemplateEngine templateEngine;
    @Autowired private DocumentRenderer renderer;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void everyEnabledApprovalTypeHasOneSafeSelfConsistentProvider() {
        List<String> enabledTypes = jdbc.queryForList("""
                SELECT DISTINCT business_type FROM wf_template
                WHERE tenant_id = ? AND enabled = 1 AND deleted_flag = 0
                ORDER BY business_type
                """, String.class, TestUserContext.TENANT_0);

        assertEquals(28, enabledTypes.size(), "tenant 0 enabled workflow denominator drifted");
        assertTrue(enabledTypes.stream().allMatch(registry::has),
                () -> "missing providers: " + enabledTypes.stream().filter(type -> !registry.has(type)).toList());

        enabledTypes.forEach(type -> assertProvider(type, registry.require(type)));
    }

    @Test
    void allTwentyEightSystemDefinitionsCompileEverySafeFieldAndRenderPdf() throws Exception {
        List<SystemDocumentTemplateCatalog.ValidatedDefinition> definitions = systemTemplateCatalog.validateAll();
        String outputDirectory = System.getProperty("document.pdf.qa.output", "").trim();
        if (!outputDirectory.isEmpty()) Files.createDirectories(Path.of(outputDirectory));

        assertEquals(28, definitions.size());
        assertFalse(definitions.stream().anyMatch(value ->
                SystemDocumentTemplateCatalog.EXCLUDED_BUSINESS_TYPE.equals(value.definition().businessType())));
        for (SystemDocumentTemplateCatalog.ValidatedDefinition value : definitions) {
            DocumentDataProvider provider = registry.require(value.definition().businessType());
            assertEquals("SYSTEM", provider.defaultTemplatePolicy());
            assertEquals(provider.schemaVersion(), value.definition().schemaVersion());
            assertTrue(value.designSchema().contains("\"layoutVersion\":2"));
            assertTrue(value.designSchema().contains("\"type\":\"SIGNATURE_GRID\""));
            String html = templateEngine.render(value.templateContent(), provider.sampleData().values());
            var rendered = renderer.render(html);
            assertTrue(rendered.content().length > 1_000, value.definition().businessType());
            assertTrue(rendered.pageCount() >= 1, value.definition().businessType());
            if (!outputDirectory.isEmpty()) {
                Files.write(Path.of(outputDirectory, value.definition().businessType() + ".pdf"), rendered.content());
            }
        }
    }

    private void assertProvider(String type, DocumentDataProvider provider) {
        DocumentTemplateFieldCatalog.Catalog catalog = provider.fieldCatalog();
        DocumentDataSnapshot sample = provider.sampleData();
        assertEquals(type, provider.businessType());
        assertEquals(type, catalog.businessType());
        assertEquals(provider.schemaVersion(), catalog.schemaVersion());
        assertEquals(catalog.schemaVersion(), sample.schemaVersion());
        assertFalse(provider.queryAuthority().isBlank());
        assertTrue(Set.of("SYSTEM", "NONE").contains(provider.defaultTemplatePolicy()));

        for (DocumentTemplateFieldCatalog.Field field : catalog.fields()) {
            String[] segments = field.path().split("\\.");
            assertFalse(FORBIDDEN_ROOTS.contains(segments[0]), () -> "forbidden root: " + field.path());
            for (String segment : segments) {
                assertFalse(segment.equals("id") || segment.endsWith("Id")
                                || Set.of("tenantId", "deletedFlag", "approvalInstance", "idempotencyKey")
                                .contains(segment),
                        () -> "internal field: " + field.path());
            }
            assertNotNull(resolveSample(sample.values(), field), () -> "sample path missing: " + field.path());
        }
    }

    private Object resolveSample(Map<String, Object> root, DocumentTemplateFieldCatalog.Field field) {
        if (field.collectionPath() == null) return resolve(root, field.path());
        Object collection = resolve(root, field.collectionPath());
        assertTrue(collection instanceof Collection<?>, () -> "not a collection: " + field.collectionPath());
        Object first = ((Collection<?>) collection).stream().findFirst().orElse(null);
        assertTrue(first instanceof Map<?, ?>, () -> "empty collection sample: " + field.collectionPath());
        return resolve((Map<?, ?>) first, field.path().substring(field.collectionPath().length() + 1));
    }

    private Object resolve(Map<?, ?> root, String path) {
        Object value = root;
        for (String segment : path.split("\\.")) {
            if (!(value instanceof Map<?, ?> map)) return null;
            value = map.get(segment);
        }
        return value;
    }
}
