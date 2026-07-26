package com.cgcpms.payment.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.payment.vo.RelationIntegrityIssueVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentRelationIntegrityService {
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<RelationIntegrityIssueVO> scan() {
        long tenantId = UserContext.getCurrentTenantId();
        List<RelationIntegrityIssueVO> issues = new ArrayList<>();
        add(issues, "PAY_APP_PROJECT_ORPHAN", count("SELECT COUNT(*) FROM pay_application a LEFT JOIN pm_project p ON p.id=a.project_id WHERE a.tenant_id=? AND a.deleted_flag=0 AND a.project_id IS NOT NULL AND p.id IS NULL", tenantId));
        add(issues, "PAY_APP_CONTRACT_ORPHAN", count("SELECT COUNT(*) FROM pay_application a LEFT JOIN ct_contract c ON c.id=a.contract_id WHERE a.tenant_id=? AND a.deleted_flag=0 AND c.id IS NULL", tenantId));
        add(issues, "PAY_APP_PARTNER_ORPHAN", count("SELECT COUNT(*) FROM pay_application a LEFT JOIN md_partner p ON p.id=a.partner_id WHERE a.tenant_id=? AND a.deleted_flag=0 AND a.partner_id IS NOT NULL AND p.id IS NULL", tenantId));
        add(issues, "PAY_APP_BUDGET_LINE_ORPHAN", count("SELECT COUNT(*) FROM pay_application a LEFT JOIN project_budget_line b ON b.id=a.budget_line_id WHERE a.tenant_id=? AND a.deleted_flag=0 AND a.budget_line_id IS NOT NULL AND b.id IS NULL", tenantId));
        add(issues, "PAY_RECORD_APPLICATION_ORPHAN", count("SELECT COUNT(*) FROM pay_record r LEFT JOIN pay_application a ON a.id=r.pay_application_id WHERE r.tenant_id=? AND r.deleted_flag=0 AND a.id IS NULL", tenantId));
        add(issues, "CASH_JOURNAL_PAY_RECORD_ORPHAN", count("SELECT COUNT(*) FROM cash_journal_entry j LEFT JOIN pay_record r ON r.id=j.pay_record_id WHERE j.tenant_id=? AND j.deleted_flag=0 AND j.pay_record_id IS NOT NULL AND r.id IS NULL", tenantId));
        add(issues, "PAYMENT_DOCUMENT_JOURNAL_ORPHAN", count("SELECT COUNT(*) FROM payment_document_link d LEFT JOIN cash_journal_entry j ON j.id=d.cash_journal_id WHERE d.tenant_id=? AND j.id IS NULL", tenantId));
        add(issues, "PAYMENT_DOCUMENT_FILE_ORPHAN", count("SELECT COUNT(*) FROM payment_document_link d LEFT JOIN sys_file f ON f.id=d.file_id WHERE d.tenant_id=? AND f.id IS NULL", tenantId));
        add(issues, "INVOICE_ALLOCATION_INVOICE_ORPHAN", count("SELECT COUNT(*) FROM invoice_payment_allocation x LEFT JOIN pay_invoice i ON i.id=x.invoice_id WHERE x.tenant_id=? AND i.id IS NULL", tenantId));
        add(issues, "INVOICE_ALLOCATION_RECORD_ORPHAN", count("SELECT COUNT(*) FROM invoice_payment_allocation x LEFT JOIN pay_record r ON r.id=x.pay_record_id WHERE x.tenant_id=? AND r.id IS NULL", tenantId));
        add(issues, "INVOICE_ALLOCATION_APPLICATION_ORPHAN", count("SELECT COUNT(*) FROM invoice_payment_allocation x LEFT JOIN pay_application a ON a.id=x.pay_application_id WHERE x.tenant_id=? AND a.id IS NULL", tenantId));
        add(issues, "ACCOUNTING_PAY_RECORD_ORPHAN", count("SELECT COUNT(*) FROM accounting_entry e LEFT JOIN pay_record r ON r.id=e.pay_record_id WHERE e.tenant_id=? AND e.deleted_flag=0 AND e.pay_record_id IS NOT NULL AND r.id IS NULL", tenantId));
        add(issues, "ACCOUNTING_CASH_JOURNAL_ORPHAN", count("SELECT COUNT(*) FROM accounting_entry e LEFT JOIN cash_journal_entry j ON j.id=e.cash_journal_id WHERE e.tenant_id=? AND e.deleted_flag=0 AND e.cash_journal_id IS NOT NULL AND j.id IS NULL", tenantId));
        add(issues, "PAY_APP_CROSS_TENANT_RELATION", count("SELECT COUNT(*) FROM pay_application a JOIN ct_contract c ON c.id=a.contract_id WHERE a.tenant_id=? AND a.deleted_flag=0 AND (a.tenant_id<>c.tenant_id OR a.project_id<>c.project_id)", tenantId));
        add(issues, "PAYMENT_DOCUMENT_CROSS_TENANT_RELATION", count("SELECT COUNT(*) FROM payment_document_link d JOIN cash_journal_entry j ON j.id=d.cash_journal_id JOIN sys_file f ON f.id=d.file_id WHERE d.tenant_id=? AND (d.tenant_id<>j.tenant_id OR d.tenant_id<>f.tenant_id)", tenantId));
        add(issues, "INVOICE_ALLOCATION_CROSS_TENANT_RELATION", count("SELECT COUNT(*) FROM invoice_payment_allocation x JOIN pay_invoice i ON i.id=x.invoice_id JOIN pay_record r ON r.id=x.pay_record_id JOIN pay_application a ON a.id=x.pay_application_id WHERE x.tenant_id=? AND (x.tenant_id<>i.tenant_id OR x.tenant_id<>r.tenant_id OR x.tenant_id<>a.tenant_id)", tenantId));
        add(issues, "INVOICE_ALLOCATION_PROJECT_RELATION", count("SELECT COUNT(*) FROM invoice_payment_allocation x JOIN pay_invoice i ON i.id=x.invoice_id JOIN pay_record r ON r.id=x.pay_record_id JOIN pay_application a ON a.id=x.pay_application_id WHERE x.tenant_id=? AND (COALESCE(i.project_id,-1)<>COALESCE(r.project_id,-1) OR COALESCE(i.project_id,-1)<>COALESCE(a.project_id,-1))", tenantId));
        add(issues, "ACCOUNTING_CASH_JOURNAL_CROSS_TENANT_RELATION", count("SELECT COUNT(*) FROM accounting_entry e JOIN cash_journal_entry j ON j.id=e.cash_journal_id WHERE e.tenant_id=? AND e.deleted_flag=0 AND e.cash_journal_id IS NOT NULL AND e.tenant_id<>j.tenant_id", tenantId));
        add(issues, "PAYMENT_DIRECT_REFERENCE_MISMATCH", count("SELECT COUNT(*) FROM payment_application_source s WHERE s.tenant_id=? AND s.deleted_flag=0 AND s.source_type='DIRECT' AND s.source_ref_id<>s.pay_application_id", tenantId));
        return issues;
    }

    private long count(String sql, long tenantId) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, tenantId);
        return value == null ? 0 : value;
    }

    private void add(List<RelationIntegrityIssueVO> issues, String code, long count) {
        issues.add(new RelationIntegrityIssueVO(code, count, count == 0 ? "PASS" : "BLOCKER",
                count == 0 ? "无需处理" : "逐笔核对来源并修正；禁止静默删除或跨租户回填"));
    }
}
