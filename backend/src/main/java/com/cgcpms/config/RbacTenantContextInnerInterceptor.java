package com.cgcpms.config;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.cgcpms.auth.context.UserContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * Fails closed when tenant-owned RBAC association mappers run without tenant context.
 */
final class RbacTenantContextInnerInterceptor implements InnerInterceptor {

    private static final String USER_ROLE_MAPPER = "com.cgcpms.system.mapper.SysUserRoleMapper.";
    private static final String ROLE_MENU_MAPPER = "com.cgcpms.system.mapper.SysRoleMenuMapper.";

    @Override
    public void beforeQuery(Executor executor, MappedStatement mappedStatement, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        requireTenantContext(mappedStatement.getId());
    }

    @Override
    public void beforeUpdate(Executor executor, MappedStatement mappedStatement, Object parameter) {
        requireTenantContext(mappedStatement.getId());
    }

    static void requireTenantContext(String mappedStatementId) {
        if (UserContext.getCurrentTenantId() == null && isAssociationMapper(mappedStatementId)) {
            throw new IllegalStateException("TENANT_CONTEXT_REQUIRED");
        }
    }

    private static boolean isAssociationMapper(String mappedStatementId) {
        return mappedStatementId != null
                && (mappedStatementId.startsWith(USER_ROLE_MAPPER)
                || mappedStatementId.startsWith(ROLE_MENU_MAPPER));
    }
}
