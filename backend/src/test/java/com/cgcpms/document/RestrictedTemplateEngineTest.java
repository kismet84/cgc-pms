package com.cgcpms.document;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.config.DocumentGenerationProperties;
import com.cgcpms.document.render.RestrictedTemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestrictedTemplateEngineTest {
    private RestrictedTemplateEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RestrictedTemplateEngine(new DocumentGenerationProperties());
    }

    @Test
    void escapesInjectedBusinessValues() {
        String output = engine.render("<html><body>{{payment.payee}}</body></html>",
                Map.of("payment", Map.of("payee", "<script>alert('x')</script>")));

        assertTrue(output.contains("&lt;script&gt;"));
        assertTrue(output.contains("&#39;x&#39;"));
    }

    @Test
    void rejectsRemoteAndLocalResources() {
        BusinessException remote = assertThrows(BusinessException.class,
                () -> engine.validate("<html><img src='https://example.com/a.png'/></html>"));
        assertEquals("DOCUMENT_TEMPLATE_RESOURCE_FORBIDDEN", remote.getCode());

        BusinessException local = assertThrows(BusinessException.class,
                () -> engine.validate("<html><img src='file:///etc/passwd'/></html>"));
        assertEquals("DOCUMENT_TEMPLATE_RESOURCE_FORBIDDEN", local.getCode());
    }

    @Test
    void rejectsUnsupportedExpressionsAndOversizedCollections() {
        BusinessException expression = assertThrows(BusinessException.class,
                () -> engine.render("<html>{{ payment.amount + 1 }}</html>", Map.of()));
        assertEquals("DOCUMENT_TEMPLATE_SYNTAX_INVALID", expression.getCode());

        List<Integer> rows = java.util.stream.IntStream.range(0, 201).boxed().toList();
        BusinessException collection = assertThrows(BusinessException.class,
                () -> engine.render("<html>ok</html>", Map.of("rows", rows)));
        assertEquals("DOCUMENT_COLLECTION_TOO_LARGE", collection.getCode());
    }

    @Test
    void rendersSingleBoundedCollectionAndEscapesEveryRow() {
        String output = engine.render("""
                <table>{{#each sources}}<tr><td>{{type}}</td><td>{{amount}}</td></tr>{{/each}}</table>
                """, Map.of("sources", List.of(
                Map.of("type", "费用<脚本>", "amount", "12.30"),
                Map.of("type", "结算", "amount", "45.60"))));

        assertTrue(output.contains("费用&lt;脚本&gt;"));
        assertTrue(output.contains("45.60"));
        assertEquals(2, output.split("<tr>", -1).length - 1);
    }

    @Test
    void rejectsNestedCollectionLoops() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> engine.render("{{#each rows}}{{#each children}}{{value}}{{/each}}{{/each}}",
                        Map.of("rows", List.of(Map.of("children", List.of(Map.of("value", "x")))))));

        assertEquals("DOCUMENT_TEMPLATE_LOOP_NESTED", error.getCode());
    }
}
