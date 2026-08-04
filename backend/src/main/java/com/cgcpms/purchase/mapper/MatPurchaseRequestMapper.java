package com.cgcpms.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatPurchaseRequestMapper extends BaseMapper<MatPurchaseRequest> {
    @Select("""
            SELECT id,tenant_id,project_id,contract_id,purpose,request_code,approval_status,status,
                   created_by,created_at,updated_by,updated_at,deleted_flag,remark
            FROM mat_purchase_request WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted_flag=0 FOR UPDATE
            """)
    MatPurchaseRequest selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
