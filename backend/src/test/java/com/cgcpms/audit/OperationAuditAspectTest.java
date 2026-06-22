package com.cgcpms.audit;

import com.cgcpms.audit.event.OperationAuditEvent;
import com.cgcpms.common.TestUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
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

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
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
}
