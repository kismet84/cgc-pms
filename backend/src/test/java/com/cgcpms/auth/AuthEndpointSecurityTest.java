package com.cgcpms.auth;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security test: ensures endpoints annotated with
 * {@code @PreAuthorize("isAuthenticated()")} reject unauthenticated requests.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("Auth endpoint security — unauthenticated → 401")
class AuthEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * V85 migration removes the default admin user.
     * Re-seed it so JWT-based auth tests can look up the user from the DB.
     */
    @BeforeEach
    void seedAdminUser() {
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 1, 0, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', '13800000000', 'admin@cgc-pms.com', 'ENABLE', 1, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 1)");
    }

    @Test
    @DisplayName("GET /api/auth/userinfo without JWT → 401")
    void userinfoWithoutJwt() throws Exception {
        mockMvc.perform(getWithApiContext("/auth/userinfo")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/logout without JWT → 401")
    void logoutWithoutJwt() throws Exception {
        mockMvc.perform(postWithApiContext("/auth/logout")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/auth/userinfo with valid JWT → 200")
    void userinfoWithValidJwt() throws Exception {
        String token = jwtUtils.generateToken(
                1L, "admin", 0L,
                List.of("ADMIN"),
                List.of());

        Cookie cookie = new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);

        mockMvc.perform(getWithApiContext("/auth/userinfo")
                        .cookie(cookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("POST /api/auth/logout with valid JWT → 200")
    void logoutWithValidJwt() throws Exception {
        String token = jwtUtils.generateToken(
                1L, "admin", 0L,
                List.of("ADMIN"),
                List.of());

        Cookie cookie = new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);

        mockMvc.perform(postWithApiContext("/auth/logout")
                        .cookie(cookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("GET /api/auth/userinfo with invalid JWT → 401")
    void userinfoWithInvalidJwt() throws Exception {
        Cookie cookie = new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, "not.a.valid.jwt");

        mockMvc.perform(getWithApiContext("/auth/userinfo")
                        .cookie(cookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/logout with invalid JWT → 401")
    void logoutWithInvalidJwt() throws Exception {
        Cookie cookie = new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, "not.a.valid.jwt");

        mockMvc.perform(postWithApiContext("/auth/logout")
                        .cookie(cookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder getWithApiContext(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder postWithApiContext(String pathWithinContext) {
        return post("/api" + pathWithinContext).contextPath("/api");
    }
}
