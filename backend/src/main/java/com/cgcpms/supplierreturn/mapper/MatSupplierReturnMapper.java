package com.cgcpms.supplierreturn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.supplierreturn.entity.MatSupplierReturn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatSupplierReturnMapper extends BaseMapper<MatSupplierReturn> {
    @Select("""
            SELECT id,tenant_id,project_id,contract_id,partner_id,receipt_id,warehouse_id,
                   return_code,return_date,status,idempotency_key,total_amount,reason,
                   confirmed_by,confirmed_at,reversed_by,reversed_at,reversal_reason,version,
                   created_by,created_at,updated_by,updated_at,deleted_flag,remark
            FROM mat_supplier_return
            WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted_flag=0
            FOR UPDATE
            """)
    MatSupplierReturn selectForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
