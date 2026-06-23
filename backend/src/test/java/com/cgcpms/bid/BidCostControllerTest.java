package com.cgcpms.bid;

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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("BidCostController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BidCostControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;
    private static final long ADMIN_ID = 1L;
    private static final long TENANT_ID = 0L;
    private Long bidId;

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    @Test @Order(1) @DisplayName("GET /bid-cost without JWT -> 401")
    void testList_Unauthorized() throws Exception {
        mockMvc.perform(getWith("/bid-cost")).andExpect(status().isUnauthorized());
    }

    @Test @Order(2) @DisplayName("GET /bid-cost -> 200 with paginated data")
    void testList() throws Exception {
        mockMvc.perform(getWith("/bid-cost").cookie(adminCookie()).param("pageNo", "1").param("pageSize", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.records").isArray());
    }

    @Test @Order(3) @DisplayName("POST /bid-cost -> 200 creates bid")
    void testCreate() throws Exception {
        String body = "{\"bidProjectName\":\"BID-TEST-" + System.nanoTime() + "\",\"bidStatus\":\"BIDDING\"}";
        String resp = mockMvc.perform(postWith("/bid-cost").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        bidId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(bidId);
    }

    @Test @Order(4) @DisplayName("POST /bid-cost missing required -> 4xx")
    void testCreate_MissingRequired() throws Exception {
        mockMvc.perform(postWith("/bid-cost").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(5) @DisplayName("GET /bid-cost/{id} -> 200")
    void testGetById() throws Exception {
        Assertions.assertNotNull(bidId);
        mockMvc.perform(getWith("/bid-cost/" + bidId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.id").exists());
    }

    @Test @Order(6) @DisplayName("GET /bid-cost/{id} non-existent -> 4xx")
    void testGetById_NotFound() throws Exception {
        mockMvc.perform(getWith("/bid-cost/999999").cookie(adminCookie())).andExpect(status().is4xxClientError());
    }

    @Test @Order(6) @DisplayName("PUT /bid-cost/{id} -> 200 updates bid")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(bidId);
        String body = "{\"bidProjectName\":\"BID-UPD-" + System.nanoTime() + "\",\"bidStatus\":\"BIDDING\"}";
        mockMvc.perform(putWith("/bid-cost/" + bidId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(7) @DisplayName("DELETE /bid-cost/{id} -> 200 deletes bid (before won)")
    void testDelete() throws Exception {
        Assertions.assertNotNull(bidId);
        mockMvc.perform(deleteWith("/bid-cost/" + bidId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(8) @DisplayName("POST /bid-cost -> 200 recreates after delete")
    void testRecreate() throws Exception {
        String body = "{\"bidProjectName\":\"BID-TEST-RECREATE-" + System.nanoTime() + "\",\"bidStatus\":\"BIDDING\"}";
        String resp = mockMvc.perform(postWith("/bid-cost").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        bidId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(bidId);
    }

    @Test @Order(9) @DisplayName("PUT /bid-cost/{id}/won -> 200 marks as won")
    void testMarkAsWon() throws Exception {
        Assertions.assertNotNull(bidId);
        mockMvc.perform(putWith("/bid-cost/" + bidId + "/won").cookie(adminCookie()).param("projectId", "10001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    private MockHttpServletRequestBuilder getWith(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder postWith(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder putWith(String p) { return put("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder deleteWith(String p) { return delete("/api" + p).contextPath("/api"); }
}
