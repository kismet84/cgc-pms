package com.cgcpms.system;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.common.JwtHttpTestTokenFactory;
import com.cgcpms.common.ratelimit.FallbackRateLimitCounterStore;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.role.SystemRoleContract;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=issue-040-025-controller-test-secret-key-at-least-sixty-four-characters-long"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("SysRoleController fixed catalog contract")
class SysRoleControllerTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ID = 1L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtHttpTestTokenFactory tokens;
    @Autowired private FallbackRateLimitCounterStore counterStore;
    @Autowired private SysRoleMapper roleMapper;

    @BeforeEach
    void clearRateLimit() {
        counterStore.clear();
    }

    @Test
    void listRequiresAuthenticationAndReturnsNineVisibleFixedRoles() throws Exception {
        mockMvc.perform(get("/api/system/roles").contextPath("/api"))
                .andExpect(status().isUnauthorized());

        var result = mockMvc.perform(get("/api/system/roles").contextPath("/api")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(9))
                .andExpect(jsonPath("$.data[0].roleCode").value(SystemRoleContract.COMPANY_OWNER))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(result.contains("SUPER_ADMIN"));
        org.junit.jupiter.api.Assertions.assertFalse(result.contains("\"roleCode\":\"ADMIN\""));

        mockMvc.perform(get("/api/system/roles").contextPath("/api")
                        .cookie(cookie(List.of("USER"), List.of("system:role:query"))))
                .andExpect(status().isOk());
    }

    @Test
    void detailExposesVisibleRoleButHidesSuperAdminAndLegacyAdmin() throws Exception {
        SysRole visible = roleMapper.selectList(null).stream()
                .filter(role -> TENANT_ID == role.getTenantId())
                .filter(role -> SystemRoleContract.COMPANY_OWNER.equals(role.getRoleCode()))
                .findFirst().orElseThrow();
        mockMvc.perform(get("/api/system/roles/{id}", visible.getId()).contextPath("/api")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value(SystemRoleContract.COMPANY_OWNER))
                .andExpect(jsonPath("$.data.tenantId").doesNotExist())
                .andExpect(jsonPath("$.data.roleLevel").doesNotExist());

        for (String hidden : List.of(SystemRoleContract.HIDDEN_SUPER_ADMIN, "ADMIN")) {
            SysRole role = roleMapper.selectList(null).stream()
                    .filter(item -> TENANT_ID == item.getTenantId())
                    .filter(item -> hidden.equals(item.getRoleCode()))
                    .findFirst().orElseThrow();
            mockMvc.perform(get("/api/system/roles/{id}", role.getId()).contextPath("/api")
                            .cookie(adminCookie()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));
        }
    }

    @Test
    void createUpdateAndDeleteAreFailClosedForFixedCatalog() throws Exception {
        SysRole visible = roleMapper.selectList(null).stream()
                .filter(role -> TENANT_ID == role.getTenantId())
                .filter(role -> SystemRoleContract.PROJECT_MANAGER.equals(role.getRoleCode()))
                .findFirst().orElseThrow();

        mockMvc.perform(post("/api/system/roles").contextPath("/api")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"CUSTOM\",\"roleName\":\"custom\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_CATALOG_FIXED"));

        mockMvc.perform(put("/api/system/roles/{id}", visible.getId()).contextPath("/api")
                        .cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"PROJECT_MANAGER\",\"roleName\":\"changed\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_CATALOG_FIXED"));

        mockMvc.perform(delete("/api/system/roles/{id}", visible.getId()).contextPath("/api")
                        .cookie(adminCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_CATALOG_FIXED"));
    }

    @Test
    void mutationEndpointsStillEnforcePermissionBeforeCatalogGuard() throws Exception {
        mockMvc.perform(post("/api/system/roles").contextPath("/api")
                        .cookie(cookie(List.of("USER"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"CUSTOM\",\"roleName\":\"custom\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/system/roles").contextPath("/api")
                        .cookie(cookie(List.of("USER"), List.of("system:role:add")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"CUSTOM\",\"roleName\":\"custom\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_CATALOG_FIXED"));
    }

    private Cookie adminCookie() {
        return cookie(List.of("ADMIN"), List.of());
    }

    private Cookie cookie(List<String> roles, List<String> permissions) {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                tokens.generateToken(USER_ID, "admin", TENANT_ID, roles, permissions));
    }
}
