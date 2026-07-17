package com.cgcpms.materialreturn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.materialreturn.entity.MaterialReturnItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface MaterialReturnItemMapper extends BaseMapper<MaterialReturnItem> {
    @Select("""
            SELECT COALESCE(SUM(i.quantity),0)
            FROM mat_material_return_item i
            JOIN mat_material_return r
              ON r.tenant_id=i.tenant_id AND r.id=i.return_id
            WHERE i.tenant_id=#{tenantId}
              AND i.original_stock_txn_id=#{stockTxnId}
              AND i.deleted_flag=0
              AND r.deleted_flag=0
              AND r.status='CONFIRMED'
            """)
    BigDecimal sumConfirmedQuantity(@Param("tenantId") Long tenantId,
                                    @Param("stockTxnId") Long stockTxnId);
}
