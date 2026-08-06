package com.cgcpms.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.vo.MdMaterialPurchasePriceRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MdMaterialMapper extends BaseMapper<MdMaterial> {
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id,tenant_id,material_code,material_name,category_id,specification,unit,brand,
                   default_tax_rate,tax_inclusive_info_price,info_price_period,info_price_source,
                   info_price_verification_status,info_price_external_row_key,info_price_review_required,
                   status,created_by,created_at,updated_by,updated_at,deleted_flag,remark
            FROM md_material
            WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted_flag=0
            FOR UPDATE
            """)
    MdMaterial selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT CASE WHEN
                EXISTS (SELECT 1 FROM ct_contract_item WHERE tenant_id=#{tenantId} AND material_id=#{id} AND deleted_flag=0)
                OR EXISTS (SELECT 1 FROM mat_purchase_request_item WHERE tenant_id=#{tenantId} AND material_id=#{id} AND deleted_flag=0)
                OR EXISTS (SELECT 1 FROM mat_purchase_order_item WHERE tenant_id=#{tenantId} AND material_id=#{id} AND deleted_flag=0)
                OR EXISTS (SELECT 1 FROM mat_receipt_item WHERE tenant_id=#{tenantId} AND material_id=#{id} AND deleted_flag=0)
                OR EXISTS (SELECT 1 FROM mat_requisition_item WHERE tenant_id=#{tenantId} AND material_id=#{id} AND deleted_flag=0)
                OR EXISTS (SELECT 1 FROM mat_material_return_item WHERE tenant_id=#{tenantId} AND material_id=#{id} AND deleted_flag=0)
                OR EXISTS (SELECT 1 FROM mat_stock WHERE tenant_id=#{tenantId} AND material_id=#{id} AND deleted_flag=0)
                OR EXISTS (SELECT 1 FROM mat_stock_transfer WHERE tenant_id=#{tenantId} AND material_id=#{id} AND deleted_flag=0)
                OR EXISTS (SELECT 1 FROM mat_stock_txn WHERE tenant_id=#{tenantId} AND material_id=#{id} AND deleted_flag=0)
                OR EXISTS (SELECT 1 FROM sp_supplier_return_item WHERE tenant_id=#{tenantId} AND material_id=#{id} AND deleted_flag=0)
            THEN 1 ELSE 0 END
            """)
    int hasActiveReferences(@Param("id") Long id, @Param("tenantId") Long tenantId);

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
