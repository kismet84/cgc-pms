package com.cgcpms.supplierreturn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.supplierreturn.entity.SupplierReturnItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface SupplierReturnItemMapper extends BaseMapper<SupplierReturnItem> {
    @Select("""
            SELECT COALESCE(SUM(i.quantity), 0)
            FROM mat_supplier_return_item i
            JOIN mat_supplier_return r ON r.id = i.return_id AND r.tenant_id = i.tenant_id
            WHERE i.tenant_id = #{tenantId} AND i.receipt_item_id = #{receiptItemId}
              AND r.return_kind = #{returnKind} AND r.status = 'CONFIRMED'
              AND i.deleted_flag = 0 AND r.deleted_flag = 0
            """)
    BigDecimal sumConfirmedQuantity(@Param("tenantId") Long tenantId,
                                    @Param("receiptItemId") Long receiptItemId,
                                    @Param("returnKind") String returnKind);
}
