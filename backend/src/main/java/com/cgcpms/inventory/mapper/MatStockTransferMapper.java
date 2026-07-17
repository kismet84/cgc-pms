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
    @Select("SELECT * FROM mat_stock_transfer WHERE tenant_id = #{tenantId} "
            + "AND idempotency_key = #{idempotencyKey} AND deleted_flag = 0 FOR UPDATE")
    MatStockTransfer selectByTenantAndKeyForUpdate(@Param("tenantId") Long tenantId,
                                                   @Param("idempotencyKey") String idempotencyKey);
}
