package com.cgcpms.cashbook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.cashbook.dto.CashJournalQuery;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.vo.CashJournalEntryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CashJournalEntryMapper extends BaseMapper<CashJournalEntry> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM cash_journal_entry WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE")
    CashJournalEntry selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM cash_journal_entry WHERE tenant_id = #{tenantId} AND pay_record_id = #{payRecordId} AND deleted_flag = 0 FOR UPDATE")
    CashJournalEntry selectByPayRecordForUpdate(@Param("tenantId") Long tenantId,
                                                @Param("payRecordId") Long payRecordId);

    @Select("SELECT * FROM cash_journal_entry WHERE tenant_id = #{tenantId} AND entry_no = #{entryNo} AND deleted_flag = 0 FOR UPDATE")
    CashJournalEntry selectByEntryNoForUpdate(@Param("tenantId") Long tenantId, @Param("entryNo") String entryNo);

    IPage<CashJournalEntryVO> selectPageWithBalance(Page<CashJournalEntryVO> page,
                                                     @Param("tenantId") Long tenantId,
                                                     @Param("query") CashJournalQuery query,
                                                     @Param("accessibleProjectIds") java.util.List<Long> accessibleProjectIds);

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

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT
              COALESCE(SUM(CASE
                WHEN (status = 'ARCHIVED' OR (status = 'REVERSED' AND archived_at IS NOT NULL))
                 AND direction = 'IN' THEN amount
                ELSE 0 END), 0) AS cash_in,
              COALESCE(SUM(CASE
                WHEN (status = 'ARCHIVED' OR (status = 'REVERSED' AND archived_at IS NOT NULL))
                 AND direction = 'OUT' THEN amount
                ELSE 0 END), 0) AS cash_out,
              COALESCE(SUM(CASE
                WHEN (status = 'ARCHIVED' OR (status = 'REVERSED' AND archived_at IS NOT NULL))
                 AND EXISTS (
                   SELECT 1 FROM cost_subject subject
                   WHERE subject.tenant_id = cash_journal_entry.tenant_id
                     AND subject.id = cash_journal_entry.cost_subject_id
                     AND subject.deleted_flag = 0
                     AND subject.account_category = 'COST'
                 )
                THEN CASE WHEN direction = 'OUT' THEN amount ELSE -amount END
                ELSE 0 END), 0) AS actual_bid_expense,
              COALESCE(SUM(CASE
                WHEN status IN ('DRAFT', 'PENDING_ARCHIVE') THEN 1
                ELSE 0 END), 0) AS pending_count
            FROM cash_journal_entry
            WHERE tenant_id = #{tenantId}
              AND deleted_flag = 0
            <if test="ew != null and ew.sqlSegment != null and ew.sqlSegment != ''">
              AND ${ew.sqlSegment}
            </if>
            </script>
            """)
    CashJournalAggregate selectSummaryAggregate(@Param("tenantId") Long tenantId,
                                                @Param(Constants.WRAPPER) Wrapper<CashJournalEntry> wrapper);

    class CashJournalAggregate {
        private BigDecimal cashIn;
        private BigDecimal cashOut;
        private BigDecimal actualBidExpense;
        private Long pendingCount;

        public CashJournalAggregate() {
        }

        public CashJournalAggregate(BigDecimal cashIn, BigDecimal cashOut,
                                    BigDecimal actualBidExpense, Long pendingCount) {
            this.cashIn = cashIn;
            this.cashOut = cashOut;
            this.actualBidExpense = actualBidExpense;
            this.pendingCount = pendingCount;
        }

        public static CashJournalAggregate empty() {
            return new CashJournalAggregate(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L);
        }

        public BigDecimal getCashIn() {
            return cashIn;
        }

        public void setCashIn(BigDecimal cashIn) {
            this.cashIn = cashIn;
        }

        public BigDecimal getCashOut() {
            return cashOut;
        }

        public void setCashOut(BigDecimal cashOut) {
            this.cashOut = cashOut;
        }

        public BigDecimal getActualBidExpense() {
            return actualBidExpense;
        }

        public void setActualBidExpense(BigDecimal actualBidExpense) {
            this.actualBidExpense = actualBidExpense;
        }

        public Long getPendingCount() {
            return pendingCount;
        }

        public void setPendingCount(Long pendingCount) {
            this.pendingCount = pendingCount;
        }
    }
}
