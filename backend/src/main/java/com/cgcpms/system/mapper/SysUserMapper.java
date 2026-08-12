package com.cgcpms.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.system.entity.SysUser;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id, tenant_id, username, password, real_name, phone, email, org_id, avatar, status, is_admin
            FROM sys_user
            WHERE tenant_id = #{tenantId}
              AND username = #{username}
              AND deleted_flag = 0
            """)
    SysUser selectByTenantAndUsername(@Param("tenantId") Long tenantId,
                                      @Param("username") String username);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id, tenant_id, username, password, real_name, phone, email, org_id, avatar, status, is_admin
            FROM sys_user
            WHERE tenant_id = #{tenantId}
              AND id = #{userId}
              AND deleted_flag = 0
            """)
    SysUser selectByTenantAndId(@Param("tenantId") Long tenantId,
                                @Param("userId") Long userId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id, tenant_id, password, status
            FROM sys_user
            WHERE id = #{userId}
              AND tenant_id = #{tenantId}
              AND deleted_flag = 0
            """)
    SysUser selectCredentialByTenantAndId(@Param("tenantId") Long tenantId,
                                          @Param("userId") Long userId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT r.role_code
            FROM sys_user_role ur
            JOIN sys_role r
              ON r.tenant_id = ur.tenant_id
             AND r.id = ur.role_id
             AND r.deleted_flag = 0
             AND r.status = 'ENABLE'
            WHERE ur.tenant_id = #{tenantId}
              AND ur.user_id = #{userId}
            ORDER BY r.role_code
            """)
    List<String> selectEnabledRoleCodesByTenantAndUserId(@Param("tenantId") Long tenantId,
                                                          @Param("userId") Long userId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT m.perms
            FROM sys_menu m
            WHERE m.tenant_id = #{tenantId}
              AND m.deleted_flag = 0
              AND m.status = 'ENABLE'
              AND m.perms IS NOT NULL
              AND m.perms <> ''
              AND (
                  EXISTS (
                      SELECT 1
                      FROM sys_user_role ur
                      JOIN sys_role r
                        ON r.tenant_id = ur.tenant_id
                       AND r.id = ur.role_id
                       AND r.deleted_flag = 0
                       AND r.status = 'ENABLE'
                      WHERE ur.tenant_id = #{tenantId}
                        AND ur.user_id = #{userId}
                        AND UPPER(r.role_code) IN ('ADMIN', 'SUPER_ADMIN')
                  )
                  OR EXISTS (
                      SELECT 1
                      FROM sys_user_role ur
                      JOIN sys_role r
                        ON r.tenant_id = ur.tenant_id
                       AND r.id = ur.role_id
                       AND r.deleted_flag = 0
                       AND r.status = 'ENABLE'
                      JOIN sys_role_menu rm
                        ON rm.tenant_id = r.tenant_id
                       AND rm.role_id = r.id
                       AND rm.menu_id = m.id
                      WHERE ur.tenant_id = #{tenantId}
                        AND ur.user_id = #{userId}
                  )
              )
            ORDER BY m.perms
            """)
    List<String> selectEnabledPermissionCodesByTenantAndUserId(@Param("tenantId") Long tenantId,
                                                                @Param("userId") Long userId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT u.id
            FROM sys_user u
            JOIN sys_user_role ur ON ur.tenant_id = u.tenant_id AND ur.user_id = u.id
            JOIN sys_role r ON r.tenant_id = ur.tenant_id AND r.id = ur.role_id
            JOIN sys_role_menu rm ON rm.tenant_id = r.tenant_id AND rm.role_id = r.id
            JOIN sys_menu m ON m.tenant_id = rm.tenant_id AND m.id = rm.menu_id
            WHERE u.tenant_id = #{tenantId}
              AND u.status = 'ENABLE'
              AND u.deleted_flag = 0
              AND r.status = 'ENABLE'
              AND r.deleted_flag = 0
              AND r.tenant_id = #{tenantId}
              AND m.deleted_flag = 0
              AND m.perms IN ('payment:record:writeback', 'cashbook:journal:maintain')
            ORDER BY u.id
            """)
    List<Long> selectCashJournalAlertRecipientIds(@Param("tenantId") Long tenantId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT u.id
            FROM sys_user u
            JOIN sys_user_role ur
              ON ur.tenant_id = u.tenant_id
             AND ur.user_id = u.id
            JOIN sys_role r
              ON r.tenant_id = ur.tenant_id
             AND r.id = ur.role_id
            WHERE u.tenant_id = #{tenantId}
              AND u.status = 'ENABLE'
              AND u.deleted_flag = 0
              AND r.status = 'ENABLE'
              AND r.deleted_flag = 0
              AND r.tenant_id = #{tenantId}
              AND UPPER(r.role_code) IN ('ADMIN', 'SUPER_ADMIN')
            ORDER BY u.id
            """)
    List<Long> selectTenantAdminRecipientIds(@Param("tenantId") Long tenantId);
}
