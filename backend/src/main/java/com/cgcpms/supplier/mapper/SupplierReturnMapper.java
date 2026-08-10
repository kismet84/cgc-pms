package com.cgcpms.supplier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.supplier.entity.SupplierReturn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Mapper
public interface SupplierReturnMapper extends BaseMapper<SupplierReturn> {
    @Select("""
            SELECT r.* FROM sp_supplier_return r
            WHERE r.tenant_id=#{tenantId} AND r.project_id=#{projectId}
              AND r.status IN ('CONFIRMED','REVERSED') AND r.deleted_flag=0
              AND EXISTS (SELECT 1 FROM sp_supplier_return_item i
                WHERE i.tenant_id=r.tenant_id AND i.return_id=r.id AND i.deleted_flag=0)
            ORDER BY r.return_date DESC, r.id DESC
            """)
    List<SupplierReturn> selectFormalByProject(@Param("tenantId") Long tenantId,
                                                @Param("projectId") Long projectId);

    @Select("""
            <script>
            SELECT r.* FROM sp_supplier_return r
            WHERE r.tenant_id=#{tenantId} AND r.purchase_order_id IN
              <foreach collection="orderIds" item="orderId" open="(" separator="," close=")">#{orderId}</foreach>
              AND r.status IN ('CONFIRMED','REVERSED') AND r.deleted_flag=0
              AND EXISTS (SELECT 1 FROM sp_supplier_return_item i
                WHERE i.tenant_id=r.tenant_id AND i.return_id=r.id AND i.deleted_flag=0)
            ORDER BY r.return_date, r.id
            </script>
            """)
    List<SupplierReturn> selectFormalByOrders(@Param("tenantId") Long tenantId,
                                               @Param("orderIds") Collection<Long> orderIds);

    @Select("""
            SELECT COUNT(*) FROM sp_supplier_return r
            WHERE r.tenant_id=#{tenantId} AND r.purchase_order_id=#{orderId}
              AND r.status='CONFIRMED' AND r.return_date BETWEEN #{periodStart} AND #{periodEnd}
              AND r.deleted_flag=0
              AND EXISTS (SELECT 1 FROM sp_supplier_return_item i
                WHERE i.tenant_id=r.tenant_id AND i.return_id=r.id AND i.deleted_flag=0)
            """)
    long countConfirmedFormalByOrder(@Param("tenantId") Long tenantId,
                                     @Param("orderId") Long orderId,
                                     @Param("periodStart") LocalDate periodStart,
                                     @Param("periodEnd") LocalDate periodEnd);
}
