package com.cgcpms.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.project.entity.PmProject;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PmProjectMapper extends BaseMapper<PmProject> {

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
