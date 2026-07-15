package com.cgcpms.bid;

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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    @Autowired private JdbcTemplate jdbcTemplate;
    private static final long ADMIN_ID = 1L;
    private static final long TENANT_ID = 0L;
    private Long bidId;

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    private Cookie userCookie(long tenantId, List<String> permissions) {
        String token = jwtUtils.generateToken(99L, "bid-reader", tenantId, List.of("COMMON_USER"), permissions);
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

    @Test @Order(2) @DisplayName("GET /bid-cost allows bid:query and rejects missing permission")
    void testList_QueryPermissionBoundary() throws Exception {
        mockMvc.perform(getWith("/bid-cost").cookie(userCookie(TENANT_ID, List.of("bid:query"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
        mockMvc.perform(getWith("/bid-cost").cookie(userCookie(TENANT_ID, List.of())))
                .andExpect(status().isForbidden());
    }

    @Test @Order(2) @DisplayName("GET /bid-cost hides another tenant record")
    void testList_TenantIsolation() throws Exception {
        long id = 900000000000L + Math.abs(System.nanoTime() % 100000000L);
        String name = "CROSS-TENANT-BID-" + id;
        jdbcTemplate.update("""
                INSERT INTO bid_cost (id, tenant_id, bid_project_name, bid_status, deleted_flag)
                VALUES (?, ?, ?, 'BIDDING', 0)
                """, id, 9001L, name);
        try {
            mockMvc.perform(getWith("/bid-cost")
                            .cookie(userCookie(TENANT_ID, List.of("bid:query")))
                            .param("keyword", name))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(0))
                    .andExpect(jsonPath("$.data.records").isEmpty());
        } finally {
            jdbcTemplate.update("DELETE FROM bid_cost WHERE id = ?", id);
        }
    }

    @Test @Order(2) @DisplayName("V150 grants only the bid query menu to configured roles")
    void testBidQueryMenuMigrationApplied() {
        Set<String> permissions = Set.copyOf(jdbcTemplate.queryForList("""
                SELECT DISTINCT m.perms
                FROM sys_role r
                JOIN sys_role_menu rm ON rm.role_id = r.id
                JOIN sys_menu m ON m.id = rm.menu_id
                WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'COST_MANAGER')
                  AND m.id = 962
                  AND m.deleted_flag = 0
                """, String.class));
        assertEquals(Set.of("bid:query"), permissions);
        Integer writePermissionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE id = 962 AND perms IN ('bid:add','bid:edit','bid:delete')",
                Integer.class);
        assertEquals(0, writePermissionCount);
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
