package com.cgcpms.system;

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
@AutoConfigureMockMvc @ActiveProfiles("local")
@DisplayName("SysRoleController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SysRoleControllerTest {
    @Autowired private MockMvc mockMvc; @Autowired private JwtUtils jwtUtils;
    private static final long ADMIN_ID = 1L; private static final long TENANT_ID = 0L;
    private Long roleId;

    private Cookie adminCookie() {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of()));
    }

    @Test @Order(1) @DisplayName("GET /system/roles without JWT -> 401")
    void testUnauthorized() throws Exception { mockMvc.perform(g("/system/roles")).andExpect(status().isUnauthorized()); }

    @Test @Order(2) @DisplayName("GET /system/roles -> 200 with paginated data")
    void testList() throws Exception {
        mockMvc.perform(g("/system/roles").cookie(adminCookie()).param("pageNo","1").param("pageSize","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(3) @DisplayName("POST /system/roles -> 200 creates role")
    void testCreate() throws Exception {
        String body = "{\"roleCode\":\"ROLE-TEST-" + System.nanoTime() + "\",\"roleName\":\"测试角色\",\"roleType\":\"CUSTOM\"}";
        String resp = mockMvc.perform(p("/system/roles").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        roleId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(roleId);
    }

    @Test @Order(4) @DisplayName("POST /system/roles missing required -> 400")
    void testCreate_Missing() throws Exception {
        mockMvc.perform(p("/system/roles").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(5) @DisplayName("GET /system/roles/{id} -> 200")
    void testGetById() throws Exception {
        Assertions.assertNotNull(roleId);
        mockMvc.perform(g("/system/roles/" + roleId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.id").exists());
    }

    @Test @Order(6) @DisplayName("PUT /system/roles/{id} -> 200")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(roleId);
        String body = "{\"roleCode\":\"ROLE-UPD-" + System.nanoTime() + "\",\"roleName\":\"更新角色\",\"roleType\":\"CUSTOM\"}";
        mockMvc.perform(u("/system/roles/" + roleId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(7) @DisplayName("DELETE /system/roles/{id} -> 200")
    void testDelete() throws Exception {
        Assertions.assertNotNull(roleId);
        mockMvc.perform(d("/system/roles/" + roleId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    private MockHttpServletRequestBuilder g(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder p(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder u(String p) { return put("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder d(String p) { return delete("/api" + p).contextPath("/api"); }
}
