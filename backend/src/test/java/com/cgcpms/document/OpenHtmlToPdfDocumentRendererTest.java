package com.cgcpms.document;

import com.cgcpms.document.config.DocumentGenerationProperties;
import com.cgcpms.document.render.OpenHtmlToPdfDocumentRenderer;
import com.cgcpms.document.render.RenderedDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenHtmlToPdfDocumentRendererTest {
    private static final String INLINE_PNG = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

    @Test
    void rendersAndInspectsBoundedChinesePdfWithBundledFont() throws Exception {
        RenderedDocument result = render("""
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
    }

    @Test
    void rendersRepresentativeMultiPagePdfWithinBounds() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int i = 1; i <= 120; i++) {
            rows.append("<tr><td>明细-").append(i).append("</td><td>工程材料采购</td><td>")
                    .append(i).append(".00</td></tr>");
        }
        String html = """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><style>
                    @page { size: A4; margin: 18mm 12mm 20mm; }
                    table { width: 100%%; border-collapse: collapse; }
                    thead { display: table-header-group; }
                    tr { page-break-inside: avoid; }
                    th, td { border: 1px solid #333; padding: 3px; }
                  </style></head>
                  <body>
                    <h1>付款申请单</h1><p><img src="%s" alt="logo" /> 申请金额：123456.78 元</p>
                    <table><thead><tr><th>序号</th><th>用途</th><th>金额</th></tr></thead>
                    <tbody>%s</tbody></table>
                  </body>
                </html>
                """.formatted(INLINE_PNG, rows);

        RenderedDocument result = assertTimeout(Duration.ofSeconds(15), () -> render(html));
        assertTrue(result.content().length < 5L * 1024 * 1024, "representative PDF must stay below 5 MiB");
        assertTrue(result.pageCount() >= 2, "long details must paginate");
        assertTrue(result.pageCount() <= 20, "representative sample must not exceed 20 pages");
        try (PDDocument document = Loader.loadPDF(result.content())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("付款申请单"));
            assertTrue(text.contains("明细-120"), "last detail row must be present");
            assertTrue(document.getPage(0).getResources().getXObjectNames().iterator().hasNext(),
                    "allowed inline image must be embedded");
        }
    }

    @Test
    void rejectsHttpAndFileImagesWithoutEmbeddingThem() throws Exception {
        RenderedDocument result = render("""
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <body>
                    <h1>外部资源拒绝测试</h1>
                    <img src="https://example.invalid/never.png" alt="remote-blocked" />
                    <img src="file:///C:/Windows/win.ini" alt="local-blocked" />
                  </body>
                </html>
                """);

        try (PDDocument document = Loader.loadPDF(result.content())) {
            assertFalse(document.getPage(0).getResources().getXObjectNames().iterator().hasNext(),
                    "denied images must not be embedded");
            assertTrue(new PDFTextStripper().getText(document).contains("外部资源拒绝测试"));
        }
    }

    private static RenderedDocument render(String html) {
        OpenHtmlToPdfDocumentRenderer renderer =
                new OpenHtmlToPdfDocumentRenderer(new DocumentGenerationProperties());
        try {
            return renderer.render(html);
        } finally {
            renderer.close();
        }
    }
}
