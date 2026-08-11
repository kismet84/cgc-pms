package com.cgcpms.receipt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.common.util.DeletedCodeSource;
import com.cgcpms.receipt.entity.MatReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MatReceiptMapper extends BaseMapper<MatReceipt>, DeletedCodeSource {
    @Select("SELECT receipt_code FROM mat_receipt WHERE receipt_code LIKE CONCAT(#{prefix}, '%') AND tenant_id = #{tenantId} ORDER BY CHAR_LENGTH(receipt_code) DESC, receipt_code DESC LIMIT 1")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);

    @Select("""
            SELECT id,tenant_id,project_id,order_id,contract_id,partner_id,receipt_code,system_batch_no,
                   delivery_note_no,receipt_date,warehouse_id,receiver_id,receipt_mode,quality_status,
                   total_amount,approval_status,cost_generated_flag,created_by,created_at,updated_by,
                   updated_at,deleted_flag,remark
            FROM mat_receipt WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE
            """)
    MatReceipt selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("""
            <script>
            SELECT DISTINCT r.order_id
            FROM mat_receipt r
            JOIN mat_stock_txn s
              ON s.tenant_id = r.tenant_id
             AND s.source_type = 'MAT_RECEIPT'
             AND s.source_id = r.id
             AND s.txn_type = 'IN'
             AND s.deleted_flag = 0
            WHERE r.tenant_id = #{tenantId}
              AND r.project_id IN
              <foreach collection="projectIds" item="projectId" open="(" separator="," close=")">
                #{projectId}
              </foreach>
              AND r.approval_status = 'APPROVED'
              AND r.deleted_flag = 0
              AND r.order_id IS NOT NULL
            </script>
            """)
    List<Long> selectCompletedStockInOrderIds(@Param("tenantId") Long tenantId,
                                               @Param("projectIds") Collection<Long> projectIds);
}
