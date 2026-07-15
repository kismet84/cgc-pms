package com.cgcpms.project;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PmProjectController integration tests covering all endpoints.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("PmProjectController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PmProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;
    private static final long EXISTING_PROJECT_ID = 10001L;

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                ADMIN_ID, ADMIN_USERNAME, TENANT_ID,
                List.of("ADMIN"),
                List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    // ═══════════════════════════════════════════════════════════════
    // Unauthorized checks
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("GET /projects without JWT -> 401")
    void testList_Unauthorized() throws Exception {
        mockMvc.perform(getWithApi("/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1)
    @DisplayName("GET /projects/{id} without JWT -> 401")
    void testGetById_Unauthorized() throws Exception {
        mockMvc.perform(getWithApi("/projects/" + EXISTING_PROJECT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1)
    @DisplayName("POST /projects without JWT -> 401")
    void testCreate_Unauthorized() throws Exception {
        mockMvc.perform(postWithApi("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectName\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════════════
    // GET list
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("GET /projects -> 200 with paginated data")
    void testList() throws Exception {
        mockMvc.perform(getWithApi("/projects")
                        .cookie(adminCookie())
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").isString())
                .andExpect(jsonPath("$.data.records[0].id").exists());
    }

    // ═══════════════════════════════════════════════════════════════
    // GET by id
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("GET /projects/{id} -> 200 with project data")
    void testGetById() throws Exception {
        mockMvc.perform(getWithApi("/projects/" + EXISTING_PROJECT_ID)
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.projectName").exists())
                .andExpect(jsonPath("$.data.projectCode").exists());
    }

    @Test
    @Order(4)
    @DisplayName("GET /projects/{id} for non-existent -> 400")
    void testGetById_NotFound() throws Exception {
        mockMvc.perform(getWithApi("/projects/999999")
                        .cookie(adminCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    // ═══════════════════════════════════════════════════════════════
    // GET overview
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("GET /projects/{id}/overview -> 200 with overview data")
    void testOverview() throws Exception {
        mockMvc.perform(getWithApi("/projects/" + EXISTING_PROJECT_ID + "/overview")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").exists());
    }

    // ═══════════════════════════════════════════════════════════════
    // POST create
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("POST /projects -> 200 creates project and returns id")
    void testCreate() throws Exception {
        String body = """
                {
                    "projectName": "集成测试项目",
                    "projectCode": "IT-TEST-%d",
                    "projectType": "房建工程",
                    "status": "DRAFT"
                }
                """.formatted(System.nanoTime());

        mockMvc.perform(postWithApi("/projects")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isString());
    }

    @Test
    @Order(7)
    @DisplayName("POST /projects with missing required field -> 400")
    void testCreate_MissingRequired() throws Exception {
        mockMvc.perform(postWithApi("/projects")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    @DisplayName("PUT /projects/{id}/archive requires authentication and project:edit")
    void testArchiveRequiresAuthenticationAndPermission() throws Exception {
        mockMvc.perform(putWithApi("/projects/" + EXISTING_PROJECT_ID + "/archive"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(putWithApi("/projects/" + EXISTING_PROJECT_ID + "/archive")
                        .cookie(cookie(93062L, TENANT_ID, List.of(), List.of("project:query"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    @DisplayName("PUT /projects/{id}/archive rejects same-tenant editor without project access")
    void testArchiveRequiresProjectDataScope() throws Exception {
        mockMvc.perform(putWithApi("/projects/" + EXISTING_PROJECT_ID + "/archive")
                        .cookie(cookie(93062L, TENANT_ID, List.of(), List.of("project:edit"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @Order(10)
    @DisplayName("PUT /projects/{id}/archive hides cross-tenant project")
    void testArchiveHidesCrossTenantProject() throws Exception {
        mockMvc.perform(putWithApi("/projects/" + EXISTING_PROJECT_ID + "/archive")
                        .cookie(cookie(93062L, 1L, List.of(), List.of("project:edit"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    // ── helpers ──

    private MockHttpServletRequestBuilder getWithApi(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder postWithApi(String pathWithinContext) {
        return post("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder putWithApi(String pathWithinContext) {
        return put("/api" + pathWithinContext).contextPath("/api");
    }

    private Cookie cookie(long userId, long tenantId, List<String> roles, List<String> permissions) {
        String token = jwtUtils.generateToken(userId, "archive-user", tenantId, roles, permissions);
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }
}
