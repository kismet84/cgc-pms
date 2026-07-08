package com.cgcpms.config;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.main.allow-circular-references=true",
        "management.endpoints.web.exposure.include=health,info"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(ActuatorMetricsTest.MetricsProbeController.class)
@DisplayName("Actuator 与指标回归测试")
class ActuatorMetricsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Test
    @DisplayName("HealthEndpoint 注册逻辑应可用")
    void shouldExposeHealthEndpoint() {
        assertNotNull(healthEndpoint);
        assertNotNull(healthEndpoint.health().getStatus());
    }

    @Test
    @DisplayName("HTTP 请求成功/失败都应写入本地请求指标")
    void shouldRecordHttpServerRequestMetricsForSuccessAndFailure() throws Exception {
        mockMvc.perform(get("/api/test-metrics/ok")
                        .contextPath("/api")
                        .cookie(adminCookie())
                        .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/test-metrics/fail")
                        .contextPath("/api")
                        .cookie(adminCookie())
                        .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().is5xxServerError());

        assertTrue(hasHttpServerRequestMetric("200", "none"),
                "expected a success http.server.requests metric with status=200");
        assertTrue(hasHttpServerRequestMetric("500", "none"),
                "expected a failure http.server.requests metric with status=500");
    }

    @Test
    @DisplayName("JVM、连接池和异步线程池指标应存在")
    void shouldRegisterJvmDatasourceAndAsyncMetrics() {
        applicationContext.getBean("taskExecutor");

        assertHasMeter("jvm.threads.live");
        assertHasMeter("hikaricp.connections.max");
        assertNotNull(meterRegistry.find("executor.completed")
                .tag("name", "cgc.pms.async")
                .tag("executor", "taskExecutor")
                .functionCounter());
    }

    private void assertHasMeter(String meterName) {
        assertTrue(meterRegistry.getMeters().stream()
                        .map(Meter::getId)
                        .anyMatch(id -> meterName.equals(id.getName())),
                () -> "expected meter to exist: " + meterName);
    }

    private boolean hasHttpServerRequestMetric(String status, String exception) {
        return meterRegistry.getMeters().stream()
                .filter(meter -> "http.server.requests".equals(meter.getId().getName()))
                .map(Meter::getId)
                .map(id -> id.getTags().stream()
                        .collect(Collectors.toMap(tag -> tag.getKey(), tag -> tag.getValue(), (left, right) -> right)))
                .anyMatch(tags -> matchesHttpMetric(tags, status, exception));
    }

    private boolean matchesHttpMetric(Map<String, String> tags, String status, String exception) {
        return status.equals(tags.get("status"))
                && "GET".equals(tags.get("method"))
                && exception.equalsIgnoreCase(tags.getOrDefault("exception", ""));
    }

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(1L, "admin", 0L, List.of("ADMIN"), List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    @RestController
    static class MetricsProbeController {

        @GetMapping(path = "/test-metrics/ok", produces = MediaType.TEXT_PLAIN_VALUE)
        String ok() {
            return "ok";
        }

        @GetMapping(path = "/test-metrics/fail", produces = MediaType.TEXT_PLAIN_VALUE)
        ResponseEntity<String> fail() {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("fail");
        }
    }
}
