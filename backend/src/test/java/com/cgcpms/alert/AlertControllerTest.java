package com.cgcpms.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.audit.entity.OperationAuditLog;
import com.cgcpms.audit.mapper.OperationAuditLogMapper;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.main.allow-circular-references=true",
        "jwt.secret=alert-controller-test-secret-key-at-least-sixty-four-characters-long"
})
@AutoConfigureMockMvc @ActiveProfiles("local")
@DisplayName("AlertController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AlertControllerTest {
    @Autowired private MockMvc mockMvc; @Autowired private JwtUtils jwtUtils; @Autowired private AlertLogMapper alertLogMapper;
    @Autowired private OperationAuditLogMapper operationAuditLogMapper;
    @Autowired private PmProjectMapper projectMapper; @Autowired private PmProjectMemberMapper projectMemberMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    private static final long ADMIN_ID = 1L; private static final long TENANT_ID = 0L;
    private static final long DIFFERENT_PROJECT_MEMBER_ID = 92001L; private static final long OTHER_PROJECT_ID = 82001L;
    private static final long NO_ACCESS_MEMBER_ID = 92002L; private static final long PURCHASE_MANAGER_ID = 92003L;

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

            mockMvc.perform(g("/alerts/rule-effect-report?projectId=10001&ruleType=TEST_REPORT_CTRL")
                            .cookie(adminCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data[?(@.ruleType == 'TEST_REPORT_CTRL')].generatedCount").exists());
        } finally {
            deleteReportControllerAlerts();
        }
    }

    @Test @Order(12) @DisplayName("POST /alerts/export-audit 契约带下载审计且允许 alert:view 或管理员")
    void testExportAuditContract() throws Exception {
        Method method = com.cgcpms.alert.controller.AlertController.class
                .getMethod("exportAudit", com.cgcpms.alert.dto.AlertExportAuditRequest.class);
        AuditedOperation audited = method.getAnnotation(AuditedOperation.class);
        assertNotNull(audited);
        assertEquals("DOWNLOAD", audited.type());
        assertEquals("ALERT_EXPORT", audited.businessType());
        assertEquals("#request.filterSignature", audited.businessIdExpression());

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals("hasAuthority('alert:view') or hasAnyRole('ADMIN','SUPER_ADMIN')", preAuthorize.value());
    }

    @Test @Order(12) @DisplayName("POST /alerts/export-audit -> 200 and persists minimal audit log")
    void testExportAuditPersistsAuditLog() throws Exception {
        String path = "/api/alerts/export-audit";
        String filterSignature = "alert-export-a1b2c3";
        long before = countAuditLogs("DOWNLOAD", path);

        mockMvc.perform(p("/alerts/export-audit").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "filterSignature": "%s",
                                  "recordCount": 2
                                }
                                """.formatted(filterSignature)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        OperationAuditLog log = waitForAuditLog("DOWNLOAD", path, filterSignature, before + 1);
        assertEquals(TENANT_ID, log.getTenantId());
        assertEquals(ADMIN_ID, log.getUserId());
        assertEquals("POST", log.getHttpMethod());
        assertEquals("ALERT_EXPORT", log.getBusinessType());
        assertEquals(filterSignature, log.getBusinessId());
        assertEquals(1, log.getSuccessFlag());
        assertEquals(path, log.getRequestPath());
        assertTrue(log.getErrorCode() == null || log.getErrorCode().isBlank());
    }

    @Test @Order(12) @DisplayName("POST /alerts/export-audit 非白名单筛选签名 -> 400 且不新增成功审计日志")
    void testExportAuditRejectsUnsafeFilterSignature() throws Exception {
        String path = "/api/alerts/export-audit";
        long before = countSuccessfulAlertExportAuditLogs(path);

        assertExportAuditBadRequest("""
                {
                  "filterSignature": "project=10001|rule=TEST_EXPORT_AUDIT|severity=HIGH",
                  "recordCount": 2
                }
                """);
        assertExportAuditBadRequest("""
                {
                  "filterSignature": "alert-export-tokenkeyword",
                  "recordCount": 2
                }
                """);
        assertExportAuditBadRequest("""
                {
                  "filterSignature": "{\"keyword\":\"budget\"}",
                  "recordCount": 2
                }
                """);
        assertExportAuditBadRequest("""
                {
                  "filterSignature": "alert-export-abcdefghijklmnopqrstuvwxyz123456",
                  "recordCount": 2
                }
                """);

        assertEquals(before, countSuccessfulAlertExportAuditLogs(path));
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

    @Test @Order(4) @DisplayName("POST /alerts/batch-evaluate requires dedicated alert:evaluate permission")
    void testBatchEvaluateRequiresDedicatedPermission() throws Exception {
        Method method = com.cgcpms.alert.controller.AlertController.class.getMethod("batchEvaluate");
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals("hasAuthority('alert:evaluate') or hasAnyRole('ADMIN','SUPER_ADMIN')", preAuthorize.value());

        mockMvc.perform(p("/alerts/batch-evaluate")
                        .cookie(memberCookie(NO_ACCESS_MEMBER_ID, "alert:edit")))
                .andExpect(status().isForbidden());

        mockMvc.perform(p("/alerts/batch-evaluate")
                        .cookie(memberCookie(NO_ACCESS_MEMBER_ID, "alert:evaluate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(4) @DisplayName("POST /alerts/escalate-overdue uses dedicated evaluate permission and persists escalation")
    void testEscalateOverdue() throws Exception {
        deleteAlertsByRuleType("TEST_ESCALATE_CTRL");
        AlertLog alert = newAlert("TEST_ESCALATE_CTRL", 10001L);
        alert.setResponseDueAt(LocalDateTime.now().minusMinutes(1));
        alert.setResolutionDueAt(LocalDateTime.now().plusHours(1));
        alert.setEscalationLevel(0);
        alertLogMapper.insert(alert);
        try {
            mockMvc.perform(p("/alerts/escalate-overdue")
                            .cookie(memberCookie(NO_ACCESS_MEMBER_ID, "alert:edit")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(p("/alerts/escalate-overdue").cookie(adminCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data.alertsEscalated").value(1));
            assertEquals(1, alertLogMapper.selectById(alert.getId()).getEscalationLevel());
        } finally {
            jdbcTemplate.update("delete from alert_notification_send_record where alert_id = ?", alert.getId());
            jdbcTemplate.update("delete from sys_notification where biz_id = ? and biz_type = 'ALERT_ESCALATION'", alert.getId());
            jdbcTemplate.update("delete from alert_lifecycle_event where alert_id = ?", alert.getId());
            deleteAlertsByRuleType("TEST_ESCALATE_CTRL");
        }
    }

    @Test @Order(4) @DisplayName("V146 keeps ordinary edit and batch evaluate permissions separate")
    void testAlertPermissionMigrationApplied() {
        assertEquals("alert:edit", jdbcTemplate.queryForObject(
                "SELECT perms FROM sys_menu WHERE id = 767", String.class));
        assertEquals("alert:evaluate", jdbcTemplate.queryForObject(
                "SELECT perms FROM sys_menu WHERE id = 768", String.class));
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

        mockMvc.perform(u("/alerts/" + alert.getId() + "/acknowledge").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"take\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        mockMvc.perform(u("/alerts/" + alert.getId() + "/status").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processStatus\":\"PROCESSED\",\"statusRemark\":\"done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.processStatus").value("PROCESSED"));

        mockMvc.perform(g("/alerts/" + alert.getId() + "/trace").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.alert.id").value(alert.getId().toString()))
                .andExpect(jsonPath("$.data.lifecycleEvents.length()").value(2))
                .andExpect(jsonPath("$.data.lifecycleEvents[0].payloadHash").isString());
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

    @Test @Order(7) @DisplayName("GET /alerts 跨页查询仍受项目筛选和成员权限约束")
    void testList_CrossPageFilteringStaysWithinAuthorizedProject() throws Exception {
        seedProjectIfAbsent(OTHER_PROJECT_ID, "ALERT-CTRL-OTHER");
        seedMemberIfAbsent(10001L, DIFFERENT_PROJECT_MEMBER_ID, "PM");
        deleteAlertsByRuleType("TEST_PAGE_SCOPE");
        try {
            AlertLog newestOwnAlert = newAlert("TEST_PAGE_SCOPE", 10001L);
            newestOwnAlert.setSeverity("HIGH");
            newestOwnAlert.setTriggeredAt(LocalDateTime.now().minusMinutes(1));
            alertLogMapper.insert(newestOwnAlert);

            AlertLog olderOwnAlert = newAlert("TEST_PAGE_SCOPE", 10001L);
            olderOwnAlert.setSeverity("HIGH");
            olderOwnAlert.setTriggeredAt(LocalDateTime.now().minusMinutes(2));
            alertLogMapper.insert(olderOwnAlert);

            AlertLog otherProjectAlert = newAlert("TEST_PAGE_SCOPE", OTHER_PROJECT_ID);
            otherProjectAlert.setSeverity("HIGH");
            otherProjectAlert.setTriggeredAt(LocalDateTime.now());
            alertLogMapper.insert(otherProjectAlert);

            mockMvc.perform(g("/alerts?pageNum=1&pageSize=1&projectId=10001&ruleType=TEST_PAGE_SCOPE&severity=HIGH")
                            .cookie(memberCookie(DIFFERENT_PROJECT_MEMBER_ID, "alert:view")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data.total").value(2))
                    .andExpect(jsonPath("$.data.records.length()").value(1))
                    .andExpect(jsonPath("$.data.records[0].id").value(newestOwnAlert.getId()))
                    .andExpect(jsonPath("$.data.records[0].projectId").value(10001));

            mockMvc.perform(g("/alerts?pageNum=2&pageSize=1&projectId=10001&ruleType=TEST_PAGE_SCOPE&severity=HIGH")
                            .cookie(memberCookie(DIFFERENT_PROJECT_MEMBER_ID, "alert:view")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data.total").value(2))
                    .andExpect(jsonPath("$.data.records.length()").value(1))
                    .andExpect(jsonPath("$.data.records[0].id").value(olderOwnAlert.getId()))
                    .andExpect(jsonPath("$.data.records[0].projectId").value(10001));
        } finally {
            deleteAlertsByRuleType("TEST_PAGE_SCOPE");
        }
    }

    @Test @Order(7) @DisplayName("GET /alerts 未授权域在不同分页参数下仍返回空结果")
    void testList_UnauthorizedDomainRemainsEmptyAcrossPagination() throws Exception {
        seedMemberIfAbsent(10001L, PURCHASE_MANAGER_ID, "PURCHASE_MANAGER");
        deleteAlertsByRuleType("TEST_DOMAIN_SCOPE");
        try {
            AlertLog purchaseAlert = newAlert("TEST_DOMAIN_SCOPE", 10001L);
            purchaseAlert.setAlertDomain("PURCHASE");
            purchaseAlert.setAlertCategory("PURCHASE_DELIVERY");
            purchaseAlert.setTriggeredAt(LocalDateTime.now().minusMinutes(1));
            alertLogMapper.insert(purchaseAlert);

            AlertLog contractAlert = newAlert("TEST_DOMAIN_SCOPE", 10001L);
            contractAlert.setAlertDomain("CONTRACT");
            contractAlert.setAlertCategory("CONTRACT_TERM");
            contractAlert.setTriggeredAt(LocalDateTime.now());
            alertLogMapper.insert(contractAlert);

            mockMvc.perform(g("/alerts?pageNum=2&pageSize=1&projectId=10001&ruleType=TEST_DOMAIN_SCOPE&alertDomain=CONTRACT")
                            .cookie(roleCookie(PURCHASE_MANAGER_ID, List.of("PURCHASE_MANAGER"), "alert:view")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data.total").value(0))
                    .andExpect(jsonPath("$.data.records.length()").value(0));
        } finally {
            deleteAlertsByRuleType("TEST_DOMAIN_SCOPE");
        }
    }

    @Test @Order(8) @DisplayName("GET /alerts/subscription purchase manager -> 返回受限域")
    void testGetSubscription() throws Exception {
        mockMvc.perform(g("/alerts/subscription")
                        .cookie(roleCookie(DIFFERENT_PROJECT_MEMBER_ID, List.of("PURCHASE_MANAGER"), "alert:view")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.effectiveSubscription.enabled").value(true))
                .andExpect(jsonPath("$.data.effectiveSubscription.channels.length()").value(1))
                .andExpect(jsonPath("$.data.effectiveSubscription.channels[0]").value("IN_APP"))
                .andExpect(jsonPath("$.data.effectiveSubscription.domains[0]").value("PURCHASE"))
                .andExpect(jsonPath("$.data.availableOptions.channels.length()").value(1))
                .andExpect(jsonPath("$.data.availableOptions.channels[0]").value("IN_APP"))
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
                .andExpect(jsonPath("$.data.rawUserOverrides.channels.length()").value(1))
                .andExpect(jsonPath("$.data.rawUserOverrides.channels[0]").value("IN_APP"))
                .andExpect(jsonPath("$.data.effectiveSubscription.channels.length()").value(1))
                .andExpect(jsonPath("$.data.effectiveSubscription.channels[0]").value("IN_APP"))
                .andExpect(jsonPath("$.data.availableOptions.channels.length()").value(1))
                .andExpect(jsonPath("$.data.availableOptions.channels[0]").value("IN_APP"))
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

    private void deleteAlertsByRuleType(String ruleType) {
        alertLogMapper.delete(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, TENANT_ID)
                .eq(AlertLog::getRuleType, ruleType));
    }

    private long countAuditLogs(String operationType, String requestPath) {
        return operationAuditLogMapper.selectCount(new LambdaQueryWrapper<OperationAuditLog>()
                .eq(OperationAuditLog::getTenantId, TENANT_ID)
                .eq(OperationAuditLog::getOperationType, operationType)
                .eq(OperationAuditLog::getRequestPath, requestPath));
    }

    private long countSuccessfulAlertExportAuditLogs(String requestPath) {
        return operationAuditLogMapper.selectCount(new LambdaQueryWrapper<OperationAuditLog>()
                .eq(OperationAuditLog::getTenantId, TENANT_ID)
                .eq(OperationAuditLog::getOperationType, "DOWNLOAD")
                .eq(OperationAuditLog::getBusinessType, "ALERT_EXPORT")
                .eq(OperationAuditLog::getRequestPath, requestPath)
                .eq(OperationAuditLog::getSuccessFlag, 1));
    }

    private void assertExportAuditBadRequest(String body) throws Exception {
        mockMvc.perform(p("/alerts/export-audit").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private OperationAuditLog waitForAuditLog(String operationType, String requestPath, String businessId, long expectedCount)
            throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            long count = countAuditLogs(operationType, requestPath);
            if (count >= expectedCount) {
                List<OperationAuditLog> logs = operationAuditLogMapper.selectList(new LambdaQueryWrapper<OperationAuditLog>()
                        .eq(OperationAuditLog::getTenantId, TENANT_ID)
                        .eq(OperationAuditLog::getOperationType, operationType)
                        .eq(OperationAuditLog::getRequestPath, requestPath)
                        .eq(OperationAuditLog::getBusinessId, businessId)
                        .orderByDesc(OperationAuditLog::getId)
                        .last("LIMIT 1"));
                if (!logs.isEmpty()) {
                    return logs.get(0);
                }
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("未等到审计日志落库: " + operationType + " " + requestPath + " businessId=" + businessId);
    }

    private MockHttpServletRequestBuilder g(String p) { return get("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder p(String p) { return post("/api" + p).contextPath("/api"); }
    private MockHttpServletRequestBuilder u(String p) { return put("/api" + p).contextPath("/api"); }
}
