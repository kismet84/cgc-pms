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
            SELECT DISTINCT u.id
            FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id
            JOIN sys_role_menu rm ON rm.role_id = r.id
            JOIN sys_menu m ON m.id = rm.menu_id
            WHERE u.tenant_id = #{tenantId}
              AND u.status = 'ENABLE'
              AND u.deleted_flag = 0
              AND r.status = 'ENABLE'
              AND r.deleted_flag = 0
              AND (r.tenant_id = 0 OR r.tenant_id = #{tenantId})
              AND m.deleted_flag = 0
              AND m.perms IN ('payment:record:writeback', 'cashbook:journal:maintain')
            ORDER BY u.id
            """)
    List<Long> selectCashJournalAlertRecipientIds(@Param("tenantId") Long tenantId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT u.id
            FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE u.tenant_id = #{tenantId}
              AND u.status = 'ENABLE'
              AND u.deleted_flag = 0
              AND r.status = 'ENABLE'
              AND r.deleted_flag = 0
              AND (r.tenant_id = 0 OR r.tenant_id = #{tenantId})
              AND UPPER(r.role_code) IN ('ADMIN', 'SUPER_ADMIN')
            ORDER BY u.id
            """)
    List<Long> selectTenantAdminRecipientIds(@Param("tenantId") Long tenantId);
}
