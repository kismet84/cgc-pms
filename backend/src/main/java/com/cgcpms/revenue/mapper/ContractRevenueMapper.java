package com.cgcpms.revenue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.common.util.DeletedCodeSource;
import com.cgcpms.revenue.entity.ContractRevenue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContractRevenueMapper extends BaseMapper<ContractRevenue>, DeletedCodeSource {

    @Override
    @Select("SELECT revenue_code FROM contract_revenue WHERE revenue_code LIKE CONCAT(#{prefix}, '%') "
            + "AND tenant_id = #{tenantId} "
            + "ORDER BY CHAR_LENGTH(revenue_code) DESC, revenue_code DESC LIMIT 1")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);
}
