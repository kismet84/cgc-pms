package com.cgcpms.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.inventory.vo.StockConsumptionBaselineVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

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

    @Select("""
            SELECT
              COALESCE(SUM(CASE WHEN created_at >= #{window30Start}
                AND txn_type='OUT' AND source_type='MAT_REQUISITION' THEN quantity ELSE 0 END), 0) AS grossIssued30,
              COALESCE(SUM(CASE WHEN created_at >= #{window30Start}
                AND txn_type='IN' AND source_type='MATERIAL_RETURN' THEN quantity ELSE 0 END), 0) AS returned30,
              COALESCE(SUM(CASE WHEN txn_type='OUT' AND source_type='MAT_REQUISITION' THEN quantity ELSE 0 END), 0) AS grossIssued90,
              COALESCE(SUM(CASE WHEN txn_type='IN' AND source_type='MATERIAL_RETURN' THEN quantity ELSE 0 END), 0) AS returned90
            FROM mat_stock_txn
            WHERE tenant_id=#{tenantId} AND warehouse_id=#{warehouseId} AND material_id=#{materialId}
              AND deleted_flag=0 AND created_at >= #{window90Start} AND created_at <= #{cutoffAt}
              AND ((txn_type='OUT' AND source_type='MAT_REQUISITION')
                OR (txn_type='IN' AND source_type='MATERIAL_RETURN'))
            """)
    StockConsumptionBaselineVO selectConsumptionBaseline(@Param("tenantId") Long tenantId,
                                                          @Param("warehouseId") Long warehouseId,
                                                          @Param("materialId") Long materialId,
                                                          @Param("window30Start") LocalDateTime window30Start,
                                                          @Param("window90Start") LocalDateTime window90Start,
                                                          @Param("cutoffAt") LocalDateTime cutoffAt);
}
