package com.cgcpms.accounting;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("AccountingEntryController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingEntryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private JdbcTemplate jdbcTemplate;
    private static final long ADMIN_ID = 1L;
    private static final long TENANT_ID = 0L;

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    private Cookie financeCookie(List<String> permissions) {
        String token = jwtUtils.generateToken(6L, "finance", TENANT_ID, List.of("FINANCE"), permissions);
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    @Test @Order(1) @DisplayName("GET /accounting-entry without JWT -> 401")
    void testList_Unauthorized() throws Exception {
        mockMvc.perform(getWith("/accounting-entry")).andExpect(status().isUnauthorized());
    }
    @Test @Order(1) @DisplayName("POST /accounting-entry/generate without JWT -> 401")
    void testGenerate_Unauthorized() throws Exception {
        mockMvc.perform(postWith("/accounting-entry/generate")).andExpect(status().isUnauthorized());
    }

    @Test @Order(2) @DisplayName("GET /accounting-entry -> 200 with paginated data")
    void testGetPage() throws Exception {
        mockMvc.perform(getWith("/accounting-entry").cookie(adminCookie()).param("pageNo", "1").param("pageSize", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.records").isArray());
    }

    @Test @Order(2) @DisplayName("GET /accounting-entry allows FINANCE with accounting:query")
    void testGetPage_FinancePermission() throws Exception {
        mockMvc.perform(getWith("/accounting-entry").cookie(financeCookie(List.of("accounting:query"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(2) @DisplayName("GET /accounting-entry rejects FINANCE without accounting:query")
    void testGetPage_ForbiddenWithoutPermission() throws Exception {
        mockMvc.perform(getWith("/accounting-entry").cookie(financeCookie(List.of())))
                .andExpect(status().isForbidden());
    }

    @Test @Order(3) @DisplayName("GET /accounting-entry/{id} -> 400 for non-existent")
    void testGetById_NotFound() throws Exception {
        mockMvc.perform(getWith("/accounting-entry/999999").cookie(adminCookie())).andExpect(status().isBadRequest());
    }

    @Test @Order(4) @DisplayName("POST /accounting-entry/generate rejects unsupported source explicitly")
    void testGenerate_InvalidSource() throws Exception {
        mockMvc.perform(postWith("/accounting-entry/generate")
                        .cookie(financeCookie(List.of("accounting:add")))
                        .param("sourceType", "NONEXISTENT").param("sourceId", "999999").param("entryType", "JOURNAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENTRY_SOURCE_UNSUPPORTED"));
    }

    @Test @Order(5) @DisplayName("PUT /accounting-entry/{id}/post non-existent -> 400")
    void testPost_NotFound() throws Exception {
        mockMvc.perform(putWith("/accounting-entry/999999/post").cookie(adminCookie())).andExpect(status().isBadRequest());
    }

    @Test @Order(6) @DisplayName("PUT /accounting-entry/{id}/reverse non-existent -> 400")
    void testReverse_NotFound() throws Exception {
        mockMvc.perform(putWith("/accounting-entry/999999/reverse").cookie(adminCookie())).andExpect(status().isBadRequest());
    }

    @Test @Order(7) @DisplayName("V148 grants query/edit to FINANCE but keeps accounting:add closed")
    void testAccountingMenuPermissionMigrationApplied() {
        Set<String> permissions = Set.copyOf(jdbcTemplate.queryForList("""
                SELECT m.perms
                FROM sys_role r
                JOIN sys_role_menu rm ON rm.role_id = r.id
                JOIN sys_menu m ON m.id = rm.menu_id
                WHERE r.role_code = 'FINANCE'
                  AND m.id IN (960, 961)
                  AND m.deleted_flag = 0
                """, String.class));

        assertEquals(Set.of("accounting:query", "accounting:edit"), permissions);
        Integer addPermissionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE perms = 'accounting:add' AND deleted_flag = 0",
                Integer.class);
        assertEquals(0, addPermissionCount);
    }

    private MockHttpServletRequestBuilder getWith(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder postWith(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder putWith(String p) { return put("/api" + p).contextPath("/api"); }
}
