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
    @DisplayName("成功请求记录 method/path/projectId/status/duration/exception 且不泄露敏感信息")
    void logsAccessFieldsForSuccessfulRequestWithoutSensitiveData() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects/123/members");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("projectId", "123"));
        request.addHeader("Authorization", "Bearer top-secret-token");
        request.addHeader("Cookie", "ACCESS_TOKEN=secret-cookie");
        request.setContent("token=body-secret".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(201));

        String message = lastMessage();
        assertEquals(201, response.getStatus());
        assertTrue(message.contains("method=GET"));
        assertTrue(message.contains("path=/api/projects/123/members"));
        assertTrue(message.contains("projectId=123"));
        assertTrue(message.contains("status=201"));
        assertTrue(message.contains("exception=-"));
        assertTrue(message.matches(".*duration=\\d+.*"), message);
        assertFalse(message.contains("top-secret-token"));
        assertFalse(message.contains("secret-cookie"));
        assertFalse(message.contains("body-secret"));
    }

    @Test
    @DisplayName("异常请求记录 query projectId、500 状态和异常类型")
    void logsProjectIdStatusAndExceptionForFailedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/purchase-requests");
        request.setQueryString("projectId=456");
        request.addParameter("projectId", "456");
        request.setContent("password=super-secret".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                filter.doFilter(request, response, (req, res) -> {
                    ((MockHttpServletResponse) res).setStatus(500);
                    throw new RuntimeException("token=should-not-leak");
                }));

        String message = lastMessage();
        assertInstanceOf(RuntimeException.class, thrown);
        assertTrue(message.contains("method=POST"));
        assertTrue(message.contains("path=/api/purchase-requests"));
        assertTrue(message.contains("projectId=456"));
        assertTrue(message.contains("status=500"));
        assertTrue(message.contains("exception=RuntimeException"));
        assertTrue(message.matches(".*duration=\\d+.*"), message);
        assertFalse(message.contains("should-not-leak"));
        assertFalse(message.contains("super-secret"));
    }

    private String lastMessage() {
        assertFalse(appender.list.isEmpty(), "expected access log entry");
        return appender.list.get(appender.list.size() - 1).getFormattedMessage();
    }
}
