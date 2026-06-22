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

        // Tenant isolation: auto-inject tenant_id into every query
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = UserContext.getCurrentTenantId();
                if (tenantId == null) {
                    return new LongValue(0);
                }
                return new LongValue(tenantId);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // Tables without tenant_id — tenant isolation is enforced
                // through their related parent entities (e.g. sys_user, sys_role).
                return "sys_user_role".equals(tableName)
                        || "sys_role_menu".equals(tableName);
            }
        }));

        return interceptor;
    }
}
