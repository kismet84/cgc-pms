package com.cgcpms.bid.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.common.util.DeletedCodeSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BidCostMapper extends BaseMapper<BidCost>, DeletedCodeSource {

    @Override
    @Select("SELECT bid_code FROM bid_cost WHERE bid_code LIKE CONCAT(#{prefix}, '%') "
            + "AND tenant_id = #{tenantId} ORDER BY bid_code DESC LIMIT 1")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);
}
