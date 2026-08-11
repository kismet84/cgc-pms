package com.cgcpms.bid.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.cgcpms.bid.entity.BidDeposit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface BidDepositMapper extends BaseMapper<BidDeposit> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COALESCE(SUM(COALESCE(deposit_amount, 0) - COALESCE(returned_amount, 0)), 0)
            FROM bid_deposit
            WHERE tenant_id = #{tenantId}
              AND bid_cost_id = #{bidCostId}
              AND deleted_flag = 0
              AND deposit_status != 'FORFEITED'
            """)
    BigDecimal selectOutstandingTotal(@Param("tenantId") Long tenantId,
                                      @Param("bidCostId") Long bidCostId);
}
