package com.cgcpms.payment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.audit.entity.OperationAuditLog;
import com.cgcpms.audit.mapper.OperationAuditLogMapper;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.main.allow-circular-references=true",
        "jwt.secret=pay-application-controller-test-secret-key-at-least-sixty-four-characters-long"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("PayApplicationController — 基础 CRUD 端点测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PayApplicationControllerTest {

    private static final Pattern ID_PATTERN = Pattern.compile("\"data\":\"?(\\d+)\"?");
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;
    private static final long PARTNER_ID = 20002L;
    private static final String CREATE_PATH = "/api/pay-applications";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private OperationAuditLogMapper operationAuditLogMapper;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                ADMIN_ID, ADMIN_USERNAME, TENANT_ID,
                List.of("ADMIN"),
                List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    @Test
    @Order(1)
    @DisplayName("GET /pay-applications → 200，返回分页列表")
    void testListApplications() throws Exception {
        mockMvc.perform(getWithApi("/pay-applications")
                        .cookie(adminCookie())
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @Order(2)
    @DisplayName("GET /pay-applications 无 JWT → 401")
    void testUnauthorized() throws Exception {
        mockMvc.perform(getWithApi("/pay-applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    @DisplayName("关键写操作带审计注解")
    void testAuditedOperationsPresent() throws Exception {
        assertAudit("create", new Class<?>[] {com.cgcpms.payment.entity.PayApplication.class},
                "CREATE", "PAYMENT", "#app.id");
        assertAudit("update", new Class<?>[] {Long.class, com.cgcpms.payment.entity.PayApplication.class},
                "UPDATE", "PAYMENT", "#id");
        assertAudit("submitForApproval", new Class<?>[] {Long.class},
                "SUBMIT", "PAYMENT", "#id");
    }

    @Test
    @Order(4)
    @DisplayName("POST /pay-applications create 请求触发审计落库")
    void testCreatePersistsRequestAuditLog() throws Exception {
        long before = countAuditLogs("CREATE", CREATE_PATH);

        String response = mockMvc.perform(postWithApi("/pay-applications")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody("审计-创建")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long appId = extractId(response);
        OperationAuditLog log = waitForAuditLog("CREATE", CREATE_PATH, appId.toString(), before + 1);

        assertEquals("POST", log.getHttpMethod());
        assertEquals(TENANT_ID, log.getTenantId());
        assertEquals(ADMIN_ID, log.getUserId());
        assertEquals("PAYMENT", log.getBusinessType());
        assertEquals(appId.toString(), log.getBusinessId());
        assertEquals(1, log.getSuccessFlag());
        assertEquals(CREATE_PATH, log.getRequestPath());
    }

    @Test
    @Order(5)
    @DisplayName("PUT /pay-applications/{id} update 请求触发审计落库")
    void testUpdatePersistsRequestAuditLog() throws Exception {
        Long appId = createDraftApplication("审计-更新-前置");
        String path = "/api/pay-applications/" + appId;
        long before = countAuditLogs("UPDATE", path);

        mockMvc.perform(putWithApi("/pay-applications/" + appId)
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateBody("审计-更新")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        OperationAuditLog log = waitForAuditLog("UPDATE", path, appId.toString(), before + 1);

        assertEquals("PUT", log.getHttpMethod());
        assertEquals(TENANT_ID, log.getTenantId());
        assertEquals(ADMIN_ID, log.getUserId());
        assertEquals("PAYMENT", log.getBusinessType());
        assertEquals(appId.toString(), log.getBusinessId());
        assertEquals(1, log.getSuccessFlag());
        assertEquals(path, log.getRequestPath());
    }

    @Test
    @Order(6)
    @DisplayName("POST /pay-applications/{id}/submit 失败请求仍触发审计落库")
    void testSubmitPersistsRequestAuditLogOnFailure() throws Exception {
        Long appId = createDraftApplication("审计-提交-前置");
        String path = "/api/pay-applications/" + appId + "/submit";
        long before = countAuditLogs("SUBMIT", path);

        mockMvc.perform(postWithApi("/pay-applications/" + appId + "/submit")
                        .cookie(adminCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());

        OperationAuditLog log = waitForAuditLog("SUBMIT", path, appId.toString(), before + 1);

        assertEquals("POST", log.getHttpMethod());
        assertEquals(TENANT_ID, log.getTenantId());
        assertEquals(ADMIN_ID, log.getUserId());
        assertEquals("PAYMENT", log.getBusinessType());
        assertEquals(appId.toString(), log.getBusinessId());
        assertEquals(0, log.getSuccessFlag());
        assertFalse(log.getErrorCode() == null || log.getErrorCode().isBlank());
        assertEquals(path, log.getRequestPath());
    }

    @Test
    @Order(7)
    @DisplayName("GET /pay-applications/source-options 返回当前分包付款上下文候选")
    void testListSourceOptions() throws Exception {
        mockMvc.perform(getWithApi("/pay-applications/source-options")
                        .cookie(adminCookie())
                        .param("projectId", String.valueOf(PROJECT_ID))
                        .param("contractId", String.valueOf(CONTRACT_ID))
                        .param("partnerId", String.valueOf(PARTNER_ID))
                        .param("payType", "PROGRESS")
                        .param("expenseCategory", "SUBCONTRACT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    private void assertAudit(String methodName, Class<?>[] parameterTypes,
                             String type, String businessType, String businessIdExpression) throws Exception {
        Method method = PayApplicationController.class.getMethod(methodName, parameterTypes);
        AuditedOperation audited = method.getAnnotation(AuditedOperation.class);
        assertNotNull(audited, methodName + " 应声明 @AuditedOperation");
        assertEquals(type, audited.type());
        assertEquals(businessType, audited.businessType());
        assertEquals(businessIdExpression, audited.businessIdExpression());
    }

    private MockHttpServletRequestBuilder getWithApi(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder postWithApi(String pathWithinContext) {
        return post("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder putWithApi(String pathWithinContext) {
        return put("/api" + pathWithinContext).contextPath("/api");
    }

    private String validCreateBody(String reason) {
        return """
                {
                  "projectId": %d,
                  "contractId": %d,
                  "partnerId": %d,
                  "applyAmount": 1000.00,
                  "payType": "MATERIAL",
                  "applyReason": "%s"
                }
                """.formatted(PROJECT_ID, CONTRACT_ID, PARTNER_ID, reason);
    }

    private String validUpdateBody(String reason) {
        return """
                {
                  "projectId": %d,
                  "contractId": %d,
                  "partnerId": %d,
                  "applyCode": "CLIENT-UPDATE",
                  "applyAmount": 1200.00,
                  "payType": "MATERIAL",
                  "applyReason": "%s"
                }
                """.formatted(PROJECT_ID, CONTRACT_ID, PARTNER_ID, reason);
    }

    private Long createDraftApplication(String reason) throws Exception {
        String response = mockMvc.perform(postWithApi("/pay-applications")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody(reason)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractId(response);
    }

    private Long extractId(String response) {
        Matcher matcher = ID_PATTERN.matcher(response);
        assertNotNull(response);
        assertTrue(matcher.find(), "响应中未找到 data id: " + response);
        return Long.parseLong(matcher.group(1));
    }

    private long countAuditLogs(String operationType, String requestPath) {
        return operationAuditLogMapper.selectCount(new LambdaQueryWrapper<OperationAuditLog>()
                .eq(OperationAuditLog::getTenantId, TENANT_ID)
                .eq(OperationAuditLog::getOperationType, operationType)
                .eq(OperationAuditLog::getRequestPath, requestPath));
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
}
