package com.cgcpms.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.cgcpms.auth.context.UserContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus configuration.
 * Registers pagination, optimistic-lock, block-attack and tenant-line interceptors.
 * Tenant-line interceptor auto-injects tenant_id into every query.
 * Use {@code @InterceptorIgnore(tenantLine = "true")} on mapper methods to bypass.
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // Pagination interceptor for MySQL
        PaginationInnerInterceptor paginationInnerInterceptor =
                new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInnerInterceptor.setMaxLimit(1000L);
        paginationInnerInterceptor.setOverflow(false);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);

        // Optimistic lock interceptor
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // Block full-table update / delete operations
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        // Association mappers require an authenticated or explicitly scoped tenant.
        interceptor.addInnerInterceptor(new RbacTenantContextInnerInterceptor());

        // Tenant isolation: auto-inject tenant_id into every query
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = UserContext.getCurrentTenantId();
                // Keep tenant-0 fallback for legacy startup/scheduled discovery paths. RBAC
                // association mappers apply the stricter missing-context guard above.
                return new LongValue(tenantId == null ? 0L : tenantId);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // Workflow templates are shared across tenants (tenant_id=0 fallback).
                return "wf_template".equals(tableName)
                        || "wf_template_node".equals(tableName);
            }
        }));

        return interceptor;
    }
}
