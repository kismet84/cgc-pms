package com.cgcpms.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.inventory.entity.MatStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatStockMapper extends BaseMapper<MatStock> {

    @Select("SELECT id, tenant_id, warehouse_id, material_id, available_qty, inventory_value, average_unit_cost, "
            + "safety_stock_qty, replenishment_target_qty, replenishment_lead_days, version, created_by, created_at, "
            + "updated_by, updated_at, deleted_flag, remark FROM mat_stock WHERE id = #{id} AND tenant_id = #{tenantId} "
            + "AND deleted_flag = 0 FOR UPDATE")
    MatStock selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
