package com.cgcpms.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.common.util.DeletedCodeSource;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatPurchaseOrderMapper extends BaseMapper<MatPurchaseOrder>, DeletedCodeSource {
    @Select("SELECT order_code FROM mat_purchase_order WHERE order_code LIKE CONCAT(#{prefix}, '%') AND tenant_id = #{tenantId} ORDER BY CHAR_LENGTH(order_code) DESC, order_code DESC LIMIT 1")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);

    @Select("""
            SELECT id,tenant_id,project_id,request_id,contract_id,partner_id,order_code,order_type,
                   order_date,delivery_date,delivery_terms,exception_purchase_flag,exception_reason,
                   total_amount,approval_status,order_status,budget_revision,pricing_mode,created_by,
                   created_at,updated_by,updated_at,deleted_flag,remark
            FROM mat_purchase_order WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted_flag=0 FOR UPDATE
            """)
    MatPurchaseOrder selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
