package com.cgcpms.cashbook.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.payment.entity.PayRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentArchiveEvidenceService {

    private static final Set<String> PAYMENT_DOCUMENT_TYPES = Set.of("BANK_RECEIPT", "PAYMENT_PROOF");

    private final JdbcTemplate jdbc;

    public void requireEvidenceAndBind(CashJournalEntry journal, PayRecord record) {
        String requiredType = "BANK_TRANSFER".equals(record.getPayMethod())
                ? "BANK_RECEIPT" : "PAYMENT_PROOF";
        List<Map<String, Object>> files = jdbc.queryForList("""
                SELECT id,document_type
                  FROM sys_file
                 WHERE tenant_id=? AND business_type='CASH_JOURNAL' AND business_id=?
                   AND virus_scan_status='CLEAN' AND deleted_flag=0
                 FOR UPDATE
                """, journal.getTenantId(), journal.getId());
        if (files.stream().noneMatch(file -> requiredType.equals(file.get("document_type")))) {
            throw new BusinessException("CASH_JOURNAL_EVIDENCE_REQUIRED",
                    "归档前必须上传病毒扫描通过的" + ("BANK_RECEIPT".equals(requiredType) ? "银行回单" : "付款凭证"));
        }
        for (Map<String, Object> file : files) {
            String documentType = String.valueOf(file.get("document_type"));
            if (!PAYMENT_DOCUMENT_TYPES.contains(documentType)) continue;
            jdbc.update("""
                    INSERT INTO payment_document_link
                        (id,tenant_id,cash_journal_id,file_id,document_type,created_by,created_at)
                    SELECT ?,?,?,?,?,?,CURRENT_TIMESTAMP
                     WHERE NOT EXISTS (
                        SELECT 1 FROM payment_document_link WHERE tenant_id=? AND file_id=?
                     )
                    """, IdWorker.getId(), journal.getTenantId(), journal.getId(),
                    ((Number) file.get("id")).longValue(), documentType, UserContext.getCurrentUserId(),
                    journal.getTenantId(), ((Number) file.get("id")).longValue());
        }
        int bound = jdbc.update("""
                UPDATE accounting_entry
                   SET cash_journal_id=?,version=version+1,updated_at=CURRENT_TIMESTAMP
                 WHERE tenant_id=? AND pay_record_id=? AND entry_type='PAYMENT'
                   AND entry_status='DRAFT' AND deleted_flag=0
                   AND (cash_journal_id IS NULL OR cash_journal_id=?)
                """, journal.getId(), journal.getTenantId(), record.getId(), journal.getId());
        if (bound != 1) {
            throw new BusinessException("PAYMENT_ACCOUNTING_ENTRY_BIND_FAILED",
                    "付款草稿凭证缺失、状态异常或已绑定其他现金日记");
        }
    }

    public void bindReversal(CashJournalEntry journal, Long payRecordId) {
        int bound = jdbc.update("""
                UPDATE accounting_entry
                   SET cash_journal_id=?,version=version+1,updated_at=CURRENT_TIMESTAMP
                 WHERE tenant_id=? AND pay_record_id=? AND entry_type='PAYMENT_REVERSAL'
                   AND entry_status='DRAFT' AND deleted_flag=0
                   AND (cash_journal_id IS NULL OR cash_journal_id=?)
                """, journal.getId(), journal.getTenantId(), payRecordId, journal.getId());
        if (bound != 1) {
            throw new BusinessException("PAYMENT_REVERSAL_ENTRY_BIND_FAILED",
                    "付款冲销草稿凭证缺失、状态异常或已绑定其他现金日记");
        }
    }

    public void assertReopenAllowed(CashJournalEntry journal) {
        Long posted = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM accounting_entry
                 WHERE tenant_id=? AND cash_journal_id=?
                   AND entry_status='POSTED' AND deleted_flag=0
                """, Long.class, journal.getTenantId(), journal.getId());
        if (posted != null && posted > 0) {
            throw new BusinessException("PAYMENT_ACCOUNTING_ENTRY_POSTED",
                    "付款凭证已过账，必须先走凭证冲销，禁止撤销现金归档");
        }
    }
}
