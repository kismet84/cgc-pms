package com.cgcpms.file;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.file.service.FileService;
import io.minio.MinioClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件上传安全边界验证。
 * <p>
 * 覆盖 FileService.upload() 的输入校验逻辑：
 * 类型白名单、大小限制、必填参数、路径遍历防御。
 * MinIO 和权限校验器均被 mock，使所有校验路径可达。
 * </p>
 */
@SpringBootTest(properties = {
        "minio.enabled=true",
        "minio.endpoint=http://localhost:9000",
        "minio.access-key=test",
        "minio.secret-key=test",
        "minio.bucket=test-bucket"
})
@ActiveProfiles("local")
@DisplayName("FileService — upload validation security boundary")
class FileServiceTest {

    @Autowired
    private FileService fileService;

    /** Mock MinIO client 避免真实网络连接 */
    @MockBean
    private MinioClient minioClient;

    /** Mock 权限校验器，使 path traversal 测试可达 */
    @MockBean
    private BusinessObjectAuthorizer authorizer;

    @BeforeEach
    void setupContext() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
    }

    @AfterEach
    void clearContext() {
        TestUserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. 空文件拒绝
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("upload rejects empty file with FILE_EMPTY")
    void testUploadRejectsEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", new byte[0]);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                fileService.upload(emptyFile, "CONTRACT", 1L));
        assertEquals("FILE_EMPTY", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. 超大文件拒绝 (MAX_FILE_SIZE = 50MB)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("upload rejects oversized file (>50MB) with FILE_TOO_LARGE")
    void testUploadRejectsOversizedFile() {
        byte[] bigContent = new byte[51 * 1024 * 1024]; // 51 MB
        // Set valid PDF signature at the start so size check is reached
        System.arraycopy("%PDF-".getBytes(), 0, bigContent, 0, 5);
        MockMultipartFile bigFile = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", bigContent);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                fileService.upload(bigFile, "CONTRACT", 1L));
        assertEquals("FILE_TOO_LARGE", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. 不允许的文件类型拒绝
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("upload rejects disallowed extension with FILE_TYPE_NOT_ALLOWED")
    void testUploadRejectsDisallowedType() {
        MockMultipartFile exeFile = new MockMultipartFile(
                "file", "malware.exe", "application/x-msdownload", "evil".getBytes());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                fileService.upload(exeFile, "CONTRACT", 1L));
        assertEquals("FILE_TYPE_NOT_ALLOWED", ex.getCode());
        assertTrue(ex.getMessage().contains(".exe"), "错误消息应包含被拒绝的扩展名");
    }

    @Test
    @DisplayName("upload rejects script file (.sh) with FILE_TYPE_NOT_ALLOWED")
    void testUploadRejectsScriptFile() {
        MockMultipartFile shFile = new MockMultipartFile(
                "file", "script.sh", "text/x-sh", "#!/bin/bash".getBytes());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                fileService.upload(shFile, "CONTRACT", 1L));
        assertEquals("FILE_TYPE_NOT_ALLOWED", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. 必填参数校验
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("upload rejects null businessType with FILE_PARAM_MISSING")
    void testUploadRejectsNullBusinessType() {
        byte[] pdfContent = "%PDF-1.4 valid".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", pdfContent);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                fileService.upload(file, null, 1L));
        assertEquals("FILE_PARAM_MISSING", ex.getCode());
    }

    @Test
    @DisplayName("upload rejects blank businessType with FILE_PARAM_MISSING")
    void testUploadRejectsBlankBusinessType() {
        byte[] pdfContent = "%PDF-1.4 valid".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", pdfContent);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                fileService.upload(file, "   ", 1L));
        assertEquals("FILE_PARAM_MISSING", ex.getCode());
    }

    @Test
    @DisplayName("upload rejects null businessId with FILE_PARAM_MISSING")
    void testUploadRejectsNullBusinessId() {
        byte[] pdfContent = "%PDF-1.4 valid".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", pdfContent);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                fileService.upload(file, "CONTRACT", null));
        assertEquals("FILE_PARAM_MISSING", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. 路径遍历防御 — businessType 格式校验
    // (权限校验器已 mock，使 path traversal 校验可达)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("upload rejects businessType with path traversal chars (when authorizer bypassed)")
    void testUploadRejectsPathTraversalInBusinessType() {
        byte[] pdfContent = "%PDF-1.4 valid".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", pdfContent);

        // businessType 含 "/" 会被正则 [A-Za-z0-9_-]+ 拒绝
        BusinessException ex = assertThrows(BusinessException.class, () ->
                fileService.upload(file, "CONTRACT/../etc", 1L));
        assertEquals("FILE_PARAM_INVALID", ex.getCode());
    }

    @Test
    @DisplayName("upload allows valid businessType with alphanumeric, dash, underscore")
    void testUploadAllowsValidBusinessTypeFormat() {
        byte[] pdfContent = "%PDF-1.4 valid".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", pdfContent);

        // 合法 businessType 格式 + mocked authorizer + mocked MinIO
        // 后续操作（DB insert）在 H2 中正常执行
        try {
            fileService.upload(file, "MY-TYPE_01", 1L);
            // upload 成功说明格式校验通过
        } catch (BusinessException e) {
            // 可能因 authorizer 不识别的 businessType 或 MinIO 连接失败
            // 但不应该是格式校验失败
            assertNotEquals("FILE_PARAM_INVALID", e.getCode(),
                    "合法格式 businessType 不应被格式校验拒绝: " + e.getCode());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. 伪装文件 — MIME/扩展名与实际魔术字节不匹配
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("upload rejects file with PDF extension but PE/EXE magic bytes")
    void testUploadRejectsPdfWithExeMagic() {
        // MZ header (PE executable) masquerading as .pdf
        byte[] exeContent = new byte[] {0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", exeContent);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                fileService.upload(fakePdf, "CONTRACT", 1L));
        assertEquals("FILE_TYPE_NOT_ALLOWED", ex.getCode());
        assertTrue(ex.getMessage().contains("无法识别") || ex.getMessage().contains("不匹配"),
                "应提示文件格式无法识别或类型不匹配: " + ex.getMessage());
    }
}
