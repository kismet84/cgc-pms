package com.cgcpms.workflow;

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
@DisplayName("WorkflowTemplateController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowTemplateControllerTest {
    @Autowired private MockMvc mockMvc; @Autowired private JwtUtils jwtUtils;
    private static final long ADMIN_ID = 1L; private static final long TENANT_ID = 0L;

    private Cookie adminCookie() {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of()));
    }

    @Test @Order(1) @DisplayName("GET /workflow/templates without JWT -> 401")
    void testUnauthorized() throws Exception { mockMvc.perform(g("/workflow/templates")).andExpect(status().isUnauthorized()); }

    @Test @Order(2) @DisplayName("GET /workflow/templates -> 200 with list data")
    void testList() throws Exception {
        mockMvc.perform(g("/workflow/templates").cookie(adminCookie()).param("pageNo","1").param("pageSize","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.records").isArray());
    }

    @Test @Order(3) @DisplayName("GET /workflow/templates/{templateId} -> 200")
    void testDetail() throws Exception {
        mockMvc.perform(g("/workflow/templates/50001").cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.id").exists());
    }

    @Test @Order(4) @DisplayName("GET /workflow/templates/{templateId} non-existent -> 400")
    void testDetail_NotFound() throws Exception {
        mockMvc.perform(g("/workflow/templates/999999").cookie(adminCookie())).andExpect(status().isBadRequest());
    }

    @Test @Order(5) @DisplayName("PUT /workflow/templates/{templateId} -> 200")
    void testUpdateTemplate() throws Exception {
        String body = "{\"templateName\":\"更新模板-" + System.nanoTime() + "\",\"enabled\":1}";
        mockMvc.perform(u("/workflow/templates/50001").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(6) @DisplayName("POST /workflow/templates/{templateId}/nodes -> 200")
    void testCreateNode() throws Exception {
        String body = "{\"nodeCode\":\"NODE-TEST-" + System.nanoTime() + "\",\"nodeName\":\"测试节点\",\"nodeOrder\":99,\"nodeType\":\"APPROVAL\",\"approveMode\":\"SEQUENTIAL\",\"approverConfig\":\"{\\\"type\\\":\\\"USER\\\",\\\"userId\\\":1}\"}";
        mockMvc.perform(p("/workflow/templates/50001/nodes").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    private MockHttpServletRequestBuilder g(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder p(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder u(String p) { return put("/api" + p).contextPath("/api"); }
}
