package com.cgcpms.common.filter;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.common.ratelimit.FallbackRateLimitCounterStore;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.main.allow-circular-references=true",
        "rate-limit.global-write.max-requests=2",
        "rate-limit.global-write.window-seconds=60"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("GlobalWriteRateLimitFilter - 全局写接口限流")
class GlobalWriteRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FallbackRateLimitCounterStore counterStore;

    @BeforeEach
    void setUp() {
        counterStore.clear();
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                        "SELECT 1, 0, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', '13800000000', 'admin@cgc-pms.com', 'ENABLE', 1, 1, 'test-seed' " +
                        "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 1)");
    }

    @Test
    @DisplayName("POST 写接口超过阈值后返回 429")
    void writeRequestsOverThresholdAreBlocked() throws Exception {
        Cookie cookie = accessCookie();

        mockMvc.perform(postWithApiContext("/test-global-rl/write").cookie(cookie))
                .andExpect(status().isOk());
        mockMvc.perform(postWithApiContext("/test-global-rl/write").cookie(cookie))
                .andExpect(status().isOk());
        mockMvc.perform(postWithApiContext("/test-global-rl/write").cookie(cookie))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("GET 与 OPTIONS 不受全局写限流影响")
    void getAndOptionsAreNotBlocked() throws Exception {
        Cookie cookie = accessCookie();

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(getWithApiContext("/test-global-rl/read").cookie(cookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
        }

        mockMvc.perform(options("/api/test-global-rl/write")
                        .contextPath("/api")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("/auth/login、/auth/refresh、/actuator/health 命中白名单时不受全局写限流影响")
    void authAndHealthWhitelistEndpointsBypassGlobalWriteLimit() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(postWithApiContext("/auth/login")
                            .servletPath("/auth/login")
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(postWithApiContext("/auth/refresh")
                            .servletPath("/auth/refresh"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        }

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(getWithApiContext("/actuator/health")
                            .servletPath("/actuator/health"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("已有 @RateLimit 仍独立生效")
    void annotationRateLimitStillWorksIndependently() throws Exception {
        Cookie cookie = accessCookie();

        mockMvc.perform(postWithApiContext("/test-global-rl/strict").cookie(cookie))
                .andExpect(status().isOk());
        mockMvc.perform(postWithApiContext("/test-global-rl/strict").cookie(cookie))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    private Cookie accessCookie() {
        String token = jwtUtils.generateToken(1L, "admin", 0L, List.of("ADMIN"), List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postWithApiContext(String pathWithinContext) {
        return post("/api" + pathWithinContext).contextPath("/api");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder getWithApiContext(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }
}
