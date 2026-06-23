package com.cgcpms.accounting;

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

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("AccountingEntryController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingEntryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;
    private static final long ADMIN_ID = 1L;
    private static final long TENANT_ID = 0L;

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of());
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

    @Test @Order(3) @DisplayName("GET /accounting-entry/{id} -> 400 for non-existent")
    void testGetById_NotFound() throws Exception {
        mockMvc.perform(getWith("/accounting-entry/999999").cookie(adminCookie())).andExpect(status().isBadRequest());
    }

    @Test @Order(4) @DisplayName("POST /accounting-entry/generate -> generates entry (accept any non-401 status)")
    void testGenerate_InvalidSource() throws Exception {
        mockMvc.perform(postWith("/accounting-entry/generate").cookie(adminCookie())
                        .param("sourceType", "NONEXISTENT").param("sourceId", "999999").param("entryType", "JOURNAL"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test @Order(5) @DisplayName("PUT /accounting-entry/{id}/post non-existent -> 400")
    void testPost_NotFound() throws Exception {
        mockMvc.perform(putWith("/accounting-entry/999999/post").cookie(adminCookie())).andExpect(status().isBadRequest());
    }

    @Test @Order(6) @DisplayName("PUT /accounting-entry/{id}/reverse non-existent -> 400")
    void testReverse_NotFound() throws Exception {
        mockMvc.perform(putWith("/accounting-entry/999999/reverse").cookie(adminCookie())).andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder getWith(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder postWith(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder putWith(String p) { return put("/api" + p).contextPath("/api"); }
}
