package com.cgcpms.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.inventory.entity.MatStockTxn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatStockTxnMapper extends BaseMapper<MatStockTxn> {

    @Select("""
            SELECT id, tenant_id, warehouse_id, material_id, txn_type, quantity,
                   available_after, unit_cost, amount, source_type, source_id, source_line_id,
                   created_by, created_at, updated_by, updated_at, deleted_flag, remark
            FROM mat_stock_txn
            WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0
            FOR UPDATE
            """)
    MatStockTxn selectForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("""
            SELECT id, tenant_id, warehouse_id, material_id, txn_type, quantity,
                   available_after, unit_cost, amount, source_type, source_id, source_line_id,
                   created_by, created_at, updated_by, updated_at, deleted_flag, remark
            FROM mat_stock_txn
            WHERE tenant_id=#{tenantId} AND source_type='MAT_RECEIPT'
              AND source_id=#{receiptId} AND source_line_id=#{receiptItemId}
              AND txn_type='IN' AND deleted_flag=0
            FOR UPDATE
            """)
    MatStockTxn selectReceiptInForUpdate(@Param("tenantId") Long tenantId,
                                         @Param("receiptId") Long receiptId,
                                         @Param("receiptItemId") Long receiptItemId);
}
