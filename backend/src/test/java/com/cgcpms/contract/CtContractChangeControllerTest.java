package com.cgcpms.contract;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.math.BigDecimal;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc @ActiveProfiles("local")
@DisplayName("CtContractChangeController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CtContractChangeControllerTest {
    @Autowired private MockMvc mockMvc; @Autowired private JwtUtils jwtUtils;
    @Autowired private JdbcTemplate jdbcTemplate;
    private static final long ADMIN_ID = 1L; private static final long TENANT_ID = 0L;
    private static final long CONTRACT_ID = 30001L;
    private Long changeId;

    @BeforeAll
    void ensureWorkflowApprover() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=?", Integer.class, ADMIN_ID);
        if (count != null && count == 0) {
            jdbcTemplate.update("""
                    INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,deleted_flag)
                    VALUES(?,?,'admin','{noop}test','管理员','ENABLE',1,0)
                    """, ADMIN_ID, TENANT_ID);
        } else {
            jdbcTemplate.update("UPDATE sys_user SET tenant_id=?,status='ENABLE',deleted_flag=0 WHERE id=?",
                    TENANT_ID, ADMIN_ID);
        }
    }

    private Cookie adminCookie() {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of()));
    }

    @Test @Order(1) @DisplayName("GET /contract-changes without JWT -> 401")
    void testUnauthorized() throws Exception { mockMvc.perform(g("/contract-changes")).andExpect(status().isUnauthorized()); }

    @Test @Order(2) @DisplayName("GET /contract-changes -> 200 with paginated data")
    void testList() throws Exception {
        mockMvc.perform(g("/contract-changes").cookie(adminCookie()).param("pageNo","1").param("pageSize","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.records").isArray());
    }

    @Test @Order(3) @DisplayName("POST /contract-changes -> 200 creates change")
    void testCreate() throws Exception {
        String body = "{\"projectId\":10001,\"contractId\":" + CONTRACT_ID + ",\"changeName\":\"CC-TEST-" + System.nanoTime() + "\",\"changeType\":\"AMOUNT\",\"changeAmount\":5000.00,\"reason\":\"测试变更\"}";
        String resp = mockMvc.perform(p("/contract-changes").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        changeId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(changeId);
    }

    @Test @Order(4) @DisplayName("POST /contract-changes missing required -> 400")
    void testCreate_Missing() throws Exception {
        mockMvc.perform(p("/contract-changes").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(5) @DisplayName("GET /contract-changes/{id} -> 200")
    void testGetById() throws Exception {
        Assertions.assertNotNull(changeId);
        mockMvc.perform(g("/contract-changes/" + changeId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.id").exists());
    }

    @Test @Order(6) @DisplayName("GET /contract-changes/{id} not found -> 400")
    void testGetById_NotFound() throws Exception {
        mockMvc.perform(g("/contract-changes/999999").cookie(adminCookie())).andExpect(status().isBadRequest());
    }

    @Test @Order(7) @DisplayName("PUT /contract-changes/{id} -> 200")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(changeId);
        String body = "{\"projectId\":10001,\"contractId\":" + CONTRACT_ID + ",\"changeName\":\"CC-UPD-" + System.nanoTime() + "\",\"changeType\":\"AMOUNT\",\"changeAmount\":8000.00,\"reason\":\"更新变更\"}";
        mockMvc.perform(u("/contract-changes/" + changeId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(8) @DisplayName("DELETE /contract-changes/{id} -> 200 (before submit)")
    void testDelete() throws Exception {
        Assertions.assertNotNull(changeId);
        mockMvc.perform(d("/contract-changes/" + changeId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(9) @DisplayName("POST /contract-changes -> 200 recreates after delete")
    void testRecreate() throws Exception {
        String body = "{\"projectId\":10001,\"contractId\":" + CONTRACT_ID + ",\"changeName\":\"CC-RECREATE-" + System.nanoTime() + "\",\"changeType\":\"AMOUNT\",\"changeAmount\":5000.00,\"reason\":\"重创变更\"}";
        String resp = mockMvc.perform(p("/contract-changes").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        changeId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(changeId);
    }

    @Test @Order(10) @DisplayName("POST /contract-changes/{id}/submit -> 2xx")
    void testSubmit() throws Exception {
        Assertions.assertNotNull(changeId);
        mockMvc.perform(p("/contract-changes/" + changeId + "/submit").cookie(adminCookie()))
                .andExpect(status().is2xxSuccessful());
    }

    private MockHttpServletRequestBuilder g(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder p(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder u(String p) { return put("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder d(String p) { return delete("/api" + p).contextPath("/api"); }
}
