package com.cgcpms.subcontract;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("SubMeasureController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubMeasureControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;
    private static final long PARTNER_ID = 20002L;

    private Long measureId;

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(ADMIN_ID, ADMIN_USERNAME, TENANT_ID, List.of("ADMIN"), List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    @Test @Order(1) @DisplayName("GET /sub-measures without JWT -> 401")
    void testList_Unauthorized() throws Exception {
        mockMvc.perform(getWithApi("/sub-measures")).andExpect(status().isUnauthorized());
    }
    @Test @Order(1) @DisplayName("POST /sub-measures without JWT -> 401")
    void testCreate_Unauthorized() throws Exception {
        mockMvc.perform(postWithApi("/sub-measures").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test @Order(2) @DisplayName("GET /sub-measures -> 200 with paginated data")
    void testList() throws Exception {
        mockMvc.perform(getWithApi("/sub-measures").cookie(adminCookie()).param("pageNo", "1").param("pageSize", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.records").isArray());
    }

    @Test @Order(3) @DisplayName("POST /sub-measures -> 200 creates measure")
    void testCreate() throws Exception {
        String body = String.format("""
                {"projectId":%d,"contractId":%d,"partnerId":%d,"measureCode":"SM-TEST-%d","measurePeriod":"2026-06",
                "measureDate":"%s","reportedAmount":50000.00,"approvedAmount":48000.00,"deductionAmount":2000.00,"netAmount":46000.00}
                """, PROJECT_ID, CONTRACT_ID, PARTNER_ID, System.nanoTime(), LocalDate.now());
        String resp = mockMvc.perform(postWithApi("/sub-measures").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        measureId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(measureId);
    }

    @Test @Order(4) @DisplayName("POST /sub-measures missing required -> 400")
    void testCreate_MissingRequired() throws Exception {
        mockMvc.perform(postWithApi("/sub-measures").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(5) @DisplayName("GET /sub-measures/{id} -> 200")
    void testGetById() throws Exception {
        Assertions.assertNotNull(measureId);
        mockMvc.perform(getWithApi("/sub-measures/" + measureId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.id").exists());
    }

    @Test @Order(6) @DisplayName("GET /sub-measures/{id} non-existent -> 400")
    void testGetById_NotFound() throws Exception {
        mockMvc.perform(getWithApi("/sub-measures/999999").cookie(adminCookie())).andExpect(status().isBadRequest());
    }

    @Test @Order(7) @DisplayName("PUT /sub-measures/{id} -> 200 updates measure")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(measureId);
        String body = String.format("""
                {"projectId":%d,"contractId":%d,"partnerId":%d,"measureCode":"SM-TEST-%d","measurePeriod":"2026-06",
                "measureDate":"%s","reportedAmount":60000.00,"approvedAmount":58000.00,"deductionAmount":2000.00,"netAmount":56000.00}
                """, PROJECT_ID, CONTRACT_ID, PARTNER_ID, System.nanoTime(), LocalDate.now());
        mockMvc.perform(putWithApi("/sub-measures/" + measureId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(8) @DisplayName("PUT /sub-measures/{id} non-existent -> 400")
    void testUpdate_NotFound() throws Exception {
        String body = "{\"projectId\":" + PROJECT_ID + ",\"contractId\":" + CONTRACT_ID + ",\"partnerId\":" + PARTNER_ID + ",\"measureCode\":\"SM-NF\"}";
        mockMvc.perform(putWithApi("/sub-measures/999999").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(8) @DisplayName("DELETE /sub-measures/{id} -> 200 deletes measure")
    void testDelete() throws Exception {
        Assertions.assertNotNull(measureId);
        mockMvc.perform(deleteWithApi("/sub-measures/" + measureId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
        mockMvc.perform(getWithApi("/sub-measures/" + measureId).cookie(adminCookie())).andExpect(status().isBadRequest());
    }

    @Test @Order(9) @DisplayName("POST /sub-measures -> 200 recreates after delete")
    void testRecreate() throws Exception {
        String body = String.format("""
                {"projectId":%d,"contractId":%d,"partnerId":%d,"measureCode":"SM-TEST-%d","measurePeriod":"2026-06",
                "measureDate":"%s","reportedAmount":50000.00,"approvedAmount":48000.00,"deductionAmount":2000.00,"netAmount":46000.00}
                """, PROJECT_ID, CONTRACT_ID, PARTNER_ID, System.nanoTime(), LocalDate.now());
        String resp = mockMvc.perform(postWithApi("/sub-measures").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        measureId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(measureId);
    }

    @Test @Order(10) @DisplayName("POST /sub-measures/{id}/submit -> 2xx submits for approval")
    void testSubmit() throws Exception {
        Assertions.assertNotNull(measureId);
        mockMvc.perform(postWithApi("/sub-measures/" + measureId + "/submit").cookie(adminCookie()))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.code").value("0"));
        mockMvc.perform(getWithApi("/sub-measures/" + measureId).cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVING"))
                .andExpect(jsonPath("$.data.status").value("APPROVING"));
    }

    @Test @Order(11) @DisplayName("GET /sub-measures/{id}/items -> 200 with items")
    void testListItems() throws Exception {
        Assertions.assertNotNull(measureId);
        mockMvc.perform(getWithApi("/sub-measures/" + measureId + "/items").cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isArray());
    }

    private MockHttpServletRequestBuilder getWithApi(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder postWithApi(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder putWithApi(String p) { return put("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder deleteWithApi(String p) { return delete("/api" + p).contextPath("/api"); }
}
