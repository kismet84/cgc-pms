package com.cgcpms.cost;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.common.JwtHttpTestTokenFactory;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc @ActiveProfiles("local")
@DisplayName("CostSubjectController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CostSubjectControllerTest {
    @Autowired private MockMvc mockMvc; @Autowired private JwtHttpTestTokenFactory tokenFactory;
    @Autowired private JdbcTemplate jdbc;
    private static final long ADMIN_ID = 994_896_010_000L; private static final long TENANT_ID = 0L;
    private static final long FINANCE_ROLE_LINK_ID = 994_896_010_001L;
    private Long subjectId;

    @BeforeAll
    void grantCompanyFinanceRole() {
        jdbc.update("DELETE FROM sys_user_role WHERE id=?", FINANCE_ROLE_LINK_ID);
        jdbc.update("DELETE FROM sys_user WHERE tenant_id=? AND id=?", TENANT_ID, ADMIN_ID);
        jdbc.update("""
                INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,deleted_flag)
                VALUES (?,?,?,'x','成本科目控制器测试财务','ENABLE',0,0)
                """, ADMIN_ID, TENANT_ID, "cost-subject-controller-finance");
        jdbc.update("""
                INSERT INTO sys_user_role(id,tenant_id,user_id,role_id)
                SELECT ?,?,?,r.id
                FROM sys_role r
                WHERE r.tenant_id=? AND r.role_code='COMPANY_FINANCE'
                  AND r.status='ENABLE' AND r.deleted_flag=0
                  AND NOT EXISTS (
                      SELECT 1 FROM sys_user_role ur
                      WHERE ur.tenant_id=? AND ur.user_id=? AND ur.role_id=r.id
                  )
                """, FINANCE_ROLE_LINK_ID, TENANT_ID, ADMIN_ID, TENANT_ID, TENANT_ID, ADMIN_ID);
    }

    @AfterAll
    void revokeTestCompanyFinanceRole() {
        jdbc.update("DELETE FROM sys_user_role WHERE id=?", FINANCE_ROLE_LINK_ID);
        jdbc.update("DELETE FROM sys_user WHERE tenant_id=? AND id=?", TENANT_ID, ADMIN_ID);
    }

    private Cookie adminCookie() {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                tokenFactory.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of()));
    }

    @Test @Order(1) @DisplayName("GET /cost-subjects without JWT -> 401")
    void testUnauthorized() throws Exception { mockMvc.perform(g("/cost-subjects")).andExpect(status().isUnauthorized()); }

    @Test @Order(2) @DisplayName("GET /cost-subjects/tree -> 200 with tree data")
    void testGetTree() throws Exception {
        mockMvc.perform(g("/cost-subjects/tree").cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(3) @DisplayName("GET /cost-subjects -> 200 with list data")
    void testGetList() throws Exception {
        mockMvc.perform(g("/cost-subjects").cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(4) @DisplayName("POST /cost-subjects -> 200 creates subject")
    void testCreate() throws Exception {
        String body = "{\"subjectCode\":\"CS-TEST-" + System.nanoTime()
                + "\",\"subjectName\":\"测试科目\",\"subjectType\":\"GENERAL_LEDGER\","
                + "\"accountCategory\":\"ASSET\",\"sortOrder\":0,\"status\":\"ENABLE\",\"parentId\":0}";
        String resp = mockMvc.perform(p("/cost-subjects").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        subjectId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(subjectId);
    }

    @Test @Order(5) @DisplayName("POST /cost-subjects missing required -> 4xx")
    void testCreate_Missing() throws Exception {
        mockMvc.perform(p("/cost-subjects").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(6) @DisplayName("GET /cost-subjects/{id} -> 200")
    void testGetById() throws Exception {
        Assertions.assertNotNull(subjectId);
        mockMvc.perform(g("/cost-subjects/" + subjectId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.id").exists());
    }

    @Test @Order(7) @DisplayName("PUT /cost-subjects/{id} -> 200")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(subjectId);
        String body = "{\"subjectCode\":\"CS-UPD-" + System.nanoTime()
                + "\",\"subjectName\":\"更新科目\",\"subjectType\":\"GENERAL_LEDGER\","
                + "\"accountCategory\":\"ASSET\",\"sortOrder\":0,\"status\":\"ENABLE\",\"parentId\":0}";
        mockMvc.perform(u("/cost-subjects/" + subjectId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(8) @DisplayName("PUT /cost-subjects/{id}/toggle -> 200 toggles status")
    void testToggleStatus() throws Exception {
        Assertions.assertNotNull(subjectId);
        mockMvc.perform(u("/cost-subjects/" + subjectId + "/toggle").cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(9) @DisplayName("DELETE /cost-subjects/{id} -> 200")
    void testDelete() throws Exception {
        Assertions.assertNotNull(subjectId);
        mockMvc.perform(d("/cost-subjects/" + subjectId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    private MockHttpServletRequestBuilder g(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder p(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder u(String p) { return put("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder d(String p) { return delete("/api" + p).contextPath("/api"); }
}
