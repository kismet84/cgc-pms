package com.cgcpms.settlement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.common.util.DeletedCodeSource;
import com.cgcpms.settlement.entity.StlSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StlSettlementMapper extends BaseMapper<StlSettlement>, DeletedCodeSource {
    @Select("SELECT settlement_code FROM stl_settlement WHERE settlement_code LIKE CONCAT(#{prefix}, '%') AND tenant_id = #{tenantId} ORDER BY CHAR_LENGTH(settlement_code) DESC, settlement_code DESC LIMIT 1")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM stl_settlement WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE")
    StlSettlement selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
