package com.cgcpms.document;

import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("M0 HTML/CSS to PDF renderer spike")
class OpenHtmlToPdfRendererSpikeTest {

    private static final int DETAIL_ROWS = 120;
    private static final long MAX_SPIKE_PDF_BYTES = 5L * 1024 * 1024;
    private static final Duration MAX_LOCAL_RENDER_TIME = Duration.ofSeconds(15);
    @Test
    @DisplayName("renders Chinese long table, pagination, header/footer and inline image")
    void rendersRepresentativeChineseDocumentWithinSpikeBounds() throws Exception {
        File chineseFont = findChineseFont();
        Assumptions.assumeTrue(chineseFont != null, "Chinese font is required for the local M0 spike");

        Instant startedAt = Instant.now();
        byte[] pdf = render(representativeHtml(), chineseFont, (uri, type) -> uri != null && uri.startsWith("data:"));
        Duration elapsed = Duration.between(startedAt, Instant.now());

        assertTrue(pdf.length > 4, "renderer must return a non-empty PDF");
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertTrue(pdf.length < MAX_SPIKE_PDF_BYTES, "representative PDF must stay below the M0 sample bound");
        assertTrue(elapsed.compareTo(MAX_LOCAL_RENDER_TIME) < 0,
                "representative render must finish within the local M0 spike bound");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() >= 2, "long details must paginate");
            assertTrue(document.getNumberOfPages() <= 20, "representative sample must not explode page count");
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("付款申请单"));
            assertTrue(text.contains("申请金额：123456.78 元"));
            assertTrue(text.contains("明细-120"), "last detail row must be present");
            assertTrue(document.getPage(0).getResources().getXObjectNames().iterator().hasNext(),
                    "inline image must be embedded into the PDF");
        }
    }

    @Test
    @DisplayName("denies remote and local resources before URI resolution")
    void deniesRemoteAndLocalResources() throws Exception {
        File chineseFont = findChineseFont();
        Assumptions.assumeTrue(chineseFont != null, "Chinese font is required for the local M0 spike");
        AtomicInteger deniedResources = new AtomicInteger();

        String html = """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><style>body { font-family: 'CGC PMS Chinese'; }</style></head>
                  <body>
                    <h1>外部资源拒绝测试</h1>
                    <img src="https://example.invalid/never.png" alt="remote-blocked" />
                    <img src="file:///C:/Windows/win.ini" alt="local-blocked" />
                  </body>
                </html>
                """;

        byte[] pdf = render(html, chineseFont, (uri, type) -> {
            deniedResources.incrementAndGet();
            return false;
        });

        assertTrue(deniedResources.get() >= 2, "both remote and local resources must reach the deny guard");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(1, document.getNumberOfPages());
            assertFalse(document.getPage(0).getResources().getXObjectNames().iterator().hasNext(),
                    "denied images must not be embedded");
            assertTrue(new PDFTextStripper().getText(document).contains("外部资源拒绝测试"));
        }
    }

    private static byte[] render(
            String html,
            File font,
            java.util.function.BiPredicate<String, com.openhtmltopdf.outputdevice.helper.ExternalResourceType> resourceGuard)
            throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(font, "CGC PMS Chinese");
            builder.useExternalResourceAccessControl(
                    resourceGuard,
                    ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
            builder.withHtmlContent(html, "https://document.invalid/");
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        }
    }

    private static String representativeHtml() {
        StringBuilder rows = new StringBuilder();
        for (int i = 1; i <= DETAIL_ROWS; i++) {
            rows.append("<tr><td>明细-").append(i).append("</td><td>工程材料采购</td><td>")
                    .append(i).append(".00</td></tr>");
        }
        return """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head>
                    <style>
                      @page {
                        size: A4;
                        margin: 18mm 12mm 20mm;
                        @top-center { content: "CGC-PMS 可审计单据"; }
                        @bottom-center { content: "第 " counter(page) " 页 / 共 " counter(pages) " 页"; }
                      }
                      body { font-family: 'CGC PMS Chinese'; font-size: 10pt; }
                      h1 { text-align: center; }
                      table { width: 100%%; border-collapse: collapse; }
                      thead { display: table-header-group; }
                      tr { page-break-inside: avoid; }
                      th, td { border: 1px solid #333; padding: 3px; }
                      .logo { width: 8px; height: 8px; }
                    </style>
                  </head>
                  <body>
                    <h1>付款申请单</h1>
                    <p><img class="logo" src="%s" alt="logo" /> 申请金额：123456.78 元</p>
                    <table>
                      <thead><tr><th>序号</th><th>用途</th><th>金额</th></tr></thead>
                      <tbody>%s</tbody>
                    </table>
                  </body>
                </html>
                """.formatted(inlinePng(), rows);
    }

    private static String inlinePng() {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, Color.BLUE.getRGB());
            }
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(image, "png", output), "JDK must provide a PNG writer");
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to create inline M0 spike image", exception);
        }
    }

    private static File findChineseFont() {
        String configured = System.getProperty("document.spike.font");
        List<String> candidates = List.of(
                configured == null ? "" : configured,
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/Deng.ttf",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc");
        return candidates.stream()
                .filter(path -> !path.isBlank())
                .map(Path::of)
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .findFirst()
                .orElse(null);
    }
}
