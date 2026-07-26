package com.cgcpms.accounting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.accounting.entity.AccountingEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AccountingEntryMapper extends BaseMapper<AccountingEntry> {
    @Select("""
            SELECT *
            FROM accounting_entry
            WHERE id = #{id}
              AND tenant_id = #{tenantId}
              AND deleted_flag = 0
            FOR UPDATE
            """)
    AccountingEntry selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("""
            SELECT *
            FROM accounting_entry
            WHERE tenant_id = #{tenantId}
              AND pay_record_id = #{payRecordId}
              AND entry_type = 'PAYMENT'
              AND deleted_flag = 0
            FOR UPDATE
            """)
    AccountingEntry selectPaymentByRecordForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("payRecordId") Long payRecordId);

    @Select("""
            SELECT status
            FROM cash_journal_entry
            WHERE id = #{cashJournalId}
              AND tenant_id = #{tenantId}
              AND deleted_flag = 0
            """)
    String selectCashJournalStatus(
            @Param("tenantId") Long tenantId,
            @Param("cashJournalId") Long cashJournalId);
}
