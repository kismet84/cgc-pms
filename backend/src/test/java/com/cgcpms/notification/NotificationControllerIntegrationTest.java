package com.cgcpms.notification;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for the NotificationController SSE stream endpoint
 * through the full Spring Security filter chain (including JwtAuthenticationFilter).
 *
 * <p>Uses {@code @SpringBootTest} with full application context and
 * {@code MockMvc} to exercise the real servlet filters.</p>
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("通知控制器 SSE 集成测试（全 Security 链路）")
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * Test 1: valid JWT in {@code access_token} cookie → 200 + SSE content type
     * + the initial "connected" event.
     */
    @Test
    @DisplayName("GET /api/notifications/stream with valid JWT returns 200 and text/event-stream")
    void streamWithValidJwt() throws Exception {
        String token = jwtUtils.generateToken(
                1L,
                "admin",
                0L,
                List.of("ADMIN"),
                List.of("notification:view"));

        Cookie cookie = new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);

        MvcResult result = mockMvc.perform(getWithApiContext("/notifications/stream")
                        .cookie(cookie)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andReturn();

        // The "connected" event is sent synchronously inside subscribe(),
        // so it is already in the response body before async dispatch.
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("connected"), "Response should contain 'connected' event");
        assertTrue(body.contains("\"userId\":1"), "Response should reference userId=1");
        assertTrue(body.contains("\"tenantId\":0"), "Response should reference tenantId=0");
    }

    /**
     * Test 2: no auth cookie → JwtAuthenticationFilter rejects with 401.
     */
    @Test
    @DisplayName("GET /api/notifications/stream without JWT returns 401")
    void streamWithoutJwt() throws Exception {
        mockMvc.perform(getWithApiContext("/notifications/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test 3: invalid/expired JWT → JwtAuthenticationFilter rejects with 401.
     */
    @Test
    @DisplayName("GET /api/notifications/stream with invalid JWT returns 401")
    void streamWithInvalidJwt() throws Exception {
        Cookie cookie = new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, "not.a.valid.jwt");

        mockMvc.perform(getWithApiContext("/notifications/stream")
                        .cookie(cookie)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/notifications/unread-count with valid JWT returns count payload")
    void unreadCountWithValidJwt() throws Exception {
        String token = jwtUtils.generateToken(
                1L,
                "admin",
                0L,
                List.of("ADMIN"),
                List.of("notification:view"));

        Cookie cookie = new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);

        mockMvc.perform(getWithApiContext("/notifications/unread-count")
                        .cookie(cookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    @DisplayName("GET /api/notifications/unread-count without notification:view returns 403 envelope")
    void unreadCountWithoutViewPermission() throws Exception {
        String token = jwtUtils.generateToken(
                2L,
                "limited",
                0L,
                List.of("USER"),
                List.of());

        Cookie cookie = new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);

        mockMvc.perform(getWithApiContext("/notifications/unread-count")
                        .cookie(cookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    private static MockHttpServletRequestBuilder getWithApiContext(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }
}
