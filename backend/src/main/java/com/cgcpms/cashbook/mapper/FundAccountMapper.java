package com.cgcpms.cashbook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.cgcpms.cashbook.entity.FundAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

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
                  AND (e.status = 'ARCHIVED'
                       OR (e.status = 'REVERSED' AND e.archived_at IS NOT NULL))
                  AND e.business_date >= a.opening_date
            ), 0)
            FROM fund_account a
            WHERE a.id = #{accountId} AND a.tenant_id = #{tenantId} AND a.deleted_flag = 0
            """)
    BigDecimal selectCurrentBalance(@Param("accountId") Long accountId, @Param("tenantId") Long tenantId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT fa.account_type,
                   COALESCE(SUM(fa.opening_balance + COALESCE(movement.net_change, 0)), 0) AS balance
            FROM fund_account fa
            LEFT JOIN (
                SELECT journal.tenant_id,
                       journal.account_id,
                       SUM(CASE WHEN journal.direction = 'IN'
                                THEN journal.amount ELSE -journal.amount END) AS net_change
                FROM cash_journal_entry journal
                JOIN fund_account opened
                  ON opened.tenant_id = journal.tenant_id
                 AND opened.id = journal.account_id
                 AND opened.deleted_flag = 0
                WHERE journal.tenant_id = #{tenantId}
                  AND journal.deleted_flag = 0
                  AND (journal.status = 'ARCHIVED'
                       OR (journal.status = 'REVERSED' AND journal.archived_at IS NOT NULL))
                  AND journal.business_date &gt;= opened.opening_date
                <if test="accountId != null">
                  AND journal.account_id = #{accountId}
                </if>
                <if test="enabledOnly">
                  AND opened.enabled_flag = 1
                </if>
                GROUP BY journal.tenant_id, journal.account_id
            ) movement
              ON movement.tenant_id = fa.tenant_id
             AND movement.account_id = fa.id
            WHERE fa.tenant_id = #{tenantId}
              AND fa.deleted_flag = 0
            <if test="accountId != null">
              AND fa.id = #{accountId}
            </if>
            <if test="enabledOnly">
              AND fa.enabled_flag = 1
            </if>
            GROUP BY fa.account_type
            </script>
            """)
    List<AccountTypeBalance> selectBalancesByType(@Param("tenantId") Long tenantId,
                                                   @Param("accountId") Long accountId,
                                                   @Param("enabledOnly") boolean enabledOnly);

    class AccountTypeBalance {
        private String accountType;
        private BigDecimal balance;

        public AccountTypeBalance() {
        }

        public AccountTypeBalance(String accountType, BigDecimal balance) {
            this.accountType = accountType;
            this.balance = balance;
        }

        public String getAccountType() {
            return accountType;
        }

        public void setAccountType(String accountType) {
            this.accountType = accountType;
        }

        public BigDecimal getBalance() {
            return balance;
        }

        public void setBalance(BigDecimal balance) {
            this.balance = balance;
        }
    }
}
