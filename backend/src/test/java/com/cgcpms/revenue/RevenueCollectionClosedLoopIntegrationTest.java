package com.cgcpms.revenue;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.revenue.dto.RevenueOperationsModels.*;
import com.cgcpms.revenue.service.RevenueOperationsService;
import com.cgcpms.revenue.service.RevenueAdvancedService;
import com.cgcpms.financeops.dto.FinanceOperationsModels.BankReceiptRequest;
import com.cgcpms.financeops.service.FinanceIntegrationService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class RevenueCollectionClosedLoopIntegrationTest {
    private static final long PROJECT = 99171001L;
    private static final long CUSTOMER = 99171002L;
    private static final long CONTRACT = 99171003L;
    private static final long REVENUE = 99171004L;
    private static final long ACCOUNT = 99171005L;
    private static final long ENDPOINT = 99171006L;

    @Autowired RevenueOperationsService service;
    @Autowired RevenueAdvancedService advanced;
    @Autowired FinanceIntegrationService financeIntegration;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        UserContext.set(Jwts.claims().subject("admin").add("userId",1L).add("username","admin")
                .add("tenantId",0L).add("roleCodes", List.of("ADMIN")).build());
        cleanup();
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'REV-IT-P','收入闭环测试项目','ACTIVE',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", PROJECT);
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_at,updated_at,deleted_flag) VALUES(?,0,'REV-IT-CUSTOMER','测试业主','CUSTOMER','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", CUSTOMER);
        jdbc.update("INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,contract_status,approval_status,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,'REV-IT-C','业主总包合同','MAIN',?,?,10000,10000,0,'PERFORMING','APPROVED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", CONTRACT, PROJECT, CUSTOMER, CUSTOMER);
        jdbc.update("INSERT INTO contract_revenue(id,tenant_id,project_id,contract_id,revenue_code,revenue_date,progress_percent,revenue_amount,revenue_tax,revenue_amount_with_tax,billed_amount,billed_tax,approval_status,formula_version,attachment_count,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,?,'REV-IT-R',CURRENT_DATE,50,8000,0,8000,0,0,'APPROVED','REVENUE_PROGRESS_V1',1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", REVENUE, PROJECT, CONTRACT);
        jdbc.update("INSERT INTO fund_account(id,tenant_id,account_code,account_name,account_type,opening_date,opening_balance,enabled_flag,version,created_at,updated_at,deleted_flag) VALUES(?,0,'REV-IT-A','回款账户','BANK',CURRENT_DATE,1000,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", ACCOUNT);
        jdbc.update("INSERT INTO finance_integration_endpoint(id,tenant_id,endpoint_type,endpoint_code,endpoint_name,enabled_flag,version,created_at,updated_at) VALUES(?,0,'ERP','REV-IT-ENDPOINT','收入集成测试端点',1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",ENDPOINT);
    }

    @AfterEach
    void teardown() { cleanup(); UserContext.clear(); }

    @Test
    void fullChainCreatesReceivableInvoiceCollectionJournalEntryAndTrace() {
        var settlement = service.createSettlement(new OwnerSettlementRequest(PROJECT,CONTRACT,REVENUE,"2026-07",
                LocalDate.now(),new BigDecimal("1000"),BigDecimal.ZERO,new BigDecimal("100"),
                LocalDate.now().plusDays(30),CUSTOMER,1,"业主确认"));
        long settlementId = ((Number) settlement.get("id")).longValue();
        jdbc.update("UPDATE owner_settlement SET status='PENDING' WHERE id=?", settlementId);
        service.onSettlementApproved(settlementId);

        var receivables = service.receivables(PROJECT,null);
        assertEquals(2, receivables.size());
        long progressReceivable = receivables.stream().filter(r -> "PROGRESS".equals(r.get("receivable_type")))
                .map(r -> ((Number)r.get("id")).longValue()).findFirst().orElseThrow();

        service.createSalesInvoice(new SalesInvoiceRequest(PROJECT,CONTRACT,CUSTOMER,"INV-CODE","INV-REV-001","SPECIAL",
                LocalDate.now(),new BigDecimal("600"),BigDecimal.ZERO,1,
                List.of(new AmountAllocation(progressReceivable,new BigDecimal("600"))),"销项发票"));

        var collection = service.createCollection(new CollectionRequest(PROJECT,CONTRACT,CUSTOMER,ACCOUNT,"BANK-REV-001",
                LocalDateTime.now(),new BigDecimal("600"),"测试业主",1,
                List.of(new AmountAllocation(progressReceivable,new BigDecimal("600"))),"进度款回款"));
        long collectionId = ((Number) collection.get("id")).longValue();
        var duplicate = service.createCollection(new CollectionRequest(PROJECT,CONTRACT,CUSTOMER,ACCOUNT,"BANK-REV-001",
                LocalDateTime.now(),new BigDecimal("600"),"测试业主",1,List.of(),"重复回调"));
        assertEquals(collectionId, ((Number) duplicate.get("id")).longValue());

        Long journalId = jdbc.queryForObject("SELECT id FROM cash_journal_entry WHERE collection_record_id=?", Long.class, collectionId);
        assertNotNull(journalId);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM accounting_entry WHERE collection_record_id=?", Integer.class, collectionId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM cash_journal_entry WHERE collection_record_id=?", Integer.class, collectionId));
        assertEquals(new BigDecimal("300.00"), jdbc.queryForObject("SELECT outstanding_amount FROM account_receivable WHERE id=?", BigDecimal.class, progressReceivable));
        var trace = service.traceByCashJournal(journalId);
        assertEquals(collectionId, ((Number)((java.util.Map<?,?>)trace.get("collection")).get("id")).longValue());
        assertFalse(((List<?>) trace.get("settlements")).isEmpty());
        assertFalse(((List<?>) trace.get("revenues")).isEmpty());
        assertTrue(trace.containsKey("approvalInstances"));
        assertTrue(trace.containsKey("approvalRecords"));
        assertFalse(((List<?>) trace.get("salesInvoices")).isEmpty());
    }

    @Test
    void rejectsCrossContextAndOverAllocationWithoutSideEffects() {
        var settlement = service.createSettlement(new OwnerSettlementRequest(PROJECT,CONTRACT,REVENUE,"2026-08",
                LocalDate.now(),new BigDecimal("500"),BigDecimal.ZERO,BigDecimal.ZERO,
                LocalDate.now().plusDays(15),CUSTOMER,1,null));
        long id = ((Number) settlement.get("id")).longValue();
        jdbc.update("UPDATE owner_settlement SET status='PENDING' WHERE id=?", id);
        service.onSettlementApproved(id);
        long ar = ((Number)service.receivables(PROJECT,null).get(0).get("id")).longValue();
        assertThrows(BusinessException.class, () -> service.createCollection(new CollectionRequest(PROJECT,CONTRACT,CUSTOMER,ACCOUNT,"BANK-OVER",
                LocalDateTime.now(),new BigDecimal("600"),"测试业主",1,List.of(new AmountAllocation(ar,new BigDecimal("600"))),null)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM collection_record WHERE external_txn_no='BANK-OVER'", Integer.class));
    }

    @Test
    void p1ToP3OperationsAreAuditableAndIdempotent() {
        long ar=createReceivable("2026-09",new BigDecimal("500"));
        advanced.creditReceivable(ar,new ReceivableCreditRequest(new BigDecimal("50"),"业主核减","CREDIT-REV-1"));
        advanced.createSchedule(new CollectionScheduleRequest(PROJECT,CONTRACT,ar,LocalDate.now().plusDays(7),new BigDecimal("200"),3,"催收计划"));
        var invoice=service.createSalesInvoice(new SalesInvoiceRequest(PROJECT,CONTRACT,CUSTOMER,null,"INV-REV-P2","SPECIAL",LocalDate.now(),new BigDecimal("200"),BigDecimal.ZERO,1,List.of(new AmountAllocation(ar,new BigDecimal("200"))),null));
        long invoiceId=((Number)invoice.get("id")).longValue();
        var collection=service.createCollection(new CollectionRequest(PROJECT,CONTRACT,CUSTOMER,ACCOUNT,"BANK-REV-P1",LocalDateTime.now(),new BigDecimal("200"),"测试业主",1,List.of(new AmountAllocation(ar,new BigDecimal("200"))),null));
        long collectionId=((Number)collection.get("id")).longValue();
        advanced.reverseCollection(collectionId,new CollectionReverseRequest("银行退回","REV-COL-1"));
        var second=advanced.reverseCollection(collectionId,new CollectionReverseRequest("重复回调","REV-COL-1"));
        assertEquals(collectionId,((Number)second.get("collection_id")).longValue());
        assertEquals("REVERSED",jdbc.queryForObject("SELECT status FROM collection_record WHERE id=?",String.class,collectionId));

        assertNotNull(advanced.reconcile(LocalDate.of(2099,1,1)).get("id"));
        assertNotNull(advanced.rebuildSnapshot(PROJECT,LocalDate.now(),"TEST").get("id"));
        var review=advanced.createInvoiceReview(new SalesInvoiceReviewRequest(invoiceId,new BigDecimal("0.9800"),java.util.Map.of("invoiceNo","INV-REV-P2"),java.util.Map.of()));
        advanced.decideReview(((Number)review.get("id")).longValue(),new ReviewDecisionRequest("APPROVED","字段一致"));
        assertNotNull(advanced.previewImport(new RevenueImportRequest("COLLECTION",PROJECT,"collections.xlsx","hash-rev-1",List.of(new RevenueImportRow(1,java.util.Map.of("amount","100"))))).get("id"));
        assertNotNull(advanced.createForecast(PROJECT,CONTRACT,LocalDate.now().plusDays(30),"BASE",new BigDecimal("300"),new BigDecimal("0.8"),"RECEIVABLE",ar).get("id"));
        assertNotNull(advanced.refreshCustomerCredit(CUSTOMER).get("id"));
        assertNotNull(advanced.enqueueIntegration(new RevenueIntegrationRequest(ENDPOINT,"AR_SYNC","ACCOUNT_RECEIVABLE",ar,"SYNC-REV-1",java.util.Map.of("receivableId",ar))).get("id"));
        assertFalse(advanced.aging(PROJECT).isEmpty());
        assertTrue(advanced.exportAudit(PROJECT).length>0);
    }

    @Test
    void concurrentDuplicateBankCallbacksCreateOnlyOneCollectionJournalAndEntry() throws Exception {
        long ar = createReceivable("2026-10", new BigDecimal("300"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Long> callback = () -> {
            UserContext.set(Jwts.claims().subject("admin").add("userId",1L).add("username","admin")
                    .add("tenantId",0L).add("roleCodes", List.of("ADMIN")).build());
            try {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                var result = service.createCollection(new CollectionRequest(PROJECT,CONTRACT,CUSTOMER,ACCOUNT,"BANK-REV-CONCURRENT",
                        LocalDateTime.now(),new BigDecimal("100"),"测试业主",1,
                        List.of(new AmountAllocation(ar,new BigDecimal("100"))),"并发银行回调"));
                return ((Number) result.get("id")).longValue();
            } finally {
                UserContext.clear();
            }
        };
        try {
            Future<Long> first = pool.submit(callback);
            Future<Long> second = pool.submit(callback);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM collection_record WHERE external_txn_no='BANK-REV-CONCURRENT'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM cash_journal_entry WHERE collection_record_id IN(SELECT id FROM collection_record WHERE external_txn_no='BANK-REV-CONCURRENT')", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM accounting_entry WHERE collection_record_id IN(SELECT id FROM collection_record WHERE external_txn_no='BANK-REV-CONCURRENT')", Integer.class));
    }

    @Test
    void inboundBankReceiptMatchesCollectionAmountDirectionAndCashJournal() {
        long ar = createReceivable("2026-11", new BigDecimal("300"));
        var collection = service.createCollection(new CollectionRequest(PROJECT,CONTRACT,CUSTOMER,ACCOUNT,"BANK-REV-IN-MATCH",
                LocalDateTime.now(),new BigDecimal("200"),"测试业主",1,
                List.of(new AmountAllocation(ar,new BigDecimal("200"))),"银行收入对账"));
        long collectionId = ((Number) collection.get("id")).longValue();
        jdbc.update("UPDATE finance_integration_endpoint SET endpoint_type='BANK' WHERE id=?", ENDPOINT);

        var receipt = financeIntegration.ingestBankReceipt(new BankReceiptRequest(ENDPOINT,"BANK-REV-IN-MATCH",null,
                LocalDateTime.now(),"IN",new BigDecimal("200"),"测试业主","工程回款",
                null,null,null,null,List.of(),Map.of("source","test")));
        assertEquals("MATCHED", receipt.get("match_status"));
        assertEquals(collectionId, ((Number)receipt.get("collection_record_id")).longValue());
        assertNull(receipt.get("pay_record_id"));
        assertNotNull(receipt.get("cash_journal_id"));
    }

    private long createReceivable(String period,BigDecimal amount){
        var settlement=service.createSettlement(new OwnerSettlementRequest(PROJECT,CONTRACT,REVENUE,period,LocalDate.now(),amount,BigDecimal.ZERO,BigDecimal.ZERO,LocalDate.now().plusDays(15),CUSTOMER,1,null));
        long id=((Number)settlement.get("id")).longValue();jdbc.update("UPDATE owner_settlement SET status='PENDING' WHERE id=?",id);service.onSettlementApproved(id);
        return ((Number)service.receivables(PROJECT,null).stream().filter(r->((Number)r.get("settlement_id")).longValue()==id).findFirst().orElseThrow().get("id")).longValue();
    }

    private void cleanup() {
        jdbc.update("DELETE FROM finance_bank_reconciliation WHERE bank_receipt_id IN(SELECT id FROM bank_receipt WHERE endpoint_id=?)",ENDPOINT);
        jdbc.update("DELETE FROM bank_receipt WHERE endpoint_id=?",ENDPOINT);
        jdbc.update("DELETE FROM revenue_external_sync WHERE business_id IN(SELECT id FROM account_receivable WHERE project_id=?)",PROJECT);
        jdbc.update("DELETE FROM finance_integration_message WHERE endpoint_id=?",ENDPOINT);
        jdbc.update("DELETE FROM finance_integration_endpoint WHERE id=?",ENDPOINT);
        jdbc.update("DELETE FROM customer_credit_profile WHERE customer_id=?",CUSTOMER);
        jdbc.update("DELETE FROM collection_forecast WHERE project_id=?",PROJECT);
        jdbc.update("DELETE FROM revenue_audit_event WHERE project_id=?",PROJECT);
        jdbc.update("DELETE FROM revenue_import_row WHERE batch_id IN(SELECT id FROM revenue_import_batch WHERE project_id=?)",PROJECT);
        jdbc.update("DELETE FROM revenue_import_batch WHERE project_id=?",PROJECT);
        jdbc.update("DELETE FROM sales_invoice_review WHERE invoice_id IN(SELECT id FROM sales_invoice WHERE project_id=?)",PROJECT);
        jdbc.update("DELETE FROM revenue_dashboard_snapshot WHERE project_id=?",PROJECT);
        jdbc.update("DELETE FROM revenue_reconciliation_issue WHERE run_id IN(SELECT id FROM revenue_reconciliation_run WHERE tenant_id=0 AND business_date=DATE '2099-01-01')");
        jdbc.update("DELETE FROM revenue_reconciliation_run WHERE tenant_id=0 AND business_date=DATE '2099-01-01'");
        jdbc.update("DELETE FROM collection_schedule WHERE project_id=?",PROJECT);
        jdbc.update("DELETE FROM collection_reversal WHERE collection_id IN(SELECT id FROM collection_record WHERE project_id=?)",PROJECT);
        jdbc.update("DELETE FROM receivable_adjustment WHERE receivable_id IN(SELECT id FROM account_receivable WHERE project_id=?)",PROJECT);
        jdbc.update("DELETE FROM accounting_entry_line WHERE entry_id IN(SELECT id FROM accounting_entry WHERE collection_record_id IN(SELECT id FROM collection_record WHERE project_id=?))", PROJECT);
        jdbc.update("DELETE FROM accounting_entry WHERE collection_record_id IN(SELECT id FROM collection_record WHERE project_id=?)", PROJECT);
        jdbc.update("DELETE FROM cash_journal_change_log WHERE journal_entry_id IN(SELECT id FROM cash_journal_entry WHERE collection_record_id IN(SELECT id FROM collection_record WHERE project_id=?))", PROJECT);
        jdbc.update("DELETE FROM cash_journal_entry WHERE collection_record_id IN(SELECT id FROM collection_record WHERE project_id=?)", PROJECT);
        jdbc.update("DELETE FROM collection_allocation WHERE collection_id IN(SELECT id FROM collection_record WHERE project_id=?)", PROJECT);
        jdbc.update("DELETE FROM collection_record WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM sales_invoice_allocation WHERE invoice_id IN(SELECT id FROM sales_invoice WHERE project_id=?)", PROJECT);
        jdbc.update("DELETE FROM sales_invoice WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM account_receivable WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM owner_settlement WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM contract_revenue WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM fund_account WHERE id=?", ACCOUNT);
        jdbc.update("DELETE FROM ct_contract WHERE id=?", CONTRACT);
        jdbc.update("DELETE FROM md_partner WHERE id=?", CUSTOMER);
        jdbc.update("DELETE FROM pm_project WHERE id=?", PROJECT);
    }
}
