package com.cgcpms.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MatPurchaseOrderItemMapper extends BaseMapper<MatPurchaseOrderItem> {
    void insertBatch(@Param("items") List<MatPurchaseOrderItem> items);

    @Select("""
            SELECT id,tenant_id,order_id,request_item_id,wbs_task_id,budget_line_id,project_id,
                   material_id,material_name,specification,unit,quantity,unit_price,amount,
                   received_quantity,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark
            FROM mat_purchase_order_item
            WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted_flag=0
            FOR UPDATE
            """)
    MatPurchaseOrderItem selectForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
