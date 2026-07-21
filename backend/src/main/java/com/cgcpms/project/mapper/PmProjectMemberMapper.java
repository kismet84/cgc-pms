package com.cgcpms.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.project.entity.PmProjectMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PmProjectMemberMapper extends BaseMapper<PmProjectMember> {

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
