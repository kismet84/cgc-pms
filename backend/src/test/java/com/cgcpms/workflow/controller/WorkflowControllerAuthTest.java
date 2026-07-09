package com.cgcpms.workflow.controller;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.dto.WorkflowActionRequest;
import com.cgcpms.workflow.dto.WorkflowAddSignRequest;
import com.cgcpms.workflow.dto.WorkflowSubmitRequest;
import com.cgcpms.workflow.dto.WorkflowTransferRequest;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.workflow.service.WorkflowQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for WorkflowController authorization alignment.
 *
 * Verifies:
 * - ADMIN role bypasses write endpoint checks (including submit permission)
 * - SUPER_ADMIN role bypasses write endpoint checks (including submit permission)
 * - Non-admin users without correct permission are rejected
 * - Users with the exact required permission pass
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowController 授权对齐")
class WorkflowControllerAuthTest {

    @Mock
    private WorkflowEngine workflowEngine;

    @Mock
    private WorkflowQueryService workflowQueryService;

    private WorkflowController controller;
    private WorkflowEngine permissionEngine;

    @BeforeEach
    void setUp() {
        controller = new WorkflowController(workflowEngine, workflowQueryService);
        permissionEngine = new WorkflowEngine(null, null, null, null, null, null, null, null);
        // clear any lingering context from other tests
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ---- checkSubmitPermission (via submit endpoint) ----

    @Nested
    @DisplayName("checkSubmitPermission — 角色旁路")
    class SubmitPermissionBypass {

        private WorkflowSubmitRequest validRequest() {
            WorkflowSubmitRequest req = new WorkflowSubmitRequest();
            req.setBusinessType(WorkflowBusinessTypes.CONTRACT_APPROVAL);
            req.setBusinessId(1L);
            req.setTitle("test");
            return req;
        }

        @Test
        @DisplayName("ADMIN 角色绕过权限检查通过")
        void adminRoleBypassesSubmitPermission() {
            setAuthentication("ROLE_ADMIN");

            // ADMIN should pass checkSubmitPermission without needing contract:submit
            // Submit will fail deeper (mocked engine) but the permission check should pass
            // We verify by calling submit - if it gets past permission check, it's correct
            // (the engine mock will return null, but we only care that AccessDeniedException
            //  is NOT thrown for the permission check step)
            // Since the engine is mocked and submit creates a real WfInstance, we test
            // checkSubmitPermission indirectly via a helper that wraps it.

            // Directly test the permission check logic:
            // The controller calls checkSubmitPermission which reads SecurityContextHolder.
            // With ROLE_ADMIN it should pass.
            assertDoesNotThrow(() -> invokeCheckSubmitPermission(WorkflowBusinessTypes.CONTRACT_APPROVAL),
                    "ADMIN 应绕过 contract:submit 权限检查");
        }

        @Test
        @DisplayName("SUPER_ADMIN 角色绕过权限检查通过")
        void superAdminRoleBypassesSubmitPermission() {
            setAuthentication("ROLE_SUPER_ADMIN");

            assertDoesNotThrow(() -> invokeCheckSubmitPermission(WorkflowBusinessTypes.CONTRACT_APPROVAL),
                    "SUPER_ADMIN 应绕过 contract:submit 权限检查");
        }

        @Test
        @DisplayName("无权限普通用户被拒绝")
        void ordinaryUserWithoutPermissionIsRejected() {
            setAuthentication("ROLE_USER"); // no matching permission

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> invokeCheckSubmitPermission(WorkflowBusinessTypes.CONTRACT_APPROVAL),
                    "缺少 contract:submit 权限的普通用户应被拒绝");
            assertEquals("WORKFLOW_PERMISSION_DENIED", ex.getCode(),
                    "错误码应为 WORKFLOW_PERMISSION_DENIED");
        }

        @Test
        @DisplayName("具有精确权限的用户通过")
        void userWithExactPermissionPasses() {
            setAuthentication("contract:submit");

            assertDoesNotThrow(() -> invokeCheckSubmitPermission(WorkflowBusinessTypes.CONTRACT_APPROVAL),
                    "具有 contract:submit 权限的用户应通过");
        }

        @Test
        @DisplayName("CONTRACT_REVENUE 使用生产权限映射允许 revenue:submit")
        void contractRevenueUsesProductionPermissionMapping() {
            assertEquals("revenue:submit",
                    permissionEngine.getRequiredPermission(WorkflowBusinessTypes.CONTRACT_REVENUE),
                    "CONTRACT_REVENUE 应映射到 revenue:submit");

            setAuthentication("revenue:submit");

            assertDoesNotThrow(() -> invokeCheckSubmitPermission(WorkflowBusinessTypes.CONTRACT_REVENUE),
                    "具有 revenue:submit 权限的用户应通过 CONTRACT_REVENUE 提交校验");
        }

        @Test
        @DisplayName("CONTRACT_REVENUE 错误权限被拒绝")
        void contractRevenueWrongPermissionIsRejected() {
            setAuthentication("contract:submit");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> invokeCheckSubmitPermission(WorkflowBusinessTypes.CONTRACT_REVENUE),
                    "错误权限不应通过 CONTRACT_REVENUE 提交校验");
            assertEquals("WORKFLOW_PERMISSION_DENIED", ex.getCode(),
                    "错误码应为 WORKFLOW_PERMISSION_DENIED");
        }

        @Test
        @DisplayName("ADMIN 绕过所有业务类型的提交权限")
        void adminBypassesAllBusinessTypes() {
            setAuthentication("ROLE_ADMIN");

            String[] types = {
                    WorkflowBusinessTypes.CONTRACT_APPROVAL,
                    WorkflowBusinessTypes.PURCHASE_ORDER,
                    WorkflowBusinessTypes.PURCHASE_REQUEST,
                    WorkflowBusinessTypes.MATERIAL_RECEIPT,
                    WorkflowBusinessTypes.SUB_MEASURE,
                    WorkflowBusinessTypes.PAY_REQUEST,
                    WorkflowBusinessTypes.VAR_ORDER,
                    WorkflowBusinessTypes.CT_CHANGE,
                    WorkflowBusinessTypes.SETTLEMENT,
                    WorkflowBusinessTypes.COST_TARGET,
                    WorkflowBusinessTypes.CONTRACT_REVENUE
            };

            for (String type : types) {
                assertDoesNotThrow(() -> invokeCheckSubmitPermission(type),
                        "ADMIN 应绕过 " + type + " 权限检查");
            }
        }

        @Test
        @DisplayName("SUPER_ADMIN 绕过所有业务类型的提交权限")
        void superAdminBypassesAllBusinessTypes() {
            setAuthentication("ROLE_SUPER_ADMIN");

            String[] types = {
                    WorkflowBusinessTypes.CONTRACT_APPROVAL,
                    WorkflowBusinessTypes.PURCHASE_ORDER,
                    WorkflowBusinessTypes.PURCHASE_REQUEST,
                    WorkflowBusinessTypes.MATERIAL_RECEIPT,
                    WorkflowBusinessTypes.SUB_MEASURE,
                    WorkflowBusinessTypes.PAY_REQUEST,
                    WorkflowBusinessTypes.VAR_ORDER,
                    WorkflowBusinessTypes.CT_CHANGE,
                    WorkflowBusinessTypes.SETTLEMENT,
                    WorkflowBusinessTypes.COST_TARGET,
                    WorkflowBusinessTypes.CONTRACT_REVENUE
            };

            for (String type : types) {
                assertDoesNotThrow(() -> invokeCheckSubmitPermission(type),
                        "SUPER_ADMIN 应绕过 " + type + " 权限检查");
            }
        }

        @Test
        @DisplayName("未认证用户被拒绝")
        void unauthenticatedUserIsRejected() {
            SecurityContextHolder.clearContext();

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> invokeCheckSubmitPermission(WorkflowBusinessTypes.CONTRACT_APPROVAL),
                    "未认证用户应被拒绝");
            assertEquals("UNAUTHORIZED", ex.getCode(), "错误码应为 UNAUTHORIZED");
        }

        @Test
        @DisplayName("不支持的业务类型抛出商务异常")
        void unsupportedBusinessTypeThrowsIllegalArgument() {
            setAuthentication("ROLE_ADMIN");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> invokeCheckSubmitPermission("UNKNOWN_TYPE"),
                    "不支持的业务类型应抛出 BusinessException");
            assertEquals("UNSUPPORTED_BUSINESS_TYPE", ex.getCode(),
                    "错误码应为 UNSUPPORTED_BUSINESS_TYPE");
        }

        @Test
        @DisplayName("TECH_ITEM 未支持时抛出商务异常")
        void techItemUnsupportedThrowsBusinessException() {
            setAuthentication("ROLE_ADMIN");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> invokeCheckSubmitPermission(WorkflowBusinessTypes.TECH_ITEM),
                    "TECH_ITEM 当前未支持，应抛出 BusinessException");
            assertEquals("UNSUPPORTED_BUSINESS_TYPE", ex.getCode(),
                    "错误码应为 UNSUPPORTED_BUSINESS_TYPE");
        }
    }

    // ---- Spring Security @PreAuthorize pattern verification ----

    @Nested
    @DisplayName("@PreAuthorize 注解模式一致")
    class PreAuthorizePatternConsistency {

        /**
         * 验证规则：所有 write 端点的 @PreAuthorize 注解必须包含
         * or hasAnyRole('ADMIN','SUPER_ADMIN') 后备。
         *
         * 此测试通过反射检查注解内容，确保代码审查后不会回退。
         */
        @Test
        @DisplayName("approve 端点允许 ADMIN/SUPER_ADMIN 后备")
        void approveEndpointAllowsAdminFallback() throws Exception {
            var method = WorkflowController.class.getMethod("approve", Long.class, WorkflowActionRequest.class);
            var annotation = method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
            String value = annotation.value();
            assert value.contains("hasAnyRole('ADMIN','SUPER_ADMIN')")
                    : "approve 缺少 ADMIN/SUPER_ADMIN 后备: " + value;
        }

        @Test
        @DisplayName("reject 端点允许 ADMIN/SUPER_ADMIN 后备")
        void rejectEndpointAllowsAdminFallback() throws Exception {
            var method = WorkflowController.class.getMethod("reject", Long.class, WorkflowActionRequest.class);
            var annotation = method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
            String value = annotation.value();
            assert value.contains("hasAnyRole('ADMIN','SUPER_ADMIN')")
                    : "reject 缺少 ADMIN/SUPER_ADMIN 后备: " + value;
        }

        @Test
        @DisplayName("withdraw 端点允许 ADMIN/SUPER_ADMIN 后备")
        void withdrawEndpointAllowsAdminFallback() throws Exception {
            var method = WorkflowController.class.getMethod("withdraw", Long.class);
            var annotation = method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
            String value = annotation.value();
            assert value.contains("hasAnyRole('ADMIN','SUPER_ADMIN')")
                    : "withdraw 缺少 ADMIN/SUPER_ADMIN 后备: " + value;
        }

        @Test
        @DisplayName("resubmit 端点允许 ADMIN/SUPER_ADMIN 后备")
        void resubmitEndpointAllowsAdminFallback() throws Exception {
            var method = WorkflowController.class.getMethod("resubmit", Long.class);
            var annotation = method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
            String value = annotation.value();
            assert value.contains("hasAnyRole('ADMIN','SUPER_ADMIN')")
                    : "resubmit 缺少 ADMIN/SUPER_ADMIN 后备: " + value;
        }

        @Test
        @DisplayName("transfer 端点允许 ADMIN/SUPER_ADMIN 后备")
        void transferEndpointAllowsAdminFallback() throws Exception {
            var method = WorkflowController.class.getMethod("transfer", Long.class, WorkflowTransferRequest.class);
            var annotation = method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
            String value = annotation.value();
            assert value.contains("hasAnyRole('ADMIN','SUPER_ADMIN')")
                    : "transfer 缺少 ADMIN/SUPER_ADMIN 后备: " + value;
        }

        @Test
        @DisplayName("addSign 端点允许 ADMIN/SUPER_ADMIN 后备")
        void addSignEndpointAllowsAdminFallback() throws Exception {
            var method = WorkflowController.class.getMethod("addSign", Long.class, WorkflowAddSignRequest.class);
            var annotation = method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
            String value = annotation.value();
            assert value.contains("hasAnyRole('ADMIN','SUPER_ADMIN')")
                    : "addSign 缺少 ADMIN/SUPER_ADMIN 后备: " + value;
        }
    }

    // ---- helpers ----

    private void setAuthentication(String... authorities) {
        List<SimpleGrantedAuthority> authList = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("testUser", null, authList));
    }

    private void invokeCheckSubmitPermission(String businessType) {
        permissionEngine.checkSubmitPermission(businessType);
    }
}
