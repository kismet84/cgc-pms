package com.cgcpms.project.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.vo.PmProjectMemberRoleRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PmProjectMemberMapper extends BaseMapper<PmProjectMember> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT candidate.id AS user_id,
                   candidate.username,
                   candidate.real_name,
                   r.role_code
            FROM (
                SELECT u.id, u.username, u.real_name
                FROM sys_user u
                WHERE u.tenant_id = #{tenantId}
                  AND u.status = 'ENABLE'
                  AND u.deleted_flag = 0
                  AND (
                      NOT EXISTS (
                          SELECT 1
                          FROM pm_project_member pm
                          WHERE pm.tenant_id = #{tenantId}
                            AND pm.project_id = #{projectId}
                            AND pm.user_id = u.id
                            AND pm.deleted_flag = 0
                      )
                      OR u.id = #{includeUserId}
                  )
                  AND (
                      #{keyword} = ''
                      OR LOWER(u.username) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                      OR LOWER(COALESCE(u.real_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                  )
                  AND EXISTS (
                      SELECT 1
                      FROM sys_user_role eligible_ur
                      JOIN sys_role eligible_r
                        ON eligible_r.tenant_id = eligible_ur.tenant_id
                       AND eligible_r.id = eligible_ur.role_id
                      WHERE eligible_ur.tenant_id = #{tenantId}
                        AND eligible_ur.user_id = u.id
                        AND eligible_r.status = 'ENABLE'
                        AND eligible_r.deleted_flag = 0
                        AND eligible_r.data_scope = 'PROJECT_MEMBER'
                        AND eligible_r.role_code IN (
                            'PROJECT_MANAGER', 'PROJECT_ACCOUNTANT', 'TECHNICAL_LEAD', 'SAFETY_LEAD',
                            'CONSTRUCTION_LEAD', 'PROCUREMENT_LEAD', 'EMPLOYEE'
                        )
                  )
                ORDER BY CASE WHEN u.id = #{includeUserId} THEN 0 ELSE 1 END, u.username, u.id
                LIMIT 101
            ) candidate
            JOIN sys_user_role ur
              ON ur.tenant_id = #{tenantId}
             AND ur.user_id = candidate.id
            JOIN sys_role r
              ON r.tenant_id = ur.tenant_id
             AND r.id = ur.role_id
            WHERE r.tenant_id = #{tenantId}
              AND r.status = 'ENABLE'
              AND r.deleted_flag = 0
              AND r.data_scope = 'PROJECT_MEMBER'
              AND r.role_code IN (
                  'PROJECT_MANAGER', 'PROJECT_ACCOUNTANT', 'TECHNICAL_LEAD', 'SAFETY_LEAD',
                  'CONSTRUCTION_LEAD', 'PROCUREMENT_LEAD', 'EMPLOYEE'
              )
            ORDER BY CASE WHEN candidate.id = #{includeUserId} THEN 0 ELSE 1 END,
                     candidate.username, candidate.id, r.role_code
            """)
    List<PmProjectMemberRoleRowVO> selectEnabledProjectRoleRows(
            @Param("tenantId") Long tenantId,
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("includeUserId") Long includeUserId);

    @Select("""
            SELECT id
            FROM pm_project_member
            WHERE tenant_id = #{tenantId}
              AND project_id = #{projectId}
              AND user_id = #{userId}
            """)
    Long selectIdIncludingDeleted(@Param("tenantId") Long tenantId,
                                  @Param("projectId") Long projectId,
                                  @Param("userId") Long userId);

    @Update("""
            UPDATE pm_project_member
            SET role_code = #{member.roleCode},
                position_name = #{member.positionName},
                start_date = #{member.startDate},
                end_date = #{member.endDate},
                status = #{member.status},
                updated_by = #{updatedBy},
                updated_at = CURRENT_TIMESTAMP,
                deleted_flag = 0,
                remark = #{member.remark}
            WHERE id = #{id}
              AND tenant_id = #{tenantId}
              AND project_id = #{projectId}
              AND user_id = #{member.userId}
              AND deleted_flag = 1
            """)
    int restoreDeleted(@Param("id") Long id,
                       @Param("tenantId") Long tenantId,
                       @Param("projectId") Long projectId,
                       @Param("member") PmProjectMember member,
                       @Param("updatedBy") Long updatedBy);
}
