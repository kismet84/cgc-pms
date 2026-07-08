package com.cgcpms.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.file.service.FileService;
import io.micrometer.core.instrument.MeterRegistry;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @Autowired
    private SysFileMapper sysFileMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

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
    // 2. 超大文件拒绝 (MAX_FILE_SIZE = 20MB)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("upload rejects oversized file (>20MB) with FILE_TOO_LARGE")
    void testUploadRejectsOversizedFile() {
        byte[] bigContent = new byte[20 * 1024 * 1024 + 1];
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

    @Test
    @DisplayName("upload retries putObject once then succeeds and inserts one record")
    void testUploadRetriesPutObjectThenSucceeds() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "retry.pdf", "application/pdf", "%PDF-1.4 retry".getBytes());
        String businessType = "RETRY_OK";
        long businessId = Math.abs(System.nanoTime());
        LambdaQueryWrapper<SysFile> query = new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getBusinessType, businessType)
                .eq(SysFile::getBusinessId, businessId);
        assertEquals(0L, sysFileMapper.selectCount(query));

        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new RuntimeException("transient minio error"))
                .thenReturn(null);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio.local/test-bucket/RETRY_OK/file.pdf?X-Amz-Signature=test");

        assertDoesNotThrow(() -> fileService.upload(file, businessType, businessId));
        assertEquals(1L, sysFileMapper.selectCount(query));
        verify(minioClient, times(2)).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("upload fails after max putObject retries and does not insert record")
    void testUploadFailsAfterMaxPutObjectRetries() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "retry-fail.pdf", "application/pdf", "%PDF-1.4 retry fail".getBytes());
        String businessType = "RETRY_FAIL";
        long businessId = Math.abs(System.nanoTime());
        LambdaQueryWrapper<SysFile> query = new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getBusinessType, businessType)
                .eq(SysFile::getBusinessId, businessId);
        assertEquals(0L, sysFileMapper.selectCount(query));

        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new RuntimeException("minio error 1"))
                .thenThrow(new RuntimeException("minio error 2"))
                .thenThrow(new RuntimeException("minio error 3"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.upload(file, businessType, businessId));
        assertEquals("FILE_UPLOAD_FAILED", ex.getCode());
        assertEquals(0L, sysFileMapper.selectCount(query));
        verify(minioClient, times(3)).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("upload classifies MinIO connectivity failures as FILE_STORAGE_UNAVAILABLE without leaking config")
    void testUploadClassifiesMinioConnectivityFailure() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "storage-down.pdf", "application/pdf", "%PDF-1.4 storage down".getBytes());
        String businessType = "STORAGE_DOWN";
        long businessId = Math.abs(System.nanoTime());

        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new ConnectException("connect http://localhost:9000 with accessKey=test secretKey=test"))
                .thenThrow(new ConnectException("connect http://localhost:9000 with accessKey=test secretKey=test"))
                .thenThrow(new ConnectException("connect http://localhost:9000 with accessKey=test secretKey=test"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.upload(file, businessType, businessId));

        assertEquals("FILE_STORAGE_UNAVAILABLE", ex.getCode());
        assertEquals("文件服务暂不可用，请稍后重试", ex.getMessage());
        assertFalse(ex.getMessage().contains("http://localhost:9000"));
        assertFalse(ex.getMessage().contains("accessKey"));
        assertFalse(ex.getMessage().contains("secretKey"));
        verify(minioClient, times(3)).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("upload keeps non-connectivity putObject failures as FILE_UPLOAD_FAILED")
    void testUploadClassifiesGenericPutObjectFailure() throws Exception {
        double before = uploadFailureCount("FILE_UPLOAD_FAILED");
        MockMultipartFile file = new MockMultipartFile(
                "file", "upload-failed.pdf", "application/pdf", "%PDF-1.4 upload failed".getBytes());
        String businessType = "UPLOAD_FAIL";
        long businessId = Math.abs(System.nanoTime());

        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new IllegalStateException("stream aborted"))
                .thenThrow(new IllegalStateException("stream aborted"))
                .thenThrow(new IllegalStateException("stream aborted"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.upload(file, businessType, businessId));

        assertEquals("FILE_UPLOAD_FAILED", ex.getCode());
        assertEquals("文件上传失败，请稍后重试", ex.getMessage());
        verify(minioClient, times(3)).putObject(any(PutObjectArgs.class));
        assertEquals(before + 1, uploadFailureCount("FILE_UPLOAD_FAILED"), 0.001);
        assertUploadFailureMetricHasNoSensitiveTags("FILE_UPLOAD_FAILED");
    }

    @Test
    @DisplayName("getPresignedUrl rejects files outside current tenant as not found")
    void testGetPresignedUrlHidesCrossTenantFile() {
        SysFile file = insertFile("CONTRACT", 30001L, 9999L,
                "contract.pdf", "application/pdf");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.getPresignedUrl(file.getId()));

        assertEquals("FILE_NOT_FOUND", ex.getCode());
        verify(authorizer, never()).checkReadAccess(any(), any());
        verifyNoInteractions(minioClient);
    }

    @Test
    @DisplayName("getPresignedUrl rejects business object read denial before temporary link")
    void testGetPresignedUrlRejectsBusinessObjectReadDenied() {
        SysFile file = insertFile("CONTRACT", 30002L, TestUserContext.TENANT_0,
                "contract.pdf", "application/pdf");
        doThrow(new BusinessException("FILE_ACCESS_DENIED", "无权访问该合同文件"))
                .when(authorizer).checkReadAccess("CONTRACT", 30002L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.getPresignedUrl(file.getId()));

        assertEquals("FILE_ACCESS_DENIED", ex.getCode());
        verifyNoInteractions(minioClient);
    }

    @Test
    @DisplayName("delete rejects cross-tenant file before write auth and MinIO removal")
    void testDeleteHidesCrossTenantFileBeforeSideEffects() {
        SysFile file = insertFile("CONTRACT", 30004L, 9999L,
                "contract-delete.pdf", "application/pdf");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.delete(file.getId()));

        assertEquals("FILE_NOT_FOUND", ex.getCode());
        assertEquals(1, countRawFileRows(file.getId(), 0));
        verify(authorizer, never()).checkWriteAccess(any(), any());
        verifyNoInteractions(minioClient);
    }

    @Test
    @DisplayName("delete rejects business object write denial before MinIO removal")
    void testDeleteRejectsBusinessObjectWriteDeniedBeforeMinioRemoval() {
        SysFile file = insertFile("CONTRACT", 30005L, TestUserContext.TENANT_0,
                "contract-denied.pdf", "application/pdf");
        doThrow(new BusinessException("FILE_ACCESS_DENIED", "无权删除该合同文件"))
                .when(authorizer).checkWriteAccess("CONTRACT", 30005L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.delete(file.getId()));

        assertEquals("FILE_ACCESS_DENIED", ex.getCode());
        assertNotNull(sysFileMapper.selectById(file.getId()));
        verifyNoInteractions(minioClient);
    }

    @Test
    @DisplayName("delete removes object from MinIO then logically deletes record")
    void testDeleteRemovesObjectThenDeletesRecord() throws Exception {
        SysFile file = insertFile("CONTRACT", 30006L, TestUserContext.TENANT_0,
                "contract-ok.pdf", "application/pdf");

        fileService.delete(file.getId());

        var args = org.mockito.ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(args.capture());
        assertEquals("test-bucket", args.getValue().bucket());
        assertEquals("CONTRACT/30006/contract-ok.pdf", args.getValue().object());
        assertNull(sysFileMapper.selectById(file.getId()));
    }

    @Test
    @DisplayName("getPresignedUrl keeps text downloads as attachment with utf-8 plain text")
    void testGetPresignedUrlSetsTextDownloadHeaders() throws Exception {
        SysFile file = insertFile("CONTRACT", 30003L, TestUserContext.TENANT_0,
                "notes.txt", "text/plain");
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio.local/test-bucket/CONTRACT/30003/notes.txt?X-Amz-Signature=test");

        String url = fileService.getPresignedUrl(file.getId());

        assertTrue(url.contains("X-Amz-Signature=test"));
        var args = org.mockito.ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(args.capture());
        assertEquals(Method.GET, args.getValue().method());
        assertEquals(5 * 60, args.getValue().expiry());
        assertTrue(args.getValue().extraQueryParams()
                .get("response-content-type")
                .contains("text/plain; charset=utf-8"));
        assertTrue(args.getValue().extraQueryParams()
                .get("response-content-disposition")
                .contains("attachment; filename=\"notes.txt\""));
    }

    @Test
    @DisplayName("getPresignedUrl rejects unsigned public bucket URL")
    void testGetPresignedUrlRejectsUnsignedPublicUrl() throws Exception {
        SysFile file = insertFile("CONTRACT", 30007L, TestUserContext.TENANT_0,
                "public.pdf", "application/pdf");
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio.local/test-bucket/CONTRACT/30007/public.pdf");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.getPresignedUrl(file.getId()));

        assertEquals("FILE_URL_ERROR", ex.getCode());
        assertEquals("获取下载链接失败，请稍后重试", ex.getMessage());
        var args = org.mockito.ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(args.capture());
        assertEquals(Method.GET, args.getValue().method());
        assertEquals(5 * 60, args.getValue().expiry());
    }

    private SysFile insertFile(String businessType, Long businessId, Long tenantId,
                               String fileName, String contentType) {
        SysFile file = new SysFile();
        file.setTenantId(tenantId);
        file.setBusinessType(businessType);
        file.setBusinessId(businessId);
        file.setFileName(fileName);
        file.setOriginalName(fileName);
        file.setFileSize(12L);
        file.setContentType(contentType);
        file.setStoragePath(businessType + "/" + businessId + "/" + fileName);
        file.setBucketName("test-bucket");
        sysFileMapper.insert(file);
        return file;
    }

    private Integer countRawFileRows(Long id, int deletedFlag) {
        return jdbcTemplate.queryForObject(
                "select count(*) from sys_file where id = ? and deleted_flag = ?",
                Integer.class, id, deletedFlag);
    }

    private double uploadFailureCount(String code) {
        var counter = meterRegistry.find("file.upload.failures").tag("code", code).counter();
        return counter == null ? 0 : counter.count();
    }

    private void assertUploadFailureMetricHasNoSensitiveTags(String code) {
        var counter = meterRegistry.find("file.upload.failures").tag("code", code).counter();
        assertNotNull(counter);
        assertTrue(counter.getId().getTags().stream()
                .noneMatch(tag -> {
                    String key = tag.getKey().toLowerCase();
                    return key.contains("password")
                            || key.contains("token")
                            || key.contains("content")
                            || key.contains("filename")
                            || key.contains("file_name");
                }));
    }
}
