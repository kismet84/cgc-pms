package com.cgcpms.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.vo.MdMaterialPurchasePriceRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MdMaterialMapper extends BaseMapper<MdMaterial> {
    @Select("""
            <script>
            SELECT material_id, unit_price AS purchase_price, receipt_item_id, receipt_date
            FROM (
                SELECT i.material_id, i.unit_price, i.id AS receipt_item_id, r.receipt_date,
                       ROW_NUMBER() OVER (
                           PARTITION BY i.material_id
                           ORDER BY r.receipt_date DESC, i.id DESC
                       ) AS row_no
                FROM mat_receipt_item i
                JOIN mat_receipt r ON r.id = i.receipt_id AND r.tenant_id = i.tenant_id
                WHERE i.tenant_id = #{tenantId}
                  AND i.deleted_flag = 0
                  AND r.deleted_flag = 0
                  AND r.approval_status = 'APPROVED'
                  AND i.qualified_quantity &gt; 0
                  AND i.unit_price &gt; 0
                  AND i.material_id IN
                  <foreach collection="materialIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ) ranked
            WHERE row_no = 1
            </script>
            """)
    List<MdMaterialPurchasePriceRow> selectLatestApprovedPurchasePrices(
            @Param("tenantId") Long tenantId,
            @Param("materialIds") List<Long> materialIds);
}
