package com.cgcpms.overhead;

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
@DisplayName("OverheadAllocationController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OverheadAllocationControllerTest {
    @Autowired private MockMvc mockMvc; @Autowired private JwtUtils jwtUtils;
    private static final long ADMIN_ID = 1L; private static final long TENANT_ID = 0L;
    private Long ruleId;

    private Cookie adminCookie() {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of()));
    }

    @Test @Order(1) @DisplayName("GET /overhead-allocation/rules without JWT -> 401")
    void testUnauthorized() throws Exception { mockMvc.perform(g("/overhead-allocation/rules")).andExpect(status().isUnauthorized()); }

    @Test @Order(2) @DisplayName("GET /overhead-allocation/rules -> 200")
    void testGetRules() throws Exception {
        mockMvc.perform(g("/overhead-allocation/rules").cookie(adminCookie()).param("pageNo","1").param("pageSize","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(3) @DisplayName("POST /overhead-allocation/rules -> 200 creates rule")
    void testCreateRule() throws Exception {
        String body = "{\"costSubjectId\":%d,\"allocationBasis\":\"DIRECT_LABOR\",\"allocationCycle\":\"MONTHLY\"}".formatted(System.nanoTime() % 1000 + 1000);
        String resp = mockMvc.perform(p("/overhead-allocation/rules").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        ruleId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(ruleId);
    }

    @Test @Order(4) @DisplayName("POST /overhead-allocation/rules missing required -> 400 or 409")
    void testCreateRule_Missing() throws Exception {
        mockMvc.perform(p("/overhead-allocation/rules").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(5) @DisplayName("PUT /overhead-allocation/rules/{id} -> 200")
    void testUpdateRule() throws Exception {
        Assertions.assertNotNull(ruleId);
        String body = "{\"costSubjectId\":" + (System.nanoTime() % 1000 + 2000) + ",\"allocationBasis\":\"CONTRACT_AMOUNT\",\"allocationCycle\":\"PER_OCCURRENCE\"}";
        mockMvc.perform(u("/overhead-allocation/rules/" + ruleId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(6) @DisplayName("POST /overhead-allocation/execute -> accepts any response")
    void testExecuteAllocation() throws Exception {
        mockMvc.perform(p("/overhead-allocation/execute").cookie(adminCookie()).param("period", "2026-06-01"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test @Order(7) @DisplayName("DELETE /overhead-allocation/rules/{id} -> 200")
    void testDeleteRule() throws Exception {
        Assertions.assertNotNull(ruleId);
        mockMvc.perform(d("/overhead-allocation/rules/" + ruleId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    private MockHttpServletRequestBuilder g(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder p(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder u(String p) { return put("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder d(String p) { return delete("/api" + p).contextPath("/api"); }
}
