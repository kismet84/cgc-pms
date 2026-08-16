package com.cgcpms.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.entity.FundAccount;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.mapper.FundAccountMapper;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.invoice.entity.InvoicePaymentAllocation;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.InvoicePaymentAllocationMapper;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.invoice.service.InvoiceService;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.payment.service.PayRecordService;
import com.cgcpms.payment.service.PaymentReversalService;
import com.cgcpms.payment.dto.PaymentReversalRequest;
import com.cgcpms.payment.dto.PaymentFailureRequest;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "CGCPMS_M70_MYSQL_CONCURRENCY", matches = "true")
class PaymentMySqlConcurrencyTest {
    private static final long TENANT = 0L;
    private static final long USER = 1L;
    private static final long PROJECT = 970001L;
    private static final long PARTNER_A = 970001L;
    private static final long PARTNER_B = 970002L;
    private static final long CONTRACT = 970001L;
    private static final long CONTRACT_2 = 970002L;
    private static final long APP_1 = 970001L;
    private static final long APP_2 = 970002L;
    private static final long INVOICE_1 = 970001L;
    private static final long INVOICE_2 = 970002L;
    private static final long FUND_ACCOUNT = 970001L;
    private static final long BUDGET = 970020L;
    private static final long BUDGET_LINE = 970021L;
    private static final long CONTRACT_BUDGET_ALLOCATION = 970022L;

    @Autowired private PayApplicationService payApplicationService;
    @Autowired private PayRecordService payRecordService;
    @Autowired private PaymentReversalService reversalService;
    @Autowired private CashJournalService cashJournalService;
    @Autowired private CashJournalEntryMapper cashJournalMapper;
    @Autowired private FundAccountMapper fundAccountMapper;
    @Autowired private PayApplicationMapper payApplicationMapper;
    @Autowired private PayRecordMapper payRecordMapper;
    @Autowired private CtContractMapper contractMapper;
    @Autowired private PmProjectMapper projectMapper;
    @Autowired private MdPartnerMapper partnerMapper;
    @Autowired private InvoiceService invoiceService;
    @Autowired private PayInvoiceMapper invoiceMapper;
    @Autowired private InvoicePaymentAllocationMapper invoiceAllocationMapper;
    @Autowired private SysFileMapper sysFileMapper;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.locations",
                () -> "classpath:db/migration,classpath:db/migration-legacy");
    }

    @BeforeEach
    void seed() {
        cleanupRows();
        TestUserContext.setAdmin(TENANT, USER);

        PmProject project = new PmProject();
        project.setId(PROJECT);
        project.setTenantId(TENANT);
        project.setProjectCode("M70-MYSQL-CONC");
        project.setProjectName("M70 MySQL concurrency");
        project.setProjectType("CONSTRUCTION");
        project.setContractAmount(new BigDecimal("1000000.00"));
        project.setApprovalStatus("APPROVED");
        project.setStatus("ACTIVE");
        projectMapper.insert(project);

        partnerMapper.insert(partner(PARTNER_A, "M70-MYSQL-PA", "Party A"));
        partnerMapper.insert(partner(PARTNER_B, "M70-MYSQL-PB", "Party B"));

        CtContract contract = new CtContract();
        contract.setId(CONTRACT);
        contract.setTenantId(TENANT);
        contract.setProjectId(PROJECT);
        contract.setContractCode("M70-MYSQL-CT");
        contract.setContractName("M70 MySQL contract");
        contract.setContractType("SUB");
        contract.setPartyAId(PARTNER_A);
        contract.setPartyBId(PARTNER_B);
        contract.setContractAmount(new BigDecimal("1000000.00"));
        contract.setCurrentAmount(new BigDecimal("1000000.00"));
        contract.setPaidAmount(BigDecimal.ZERO);
        contract.setTaxRate(BigDecimal.ZERO);
        contract.setApprovalStatus("APPROVED");
        contract.setContractStatus("PERFORMING");
        contractMapper.insert(contract);

        FundAccount account = new FundAccount();
        account.setId(FUND_ACCOUNT);
        account.setTenantId(TENANT);
        account.setAccountCode("M70-MYSQL-FUND");
        account.setAccountName("M70 MySQL fund");
        account.setAccountType("BANK");
        account.setAccountingSubjectCode("1002.02");
        account.setOpeningDate(LocalDate.now().minusYears(1));
        account.setOpeningBalance(new BigDecimal("2000000.00"));
        account.setEnabledFlag(1);
        account.setVersion(0);
        fundAccountMapper.insert(account);

        payApplicationMapper.insert(application(APP_1, "M70-MYSQL-APP-1"));
        payApplicationMapper.insert(application(APP_2, "M70-MYSQL-APP-2"));
        TestUserContext.clear();
    }

    @AfterEach
    void cleanup() {
        TestUserContext.clear();
        cleanupRows();
    }

    @Test
    void repeatableReadSerializesTwoApplicationsAndRefreshesAllProjections() throws Exception {
        updateApplicationAmount(APP_1, new BigDecimal("600000.00"));
        updateApplicationAmount(APP_2, new BigDecimal("400000.00"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> attemptPayment(
                    APP_1, new BigDecimal("600000.00"), ready, start));
            Future<Boolean> second = executor.submit(() -> attemptPayment(
                    APP_2, new BigDecimal("400000.00"), ready, start));
            ready.await();
            start.countDown();

            List<Boolean> results = List.of(first.get(), second.get());
            assertEquals(2, results.stream().filter(Boolean::booleanValue).count());
        }

        List<PayRecord> committed = payRecordMapper.selectList(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, TENANT)
                .eq(PayRecord::getContractId, CONTRACT)
                .eq(PayRecord::getPayStatus, "SUCCESS"));
        assertEquals(2, committed.size());
        assertEquals(0, new BigDecimal("1000000.00").compareTo(committed.stream()
                .map(PayRecord::getPayAmount).reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(0, new BigDecimal("1000000.00").compareTo(
                contractMapper.selectById(CONTRACT).getPaidAmount()));
        assertEquals(0, new BigDecimal("600000.00").compareTo(
                payApplicationMapper.selectById(APP_1).getActualPayAmount()));
        assertEquals(0, new BigDecimal("400000.00").compareTo(
                payApplicationMapper.selectById(APP_2).getActualPayAmount()));
        assertEquals(0, new BigDecimal("1000000.00").compareTo(jdbc.queryForObject("""
                SELECT COALESCE(MAX(paid_amount),0) FROM cost_summary
                 WHERE tenant_id=? AND project_id=? AND summary_date=CURRENT_DATE AND deleted_flag=0
                """, BigDecimal.class, TENANT, PROJECT)));
    }

    @Test
    void repeatableReadApplicationSubmitGateAllowsOnlyOneCriticalReservation() throws Exception {
        updateApplicationStatus(APP_1, "DRAFT");
        updateApplicationStatus(APP_2, "DRAFT");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> attemptApplicationSubmitGate(APP_1, ready, start));
            Future<Boolean> second = executor.submit(() -> attemptApplicationSubmitGate(APP_2, ready, start));
            ready.await();
            start.countDown();
            assertEquals(1, List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)).stream()
                    .filter(Boolean::booleanValue).count());
        }
        assertEquals(1L, payApplicationMapper.selectList(new LambdaQueryWrapper<PayApplication>()
                .in(PayApplication::getId, APP_1, APP_2)
                .eq(PayApplication::getApprovalStatus, "APPROVING")).size());
        assertEquals(0L, payRecordMapper.selectCount(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, TENANT).eq(PayRecord::getContractId, CONTRACT)));
    }

    @Test
    void concurrentIdenticalCallbacksReturnTheSameRecordAndDifferentFactsConflict() throws Exception {
        updateApplicationStatus(APP_2, "DRAFT");
        LocalDateTime paidAt = LocalDateTime.now().minusMinutes(1).withNano(0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> writeback(APP_1, "M70-MYSQL-IDEMPOTENT", paidAt,
                    "BANK_TRANSFER", "VOUCHER-1", new BigDecimal("600000.00"), ready, start));
            Future<String> second = executor.submit(() -> writeback(APP_1, "M70-MYSQL-IDEMPOTENT", paidAt,
                    "BANK_TRANSFER", "VOUCHER-1", new BigDecimal("600000.00"), ready, start));
            ready.await();
            start.countDown();
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1L, payRecordMapper.selectCount(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, TENANT)
                .eq(PayRecord::getExternalTxnNo, "M70-MYSQL-IDEMPOTENT")));

        TestUserContext.setAdmin(TENANT, USER);
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> payRecordService.writeback(payment(APP_1, "M70-MYSQL-IDEMPOTENT", paidAt,
                        "BANK_TRANSFER", "VOUCHER-2", new BigDecimal("600000.00"))));
        assertEquals("PAY_WRITEBACK_IDEMPOTENCY_CONFLICT", conflict.getCode());
        TestUserContext.clear();
    }

    @Test
    void concurrentPaymentsAcrossContractsReceiveDistinctCodes() throws Exception {
        CtContract secondContract = new CtContract();
        secondContract.setId(CONTRACT_2);
        secondContract.setTenantId(TENANT);
        secondContract.setProjectId(PROJECT);
        secondContract.setContractCode("M70-MYSQL-CT-2");
        secondContract.setContractName("M70 MySQL contract 2");
        secondContract.setContractType("SUB");
        secondContract.setPartyAId(PARTNER_A);
        secondContract.setPartyBId(PARTNER_B);
        secondContract.setContractAmount(new BigDecimal("1000000.00"));
        secondContract.setCurrentAmount(new BigDecimal("1000000.00"));
        secondContract.setPaidAmount(BigDecimal.ZERO);
        secondContract.setTaxRate(BigDecimal.ZERO);
        secondContract.setApprovalStatus("APPROVED");
        secondContract.setContractStatus("PERFORMING");
        contractMapper.insert(secondContract);
        PayApplication secondApplication = payApplicationMapper.selectById(APP_2);
        secondApplication.setContractId(CONTRACT_2);
        payApplicationMapper.updateById(secondApplication);

        LocalDateTime paidAt = LocalDateTime.now().minusMinutes(1).withNano(0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> writeback(APP_1, "M70-MYSQL-CODE-1", paidAt,
                    "BANK_TRANSFER", "CODE-1", new BigDecimal("600000.00"), ready, start));
            Future<String> second = executor.submit(() -> writeback(APP_2, "M70-MYSQL-CODE-2", paidAt,
                    "BANK_TRANSFER", "CODE-2", new BigDecimal("600000.00"), ready, start));
            ready.await();
            start.countDown();
            String firstCode = payRecordMapper.selectById(Long.parseLong(first.get(10, TimeUnit.SECONDS))).getRecordCode();
            String secondCode = payRecordMapper.selectById(Long.parseLong(second.get(10, TimeUnit.SECONDS))).getRecordCode();
            assertNotEquals(firstCode, secondCode);
        }
    }

    @Test
    void concurrentPaymentAndReversalCompleteWithoutDeadlock() throws Exception {
        updateApplicationAmount(APP_1, new BigDecimal("400000.00"));
        updateApplicationAmount(APP_2, new BigDecimal("600000.00"));
        LocalDateTime paidAt = LocalDateTime.now().minusMinutes(2).withNano(0);
        TestUserContext.setAdmin(TENANT, USER);
        long originalId = Long.parseLong(payRecordService.writeback(payment(APP_1,
                "M70-MYSQL-REV-ORIGINAL", paidAt, "BANK_TRANSFER", "REV-ORIGINAL",
                new BigDecimal("400000.00"))).getId());
        seedPendingReversalFacts(originalId, paidAt);
        TestUserContext.clear();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> reversal = executor.submit(() -> reverse(originalId, ready, start));
            Future<String> payment = executor.submit(() -> writeback(APP_2, "M70-MYSQL-REV-CONCURRENT",
                    LocalDateTime.now().minusMinutes(1).withNano(0), "BANK_TRANSFER", "REV-CONCURRENT",
                    new BigDecimal("600000.00"), ready, start));
            ready.await();
            start.countDown();
            assertTrue(Long.parseLong(reversal.get(10, TimeUnit.SECONDS)) > 0);
            assertTrue(Long.parseLong(payment.get(10, TimeUnit.SECONDS)) > 0);
        }

        assertEquals("REVERSED", payRecordMapper.selectById(originalId).getPayStatus());
        assertEquals(0, new BigDecimal("600000.00").compareTo(contractMapper.selectById(CONTRACT).getPaidAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(payApplicationMapper.selectById(APP_1).getActualPayAmount()));
        assertEquals(0, new BigDecimal("600000.00").compareTo(
                payApplicationMapper.selectById(APP_2).getActualPayAmount()));
    }

    @Test
    void concurrentInvoicesCannotOverAllocateOnePayment() throws Exception {
        PayRecord record = directSuccessRecord(APP_1, "M70-MYSQL-INVOICE-PAY", new BigDecimal("600000.00"));
        payRecordMapper.insert(record);
        invoiceMapper.insert(invoice(INVOICE_1, "M70-MYSQL-INV-1", record.getId()));
        invoiceMapper.insert(invoice(INVOICE_2, "M70-MYSQL-INV-2", record.getId()));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> allocateInvoice(INVOICE_1, record.getId(), ready, start));
            Future<Boolean> second = executor.submit(() -> allocateInvoice(INVOICE_2, record.getId(), ready, start));
            ready.await();
            start.countDown();
            assertEquals(1, List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)).stream()
                    .filter(Boolean::booleanValue).count());
        }
        BigDecimal allocated = invoiceAllocationMapper.selectByPayRecordForUpdate(TENANT, record.getId()).stream()
                .map(InvoicePaymentAllocation::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("600000.00").compareTo(allocated));
    }

    @Test
    void concurrentFailureCallbacksAreIdempotentAcrossAllFacts() throws Exception {
        LocalDateTime attemptedAt = LocalDateTime.now().minusMinutes(1).withNano(0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> recordFailure(
                    APP_1, "M70-MYSQL-FAILURE", attemptedAt, "通道拒绝", ready, start));
            Future<String> second = executor.submit(() -> recordFailure(
                    APP_1, "M70-MYSQL-FAILURE", attemptedAt, "通道拒绝", ready, start));
            ready.await();
            start.countDown();
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1L, payRecordMapper.selectCount(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, TENANT)
                .eq(PayRecord::getExternalTxnNo, "M70-MYSQL-FAILURE")));

        TestUserContext.setAdmin(TENANT, USER);
        PaymentFailureRequest conflicting = failure(
                APP_1, "M70-MYSQL-FAILURE", attemptedAt, "不同失败原因");
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> reversalService.recordFailure(conflicting));
        assertEquals("PAYMENT_FAILURE_IDEMPOTENCY_CONFLICT", conflict.getCode());
        TestUserContext.clear();
    }

    @Test
    void concurrentArchiveAndReversalUseOneLockOrder() throws Exception {
        updateApplicationAmount(APP_1, new BigDecimal("400000.00"));
        LocalDateTime paidAt = LocalDateTime.now().minusMinutes(2).withNano(0);
        TestUserContext.setAdmin(TENANT, USER);
        long originalId = Long.parseLong(payRecordService.writeback(payment(APP_1,
                "M70-MYSQL-ARCHIVE-REV", paidAt, "BANK_TRANSFER", "ARCHIVE-REV",
                new BigDecimal("400000.00"))).getId());
        seedPendingReversalFacts(originalId, paidAt);
        CashJournalEntry journal = cashJournalMapper.selectOne(new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getTenantId, TENANT)
                .eq(CashJournalEntry::getPayRecordId, originalId));
        attachCashJournal(journal.getId());
        TestUserContext.clear();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        String archiveResult;
        long reversalId;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> archive = executor.submit(() -> archive(journal.getId(), ready, start));
            Future<String> reversal = executor.submit(() -> reverse(originalId, ready, start));
            ready.await();
            start.countDown();
            archiveResult = archive.get(10, TimeUnit.SECONDS);
            reversalId = Long.parseLong(reversal.get(10, TimeUnit.SECONDS));
        }
        assertTrue(List.of("ARCHIVED", "CASH_JOURNAL_ARCHIVED_IMMUTABLE").contains(archiveResult));
        assertTrue(reversalId > 0);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pay_record WHERE id=? AND pay_status='REVERSED'",
                Integer.class, originalId));
        assertEquals(0, jdbc.queryForObject("SELECT paid_amount FROM payment_application_source WHERE id=970011",
                BigDecimal.class).compareTo(BigDecimal.ZERO));
        assertEquals(0, jdbc.queryForObject("SELECT paid_amount FROM ct_contract WHERE id=?",
                BigDecimal.class, CONTRACT).compareTo(BigDecimal.ZERO));
        assertEquals(0, jdbc.queryForObject("SELECT reserved_amount FROM project_budget_line WHERE id=?",
                BigDecimal.class, BUDGET_LINE).compareTo(new BigDecimal("400000.00")));
        assertEquals(0, jdbc.queryForObject("SELECT consumed_amount FROM project_budget_line WHERE id=?",
                BigDecimal.class, BUDGET_LINE).compareTo(BigDecimal.ZERO));
        assertEquals(0, jdbc.queryForObject("SELECT reserved_amount FROM contract_budget_allocation WHERE id=?",
                BigDecimal.class, CONTRACT_BUDGET_ALLOCATION).compareTo(new BigDecimal("400000.00")));
        assertEquals(0, jdbc.queryForObject("SELECT consumed_amount FROM contract_budget_allocation WHERE id=?",
                BigDecimal.class, CONTRACT_BUDGET_ALLOCATION).compareTo(BigDecimal.ZERO));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM cash_journal_entry WHERE id=? AND status='REVERSED'",
                Integer.class, journal.getId()));
        if ("ARCHIVED".equals(archiveResult)) {
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM accounting_entry WHERE id=970013 AND reversed_entry_id IS NOT NULL",
                    Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM accounting_entry WHERE pay_record_id=? AND entry_type='PAYMENT_REVERSAL'",
                    Integer.class, reversalId));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM cash_journal_entry WHERE reverse_of_entry_id=? AND status='ARCHIVED'",
                    Integer.class, journal.getId()));
        } else {
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM accounting_entry WHERE id=970013 AND entry_status='REVERSED' AND reversed_entry_id IS NULL",
                    Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM cash_journal_entry WHERE reverse_of_entry_id=?",
                    Integer.class, journal.getId()));
        }
    }

    @Test
    void concurrentInvoiceVerificationLocksPaymentRecordsInIdOrder() throws Exception {
        PayRecord firstRecord = directSuccessRecord(APP_1, "M70-MYSQL-VERIFY-PAY-1",
                new BigDecimal("600000.00"));
        PayRecord secondRecord = directSuccessRecord(APP_1, "M70-MYSQL-VERIFY-PAY-2",
                new BigDecimal("600000.00"));
        payRecordMapper.insert(firstRecord);
        payRecordMapper.insert(secondRecord);
        invoiceMapper.insert(invoice(INVOICE_1, "M70-MYSQL-VERIFY-INV-1", firstRecord.getId()));
        invoiceMapper.insert(invoice(INVOICE_2, "M70-MYSQL-VERIFY-INV-2", secondRecord.getId()));
        LocalDateTime now = LocalDateTime.now().withNano(0);
        invoiceAllocationMapper.insert(allocation(970101L, INVOICE_1, firstRecord.getId(), now.minusSeconds(2)));
        invoiceAllocationMapper.insert(allocation(970102L, INVOICE_1, secondRecord.getId(), now.minusSeconds(1)));
        invoiceAllocationMapper.insert(allocation(970103L, INVOICE_2, secondRecord.getId(), now.minusSeconds(2)));
        invoiceAllocationMapper.insert(allocation(970104L, INVOICE_2, firstRecord.getId(), now.minusSeconds(1)));
        attachInvoice(INVOICE_1, "M70-MYSQL-VERIFY-FILE-1");
        attachInvoice(INVOICE_2, "M70-MYSQL-VERIFY-FILE-2");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> verifyInvoice(INVOICE_1, ready, start));
            Future<String> second = executor.submit(() -> verifyInvoice(INVOICE_2, ready, start));
            ready.await();
            start.countDown();
            assertEquals("VERIFIED", first.get(10, TimeUnit.SECONDS));
            assertEquals("VERIFIED", second.get(10, TimeUnit.SECONDS));
        }
    }

    private boolean attemptPayment(long applicationId, BigDecimal amount,
                                   CountDownLatch ready, CountDownLatch start) throws Exception {
        TestUserContext.setAdmin(TENANT, USER);
        try {
            ready.countDown();
            start.await();
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            try {
                return Boolean.TRUE.equals(transaction.execute(status -> {
                    payRecordService.writeback(payment(applicationId, "M70-MYSQL-TXN-" + applicationId,
                            LocalDateTime.now().minusMinutes(1).withNano(0), "BANK_TRANSFER",
                            "VOUCHER-" + applicationId, amount));
                    return true;
                }));
            } catch (BusinessException rejected) {
                assertTrue(rejected.getCode().contains("EXCEED"));
                return false;
            }
        } finally {
            TestUserContext.clear();
        }
    }

    private boolean attemptApplicationSubmitGate(long applicationId,
                                                 CountDownLatch ready, CountDownLatch start) throws Exception {
        TestUserContext.setAdmin(TENANT, USER);
        try {
            ready.countDown();
            start.await();
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            try {
                return Boolean.TRUE.equals(transaction.execute(status -> {
                    PayApplication application = payApplicationService.lockForAmountGate(applicationId);
                    payApplicationService.validatePaymentAmount(application);
                    application.setApprovalStatus("APPROVING");
                    payApplicationMapper.updateById(application);
                    return true;
                }));
            } catch (BusinessException rejected) {
                assertEquals("EXCEED_CONTRACT_BALANCE", rejected.getCode());
                return false;
            }
        } finally {
            TestUserContext.clear();
        }
    }

    private String writeback(long applicationId, String externalTxnNo, LocalDateTime paidAt,
                             String payMethod, String voucherNo, BigDecimal amount,
                             CountDownLatch ready, CountDownLatch start) throws Exception {
        TestUserContext.setAdmin(TENANT, USER);
        try {
            ready.countDown();
            start.await();
            return payRecordService.writeback(payment(
                    applicationId, externalTxnNo, paidAt, payMethod, voucherNo, amount)).getId();
        } finally {
            TestUserContext.clear();
        }
    }

    private String reverse(long originalId, CountDownLatch ready, CountDownLatch start) throws Exception {
        TestUserContext.setAdmin(TENANT, USER);
        try {
            ready.countDown();
            start.await();
            PaymentReversalRequest request = new PaymentReversalRequest();
            request.setReason("MySQL 付款冲销并发锁序测试");
            request.setExternalTxnNo("M70-MYSQL-REVERSAL");
            request.setReversedAt(LocalDateTime.now().withNano(0));
            return reversalService.reverse(originalId, request).getId();
        } finally {
            TestUserContext.clear();
        }
    }

    private boolean allocateInvoice(long invoiceId, long recordId,
                                    CountDownLatch ready, CountDownLatch start) throws Exception {
        TestUserContext.setAdmin(TENANT, USER);
        try {
            ready.countDown();
            start.await();
            InvoicePaymentAllocation allocation = new InvoicePaymentAllocation();
            allocation.setPayRecordId(recordId);
            allocation.setAllocatedAmount(new BigDecimal("600000.00"));
            try {
                invoiceService.saveAllocations(invoiceId, List.of(allocation));
                return true;
            } catch (BusinessException rejected) {
                assertEquals("PAY_RECORD_INVOICE_OVER_ALLOCATED", rejected.getCode());
                return false;
            }
        } finally {
            TestUserContext.clear();
        }
    }

    private String recordFailure(long applicationId, String externalTxnNo, LocalDateTime attemptedAt,
                                 String reason, CountDownLatch ready, CountDownLatch start) throws Exception {
        TestUserContext.setAdmin(TENANT, USER);
        try {
            ready.countDown();
            start.await();
            return reversalService.recordFailure(
                    failure(applicationId, externalTxnNo, attemptedAt, reason)).getId();
        } finally {
            TestUserContext.clear();
        }
    }

    private PaymentFailureRequest failure(long applicationId, String externalTxnNo,
                                          LocalDateTime attemptedAt, String reason) {
        PaymentFailureRequest request = new PaymentFailureRequest();
        request.setPayApplicationId(applicationId);
        request.setPayAmount(new BigDecimal("600000.00"));
        request.setExternalTxnNo(externalTxnNo);
        request.setAttemptedAt(attemptedAt);
        request.setFailureReason(reason);
        request.setFundAccountId(FUND_ACCOUNT);
        request.setPayMethod("BANK_TRANSFER");
        return request;
    }

    private String archive(long journalId, CountDownLatch ready, CountDownLatch start) throws Exception {
        TestUserContext.setAdmin(TENANT, USER);
        try {
            ready.countDown();
            start.await();
            try {
                return cashJournalService.archive(journalId).getStatus();
            } catch (BusinessException rejected) {
                return rejected.getCode();
            }
        } finally {
            TestUserContext.clear();
        }
    }

    private String verifyInvoice(long invoiceId, CountDownLatch ready, CountDownLatch start) throws Exception {
        TestUserContext.setAdmin(TENANT, USER);
        try {
            ready.countDown();
            start.await();
            invoiceService.verify(invoiceId, "VERIFIED");
            return invoiceMapper.selectById(invoiceId).getVerifyStatus();
        } finally {
            TestUserContext.clear();
        }
    }

    private PayRecord payment(long applicationId, String externalTxnNo, LocalDateTime paidAt,
                              String payMethod, String voucherNo, BigDecimal amount) {
        PayRecord record = new PayRecord();
        record.setPayApplicationId(applicationId);
        record.setPayAmount(amount);
        record.setPaidAt(paidAt);
        record.setFundAccountId(FUND_ACCOUNT);
        record.setPayMethod(payMethod);
        record.setVoucherNo(voucherNo);
        record.setExternalTxnNo(externalTxnNo);
        return record;
    }

    private PayRecord directSuccessRecord(long applicationId, String externalTxnNo, BigDecimal amount) {
        PayRecord record = payment(applicationId, externalTxnNo, LocalDateTime.now().minusMinutes(1),
                "BANK_TRANSFER", externalTxnNo, amount);
        record.setTenantId(TENANT);
        record.setProjectId(PROJECT);
        record.setContractId(CONTRACT);
        record.setPartnerId(PARTNER_B);
        record.setRecordCode(externalTxnNo);
        record.setPayDate(record.getPaidAt().toLocalDate());
        record.setPayStatus("SUCCESS");
        record.setVersion(0);
        return record;
    }

    private PayInvoice invoice(long id, String invoiceNo, long recordId) {
        PayInvoice invoice = new PayInvoice();
        invoice.setId(id);
        invoice.setTenantId(TENANT);
        invoice.setPayApplicationId(APP_1);
        invoice.setPayRecordId(recordId);
        invoice.setProjectId(PROJECT);
        invoice.setContractId(CONTRACT);
        invoice.setPartnerId(PARTNER_B);
        invoice.setInvoiceNo(invoiceNo);
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setInvoiceAmount(new BigDecimal("600000.00"));
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setVerifyStatus("PENDING");
        invoice.setDocumentType("ELECTRONIC_INVOICE");
        invoice.setIntegrityVersion("CLOSED_LOOP_V1");
        invoice.setVersion(0);
        return invoice;
    }

    private InvoicePaymentAllocation allocation(long id, long invoiceId, long recordId,
                                                LocalDateTime createdAt) {
        InvoicePaymentAllocation allocation = new InvoicePaymentAllocation();
        allocation.setId(id);
        allocation.setTenantId(TENANT);
        allocation.setInvoiceId(invoiceId);
        allocation.setPayApplicationId(APP_1);
        allocation.setPayRecordId(recordId);
        allocation.setAllocatedAmount(new BigDecimal("300000.00"));
        allocation.setCreatedBy(USER);
        allocation.setCreatedAt(createdAt);
        return allocation;
    }

    private void attachInvoice(long invoiceId, String fileName) {
        SysFile file = new SysFile();
        file.setTenantId(TENANT);
        file.setBusinessType("INVOICE");
        file.setBusinessId(invoiceId);
        file.setFileName(fileName + ".pdf");
        file.setOriginalName(fileName + ".pdf");
        file.setFileSize(100L);
        file.setContentType("application/pdf");
        file.setStoragePath("test/" + fileName + ".pdf");
        file.setBucketName("test");
        file.setDocumentType("ELECTRONIC_INVOICE");
        file.setVirusScanStatus("CLEAN");
        sysFileMapper.insert(file);
    }

    private void attachCashJournal(long journalId) {
        SysFile file = new SysFile();
        file.setTenantId(TENANT);
        file.setBusinessType("CASH_JOURNAL");
        file.setBusinessId(journalId);
        file.setFileName("M70-MYSQL-BANK-RECEIPT.pdf");
        file.setOriginalName("M70-MYSQL-BANK-RECEIPT.pdf");
        file.setFileSize(100L);
        file.setContentType("application/pdf");
        file.setStoragePath("test/M70-MYSQL-BANK-RECEIPT.pdf");
        file.setBucketName("test");
        file.setDocumentType("BANK_RECEIPT");
        file.setVirusScanStatus("CLEAN");
        sysFileMapper.insert(file);
    }

    private void updateApplicationAmount(long id, BigDecimal amount) {
        PayApplication application = payApplicationMapper.selectById(id);
        application.setApplyAmount(amount);
        application.setApprovedAmount(amount);
        payApplicationMapper.updateById(application);
    }

    private void updateApplicationStatus(long id, String status) {
        PayApplication application = payApplicationMapper.selectById(id);
        application.setApprovalStatus(status);
        application.setPayStatus(status);
        payApplicationMapper.updateById(application);
    }

    private void seedPendingReversalFacts(long recordId, LocalDateTime paidAt) {
        jdbc.update("""
                INSERT INTO project_budget
                  (id,tenant_id,project_id,budget_code,version_no,budget_name,total_amount,approval_status,
                   status,active_flag,active_token,effective_at,version,deleted_flag)
                VALUES(?,?,?,'M70-MYSQL-BUDGET','V1','M70 MySQL budget',1000000,'APPROVED',
                       'ACTIVE',1,?,CURRENT_TIMESTAMP,0,0)
                """, BUDGET, TENANT, PROJECT, PROJECT);
        jdbc.update("""
                INSERT INTO project_budget_line
                  (id,tenant_id,budget_id,project_id,cost_subject_id,budget_amount,reserved_amount,
                   consumed_amount,version,deleted_flag)
                SELECT ?,?,?,?,id,1000000,400000,0,0,0
                  FROM cost_subject WHERE tenant_id=? AND deleted_flag=0 ORDER BY id LIMIT 1
                """, BUDGET_LINE, TENANT, BUDGET, PROJECT, TENANT);
        jdbc.update("""
                INSERT INTO contract_budget_allocation
                  (id,tenant_id,project_id,contract_id,budget_line_id,allocated_amount,
                   reserved_amount,consumed_amount,version,deleted_flag)
                VALUES(?,?,?,?,?,1000000,400000,0,0,0)
                """, CONTRACT_BUDGET_ALLOCATION, TENANT, PROJECT, CONTRACT, BUDGET_LINE);
        jdbc.update("UPDATE pay_application SET budget_line_id=?,contract_budget_allocation_id=? WHERE id=?",
                BUDGET_LINE, CONTRACT_BUDGET_ALLOCATION, APP_1);
        long sourceId = 970011L;
        jdbc.update("""
                INSERT INTO payment_application_source
                  (id,tenant_id,pay_application_id,source_type,source_ref_id,source_amount,paid_amount,version,deleted_flag)
                VALUES(?,?,?,'DIRECT',?,?,?,0,0)
                """, sourceId, TENANT, APP_1, APP_1, new BigDecimal("400000.00"), new BigDecimal("400000.00"));
        jdbc.update("""
                INSERT INTO payment_record_source_allocation
                  (id,tenant_id,pay_record_id,payment_source_id,source_type,source_ref_id,allocated_amount,created_by)
                VALUES(970012,?,?,?,'DIRECT',?,?,?)
                """, TENANT, recordId, sourceId, APP_1, new BigDecimal("400000.00"), USER);
        jdbc.update("""
                INSERT INTO accounting_entry
                  (id,tenant_id,entry_code,entry_date,entry_type,source_type,source_id,project_id,contract_id,
                   pay_application_id,pay_record_id,entry_status,review_status,total_debit,total_credit,version,adjustment_flag,deleted_flag)
                VALUES(970013,?,'M70-MYSQL-REV-ENTRY',?,'PAYMENT','PAY_RECORD',?,?,?,?,?,'DRAFT','PENDING',400000,400000,0,0,0)
                """, TENANT, paidAt.toLocalDate(), recordId, PROJECT, CONTRACT, APP_1, recordId);
        jdbc.update("""
                INSERT INTO accounting_entry_line
                  (id,tenant_id,entry_id,line_no,direction,account_code,account_name,amount,summary,deleted_flag)
                VALUES(970014,?,970013,1,'DEBIT','1001','cash',400000,'M70 debit',0),
                      (970015,?,970013,2,'CREDIT','2202','payable',400000,'M70 credit',0)
                """, TENANT, TENANT);
    }

    private PayApplication application(long id, String code) {
        PayApplication application = new PayApplication();
        application.setId(id);
        application.setTenantId(TENANT);
        application.setProjectId(PROJECT);
        application.setContractId(CONTRACT);
        application.setPartnerId(PARTNER_B);
        application.setApplyCode(code);
        application.setApplyAmount(new BigDecimal("600000.00"));
        application.setApprovedAmount(new BigDecimal("600000.00"));
        application.setActualPayAmount(BigDecimal.ZERO);
        application.setPayType("DIRECT");
        application.setPayStatus("APPROVED");
        application.setApprovalStatus("APPROVED");
        return application;
    }

    private MdPartner partner(long id, String code, String name) {
        MdPartner partner = new MdPartner();
        partner.setId(id);
        partner.setTenantId(TENANT);
        partner.setPartnerCode(code);
        partner.setPartnerName(name);
        partner.setPartnerType("SUPPLIER");
        partner.setStatus("ENABLE");
        return partner;
    }

    private void cleanupRows() {
        jdbc.update("DELETE FROM payment_document_link WHERE tenant_id=? AND cash_journal_id IN "
                + "(SELECT id FROM cash_journal_entry WHERE tenant_id=? AND project_id=?)", TENANT, TENANT, PROJECT);
        jdbc.update("DELETE FROM sys_file WHERE tenant_id=? AND ((business_type='INVOICE' AND business_id IN (?,?)) "
                + "OR (business_type='CASH_JOURNAL' AND business_id IN "
                + "(SELECT id FROM cash_journal_entry WHERE tenant_id=? AND project_id=?)))",
                TENANT, INVOICE_1, INVOICE_2, TENANT, PROJECT);
        jdbc.update("DELETE FROM invoice_payment_allocation WHERE tenant_id=? AND invoice_id IN (?,?)",
                TENANT, INVOICE_1, INVOICE_2);
        jdbc.update("DELETE FROM pay_invoice WHERE tenant_id=? AND id IN (?,?)", TENANT, INVOICE_1, INVOICE_2);
        jdbc.update("DELETE FROM cash_journal_change_log WHERE journal_entry_id IN "
                + "(SELECT id FROM cash_journal_entry WHERE tenant_id=? AND project_id=?)", TENANT, PROJECT);
        jdbc.update("DELETE FROM accounting_entry_line WHERE entry_id IN "
                + "(SELECT id FROM accounting_entry WHERE tenant_id=? AND project_id=?)", TENANT, PROJECT);
        jdbc.update("UPDATE accounting_entry SET reversed_entry_id=NULL,original_entry_id=NULL "
                + "WHERE tenant_id=? AND project_id=?", TENANT, PROJECT);
        jdbc.update("DELETE FROM accounting_entry WHERE tenant_id=? AND project_id=?", TENANT, PROJECT);
        jdbc.update("UPDATE cash_journal_entry SET reverse_of_entry_id=NULL,reversal_entry_id=NULL "
                + "WHERE tenant_id=? AND project_id=?", TENANT, PROJECT);
        jdbc.update("DELETE FROM cash_journal_entry WHERE tenant_id=? AND project_id=?", TENANT, PROJECT);
        jdbc.update("DELETE FROM payment_record_source_allocation WHERE tenant_id=? AND pay_record_id IN "
                + "(SELECT id FROM pay_record WHERE tenant_id=? AND project_id=?)", TENANT, TENANT, PROJECT);
        jdbc.update("DELETE FROM payment_application_source WHERE tenant_id=? AND pay_application_id IN (?,?)",
                TENANT, APP_1, APP_2);
        jdbc.update("UPDATE pay_application SET contract_budget_allocation_id=NULL,budget_line_id=NULL "
                + "WHERE tenant_id=? AND id IN (?,?)", TENANT, APP_1, APP_2);
        jdbc.update("DELETE FROM budget_ledger WHERE tenant_id=? AND budget_id=?", TENANT, BUDGET);
        jdbc.update("DELETE FROM contract_budget_allocation WHERE tenant_id=? AND id=?",
                TENANT, CONTRACT_BUDGET_ALLOCATION);
        jdbc.update("DELETE FROM project_budget_line WHERE tenant_id=? AND id=?", TENANT, BUDGET_LINE);
        jdbc.update("DELETE FROM project_budget WHERE tenant_id=? AND id=?", TENANT, BUDGET);
        jdbc.update("DELETE FROM cost_summary WHERE tenant_id=? AND project_id=?", TENANT, PROJECT);
        jdbc.update("UPDATE pay_record SET reversed_record_id=NULL WHERE tenant_id=? AND project_id=?", TENANT, PROJECT);
        jdbc.update("DELETE FROM pay_record WHERE tenant_id=? AND (contract_id=? OR pay_application_id IN (?,?))",
                TENANT, CONTRACT, APP_1, APP_2);
        jdbc.update("DELETE FROM pay_application WHERE tenant_id=? AND id IN (?,?)", TENANT, APP_1, APP_2);
        jdbc.update("DELETE FROM ct_contract WHERE tenant_id=? AND project_id=?", TENANT, PROJECT);
        jdbc.update("DELETE FROM pm_project WHERE tenant_id=? AND id=?", TENANT, PROJECT);
        jdbc.update("DELETE FROM fund_account WHERE tenant_id=? AND id=?", TENANT, FUND_ACCOUNT);
        jdbc.update("DELETE FROM md_partner WHERE tenant_id=? AND id IN (?,?)", TENANT, PARTNER_A, PARTNER_B);
    }
}
