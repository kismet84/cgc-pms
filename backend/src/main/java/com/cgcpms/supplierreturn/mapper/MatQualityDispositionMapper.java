package com.cgcpms.supplierreturn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.supplierreturn.entity.MatQualityDisposition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatQualityDispositionMapper extends BaseMapper<MatQualityDisposition> {
    @Select("""
            SELECT id,tenant_id,project_id,receipt_id,receipt_item_id,rejected_quantity,
                   disposition_action,status,resolved_quantity,resolved_at,version,
                   created_by,created_at,updated_by,updated_at,deleted_flag,remark
            FROM mat_quality_disposition
            WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted_flag=0
            FOR UPDATE
            """)
    MatQualityDisposition selectForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("""
            SELECT id,tenant_id,project_id,receipt_id,receipt_item_id,rejected_quantity,
                   disposition_action,status,resolved_quantity,resolved_at,version,
                   created_by,created_at,updated_by,updated_at,deleted_flag,remark
            FROM mat_quality_disposition
            WHERE receipt_item_id=#{receiptItemId} AND tenant_id=#{tenantId}
              AND disposition_action='RETURN_TO_SUPPLIER' AND status<>'CANCELLED' AND deleted_flag=0
            FOR UPDATE
            """)
    MatQualityDisposition selectReturnForUpdate(@Param("receiptItemId") Long receiptItemId,
                                                @Param("tenantId") Long tenantId);
}
