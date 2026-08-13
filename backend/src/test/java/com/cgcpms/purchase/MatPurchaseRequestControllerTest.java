package com.cgcpms.purchase;

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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MatPurchaseRequestController integration tests covering list, getById, create,
 * update, delete, submit, getItems, and saveItemsBatch.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("MatPurchaseRequestController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatPurchaseRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;

    private Long requestId;

    @BeforeAll
    void ensureAdminUser() {
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    id, tenant_id, username, password, real_name, status, is_admin,
                    created_by, updated_by, deleted_flag
                ) SELECT ?, ?, ?, '{noop}test', '采购申请接口测试管理员', 'ENABLE', 1, ?, ?, 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = ?)
                """, ADMIN_ID, TENANT_ID, ADMIN_USERNAME, ADMIN_ID, ADMIN_ID, ADMIN_ID);
    }

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                ADMIN_ID, ADMIN_USERNAME, TENANT_ID,
                List.of("ADMIN"), List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    @Test
    @Order(1) @DisplayName("GET /purchase-requests without JWT -> 401")
    void testList_Unauthorized() throws Exception {
        mockMvc.perform(getWithApi("/purchase-requests")).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1) @DisplayName("POST /purchase-requests without JWT -> 401")
    void testCreate_Unauthorized() throws Exception {
        mockMvc.perform(postWithApi("/purchase-requests").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2) @DisplayName("GET /purchase-requests -> 200 with paginated data")
    void testList() throws Exception {
        mockMvc.perform(getWithApi("/purchase-requests").cookie(adminCookie()).param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @Order(3) @DisplayName("POST /purchase-requests -> 200 creates request and returns id")
    void testCreate() throws Exception {
        String body = "{\"projectId\":" + PROJECT_ID + ",\"contractId\":" + CONTRACT_ID + ",\"requestCode\":\"PR-TEST-" + System.nanoTime() + "\"}";
        String response = mockMvc.perform(postWithApi("/purchase-requests").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        requestId = Long.parseLong(response.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(requestId);
    }

    @Test
    @Order(3) @DisplayName("POST /purchase-requests/with-items -> 200 原子创建头和多明细")
    void testCreateWithItems() throws Exception {
        String response = mockMvc.perform(postWithApi("/purchase-requests/with-items").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("接口多明细创建")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andReturn().getResponse().getContentAsString();
        Long id = Long.parseLong(response.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        mockMvc.perform(getWithApi("/purchase-requests/" + id).cookie(adminCookie()))
                .andExpect(jsonPath("$.data.purpose").doesNotExist());
        mockMvc.perform(getWithApi("/purchase-requests/" + id + "/items").cookie(adminCookie()))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @Order(3) @DisplayName("POST /purchase-requests/{id}/items/batch -> 路径归属与审批字段由服务端控制")
    void testSaveItemsBatch_IgnoresServerManagedFields() throws Exception {
        String response = mockMvc.perform(postWithApi("/purchase-requests").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + PROJECT_ID + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andReturn().getResponse().getContentAsString();
        Long isolatedRequestId = Long.parseLong(response.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));

        mockMvc.perform(postWithApi("/purchase-requests/" + isolatedRequestId + "/items/batch")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"id":999999,"tenantId":999,"requestId":888888,"materialId":1,
                                  "quantity":2,"plannedDate":"2026-08-15",
                                  "approvedQuantity":99,"approvalVersion":77}]
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));

        Map<String, Object> stored = jdbcTemplate.queryForMap("""
                SELECT id, tenant_id, request_id, approved_quantity, approval_version
                  FROM mat_purchase_request_item WHERE request_id = ?
                """, isolatedRequestId);
        Assertions.assertNotEquals(999999L, ((Number) stored.get("id")).longValue());
        Assertions.assertEquals(TENANT_ID, ((Number) stored.get("tenant_id")).longValue());
        Assertions.assertEquals(isolatedRequestId.longValue(), ((Number) stored.get("request_id")).longValue());
        Assertions.assertNull(stored.get("approved_quantity"));
        Assertions.assertEquals(0, ((Number) stored.get("approval_version")).intValue());
    }

    @Test
    @Order(4) @DisplayName("POST /purchase-requests missing required -> 4xx")
    void testCreate_MissingRequired() throws Exception {
        mockMvc.perform(postWithApi("/purchase-requests").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(5) @DisplayName("GET /purchase-requests/{id} -> 200 with request data")
    void testGetById() throws Exception {
        Assertions.assertNotNull(requestId);
        mockMvc.perform(getWithApi("/purchase-requests/" + requestId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.id").exists());
    }

    @Test
    @Order(6) @DisplayName("GET /purchase-requests/{id} non-existent -> 400")
    void testGetById_NotFound() throws Exception {
        mockMvc.perform(getWithApi("/purchase-requests/999999").cookie(adminCookie())).andExpect(status().isBadRequest());
    }

    @Test
    @Order(7) @DisplayName("PUT /purchase-requests/{id} -> 200 updates request")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(requestId);
        String body = "{\"projectId\":" + PROJECT_ID + ",\"contractId\":" + CONTRACT_ID + ",\"requestCode\":\"PR-TEST-UPD-" + System.nanoTime() + "\"}";
        mockMvc.perform(putWithApi("/purchase-requests/" + requestId).cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @Order(8) @DisplayName("PUT /purchase-requests/{id} non-existent -> 400")
    void testUpdate_NotFound() throws Exception {
        String body = "{\"projectId\":" + PROJECT_ID + ",\"contractId\":" + CONTRACT_ID + ",\"requestCode\":\"PR-TEST-NF\"}";
        mockMvc.perform(putWithApi("/purchase-requests/999999").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8) @DisplayName("DELETE /purchase-requests/{id} -> 200 deletes request")
    void testDelete() throws Exception {
        Assertions.assertNotNull(requestId);
        mockMvc.perform(deleteWithApi("/purchase-requests/" + requestId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
        mockMvc.perform(getWithApi("/purchase-requests/" + requestId).cookie(adminCookie())).andExpect(status().isBadRequest());
    }

    @Test
    @Order(9) @DisplayName("POST /purchase-requests -> 200 recreates after delete")
    void testRecreate() throws Exception {
        String body = "{\"projectId\":" + PROJECT_ID + ",\"contractId\":" + CONTRACT_ID + ",\"requestCode\":\"PR-TEST-RECREATE-" + System.nanoTime() + "\"}";
        String response = mockMvc.perform(postWithApi("/purchase-requests").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        requestId = Long.parseLong(response.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(requestId);
    }

    @Test
    @Order(10) @DisplayName("POST /purchase-requests/{id}/submit -> 400 (recreate may lack requirements)")
    void testSubmitForApproval() throws Exception {
        Assertions.assertNotNull(requestId);
        mockMvc.perform(postWithApi("/purchase-requests/" + requestId + "/submit").cookie(adminCookie()))
                .andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder getWithApi(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder postWithApi(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder putWithApi(String p) { return put("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder deleteWithApi(String p) { return delete("/api" + p).contextPath("/api"); }

    private String createBody(String remark) {
        return """
                {"header":{"projectId":%d,"purpose":"应被忽略","remark":"%s"},"items":[
                  {"materialId":1,"quantity":2,"plannedDate":"2026-08-15","useLocation":"一层主体"},
                  {"materialId":1,"quantity":3,"plannedDate":"2026-08-16","useLocation":"二层主体"}
                ]}
                """.formatted(PROJECT_ID, remark);
    }
}
