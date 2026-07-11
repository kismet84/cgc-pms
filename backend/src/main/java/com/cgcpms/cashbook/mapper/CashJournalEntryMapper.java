package com.cgcpms.cashbook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.cashbook.dto.CashJournalQuery;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.vo.CashJournalEntryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CashJournalEntryMapper extends BaseMapper<CashJournalEntry> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM cash_journal_entry WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE")
    CashJournalEntry selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM cash_journal_entry WHERE tenant_id = #{tenantId} AND entry_no = #{entryNo} AND deleted_flag = 0 FOR UPDATE")
    CashJournalEntry selectByEntryNoForUpdate(@Param("tenantId") Long tenantId, @Param("entryNo") String entryNo);

    IPage<CashJournalEntryVO> selectPageWithBalance(Page<CashJournalEntryVO> page,
                                                     @Param("tenantId") Long tenantId,
                                                     @Param("query") CashJournalQuery query);

    @Select("SELECT entry_no FROM cash_journal_entry WHERE tenant_id = #{tenantId} AND entry_no LIKE CONCAT(#{prefix}, '%') ORDER BY entry_no DESC LIMIT 1")
    String selectLastEntryNo(@Param("tenantId") Long tenantId, @Param("prefix") String prefix);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT tenant_id
            FROM cash_journal_entry
            WHERE deleted_flag = 0
              AND status IN ('DRAFT', 'PENDING_ARCHIVE')
            ORDER BY tenant_id
            """)
    List<Long> selectPendingArchiveTenantIds();

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT *
            FROM cash_journal_entry
            WHERE tenant_id = #{tenantId}
              AND deleted_flag = 0
              AND status IN ('DRAFT', 'PENDING_ARCHIVE')
              AND closure_due_at <= #{now}
            ORDER BY id
            """)
    List<CashJournalEntry> selectOverdueForTenant(@Param("tenantId") Long tenantId,
                                                   @Param("now") LocalDateTime now);
}
