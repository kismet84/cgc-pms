package com.cgcpms.cost.controller;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CostSubjectV2ControllerPermissionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Test
    @DisplayName("V2查询端点未登录返回401")
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/cost-subject-v2/rules").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("归集规则Tab权限可读取规则及其映射版本")
    void ruleQueryPermissionCanLoadWholeWorkspace() throws Exception {
        Cookie cookie = cookie("cost:subject:rule:query");
        mockMvc.perform(get("/api/cost-subject-v2/rules").contextPath("/api").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mockMvc.perform(get("/api/cost-subject-v2/mapping-versions").contextPath("/api").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("审计Tab权限可读取投标转入与财务分摊追踪")
    void auditPermissionCanLoadTraceWorkspace() throws Exception {
        Cookie cookie = cookie("cost:subject:audit:query");
        mockMvc.perform(get("/api/cost-subject-v2/bid-transfers").contextPath("/api").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mockMvc.perform(get("/api/cost-subject-v2/finance-allocations").contextPath("/api").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("无关权限不能读取V2工作区")
    void rejectsUnrelatedPermission() throws Exception {
        mockMvc.perform(get("/api/cost-subject-v2/rules").contextPath("/api")
                        .cookie(cookie("project:query")))
                .andExpect(status().isForbidden());
    }

    private Cookie cookie(String permission) {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(1L, "cost-v2-test", 0L,
                        List.of("COMMON_USER"), List.of(permission)));
    }
}
