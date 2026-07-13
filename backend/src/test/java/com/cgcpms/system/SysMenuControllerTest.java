package com.cgcpms.system;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.common.ratelimit.FallbackRateLimitCounterStore;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.main.allow-circular-references=true",
        "jwt.secret=issue-040-019-test-secret-at-least-32-chars"
})
@AutoConfigureMockMvc @ActiveProfiles("local")
@DisplayName("SysMenuController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class SysMenuControllerTest {
    @Autowired private MockMvc mockMvc; @Autowired private JwtUtils jwtUtils;
    @Autowired private FallbackRateLimitCounterStore counterStore;
    private static final long ADMIN_ID = 1L; private static final long TENANT_ID = 0L;

    @BeforeEach
    void setUp() {
        counterStore.clear();
    }

    private Cookie adminCookie() {
        return cookie(List.of("ADMIN"), List.of());
    }

    private Cookie cookie(List<String> roles, List<String> permissions) {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(ADMIN_ID, "menu-test", TENANT_ID, roles, permissions));
    }

    @Test @Order(1) @DisplayName("GET /system/menus without JWT -> 401")
    void testUnauthorized() throws Exception { mockMvc.perform(g("/system/menus")).andExpect(status().isUnauthorized()); }

    @Test @Order(2) @DisplayName("GET /system/menus -> 200 with menu list")
    void testList() throws Exception {
        mockMvc.perform(g("/system/menus").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].parentId").exists())
                .andExpect(jsonPath("$.data[0].menuName").exists())
                .andExpect(jsonPath("$.data[0].menuType").exists())
                .andExpect(jsonPath("$.data[0].path").hasJsonPath())
                .andExpect(jsonPath("$.data[0].component").hasJsonPath())
                .andExpect(jsonPath("$.data[0].perms").hasJsonPath())
                .andExpect(jsonPath("$.data[0].icon").hasJsonPath())
                .andExpect(jsonPath("$.data[0].orderNum").exists())
                .andExpect(jsonPath("$.data[0].status").exists())
                .andExpect(jsonPath("$.data[0].visible").exists())
                .andExpect(jsonPath("$.data[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.data[0].deletedFlag").doesNotExist())
                .andExpect(jsonPath("$.data[0].createdBy").doesNotExist())
                .andExpect(jsonPath("$.data[0].updatedBy").doesNotExist())
                .andExpect(jsonPath("$.data[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].updatedAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].remark").doesNotExist())
                .andExpect(jsonPath("$.data[0].children").doesNotExist());
    }

    @Test @Order(3) @DisplayName("GET /system/menus/tree -> 200 with tree data")
    void testGetTree() throws Exception {
        mockMvc.perform(g("/system/menus/tree").cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(4) @DisplayName("GET /system/menus/{id} -> 200")
    void testGetById() throws Exception {
        mockMvc.perform(g("/system/menus/1").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.parentId").exists())
                .andExpect(jsonPath("$.data.menuName").exists())
                .andExpect(jsonPath("$.data.menuType").exists())
                .andExpect(jsonPath("$.data.path").hasJsonPath())
                .andExpect(jsonPath("$.data.component").hasJsonPath())
                .andExpect(jsonPath("$.data.perms").hasJsonPath())
                .andExpect(jsonPath("$.data.icon").hasJsonPath())
                .andExpect(jsonPath("$.data.orderNum").exists())
                .andExpect(jsonPath("$.data.status").exists())
                .andExpect(jsonPath("$.data.visible").exists())
                .andExpect(jsonPath("$.data.tenantId").doesNotExist())
                .andExpect(jsonPath("$.data.deletedFlag").doesNotExist())
                .andExpect(jsonPath("$.data.createdBy").doesNotExist())
                .andExpect(jsonPath("$.data.updatedBy").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.data.remark").doesNotExist())
                .andExpect(jsonPath("$.data.children").doesNotExist());
    }

    @Test @Order(5) @DisplayName("POST /system/menus missing required -> 400")
    void testCreate_Missing() throws Exception {
        mockMvc.perform(p("/system/menus").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(6) @DisplayName("POST /system/menus ADMIN -> 200")
    void testCreate_Admin() throws Exception {
        mockMvc.perform(p("/system/menus").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":0,\"menuName\":\"管理员新建菜单\",\"menuType\":\"MENU\",\"path\":\"/admin-created\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test @Order(7) @DisplayName("POST /system/menus system:menu:add -> 200")
    void testCreate_WithPermission() throws Exception {
        mockMvc.perform(p("/system/menus")
                        .cookie(cookie(List.of("USER"), List.of("system:menu:add")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":0,\"menuName\":\"权限码新建菜单\",\"menuType\":\"DIR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(8) @DisplayName("POST /system/menus without role or permission -> 403")
    void testCreate_Forbidden() throws Exception {
        mockMvc.perform(p("/system/menus")
                        .cookie(cookie(List.of("USER"), List.of("system:menu:query")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":0,\"menuName\":\"越权新建菜单\",\"menuType\":\"MENU\"}"))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder g(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder p(String p) { return post("/api" + p).contextPath("/api"); }
}
