package com.cgcpms.receipt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatReceiptMapper extends BaseMapper<MatReceipt> {
    @Select("""
            SELECT id,tenant_id,project_id,order_id,contract_id,partner_id,receipt_code,system_batch_no,
                   delivery_note_no,receipt_date,warehouse_id,receiver_id,receipt_mode,quality_status,
                   total_amount,approval_status,cost_generated_flag,created_by,created_at,updated_by,
                   updated_at,deleted_flag,remark
            FROM mat_receipt WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE
            """)
    MatReceipt selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
