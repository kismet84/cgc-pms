package com.cgcpms.system;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.common.ratelimit.FallbackRateLimitCounterStore;
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
@AutoConfigureMockMvc @ActiveProfiles("local")
@DisplayName("SysUserController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SysUserControllerTest {
    @Autowired private MockMvc mockMvc; @Autowired private JwtUtils jwtUtils;
    @Autowired private FallbackRateLimitCounterStore counterStore;
    private static final long ADMIN_ID = 1L; private static final long TENANT_ID = 0L;
    private Long userId;

    @BeforeEach
    void setUp() {
        counterStore.clear();
    }

    private Cookie adminCookie() {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of()));
    }

    @Test @Order(1) @DisplayName("GET /system/users without JWT -> 401")
    void testUnauthorized() throws Exception { mockMvc.perform(g("/system/users")).andExpect(status().isUnauthorized()); }

    @Test @Order(2) @DisplayName("GET /system/users -> 200 with paginated data")
    void testList() throws Exception {
        mockMvc.perform(g("/system/users").cookie(adminCookie()).param("pageNo","1").param("pageSize","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.records").isArray());
    }

    @Test @Order(3) @DisplayName("POST /system/users -> 200 creates user")
    void testCreate() throws Exception {
        String body = "{\"username\":\"testuser-" + System.nanoTime() + "\",\"password\":\"Test123456\",\"realName\":\"测试用户\",\"phone\":\"13800001111\",\"status\":\"ENABLE\"}";
        String resp = mockMvc.perform(p("/system/users").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        userId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(userId);
    }

    @Test @Order(4) @DisplayName("POST /system/users missing required -> 400")
    void testCreate_Missing() throws Exception {
        mockMvc.perform(p("/system/users").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(5) @DisplayName("GET /system/users/{id} -> 200")
    void testGetById() throws Exception {
        Assertions.assertNotNull(userId);
        mockMvc.perform(g("/system/users/" + userId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.id").exists());
    }

    @Test @Order(6) @DisplayName("PUT /system/users/{id} -> 200")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(userId);
        String body = "{\"username\":\"testuser-" + System.nanoTime() + "\",\"password\":\"Test123456\",\"realName\":\"更新用户\",\"phone\":\"13800002222\",\"status\":\"ENABLE\"}";
        mockMvc.perform(u("/system/users/" + userId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(7) @DisplayName("PATCH /system/users/{id}/status -> 200")
    void testUpdateStatus() throws Exception {
        Assertions.assertNotNull(userId);
        mockMvc.perform(patch("/api/system/users/" + userId + "/status").contextPath("/api").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(8) @DisplayName("DELETE /system/users/{id} -> 200")
    void testDelete() throws Exception {
        Assertions.assertNotNull(userId);
        mockMvc.perform(d("/system/users/" + userId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    private MockHttpServletRequestBuilder g(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder p(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder u(String p) { return put("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder d(String p) { return delete("/api" + p).contextPath("/api"); }
}
