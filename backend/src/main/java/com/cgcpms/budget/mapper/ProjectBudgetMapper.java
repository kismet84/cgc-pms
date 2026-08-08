package com.cgcpms.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.common.util.DeletedCodeSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectBudgetMapper extends BaseMapper<ProjectBudget>, DeletedCodeSource {
    @Override
    @Select("SELECT budget_code FROM project_budget WHERE budget_code LIKE CONCAT(#{prefix}, '%') "
            + "AND tenant_id = #{tenantId} "
            + "ORDER BY CHAR_LENGTH(budget_code) DESC, budget_code DESC LIMIT 1")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM project_budget WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE")
    ProjectBudget selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM project_budget WHERE project_id = #{projectId} AND tenant_id = #{tenantId} "
            + "AND active_flag = 1 AND deleted_flag = 0 FOR UPDATE")
    ProjectBudget selectActiveByProjectForUpdate(@Param("projectId") Long projectId,
                                                  @Param("tenantId") Long tenantId);
}
