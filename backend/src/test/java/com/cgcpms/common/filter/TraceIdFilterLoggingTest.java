package com.cgcpms.common.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TraceIdFilter - 访问日志字段回归")
class TraceIdFilterLoggingTest {

    private final TraceIdFilter filter = new TraceIdFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(TraceIdFilter.class);

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("成功请求透传 traceId/requestId 到响应头和访问日志且不泄露敏感信息")
    void logsAccessFieldsForSuccessfulRequestWithoutSensitiveData() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects/123/members");
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-from-client");
        request.addHeader(TraceIdFilter.REQUEST_ID_HEADER, "request-from-client");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("projectId", "123"));
        request.setAttribute("accessLog.userId", 7L);
        request.setAttribute("accessLog.tenantId", 0L);
        request.addHeader("Authorization", "Bearer top-secret-token");
        request.addHeader("Cookie", "ACCESS_TOKEN=secret-cookie");
        request.setContent("token=body-secret".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(201));

        String message = lastMessage();
        assertEquals(201, response.getStatus());
        assertEquals("trace-from-client", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
        assertEquals("request-from-client", response.getHeader(TraceIdFilter.REQUEST_ID_HEADER));
        assertTrue(message.contains("traceId=trace-from-client"));
        assertTrue(message.contains("requestId=request-from-client"));
        assertTrue(message.contains("method=GET"));
        assertTrue(message.contains("path=/api/projects/123/members"));
        assertTrue(message.contains("projectId=123"));
        assertTrue(message.contains("userId=7"));
        assertTrue(message.contains("tenantId=0"));
        assertTrue(message.contains("status=201"));
        assertTrue(message.contains("exception=-"));
        assertTrue(message.matches(".*duration=\\d+.*"), message);
        assertNoSensitiveData(message);
    }

    @Test
    @DisplayName("匿名请求生成 traceId/requestId 并记录 userId/tenantId 兜底值")
    void logsFallbackIdentityForAnonymousRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/actuator/health");
        request.addHeader("Authorization", "Bearer should-not-leak");
        request.addHeader("Cookie", "ACCESS_TOKEN=anonymous-cookie");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));

        String message = lastMessage();
        String generatedTraceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        String generatedRequestId = response.getHeader(TraceIdFilter.REQUEST_ID_HEADER);
        assertGeneratedCorrelationId(generatedTraceId);
        assertGeneratedCorrelationId(generatedRequestId);
        assertTrue(message.contains("traceId=" + generatedTraceId));
        assertTrue(message.contains("requestId=" + generatedRequestId));
        assertTrue(message.contains("userId=-"));
        assertTrue(message.contains("tenantId=-"));
        assertTrue(message.contains("status=200"));
        assertNoSensitiveData(message);
    }

    @Test
    @DisplayName("异常请求透传 traceId/requestId 并记录 userId/tenantId、query projectId、500 状态和异常类型")
    void logsProjectIdStatusAndExceptionForFailedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/purchase-requests");
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-failure");
        request.addHeader(TraceIdFilter.REQUEST_ID_HEADER, "request-failure");
        request.setQueryString("projectId=456");
        request.addParameter("projectId", "456");
        request.setAttribute("accessLog.userId", 8L);
        request.setAttribute("accessLog.tenantId", 2L);
        request.addHeader("Authorization", "Bearer failing-token");
        request.addHeader("Cookie", "ACCESS_TOKEN=failing-cookie");
        request.setContent("password=super-secret".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                filter.doFilter(request, response, (req, res) -> {
                    ((MockHttpServletResponse) res).setStatus(500);
                    throw new RuntimeException("token=should-not-leak");
                }));

        String message = lastMessage();
        assertInstanceOf(RuntimeException.class, thrown);
        assertEquals("trace-failure", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
        assertEquals("request-failure", response.getHeader(TraceIdFilter.REQUEST_ID_HEADER));
        assertTrue(message.contains("traceId=trace-failure"));
        assertTrue(message.contains("requestId=request-failure"));
        assertTrue(message.contains("method=POST"));
        assertTrue(message.contains("path=/api/purchase-requests"));
        assertTrue(message.contains("projectId=456"));
        assertTrue(message.contains("userId=8"));
        assertTrue(message.contains("tenantId=2"));
        assertTrue(message.contains("status=500"));
        assertTrue(message.contains("exception=RuntimeException"));
        assertTrue(message.matches(".*duration=\\d+.*"), message);
        assertNoSensitiveData(message);
    }

    private String lastMessage() {
        assertFalse(appender.list.isEmpty(), "expected access log entry");
        return appender.list.get(appender.list.size() - 1).getFormattedMessage();
    }

    private void assertGeneratedCorrelationId(String value) {
        assertNotNull(value);
        assertFalse(value.isBlank());
        assertTrue(value.matches("[a-f0-9]{32}"), value);
    }

    private void assertNoSensitiveData(String message) {
        assertFalse(message.contains("Authorization"));
        assertFalse(message.contains("Cookie"));
        assertFalse(message.contains("password"));
        assertFalse(message.contains("token"));
        assertFalse(message.contains("top-secret-token"));
        assertFalse(message.contains("secret-cookie"));
        assertFalse(message.contains("body-secret"));
        assertFalse(message.contains("should-not-leak"));
        assertFalse(message.contains("anonymous-cookie"));
        assertFalse(message.contains("super-secret"));
        assertFalse(message.contains("failing-token"));
        assertFalse(message.contains("failing-cookie"));
    }
}
