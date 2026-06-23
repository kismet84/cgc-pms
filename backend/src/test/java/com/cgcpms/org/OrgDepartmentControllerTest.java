package com.cgcpms.org;

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
@DisplayName("OrgDepartmentController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrgDepartmentControllerTest {
    @Autowired private MockMvc mockMvc; @Autowired private JwtUtils jwtUtils;
    private static final long ADMIN_ID = 1L; private static final long TENANT_ID = 0L;
    private static final long COMPANY_ID = 90001L;
    private Long deptId;

    private Cookie adminCookie() {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of()));
    }

    @Test @Order(1) @DisplayName("GET /org/departments without JWT -> 401")
    void testUnauthorized() throws Exception { mockMvc.perform(g("/org/departments")).andExpect(status().isUnauthorized()); }

    @Test @Order(2) @DisplayName("GET /org/departments -> 200 with paginated data")
    void testList() throws Exception {
        mockMvc.perform(g("/org/departments").cookie(adminCookie()).param("pageNo","1").param("pageSize","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.records").isArray());
    }

    @Test @Order(3) @DisplayName("GET /org/departments/tree -> 200 with tree data")
    void testGetTree() throws Exception {
        mockMvc.perform(g("/org/departments/tree").cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(4) @DisplayName("POST /org/departments -> 200 creates department")
    void testCreate() throws Exception {
        String body = "{\"companyId\":" + COMPANY_ID + ",\"deptCode\":\"DEPT-TEST-" + System.nanoTime() + "\",\"deptName\":\"测试部门\",\"status\":\"ENABLE\"}";
        String resp = mockMvc.perform(p("/org/departments").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        deptId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(deptId);
    }

    @Test @Order(5) @DisplayName("POST /org/departments missing required -> 4xx")
    void testCreate_Missing() throws Exception {
        mockMvc.perform(p("/org/departments").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(6) @DisplayName("GET /org/departments/{id} -> 200")
    void testGetById() throws Exception {
        Assertions.assertNotNull(deptId);
        mockMvc.perform(g("/org/departments/" + deptId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.id").exists());
    }

    @Test @Order(7) @DisplayName("PUT /org/departments/{id} -> 200")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(deptId);
        String body = "{\"companyId\":" + COMPANY_ID + ",\"deptCode\":\"DEPT-UPD-" + System.nanoTime() + "\",\"deptName\":\"更新部门\",\"status\":\"ENABLE\"}";
        mockMvc.perform(u("/org/departments/" + deptId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(8) @DisplayName("DELETE /org/departments/{id} -> 200")
    void testDelete() throws Exception {
        Assertions.assertNotNull(deptId);
        mockMvc.perform(d("/org/departments/" + deptId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    private MockHttpServletRequestBuilder g(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder p(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder u(String p) { return put("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder d(String p) { return delete("/api" + p).contextPath("/api"); }
}
