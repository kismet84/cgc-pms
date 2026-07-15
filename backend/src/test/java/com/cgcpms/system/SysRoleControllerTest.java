package com.cgcpms.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.common.ratelimit.FallbackRateLimitCounterStore;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysRoleMenu;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysRoleMenuMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest(properties = {
        "spring.main.allow-circular-references=true",
        "jwt.secret=issue-040-025-controller-test-secret-key-at-least-sixty-four-characters-long"
})
@AutoConfigureMockMvc @ActiveProfiles("local")
@DisplayName("SysRoleController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SysRoleControllerTest {
    @Autowired private MockMvc mockMvc; @Autowired private JwtUtils jwtUtils;
    @Autowired private FallbackRateLimitCounterStore counterStore;
    @Autowired private SysRoleMapper roleMapper; @Autowired private SysRoleMenuMapper roleMenuMapper;
    @Autowired private ObjectMapper objectMapper;
    private static final long ADMIN_ID = 1L; private static final long TENANT_ID = 0L;
    private Long roleId;

    @BeforeEach
    void setUp() {
        counterStore.clear();
    }

    private Cookie adminCookie() {
        return authCookie(TENANT_ID, List.of("ADMIN"), List.of());
    }

    private Cookie authCookie(long tenantId, List<String> roles, List<String> permissions) {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(ADMIN_ID, "admin", tenantId, roles, permissions));
    }

    @Test @Order(1) @DisplayName("GET /system/roles without JWT -> 401")
    void testUnauthorized() throws Exception { mockMvc.perform(g("/system/roles")).andExpect(status().isUnauthorized()); }

    @Test @Order(2) @DisplayName("GET /system/roles -> 200 with paginated data")
    void testList() throws Exception {
        mockMvc.perform(g("/system/roles").cookie(adminCookie()).param("pageNo","1").param("pageSize","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(3) @DisplayName("POST /system/roles -> 200 creates role")
    void testCreate() throws Exception {
        String roleCode = "ROLE-TEST-" + System.nanoTime();
        String body = "{\"id\":999999,\"tenantId\":777,\"roleCode\":\"" + roleCode
                + "\",\"roleName\":\"测试角色\"}";
        String resp = mockMvc.perform(p("/system/roles").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        roleId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(roleId);
        Assertions.assertNotEquals(999999L, roleId);

        SysRole saved = roleMapper.selectById(roleId);
        Assertions.assertEquals(TENANT_ID, saved.getTenantId());
        Assertions.assertEquals("CUSTOM", saved.getRoleType());
        Assertions.assertEquals(2, saved.getRoleLevel());
        Assertions.assertEquals("ENABLE", saved.getStatus());
        Assertions.assertEquals("SELF", saved.getDataScope());
        Assertions.assertEquals(0, roleMenuMapper.selectCount(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId)));

        String currentTenantList = mockMvc.perform(g("/system/roles").cookie(adminCookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode createdRole = java.util.stream.StreamSupport.stream(
                        objectMapper.readTree(currentTenantList).path("data").spliterator(), false)
                .filter(node -> roleCode.equals(node.path("roleCode").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("新建角色未出现在当前租户角色列表"));
        Assertions.assertEquals("测试角色", createdRole.path("roleName").asText());
        Assertions.assertEquals("CUSTOM", createdRole.path("roleType").asText());
        Assertions.assertEquals("ENABLE", createdRole.path("status").asText());
        Assertions.assertEquals("SELF", createdRole.path("dataScope").asText());
        Assertions.assertTrue(createdRole.path("menuIds").isArray());
        Assertions.assertTrue(createdRole.path("menuIds").isEmpty());

        long beforeDuplicate = roleMapper.selectCount(null);
        mockMvc.perform(p("/system/roles").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_CODE_EXISTS"));
        Assertions.assertEquals(beforeDuplicate, roleMapper.selectCount(null));

        String otherTenantList = mockMvc.perform(g("/system/roles")
                        .cookie(authCookie(999L, List.of("ADMIN"), List.of())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Assertions.assertFalse(otherTenantList.contains(roleCode));
    }

    @Test @Order(4) @DisplayName("POST /system/roles missing required -> 400")
    void testCreate_Missing() throws Exception {
        mockMvc.perform(p("/system/roles").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(4) @DisplayName("POST /system/roles supports system:role:add without admin role")
    void testCreate_WithExplicitPermission() throws Exception {
        String code = "PERMISSION-ROLE-" + System.nanoTime();
        String body = "{\"roleCode\":\"" + code + "\",\"roleName\":\"权限创建角色\"}";
        String resp = mockMvc.perform(p("/system/roles")
                        .cookie(authCookie(TENANT_ID, List.of("USER"), List.of("system:role:add")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andReturn().getResponse().getContentAsString();
        Long createdId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        mockMvc.perform(d("/system/roles/" + createdId).cookie(adminCookie()))
                .andExpect(status().isOk());
    }

    @Test @Order(4) @DisplayName("POST /system/roles rejects requests without role:add")
    void testCreate_Forbidden() throws Exception {
        mockMvc.perform(p("/system/roles")
                        .cookie(authCookie(TENANT_ID, List.of("USER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"FORBIDDEN_ROLE\",\"roleName\":\"无权限\"}"))
                .andExpect(status().isForbidden());
    }

    @Test @Order(4) @DisplayName("POST /system/roles without JWT -> 401")
    void testCreate_Unauthorized() throws Exception {
        mockMvc.perform(p("/system/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"NO_JWT_ROLE\",\"roleName\":\"未登录\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(4) @DisplayName("POST /system/roles rejects reserved and elevated role fields")
    void testCreate_RejectsPrivilegeEscalationPayloads() throws Exception {
        long before = roleMapper.selectCount(null);
        long beforeMenus = roleMenuMapper.selectCount(null);
        List<String> payloads = List.of(
                "{\"roleCode\":\"ADMIN\",\"roleName\":\"伪管理员\"}",
                "{\"roleCode\":\" super_admin \",\"roleName\":\"伪超管\"}",
                "{\"roleCode\":\"SYSTEM_TYPE_ROLE\",\"roleName\":\"伪系统角色\",\"roleType\":\"SYSTEM\"}",
                "{\"roleCode\":\"LEVEL_ZERO_ROLE\",\"roleName\":\"伪零级\",\"roleLevel\":0}",
                "{\"roleCode\":\"LEVEL_ONE_ROLE\",\"roleName\":\"伪一级\",\"roleLevel\":1}"
        );
        for (String payload : payloads) {
            mockMvc.perform(p("/system/roles").cookie(adminCookie())
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ROLE_CREATE_PRIVILEGE_ESCALATION"));
        }
        Assertions.assertEquals(before, roleMapper.selectCount(null));
        Assertions.assertEquals(beforeMenus, roleMenuMapper.selectCount(null));
    }

    @Test @Order(5) @DisplayName("GET /system/roles/{id} -> 200")
    void testGetById() throws Exception {
        Assertions.assertNotNull(roleId);
        mockMvc.perform(g("/system/roles/" + roleId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(roleId.toString()))
                .andExpect(jsonPath("$.data.roleCode").exists())
                .andExpect(jsonPath("$.data.roleName").exists())
                .andExpect(jsonPath("$.data.roleType").exists())
                .andExpect(jsonPath("$.data.status").exists())
                .andExpect(jsonPath("$.data.dataScope").exists())
                .andExpect(jsonPath("$.data.menuIds").isArray())
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.tenantId").doesNotExist())
                .andExpect(jsonPath("$.data.roleLevel").doesNotExist())
                .andExpect(jsonPath("$.data.createdBy").doesNotExist())
                .andExpect(jsonPath("$.data.updatedBy").doesNotExist())
                .andExpect(jsonPath("$.data.deletedFlag").doesNotExist());
    }

    @Test @Order(5) @DisplayName("GET /system/roles/{id} supports system:role:query")
    void testGetById_WithExplicitPermission() throws Exception {
        Assertions.assertNotNull(roleId);
        mockMvc.perform(g("/system/roles/" + roleId)
                        .cookie(authCookie(TENANT_ID, List.of("USER"), List.of("system:role:query"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(roleId.toString()));
    }

    @Test @Order(5) @DisplayName("GET /system/roles/{id} supports SUPER_ADMIN")
    void testGetById_WithSuperAdminRole() throws Exception {
        Assertions.assertNotNull(roleId);
        mockMvc.perform(g("/system/roles/" + roleId)
                        .cookie(authCookie(TENANT_ID, List.of("SUPER_ADMIN"), List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(roleId.toString()));
    }

    @Test @Order(5) @DisplayName("GET /system/roles/{id} rejects requests without role:query")
    void testGetById_Forbidden() throws Exception {
        Assertions.assertNotNull(roleId);
        mockMvc.perform(g("/system/roles/" + roleId)
                        .cookie(authCookie(TENANT_ID, List.of("USER"), List.of())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test @Order(5) @DisplayName("GET /system/roles/{id} without JWT -> 401")
    void testGetById_Unauthorized() throws Exception {
        Assertions.assertNotNull(roleId);
        mockMvc.perform(g("/system/roles/" + roleId))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(5) @DisplayName("GET /system/roles/{id} hides cross-tenant and missing roles")
    void testGetById_HidesCrossTenantAndMissing() throws Exception {
        Assertions.assertNotNull(roleId);
        mockMvc.perform(g("/system/roles/" + roleId)
                        .cookie(authCookie(999L, List.of("ADMIN"), List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));
        mockMvc.perform(g("/system/roles/9223372036854775806").cookie(adminCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));
    }

    @Test @Order(6) @DisplayName("PUT /system/roles/{id} updates only whitelisted fields")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(roleId);
        SysRole before = roleMapper.selectById(roleId);
        long menuCount = roleMenuMapper.selectCount(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        String body = "{\"id\":999999,\"tenantId\":777,\"roleCode\":\"" + before.getRoleCode()
                + "\",\"roleName\":\"更新角色\",\"status\":\"DISABLE\",\"dataScope\":\"DEPT\""
                + ",\"roleType\":\"SYSTEM\",\"roleLevel\":0}";
        mockMvc.perform(u("/system/roles/" + roleId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));

        SysRole saved = roleMapper.selectById(roleId);
        Assertions.assertEquals("更新角色", saved.getRoleName());
        Assertions.assertEquals("DISABLE", saved.getStatus());
        Assertions.assertEquals("DEPT", saved.getDataScope());
        Assertions.assertEquals(before.getRoleCode(), saved.getRoleCode());
        Assertions.assertEquals(before.getTenantId(), saved.getTenantId());
        Assertions.assertEquals(before.getRoleType(), saved.getRoleType());
        Assertions.assertEquals(before.getRoleLevel(), saved.getRoleLevel());
        Assertions.assertEquals(menuCount, roleMenuMapper.selectCount(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId)));
    }

    @Test @Order(6) @DisplayName("PUT /system/roles/{id} supports system:role:edit")
    void testUpdate_WithExplicitPermission() throws Exception {
        Assertions.assertNotNull(roleId);
        SysRole before = roleMapper.selectById(roleId);
        String body = "{\"roleCode\":\"" + before.getRoleCode()
                + "\",\"roleName\":\"显式权限修改\",\"status\":\"ENABLE\",\"dataScope\":\"SELF\"}";
        mockMvc.perform(u("/system/roles/" + roleId)
                        .cookie(authCookie(TENANT_ID, List.of("USER"), List.of("system:role:edit")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(6) @DisplayName("PUT /system/roles/{id} rejects missing role:edit")
    void testUpdate_Forbidden() throws Exception {
        Assertions.assertNotNull(roleId);
        SysRole before = roleMapper.selectById(roleId);
        String body = "{\"roleCode\":\"" + before.getRoleCode() + "\",\"roleName\":\"无权限修改\"}";
        mockMvc.perform(u("/system/roles/" + roleId)
                        .cookie(authCookie(TENANT_ID, List.of("USER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test @Order(6) @DisplayName("PUT /system/roles/{id} without JWT -> 401")
    void testUpdate_Unauthorized() throws Exception {
        Assertions.assertNotNull(roleId);
        SysRole before = roleMapper.selectById(roleId);
        String body = "{\"roleCode\":\"" + before.getRoleCode() + "\",\"roleName\":\"未登录修改\"}";
        mockMvc.perform(u("/system/roles/" + roleId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(6) @DisplayName("PUT /system/roles/{id} hides cross-tenant roles")
    void testUpdate_CrossTenant() throws Exception {
        Assertions.assertNotNull(roleId);
        SysRole before = roleMapper.selectById(roleId);
        String body = "{\"roleCode\":\"" + before.getRoleCode() + "\",\"roleName\":\"跨租户修改\"}";
        mockMvc.perform(u("/system/roles/" + roleId)
                        .cookie(authCookie(999L, List.of("ADMIN"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));
    }

    @Test @Order(6) @DisplayName("PUT /system/roles/{id} rejects immutable role code changes")
    void testUpdate_RejectsRoleCodeChange() throws Exception {
        Assertions.assertNotNull(roleId);
        String body = "{\"roleCode\":\"CHANGED_CODE\",\"roleName\":\"越权修改\"}";
        mockMvc.perform(u("/system/roles/" + roleId).cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_UPDATE_IMMUTABLE_FIELD"));
    }

    @Test @Order(7) @DisplayName("DELETE /system/roles/{id} -> 200")
    void testDelete() throws Exception {
        Assertions.assertNotNull(roleId);
        mockMvc.perform(d("/system/roles/" + roleId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(8) @DisplayName("DELETE /system/roles/{id} supports system:role:delete")
    void testDelete_WithExplicitPermission() throws Exception {
        Long targetId = createDeletableRole("DELETE-PERMISSION-");

        mockMvc.perform(d("/system/roles/" + targetId)
                        .cookie(authCookie(TENANT_ID, List.of("USER"), List.of("system:role:delete"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        Assertions.assertNull(roleMapper.selectById(targetId));
    }

    @Test @Order(9) @DisplayName("DELETE /system/roles/{id} rejects requests without role:delete")
    void testDelete_Forbidden() throws Exception {
        Long targetId = createDeletableRole("DELETE-FORBIDDEN-");
        try {
            mockMvc.perform(d("/system/roles/" + targetId)
                            .cookie(authCookie(TENANT_ID, List.of("USER"), List.of())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
            Assertions.assertNotNull(roleMapper.selectById(targetId));
        } finally {
            mockMvc.perform(d("/system/roles/" + targetId).cookie(adminCookie()))
                    .andExpect(status().isOk());
        }
    }

    @Test @Order(10) @DisplayName("DELETE /system/roles/{id} without JWT -> 401")
    void testDelete_Unauthorized() throws Exception {
        Long targetId = createDeletableRole("DELETE-UNAUTHORIZED-");
        try {
            mockMvc.perform(d("/system/roles/" + targetId))
                    .andExpect(status().isUnauthorized());
            Assertions.assertNotNull(roleMapper.selectById(targetId));
        } finally {
            mockMvc.perform(d("/system/roles/" + targetId).cookie(adminCookie()))
                    .andExpect(status().isOk());
        }
    }

    private Long createDeletableRole(String codePrefix) throws Exception {
        String body = "{\"roleCode\":\"" + codePrefix + System.nanoTime()
                + "\",\"roleName\":\"待删除角色\"}";
        String response = mockMvc.perform(p("/system/roles").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(response.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
    }

    private MockHttpServletRequestBuilder g(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder p(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder u(String p) { return put("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder d(String p) { return delete("/api" + p).contextPath("/api"); }
}
