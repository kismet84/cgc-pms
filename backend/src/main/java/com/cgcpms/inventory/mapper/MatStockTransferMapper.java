package com.cgcpms.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.inventory.entity.MatStockTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatStockTransferMapper extends BaseMapper<MatStockTransfer> {

    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT id, tenant_id, project_id, source_stock_id, target_stock_id, source_warehouse_id, "
            + "target_warehouse_id, material_id, quantity, unit_cost, amount, idempotency_key, status, completed_at, "
            + "created_by, created_at, updated_by, updated_at, deleted_flag, remark FROM mat_stock_transfer WHERE tenant_id = #{tenantId} "
            + "AND idempotency_key = #{idempotencyKey} AND deleted_flag = 0 FOR UPDATE")
    MatStockTransfer selectByTenantAndKeyForUpdate(@Param("tenantId") Long tenantId,
                                                   @Param("idempotencyKey") String idempotencyKey);
}
