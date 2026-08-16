package com.cgcpms.financeclose;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.service.AccountingEntryService;
import com.cgcpms.audit.service.MandatoryAuditService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class FinancialAccountingMonthEndClosedLoopIntegrationTest {
    private static final long TENANT = 88192L;
    private static final int YEAR = 2025;
    private static final int MONTH = 1;
    private static final long BANK_ENDPOINT = 8819201L;

    @Autowired FinancialCloseService closeService;
    @Autowired AccountingEntryService entryService;
    @Autowired FinanceIntegrationService integrationService;
    @Autowired MandatoryAuditService mandatoryAuditService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

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
        String closeCommand = jdbc.queryForObject("""
                SELECT command_key FROM finance_audit_event
                 WHERE tenant_id=? AND event_type='FINANCE_PERIOD_CLOSED' AND business_id=?
                """, String.class, TENANT, ((Number) period.get("id")).longValue());
        assertTrue(closeCommand.startsWith("CLOSE:" + period.get("id") + ":V"));

        BusinessException locked = assertThrows(BusinessException.class, () -> closeService.createAdjustment(adjustment("锁账后调整")));
        assertEquals("FINANCE_PERIOD_CLOSED", locked.getCode());
        Map<String,Object> reopened = closeService.reopen(YEAR, MONTH, "审计调整");
        assertEquals("REOPENED", ((Map<?,?>)reopened.get("period")).get("status"));
        Map<String,Object> reopenAudit = jdbc.queryForMap("""
                SELECT command_key,payload_json FROM finance_audit_event
                 WHERE tenant_id=? AND event_type='FINANCE_PERIOD_REOPENED' AND business_id=?
                """, TENANT, ((Number) period.get("id")).longValue());
        assertTrue(String.valueOf(reopenAudit.get("command_key")).startsWith("REOPEN:" + period.get("id") + ":V"));
        assertTrue(String.valueOf(reopenAudit.get("payload_json")).contains("审计调整"));
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
    void monthEndCheckBlocksUnallocatedOverheadUntilClearingExists() {
        long subjectId = 8819291L;
        long ruleId = 8819292L;
        long sourceId = 8819293L;
        long clearingId = 8819294L;
        long projectId = 8819295L;
        long secondSourceId = 8819393L;
        long secondClearingId = 8819394L;
        long secondProjectId = 8819395L;
        closeService.ensurePeriod(YEAR, MONTH);
        jdbc.update("""
                INSERT INTO pm_project
                (id,tenant_id,project_code,project_name,status,approval_status,deleted_flag)
                VALUES (?,?,'FIN-CLOSE-OH','月结间接费完整性项目','ACTIVE','APPROVED',0)
                """, projectId, TENANT);
        jdbc.update("""
                INSERT INTO pm_project
                (id,tenant_id,project_code,project_name,status,approval_status,deleted_flag)
                VALUES (?,?,'FIN-CLOSE-OH-NEG','月结间接费负向项目','ACTIVE','APPROVED',0)
                """, secondProjectId, TENANT);
        jdbc.update("""
                INSERT INTO cost_subject
                (id,tenant_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,deleted_flag)
                VALUES (?,?,'5401.04.99','待分摊间接费','OVERHEAD','COST',3,1,'ENABLE',0)
                """, subjectId, TENANT);
        jdbc.update("""
                INSERT INTO overhead_allocation_rule
                (id,tenant_id,cost_subject_id,allocation_basis,allocation_cycle,status,deleted_flag)
                VALUES (?,?,?,'CONTRACT_AMOUNT','MONTHLY','ENABLE',0)
                """, ruleId, TENANT, subjectId);
        jdbc.update("""
                INSERT INTO cost_item
                (id,tenant_id,project_id,cost_subject_id,classification_status,recognition_role,cost_type,amount,tax_amount,
                 amount_without_tax,source_type,source_id,source_item_id,cost_date,cost_status,generated_flag,deleted_flag)
                VALUES (?,?,?,?,'CLASSIFIED','ACTUAL','OVERHEAD',100,0,100,'MANUAL_COST',?,?,?,'CONFIRMED',1,0)
                """, sourceId, TENANT, projectId, subjectId, sourceId, sourceId, LocalDate.of(YEAR, MONTH, 31));
        jdbc.update("""
                INSERT INTO cost_item
                (id,tenant_id,project_id,cost_subject_id,classification_status,recognition_role,cost_type,amount,tax_amount,
                 amount_without_tax,source_type,source_id,source_item_id,cost_date,cost_status,generated_flag,deleted_flag)
                VALUES (?,?,?,?,'CLASSIFIED','ACTUAL','OVERHEAD',-100,0,-100,'MANUAL_COST',?,?,?,'CONFIRMED',1,0)
                """, secondSourceId, TENANT, secondProjectId, subjectId,
                secondSourceId, secondSourceId, LocalDate.of(YEAR, MONTH, 31));

        Map<String, Object> blocked = closeService.runChecks(YEAR, MONTH);
        Map<?, ?> pending = ((List<Map<String, Object>>) blocked.get("checks")).stream()
                .filter(row -> "OVERHEAD_ALLOCATION_COMPLETENESS".equals(row.get("check_type")))
                .findFirst().orElseThrow();
        assertEquals(2, ((Number) pending.get("issue_count")).intValue());

        jdbc.update("""
                INSERT INTO cost_item
                (id,tenant_id,project_id,cost_subject_id,classification_status,recognition_role,original_cost_item_id,cost_type,
                 amount,tax_amount,amount_without_tax,source_type,source_id,source_item_id,cost_date,cost_status,generated_flag,deleted_flag)
                VALUES (?,?,?,?,'REVERSAL','ACTUAL',?,'OVERHEAD_CLEARING',-100,0,-100,
                        'OVERHEAD_ALLOCATION_CLEARING',?,?,?,'CONFIRMED',1,0)
                """, clearingId, TENANT, projectId, subjectId, sourceId, ruleId, sourceId, LocalDate.of(YEAR, MONTH, 31));
        jdbc.update("""
                INSERT INTO cost_item
                (id,tenant_id,project_id,cost_subject_id,classification_status,recognition_role,original_cost_item_id,cost_type,
                 amount,tax_amount,amount_without_tax,source_type,source_id,source_item_id,cost_date,cost_status,generated_flag,deleted_flag)
                VALUES (?,?,?,?,'REVERSAL','ACTUAL',?,'OVERHEAD_CLEARING',100,0,100,
                        'OVERHEAD_ALLOCATION_CLEARING',?,?,?,'CONFIRMED',1,0)
                """, secondClearingId, TENANT, secondProjectId, subjectId, secondSourceId,
                ruleId, secondSourceId, LocalDate.of(YEAR, MONTH, 31));

        Map<String, Object> cleared = closeService.runChecks(YEAR, MONTH);
        Map<?, ?> complete = ((List<Map<String, Object>>) cleared.get("checks")).stream()
                .filter(row -> "OVERHEAD_ALLOCATION_COMPLETENESS".equals(row.get("check_type")))
                .findFirst().orElseThrow();
        assertEquals(0, ((Number) complete.get("issue_count")).intValue());
    }

    @Test
    void unclassifiedCostFactBlocksPeriodCloseChecks() {
        long projectId = 8819296L;
        long factId = 8819297L;
        closeService.ensurePeriod(YEAR, MONTH);
        jdbc.update("""
                INSERT INTO pm_project
                (id,tenant_id,project_code,project_name,status,approval_status,deleted_flag)
                VALUES (?,?,'FIN-CLOSE-UNCLASSIFIED','月结待归类成本项目','ACTIVE','APPROVED',0)
                """, projectId, TENANT);
        jdbc.update("""
                INSERT INTO cost_item
                (id,tenant_id,project_id,classification_status,recognition_role,cost_type,amount,tax_amount,
                 amount_without_tax,source_type,source_id,source_item_id,cost_date,cost_status,generated_flag,deleted_flag)
                VALUES (?,?,?,'UNCLASSIFIED','ACTUAL','MATERIAL',100,0,100,'MAT_RECEIPT',?,?,?,'CONFIRMED',1,0)
                """, factId, TENANT, projectId, factId, factId, LocalDate.of(YEAR, MONTH, 15));

        Map<String, Object> blocked = closeService.runChecks(YEAR, MONTH);
        Map<?, ?> pending = ((List<Map<String, Object>>) blocked.get("checks")).stream()
                .filter(row -> "UNCLASSIFIED_COST_FACT_COMPLETENESS".equals(row.get("check_type")))
                .findFirst().orElseThrow();
        assertEquals(1, ((Number) pending.get("issue_count")).intValue());

        jdbc.update("UPDATE cost_item SET classification_status='CLASSIFIED' WHERE tenant_id=? AND id=?", TENANT, factId);
        Map<String, Object> classified = closeService.runChecks(YEAR, MONTH);
        Map<?, ?> complete = ((List<Map<String, Object>>) classified.get("checks")).stream()
                .filter(row -> "UNCLASSIFIED_COST_FACT_COMPLETENESS".equals(row.get("check_type")))
                .findFirst().orElseThrow();
        assertEquals(0, ((Number) complete.get("issue_count")).intValue());
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
    void arAndApReconciliationUsePostedLedgerInsteadOfSelfComparingSubledgers() {
        closeService.ensurePeriod(YEAR, MONTH);
        AccountingEntry adjustment = closeService.createAdjustment(new AdjustmentRequest(
                LocalDate.of(YEAR, MONTH, 15), null, null, "制造独立账差",
                List.of(
                        new AdjustmentLine("DEBIT", "1122", "应收账款", null,
                                new BigDecimal("0.01"), "总账应收差异"),
                        new AdjustmentLine("CREDIT", "2202.04", "应付分包款", null,
                                new BigDecimal("0.01"), "总账应付差异"))));
        TestUserContext.setUser(TENANT, 102L, "reviewer", List.of("FINANCE"));
        entryService.review(adjustment.getId(), true, "差异测试复核");
        TestUserContext.setUser(TENANT, 103L, "poster", List.of("FINANCE"));
        entryService.post(adjustment.getId());

        closeService.runChecks(YEAR, MONTH);

        assertEquals("EXCEPTION", jdbc.queryForObject(
                "SELECT status FROM finance_account_reconciliation WHERE tenant_id=? AND account_type='AR'",
                String.class, TENANT));
        assertEquals(new BigDecimal("0.01"), jdbc.queryForObject(
                "SELECT difference_amount FROM finance_account_reconciliation WHERE tenant_id=? AND account_type='AR'",
                BigDecimal.class, TENANT));
        assertEquals("EXCEPTION", jdbc.queryForObject(
                "SELECT status FROM finance_account_reconciliation WHERE tenant_id=? AND account_type='AP'",
                String.class, TENANT));
    }

    @Test
    void mandatoryAuditUsesCanonicalPayloadAndChecksMissingOrDamagedFacts() {
        long businessId = 8819299L;
        Map<String,Object> first = new LinkedHashMap<>();
        first.put("z", 1);
        first.put("a", 2);
        Map<String,Object> replay = new LinkedHashMap<>();
        replay.put("a", 2);
        replay.put("z", 1);

        inTransaction(() -> mandatoryAuditService.finance("PAYMENT_COMPLETED", "PAY_RECORD", businessId,
                null, "CANONICAL-1", first));
        mandatoryAuditService.verifyFinance("PAYMENT_COMPLETED", "PAY_RECORD", businessId,
                "CANONICAL-1", replay);
        assertEquals("{\"a\":2,\"z\":1}", jdbc.queryForObject("""
                SELECT payload_json FROM finance_audit_event
                 WHERE tenant_id=? AND event_type='PAYMENT_COMPLETED' AND business_id=?
                """, String.class, TENANT, businessId));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM mandatory_audit_expectation
                 WHERE tenant_id=? AND audit_domain='FINANCE' AND business_id=?
                """, Integer.class, TENANT, businessId));

        jdbc.update("UPDATE finance_audit_event SET payload_hash=? WHERE tenant_id=? AND business_id=?",
                "0".repeat(64), TENANT, businessId);
        BusinessException damaged = assertThrows(BusinessException.class,
                () -> mandatoryAuditService.verifyFinance("PAYMENT_COMPLETED", "PAY_RECORD", businessId,
                        "CANONICAL-1", replay));
        assertEquals("MANDATORY_AUDIT_INTEGRITY_VIOLATION", damaged.getCode());
        closeService.ensurePeriod(YEAR, MONTH);
        closeService.runChecks(YEAR, MONTH);
        assertEquals(1, jdbc.queryForObject("""
                SELECT issue_count FROM finance_period_check
                 WHERE tenant_id=? AND check_type='MANDATORY_AUDIT_INTEGRITY'
                """, Integer.class, TENANT));

        jdbc.update("DELETE FROM finance_audit_event WHERE tenant_id=? AND business_id=?", TENANT, businessId);
        jdbc.update("DELETE FROM mandatory_audit_expectation WHERE tenant_id=? AND business_id=?", TENANT, businessId);
        jdbc.update("UPDATE finance_period SET closed_at=CURRENT_TIMESTAMP,reopened_at=CURRENT_TIMESTAMP WHERE tenant_id=?", TENANT);
        Long periodId = jdbc.queryForObject("SELECT id FROM finance_period WHERE tenant_id=?", Long.class, TENANT);
        inTransaction(() -> mandatoryAuditService.finance("FINANCE_PERIOD_CLOSED", "FINANCE_PERIOD", periodId,
                null, "CLOSE:" + periodId + ":V1", Map.of("period", "wrong-key")));
        inTransaction(() -> mandatoryAuditService.finance("FINANCE_PERIOD_REOPENED", "FINANCE_PERIOD", periodId,
                null, "REOPEN:" + periodId + ":V2", Map.of("period", "wrong-key")));
        jdbc.update("UPDATE finance_audit_event SET command_key='WRONG-CLOSE' WHERE tenant_id=? AND event_type='FINANCE_PERIOD_CLOSED'", TENANT);
        jdbc.update("UPDATE finance_audit_event SET command_key='WRONG-REOPEN' WHERE tenant_id=? AND event_type='FINANCE_PERIOD_REOPENED'", TENANT);
        assertEquals(2, mandatoryAuditService.inspectTenant().missingEvents());
        assertEquals(2, mandatoryAuditService.inspectTenant().hashMismatches());
        closeService.runChecks(YEAR, MONTH);
        assertEquals(4, jdbc.queryForObject("""
                SELECT issue_count FROM finance_period_check
                 WHERE tenant_id=? AND check_type='MANDATORY_AUDIT_INTEGRITY'
                """, Integer.class, TENANT));
    }

    @Test
    void mandatoryAuditDenominatorDoesNotFabricateLegacySuccessEvidence() {
        closeService.ensurePeriod(YEAR, MONTH);
        int missingBefore = mandatoryAuditService.inspectTenant().missingEvents();
        jdbc.update("UPDATE finance_period SET closed_at=CURRENT_TIMESTAMP WHERE tenant_id=?", TENANT);

        assertEquals(missingBefore, mandatoryAuditService.inspectTenant().missingEvents());
    }

    @Test
    void repeatedCloseAndReopenKeepEveryCommandAsExactAuditDenominator() {
        Map<String,Object> period = closeService.ensurePeriod(YEAR, MONTH);
        long periodId = ((Number) period.get("id")).longValue();
        closeService.runChecks(YEAR, MONTH);
        closeService.close(YEAR, MONTH, "首次关账");
        closeService.reopen(YEAR, MONTH, "重新核对");
        closeService.runChecks(YEAR, MONTH);
        closeService.close(YEAR, MONTH, "再次关账");

        assertEquals(3, jdbc.queryForObject("""
                SELECT COUNT(*) FROM mandatory_audit_expectation
                 WHERE tenant_id=? AND business_type='FINANCE_PERIOD' AND business_id=?
                """, Integer.class, TENANT, periodId));
        List<String> closeCommands = jdbc.queryForList("""
                SELECT command_key FROM finance_audit_event
                 WHERE tenant_id=? AND event_type='FINANCE_PERIOD_CLOSED' AND business_id=?
                 ORDER BY command_key
                """, String.class, TENANT, periodId);
        assertEquals(2, closeCommands.size());

        jdbc.update("""
                DELETE FROM finance_audit_event
                 WHERE tenant_id=? AND event_type='FINANCE_PERIOD_CLOSED' AND business_id=? AND command_key=?
                """, TENANT, periodId, closeCommands.getFirst());
        assertEquals(1, mandatoryAuditService.inspectTenant().missingEvents());
    }

    @Test
    void mandatoryExpectationCollisionRollsBackNewEvent() {
        long businessId = 8819298L;
        jdbc.update("""
                INSERT INTO mandatory_audit_expectation
                    (id,tenant_id,audit_domain,event_type,business_type,business_id,command_key,expected_hash)
                VALUES(?,?,?,?,?,?,?,?)
                """, businessId, TENANT, "FINANCE", "PAYMENT_COMPLETED", "PAY_RECORD",
                businessId, "COLLISION", "0".repeat(64));

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> inTransaction(() -> mandatoryAuditService.finance("PAYMENT_COMPLETED", "PAY_RECORD",
                        businessId, null, "COLLISION", Map.of("amount", 1))));

        assertEquals("MANDATORY_AUDIT_EXPECTATION_CONFLICT", conflict.getCode());
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM finance_audit_event
                 WHERE tenant_id=? AND event_type='PAYMENT_COMPLETED' AND business_id=?
                """, Integer.class, TENANT, businessId));
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

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private void cleanup() {
        jdbc.update("DELETE FROM cost_item WHERE tenant_id=? AND original_cost_item_id IS NOT NULL", TENANT);
        jdbc.update("DELETE FROM cost_item WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM overhead_allocation_run WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM overhead_allocation_rule WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM cost_subject WHERE tenant_id=? AND subject_code='5401.04.99'", TENANT);
        jdbc.update("DELETE FROM pm_project WHERE tenant_id=? AND project_code IN ('FIN-CLOSE-OH','FIN-CLOSE-UNCLASSIFIED')", TENANT);
        jdbc.update("DELETE FROM mandatory_audit_expectation WHERE tenant_id=?", TENANT);
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
