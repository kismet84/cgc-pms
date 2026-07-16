package com.cgcpms.settlement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StlSettlementMapper extends BaseMapper<StlSettlement> {
    @Select("SELECT * FROM stl_settlement WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE")
    StlSettlement selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
