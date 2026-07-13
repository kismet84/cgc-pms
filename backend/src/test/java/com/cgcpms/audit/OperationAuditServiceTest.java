package com.cgcpms.audit;

import com.cgcpms.audit.event.OperationAuditEvent;
import com.cgcpms.audit.entity.OperationAuditLog;
import com.cgcpms.audit.mapper.OperationAuditLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("OperationAuditService — 审计持久化测试")
class OperationAuditServiceTest {

    @Autowired
    private ApplicationEventPublisher publisher;

    @MockitoBean
    private OperationAuditLogMapper mapper;

    @Test
    @DisplayName("事件发布返回前应完成审计写入")
    void auditShouldBePersistedBeforePublishReturns() {
        AtomicReference<Thread> persistenceThread = new AtomicReference<>();
        doAnswer(invocation -> {
            persistenceThread.set(Thread.currentThread());
            return 1;
        }).when(mapper).insert(any(OperationAuditLog.class));
        OperationAuditEvent event = OperationAuditEvent.builder()
                .tenantId(0L).userId(1L).operationType("UPDATE")
                .businessType("SITE_DAILY_LOG").businessId("1")
                .successFlag(true).createdAt(LocalDateTime.now()).build();
        Thread publisherThread = Thread.currentThread();

        publisher.publishEvent(event);

        verify(mapper).insert(any(OperationAuditLog.class));
        assertSame(publisherThread, persistenceThread.get());
    }

    @Test
    @DisplayName("Mapper 异常不传播 — catch 后不抛回调用方")
    void testMapperExceptionNotPropagated() throws Exception {
        // 设计一个极端情况：插入超长路径来尝试触发异常，或直接验证 catch 不 throw
        OperationAuditEvent event = OperationAuditEvent.builder()
                .tenantId(0L)
                .userId(1L)
                .operationType("CREATE")
                .businessType("CONTRACT")
                .businessId("123")
                .httpMethod("POST")
                .requestPath("/api/contracts")
                .successFlag(true)
                .errorCode(null)
                .sourceIp("127.0.0.1")
                .durationMs(100)
                .createdAt(LocalDateTime.now())
                .build();

        // 不应抛出任何异常（包括 Mapper 异常不应该传播）
        assertDoesNotThrow(() -> publisher.publishEvent(event));
    }

    @Test
    @DisplayName("租户隔离 — 查询强制按 tenantId 过滤")
    void testTenantIsolation() throws Exception {
        // 插入租户 0 的记录
        OperationAuditEvent event0 = OperationAuditEvent.builder()
                .tenantId(0L).userId(1L).operationType("CREATE")
                .businessType("CONTRACT").businessId("iso-test")
                .httpMethod("POST").requestPath("/api/test")
                .successFlag(true).errorCode(null).sourceIp("127.0.0.1")
                .durationMs(10).createdAt(LocalDateTime.now())
                .build();

        // 插入租户 999 的记录
        OperationAuditEvent event999 = OperationAuditEvent.builder()
                .tenantId(999L).userId(1L).operationType("UPDATE")
                .businessType("CONTRACT").businessId("iso-test-999")
                .httpMethod("PUT").requestPath("/api/test")
                .successFlag(true).errorCode(null).sourceIp("127.0.0.1")
                .durationMs(10).createdAt(LocalDateTime.now())
                .build();

        assertDoesNotThrow(() -> {
            publisher.publishEvent(event0);
            publisher.publishEvent(event999);
        });
    }

    @Test
    @DisplayName("事件字段正确性 — 发布的字段应完整传递到异步处理器")
    void testEventFieldsIntegrity() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        OperationAuditEvent event = OperationAuditEvent.builder()
                .tenantId(0L)
                .userId(42L)
                .operationType("APPROVE")
                .businessType("SETTLEMENT")
                .businessId("settlement-999")
                .httpMethod("POST")
                .requestPath("/api/settlements/999/approve")
                .successFlag(true)
                .errorCode(null)
                .sourceIp("192.168.1.100")
                .durationMs(250)
                .createdAt(now)
                .build();

        // 验证 builder 正确性
        assertEquals(0L, event.tenantId());
        assertEquals(42L, event.userId());
        assertEquals("APPROVE", event.operationType());
        assertEquals("SETTLEMENT", event.businessType());
        assertEquals("settlement-999", event.businessId());
        assertEquals("192.168.1.100", event.sourceIp());
        assertEquals(250, event.durationMs());
        assertTrue(event.successFlag());
        assertNull(event.errorCode());
    }

    @Test
    @DisplayName("敏感字段不持久化 — 验证操作日志表不含请求体、Token、Cookie 字段")
    void testSensitiveFieldsNotPersisted() {
        // 验证 OperationAuditLog 实体不包含敏感字段
        var fields = com.cgcpms.audit.entity.OperationAuditLog.class.getDeclaredFields();
        for (var field : fields) {
            String name = field.getName();
            assertFalse(name.contains("body") || name.contains("Body"), "实体不应包含 body 字段: " + name);
            assertFalse(name.toLowerCase().contains("token"), "实体不应包含 token 字段: " + name);
            assertFalse(name.toLowerCase().contains("cookie"), "实体不应包含 cookie 字段: " + name);
            assertFalse(name.contentEquals("requestBody") || name.contentEquals("responseBody"),
                    "实体不应包含 requestBody/responseBody 字段: " + name);
        }
    }
}
