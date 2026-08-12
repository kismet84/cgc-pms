package com.cgcpms.config;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.cgcpms.common.TestUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MybatisPlusTenantTableConfigTest {

    @AfterEach
    void clearContext() {
        TestUserContext.clear();
    }

    @Test
    void tenantJoinTablesAreProtectedAndSharedWorkflowTemplatesRemainIgnored() {
        var tenantInterceptor = new MybatisPlusConfig().mybatisPlusInterceptor().getInterceptors().stream()
                .filter(TenantLineInnerInterceptor.class::isInstance)
                .map(TenantLineInnerInterceptor.class::cast)
                .findFirst()
                .orElseThrow();
        var handler = tenantInterceptor.getTenantLineHandler();

        assertFalse(handler.ignoreTable("sys_user_role"));
        assertFalse(handler.ignoreTable("sys_role_menu"));
        assertTrue(handler.ignoreTable("wf_template"));
        assertTrue(handler.ignoreTable("wf_template_node"));
    }

    @Test
    void tenantExpressionUsesCurrentContextAndKeepsLegacyTenantZeroFallback() {
        var handler = new MybatisPlusConfig().mybatisPlusInterceptor().getInterceptors().stream()
                .filter(TenantLineInnerInterceptor.class::isInstance)
                .map(TenantLineInnerInterceptor.class::cast)
                .findFirst()
                .orElseThrow()
                .getTenantLineHandler();

        assertEquals("0", handler.getTenantId().toString());

        TestUserContext.setAdmin(0L, 1L);
        assertEquals("0", handler.getTenantId().toString());
        TestUserContext.setAdmin(1001L, 2L);
        assertEquals("1001", handler.getTenantId().toString());
    }

    @Test
    void tenantJoinMapperGuardRejectsMissingContextWithoutBlockingExplicitAuthMapper() {
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> RbacTenantContextInnerInterceptor.requireTenantContext(
                        "com.cgcpms.system.mapper.SysUserRoleMapper.selectById"));
        assertEquals("TENANT_CONTEXT_REQUIRED", missing.getMessage());

        RbacTenantContextInnerInterceptor.requireTenantContext(
                "com.cgcpms.system.mapper.SysUserMapper.selectEnabledRoleCodesByTenantAndUserId");

        TestUserContext.setAdmin(1001L, 2L);
        RbacTenantContextInnerInterceptor.requireTenantContext(
                "com.cgcpms.system.mapper.SysRoleMenuMapper.deleteById");
    }
}
