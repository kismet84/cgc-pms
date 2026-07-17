package com.cgcpms.supplierreturn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.supplierreturn.entity.MatSupplierReturnItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface MatSupplierReturnItemMapper extends BaseMapper<MatSupplierReturnItem> {
    @Select("""
            SELECT COALESCE(SUM(i.quantity),0)
            FROM mat_supplier_return_item i
            JOIN mat_supplier_return r ON r.id=i.return_id AND r.tenant_id=i.tenant_id
            WHERE i.tenant_id=#{tenantId} AND i.receipt_item_id=#{receiptItemId}
              AND i.return_source=#{returnSource} AND r.status='CONFIRMED'
            """)
    BigDecimal sumConfirmedQuantity(@Param("tenantId") Long tenantId,
                                    @Param("receiptItemId") Long receiptItemId,
                                    @Param("returnSource") String returnSource);
}
