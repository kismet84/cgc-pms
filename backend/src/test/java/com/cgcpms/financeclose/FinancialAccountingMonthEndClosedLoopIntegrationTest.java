package com.cgcpms.financeclose;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.service.AccountingEntryService;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.financeclose.dto.FinancialCloseModels.AdjustmentLine;
import com.cgcpms.financeclose.dto.FinancialCloseModels.AdjustmentRequest;
import com.cgcpms.financeclose.service.FinancialCloseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class FinancialAccountingMonthEndClosedLoopIntegrationTest {
    private static final long TENANT = 88192L;
    private static final int YEAR = 2031;
    private static final int MONTH = 1;

    @Autowired FinancialCloseService closeService;
    @Autowired AccountingEntryService entryService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        TestUserContext.setAdmin(TENANT, 101L);
        cleanup();
    }

    @AfterEach
    void teardown() {
        cleanup();
        UserContext.clear();
    }

    @Test
    void fullChainRequiresReviewBlocksIssuesLocksAndReopensPeriod() {
        Map<String,Object> period = closeService.ensurePeriod(YEAR, MONTH);
        assertEquals("OPEN", period.get("status"));

        AccountingEntry adjustment = closeService.createAdjustment(adjustment("月末计提"));
        assertEquals("PENDING", adjustment.getReviewStatus());
        BusinessException unreviewed = assertThrows(BusinessException.class, () -> entryService.post(adjustment.getId()));
        assertEquals("ENTRY_REVIEW_REQUIRED", unreviewed.getCode());

        Map<String,Object> failedCheck = closeService.runChecks(YEAR, MONTH);
        Number issueCount = (Number) ((Map<?,?>) failedCheck.get("period")).get("issue_count");
        assertTrue(issueCount.intValue() > 0);
        BusinessException issues = assertThrows(BusinessException.class, () -> closeService.close(YEAR, MONTH, "月结"));
        assertEquals("FINANCE_PERIOD_ISSUES_EXIST", issues.getCode());

        TestUserContext.setUser(TENANT, 102L, "reviewer", List.of("FINANCE"));
        entryService.review(adjustment.getId(), true, "科目和金额复核通过");
        TestUserContext.setUser(TENANT, 103L, "poster", List.of("FINANCE"));
        entryService.post(adjustment.getId());

        Map<String,Object> passed = closeService.runChecks(YEAR, MONTH);
        assertEquals(0, ((Number)((Map<?,?>)passed.get("period")).get("issue_count")).intValue());
        Map<String,Object> closed = closeService.close(YEAR, MONTH, "2031年1月结账");
        assertEquals("CLOSED", ((Map<?,?>)closed.get("period")).get("status"));
        assertFalse(((List<?>)closed.get("checks")).isEmpty());
        assertFalse(((List<?>)closed.get("auditTrail")).isEmpty());
        assertFalse(((List<?>) closeService.statements(YEAR, MONTH).get("trialBalance")).isEmpty());

        BusinessException locked = assertThrows(BusinessException.class, () -> closeService.createAdjustment(adjustment("锁账后调整")));
        assertEquals("FINANCE_PERIOD_CLOSED", locked.getCode());
        Map<String,Object> reopened = closeService.reopen(YEAR, MONTH, "审计调整");
        assertEquals("REOPENED", ((Map<?,?>)reopened.get("period")).get("status"));
        AccountingEntry afterReopen = closeService.createAdjustment(adjustment("审计调整"));
        assertEquals("DRAFT", afterReopen.getEntryStatus());
    }

    @Test
    void makerCheckerSeparationAndBalancedAdjustmentAreMandatory() {
        closeService.ensurePeriod(YEAR, MONTH);
        AccountingEntry adjustment = closeService.createAdjustment(adjustment("职责分离"));
        BusinessException segregation = assertThrows(BusinessException.class, () -> entryService.review(adjustment.getId(), true, "自审"));
        assertEquals("ENTRY_REVIEW_SEGREGATION_REQUIRED", segregation.getCode());

        AdjustmentRequest invalid = new AdjustmentRequest(LocalDate.of(YEAR, MONTH, 15), null, null, "不平衡",
                List.of(new AdjustmentLine("DEBIT", "6602", "管理费用", null, new BigDecimal("10.00"), "单边分录")));
        BusinessException unbalanced = assertThrows(BusinessException.class, () -> closeService.createAdjustment(invalid));
        assertEquals("ADJUSTMENT_ENTRY_UNBALANCED", unbalanced.getCode());
    }

    private AdjustmentRequest adjustment(String reason) {
        return new AdjustmentRequest(LocalDate.of(YEAR, MONTH, 15), null, null, reason, List.of(
                new AdjustmentLine("DEBIT", "6602", "管理费用", null, new BigDecimal("100.00"), reason),
                new AdjustmentLine("CREDIT", "2202", "应付账款", null, new BigDecimal("100.00"), reason)));
    }

    private void cleanup() {
        jdbc.update("DELETE FROM finance_audit_event WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM accounting_entry_line WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM accounting_entry WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM finance_bank_reconciliation WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM finance_account_reconciliation WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM finance_period_check WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM finance_period WHERE tenant_id=?", TENANT);
    }
}
