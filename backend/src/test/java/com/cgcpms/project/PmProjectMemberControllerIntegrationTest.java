package com.cgcpms.project;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PmProjectMemberControllerIntegrationTest {

    private static final long ADMIN_ID = 1L;
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long TARGET_USER_ID = 930013000001L;
    private static final long CROSS_TENANT_USER_ID = 930013000002L;
    private static final Timestamp ATTACK_TIME = Timestamp.valueOf("1999-01-01 00:00:00");
    private static final Timestamp FORCED_OLD_UPDATE_TIME = Timestamp.valueOf("2000-01-01 00:00:00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    @Test
    @Transactional
    void postUpdateAndRestoreKeepServerManagedFieldsAuthoritative() throws Exception {
        ensureAdmin();
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    id, tenant_id, username, password, real_name, status, is_admin,
                    created_by, updated_by, deleted_flag
                ) VALUES (?, ?, ?, '{noop}test', '主线93成员', 'ENABLE', 0, ?, ?, 0)
                """, TARGET_USER_ID, TENANT_ID, "m93-member-" + TARGET_USER_ID,
                ADMIN_ID, ADMIN_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    id, tenant_id, username, password, real_name, status, is_admin,
                    created_by, updated_by, deleted_flag
                ) VALUES (?, 999, ?, '{noop}test', '其他租户成员', 'ENABLE', 0, ?, ?, 0)
                """, CROSS_TENANT_USER_ID, "m93-cross-tenant-" + CROSS_TENANT_USER_ID,
                ADMIN_ID, ADMIN_ID);
        grantRole(930013100001L, TENANT_ID, TARGET_USER_ID, "EMPLOYEE");
        grantRole(930013100002L, TENANT_ID, TARGET_USER_ID, "PROCUREMENT_LEAD");

        mockMvc.perform(post("/api/projects/{projectId}/members", PROJECT_ID)
                        .contextPath("/api").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(attackBody(CROSS_TENANT_USER_ID, "EMPLOYEE", "跨租户用户")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_MEMBER_USER_INVALID"));
        Integer crossTenantMemberCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pm_project_member
                 WHERE project_id = ? AND user_id = ?
                """, Integer.class, PROJECT_ID, CROSS_TENANT_USER_ID);
        assertEquals(0, crossTenantMemberCount);

        String createResponse = mockMvc.perform(post("/api/projects/{projectId}/members", PROJECT_ID)
                        .contextPath("/api").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(attackBody(TARGET_USER_ID, "EMPLOYEE", "首次创建")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andReturn().getResponse().getContentAsString();
        long memberId = objectMapper.readTree(createResponse).path("data").asLong();

        Map<String, Object> created = readMember(memberId);
        assertNotEquals(42L, memberId);
        assertOwnershipAndAudit(created, TARGET_USER_ID);
        assertEquals("EMPLOYEE", created.get("role_code"));
        Timestamp originalCreatedAt = (Timestamp) created.get("created_at");
        long originalCreatedBy = ((Number) created.get("created_by")).longValue();
        jdbcTemplate.update("UPDATE pm_project_member SET updated_by = 12345, updated_at = ? WHERE id = ?",
                FORCED_OLD_UPDATE_TIME, memberId);

        mockMvc.perform(put("/api/projects/{projectId}/members/{id}", PROJECT_ID, memberId)
                        .contextPath("/api").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(attackBody(TARGET_USER_ID, "PROCUREMENT_LEAD", "合法更新")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));

        Map<String, Object> updated = readMember(memberId);
        assertOwnershipAndAudit(updated, TARGET_USER_ID);
        assertEquals("PROCUREMENT_LEAD", updated.get("role_code"));
        assertEquals(originalCreatedAt, updated.get("created_at"));
        assertEquals(originalCreatedBy, ((Number) updated.get("created_by")).longValue());
        assertEquals(ADMIN_ID, ((Number) updated.get("updated_by")).longValue());
        assertNotEquals(FORCED_OLD_UPDATE_TIME, updated.get("updated_at"));

        Map<String, Object> beforeRejectedUpdate = Map.copyOf(updated);
        mockMvc.perform(put("/api/projects/{projectId}/members/{id}", PROJECT_ID, memberId)
                        .contextPath("/api").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(attackBody(TARGET_USER_ID + 1, "EMPLOYEE", "非法换人")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_MEMBER_USER_IMMUTABLE"));
        assertEquals(beforeRejectedUpdate, readMember(memberId));

        mockMvc.perform(delete("/api/projects/{projectId}/members/{id}", PROJECT_ID, memberId)
                        .contextPath("/api").cookie(adminCookie()))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE pm_project_member SET updated_by = 12346, updated_at = ? WHERE id = ?",
                FORCED_OLD_UPDATE_TIME, memberId);
        String restoreResponse = mockMvc.perform(post("/api/projects/{projectId}/members", PROJECT_ID)
                        .contextPath("/api").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(attackBody(TARGET_USER_ID, "EMPLOYEE", "软删除恢复")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(memberId, objectMapper.readTree(restoreResponse).path("data").asLong());

        Map<String, Object> restored = readMember(memberId);
        assertOwnershipAndAudit(restored, TARGET_USER_ID);
        assertEquals(originalCreatedAt, restored.get("created_at"));
        assertEquals(originalCreatedBy, ((Number) restored.get("created_by")).longValue());
        assertEquals(ADMIN_ID, ((Number) restored.get("updated_by")).longValue());
        assertNotEquals(FORCED_OLD_UPDATE_TIME, restored.get("updated_at"));
        assertEquals("EMPLOYEE", restored.get("role_code"));
    }

    @Test
    @Transactional
    void memberOptionsAreTenantRoleScopedAndLegacyUpdatesStayReadable() throws Exception {
        ensureAdmin();
        long multiRoleUserId = 930013000011L;
        long disabledUserId = 930013000012L;
        long crossTenantUserId = 930013000013L;
        insertUser(multiRoleUserId, TENANT_ID, "multi-role", "多角色用户", "ENABLE");
        insertUser(disabledUserId, TENANT_ID, "disabled-role", "停用用户", "DISABLE");
        insertUser(crossTenantUserId, 999L, "cross-role", "跨租户用户", "ENABLE");
        grantRole(930013100011L, TENANT_ID, multiRoleUserId, "PROJECT_ACCOUNTANT");
        grantRole(930013100012L, TENANT_ID, multiRoleUserId, "EMPLOYEE");
        grantRole(930013100013L, TENANT_ID, disabledUserId, "EMPLOYEE");
        jdbcTemplate.update("""
                INSERT INTO sys_role (
                    id, tenant_id, role_code, role_name, role_type, status, data_scope,
                    created_by, updated_by, deleted_flag, role_level
                ) VALUES (?, 999, 'EMPLOYEE', '其他租户员工', 'CUSTOM', 'ENABLE',
                          'PROJECT_MEMBER', ?, ?, 0, 3)
                """, 930013200013L, ADMIN_ID, ADMIN_ID);
        grantRole(930013100014L, 999L, crossTenantUserId, "EMPLOYEE");

        String optionsResponse = mockMvc.perform(get("/api/projects/{projectId}/members/options", PROJECT_ID)
                        .contextPath("/api").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn().getResponse().getContentAsString();
        JsonNode options = objectMapper.readTree(optionsResponse).path("data");
        JsonNode multiRoleUser = findUser(options.path("users"), multiRoleUserId);
        assertNotNull(multiRoleUser);
        assertEquals(List.of("PROJECT_ACCOUNTANT", "EMPLOYEE"),
                objectMapper.convertValue(multiRoleUser.path("roleCodes"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
        assertFalse(hasUser(options.path("users"), disabledUserId));
        assertFalse(hasUser(options.path("users"), crossTenantUserId));
        assertFalse(options.path("usersTruncated").asBoolean());

        String createResponse = mockMvc.perform(post("/api/projects/{projectId}/members", PROJECT_ID)
                        .contextPath("/api").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(attackBody(multiRoleUserId, "EMPLOYEE", "候选创建")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long memberId = objectMapper.readTree(createResponse).path("data").asLong();

        String availableAfterCreate = mockMvc.perform(get("/api/projects/{projectId}/members/options", PROJECT_ID)
                        .contextPath("/api").cookie(memberEditorCookie(multiRoleUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(hasUser(objectMapper.readTree(availableAfterCreate).path("data").path("users"),
                multiRoleUserId));
        mockMvc.perform(get("/api/projects/{projectId}/members/options", PROJECT_ID)
                        .queryParam("includeUserId", Long.toString(multiRoleUserId))
                        .contextPath("/api").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users[0].userId").value(Long.toString(multiRoleUserId)))
                .andExpect(jsonPath("$.data.users[0].roleCodes.length()").value(2));
        mockMvc.perform(get("/api/projects/{projectId}/members", PROJECT_ID)
                        .contextPath("/api").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.userId == '%s')].realName"
                        .formatted(multiRoleUserId)).value("多角色用户"))
                .andExpect(jsonPath("$.data.records[?(@.userId == '%s')].roleName"
                        .formatted(multiRoleUserId)).value("员工"));

        jdbcTemplate.update("UPDATE pm_project_member SET status = 'INACTIVE' WHERE id = ?", memberId);
        jdbcTemplate.update("UPDATE sys_user SET status = 'DISABLE' WHERE id = ?", multiRoleUserId);
        sqlSessionTemplate.clearCache();
        mockMvc.perform(put("/api/projects/{projectId}/members/{id}", PROJECT_ID, memberId)
                        .contextPath("/api").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(attackBody(multiRoleUserId, "EMPLOYEE", "重新启用必须复验")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_MEMBER_USER_INVALID"));
        assertEquals("INACTIVE", jdbcTemplate.queryForObject(
                "SELECT status FROM pm_project_member WHERE id = ?", String.class, memberId));
        jdbcTemplate.update("UPDATE sys_user SET status = 'ENABLE' WHERE id = ?", multiRoleUserId);
        jdbcTemplate.update("UPDATE pm_project_member SET status = 'ACTIVE' WHERE id = ?", memberId);
        jdbcTemplate.update("UPDATE pm_project_member SET role_code = 'PM' WHERE id = ?", memberId);
        sqlSessionTemplate.clearCache();
        mockMvc.perform(put("/api/projects/{projectId}/members/{id}", PROJECT_ID, memberId)
                        .contextPath("/api").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(attackBody(multiRoleUserId, "PM", "历史角色未变")))
                .andExpect(status().isOk());
        assertEquals("PM", readMember(memberId).get("role_code"));

        mockMvc.perform(put("/api/projects/{projectId}/members/{id}", PROJECT_ID, memberId)
                        .contextPath("/api").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(attackBody(multiRoleUserId, "TECHNICAL_LEAD", "角色不匹配")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_MEMBER_ROLE_MISMATCH"));
        assertEquals("PM", readMember(memberId).get("role_code"));

        mockMvc.perform(post("/api/projects/{projectId}/members", PROJECT_ID)
                        .contextPath("/api").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(attackBody(disabledUserId, "EMPLOYEE", "停用用户")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_MEMBER_USER_INVALID"));
        assertTrue(options.path("roles").size() >= 2);
    }

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    private Cookie memberEditorCookie(long userId) {
        String token = jwtUtils.generateToken(
                userId, "member-editor", TENANT_ID, List.of("EMPLOYEE"), List.of("project:member:add"));
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    private void ensureAdmin() {
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    id, tenant_id, username, password, real_name, status, is_admin,
                    created_by, updated_by, deleted_flag
                ) SELECT ?, ?, 'admin', '{noop}test', '测试管理员', 'ENABLE', 1, ?, ?, 0
                  WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = ?)
                """, ADMIN_ID, TENANT_ID, ADMIN_ID, ADMIN_ID, ADMIN_ID);
    }

    private void insertUser(long id, long tenantId, String username, String realName, String status) {
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    id, tenant_id, username, password, real_name, status, is_admin,
                    created_by, updated_by, deleted_flag
                ) VALUES (?, ?, ?, '{noop}test', ?, ?, 0, ?, ?, 0)
                """, id, tenantId, username, realName, status, ADMIN_ID, ADMIN_ID);
    }

    private void grantRole(long id, long tenantId, long userId, String roleCode) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO sys_user_role (id, tenant_id, user_id, role_id)
                SELECT ?, ?, ?, r.id
                  FROM sys_role r
                 WHERE r.tenant_id = ?
                   AND r.role_code = ?
                   AND r.deleted_flag = 0
                """, id, tenantId, userId, tenantId, roleCode);
        assertEquals(1, inserted);
    }

    private JsonNode findUser(JsonNode users, long userId) {
        for (JsonNode user : users) {
            if (Long.toString(userId).equals(user.path("userId").asText())) return user;
        }
        return null;
    }

    private boolean hasUser(JsonNode users, long userId) {
        return findUser(users, userId) != null;
    }

    private Map<String, Object> readMember(long memberId) {
        return jdbcTemplate.queryForMap("""
                SELECT id, tenant_id, project_id, user_id, role_code, created_by, created_at,
                       updated_by, updated_at, deleted_flag
                  FROM pm_project_member WHERE id = ?
                """, memberId);
    }

    private void assertOwnershipAndAudit(Map<String, Object> row, long expectedUserId) {
        assertEquals(TENANT_ID, ((Number) row.get("tenant_id")).longValue());
        assertEquals(PROJECT_ID, ((Number) row.get("project_id")).longValue());
        assertEquals(expectedUserId, ((Number) row.get("user_id")).longValue());
        assertEquals(0, ((Number) row.get("deleted_flag")).intValue());
        assertNotEquals(999L, ((Number) row.get("created_by")).longValue());
        assertNotEquals(999L, ((Number) row.get("updated_by")).longValue());
        assertNotEquals(ATTACK_TIME, row.get("created_at"));
        assertNotEquals(ATTACK_TIME, row.get("updated_at"));
    }

    private String attackBody(long userId, String roleCode, String remark) {
        return """
                {"id":42,"tenantId":999,"projectId":888,"userId":%d,"roleCode":"%s",
                 "positionName":"材料员","status":"ACTIVE","remark":"%s",
                 "createdBy":999,"createdTime":"1999-01-01 00:00:00",
                 "createdAt":"1999-01-01T00:00:00","updatedBy":999,
                 "updatedTime":"1999-01-01 00:00:00","updatedAt":"1999-01-01T00:00:00",
                 "deletedFlag":1}
                """.formatted(userId, roleCode, remark);
    }
}
