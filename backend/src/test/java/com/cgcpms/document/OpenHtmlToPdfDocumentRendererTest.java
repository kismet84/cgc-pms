package com.cgcpms.document;

import com.cgcpms.document.config.DocumentGenerationProperties;
import com.cgcpms.document.render.OpenHtmlToPdfDocumentRenderer;
import com.cgcpms.document.render.RenderedDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenHtmlToPdfDocumentRendererTest {
    @Test
    void rendersAndInspectsBoundedChinesePdfWithBundledFont() throws Exception {
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        OpenHtmlToPdfDocumentRenderer renderer = new OpenHtmlToPdfDocumentRenderer(properties);
        try {
            RenderedDocument result = renderer.render("""
                    <html><head><style>@page { size: A4; margin: 10mm; }</style></head>
                    <body><h1>业务单据</h1><p>付款审批与审计输出</p></body></html>
                    """);
            assertTrue(result.content().length > 100);
            assertEquals(64, result.sha256().length());
            assertEquals(1, result.pageCount());
            try (PDDocument document = Loader.loadPDF(result.content())) {
                String text = new PDFTextStripper().getText(document);
                assertTrue(text.contains("业务单据"));
                assertTrue(text.contains("付款审批与审计输出"));
            }
        } finally {
            renderer.close();
        }
    }
}
