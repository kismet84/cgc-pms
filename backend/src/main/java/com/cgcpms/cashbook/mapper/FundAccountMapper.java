package com.cgcpms.cashbook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.cashbook.entity.FundAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface FundAccountMapper extends BaseMapper<FundAccount> {

    @Select("SELECT * FROM fund_account WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE")
    FundAccount selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("""
            SELECT a.opening_balance + COALESCE((
                SELECT SUM(CASE WHEN e.direction = 'IN' THEN e.amount ELSE -e.amount END)
                FROM cash_journal_entry e
                WHERE e.tenant_id = #{tenantId}
                  AND e.account_id = #{accountId}
                  AND e.deleted_flag = 0
                  AND e.status IN ('ARCHIVED', 'REVERSED')
                  AND e.business_date >= a.opening_date
            ), 0)
            FROM fund_account a
            WHERE a.id = #{accountId} AND a.tenant_id = #{tenantId} AND a.deleted_flag = 0
            """)
    BigDecimal selectCurrentBalance(@Param("accountId") Long accountId, @Param("tenantId") Long tenantId);
}
