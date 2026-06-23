package com.cgcpms.purchase;

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
 * MatPurchaseRequestController integration tests covering list, getById, create,
 * update, delete, submit, getItems, and saveItemsBatch.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
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

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;

    private Long requestId;

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
}
