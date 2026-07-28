package com.cgcpms.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.system.entity.SysUserRole;
import com.cgcpms.system.vo.RoleUserCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Select("""
            SELECT ur.role_id AS role_id, COUNT(DISTINCT ur.user_id) AS user_count
            FROM sys_user_role ur
            JOIN sys_user u
              ON u.tenant_id = ur.tenant_id
             AND u.id = ur.user_id
             AND u.deleted_flag = 0
            WHERE ur.tenant_id = #{tenantId}
            GROUP BY ur.role_id
            """)
    List<RoleUserCountVO> countUsersByRole(@Param("tenantId") Long tenantId);

    @Select("""
            SELECT COUNT(DISTINCT ur.user_id)
            FROM sys_user_role ur
            JOIN sys_user u
              ON u.tenant_id = ur.tenant_id
             AND u.id = ur.user_id
             AND u.deleted_flag = 0
            WHERE ur.tenant_id = #{tenantId}
              AND ur.role_id = #{roleId}
            """)
    Long countUsersForRole(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);
}
