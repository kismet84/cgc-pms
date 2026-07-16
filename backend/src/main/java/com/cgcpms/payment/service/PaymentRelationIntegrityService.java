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
        add(issues, "INVOICE_ALLOCATION_RECORD_ORPHAN", count("SELECT COUNT(*) FROM invoice_payment_allocation x LEFT JOIN pay_record r ON r.id=x.pay_record_id WHERE x.tenant_id=? AND r.id IS NULL", tenantId));
        add(issues, "ACCOUNTING_PAY_RECORD_ORPHAN", count("SELECT COUNT(*) FROM accounting_entry e LEFT JOIN pay_record r ON r.id=e.pay_record_id WHERE e.tenant_id=? AND e.deleted_flag=0 AND e.pay_record_id IS NOT NULL AND r.id IS NULL", tenantId));
        add(issues, "PAY_APP_CROSS_TENANT_RELATION", count("SELECT COUNT(*) FROM pay_application a JOIN ct_contract c ON c.id=a.contract_id WHERE a.tenant_id=? AND a.deleted_flag=0 AND (a.tenant_id<>c.tenant_id OR a.project_id<>c.project_id)", tenantId));
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
