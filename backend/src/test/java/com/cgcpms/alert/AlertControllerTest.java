package com.cgcpms.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc @ActiveProfiles("local")
@DisplayName("AlertController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AlertControllerTest {
    @Autowired private MockMvc mockMvc; @Autowired private JwtUtils jwtUtils; @Autowired private AlertLogMapper alertLogMapper;
    @Autowired private PmProjectMapper projectMapper; @Autowired private PmProjectMemberMapper projectMemberMapper;
    private static final long ADMIN_ID = 1L; private static final long TENANT_ID = 0L;
    private static final long DIFFERENT_PROJECT_MEMBER_ID = 92001L; private static final long OTHER_PROJECT_ID = 82001L;
    private static final long NO_ACCESS_MEMBER_ID = 92002L;

    private Cookie adminCookie() {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of()));
    }

    private Cookie memberCookie(long userId, String... permissions) {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(userId, "pm-" + userId, TENANT_ID, List.of("PROJECT_MANAGER"), Arrays.asList(permissions)));
    }

    private Cookie roleCookie(long userId, List<String> roles, String... permissions) {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(userId, "user-" + userId, TENANT_ID, roles, Arrays.asList(permissions)));
    }

    @Test @Order(1) @DisplayName("GET /alerts without JWT -> 401")
    void testUnauthorized() throws Exception { mockMvc.perform(g("/alerts")).andExpect(status().isUnauthorized()); }

    @Test @Order(2) @DisplayName("GET /alerts -> 200 with list data")
    void testList() throws Exception {
        mockMvc.perform(g("/alerts").cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test @Order(12) @DisplayName("GET /alerts/processing-report -> 200 with aggregate metrics")
    void testProcessingReport() throws Exception {
        deleteReportControllerAlerts();
        try {
            AlertLog open = newAlert("TEST_REPORT_CTRL", 10001L);
            open.setSeverity("HIGH");
            open.setIsRead(0);
            open.setProcessStatus("OPEN");
            alertLogMapper.insert(open);

            AlertLog processed = newAlert("TEST_REPORT_CTRL", 10001L);
            processed.setSeverity("MEDIUM");
            processed.setIsRead(1);
            processed.setProcessStatus("PROCESSED");
            alertLogMapper.insert(processed);

            mockMvc.perform(g("/alerts/processing-report?projectId=10001&ruleType=TEST_REPORT_CTRL")
                            .cookie(adminCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data.totalCount").value(2))
                    .andExpect(jsonPath("$.data.unreadCount").value(1))
                    .andExpect(jsonPath("$.data.readCount").value(1))
                    .andExpect(jsonPath("$.data.severityCounts.HIGH").value(1))
                    .andExpect(jsonPath("$.data.severityCounts.MEDIUM").value(1))
                    .andExpect(jsonPath("$.data.processStatusCounts.OPEN").value(1))
                    .andExpect(jsonPath("$.data.processStatusCounts.PROCESSED").value(1));
        } finally {
            deleteReportControllerAlerts();
        }
    }

    @Test @Order(13) @DisplayName("GET /alerts/processing-report same-tenant different-project member -> denied")
    void testProcessingReport_SameTenantDifferentProjectMemberDenied() throws Exception {
        seedProjectIfAbsent(OTHER_PROJECT_ID, "ALERT-CTRL-OTHER");
        seedMemberIfAbsent(OTHER_PROJECT_ID, NO_ACCESS_MEMBER_ID, "PM");

        mockMvc.perform(g("/alerts/processing-report?projectId=10001&ruleType=TEST_REPORT_CTRL")
                        .cookie(memberCookie(NO_ACCESS_MEMBER_ID, "alert:view")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALERT_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("无权访问该预警"));
    }

    @Test @Order(3) @DisplayName("PUT /alerts/{id}/read non-existent -> still returns ok")
    void testMarkRead_NotFound() throws Exception {
        mockMvc.perform(u("/alerts/999999/read").cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(4) @DisplayName("POST /alerts/batch-evaluate -> 200")
    void testBatchEvaluate() throws Exception {
        mockMvc.perform(p("/alerts/batch-evaluate").cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(5) @DisplayName("PUT /alerts/{id}/status -> 200")
    void testUpdateStatus() throws Exception {
        AlertLog alert = new AlertLog();
        alert.setTenantId(TENANT_ID);
        alert.setProjectId(10001L);
        alert.setRuleType("TEST_STATUS");
        alert.setAlertDomain("CONTRACT");
        alert.setAlertCategory("CONTRACT_TERM");
        alert.setSeverity("LOW");
        alert.setMessage("控制器状态测试");
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setIsRead(0);
        alert.setProcessStatus("OPEN");
        alert.setDeletedFlag(0);
        alertLogMapper.insert(alert);

        mockMvc.perform(u("/alerts/" + alert.getId() + "/status").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processStatus\":\"PROCESSED\",\"statusRemark\":\"done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.processStatus").value("PROCESSED"));
    }

    @Test @Order(6) @DisplayName("PUT /alerts/{id}/status same-tenant different-project member -> denied")
    void testUpdateStatus_SameTenantDifferentProjectMemberDenied() throws Exception {
        seedProjectIfAbsent(OTHER_PROJECT_ID, "ALERT-CTRL-OTHER");
        seedMemberIfAbsent(OTHER_PROJECT_ID, DIFFERENT_PROJECT_MEMBER_ID, "PM");

        AlertLog alert = new AlertLog();
        alert.setTenantId(TENANT_ID);
        alert.setProjectId(10001L);
        alert.setRuleType("TEST_STATUS_DENIED");
        alert.setAlertDomain("CONTRACT");
        alert.setAlertCategory("CONTRACT_TERM");
        alert.setSeverity("LOW");
        alert.setMessage("控制器状态越权测试");
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setIsRead(0);
        alert.setProcessStatus("OPEN");
        alert.setDeletedFlag(0);
        alertLogMapper.insert(alert);

        mockMvc.perform(u("/alerts/" + alert.getId() + "/status").cookie(memberCookie(DIFFERENT_PROJECT_MEMBER_ID, "alert:edit"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processStatus\":\"PROCESSED\",\"statusRemark\":\"denied\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALERT_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("无权访问该预警"));

        AlertLog unchanged = alertLogMapper.selectById(alert.getId());
        Assertions.assertEquals("OPEN", unchanged.getProcessStatus());
    }

    @Test @Order(7) @DisplayName("GET /alerts project manager project member with alert:view -> 200")
    void testList_ProjectManagerMemberWithAlertView() throws Exception {
        seedMemberIfAbsent(10001L, DIFFERENT_PROJECT_MEMBER_ID, "PM");

        mockMvc.perform(g("/alerts?projectId=10001").cookie(memberCookie(DIFFERENT_PROJECT_MEMBER_ID, "alert:view")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test @Order(8) @DisplayName("GET /alerts/subscription purchase manager -> 返回受限域")
    void testGetSubscription() throws Exception {
        mockMvc.perform(g("/alerts/subscription")
                        .cookie(roleCookie(DIFFERENT_PROJECT_MEMBER_ID, List.of("PURCHASE_MANAGER"), "alert:view")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.effectiveSubscription.enabled").value(true))
                .andExpect(jsonPath("$.data.effectiveSubscription.domains[0]").value("PURCHASE"))
                .andExpect(jsonPath("$.data.availableOptions.domains[0]").value("PURCHASE"));
    }

    @Test @Order(9) @DisplayName("PUT /alerts/subscription -> 用户覆盖保存后仍按默认范围收敛")
    void testUpdateSubscription() throws Exception {
        mockMvc.perform(u("/alerts/subscription")
                        .cookie(roleCookie(DIFFERENT_PROJECT_MEMBER_ID, List.of("COMMERCIAL_MANAGER"), "alert:view"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subscription": {
                                    "enabled": true,
                                    "channels": ["IN_APP", "EMAIL"],
                                    "domains": ["CONTRACT", "PURCHASE"],
                                    "minSeverity": "HIGH",
                                    "notifyOnStatusChanged": false
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.effectiveSubscription.channels[0]").value("IN_APP"))
                .andExpect(jsonPath("$.data.effectiveSubscription.domains.length()").value(1))
                .andExpect(jsonPath("$.data.effectiveSubscription.domains[0]").value("CONTRACT"))
                .andExpect(jsonPath("$.data.effectiveSubscription.minSeverity").value("HIGH"))
                .andExpect(jsonPath("$.data.effectiveSubscription.notifyOnStatusChanged").value(false));
    }

    @Test @Order(10) @DisplayName("PUT /alerts/batch/read -> 支持部分成功")
    void testBatchMarkRead_PartialSuccess() throws Exception {
        AlertLog alert = newAlert("TEST_BATCH_READ", 10001L);
        alertLogMapper.insert(alert);

        mockMvc.perform(u("/alerts/batch/read").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertIds\":[" + alert.getId() + ",999999]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.success").value(1))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.metrics.total").value(2))
                .andExpect(jsonPath("$.data.metrics.success").value(1))
                .andExpect(jsonPath("$.data.metrics.failed").value(1))
                .andExpect(jsonPath("$.data.metrics.skipped").value(0))
                .andExpect(jsonPath("$.data.failures[0].alertId").value(999999));

        AlertLog updated = alertLogMapper.selectById(alert.getId());
        Assertions.assertEquals(1, updated.getIsRead());
    }

    @Test @Order(11) @DisplayName("PUT /alerts/batch/status -> 非法状态逐条失败")
    void testBatchUpdateStatus_InvalidStatus() throws Exception {
        AlertLog alert = newAlert("TEST_BATCH_STATUS_INVALID", 10001L);
        alertLogMapper.insert(alert);

        mockMvc.perform(u("/alerts/batch/status").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertIds\":[" + alert.getId() + "],\"processStatus\":\"OPEN\",\"statusRemark\":\"bad\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.success").value(0))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.metrics.total").value(1))
                .andExpect(jsonPath("$.data.metrics.success").value(0))
                .andExpect(jsonPath("$.data.metrics.failed").value(1))
                .andExpect(jsonPath("$.data.metrics.skipped").value(0))
                .andExpect(jsonPath("$.data.failures[0].reason").value("预警状态不合法"));

        AlertLog unchanged = alertLogMapper.selectById(alert.getId());
        Assertions.assertEquals("OPEN", unchanged.getProcessStatus());
    }

    private void seedProjectIfAbsent(long projectId, String projectCode) {
        if (projectMapper.selectById(projectId) != null) {
            return;
        }
        PmProject project = new PmProject();
        project.setId(projectId);
        project.setProjectCode(projectCode);
        project.setProjectName(projectCode);
        project.setProjectType("CONSTRUCTION");
        project.setContractAmount(new BigDecimal("1000000.00"));
        project.setTargetCost(new BigDecimal("800000.00"));
        project.setStatus("ACTIVE");
        project.setApprovalStatus("APPROVED");
        project.setTenantId(TENANT_ID);
        project.setCreatedBy(ADMIN_ID);
        projectMapper.insert(project);
    }

    private void seedMemberIfAbsent(long projectId, long userId, String roleCode) {
        Long count = projectMemberMapper.selectCount(new LambdaQueryWrapper<PmProjectMember>()
                .eq(PmProjectMember::getTenantId, TENANT_ID)
                .eq(PmProjectMember::getProjectId, projectId)
                .eq(PmProjectMember::getUserId, userId)
                .eq(PmProjectMember::getStatus, "ACTIVE"));
        if (count != null && count > 0) {
            return;
        }
        PmProjectMember member = new PmProjectMember();
        member.setTenantId(TENANT_ID);
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setRoleCode(roleCode);
        member.setStatus("ACTIVE");
        projectMemberMapper.insert(member);
    }

    private AlertLog newAlert(String ruleType, long projectId) {
        AlertLog alert = new AlertLog();
        alert.setTenantId(TENANT_ID);
        alert.setProjectId(projectId);
        alert.setRuleType(ruleType);
        alert.setAlertDomain("CONTRACT");
        alert.setAlertCategory("CONTRACT_TERM");
        alert.setSeverity("LOW");
        alert.setMessage(ruleType);
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setIsRead(0);
        alert.setProcessStatus("OPEN");
        alert.setDeletedFlag(0);
        return alert;
    }

    private void deleteReportControllerAlerts() {
        alertLogMapper.delete(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, TENANT_ID)
                .eq(AlertLog::getRuleType, "TEST_REPORT_CTRL"));
    }

    private MockHttpServletRequestBuilder g(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder p(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder u(String p) { return put("/api" + p).contextPath("/api"); }
}
