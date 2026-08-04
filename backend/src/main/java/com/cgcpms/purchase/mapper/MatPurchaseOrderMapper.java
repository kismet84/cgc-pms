package com.cgcpms.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatPurchaseOrderMapper extends BaseMapper<MatPurchaseOrder> {
    @Select("SELECT * FROM mat_purchase_order WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted_flag=0 FOR UPDATE")
    MatPurchaseOrder selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
