package com.cgcpms.overhead;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.overhead.service.OverheadAllocationService;
import com.cgcpms.overhead.entity.OverheadAllocationRule;
import com.cgcpms.overhead.vo.OverheadAllocationExecutionResult;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.main.allow-circular-references=true",
        "spring.main.lazy-initialization=true",
        "jwt.secret=issue-040-024-controller-test-secret-key-at-least-sixty-four-characters-long"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OverheadAllocationControllerTest {

    private static final long USER_ID = 94002601L;
    private static final long TENANT_ID = 940026L;
    private static final LocalDate PERIOD = LocalDate.of(2026, 6, 30);

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private OverheadAllocationService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM sys_operation_audit_log WHERE tenant_id=?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM overhead_allocation_run WHERE tenant_id=?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM overhead_allocation_rule WHERE tenant_id=?", TENANT_ID);
        when(service.executeAllocation(anyLong(), any(LocalDate.class))).thenAnswer(invocation -> {
            LocalDate period = invocation.getArgument(1);
            if (!period.equals(YearMonth.from(period).atEndOfMonth())
                    || !YearMonth.from(period).isBefore(YearMonth.now())) {
                throw new BusinessException("INVALID_OVERHEAD_PERIOD", "非法分摊期间");
            }
            return new OverheadAllocationExecutionResult(
                    period.toString(), 0, 0, 0, 0, "0.00", false);
        });
        clearInvocations(service);
        OverheadAllocationRule rule = new OverheadAllocationRule();
        rule.setId(940026001L);
        rule.setTenantId(TENANT_ID);
        rule.setCostSubjectId(54010401L);
        rule.setAllocationBasis("DIRECT_LABOR");
        rule.setAllocationCycle("MONTHLY");
        rule.setStatus("ENABLE");
        Page<OverheadAllocationRule> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(rule));
        when(service.getPage(1, 10)).thenReturn(page);
        when(service.createValidated(anyLong(), any(String.class), any(String.class))).thenReturn(940026002L);
    }

    @Test
    @DisplayName("规则列表要求登录和 overhead:query 权限")
    void rulesRequireAuthenticationAndPermission() throws Exception {
        mockMvc.perform(getApi("/overhead-allocation/rules"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(getApi("/overhead-allocation/rules")
                        .cookie(cookie(List.of(), List.of("cost:ledger:query"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("规则查询权限或管理员可读取认证租户分页")
    void rulesAllowQueryPermissionOrAdmin() throws Exception {
        mockMvc.perform(getApi("/overhead-allocation/rules")
                        .cookie(cookie(List.of(), List.of("overhead:query")))
                        .param("pageNo", "1").param("pageSize", "10").param("tenantId", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value("940026001"));
        mockMvc.perform(getApi("/overhead-allocation/rules")
                        .cookie(cookie(List.of("ADMIN"), List.of()))
                        .param("pageNo", "1").param("pageSize", "10"))
                .andExpect(status().isOk());
        verify(service, org.mockito.Mockito.times(2)).getPage(1, 10);
    }

    @Test
    @DisplayName("规则新建要求 overhead:add，管理员与显式权限只传白名单字段")
    void createRulePermissionAndWhitelistContract() throws Exception {
        String body = """
                {"costSubjectId":54010401,"allocationBasis":"DIRECT_LABOR","allocationCycle":"MONTHLY",
                 "id":999,"tenantId":999999,"status":"DISABLE","createdBy":888}
                """;
        mockMvc.perform(postApi("/overhead-allocation/rules")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(postApi("/overhead-allocation/rules")
                        .cookie(cookie(List.of(), List.of("overhead:query")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(postApi("/overhead-allocation/rules")
                        .cookie(cookie(List.of(), List.of("overhead:add")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(940026002L));
        mockMvc.perform(postApi("/overhead-allocation/rules")
                        .cookie(cookie(List.of("ADMIN"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(service, org.mockito.Mockito.times(2))
                .createValidated(54010401L, "DIRECT_LABOR", "MONTHLY");
    }

    @Test
    @DisplayName("规则新建拒绝缺失科目、非法依据和非法周期")
    void createRuleRejectsInvalidFields() throws Exception {
        Cookie add = cookie(List.of(), List.of("overhead:add"));
        for (String body : List.of(
                "{\"allocationBasis\":\"DIRECT_LABOR\",\"allocationCycle\":\"MONTHLY\"}",
                "{\"costSubjectId\":1,\"allocationBasis\":\"EQUAL\",\"allocationCycle\":\"MONTHLY\"}",
                "{\"costSubjectId\":1,\"allocationBasis\":\"USAGE\",\"allocationCycle\":\"YEARLY\"}")) {
            mockMvc.perform(postApi("/overhead-allocation/rules").cookie(add)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().is4xxClientError());
        }
        verify(service, org.mockito.Mockito.never())
                .createValidated(anyLong(), any(String.class), any(String.class));
    }

    @Test
    @DisplayName("规则修改要求 overhead:edit，路径 ID 与白名单字段为唯一更新输入")
    void updateRulePermissionAndWhitelistContract() throws Exception {
        String body = """
                {"costSubjectId":54010401,"allocationBasis":"USAGE","allocationCycle":"PER_OCCURRENCE",
                 "id":999,"tenantId":999999,"status":"DISABLE","updatedBy":888}
                """;
        mockMvc.perform(putApi("/overhead-allocation/rules/940026001")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(putApi("/overhead-allocation/rules/940026001")
                        .cookie(cookie(List.of(), List.of("overhead:query")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(putApi("/overhead-allocation/rules/940026001")
                        .cookie(cookie(List.of(), List.of("overhead:edit")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(putApi("/overhead-allocation/rules/940026001")
                        .cookie(cookie(List.of("ADMIN"), List.of()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(service, org.mockito.Mockito.times(2))
                .updateValidated(940026001L, 54010401L, "USAGE", "PER_OCCURRENCE");
    }

    @Test
    @DisplayName("规则修改拒绝缺失字段、非法依据和非法周期")
    void updateRuleRejectsInvalidFields() throws Exception {
        Cookie edit = cookie(List.of(), List.of("overhead:edit"));
        for (String body : List.of(
                "{\"allocationBasis\":\"DIRECT_LABOR\",\"allocationCycle\":\"MONTHLY\"}",
                "{\"costSubjectId\":1,\"allocationCycle\":\"MONTHLY\"}",
                "{\"costSubjectId\":1,\"allocationBasis\":\"EQUAL\",\"allocationCycle\":\"MONTHLY\"}",
                "{\"costSubjectId\":1,\"allocationBasis\":\"USAGE\",\"allocationCycle\":\"YEARLY\"}")) {
            mockMvc.perform(putApi("/overhead-allocation/rules/940026001").cookie(edit)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().is4xxClientError());
        }
        verify(service, org.mockito.Mockito.never())
                .updateValidated(anyLong(), anyLong(), any(String.class), any(String.class));
    }

    @Test
    @DisplayName("规则删除要求 overhead:delete 或管理员权限")
    void deleteRulePermissionContract() throws Exception {
        mockMvc.perform(deleteApi("/overhead-allocation/rules/940026001"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(deleteApi("/overhead-allocation/rules/940026001")
                        .cookie(cookie(List.of(), List.of("overhead:query"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(deleteApi("/overhead-allocation/rules/940026001")
                        .cookie(cookie(List.of(), List.of("overhead:delete"))))
                .andExpect(status().isOk());
        mockMvc.perform(deleteApi("/overhead-allocation/rules/940026001")
                        .cookie(cookie(List.of("SUPER_ADMIN"), List.of())))
                .andExpect(status().isOk());
        verify(service, org.mockito.Mockito.times(2)).delete(940026001L);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM sys_operation_audit_log WHERE tenant_id=?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM overhead_allocation_run WHERE tenant_id=?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM overhead_allocation_rule WHERE tenant_id=?", TENANT_ID);
        reset(service);
    }

    @Test
    @DisplayName("未登录执行返回 401")
    void executeRequiresAuthentication() throws Exception {
        mockMvc.perform(postApi("/overhead-allocation/execute").param("period", PERIOD.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("仅成本查询权限返回 403")
    void executeRejectsLedgerPermissionWithoutExecutePermission() throws Exception {
        mockMvc.perform(postApi("/overhead-allocation/execute")
                        .cookie(cookie(List.of(), List.of("cost:ledger:query")))
                        .param("period", PERIOD.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("显式执行权限用户与管理员可执行")
    void executeAllowsExplicitPermissionOrAdmin() throws Exception {
        mockMvc.perform(postApi("/overhead-allocation/execute")
                        .cookie(cookie(List.of(), List.of("overhead:execute")))
                        .param("period", PERIOD.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.period").value(PERIOD.toString()));

        mockMvc.perform(postApi("/overhead-allocation/execute")
                        .cookie(cookie(List.of("ADMIN"), List.of()))
                        .param("period", PERIOD.toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("请求 tenantId 不得覆盖认证租户")
    void requestTenantIdCannotOverrideAuthenticatedTenant() throws Exception {
        mockMvc.perform(postApi("/overhead-allocation/execute")
                        .cookie(cookie(List.of("SUPER_ADMIN"), List.of()))
                        .param("tenantId", "999999")
                        .param("period", PERIOD.toString()))
                .andExpect(status().isOk());

        verify(service).executeAllocation(TENANT_ID, PERIOD);
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM overhead_allocation_run WHERE tenant_id=999999", Integer.class));
    }

    @Test
    @DisplayName("非法文本、非月末、当前月份和未来月份均 fail-close")
    void invalidPeriodsFailClosed() throws Exception {
        Cookie admin = cookie(List.of("ADMIN"), List.of());
        mockMvc.perform(postApi("/overhead-allocation/execute").cookie(admin).param("period", "not-a-date"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(postApi("/overhead-allocation/execute").cookie(admin).param("period", "2026-06-01"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(postApi("/overhead-allocation/execute").cookie(admin)
                        .param("period", YearMonth.now().atEndOfMonth().toString()))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(postApi("/overhead-allocation/execute").cookie(admin)
                        .param("period", YearMonth.now().plusMonths(1).atEndOfMonth().toString()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("审计日志定位租户、期间、执行人，失败不记录伪成功")
    void auditCapturesPeriodTenantExecutorAndFailure() throws Exception {
        mockMvc.perform(postApi("/overhead-allocation/execute")
                        .cookie(cookie(List.of("ADMIN"), List.of()))
                        .param("period", "2026-06-01"))
                .andExpect(status().is4xxClientError());

        var rows = jdbcTemplate.queryForList("""
                SELECT tenant_id,user_id,business_type,business_id,success_flag
                FROM sys_operation_audit_log
                WHERE tenant_id=? AND business_type='OVERHEAD_ALLOCATION'
                ORDER BY id DESC
                """, TENANT_ID);
        assertEquals(1, rows.size());
        assertEquals(TENANT_ID, ((Number) rows.get(0).get("TENANT_ID")).longValue());
        assertEquals(USER_ID, ((Number) rows.get(0).get("USER_ID")).longValue());
        assertEquals("2026-06-01", rows.get(0).get("BUSINESS_ID"));
        assertEquals(0, ((Number) rows.get(0).get("SUCCESS_FLAG")).intValue());
    }

    private Cookie cookie(List<String> roles, List<String> permissions) {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(USER_ID, "overhead-controller-test", TENANT_ID, roles, permissions));
    }

    private MockHttpServletRequestBuilder postApi(String path) {
        return post("/api" + path).contextPath("/api");
    }

    private MockHttpServletRequestBuilder getApi(String path) {
        return get("/api" + path).contextPath("/api");
    }

    private MockHttpServletRequestBuilder putApi(String path) {
        return put("/api" + path).contextPath("/api");
    }

    private MockHttpServletRequestBuilder deleteApi(String path) {
        return delete("/api" + path).contextPath("/api");
    }

}
