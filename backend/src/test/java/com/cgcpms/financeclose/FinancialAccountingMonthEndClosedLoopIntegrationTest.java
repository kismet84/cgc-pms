package com.cgcpms.financeclose;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.service.AccountingEntryService;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.financeclose.dto.FinancialCloseModels.AdjustmentLine;
import com.cgcpms.financeclose.dto.FinancialCloseModels.AdjustmentRequest;
import com.cgcpms.financeclose.service.FinancialCloseService;
import com.cgcpms.financeops.dto.FinanceOperationsModels.BankReceiptRequest;
import com.cgcpms.financeops.service.FinanceIntegrationService;
import com.cgcpms.financeops.vo.FinanceWorkspaceVOs.PeriodCheckVO;
import com.cgcpms.financeops.vo.FinanceWorkspaceVOs.ReconciliationVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class FinancialAccountingMonthEndClosedLoopIntegrationTest {
    private static final long TENANT = 88192L;
    private static final int YEAR = 2031;
    private static final int MONTH = 1;
    private static final long BANK_ENDPOINT = 8819201L;

    @Autowired FinancialCloseService closeService;
    @Autowired AccountingEntryService entryService;
    @Autowired FinanceIntegrationService integrationService;
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

    @Test
    void workspaceDtosPreserveCheckAndLedgerFacts() {
        PeriodCheckVO check = PeriodCheckVO.from(Map.of(
                "id", 9007199254740993L, "check_type", "TRIAL_BALANCE",
                "check_status", "PASS", "issue_count", 0));
        ReconciliationVO reconciliation = ReconciliationVO.from(Map.of(
                "id", 9007199254740992L, "account_type", "AR", "status", "MATCHED",
                "expected_amount", new BigDecimal("123.40"), "ledger_amount", new BigDecimal("123.40"),
                "difference_amount", BigDecimal.ZERO));

        assertEquals("9007199254740993", check.id());
        assertEquals("PASS", check.status());
        assertEquals("123.40", reconciliation.actualAmount());
    }

    @Test
    void closeRerunsChecksAndRejectsSameSecondFactChanges() {
        closeService.ensurePeriod(YEAR, MONTH);
        Map<String,Object> checked = closeService.runChecks(YEAR, MONTH);
        assertEquals(0, ((Number)((Map<?,?>)checked.get("period")).get("issue_count")).intValue());

        closeService.createAdjustment(adjustment("检查后新增"));
        Object checkedAt = jdbc.queryForObject(
                "SELECT last_check_at FROM finance_period WHERE tenant_id=? AND fiscal_year=? AND fiscal_month=?",
                Object.class, TENANT, YEAR, MONTH);
        jdbc.update("UPDATE accounting_entry SET updated_at=? WHERE tenant_id=?", checkedAt, TENANT);

        BusinessException error = assertThrows(BusinessException.class,
                () -> closeService.close(YEAR, MONTH, "不得使用陈旧检查关账"));
        assertEquals("FINANCE_PERIOD_ISSUES_EXIST", error.getCode());
    }

    @Test
    void closingPeriodAndCreatingAdjustmentCannotBothSucceed() throws Exception {
        closeService.ensurePeriod(YEAR, MONTH);
        Map<String,Object> checked = closeService.runChecks(YEAR, MONTH);
        assertEquals(0, ((Number)((Map<?,?>)checked.get("period")).get("issue_count")).intValue());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> closed = pool.submit(() -> {
                TestUserContext.setUser(TENANT, 102L, "closer", List.of("FINANCE"));
                try {
                    start.await();
                    closeService.close(YEAR, MONTH, "并发关账");
                    return true;
                } catch (BusinessException exception) {
                    assertEquals("FINANCE_PERIOD_ISSUES_EXIST", exception.getCode());
                    return false;
                } finally {
                    TestUserContext.clear();
                }
            });
            Future<Boolean> adjusted = pool.submit(() -> {
                TestUserContext.setUser(TENANT, 103L, "accountant", List.of("FINANCE"));
                try {
                    start.await();
                    closeService.createAdjustment(adjustment("并发调整"));
                    return true;
                } catch (BusinessException exception) {
                    assertEquals("FINANCE_PERIOD_CLOSED", exception.getCode());
                    return false;
                } finally {
                    TestUserContext.clear();
                }
            });
            start.countDown();
            boolean closeSucceeded = closed.get();
            boolean adjustmentSucceeded = adjusted.get();
            assertNotEquals(closeSucceeded, adjustmentSucceeded);
            if (closeSucceeded) {
                assertEquals(0, jdbc.queryForObject(
                        "SELECT COUNT(*) FROM accounting_entry WHERE tenant_id=? AND entry_date BETWEEN ? AND ?",
                        Integer.class, TENANT, LocalDate.of(YEAR, MONTH, 1),
                        LocalDate.of(YEAR, MONTH, 1).withDayOfMonth(31)));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void closingPeriodAndIngestingBankReceiptCannotBothSucceed() throws Exception {
        closeService.ensurePeriod(YEAR, MONTH);
        jdbc.update("""
                INSERT INTO finance_integration_endpoint(
                  id,tenant_id,endpoint_type,endpoint_code,endpoint_name,enabled_flag,config_json,version,created_at,updated_at)
                VALUES(?,?,'BANK','MONTH-END-BANK','月结并发银行',1,'{}',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, BANK_ENDPOINT, TENANT);
        Map<String,Object> checked = closeService.runChecks(YEAR, MONTH);
        assertEquals(0, ((Number)((Map<?,?>)checked.get("period")).get("issue_count")).intValue());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> closed = pool.submit(() -> {
                TestUserContext.setUser(TENANT, 102L, "closer", List.of("FINANCE"));
                try {
                    start.await();
                    closeService.close(YEAR, MONTH, "银行回单并发关账");
                    return true;
                } catch (BusinessException exception) {
                    assertEquals("FINANCE_PERIOD_ISSUES_EXIST", exception.getCode());
                    return false;
                } finally {
                    TestUserContext.clear();
                }
            });
            Future<Boolean> ingested = pool.submit(() -> {
                TestUserContext.setUser(TENANT, 103L, "bank", List.of("FINANCE"));
                try {
                    start.await();
                    integrationService.ingestBankReceipt(new BankReceiptRequest(
                            BANK_ENDPOINT, "MONTH-END-TXN", "****2031",
                            LocalDateTime.of(YEAR, MONTH, 15, 10, 0), "OUT",
                            new BigDecimal("100.00"), "供应商", "并发回单",
                            null, null, null, null, List.of(), Map.of("source", "test")));
                    return true;
                } catch (BusinessException exception) {
                    assertEquals("FINANCE_PERIOD_CLOSED", exception.getCode());
                    return false;
                } finally {
                    TestUserContext.clear();
                }
            });
            start.countDown();
            assertNotEquals(closed.get(), ingested.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void directBankReceiptMatchRefusesClosedPeriod() {
        closeService.ensurePeriod(YEAR, MONTH);
        closeService.runChecks(YEAR, MONTH);
        closeService.close(YEAR, MONTH, "先关账");
        jdbc.update("""
                INSERT INTO finance_integration_endpoint(
                  id,tenant_id,endpoint_type,endpoint_code,endpoint_name,enabled_flag,config_json,version,created_at,updated_at)
                VALUES(?,?,'BANK','CLOSED-BANK','已关账银行',1,'{}',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, BANK_ENDPOINT, TENANT);
        jdbc.update("""
                INSERT INTO bank_receipt(
                  id,tenant_id,endpoint_id,bank_txn_no,transaction_time,direction,amount,match_status,raw_payload_json,created_at)
                VALUES(?,?,?,?,?,'OUT',100,'UNMATCHED','{}',CURRENT_TIMESTAMP)
                """, BANK_ENDPOINT + 1, TENANT, BANK_ENDPOINT, "CLOSED-TXN",
                LocalDateTime.of(YEAR, MONTH, 15, 10, 0));

        BusinessException error = assertThrows(BusinessException.class,
                () -> integrationService.autoMatchReceipt(BANK_ENDPOINT + 1));
        assertEquals("FINANCE_PERIOD_CLOSED", error.getCode());
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
        jdbc.update("DELETE FROM bank_receipt WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM finance_integration_endpoint WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM finance_account_reconciliation WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM finance_period_check WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM finance_period WHERE tenant_id=?", TENANT);
    }
}
