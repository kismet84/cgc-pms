package com.cgcpms.receipt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.receipt.entity.MatReceiptItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatReceiptItemMapper extends BaseMapper<MatReceiptItem> {
    @Select("""
            SELECT id,tenant_id,receipt_id,order_item_id,material_id,wbs_task_id,budget_line_id,
                   actual_quantity,qualified_quantity,unit_price,amount,use_location,batch_no,
                   created_by,created_at,updated_by,updated_at,deleted_flag,remark
            FROM mat_receipt_item
            WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted_flag=0
            FOR UPDATE
            """)
    MatReceiptItem selectForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
