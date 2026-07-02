package com.cgcpms.invoice;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.invoice.service.InvoiceService;
import com.cgcpms.invoice.vo.InvoiceRecognizeResultVO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("PDF Recognition Unit Tests")
class InvoiceRecognitionTest {

    @Autowired
    private InvoiceService invoiceService;

    /** Reusable bytes for the valid Chinese invoice PDF, populated in @BeforeAll. */
    private static byte[] sampleInvoiceBytes;

    @TempDir
    static Path tempDir;

    /** Lines of Chinese invoice text to embed in the sample PDF. */
    private static final String[] INVOICE_TEXT_LINES = {
            "发票号码：12345678",
            "增值税专用发票",
            "价税合计 ￥150,000.00",
            "税率：13%",
            "税额：￥19,500.00",
            "开票日期：2026年06月01日",
            "销售方名称：测试供应商有限公司",
            "购买方名称：某建筑公司",
            "纳税人识别号：91110000ABCDEFGH",
            "备注：工程款结算"
    };

    @BeforeAll
    static void setUpClass() throws IOException {
        // Locate a Chinese-capable TrueType font
        File fontFile = findChineseFont();
        if (fontFile == null) {
            // If no Chinese font is available, we still generate a minimal PDF with
            // Latin fallback text so that non-Chinese-regex tests can run.
            System.err.println("WARNING: No Chinese font found — Chinese regex extraction tests may fail.");
        }

        // Generate the sample-invoice.pdf bytes in memory
        if (fontFile != null) {
            sampleInvoiceBytes = createPdfWithText(fontFile, INVOICE_TEXT_LINES);
        } else {
            // Fallback: Latin-only text
            sampleInvoiceBytes = createPdfWithText(null,
                    new String[] { "Invoice No: 12345678", "VAT Special Invoice", "Amount: 150000.00" });
        }

        Path samplePdf = tempDir.resolve("sample-invoice.pdf");
        Files.write(samplePdf, sampleInvoiceBytes);
        System.out.println("Sample invoice PDF written to temp file: " + samplePdf.toAbsolutePath());
    }

    // ── Test 1: Valid Chinese invoice PDF ──

    @Test
    @Order(1)
    @DisplayName("Should extract fields from valid Chinese invoice PDF")
    void shouldExtractFieldsFromValidPdf() {
        MultipartFile file = createMockMultipartFile(
                "application/pdf", sampleInvoiceBytes.length, false, sampleInvoiceBytes);

        InvoiceRecognizeResultVO result = invoiceService.recognize(file);

        assertNotNull(result, "Result should not be null");

        // Invoice number
        assertNotNull(result.getInvoiceNo(), "invoiceNo should be extracted");
        assertEquals("12345678", result.getInvoiceNo());

        // Invoice type
        assertEquals("VAT_SPECIAL", result.getInvoiceType(),
                "Should detect VAT_SPECIAL from 增值税专用发票");

        // Invoice amount — should be numeric, no ¥ or commas
        assertNotNull(result.getInvoiceAmount(), "invoiceAmount should be extracted");
        assertEquals("150000.00", result.getInvoiceAmount(),
                "Amount should be stripped of ¥ and commas");

        // Tax rate
        assertNotNull(result.getTaxRate(), "taxRate should be extracted");
        assertEquals("13", result.getTaxRate());

        // Tax amount
        assertNotNull(result.getTaxAmount(), "taxAmount should be extracted");
        assertEquals("19500.00", result.getTaxAmount());

        // Invoice date — normalized to YYYY-MM-DD
        assertNotNull(result.getInvoiceDate(), "invoiceDate should be extracted");
        assertEquals("2026-06-01", result.getInvoiceDate());

        // Seller name
        assertNotNull(result.getSellerName(), "sellerName should be extracted");
        assertTrue(result.getSellerName().contains("测试供应商"),
                "sellerName should contain tested supplier");

        // Buyer name
        assertNotNull(result.getBuyerName(), "buyerName should be extracted");
        assertTrue(result.getBuyerName().contains("某建筑公司"),
                "buyerName should contain buyer company");

        // Buyer tax number
        assertNotNull(result.getBuyerTaxNo(), "buyerTaxNo should be extracted");
        assertEquals("91110000ABCDEFGH", result.getBuyerTaxNo());

        // Remark — always null per current implementation
        assertNull(result.getRemark(), "remark should be null");
    }

    // ── Test 2: Empty PDF (no invoice text) ──

    @Test
    @Order(2)
    @DisplayName("Should return nulls for PDF with no invoice text")
    void shouldReturnNullsForEmptyPdf() throws IOException {
        byte[] emptyPdfBytes = createEmptyPdfBytes();
        MultipartFile file = createMockMultipartFile(
                "application/pdf", emptyPdfBytes.length, false, emptyPdfBytes);

        InvoiceRecognizeResultVO result = invoiceService.recognize(file);

        assertNotNull(result, "Result should not be null (it's a VO, not null)");
        assertNull(result.getInvoiceNo(), "invoiceNo should be null for empty PDF");
        assertNull(result.getInvoiceType(), "invoiceType should be null");
        assertNull(result.getInvoiceAmount(), "invoiceAmount should be null");
        assertNull(result.getTaxRate(), "taxRate should be null");
        assertNull(result.getTaxAmount(), "taxAmount should be null");
        assertNull(result.getInvoiceDate(), "invoiceDate should be null");
        assertNull(result.getSellerName(), "sellerName should be null");
        assertNull(result.getBuyerName(), "buyerName should be null");
        assertNull(result.getBuyerTaxNo(), "buyerTaxNo should be null");
        assertNull(result.getRemark(), "remark should be null");
    }

    // ── Test 3: Encrypted PDF ──

    @Test
    @Order(3)
    @DisplayName("Should reject encrypted PDF")
    void shouldRejectEncryptedPdf() throws IOException {
        byte[] encryptedPdfBytes = createEncryptedPdfBytes("userpass");
        MultipartFile file = createMockMultipartFile(
                "application/pdf", encryptedPdfBytes.length, false, encryptedPdfBytes);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> invoiceService.recognize(file),
                "Should throw BusinessException for encrypted PDF");
        assertEquals("PDF_ENCRYPTED", ex.getCode(), "Error code should be PDF_ENCRYPTED");
    }

    // ── Test 4: Non-PDF file ──

    @Test
    @Order(4)
    @DisplayName("Should reject non-PDF file")
    void shouldRejectNonPdfFile() {
        MultipartFile file = createMockMultipartFile(
                "text/plain", 100, false, "hello world".getBytes());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> invoiceService.recognize(file),
                "Should throw BusinessException for non-PDF content type");
        assertEquals("FILE_TYPE_NOT_ALLOWED", ex.getCode());
    }

    // ── Test 5: Empty file ──

    @Test
    @Order(5)
    @DisplayName("Should reject empty file")
    void shouldRejectEmptyFile() {
        MultipartFile file = createMockMultipartFile(
                "application/pdf", 0, true, new byte[0]);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> invoiceService.recognize(file),
                "Should throw BusinessException for empty file");
        assertEquals("FILE_EMPTY", ex.getCode());
    }

    // ── Test 6: Multi-page PDF ──

    @Test
    @Order(6)
    @DisplayName("Should handle multi-page PDF")
    void shouldHandleMultiPagePdf() throws IOException {
        File fontFile = findChineseFont();
        byte[] multiPageBytes = createMultiPagePdf(fontFile);
        MultipartFile file = createMockMultipartFile(
                "application/pdf", multiPageBytes.length, false, multiPageBytes);

        InvoiceRecognizeResultVO result = invoiceService.recognize(file);

        assertNotNull(result, "Result should not be null");
        // Page 1: "发票号码：99999999" → Page 2: "发票号码：12345678"
        // Regex extractFirst() picks the FIRST match → should be from page 1
        assertEquals("99999999", result.getInvoiceNo(),
                "Should extract invoiceNo from first page");
        // "VAT_NORMAL" from page 2 should NOT override "VAT_SPECIAL" from page 1
        assertEquals("VAT_SPECIAL", result.getInvoiceType(),
                "Should use first-match invoice type (page 1)");
        // Amount from page 1
        assertNotNull(result.getInvoiceAmount(), "Should extract amount from first page");
    }

    @Test
    @Order(7)
    @DisplayName("Should not write sensitive invoice recognition details to info logs")
    void shouldNotLogSensitiveRecognitionDetailsAtInfo() {
        Logger logger = (Logger) LoggerFactory.getLogger(InvoiceService.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            MultipartFile file = createMockMultipartFile(
                    "application/pdf", sampleInvoiceBytes.length, false, sampleInvoiceBytes);

            invoiceService.recognize(file);

            List<String> infoMessages = appender.list.stream()
                    .filter(event -> event.getLevel() == Level.INFO)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
            assertTrue(infoMessages.stream().noneMatch(message -> message.contains("PDF recognition result")),
                    "不应输出识别结果明细");
            assertTrue(infoMessages.stream().noneMatch(message -> message.contains("Extracted PDF text")),
                    "不应输出PDF文本内容");
            assertTrue(infoMessages.stream().noneMatch(message -> message.contains("12345678")),
                    "不应输出发票号码");
            assertTrue(infoMessages.stream().noneMatch(message -> message.contains("150000.00")),
                    "不应输出金额");
            assertTrue(infoMessages.stream().noneMatch(message -> message.contains("测试供应商")),
                    "不应输出销售方名称");
            assertTrue(infoMessages.stream().noneMatch(message -> message.contains("91110000ABCDEFGH")),
                    "不应输出税号");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }

    @Test
    @Order(8)
    @DisplayName("Should return fixed message when PDF recognition fails")
    void shouldReturnFixedMessageWhenPdfRecognitionFails() {
        byte[] brokenPdfBytes = createMalformedPdfBytes();
        MultipartFile file = createMockMultipartFile(
                "application/pdf", brokenPdfBytes.length, false, brokenPdfBytes);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> invoiceService.recognize(file));
        assertEquals("PDF_RECOGNIZE_FAILED", ex.getCode());
        assertEquals("PDF识别失败", ex.getMessage());
    }

    // ── Helper methods ──

    /** Create a Spring MockMultipartFile with the given properties. */
    private MultipartFile createMockMultipartFile(String contentType, long size,
                                                   boolean empty, byte[] content) {
        return new MockMultipartFile("file", "test.pdf", contentType, content);
    }

    /** Create a PDF-like byte stream with a valid magic header but broken structure. */
    private static byte[] createMalformedPdfBytes() {
        return """
                %PDF-1.4
                1 0 obj
                << /Type /Catalog /Pages 2 0 R >>
                endobj
                """.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Create a PDF with the given lines of text, each on a new line.
     *
     * @param fontFile Chinese-capable font file, or null for Helvetica (Latin-only)
     * @param lines    text lines to embed
     * @return PDF bytes
     */
    private static byte[] createPdfWithText(File fontFile, String... lines) throws IOException {
        PDDocument doc = new PDDocument();
        try {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDPageContentStream cs = new PDPageContentStream(doc, page);
            cs.beginText();
            if (fontFile != null) {
                cs.setFont(PDType0Font.load(doc, fontFile), 11);
            } else {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
            }
            cs.setLeading(18);
            // Position near top-left
            cs.newLineAtOffset(50, 750);

            for (String line : lines) {
                cs.showText(line);
                cs.newLineAtOffset(0, -18);
            }

            cs.endText();
            cs.close();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } finally {
            doc.close();
        }
    }

    /** Create a minimal valid PDF with a single blank page. */
    private static byte[] createEmptyPdfBytes() throws IOException {
        PDDocument doc = new PDDocument();
        try {
            doc.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } finally {
            doc.close();
        }
    }

    /** Create a password-protected PDF (user password = {@code password}). */
    private static byte[] createEncryptedPdfBytes(String password) throws IOException {
        PDDocument doc = new PDDocument();
        try {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDPageContentStream cs = new PDPageContentStream(doc, page);
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            cs.newLineAtOffset(50, 750);
            cs.showText("This PDF is encrypted");
            cs.endText();
            cs.close();

            // Encrypt with a user password
            AccessPermission ap = new AccessPermission();
            StandardProtectionPolicy spp = new StandardProtectionPolicy("ownerpass", password, ap);
            spp.setEncryptionKeyLength(128);
            doc.protect(spp);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } finally {
            doc.close();
        }
    }

    /**
     * Create a 2-page PDF. Page 1 has one invoice number/type, page 2 has different ones.
     * Verifies that multi-page text is concatenated and first-match regex behavior.
     */
    private static byte[] createMultiPagePdf(File fontFile) throws IOException {
        PDDocument doc = new PDDocument();
        try {
            // Page 1: VAT_SPECIAL with invoice 99999999
            PDPage page1 = new PDPage(PDRectangle.A4);
            doc.addPage(page1);
            PDPageContentStream cs1 = new PDPageContentStream(doc, page1);
            cs1.beginText();
            if (fontFile != null) {
                cs1.setFont(PDType0Font.load(doc, fontFile), 11);
            } else {
                cs1.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
            }
            cs1.setLeading(18);
            cs1.newLineAtOffset(50, 750);
            cs1.showText("发票号码：99999999");
            cs1.newLineAtOffset(0, -18);
            cs1.showText("增值税专用发票");
            cs1.newLineAtOffset(0, -18);
            cs1.showText("价税合计 ￥88,000.00");
            cs1.endText();
            cs1.close();

            // Page 2: VAT_NORMAL with invoice 12345678
            PDPage page2 = new PDPage(PDRectangle.A4);
            doc.addPage(page2);
            PDPageContentStream cs2 = new PDPageContentStream(doc, page2);
            cs2.beginText();
            if (fontFile != null) {
                cs2.setFont(PDType0Font.load(doc, fontFile), 11);
            } else {
                cs2.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
            }
            cs2.setLeading(18);
            cs2.newLineAtOffset(50, 750);
            cs2.showText("发票号码：12345678");
            cs2.newLineAtOffset(0, -18);
            cs2.showText("增值税普通发票");
            cs2.endText();
            cs2.close();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } finally {
            doc.close();
        }
    }

    /**
     * Locate a Chinese-capable TrueType font on the current system.
     * Returns {@code null} if none found.
     */
    private static File findChineseFont() {
        String[] candidates = {
                "C:/Windows/Fonts/simhei.ttf",       // Windows — SimHei
                "C:/Windows/Fonts/simsun.ttc",       // Windows — SimSun
                "C:/Windows/Fonts/msyh.ttc",         // Windows — Microsoft YaHei
                "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",  // Linux — WenQuanYi
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", // Linux — Noto
                "/System/Library/Fonts/PingFang.ttc", // macOS
        };
        for (String path : candidates) {
            File f = new File(path);
            if (f.exists() && f.canRead()) {
                return f;
            }
        }
        return null;
    }
}
