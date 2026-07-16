package com.cgcpms.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.budget.entity.ProjectBudget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectBudgetMapper extends BaseMapper<ProjectBudget> {
    @Select("SELECT * FROM project_budget WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE")
    ProjectBudget selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
