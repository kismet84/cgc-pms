package com.cgcpms.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.cgcpms.auth.context.UserContext;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
                if (tenantId == null) {
                    // Legacy tenant-0 fallback for startup and fixture seeding, kept because
                    // no HTTP path reaches a mapper without context (the JWT filter fails closed
                    // outside /auth/login, /auth/refresh and /actuator/health/**) and RBAC
                    // association mappers apply the stricter guard above.
                    // Background work must NOT rely on it: a scheduler that reads through the
                    // fallback silently sees tenant 0 only. Bind the tenant explicitly with
                    // UserContext.runAsTenant and discover tenants through a mapper method
                    // annotated @InterceptorIgnore(tenantLine = "true").
                    log.warn("Tenant context missing; falling back to tenant 0. "
                            + "Background work must bind a tenant with UserContext.runAsTenant.");
                    return new LongValue(0L);
                }
                return new LongValue(tenantId);
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
