package com.cgcpms.materialreturn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.materialreturn.entity.MaterialReturn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MaterialReturnMapper extends BaseMapper<MaterialReturn> {
    @Select("""
            SELECT id, tenant_id, project_id, contract_id, warehouse_id, requisition_id,
                   return_code, return_date, status, reason, idempotency_key, total_amount,
                   confirmed_by, confirmed_at, reversed_by, reversed_at, reversal_reason, version,
                   created_by, created_at, updated_by, updated_at, deleted_flag, remark
            FROM mat_material_return
            WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted_flag=0
            FOR UPDATE
            """)
    MaterialReturn selectForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
