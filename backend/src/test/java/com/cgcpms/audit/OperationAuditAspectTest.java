package com.cgcpms.audit;

import com.cgcpms.audit.event.OperationAuditEvent;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.service.FileService;
import com.cgcpms.file.vo.SysFileVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "minio.enabled=true",
        "minio.endpoint=http://localhost:9000",
        "minio.access-key=test",
        "minio.secret-key=test",
        "minio.bucket=test-bucket"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("local")
@DisplayName("OperationAuditAspect — 切面审计测试")
class OperationAuditAspectTest {

    @TestConfiguration
    static class TestListenerConfig {
        static final AtomicReference<OperationAuditEvent> captured = new AtomicReference<>();
        static final CountDownLatch latch = new CountDownLatch(1);

        @Bean
        AuditTestListener auditTestListener() {
            return new AuditTestListener(captured, latch);
        }
    }

    static class AuditTestListener {
        private final AtomicReference<OperationAuditEvent> ref;
        private final CountDownLatch latch;

        AuditTestListener(AtomicReference<OperationAuditEvent> ref, CountDownLatch latch) {
            this.ref = ref;
            this.latch = latch;
        }

        @EventListener
        public void onEvent(OperationAuditEvent event) {
            ref.set(event);
            latch.countDown();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationEventPublisher publisher;

    @MockitoBean
    private FileService fileService;

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
        SecurityContextHolder.clearContext();
        TestListenerConfig.captured.set(null);
    }

    @Test
    @DisplayName("MockMvc 基础设施可用 — Spring 上下文正确加载")
    void testApplicationContextLoaded() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        assertNotNull(mockMvc, "MockMvc should be autowired");
        assertNotNull(publisher, "ApplicationEventPublisher should be autowired");
        assertNotNull(TestListenerConfig.captured, "captured reference should be initialized");
    }

    @Test
    @DisplayName("直接发布事件 — 验证事件监听机制可用")
    void testEventPublishAndCapture() throws Exception {
        OperationAuditEvent event = OperationAuditEvent.builder()
                .tenantId(0L)
                .userId(1L)
                .operationType("LOGIN")
                .businessType(null)
                .businessId(null)
                .httpMethod("POST")
                .requestPath("/api/auth/login")
                .successFlag(true)
                .errorCode(null)
                .sourceIp("127.0.0.1")
                .durationMs(50)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        publisher.publishEvent(event);
        boolean received = TestListenerConfig.latch.await(2, TimeUnit.SECONDS);
        assertTrue(received, "事件应在2秒内被捕获");
        assertNotNull(TestListenerConfig.captured.get());
        assertEquals("LOGIN", TestListenerConfig.captured.get().operationType());
        assertEquals("POST", TestListenerConfig.captured.get().httpMethod());
        assertTrue(TestListenerConfig.captured.get().successFlag());
    }

    @Test
    @DisplayName("异常事件 — successFlag=false 并包含 errorCode")
    void testFailedEventHasErrorCode() throws Exception {
        OperationAuditEvent event = OperationAuditEvent.builder()
                .tenantId(0L)
                .userId(1L)
                .operationType("UPDATE")
                .businessType("CONTRACT")
                .businessId("999")
                .httpMethod("PUT")
                .requestPath("/api/contracts/999")
                .successFlag(false)
                .errorCode("BusinessException")
                .sourceIp("127.0.0.1")
                .durationMs(300)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        publisher.publishEvent(event);
        boolean received = TestListenerConfig.latch.await(2, TimeUnit.SECONDS);
        assertTrue(received, "事件应在2秒内被捕获");
        assertNotNull(TestListenerConfig.captured.get());
        assertFalse(TestListenerConfig.captured.get().successFlag());
        assertEquals("BusinessException", TestListenerConfig.captured.get().errorCode());
    }

    @Test
    @DisplayName("事件不可变性 — record 类字段应通过构造器设置且不可修改")
    void testEventImmutability() {
        OperationAuditEvent event = OperationAuditEvent.builder()
                .tenantId(0L)
                .userId(1L)
                .operationType("DELETE")
                .businessType("RECEIPT")
                .businessId("777")
                .httpMethod("DELETE")
                .requestPath("/api/receipts/777")
                .successFlag(true)
                .errorCode(null)
                .sourceIp("10.0.0.1")
                .durationMs(80)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        assertEquals("DELETE", event.operationType());
        assertEquals("RECEIPT", event.businessType());
        assertEquals("777", event.businessId());
        assertEquals("10.0.0.1", event.sourceIp());
    }

    @Test
    @DisplayName("附件上传成功发布 UPLOAD 审计事件并保留业务对象上下文")
    void testFileUploadPublishesSuccessAuditEvent() throws Exception {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        setFileAuthority("file:upload");
        MockMultipartFile file = new MockMultipartFile(
                "file", "audit.txt", "text/plain", "audit".getBytes());
        SysFileVO vo = new SysFileVO();
        vo.setId("99001");
        vo.setBusinessType("CONTRACT");
        vo.setBusinessId("88001");
        when(fileService.upload(file, "CONTRACT", 88001L)).thenReturn(vo);

        mockMvc.perform(multipart("/files/upload")
                        .file(file)
                        .param("businessType", "CONTRACT")
                        .param("businessId", "88001"))
                .andExpect(status().isOk());

        OperationAuditEvent event = TestListenerConfig.captured.get();
        assertNotNull(event);
        assertEquals(TestUserContext.TENANT_0, event.tenantId());
        assertEquals(TestUserContext.USER_ADMIN, event.userId());
        assertEquals("UPLOAD", event.operationType());
        assertEquals("FILE", event.businessType());
        assertEquals("88001", event.businessId());
        assertEquals("POST", event.httpMethod());
        assertEquals("/files/upload", event.requestPath());
        assertTrue(event.successFlag());
        assertNull(event.errorCode());
    }

    @Test
    @DisplayName("附件下载成功发布 DOWNLOAD 审计事件")
    void testFileDownloadPublishesSuccessAuditEvent() throws Exception {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        setFileAuthority("file:query");
        when(fileService.getPresignedUrl(71003L)).thenReturn("http://download.local/file");

        mockMvc.perform(get("/files/{id}/url", 71003L))
                .andExpect(status().isOk());

        OperationAuditEvent event = TestListenerConfig.captured.get();
        assertNotNull(event);
        assertEquals(TestUserContext.TENANT_0, event.tenantId());
        assertEquals(TestUserContext.USER_ADMIN, event.userId());
        assertEquals("DOWNLOAD", event.operationType());
        assertEquals("FILE", event.businessType());
        assertEquals("71003", event.businessId());
        assertEquals("GET", event.httpMethod());
        assertEquals("/files/71003/url", event.requestPath());
        assertTrue(event.successFlag());
        assertNull(event.errorCode());
    }

    @Test
    @DisplayName("附件删除成功发布 DELETE 审计事件")
    void testFileDeletePublishesSuccessAuditEvent() throws Exception {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        setFileAuthority("file:delete");

        mockMvc.perform(delete("/files/{id}", 71001L))
                .andExpect(status().isOk());

        OperationAuditEvent event = TestListenerConfig.captured.get();
        assertNotNull(event);
        assertEquals(TestUserContext.TENANT_0, event.tenantId());
        assertEquals(TestUserContext.USER_ADMIN, event.userId());
        assertEquals("DELETE", event.operationType());
        assertEquals("FILE", event.businessType());
        assertEquals("71001", event.businessId());
        assertEquals("DELETE", event.httpMethod());
        assertEquals("/files/71001", event.requestPath());
        assertTrue(event.successFlag());
        assertNull(event.errorCode());
    }

    @Test
    @DisplayName("附件删除拒绝发布失败审计事件")
    void testFileDeleteDeniedPublishesFailedAuditEvent() throws Exception {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        setFileAuthority("file:delete");
        doThrow(new BusinessException("FILE_ACCESS_DENIED", "无权删除该文件"))
                .when(fileService).delete(71002L);

        mockMvc.perform(delete("/files/{id}", 71002L))
                .andExpect(status().isBadRequest());

        OperationAuditEvent event = TestListenerConfig.captured.get();
        assertNotNull(event);
        assertEquals("DELETE", event.operationType());
        assertEquals("FILE", event.businessType());
        assertEquals("71002", event.businessId());
        assertEquals("DELETE", event.httpMethod());
        assertEquals("/files/71002", event.requestPath());
        assertFalse(event.successFlag());
        assertEquals("BusinessException", event.errorCode());
    }

    private void setFileAuthority(String authority) {
        var auth = new UsernamePasswordAuthenticationToken(
                "audit-test", "N/A", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
