package com.cgcpms.file;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.service.FileTypeValidator;
import com.cgcpms.file.service.FileTypeValidator.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileTypeValidator 联合校验器单元测试。
 * <p>
 * 覆盖：合法签名通过、伪装文件拒绝、空文件、无扩展名、超限、
 * 控制字符清洗、WebP、未列出扩展名、ZIP 容器伪装。
 * </p>
 */
@DisplayName("FileTypeValidator — 扩展名/MIME/魔术字节三元校验")
class FileTypeValidatorTest {

    private final FileTypeValidator validator = new FileTypeValidator();

    // ═══════════════════════════════════════════════════════════════
    // 1. PDF 合法签名通过
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PDF 合法签名通过")
    void testPdfValid() {
        byte[] content = "%PDF-1.4 content".getBytes();
        ValidationResult result = validator.validate("report.pdf", "application/pdf", content);
        assertEquals("report.pdf", result.sanitizedName());
        assertEquals(".pdf", result.extension());
        assertEquals("application/pdf", result.detectedMime());
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. evil.exe.pdf — 声明 PDF 但 MAGIC=PE → BusinessException
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("evil.exe.pdf 伪装 PDF 但魔术字节为 PE → BusinessException")
    void testPdfExtensionButExeMagic() {
        // MZ signature (PE/EXE)
        byte[] content = new byte[] {0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00};
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validate("evil.exe.pdf", "application/pdf", content));
        assertEquals("FILE_TYPE_NOT_ALLOWED", ex.getCode());
        assertTrue(ex.getMessage().contains("无法识别的文件格式"),
                "应提示无法识别的文件格式: " + ex.getMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. JPEG 合法签名通过
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("JPEG 合法签名通过")
    void testJpegValid() {
        byte[] content = new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
        };
        ValidationResult result = validator.validate("photo.jpg", "image/jpeg", content);
        assertEquals("photo.jpg", result.sanitizedName());
        assertEquals(".jpg", result.extension());
        assertEquals("image/jpeg", result.detectedMime());
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. PNG 合法签名通过
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PNG 合法签名通过")
    void testPngValid() {
        byte[] content = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        };
        ValidationResult result = validator.validate("icon.png", "image/png", content);
        assertEquals("icon.png", result.sanitizedName());
        assertEquals(".png", result.extension());
        assertEquals("image/png", result.detectedMime());
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. DOCX 合法（ZIP + Content_Types）
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DOCX 合法签名通过（ZIP + Content_Types.xml）")
    void testDocxValid() {
        byte[] content = officeContent();
        ValidationResult result = validator.validate("doc.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content);
        assertEquals("doc.docx", result.sanitizedName());
        assertEquals(".docx", result.extension());
        assertTrue(result.detectedMime().startsWith("application/vnd.openxmlformats-officedocument"));
    }

    @Test
    @DisplayName("XLSX 合法签名通过（ZIP + Content_Types.xml）")
    void testXlsxValid() {
        ValidationResult result = validator.validate("sheet.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", officeContent());
        assertEquals("sheet.xlsx", result.sanitizedName());
        assertEquals(".xlsx", result.extension());
        assertTrue(result.detectedMime().startsWith("application/vnd.openxmlformats-officedocument"));
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. 空文件 → BusinessException
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("空文件名 → BusinessException")
    void testEmptyFilename() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validate("", "application/pdf", "data".getBytes()));
        assertEquals("FILE_EMPTY", ex.getCode());
    }

    @Test
    @DisplayName("空内容 → BusinessException")
    void testEmptyContent() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validate("test.pdf", "application/pdf", new byte[0]));
        assertEquals("FILE_EMPTY", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. 无扩展名 → BusinessException
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("无扩展名 → BusinessException")
    void testNoExtension() {
        byte[] content = "%PDF-1.4".getBytes();
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validate("noext", "application/pdf", content));
        assertEquals("FILE_TYPE_NOT_ALLOWED", ex.getCode());
        assertTrue(ex.getMessage().contains("缺少扩展名"));
    }

    // ═══════════════════════════════════════════════════════════════
    // 8. 超限文件 → BusinessException
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("超限文件（>20MB）→ BusinessException")
    void testOversizedFile() {
        byte[] bigContent = new byte[20 * 1024 * 1024 + 1];
        // Set PDF signature at the start
        System.arraycopy("%PDF-".getBytes(), 0, bigContent, 0, 5);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validate("big.pdf", "application/pdf", bigContent));
        assertEquals("FILE_TOO_LARGE", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 9. 控制字符文件名 → 清洗通过
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("含控制字符的文件名 → 清洗为下划线后通过")
    void testControlCharInFilename() {
        // ASCII 0x01 (SOH) in filename
        byte[] content = "%PDF-1.4 test".getBytes();
        ValidationResult result = validator.validate("report.pdf", "application/pdf", content);
        // 控制字符被替换为 _
        assertTrue(result.sanitizedName().contains("_"));
        assertFalse(result.sanitizedName().chars().anyMatch(c -> c < 0x20 && c != '\t' && c != '\n' && c != '\r'));
        assertEquals(".pdf", result.extension());
        assertEquals("application/pdf", result.detectedMime());
    }

    // ═══════════════════════════════════════════════════════════════
    // 10. WebP 合法签名通过
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("WebP 合法签名通过")
    void testWebpValid() {
        // RIFF + size + WEBP
        byte[] content = new byte[] {
                0x52, 0x49, 0x46, 0x46, // RIFF
                0x10, 0x00, 0x00, 0x00, // file size - 8
                0x57, 0x45, 0x42, 0x50, // WEBP
                0x56, 0x50, 0x38, 0x20  // VP8
        };
        ValidationResult result = validator.validate("image.webp", "image/webp", content);
        assertEquals("image.webp", result.sanitizedName());
        assertEquals(".webp", result.extension());
        assertEquals("image/webp", result.detectedMime());
    }

    // ═══════════════════════════════════════════════════════════════
    // 11. 未列出的扩展名（如 .bmp）→ BusinessException
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("未列出的扩展名 .bmp → BusinessException")
    void testBmpNotAllowed() {
        byte[] content = new byte[] {0x42, 0x4D, 0x00, 0x00, 0x00, 0x00}; // BM header
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validate("image.bmp", "image/bmp", content));
        assertEquals("FILE_TYPE_NOT_ALLOWED", ex.getCode());
        assertTrue(ex.getMessage().contains(".bmp"));
    }

    // ═══════════════════════════════════════════════════════════════
    // 12. ZIP 容器伪装（.jar 宣称 PDF）→ BusinessException
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName(".jar 文件扩展名在允许列表外 → BusinessException")
    void testJarNotAllowed() {
        byte[] content = new byte[] {0x50, 0x4B, 0x03, 0x04};
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validate("malware.jar", "application/pdf", content));
        assertEquals("FILE_TYPE_NOT_ALLOWED", ex.getCode());
        assertTrue(ex.getMessage().contains(".jar"));
    }

    // ═══════════════════════════════════════════════════════════════
    // 13. 补充：MIME 声明与魔术字节不匹配
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("客户端声明 image/png 但魔术字节为 PDF → BusinessException")
    void testMimeMismatchWithMagic() {
        byte[] content = "%PDF-1.4 test".getBytes();
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validate("doc.pdf", "image/png", content));
        assertEquals("FILE_TYPE_NOT_ALLOWED", ex.getCode());
        assertTrue(ex.getMessage().contains("类型不匹配") || ex.getMessage().contains("不匹配"));
    }

    @Test
    @DisplayName("DOCX 文件声明为 Excel MIME → BusinessException")
    void testOfficeMimeMismatch() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                validator.validate("doc.docx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        officeContent()));
        assertEquals("FILE_TYPE_NOT_ALLOWED", ex.getCode());
        assertTrue(ex.getMessage().contains("类型不匹配") || ex.getMessage().contains("不匹配"));
    }

    private byte[] officeContent() {
        byte[] header = new byte[] {0x50, 0x4B, 0x03, 0x04};
        byte[] contentTypes = "Content_Types]".getBytes();
        byte[] content = new byte[header.length + contentTypes.length + 100];
        System.arraycopy(header, 0, content, 0, header.length);
        System.arraycopy(contentTypes, 0, content, 60, contentTypes.length);
        return content;
    }
}
