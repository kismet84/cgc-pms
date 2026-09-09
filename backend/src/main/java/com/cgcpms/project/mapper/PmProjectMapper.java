package com.cgcpms.project.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.common.util.DeletedCodeSource;
import com.cgcpms.project.entity.PmProject;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PmProjectMapper extends BaseMapper<PmProject>, DeletedCodeSource {

    /**
     * 定时线程没有认证租户，只在这里跨租户发现存在活跃项目的租户。
     * 项目明细仍在显式租户上下文中查询。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT tenant_id
            FROM pm_project
            WHERE status = 'ACTIVE'
              AND deleted_flag = 0
            ORDER BY tenant_id
            """)
    List<Long> selectActiveTenantIds();

    @Select("SELECT project_code FROM pm_project WHERE project_code LIKE CONCAT(#{prefix}, '%') AND tenant_id = #{tenantId} ORDER BY CHAR_LENGTH(project_code) DESC, project_code DESC LIMIT 1")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);

    @Select("""
            SELECT id,tenant_id,org_id,project_code,project_name,project_type,project_address,
                   owner_unit,supervisor_unit,design_unit,contract_amount,target_cost,planned_start_date,
                   planned_end_date,actual_start_date,actual_end_date,project_manager_id,source_bid_cost_id,
                   owner_contract_id,initiation_basis,status,approval_status,created_by,created_at,
                   updated_by,updated_at,deleted_flag,remark
            FROM pm_project WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted_flag=0 FOR UPDATE
            """)
    PmProject selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Delete("DELETE FROM pm_project WHERE id=#{id} AND tenant_id=#{tenantId}")
    int physicalDelete(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
