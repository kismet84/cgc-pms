package com.cgcpms.document;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.canvas.DocumentCanvasCompiler;
import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.document.config.DocumentGenerationProperties;
import com.cgcpms.document.render.OpenHtmlToPdfDocumentRenderer;
import com.cgcpms.document.render.RestrictedTemplateEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentCanvasCompilerTest {
    private final DocumentCanvasCompiler compiler = new DocumentCanvasCompiler(new ObjectMapper());
    private final DocumentTemplateFieldCatalog.Catalog catalog = new DocumentTemplateFieldCatalog.Catalog(
            "TEST", "test.v1", List.of(
            new DocumentTemplateFieldCatalog.Field("document.code", "单据编号", "TEXT", false, null, false),
            new DocumentTemplateFieldCatalog.Field("items.name", "名称", "TEXT", false, "items", false)));

    @Test
    void compilesA4FieldsAndCollectionTableToRestrictedTemplate() {
        DocumentCanvasCompiler.Compilation result = compiler.compile(schema("PORTRAIT", 20, false), catalog);

        assertTrue(result.html().contains("@page{size:A4 portrait"));
        assertTrue(result.html().contains("{{document.code}}"));
        assertTrue(result.html().contains("单据编号 {{document.code}}"));
        assertTrue(result.html().contains("border-top:0.3mm solid #333"));
        assertTrue(result.html().contains("{{#each items}}"));
        assertTrue(result.html().contains("canvas-table-spacer\" style=\"height:50mm"));
        assertTrue(result.html().contains("margin-left:10mm;width:80mm;min-height:40mm"));
        assertTrue(result.html().contains("data-repeat=\"HEADER\""));
        assertTrue(result.html().contains("-fs-table-paginate:paginate"));
        assertTrue(result.html().contains("display:table-header-group"));
        assertEquals(java.util.Set.of("document.code", "items.name"), result.fieldManifest());
    }

    @Test
    void acceptsMissingOrBlankOptionalFieldLabel() {
        String labeled = schema("PORTRAIT", 20, false);

        assertTrue(compiler.compile(labeled.replace("\"text\":\"单据编号\"", "\"text\":\"\""), catalog)
                .html().contains(">{{document.code}}</div>"));
        assertTrue(compiler.compile(labeled.replace(",\"text\":\"单据编号\"", ""), catalog)
                .html().contains(">{{document.code}}</div>"));
    }

    @Test
    void rejectsUnknownPropertiesWrongCollectionContextAndOverflow() {
        BusinessException unknown = assertThrows(BusinessException.class,
                () -> compiler.compile(schema("PORTRAIT", 20, true), catalog));
        assertEquals("DOCUMENT_DESIGN_SCHEMA_INVALID", unknown.getCode());

        BusinessException context = assertThrows(BusinessException.class,
                () -> compiler.compile(schema("PORTRAIT", 20, false).replace("items.name", "document.code"), catalog));
        assertEquals("DOCUMENT_FIELD_CONTEXT_INVALID", context.getCode());

        BusinessException overflow = assertThrows(BusinessException.class,
                () -> compiler.compile(schema("LANDSCAPE", 290, false), catalog));
        assertEquals("DOCUMENT_CANVAS_OVERFLOW", overflow.getCode());

        BusinessException mismatch = assertThrows(BusinessException.class,
                () -> compiler.compile(schema("PORTRAIT", 20, false).replace("test.v1", "test.v2"), catalog));
        assertEquals("DOCUMENT_SCHEMA_VERSION_MISMATCH", mismatch.getCode());
    }

    @Test
    void rendersSixtyRowsAcrossPagesWithRepeatedTableHeader() throws Exception {
        DocumentCanvasCompiler.Compilation compiled = compiler.compile(schema("PORTRAIT", 20, false), catalog);
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        RestrictedTemplateEngine engine = new RestrictedTemplateEngine(properties);
        OpenHtmlToPdfDocumentRenderer renderer = new OpenHtmlToPdfDocumentRenderer(properties);
        try {
            List<Map<String, String>> items = IntStream.rangeClosed(1, 60)
                    .mapToObj(index -> Map.of("name", "明细-" + index))
                    .toList();
            String html = engine.render(compiled.html(), Map.of(
                    "document", Map.of("code", "DOC-078"), "items", items));
            try (var pdf = Loader.loadPDF(renderer.render(html).content())) {
                String text = new PDFTextStripper().getText(pdf);
                assertTrue(pdf.getNumberOfPages() >= 2);
                assertTrue(text.contains("明细-60"));
                assertTrue(text.split("名称", -1).length >= 3, "table header must repeat on later pages");
            }
        } finally {
            renderer.close();
        }
    }

    @Test
    void rendersEmptyAndThreeRowTablesWithoutTemplateMarkers() throws Exception {
        DocumentCanvasCompiler.Compilation compiled = compiler.compile(schema("PORTRAIT", 20, false), catalog);
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        RestrictedTemplateEngine engine = new RestrictedTemplateEngine(properties);
        OpenHtmlToPdfDocumentRenderer renderer = new OpenHtmlToPdfDocumentRenderer(properties);
        try {
            for (List<Map<String, String>> items : List.of(
                    List.<Map<String, String>>of(),
                    List.of(Map.of("name", "明细-1"), Map.of("name", "明细-2"), Map.of("name", "明细-3")))) {
                String html = engine.render(compiled.html(), Map.of(
                        "document", Map.of("code", "DOC-079"), "items", items));
                assertTrue(!html.contains("{{"));
                try (var pdf = Loader.loadPDF(renderer.render(html).content())) {
                    String text = new PDFTextStripper().getText(pdf);
                    assertTrue(text.contains("名称"));
                    assertEquals(items.size(), text.split("明细-", -1).length - 1);
                }
            }
        } finally {
            renderer.close();
        }
    }

    @Test
    void ordersFlowTablesByAnchorAndRendersTheSecondAfterSixtyRows() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.readTree(schema("PORTRAIT", 20, false));
        var tables = (com.fasterxml.jackson.databind.node.ArrayNode) root.get("tables");
        var second = (com.fasterxml.jackson.databind.node.ObjectNode) tables.get(0).deepCopy();
        second.put("id", "items-2");
        second.put("yMm", 100);
        second.put("heightMm", 20);
        ((com.fasterxml.jackson.databind.node.ObjectNode) second.withArray("columns").get(0))
                .put("header", "第二表");
        tables.insert(0, second);

        DocumentCanvasCompiler.Compilation compiled = compiler.compile(mapper.writeValueAsString(root), catalog);
        String firstGap = "canvas-table-spacer\" style=\"height:50mm";
        String secondGap = "canvas-table-spacer\" style=\"height:10mm";
        assertTrue(compiled.html().indexOf(firstGap) < compiled.html().indexOf(secondGap));

        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        RestrictedTemplateEngine engine = new RestrictedTemplateEngine(properties);
        OpenHtmlToPdfDocumentRenderer renderer = new OpenHtmlToPdfDocumentRenderer(properties);
        try {
            List<Map<String, String>> items = IntStream.rangeClosed(1, 60)
                    .mapToObj(index -> Map.of("name", "明细-" + index))
                    .toList();
            String html = engine.render(compiled.html(), Map.of(
                    "document", Map.of("code", "DOC-079"), "items", items));
            try (var pdf = Loader.loadPDF(renderer.render(html).content())) {
                String text = new PDFTextStripper().getText(pdf);
                assertTrue(pdf.getNumberOfPages() >= 3);
                assertTrue(text.indexOf("第二表") > text.indexOf("明细-60"));
                assertEquals(2, text.split("明细-60", -1).length - 1);
            }
        } finally {
            renderer.close();
        }
    }

    @Test
    void rejectsOverlappingFlowAnchorsAndBodyElementsBelowAFlowTable() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.readTree(schema("PORTRAIT", 20, false));
        var tables = (com.fasterxml.jackson.databind.node.ArrayNode) root.get("tables");
        var overlap = (com.fasterxml.jackson.databind.node.ObjectNode) tables.get(0).deepCopy();
        overlap.put("id", "items-overlap");
        overlap.put("yMm", 80);
        tables.add(overlap);

        BusinessException tableConflict = assertThrows(BusinessException.class,
                () -> compiler.compile(mapper.writeValueAsString(root), catalog));
        assertEquals("DOCUMENT_DESIGN_SCHEMA_INVALID", tableConflict.getCode());
        assertTrue(tableConflict.getMessage().contains("流式表格设计占位不得重叠"));

        String bodyConflictSchema = schema("PORTRAIT", 20, false)
                .replace("\"yMm\":40", "\"yMm\":60");
        BusinessException bodyConflict = assertThrows(BusinessException.class,
                () -> compiler.compile(bodyConflictSchema, catalog));
        assertEquals("DOCUMENT_DESIGN_SCHEMA_INVALID", bodyConflict.getCode());
        assertTrue(bodyConflict.getMessage().contains("divider"));
    }

    private String schema(String orientation, int fieldX, boolean unknown) {
        return """
                {
                  "schemaVersion":"test.v1",
                  "page":{"size":"A4","orientation":"%s","marginMm":{"top":10,"right":10,"bottom":10,"left":10}},
                  "elements":[{"id":"code","type":"FIELD","xMm":%d,"yMm":20,"widthMm":40,"heightMm":10,
                    "fieldPath":"document.code","text":"单据编号","fontSizePt":10,"align":"LEFT","repeat":"HEADER"%s},
                    {"id":"divider","type":"DIVIDER","xMm":10,"yMm":40,"widthMm":80,"heightMm":2}],
                  "tables":[{"id":"items","collectionPath":"items","xMm":10,"yMm":50,"widthMm":80,"heightMm":40,
                    "columns":[{"fieldPath":"items.name","header":"名称","widthMm":80}]}]
                }
                """.formatted(orientation, fieldX, unknown ? ",\"unexpected\":true" : "");
    }
}
