package com.cgcpms.cost.controller;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.common.JwtHttpTestTokenFactory;
import com.cgcpms.cost.service.CostSubjectV2Service;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CostSubjectV2ControllerPermissionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtHttpTestTokenFactory tokenFactory;

    @MockitoBean
    private CostSubjectV2Service service;

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

    @Test
    @DisplayName("历史重算与关闭后调整创建权限按batchType精确隔离")
    void recalculationCreatePermissionMatchesBatchType() throws Exception {
        String history = "{\"projectId\":1,\"ruleVersionId\":2,\"batchType\":\"HISTORY_RECALCULATION\",\"reason\":\"test\"}";
        String postClose = "{\"projectId\":1,\"ruleVersionId\":2,\"batchType\":\"POST_CLOSE_ADJUSTMENT\",\"reason\":\"test\"}";

        mockMvc.perform(post("/api/cost-subject-v2/recalculation-batches").contextPath("/api")
                        .contentType(APPLICATION_JSON).content(history).cookie(cookie("cost:post-close:edit")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/cost-subject-v2/recalculation-batches").contextPath("/api")
                        .contentType(APPLICATION_JSON).content(postClose).cookie(cookie("cost:recalculation:edit")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/cost-subject-v2/recalculation-batches").contextPath("/api")
                        .contentType(APPLICATION_JSON).content(history).cookie(cookie("cost:recalculation:edit")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/cost-subject-v2/recalculation-batches").contextPath("/api")
                        .contentType(APPLICATION_JSON).content(postClose).cookie(cookie("cost:post-close:edit")))
                .andExpect(status().isOk());
    }

    private Cookie cookie(String permission) {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                tokenFactory.generateToken(1L, "cost-v2-test", 0L,
                        List.of("COMMON_USER"), List.of(permission)));
    }
}
