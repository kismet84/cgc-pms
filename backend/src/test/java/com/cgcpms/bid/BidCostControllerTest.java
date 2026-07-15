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

import java.util.List;
import java.util.Map;
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

    @Test @Order(2) @DisplayName("V150-V154 register separate bid query, create, edit, delete and status permissions")
    void testBidMenuMigrationsApplied() {
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

        Set<String> createPermissions = Set.copyOf(jdbcTemplate.queryForList("""
                SELECT DISTINCT m.perms
                FROM sys_role r
                JOIN sys_role_menu rm ON rm.role_id = r.id
                JOIN sys_menu m ON m.id = rm.menu_id
                WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'COST_MANAGER')
                  AND m.id = 963
                  AND m.parent_id = 962
                  AND m.menu_type = 'BUTTON'
                  AND m.deleted_flag = 0
                """, String.class));
        assertEquals(Set.of("bid:add"), createPermissions);
        Integer unrelatedWritePermissionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE id = 963 AND perms IN ('bid:edit','bid:delete')",
                Integer.class);
        assertEquals(0, unrelatedWritePermissionCount);

        Set<String> editPermissions = Set.copyOf(jdbcTemplate.queryForList("""
                SELECT DISTINCT m.perms
                FROM sys_role r
                JOIN sys_role_menu rm ON rm.role_id = r.id
                JOIN sys_menu m ON m.id = rm.menu_id
                WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'COST_MANAGER')
                  AND m.id = 964
                  AND m.parent_id = 962
                  AND m.menu_type = 'BUTTON'
                  AND m.deleted_flag = 0
                """, String.class));
        assertEquals(Set.of("bid:edit"), editPermissions);
        Integer unrelatedEditPermissionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE id = 964 AND perms IN ('bid:add','bid:delete')",
                Integer.class);
        assertEquals(0, unrelatedEditPermissionCount);

        Set<String> deletePermissions = Set.copyOf(jdbcTemplate.queryForList("""
                SELECT DISTINCT m.perms
                FROM sys_role r
                JOIN sys_role_menu rm ON rm.role_id = r.id
                JOIN sys_menu m ON m.id = rm.menu_id
                WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'COST_MANAGER')
                  AND m.id = 965
                  AND m.parent_id = 962
                  AND m.menu_type = 'BUTTON'
                  AND m.deleted_flag = 0
                """, String.class));
        assertEquals(Set.of("bid:delete"), deletePermissions);
        Integer unrelatedDeletePermissionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE id = 965 AND perms IN ('bid:add','bid:edit','bid:status')",
                Integer.class);
        assertEquals(0, unrelatedDeletePermissionCount);

        Set<String> statusPermissions = Set.copyOf(jdbcTemplate.queryForList("""
                SELECT DISTINCT m.perms
                FROM sys_role r
                JOIN sys_role_menu rm ON rm.role_id = r.id
                JOIN sys_menu m ON m.id = rm.menu_id
                WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'COST_MANAGER')
                  AND m.id = 966
                  AND m.parent_id = 962
                  AND m.menu_type = 'BUTTON'
                  AND m.deleted_flag = 0
                """, String.class));
        assertEquals(Set.of("bid:status"), statusPermissions);
        Integer unrelatedStatusPermissionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE id = 966 AND perms IN ('bid:add','bid:edit','bid:delete')",
                Integer.class);
        assertEquals(0, unrelatedStatusPermissionCount);
        Integer unexpectedStatusRoleCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_role_menu rm
                JOIN sys_role r ON r.id = rm.role_id AND r.deleted_flag = 0
                WHERE rm.menu_id = 966
                  AND r.role_code NOT IN ('SUPER_ADMIN', 'ADMIN', 'COST_MANAGER')
                """, Integer.class);
        assertEquals(0, unexpectedStatusRoleCount);
    }

    @Test @Order(2) @DisplayName("POST /bid-cost enforces authentication and bid:add")
    void testCreate_PermissionBoundary() throws Exception {
        String body = "{\"bidProjectName\":\"BID-PERMISSION\"}";
        mockMvc.perform(postWith("/bid-cost").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(postWith("/bid-cost").cookie(userCookie(TENANT_ID, List.of()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        String name = "BID-ADD-" + System.nanoTime();
        String permittedBody = "{\"bidProjectName\":\"" + name + "\"}";
        String response = mockMvc.perform(postWith("/bid-cost")
                        .cookie(userCookie(TENANT_ID, List.of("bid:add")))
                        .contentType(MediaType.APPLICATION_JSON).content(permittedBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn().getResponse().getContentAsString();
        long createdId = Long.parseLong(response.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        try {
            assertEquals(TENANT_ID, jdbcTemplate.queryForObject(
                    "SELECT tenant_id FROM bid_cost WHERE id = ?", Long.class, createdId));
        } finally {
            jdbcTemplate.update("DELETE FROM bid_cost WHERE id = ?", createdId);
        }
    }

    @Test @Order(2) @DisplayName("POST /bid-cost validates the controlled create fields")
    void testCreate_ValidationBoundary() throws Exception {
        for (String body : List.of(
                "{}",
                "{\"bidProjectName\":\"   \"}",
                "{\"bidProjectName\":\"" + "项".repeat(201) + "\"}",
                "{\"bidProjectName\":\"合法项目\",\"remark\":\"" + "注".repeat(501) + "\"}")) {
            mockMvc.perform(postWith("/bid-cost").cookie(adminCookie())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Test @Order(3) @DisplayName("POST /bid-cost -> 200 creates bid")
    void testCreate() throws Exception {
        String body = "{\"bidProjectName\":\"  BID-TEST-" + System.nanoTime()
                + "  \",\"remark\":\" 受控创建 \",\"tenantId\":9001,\"projectId\":10001,"
                + "\"bidStatus\":\"WON\",\"amount\":999999}";
        String resp = mockMvc.perform(postWith("/bid-cost").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        bidId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(bidId);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT tenant_id, project_id, bid_status, bid_project_name, remark FROM bid_cost WHERE id = ?", bidId);
        assertEquals(TENANT_ID, ((Number) row.get("tenant_id")).longValue());
        Assertions.assertNull(row.get("project_id"));
        assertEquals("BIDDING", row.get("bid_status"));
        Assertions.assertFalse(((String) row.get("bid_project_name")).startsWith(" "));
        assertEquals("受控创建", row.get("remark"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cost_item WHERE source_type = 'BID_COST' AND source_id = ?",
                Integer.class, bidId));
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
        mockMvc.perform(getWith("/bid-cost/999999").cookie(adminCookie()))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("BID_COST_NOT_FOUND"));
    }

    @Test @Order(6) @DisplayName("GET /bid-cost/{id} enforces authentication and bid:query")
    void testGetById_QueryPermissionBoundary() throws Exception {
        Assertions.assertNotNull(bidId);
        mockMvc.perform(getWith("/bid-cost/" + bidId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(getWith("/bid-cost/" + bidId).cookie(userCookie(TENANT_ID, List.of())))
                .andExpect(status().isForbidden());
        mockMvc.perform(getWith("/bid-cost/" + bidId)
                        .cookie(userCookie(TENANT_ID, List.of("bid:query"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(String.valueOf(bidId)));
    }

    @Test @Order(6) @DisplayName("GET /bid-cost/{id} hides another tenant record as not found")
    void testGetById_TenantIsolation() throws Exception {
        long id = 910000000000L + Math.abs(System.nanoTime() % 100000000L);
        jdbcTemplate.update("""
                INSERT INTO bid_cost (id, tenant_id, bid_project_name, bid_status, deleted_flag)
                VALUES (?, ?, ?, 'BIDDING', 0)
                """, id, 9001L, "CROSS-TENANT-BID-DETAIL-" + id);
        try {
            mockMvc.perform(getWith("/bid-cost/" + id)
                            .cookie(userCookie(TENANT_ID, List.of("bid:query"))))
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("$.code").value("BID_COST_NOT_FOUND"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        } finally {
            jdbcTemplate.update("DELETE FROM bid_cost WHERE id = ?", id);
        }
    }

    @Test @Order(6) @DisplayName("PUT /bid-cost/{id} updates only controlled fields")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(bidId);
        String name = "BID-UPD-" + System.nanoTime();
        String body = "{\"bidProjectName\":\"  " + name + "  \",\"remark\":\" 修改备注 \","
                + "\"tenantId\":9001,\"projectId\":10001,\"bidStatus\":\"WON\",\"amount\":999}";
        mockMvc.perform(putWith("/bid-cost/" + bidId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT tenant_id, project_id, bid_status, bid_project_name, remark FROM bid_cost WHERE id = ?", bidId);
        assertEquals(TENANT_ID, ((Number) row.get("tenant_id")).longValue());
        Assertions.assertNull(row.get("project_id"));
        assertEquals("BIDDING", row.get("bid_status"));
        assertEquals(name, row.get("bid_project_name"));
        assertEquals("修改备注", row.get("remark"));
    }

    @Test @Order(6) @DisplayName("PUT /bid-cost/{id} enforces authentication and bid:edit")
    void testUpdate_PermissionBoundary() throws Exception {
        Assertions.assertNotNull(bidId);
        String body = "{\"bidProjectName\":\"权限测试\"}";
        mockMvc.perform(putWith("/bid-cost/" + bidId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(putWith("/bid-cost/" + bidId).cookie(userCookie(TENANT_ID, List.of("bid:query")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(putWith("/bid-cost/" + bidId).cookie(userCookie(TENANT_ID, List.of("bid:edit")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(6) @DisplayName("bid:edit does not grant bid status transitions")
    void testUpdate_EditPermissionDoesNotGrantStatusTransitions() throws Exception {
        Assertions.assertNotNull(bidId);
        Cookie editCookie = userCookie(TENANT_ID, List.of("bid:edit"));
        mockMvc.perform(putWith("/bid-cost/" + bidId + "/won").cookie(editCookie).param("projectId", "10001"))
                .andExpect(status().isForbidden());
        mockMvc.perform(putWith("/bid-cost/" + bidId + "/lost").cookie(editCookie))
                .andExpect(status().isForbidden());
    }

    @Test @Order(6) @DisplayName("PUT /bid-cost/{id}/won enforces status permission, tenant, state and project data scope")
    void testMarkAsWon_PermissionAndBoundaryContract() throws Exception {
        long base = 960000000000L + Math.abs(System.nanoTime() % 10000000L) * 10;
        long allowedId = base + 1;
        long crossTenantId = base + 2;
        long invalidStateId = base + 3;
        long ownedProjectId = base + 4;
        jdbcTemplate.update("""
                INSERT INTO pm_project (
                    id, tenant_id, project_code, project_name, project_type,
                    status, approval_status, created_by, deleted_flag
                ) VALUES (?, ?, ?, ?, '房建工程', 'ACTIVE', 'APPROVED', ?, 0)
                """, ownedProjectId, TENANT_ID, "BID-WON-PROJECT-" + ownedProjectId,
                "投标中标权限测试项目", 99L);
        jdbcTemplate.update("""
                INSERT INTO bid_cost (id, tenant_id, bid_project_name, bid_status, deleted_flag)
                VALUES (?, ?, ?, 'BIDDING', 0)
                """, allowedId, TENANT_ID, "BID-WON-PERMISSION-" + allowedId);
        jdbcTemplate.update("""
                INSERT INTO bid_cost (id, tenant_id, bid_project_name, bid_status, deleted_flag)
                VALUES (?, ?, ?, 'BIDDING', 0)
                """, crossTenantId, 9001L, "BID-WON-CROSS-TENANT-" + crossTenantId);
        jdbcTemplate.update("""
                INSERT INTO bid_cost (id, tenant_id, bid_project_name, bid_status, deleted_flag)
                VALUES (?, ?, ?, 'WON', 0)
                """, invalidStateId, TENANT_ID, "BID-WON-INVALID-STATE-" + invalidStateId);
        try {
            mockMvc.perform(putWith("/bid-cost/" + allowedId + "/won").param("projectId", "10001"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(putWith("/bid-cost/" + allowedId + "/won")
                            .cookie(userCookie(TENANT_ID, List.of("bid:query")))
                            .param("projectId", "10001"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(putWith("/bid-cost/" + allowedId + "/won")
                            .cookie(userCookie(TENANT_ID, List.of("bid:edit")))
                            .param("projectId", "10001"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(putWith("/bid-cost/" + allowedId + "/won")
                            .cookie(userCookie(TENANT_ID, List.of("bid:status")))
                            .param("projectId", "10001"))
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
            assertEquals("BIDDING", jdbcTemplate.queryForObject(
                    "SELECT bid_status FROM bid_cost WHERE id = ?", String.class, allowedId));

            Cookie statusCookie = userCookie(TENANT_ID, List.of("bid:status"));
            mockMvc.perform(putWith("/bid-cost/" + allowedId + "/won")
                            .cookie(statusCookie)
                            .param("projectId", String.valueOf(ownedProjectId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
            assertEquals("WON", jdbcTemplate.queryForObject(
                    "SELECT bid_status FROM bid_cost WHERE id = ?", String.class, allowedId));

            mockMvc.perform(putWith("/bid-cost/999999999999/won")
                            .cookie(statusCookie)
                            .param("projectId", String.valueOf(ownedProjectId)))
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("$.code").value("BID_COST_NOT_FOUND"));
            mockMvc.perform(putWith("/bid-cost/" + crossTenantId + "/won")
                            .cookie(statusCookie)
                            .param("projectId", String.valueOf(ownedProjectId)))
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("$.code").value("BID_COST_NOT_FOUND"));
            mockMvc.perform(putWith("/bid-cost/" + invalidStateId + "/won")
                            .cookie(statusCookie)
                            .param("projectId", String.valueOf(ownedProjectId)))
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("$.code").value("BID_STATUS_INVALID"));
        } finally {
            jdbcTemplate.update("DELETE FROM bid_cost WHERE id IN (?, ?, ?)", allowedId, crossTenantId, invalidStateId);
            jdbcTemplate.update("DELETE FROM cost_summary WHERE project_id = ?", ownedProjectId);
            jdbcTemplate.update("DELETE FROM pm_project WHERE id = ?", ownedProjectId);
        }
    }

    @Test @Order(6) @DisplayName("PUT /bid-cost/{id} validates controlled fields")
    void testUpdate_ValidationBoundary() throws Exception {
        Assertions.assertNotNull(bidId);
        for (String body : List.of(
                "{}",
                "{\"bidProjectName\":\"   \"}",
                "{\"bidProjectName\":\"" + "项".repeat(201) + "\"}",
                "{\"bidProjectName\":\"合法项目\",\"remark\":\"" + "注".repeat(501) + "\"}")) {
            mockMvc.perform(putWith("/bid-cost/" + bidId).cookie(adminCookie())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Test @Order(6) @DisplayName("PUT /bid-cost/{id} hides another tenant record as not found")
    void testUpdate_TenantIsolation() throws Exception {
        long id = 920000000000L + Math.abs(System.nanoTime() % 100000000L);
        jdbcTemplate.update("""
                INSERT INTO bid_cost (id, tenant_id, bid_project_name, bid_status, deleted_flag)
                VALUES (?, ?, ?, 'BIDDING', 0)
                """, id, 9001L, "CROSS-TENANT-BID-UPDATE-" + id);
        try {
            mockMvc.perform(putWith("/bid-cost/" + id)
                            .cookie(userCookie(TENANT_ID, List.of("bid:edit")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"bidProjectName\":\"不可见更新\"}"))
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("$.code").value("BID_COST_NOT_FOUND"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        } finally {
            jdbcTemplate.update("DELETE FROM bid_cost WHERE id = ?", id);
        }
    }

    @Test @Order(10) @DisplayName("PUT /bid-cost/{id} rejects non-BIDDING state")
    void testUpdate_NonBiddingRejected() throws Exception {
        Assertions.assertNotNull(bidId);
        mockMvc.perform(putWith("/bid-cost/" + bidId).cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bidProjectName\":\"不可修改\"}"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("BID_STATUS_NOT_EDITABLE"));
    }

    @Test @Order(6) @DisplayName("DELETE /bid-cost/{id} enforces authentication and bid:delete")
    void testDelete_PermissionBoundary() throws Exception {
        long id = 930000000000L + Math.abs(System.nanoTime() % 100000000L);
        jdbcTemplate.update("""
                INSERT INTO bid_cost (id, tenant_id, bid_project_name, bid_status, deleted_flag)
                VALUES (?, ?, ?, 'BIDDING', 0)
                """, id, TENANT_ID, "BID-DELETE-PERMISSION-" + id);
        try {
            mockMvc.perform(deleteWith("/bid-cost/" + id))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(deleteWith("/bid-cost/" + id)
                            .cookie(userCookie(TENANT_ID, List.of("bid:query"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(deleteWith("/bid-cost/" + id)
                            .cookie(userCookie(TENANT_ID, List.of("bid:delete"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bid_cost WHERE id = ? AND deleted_flag = 1", Integer.class, id));
        } finally {
            jdbcTemplate.update("DELETE FROM bid_cost WHERE id = ?", id);
        }
    }

    @Test @Order(6) @DisplayName("DELETE /bid-cost/{id} hides another tenant record as not found")
    void testDelete_TenantIsolation() throws Exception {
        long id = 940000000000L + Math.abs(System.nanoTime() % 100000000L);
        jdbcTemplate.update("""
                INSERT INTO bid_cost (id, tenant_id, bid_project_name, bid_status, deleted_flag)
                VALUES (?, ?, ?, 'BIDDING', 0)
                """, id, 9001L, "CROSS-TENANT-BID-DELETE-" + id);
        try {
            mockMvc.perform(deleteWith("/bid-cost/" + id)
                            .cookie(userCookie(TENANT_ID, List.of("bid:delete"))))
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("$.code").value("BID_COST_NOT_FOUND"))
                    .andExpect(jsonPath("$.data").doesNotExist());
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT deleted_flag FROM bid_cost WHERE id = ?", Integer.class, id));
        } finally {
            jdbcTemplate.update("DELETE FROM bid_cost WHERE id = ?", id);
        }
    }

    @Test @Order(6) @DisplayName("DELETE /bid-cost/{id} rejects non-BIDDING states")
    void testDelete_NonBiddingRejected() throws Exception {
        for (String statusValue : List.of("WON", "LOST")) {
            long id = 950000000000L + Math.abs(System.nanoTime() % 100000000L);
            jdbcTemplate.update("""
                    INSERT INTO bid_cost (id, tenant_id, bid_project_name, bid_status, deleted_flag)
                    VALUES (?, ?, ?, ?, 0)
                    """, id, TENANT_ID, "NON-BIDDING-DELETE-" + statusValue + "-" + id, statusValue);
            try {
                mockMvc.perform(deleteWith("/bid-cost/" + id)
                                .cookie(userCookie(TENANT_ID, List.of("bid:delete"))))
                        .andExpect(status().is4xxClientError())
                        .andExpect(jsonPath("$.code").value("BID_STATUS_NOT_DELETABLE"));
                assertEquals(0, jdbcTemplate.queryForObject(
                        "SELECT deleted_flag FROM bid_cost WHERE id = ?", Integer.class, id));
            } finally {
                jdbcTemplate.update("DELETE FROM bid_cost WHERE id = ?", id);
            }
        }
    }

    @Test @Order(7) @DisplayName("DELETE /bid-cost/{id} -> 200 deletes bid (before won)")
    void testDelete() throws Exception {
        Assertions.assertNotNull(bidId);
        mockMvc.perform(deleteWith("/bid-cost/" + bidId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(8) @DisplayName("POST /bid-cost -> 200 recreates after delete")
    void testRecreate() throws Exception {
        String body = "{\"bidProjectName\":\"BID-TEST-RECREATE-" + System.nanoTime() + "\"}";
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
