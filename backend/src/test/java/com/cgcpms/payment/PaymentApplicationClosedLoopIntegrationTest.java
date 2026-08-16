package com.cgcpms.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.mapper.ProjectBudgetLineMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.document.provider.DocumentDataSnapshot;
import com.cgcpms.document.provider.PaymentDocumentDataProvider;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.expense.entity.ExpenseApplication;
import com.cgcpms.expense.handler.ExpenseWorkflowHandler;
import com.cgcpms.expense.mapper.ExpenseApplicationMapper;
import com.cgcpms.expense.service.ExpenseApplicationService;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.invoice.entity.InvoicePaymentAllocation;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.service.InvoiceService;
import com.cgcpms.cashbook.entity.FundAccount;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.FundAccountMapper;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.constant.PaymentIntegrityConstants;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PaymentApplicationSource;
import com.cgcpms.payment.handler.PayRequestWorkflowHandler;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.payment.service.PaymentApplicationSourceService;
import com.cgcpms.payment.service.PaymentTraceService;
import com.cgcpms.payment.dto.PaymentReversalRequest;
import com.cgcpms.payment.service.PaymentReversalService;
import com.cgcpms.payment.service.PayRecordService;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubTask;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import com.cgcpms.system.dict.service.SysDictDataService;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowContext;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.workflow.service.WorkflowSubmitService;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.accounting.mapper.AccountingEntryMapper;
import com.cgcpms.accounting.mapper.AccountingEntryLineMapper;
import com.cgcpms.accounting.service.AccountingEntryService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("local")
class PaymentApplicationClosedLoopIntegrationTest {
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 98300101L;
    private static final long SUBJECT_ID = 98300102L;
    private static final long PARTNER_ID = 98300103L;
    private static final long CONTRACT_ID = 98300104L;
    private static final long BUDGET_ID = 98300105L;
    private static final long BUDGET_LINE_ID = 98300106L;
    private static final long FUND_ACCOUNT_ID = 98300107L;
    private static final long CONTRACT_BUDGET_ALLOCATION_ID = 98300108L;

    @Autowired private PayApplicationService applicationService;
    @Autowired private PaymentApplicationSourceService sourceService;
    @Autowired private PayRequestWorkflowHandler paymentHandler;
    @Autowired private ExpenseApplicationService expenseService;
    @Autowired private ExpenseWorkflowHandler expenseHandler;
    @Autowired private PayApplicationMapper applicationMapper;
    @Autowired private ExpenseApplicationMapper expenseMapper;
    @Autowired private ProjectBudgetLineMapper lineMapper;
    @Autowired private ProjectBudgetMapper budgetMapper;
    @Autowired private PmProjectMapper projectMapper;
    @Autowired private CostSubjectMapper subjectMapper;
    @Autowired private MdPartnerMapper partnerMapper;
    @Autowired private CtContractMapper contractMapper;
    @Autowired private SysFileMapper fileMapper;
    @Autowired private FundAccountMapper fundAccountMapper;
    @Autowired private CashJournalEntryMapper cashJournalMapper;
    @Autowired private CashJournalService cashJournalService;
    @Autowired private PayRecordService payRecordService;
    @Autowired private WfInstanceMapper wfInstanceMapper;
    @Autowired private AccountingEntryMapper accountingEntryMapper;
    @Autowired private AccountingEntryLineMapper accountingLineMapper;
    @Autowired private AccountingEntryService accountingEntryService;
    @Autowired private StlSettlementMapper settlementMapper;
    @Autowired private SubMeasureMapper subMeasureMapper;
    @Autowired private SubTaskMapper subTaskMapper;
    @Autowired private InvoiceService invoiceService;
    @Autowired private PaymentTraceService traceService;
    @Autowired private PaymentDocumentDataProvider paymentDocumentDataProvider;
    @Autowired private PaymentReversalService reversalService;
    @Autowired private WorkflowSubmitService workflowSubmitService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private WorkflowEngine workflowEngine;
    @MockitoBean private SysDictDataService sysDictDataService;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            String value = invocation.getArgument(1);
            return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        }).when(sysDictDataService).requireEnabledValue(
                anyString(), nullable(String.class), anyString(), anyString());
        setContext();
        hardCleanup();
        seedAccountingSubjects();
        seedBusinessContext();
        doAnswer(invocation -> {
            WfInstance instance = new WfInstance();
            Long businessId = invocation.getArgument(4);
            instance.setId(990000000L + businessId);
            instance.setTenantId(TENANT_ID);
            instance.setBusinessId(businessId);
            instance.setCurrentRound(1);
            instance.setTemplateId("PAY_REQUEST".equals(invocation.getArgument(3)) ? 50005L : 50010L);
            instance.setBusinessType(invocation.getArgument(3));
            instance.setProjectId(invocation.getArgument(7));
            instance.setContractId(invocation.getArgument(8));
            instance.setTitle(invocation.getArgument(5));
            instance.setAmount(invocation.getArgument(6));
            instance.setInstanceStatus("RUNNING");
            instance.setResubmitCount(0);
            instance.setBusinessRevision(1);
            instance.setInitiatorId(1L);
            instance.setStartedAt(LocalDateTime.now());
            wfInstanceMapper.insert(instance);
            jdbcTemplate.update("""
                    INSERT INTO wf_node_instance
                        (id,tenant_id,instance_id,template_node_id,node_code,node_name,node_order,approve_mode,
                         node_type,approver_config,allow_transfer,allow_add_sign,timeout_hours,node_status,round_no,
                         created_by,created_at,updated_at,deleted_flag)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                    """,
                    instance.getId() + 2, TENANT_ID, instance.getId(), null, "LEGACY_APPROVAL",
                    "历史付款审批", 1, "OR_SIGN", "APPROVAL", "{\"type\":\"USER\",\"userId\":1}",
                    0, 0, null, "COMPLETED", 1, 1L);
            jdbcTemplate.update("""
                    INSERT INTO wf_record
                        (id,tenant_id,instance_id,round_no,business_type,business_id,
                         action_type,action_name,operator_id,operator_name,record_status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    instance.getId() + 1, TENANT_ID, instance.getId(), 1, instance.getBusinessType(),
                    businessId, "SUBMIT", "提交", 1L, "admin", "SUCCESS");
            return instance;
        }).when(workflowEngine).submit(anyLong(), anyString(), anyLong(), anyString(), anyLong(),
                anyString(), any(BigDecimal.class), anyLong(), anyLong(), nullable(String.class),
                nullable(String.class), nullable(List.class));
    }

    @AfterEach
    void tearDown() {
        setContext();
        hardCleanup();
        UserContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("直接付款提交执行完整性门禁、占用预算并关联审批实例，驳回后释放")
    void directPaymentLifecycleIsTraceableAndReversible() {
        Long applicationId = createPayment(new BigDecimal("600.00"));
        saveDirectSource(applicationId, new BigDecimal("600.00"));

        BusinessException missingAttachment = assertThrows(BusinessException.class,
                () -> applicationService.submitForApproval(applicationId));
        assertEquals("PAYMENT_ATTACHMENT_REQUIRED", missingAttachment.getCode());
        assertMoney("0.00", lineMapper.selectById(BUDGET_LINE_ID).getReservedAmount());

        attach("PAYMENT", applicationId);
        applicationService.submitForApproval(applicationId);
        PayApplication submitted = applicationMapper.selectById(applicationId);
        assertEquals("APPROVING", submitted.getApprovalStatus());
        assertNotNull(submitted.getApprovalInstanceId());
        assertEquals(CONTRACT_BUDGET_ALLOCATION_ID, submitted.getContractBudgetAllocationId());
        assertEquals(PaymentIntegrityConstants.CLOSED_LOOP_V1, submitted.getIntegrityVersion());
        assertMoney("600.00", lineMapper.selectById(BUDGET_LINE_ID).getReservedAmount());
        assertMoney("600.00", jdbcTemplate.queryForObject(
                "SELECT reserved_amount FROM contract_budget_allocation WHERE id = ?",
                BigDecimal.class, CONTRACT_BUDGET_ALLOCATION_ID));

        paymentHandler.onRejected(context(instance(applicationId)));
        assertEquals("REJECTED", applicationMapper.selectById(applicationId).getApprovalStatus());
        assertMoney("0.00", lineMapper.selectById(BUDGET_LINE_ID).getReservedAmount());
        assertMoney("0.00", jdbcTemplate.queryForObject(
                "SELECT reserved_amount FROM contract_budget_allocation WHERE id = ?",
                BigDecimal.class, CONTRACT_BUDGET_ALLOCATION_ID));

        WfInstance rejected = wfInstanceMapper.selectById(submitted.getApprovalInstanceId());
        rejected.setInstanceStatus("REJECTED");
        rejected.setEndedAt(LocalDateTime.now());
        wfInstanceMapper.updateById(rejected);
        PayApplication revised = applicationMapper.selectById(applicationId);
        revised.setApplyReason("驳回后修订并重新提交");
        applicationService.update(revised);
        ensureWorkflowApprover();
        WfInstance resubmitted = workflowSubmitService.resubmit(rejected.getId(), 1L, "admin");
        assertEquals(2, resubmitted.getCurrentRound());
        assertEquals(rejected.getId(), resubmitted.getId());
        assertEquals("APPROVING", applicationMapper.selectById(applicationId).getApprovalStatus());
        assertMoney("600.00", lineMapper.selectById(BUDGET_LINE_ID).getReservedAmount());
        assertMoney("600.00", jdbcTemplate.queryForObject(
                "SELECT reserved_amount FROM contract_budget_allocation WHERE id = ?",
                BigDecimal.class, CONTRACT_BUDGET_ALLOCATION_ID));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM budget_ledger WHERE idempotency_key = ?",
                Long.class, "PAY_REQUEST:RESERVE:" + applicationId + ":R2"));
    }

    @Test
    @DisplayName("费用来源转付款只冻结来源额度，不重复占用预算，驳回付款释放来源额度")
    void expenseSourceDoesNotDoubleReserveBudget() {
        Long expenseId = createApprovedExpense(new BigDecimal("400.00"));
        assertMoney("400.00", lineMapper.selectById(BUDGET_LINE_ID).getReservedAmount());

        Long applicationId = createPayment(new BigDecimal("300.00"));
        PaymentApplicationSource source = new PaymentApplicationSource();
        source.setSourceType("EXPENSE");
        source.setSourceRefId(expenseId);
        source.setSourceAmount(new BigDecimal("300.00"));
        sourceService.save(applicationId, List.of(source));
        attach("PAYMENT", applicationId);
        applicationService.submitForApproval(applicationId);

        assertMoney("300.00", expenseMapper.selectById(expenseId).getConvertedAmount());
        assertMoney("400.00", lineMapper.selectById(BUDGET_LINE_ID).getReservedAmount());

        paymentHandler.onRejected(context(instance(applicationId)));
        assertMoney("0.00", expenseMapper.selectById(expenseId).getConvertedAmount());
        assertMoney("400.00", lineMapper.selectById(BUDGET_LINE_ID).getReservedAmount());

        applicationMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PayApplication>()
                .eq(PayApplication::getId, applicationId)
                .set(PayApplication::getApprovalStatus, "DRAFT"));
        paymentHandler.beforeSubmit(context(instance(applicationId, 2)));
        assertMoney("300.00", expenseMapper.selectById(expenseId).getConvertedAmount());
        assertMoney("400.00", lineMapper.selectById(BUDGET_LINE_ID).getReservedAmount());
    }

    @Test
    @DisplayName("管理员没有 payment:direct 也不能保存直接付款来源")
    void directPaymentRequiresExplicitPermission() {
        Long applicationId = createPayment(new BigDecimal("100.00"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        BusinessException denied = assertThrows(BusinessException.class,
                () -> saveDirectSource(applicationId, new BigDecimal("100.00")));

        assertEquals("PAYMENT_DIRECT_PERMISSION_DENIED", denied.getCode());
    }

    @Test
    @DisplayName("直接付款原因空白时失败关闭")
    void directPaymentRequiresNonBlankReason() {
        Long applicationId = createPayment(new BigDecimal("100.00"));
        PayApplication app = applicationMapper.selectById(applicationId);
        app.setApplyReason("   ");
        applicationMapper.updateById(app);

        BusinessException denied = assertThrows(BusinessException.class,
                () -> saveDirectSource(applicationId, new BigDecimal("100.00")));

        assertEquals("PAYMENT_DIRECT_REASON_REQUIRED", denied.getCode());
    }

    @Test
    @DisplayName("付款回写前预算失效时失败关闭且不生成付款记录")
    void writebackRejectsInactiveBudget() {
        Long applicationId = createPayment(new BigDecimal("100.00"));
        saveDirectSource(applicationId, new BigDecimal("100.00"));
        attach("PAYMENT", applicationId);
        applicationService.submitForApproval(applicationId);
        paymentHandler.onApproved(context(instance(applicationId)));
        AccountingEntry payableConfirmation = accountingEntryMapper.selectOne(new LambdaQueryWrapper<AccountingEntry>()
                .eq(AccountingEntry::getSourceType, "PAY_APPLICATION")
                .eq(AccountingEntry::getSourceId, applicationId)
                .eq(AccountingEntry::getEntryType, "AP_CONFIRMATION"));
        assertNotNull(payableConfirmation);
        assertMoney("100.00", jdbcTemplate.queryForObject(
                "SELECT amount FROM accounting_entry_line WHERE entry_id=? AND account_code='PAYMENT-CLOSED-LOOP-SUBJECT'",
                BigDecimal.class, payableConfirmation.getId()));
        assertMoney("100.00", jdbcTemplate.queryForObject(
                "SELECT amount FROM accounting_entry_line WHERE entry_id=? AND account_code='2202-AP'",
                BigDecimal.class, payableConfirmation.getId()));
        assertEquals(SUBJECT_ID, jdbcTemplate.queryForObject(
                "SELECT cost_subject_id FROM accounting_entry_line WHERE entry_id=? AND direction='DEBIT'",
                Long.class, payableConfirmation.getId()));
        assertEquals(1L, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM finance_audit_event
                 WHERE business_type='PAY_APPLICATION' AND business_id=?
                   AND event_type='PAY_APPLICATION_CONFIRMED'
                """, Long.class, applicationId));

        ProjectBudget budget = budgetMapper.selectById(BUDGET_ID);
        budget.setStatus("CLOSED");
        budget.setActiveFlag(0);
        budgetMapper.updateById(budget);

        PayRecord input = new PayRecord();
        input.setPayApplicationId(applicationId);
        input.setPayAmount(new BigDecimal("100.00"));
        input.setFundAccountId(FUND_ACCOUNT_ID);
        input.setPaidAt(LocalDateTime.now().minusMinutes(1));
        input.setPayMethod("BANK_TRANSFER");
        input.setExternalTxnNo("PAYMENT-INACTIVE-BUDGET");
        BusinessException rejected = assertThrows(BusinessException.class,
                () -> payRecordService.writeback(input));

        assertEquals("BUDGET_NOT_ACTIVE", rejected.getCode());
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pay_record WHERE external_txn_no = ?",
                Long.class, "PAYMENT-INACTIVE-BUDGET"));
    }

    @Test
    @DisplayName("来源合计不等于申请金额和合同预算不足均失败关闭")
    void sourceAmountAndBudgetAreFailClosed() {
        Long mismatchId = createPayment(new BigDecimal("100.00"));
        BusinessException mismatch = assertThrows(BusinessException.class,
                () -> saveDirectSource(mismatchId, new BigDecimal("99.00")));
        assertEquals("PAYMENT_SOURCE_AMOUNT_MISMATCH", mismatch.getCode());

        Long insufficientId = createPayment(new BigDecimal("1200.00"));
        saveDirectSource(insufficientId, new BigDecimal("1200.00"));
        attach("PAYMENT", insufficientId);
        BusinessException insufficient = assertThrows(BusinessException.class,
                () -> applicationService.submitForApproval(insufficientId));
        assertEquals("CONTRACT_BUDGET_INSUFFICIENT", insufficient.getCode());
        assertMoney("0.00", lineMapper.selectById(BUDGET_LINE_ID).getReservedAmount());
    }

    @Test
    @DisplayName("成功付款按来源消耗预算并自动唯一生成带显式链路的现金日记")
    void successfulPaymentConsumesBudgetAndCreatesExplicitCashTrace() {
        Long applicationId = createPayment(new BigDecimal("500.00"));
        saveDirectSource(applicationId, new BigDecimal("500.00"));
        attach("PAYMENT", applicationId);
        applicationService.submitForApproval(applicationId);
        paymentHandler.onApproved(context(instance(applicationId)));

        PayRecord input = new PayRecord();
        input.setPayApplicationId(applicationId);
        input.setPayAmount(new BigDecimal("300.00"));
        input.setFundAccountId(FUND_ACCOUNT_ID);
        input.setPaidAt(LocalDateTime.now().minusMinutes(1));
        input.setPayMethod("BANK_TRANSFER");
        input.setExternalTxnNo("PAYMENT-CLOSED-LOOP-TXN-001");
        var first = payRecordService.writeback(input);

        PayRecord duplicateInput = new PayRecord();
        duplicateInput.setPayApplicationId(applicationId);
        duplicateInput.setPayAmount(new BigDecimal("300.00"));
        duplicateInput.setFundAccountId(FUND_ACCOUNT_ID);
        duplicateInput.setPaidAt(input.getPaidAt());
        duplicateInput.setPayMethod("BANK_TRANSFER");
        duplicateInput.setExternalTxnNo("PAYMENT-CLOSED-LOOP-TXN-001");
        var duplicate = payRecordService.writeback(duplicateInput);
        assertEquals(first.getId(), duplicate.getId());
        assertEquals(1L, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM finance_audit_event
                 WHERE business_type='PAY_RECORD' AND business_id=? AND event_type='PAYMENT_COMPLETED'
                """, Long.class, Long.valueOf(first.getId())));

        ProjectBudgetLine pendingLine = lineMapper.selectById(BUDGET_LINE_ID);
        assertMoney("500.00", pendingLine.getReservedAmount());
        assertMoney("0.00", pendingLine.getConsumedAmount());
        PaymentApplicationSource source = sourceService.list(applicationId).isEmpty() ? null
                : jdbcTemplate.queryForObject("SELECT * FROM payment_application_source WHERE pay_application_id = ?",
                (rs, rowNum) -> {
                    PaymentApplicationSource value = new PaymentApplicationSource();
                    value.setPaidAmount(rs.getBigDecimal("paid_amount"));
                    return value;
                }, applicationId);
        assertNotNull(source);
        assertMoney("300.00", source.getPaidAmount());

        CashJournalEntry journal = cashJournalMapper.selectOne(new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getPayRecordId, Long.valueOf(first.getId())));
        assertNotNull(journal);
        assertEquals(applicationId, journal.getPayApplicationId());
        assertEquals(FUND_ACCOUNT_ID, journal.getAccountId());
        assertEquals(applicationMapper.selectById(applicationId).getApprovalInstanceId(), journal.getApprovalInstanceId());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_record_source_allocation WHERE pay_record_id = ?",
                Long.class, Long.valueOf(first.getId())));
        AccountingEntry entry = accountingEntryMapper.selectOne(new LambdaQueryWrapper<AccountingEntry>()
                .eq(AccountingEntry::getPayRecordId, Long.valueOf(first.getId())));
        assertNotNull(entry);
        Long accountingEntryId = entry.getId();
        jdbcTemplate.update("UPDATE accounting_entry SET review_status = 'APPROVED' WHERE id = ?", accountingEntryId);
        BusinessException preArchivePost = assertThrows(BusinessException.class,
                () -> accountingEntryService.post(accountingEntryId));
        assertEquals("PAYMENT_CASH_JOURNAL_ARCHIVE_REQUIRED", preArchivePost.getCode());

        attach("CASH_JOURNAL", journal.getId());
        cashJournalService.archive(journal.getId());
        ProjectBudgetLine archivedLine = lineMapper.selectById(BUDGET_LINE_ID);
        assertMoney("200.00", archivedLine.getReservedAmount());
        assertMoney("300.00", archivedLine.getConsumedAmount());
        assertMoney("200.00", jdbcTemplate.queryForObject(
                "SELECT reserved_amount FROM contract_budget_allocation WHERE id = ?",
                BigDecimal.class, CONTRACT_BUDGET_ALLOCATION_ID));
        assertMoney("300.00", jdbcTemplate.queryForObject(
                "SELECT consumed_amount FROM contract_budget_allocation WHERE id = ?",
                BigDecimal.class, CONTRACT_BUDGET_ALLOCATION_ID));
        entry = accountingEntryMapper.selectById(accountingEntryId);
        assertEquals(journal.getId(), entry.getCashJournalId());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_document_link WHERE cash_journal_id = ?",
                Long.class, journal.getId()));
        accountingEntryService.post(accountingEntryId);
        entry = accountingEntryMapper.selectById(accountingEntryId);
        assertEquals("POSTED", entry.getEntryStatus());
        assertMoney("300.00", entry.getTotalDebit());
        assertMoney("300.00", entry.getTotalCredit());
        assertEquals(2L, accountingLineMapper.selectCount(new LambdaQueryWrapper<AccountingEntryLine>()
                .eq(AccountingEntryLine::getEntryId, entry.getId())));

        PayInvoice invoice = new PayInvoice();
        invoice.setPayRecordId(Long.valueOf(first.getId()));
        invoice.setInvoiceNo("PAYMENT-CLOSED-LOOP-INVOICE-001");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setDocumentType("ELECTRONIC_INVOICE");
        invoice.setInvoiceAmount(new BigDecimal("300.00"));
        invoice.setInvoiceDate(LocalDate.now());
        Long invoiceId = invoiceService.create(invoice);
        InvoicePaymentAllocation invoiceAllocation = new InvoicePaymentAllocation();
        invoiceAllocation.setPayRecordId(Long.valueOf(first.getId()));
        invoiceAllocation.setAllocatedAmount(new BigDecimal("300.00"));
        invoiceService.saveAllocations(invoiceId, List.of(invoiceAllocation));
        attach("INVOICE", invoiceId);
        invoiceService.verify(invoiceId, "VERIFIED");

        DocumentDataSnapshot document = paymentDocumentDataProvider.load(applicationId);
        @SuppressWarnings("unchecked")
        var paymentDocument = (java.util.Map<String, Object>) document.values().get("payment");
        @SuppressWarnings("unchecked")
        var payeeDocument = (java.util.Map<String, Object>) document.values().get("payee");
        assertEquals("500.00", paymentDocument.get("applyAmount"));
        assertFalse(payeeDocument.containsKey("bankAccount"));
        assertFalse(payeeDocument.containsKey("contactPhone"));
        assertEquals(1, ((java.util.List<?>) document.values().get("sources")).size());
        assertEquals(1, ((java.util.List<?>) document.values().get("invoices")).size());
        assertFalse(document.values().containsKey("attachments"));

        var trace = traceService.byCashJournal(journal.getId());
        assertEquals(applicationId, trace.getPaymentApplication().getId());
        assertEquals(1, trace.getPaymentRecords().size());
        assertEquals(1, trace.getCashJournals().size());
        assertEquals(1, trace.getPaymentDocuments().size());
        assertEquals(1, trace.getInvoices().size());
        assertEquals(1, trace.getAccountingEntries().size());
        assertEquals(2, trace.getAccountingEntryLines().size());
        assertEquals(applicationId, traceService.byInvoice(invoiceId).getFirst()
                .getPaymentApplication().getId());
        assertEquals(applicationId, traceService.byVoucher(accountingEntryId).getFirst()
                .getPaymentApplication().getId());
        assertEquals(applicationId, traceService.byApproval(
                        applicationMapper.selectById(applicationId).getApprovalInstanceId()).getFirst()
                .getPaymentApplication().getId());
        assertEquals(applicationId, traceService.byContract(CONTRACT_ID).getFirst()
                .getPaymentApplication().getId());
        assertEquals(applicationId, traceService.byProject(PROJECT_ID).getFirst()
                .getPaymentApplication().getId());

        UserContext.set(Jwts.claims().add("userId", 2L).add("username", "other-tenant")
                .add("tenantId", TENANT_ID + 1).add("roleCodes", List.of("ADMIN")).build());
        BusinessException crossTenant = assertThrows(BusinessException.class,
                () -> traceService.byCashJournal(journal.getId()));
        assertEquals("CASH_JOURNAL_NOT_FOUND", crossTenant.getCode());
        setContext();

        PmProject otherProject = new PmProject();
        otherProject.setTenantId(TENANT_ID);
        otherProject.setProjectCode("PAYMENT-TRACE-OTHER-PROJECT");
        otherProject.setProjectName("付款追溯错链项目");
        otherProject.setStatus("ACTIVE");
        projectMapper.insert(otherProject);
        try {
            jdbcTemplate.update("UPDATE cash_journal_entry SET project_id=? WHERE id=?",
                    otherProject.getId(), journal.getId());
            BusinessException brokenRelation = assertThrows(BusinessException.class,
                    () -> traceService.byCashJournal(journal.getId()));
            assertEquals("PAYMENT_TRACE_INCOMPLETE", brokenRelation.getCode());
        } finally {
            jdbcTemplate.update("UPDATE cash_journal_entry SET project_id=? WHERE id=?",
                    PROJECT_ID, journal.getId());
            projectMapper.deleteById(otherProject.getId());
        }

        jdbcTemplate.update("DELETE FROM payment_document_link WHERE cash_journal_id = ?", journal.getId());
        BusinessException missingArchivedEvidence = assertThrows(BusinessException.class,
                () -> traceService.byCashJournal(journal.getId()));
        assertEquals("PAYMENT_TRACE_INCOMPLETE", missingArchivedEvidence.getCode());
    }

    @Test
    @DisplayName("竣工和质保阶段允许完成既有合同付款，项目关闭后拒绝提交与支付")
    void closeoutStagesAllowContractPaymentButClosedProjectDoesNot() {
        jdbcTemplate.update("UPDATE pm_project SET status='COMPLETION' WHERE id=?", PROJECT_ID);
        Long completionApplicationId = createPayment(new BigDecimal("100.00"));
        saveDirectSource(completionApplicationId, new BigDecimal("100.00"));
        attach("PAYMENT", completionApplicationId);
        applicationService.submitForApproval(completionApplicationId);
        paymentHandler.onApproved(context(instance(completionApplicationId)));

        jdbcTemplate.update("UPDATE pm_project SET status='WARRANTY' WHERE id=?", PROJECT_ID);
        PayRecord warrantyPayment = new PayRecord();
        warrantyPayment.setPayApplicationId(completionApplicationId);
        warrantyPayment.setPayAmount(new BigDecimal("100.00"));
        warrantyPayment.setFundAccountId(FUND_ACCOUNT_ID);
        warrantyPayment.setPaidAt(LocalDateTime.now().minusMinutes(1));
        warrantyPayment.setPayMethod("BANK_TRANSFER");
        warrantyPayment.setExternalTxnNo("PAYMENT-CLOSEOUT-WARRANTY-001");
        assertEquals("SUCCESS", payRecordService.writeback(warrantyPayment).getPayStatus());

        Long closedApplicationId = createPayment(new BigDecimal("100.00"));
        saveDirectSource(closedApplicationId, new BigDecimal("100.00"));
        attach("PAYMENT", closedApplicationId);
        applicationService.submitForApproval(closedApplicationId);
        paymentHandler.onApproved(context(instance(closedApplicationId)));

        jdbcTemplate.update("UPDATE pm_project SET status='CLOSED' WHERE id=?", PROJECT_ID);
        Long closedDraftId = createPayment(new BigDecimal("50.00"));
        saveDirectSource(closedDraftId, new BigDecimal("50.00"));
        attach("PAYMENT", closedDraftId);
        BusinessException submitBlocked = assertThrows(BusinessException.class,
                () -> applicationService.submitForApproval(closedDraftId));
        assertEquals("PROJECT_NOT_ACTIVE", submitBlocked.getCode());

        PayRecord closedPayment = new PayRecord();
        closedPayment.setPayApplicationId(closedApplicationId);
        closedPayment.setPayAmount(new BigDecimal("100.00"));
        closedPayment.setFundAccountId(FUND_ACCOUNT_ID);
        closedPayment.setPaidAt(LocalDateTime.now().minusMinutes(1));
        closedPayment.setPayMethod("BANK_TRANSFER");
        closedPayment.setExternalTxnNo("PAYMENT-CLOSEOUT-CLOSED-001");
        BusinessException paymentBlocked = assertThrows(BusinessException.class,
                () -> payRecordService.writeback(closedPayment));
        assertEquals("PROJECT_NOT_ACTIVE", paymentBlocked.getCode());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pay_record WHERE external_txn_no='PAYMENT-CLOSEOUT-CLOSED-001'",
                Integer.class));
    }

    @Test
    @DisplayName("预付款不确认AP，发票核验后显式确认并重分类")
    void advancePaymentUsesPrepaymentThenInvoiceReclassifiesIt() {
        Long applicationId = createPayment(new BigDecimal("300.00"), "ADVANCE");
        saveDirectSource(applicationId, new BigDecimal("300.00"));
        attach("PAYMENT", applicationId);
        applicationService.submitForApproval(applicationId);
        paymentHandler.onApproved(context(instance(applicationId)));

        assertEquals(0L, accountingEntryMapper.selectCount(new LambdaQueryWrapper<AccountingEntry>()
                .eq(AccountingEntry::getSourceType, "PAY_APPLICATION")
                .eq(AccountingEntry::getSourceId, applicationId)
                .eq(AccountingEntry::getEntryType, "AP_CONFIRMATION")));

        PayRecord payment = new PayRecord();
        payment.setPayApplicationId(applicationId);
        payment.setPayAmount(new BigDecimal("300.00"));
        payment.setFundAccountId(FUND_ACCOUNT_ID);
        payment.setPaidAt(LocalDateTime.now().minusMinutes(1));
        payment.setPayMethod("BANK_TRANSFER");
        payment.setExternalTxnNo("PAYMENT-CLOSED-LOOP-ADVANCE-001");
        var paid = payRecordService.writeback(payment);
        AccountingEntry paymentEntry = accountingEntryMapper.selectOne(new LambdaQueryWrapper<AccountingEntry>()
                .eq(AccountingEntry::getPayRecordId, Long.valueOf(paid.getId()))
                .eq(AccountingEntry::getEntryType, "PAYMENT"));
        assertNotNull(paymentEntry);
        assertMoney("300.00", jdbcTemplate.queryForObject(
                "SELECT amount FROM accounting_entry_line WHERE entry_id=? AND account_code='1123-PREPAY'",
                BigDecimal.class, paymentEntry.getId()));

        PayInvoice invoice = new PayInvoice();
        invoice.setPayRecordId(Long.valueOf(paid.getId()));
        invoice.setInvoiceNo("PAYMENT-CLOSED-LOOP-ADVANCE-INVOICE-001");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setDocumentType("ELECTRONIC_INVOICE");
        invoice.setInvoiceAmount(new BigDecimal("300.00"));
        invoice.setInvoiceDate(LocalDate.now());
        Long invoiceId = invoiceService.create(invoice);
        InvoicePaymentAllocation allocation = new InvoicePaymentAllocation();
        allocation.setPayRecordId(Long.valueOf(paid.getId()));
        allocation.setAllocatedAmount(new BigDecimal("300.00"));
        invoiceService.saveAllocations(invoiceId, List.of(allocation));
        attach("INVOICE", invoiceId);
        invoiceService.verify(invoiceId, "VERIFIED");

        Long confirmationId = jdbcTemplate.queryForObject("""
                SELECT id FROM accounting_entry
                 WHERE source_type='PAY_INVOICE' AND source_id=? AND entry_type='ADVANCE_AP_CONFIRMATION'
                """, Long.class, invoiceId);
        Long reclassId = jdbcTemplate.queryForObject("""
                SELECT id FROM accounting_entry
                 WHERE source_type='PAY_INVOICE' AND source_id=? AND entry_type='ADVANCE_PREPAY_RECLASS'
                """, Long.class, invoiceId);
        assertMoney("300.00", jdbcTemplate.queryForObject(
                "SELECT amount FROM accounting_entry_line WHERE entry_id=? AND account_code='PAYMENT-CLOSED-LOOP-SUBJECT'",
                BigDecimal.class, confirmationId));
        assertMoney("300.00", jdbcTemplate.queryForObject(
                "SELECT amount FROM accounting_entry_line WHERE entry_id=? AND account_code='2202-AP' AND direction='CREDIT'",
                BigDecimal.class, confirmationId));
        assertMoney("300.00", jdbcTemplate.queryForObject(
                "SELECT amount FROM accounting_entry_line WHERE entry_id=? AND account_code='2202-AP' AND direction='DEBIT'",
                BigDecimal.class, reclassId));
        assertMoney("300.00", jdbcTemplate.queryForObject(
                "SELECT amount FROM accounting_entry_line WHERE entry_id=? AND account_code='1123-PREPAY'",
                BigDecimal.class, reclassId));
    }

    @Test
    @DisplayName("付款Trace缺审批记录、来源金额或完整预算占用时失败关闭")
    void paymentTraceRejectsPartialApprovalSourceAndBudgetFacts() {
        Long applicationId = createPayment(new BigDecimal("100.00"));
        saveDirectSource(applicationId, new BigDecimal("100.00"));
        attach("PAYMENT", applicationId);
        applicationService.submitForApproval(applicationId);
        Long instanceId = applicationMapper.selectById(applicationId).getApprovalInstanceId();
        assertNotNull(traceService.byApplication(applicationId));

        jdbcTemplate.update("UPDATE wf_record SET deleted_flag=1 WHERE instance_id=?", instanceId);
        BusinessException missingApprovalRecord = assertThrows(BusinessException.class,
                () -> traceService.byApplication(applicationId));
        assertEquals("PAYMENT_TRACE_INCOMPLETE", missingApprovalRecord.getCode());
        jdbcTemplate.update("UPDATE wf_record SET deleted_flag=0 WHERE instance_id=?", instanceId);

        jdbcTemplate.update("""
                UPDATE payment_application_source SET source_amount=99.00
                 WHERE pay_application_id=? AND tenant_id=?
                """, applicationId, TENANT_ID);
        BusinessException incompleteSource = assertThrows(BusinessException.class,
                () -> traceService.byApplication(applicationId));
        assertEquals("PAYMENT_TRACE_INCOMPLETE", incompleteSource.getCode());
        jdbcTemplate.update("""
                UPDATE payment_application_source SET source_amount=100.00
                 WHERE pay_application_id=? AND tenant_id=?
                """, applicationId, TENANT_ID);

        jdbcTemplate.update("""
                UPDATE budget_ledger SET amount=99.00
                 WHERE business_type='PAY_REQUEST' AND business_id=? AND entry_type='RESERVE'
                """, applicationId);
        BusinessException incompleteBudget = assertThrows(BusinessException.class,
                () -> traceService.byApplication(applicationId));
        assertEquals("PAYMENT_TRACE_INCOMPLETE", incompleteBudget.getCode());
    }

    @Test
    @DisplayName("付款现金日记缺少合规证据时归档整体失败")
    void paymentJournalArchiveRequiresTypedCleanEvidence() {
        Long applicationId = createPayment(new BigDecimal("100.00"));
        saveDirectSource(applicationId, new BigDecimal("100.00"));
        attach("PAYMENT", applicationId);
        applicationService.submitForApproval(applicationId);
        paymentHandler.onApproved(context(instance(applicationId)));

        PayRecord input = new PayRecord();
        input.setPayApplicationId(applicationId);
        input.setPayAmount(new BigDecimal("100.00"));
        input.setFundAccountId(FUND_ACCOUNT_ID);
        input.setPaidAt(LocalDateTime.now().minusMinutes(1));
        input.setPayMethod("BANK_TRANSFER");
        input.setExternalTxnNo("PAYMENT-EVIDENCE-FAIL-CLOSED");
        Long paidId = Long.valueOf(payRecordService.writeback(input).getId());
        CashJournalEntry journal = cashJournalMapper.selectOne(new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getPayRecordId, paidId));
        attachCashJournal(journal.getId(), "BANK_RECEIPT", "PENDING");

        BusinessException failure = assertThrows(BusinessException.class,
                () -> cashJournalService.archive(journal.getId()));
        assertEquals("CASH_JOURNAL_EVIDENCE_REQUIRED", failure.getCode());
        assertEquals("PENDING_ARCHIVE", cashJournalMapper.selectById(journal.getId()).getStatus());
        assertMoney("100.00", lineMapper.selectById(BUDGET_LINE_ID).getReservedAmount());
        assertMoney("0.00", lineMapper.selectById(BUDGET_LINE_ID).getConsumedAmount());
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_document_link WHERE cash_journal_id = ?",
                Long.class, journal.getId()));
    }

    @Test
    @DisplayName("付款单据Provider从真实业务链读取多来源、多发票且金额不重算")
    void paymentDocumentProviderReadsMultipleSourcesAndInvoicesFromAuthoritativeChain() {
        Long firstExpenseId = createApprovedExpense(new BigDecimal("200.00"));
        Long secondExpenseId = createApprovedExpense(new BigDecimal("300.00"));
        Long applicationId = createPayment(new BigDecimal("500.00"));

        PaymentApplicationSource firstSource = new PaymentApplicationSource();
        firstSource.setSourceType(PaymentIntegrityConstants.SOURCE_EXPENSE);
        firstSource.setSourceRefId(firstExpenseId);
        firstSource.setSourceAmount(new BigDecimal("200.00"));
        PaymentApplicationSource secondSource = new PaymentApplicationSource();
        secondSource.setSourceType(PaymentIntegrityConstants.SOURCE_EXPENSE);
        secondSource.setSourceRefId(secondExpenseId);
        secondSource.setSourceAmount(new BigDecimal("300.00"));
        sourceService.save(applicationId, List.of(firstSource, secondSource));
        attach("PAYMENT", applicationId);
        applicationService.submitForApproval(applicationId);
        paymentHandler.onApproved(context(instance(applicationId)));

        PayRecord payment = new PayRecord();
        payment.setPayApplicationId(applicationId);
        payment.setPayAmount(new BigDecimal("500.00"));
        payment.setFundAccountId(FUND_ACCOUNT_ID);
        payment.setPaidAt(LocalDateTime.now());
        payment.setPayMethod("BANK_TRANSFER");
        payment.setExternalTxnNo("PAYMENT-DOCUMENT-MULTI-TXN-001");
        var paid = payRecordService.writeback(payment);

        createAllocatedInvoice(Long.valueOf(paid.getId()), "PAYMENT-DOCUMENT-INVOICE-001", "200.00");
        createAllocatedInvoice(Long.valueOf(paid.getId()), "PAYMENT-DOCUMENT-INVOICE-002", "300.00");
        assertEquals(applicationId, traceService.byExpense(firstExpenseId).getFirst()
                .getPaymentApplication().getId());
        assertEquals(applicationId, traceService.byExpense(secondExpenseId).getFirst()
                .getPaymentApplication().getId());

        DocumentDataSnapshot document = paymentDocumentDataProvider.load(applicationId);
        @SuppressWarnings("unchecked")
        var paymentValues = (java.util.Map<String, Object>) document.values().get("payment");
        @SuppressWarnings("unchecked")
        var sources = (List<java.util.Map<String, Object>>) document.values().get("sources");
        @SuppressWarnings("unchecked")
        var invoices = (List<java.util.Map<String, Object>>) document.values().get("invoices");

        assertEquals("500.00", paymentValues.get("applyAmount"));
        assertEquals("500.00", paymentValues.get("approvedAmount"));
        assertEquals("500.00", paymentValues.get("actualPayAmount"));
        assertEquals(List.of("200.00", "300.00"),
                sources.stream().map(row -> row.get("amount")).sorted().toList());
        assertEquals(List.of("200.00", "300.00"),
                invoices.stream().map(row -> row.get("amount")).sorted().toList());

        long wrongContractId = CONTRACT_ID + 100;
        CtContract wrongContract = new CtContract();
        wrongContract.setId(wrongContractId);
        wrongContract.setTenantId(TENANT_ID);
        wrongContract.setProjectId(PROJECT_ID);
        wrongContract.setContractCode("PAYMENT-TRACE-WRONG-CONTRACT");
        wrongContract.setContractName("付款 Trace 错链测试合同");
        wrongContract.setContractType("SUBCONTRACT");
        wrongContract.setPartyAId(PARTNER_ID);
        wrongContract.setPartyBId(PARTNER_ID);
        wrongContract.setContractAmount(new BigDecimal("5000.00"));
        wrongContract.setCurrentAmount(new BigDecimal("5000.00"));
        wrongContract.setPaidAmount(BigDecimal.ZERO);
        wrongContract.setContractStatus("PERFORMING");
        wrongContract.setApprovalStatus("APPROVED");
        wrongContract.setVersion(0);
        contractMapper.insert(wrongContract);
        try {
            jdbcTemplate.update("UPDATE expense_application SET contract_id=? WHERE id=?",
                    wrongContractId, firstExpenseId);
            BusinessException wrongLink = assertThrows(BusinessException.class,
                    () -> traceService.byApplication(applicationId));
            assertEquals("PAYMENT_TRACE_INCOMPLETE", wrongLink.getCode());
        } finally {
            jdbcTemplate.update("UPDATE expense_application SET contract_id=? WHERE id=?",
                    CONTRACT_ID, firstExpenseId);
            jdbcTemplate.update("DELETE FROM ct_contract WHERE id=?", wrongContractId);
        }
    }

    @Test
    @DisplayName("已审批定案结算可作为付款来源并占用预算")
    void finalizedSettlementCanBeUsedAsPaymentSource() {
        StlSettlement settlement = new StlSettlement();
        settlement.setTenantId(TENANT_ID);
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(CONTRACT_ID);
        settlement.setPartnerId(PARTNER_ID);
        settlement.setSettlementCode("PAYMENT-SOURCE-SETTLEMENT-001");
        settlement.setSettlementType("FINAL");
        settlement.setFinalAmount(new BigDecimal("250.00"));
        settlement.setPaidAmount(BigDecimal.ZERO);
        settlement.setApprovalStatus("APPROVED");
        settlement.setSettlementStatus("FINALIZED");
        settlement.setFinalizedAt(LocalDateTime.now());
        settlementMapper.insert(settlement);

        Long applicationId = createFinalPayment(new BigDecimal("250.00"));
        PaymentApplicationSource source = new PaymentApplicationSource();
        source.setSourceType(PaymentIntegrityConstants.SOURCE_SETTLEMENT);
        source.setSourceRefId(settlement.getId());
        source.setSourceAmount(new BigDecimal("250.00"));
        sourceService.save(applicationId, List.of(source));
        attach("PAYMENT", applicationId);

        applicationService.submitForApproval(applicationId);

        assertMoney("250.00", lineMapper.selectById(BUDGET_LINE_ID).getReservedAmount());
        assertEquals(settlement.getId(), sourceService.list(applicationId).get(0).getSettlementId() == null
                ? null : Long.valueOf(sourceService.list(applicationId).get(0).getSettlementId()));
        assertEquals(applicationId, traceService.bySettlement(settlement.getId()).getFirst()
                .getPaymentApplication().getId());
    }

    @Test
    @DisplayName("已审批分包计量可作为进度款来源且付款后可反查任务和计量")
    void approvedSubMeasureCanBePaidAndTracedWithoutDuplicatePayment() {
        SubTask task = new SubTask();
        task.setTenantId(TENANT_ID);
        task.setProjectId(PROJECT_ID);
        task.setContractId(CONTRACT_ID);
        task.setPartnerId(PARTNER_ID);
        task.setTaskCode("PAYMENT-SUB-TASK-001");
        task.setTaskName("付款闭环分包任务");
        task.setStatus("IN_PROGRESS");
        subTaskMapper.insert(task);

        SubMeasure measure = new SubMeasure();
        measure.setTenantId(TENANT_ID);
        measure.setProjectId(PROJECT_ID);
        measure.setContractId(CONTRACT_ID);
        measure.setPartnerId(PARTNER_ID);
        measure.setSubTaskId(task.getId());
        measure.setMeasureCode("PAYMENT-SUB-MEASURE-001");
        measure.setMeasurePeriod("2026-07");
        measure.setMeasureDate(LocalDate.now());
        measure.setReportedAmount(new BigDecimal("300.00"));
        measure.setApprovedAmount(new BigDecimal("280.00"));
        measure.setDeductionAmount(new BigDecimal("30.00"));
        measure.setNetAmount(new BigDecimal("250.00"));
        measure.setApprovalStatus("APPROVED");
        measure.setStatus("CONFIRMED");
        subMeasureMapper.insert(measure);

        Long applicationId = createSubcontractProgressPayment(new BigDecimal("250.00"));
        PaymentApplicationSource source = new PaymentApplicationSource();
        source.setSourceType(PaymentIntegrityConstants.SOURCE_SUB_MEASURE);
        source.setSourceRefId(measure.getId());
        source.setSourceAmount(new BigDecimal("250.00"));
        sourceService.save(applicationId, List.of(source));
        attach("PAYMENT", applicationId);
        applicationService.submitForApproval(applicationId);
        paymentHandler.onApproved(context(instance(applicationId)));

        PayRecord payment = new PayRecord();
        payment.setPayApplicationId(applicationId);
        payment.setPayAmount(new BigDecimal("250.00"));
        payment.setFundAccountId(FUND_ACCOUNT_ID);
        payment.setPaidAt(LocalDateTime.now());
        payment.setPayMethod("BANK_TRANSFER");
        payment.setExternalTxnNo("PAYMENT-SUB-MEASURE-TXN-001");
        var paid = payRecordService.writeback(payment);

        var trace = traceService.byPayRecord(Long.valueOf(paid.getId()));
        assertEquals(1, trace.getSubMeasures().size());
        assertEquals(measure.getId(), trace.getSubMeasures().getFirst().getId());
        assertEquals(1, trace.getSubTasks().size());
        assertEquals(task.getId(), trace.getSubTasks().getFirst().getId());
        assertMoney("250.00", new BigDecimal(sourceService.list(applicationId).getFirst().getPaidAmount()));

        Long duplicateApplicationId = createSubcontractProgressPayment(new BigDecimal("1.00"));
        PaymentApplicationSource duplicate = new PaymentApplicationSource();
        duplicate.setSourceType(PaymentIntegrityConstants.SOURCE_SUB_MEASURE);
        duplicate.setSourceRefId(measure.getId());
        duplicate.setSourceAmount(new BigDecimal("1.00"));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> sourceService.save(duplicateApplicationId, List.of(duplicate)));
        assertEquals("SUB_MEASURE_AVAILABLE_AMOUNT_INSUFFICIENT", exception.getCode());
    }

    @Test
    @DisplayName("付款冲销同步恢复来源、预算占用、现金日记与会计凭证")
    void paymentReversalRestoresTheWholeClosedLoop() {
        Long applicationId = createPayment(new BigDecimal("400.00"));
        saveDirectSource(applicationId, new BigDecimal("400.00"));
        attach("PAYMENT", applicationId);
        applicationService.submitForApproval(applicationId);
        paymentHandler.onApproved(context(instance(applicationId)));

        PayRecord input = new PayRecord();
        input.setPayApplicationId(applicationId);
        input.setPayAmount(new BigDecimal("400.00"));
        input.setFundAccountId(FUND_ACCOUNT_ID);
        input.setPaidAt(LocalDateTime.now().minusMinutes(1));
        input.setPayMethod("BANK_TRANSFER");
        input.setExternalTxnNo("PAYMENT-CLOSED-LOOP-REV-ORIGINAL");
        var paid = payRecordService.writeback(input);
        Long paidId = Long.valueOf(paid.getId());

        CashJournalEntry journal = cashJournalMapper.selectOne(new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getPayRecordId, paidId));
        attach("CASH_JOURNAL", journal.getId());
        cashJournalService.archive(journal.getId());

        PaymentReversalRequest request = new PaymentReversalRequest();
        request.setReason("银行退汇，恢复待付款额度");
        request.setExternalTxnNo("PAYMENT-CLOSED-LOOP-REVERSAL");
        request.setReversedAt(LocalDateTime.now());
        var reversal = reversalService.reverse(paidId, request);
        var duplicate = reversalService.reverse(paidId, request);
        assertEquals(1L, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM finance_audit_event
                 WHERE business_type='PAY_RECORD' AND business_id=? AND event_type='PAYMENT_REVERSED'
                """, Long.class, paidId));

        assertEquals("REVERSED", payRecordService.getById(paidId).getPayStatus());
        assertEquals("REVERSAL", reversal.getPayStatus());
        assertEquals(reversal.getId(), duplicate.getId());
        ProjectBudgetLine line = lineMapper.selectById(BUDGET_LINE_ID);
        assertMoney("400.00", line.getReservedAmount());
        assertMoney("0.00", line.getConsumedAmount());
        assertMoney("0.00", new BigDecimal(sourceService.list(applicationId).get(0).getPaidAmount()));
        PayApplication restored = applicationMapper.selectById(applicationId);
        assertEquals("APPROVED", restored.getApprovalStatus());
        assertMoney("0.00", restored.getActualPayAmount());
        assertEquals(1L, accountingEntryMapper.selectCount(new LambdaQueryWrapper<AccountingEntry>()
                .eq(AccountingEntry::getPayRecordId, Long.valueOf(reversal.getId()))
                .eq(AccountingEntry::getEntryType, "PAYMENT_REVERSAL")));
        CashJournalEntry reversalJournal = cashJournalMapper.selectOne(new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getPayRecordId, Long.valueOf(reversal.getId())));
        assertNotNull(reversalJournal);
        assertEquals(journal.getId(), reversalJournal.getReverseOfEntryId());
        var reversalRecord = payRecordService.getById(Long.valueOf(reversal.getId()));
        assertEquals(reversalRecord.getPaidAt(), reversalJournal.getArchivedAt().toString().replace('T', ' '));
        var reversalTrace = traceService.byCashJournal(reversalJournal.getId());
        assertEquals(2, reversalTrace.getCashJournals().size());
        assertEquals(2, reversalTrace.getPaymentDocuments().size());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_document_link WHERE cash_journal_id = ?",
                Long.class, journal.getId()));

        PaymentReversalRequest conflicting = new PaymentReversalRequest();
        conflicting.setReason("同一流水号但不同冲销原因");
        conflicting.setExternalTxnNo(request.getExternalTxnNo());
        conflicting.setReversedAt(request.getReversedAt());
        BusinessException idempotencyConflict = assertThrows(BusinessException.class,
                () -> reversalService.reverse(paidId, conflicting));
        assertEquals("PAYMENT_REVERSAL_IDEMPOTENCY_CONFLICT", idempotencyConflict.getCode());

        AccountingEntry originalEntry = accountingEntryMapper.selectOne(new LambdaQueryWrapper<AccountingEntry>()
                .eq(AccountingEntry::getPayRecordId, paidId)
                .eq(AccountingEntry::getEntryType, "PAYMENT"));
        AccountingEntry reversalEntry = accountingEntryMapper.selectOne(new LambdaQueryWrapper<AccountingEntry>()
                .eq(AccountingEntry::getPayRecordId, Long.valueOf(reversal.getId()))
                .eq(AccountingEntry::getEntryType, "PAYMENT_REVERSAL"));
        jdbcTemplate.update("UPDATE accounting_entry SET reversed_entry_id=NULL WHERE id=?", originalEntry.getId());
        jdbcTemplate.update("DELETE FROM accounting_entry_line WHERE entry_id=?", reversalEntry.getId());
        jdbcTemplate.update("DELETE FROM accounting_entry WHERE id=?", reversalEntry.getId());
        BusinessException incomplete = assertThrows(BusinessException.class,
                () -> traceService.byApplication(applicationId));
        assertEquals("PAYMENT_TRACE_INCOMPLETE", incomplete.getCode());
    }

    @Test
    @DisplayName("归档前付款冲销只恢复来源实付，预算和合同预算保持占用")
    void preArchiveReversalKeepsReservations() {
        Long applicationId = createPayment(new BigDecimal("200.00"));
        saveDirectSource(applicationId, new BigDecimal("200.00"));
        attach("PAYMENT", applicationId);
        applicationService.submitForApproval(applicationId);
        paymentHandler.onApproved(context(instance(applicationId)));

        PayRecord input = new PayRecord();
        input.setPayApplicationId(applicationId);
        input.setPayAmount(new BigDecimal("200.00"));
        input.setFundAccountId(FUND_ACCOUNT_ID);
        input.setPaidAt(LocalDateTime.now().minusMinutes(1));
        input.setPayMethod("BANK_TRANSFER");
        input.setExternalTxnNo("PAYMENT-PRE-ARCHIVE-REV-ORIGINAL");
        Long paidId = Long.valueOf(payRecordService.writeback(input).getId());
        AccountingEntry originalEntry = accountingEntryMapper.selectOne(
                new LambdaQueryWrapper<AccountingEntry>()
                        .eq(AccountingEntry::getPayRecordId, paidId)
                        .eq(AccountingEntry::getEntryType, "PAYMENT"));
        assertNotNull(originalEntry);
        assertEquals("DRAFT", originalEntry.getEntryStatus());

        PaymentReversalRequest request = new PaymentReversalRequest();
        request.setReason("归档前银行退回");
        request.setExternalTxnNo("PAYMENT-PRE-ARCHIVE-REVERSAL");
        request.setReversedAt(LocalDateTime.now());
        var reversal = reversalService.reverse(paidId, request);

        ProjectBudgetLine line = lineMapper.selectById(BUDGET_LINE_ID);
        assertMoney("200.00", line.getReservedAmount());
        assertMoney("0.00", line.getConsumedAmount());
        assertMoney("200.00", jdbcTemplate.queryForObject(
                "SELECT reserved_amount FROM contract_budget_allocation WHERE id = ?",
                BigDecimal.class, CONTRACT_BUDGET_ALLOCATION_ID));
        assertMoney("0.00", jdbcTemplate.queryForObject(
                "SELECT consumed_amount FROM contract_budget_allocation WHERE id = ?",
                BigDecimal.class, CONTRACT_BUDGET_ALLOCATION_ID));
        assertMoney("0.00", new BigDecimal(sourceService.list(applicationId).getFirst().getPaidAmount()));
        assertEquals("REVERSED", cashJournalMapper.selectOne(new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getPayRecordId, paidId)).getStatus());
        assertEquals("REVERSED", accountingEntryMapper.selectById(originalEntry.getId()).getEntryStatus());
        assertEquals(0L, accountingEntryMapper.selectCount(new LambdaQueryWrapper<AccountingEntry>()
                .eq(AccountingEntry::getPayRecordId, Long.valueOf(reversal.getId()))
                .eq(AccountingEntry::getEntryType, "PAYMENT_REVERSAL")));
    }

    @Test
    @DisplayName("PENDING 发票分配到任一付款记录后，该付款均不可冲销")
    void anyInvoiceAllocationBlocksReversalForEveryPayment() {
        Long applicationId = createPayment(new BigDecimal("400.00"));
        saveDirectSource(applicationId, new BigDecimal("400.00"));
        attach("PAYMENT", applicationId);
        applicationService.submitForApproval(applicationId);
        paymentHandler.onApproved(context(instance(applicationId)));

        PayRecord firstInput = new PayRecord();
        firstInput.setPayApplicationId(applicationId);
        firstInput.setPayAmount(new BigDecimal("200.00"));
        firstInput.setFundAccountId(FUND_ACCOUNT_ID);
        firstInput.setPaidAt(LocalDateTime.now().minusMinutes(2));
        firstInput.setPayMethod("BANK_TRANSFER");
        firstInput.setExternalTxnNo("PAYMENT-MULTI-INVOICE-FIRST");
        Long firstId = Long.valueOf(payRecordService.writeback(firstInput).getId());

        PayRecord secondInput = new PayRecord();
        secondInput.setPayApplicationId(applicationId);
        secondInput.setPayAmount(new BigDecimal("200.00"));
        secondInput.setFundAccountId(FUND_ACCOUNT_ID);
        secondInput.setPaidAt(LocalDateTime.now().minusMinutes(1));
        secondInput.setPayMethod("BANK_TRANSFER");
        secondInput.setExternalTxnNo("PAYMENT-MULTI-INVOICE-SECOND");
        Long secondId = Long.valueOf(payRecordService.writeback(secondInput).getId());

        PayInvoice invoice = new PayInvoice();
        invoice.setPayRecordId(firstId);
        invoice.setInvoiceNo("PAYMENT-MULTI-INVOICE");
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setDocumentType("ELECTRONIC_INVOICE");
        invoice.setInvoiceAmount(new BigDecimal("400.00"));
        invoice.setInvoiceDate(LocalDate.now());
        Long invoiceId = invoiceService.create(invoice);
        InvoicePaymentAllocation firstAllocation = new InvoicePaymentAllocation();
        firstAllocation.setPayRecordId(firstId);
        firstAllocation.setAllocatedAmount(new BigDecimal("200.00"));
        InvoicePaymentAllocation secondAllocation = new InvoicePaymentAllocation();
        secondAllocation.setPayRecordId(secondId);
        secondAllocation.setAllocatedAmount(new BigDecimal("200.00"));
        invoiceService.saveAllocations(invoiceId, List.of(firstAllocation, secondAllocation));
        PaymentReversalRequest request = new PaymentReversalRequest();
        request.setReason("尝试冲销发票第二笔分配付款");
        request.setExternalTxnNo("PAYMENT-MULTI-INVOICE-REVERSAL");
        request.setReversedAt(LocalDateTime.now());
        BusinessException blocked = assertThrows(BusinessException.class,
                () -> reversalService.reverse(secondId, request));
        assertEquals("PAYMENT_HAS_INVOICE_ALLOCATION", blocked.getCode());
        assertEquals("SUCCESS", payRecordService.getById(secondId).getPayStatus());
    }

    @Test
    @DisplayName("付款现金日记撤销归档与重新归档逐次恢复和消耗同一预算占用")
    void paymentJournalReopenAndRearchivePreserveBudgetEquation() {
        Long applicationId = createPayment(new BigDecimal("200.00"));
        saveDirectSource(applicationId, new BigDecimal("200.00"));
        attach("PAYMENT", applicationId);
        applicationService.submitForApproval(applicationId);
        paymentHandler.onApproved(context(instance(applicationId)));

        PayRecord input = new PayRecord();
        input.setPayApplicationId(applicationId);
        input.setPayAmount(new BigDecimal("200.00"));
        input.setFundAccountId(FUND_ACCOUNT_ID);
        input.setPaidAt(LocalDateTime.now().minusMinutes(1));
        input.setPayMethod("BANK_TRANSFER");
        input.setExternalTxnNo("PAYMENT-REOPEN-REARCHIVE");
        Long paidId = Long.valueOf(payRecordService.writeback(input).getId());
        CashJournalEntry journal = cashJournalMapper.selectOne(new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getPayRecordId, paidId));
        attach("CASH_JOURNAL", journal.getId());
        cashJournalService.archive(journal.getId());

        UserContext.set(Jwts.claims().add("userId", 1L).add("username", "admin")
                .add("tenantId", TENANT_ID).add("roleCodes", List.of("SUPER_ADMIN")).build());
        cashJournalService.reopen(journal.getId(), "归档信息复核");
        ProjectBudgetLine reopened = lineMapper.selectById(BUDGET_LINE_ID);
        assertMoney("200.00", reopened.getReservedAmount());
        assertMoney("0.00", reopened.getConsumedAmount());

        cashJournalService.archive(journal.getId());
        ProjectBudgetLine rearchived = lineMapper.selectById(BUDGET_LINE_ID);
        assertMoney("0.00", rearchived.getReservedAmount());
        assertMoney("200.00", rearchived.getConsumedAmount());
        assertMoney("0.00", jdbcTemplate.queryForObject(
                "SELECT reserved_amount FROM contract_budget_allocation WHERE id = ?",
                BigDecimal.class, CONTRACT_BUDGET_ALLOCATION_ID));
        assertMoney("200.00", jdbcTemplate.queryForObject(
                "SELECT consumed_amount FROM contract_budget_allocation WHERE id = ?",
                BigDecimal.class, CONTRACT_BUDGET_ALLOCATION_ID));
        assertEquals(2L, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM budget_ledger
                 WHERE business_type = 'PAY_REQUEST' AND business_id = ? AND entry_type = 'CONSUME'
                """, Long.class, applicationId));
        assertEquals(1L, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM budget_ledger
                 WHERE business_type = 'PAY_REQUEST' AND business_id = ?
                   AND entry_type = 'RESTORE_RESERVATION'
                """, Long.class, applicationId));

        AccountingEntry entry = accountingEntryMapper.selectOne(new LambdaQueryWrapper<AccountingEntry>()
                .eq(AccountingEntry::getPayRecordId, paidId)
                .eq(AccountingEntry::getEntryType, "PAYMENT"));
        jdbcTemplate.update("UPDATE accounting_entry SET review_status = 'APPROVED' WHERE id = ?", entry.getId());
        accountingEntryService.post(entry.getId());
        BusinessException posted = assertThrows(BusinessException.class,
                () -> cashJournalService.reopen(journal.getId(), "已过账后错误撤销"));
        assertEquals("PAYMENT_ACCOUNTING_ENTRY_POSTED", posted.getCode());
        assertEquals("ARCHIVED", cashJournalMapper.selectById(journal.getId()).getStatus());
    }

    private Long createApprovedExpense(BigDecimal amount) {
        ExpenseApplication expense = new ExpenseApplication();
        expense.setProjectId(PROJECT_ID);
        expense.setContractId(CONTRACT_ID);
        expense.setCostSubjectId(SUBJECT_ID);
        expense.setBudgetLineId(BUDGET_LINE_ID);
        expense.setPayeePartnerId(PARTNER_ID);
        expense.setExpenseCategory("LABOR");
        expense.setExpenseDate(LocalDate.now());
        expense.setAmount(amount);
        expense.setDescription("付款闭环费用来源");
        Long id = expenseService.create(expense);
        attach("EXPENSE", id);
        expenseService.submit(id);
        expenseHandler.onApproved(context(instance(id)));
        return id;
    }

    private Long createPayment(BigDecimal amount) {
        return createPayment(amount, "PROGRESS");
    }

    private Long createPayment(BigDecimal amount, String payType) {
        PayApplication app = new PayApplication();
        app.setProjectId(PROJECT_ID);
        app.setContractId(CONTRACT_ID);
        app.setPartnerId(PARTNER_ID);
        app.setCostSubjectId(SUBJECT_ID);
        app.setBudgetLineId(BUDGET_LINE_ID);
        app.setExpenseCategory("LABOR");
        app.setApplyAmount(amount);
        app.setPayType(payType);
        app.setApplyReason("付款闭环集成测试");
        return applicationService.create(app);
    }

    private Long createSubcontractProgressPayment(BigDecimal amount) {
        PayApplication app = new PayApplication();
        app.setProjectId(PROJECT_ID);
        app.setContractId(CONTRACT_ID);
        app.setPartnerId(PARTNER_ID);
        app.setCostSubjectId(SUBJECT_ID);
        app.setBudgetLineId(BUDGET_LINE_ID);
        app.setExpenseCategory("SUBCONTRACT");
        app.setApplyAmount(amount);
        app.setPayType("PROGRESS");
        app.setApplyReason("分包计量进度款");
        return applicationService.create(app);
    }

    private Long createFinalPayment(BigDecimal amount) {
        PayApplication app = new PayApplication();
        app.setProjectId(PROJECT_ID);
        app.setContractId(CONTRACT_ID);
        app.setPartnerId(PARTNER_ID);
        app.setCostSubjectId(SUBJECT_ID);
        app.setBudgetLineId(BUDGET_LINE_ID);
        app.setExpenseCategory("SUBCONTRACT");
        app.setApplyAmount(amount);
        app.setPayType("FINAL");
        app.setApplyReason("分包终期结算款");
        return applicationService.create(app);
    }

    private void saveDirectSource(Long applicationId, BigDecimal amount) {
        PaymentApplicationSource source = new PaymentApplicationSource();
        source.setSourceType("DIRECT");
        source.setSourceRefId(applicationId);
        source.setSourceAmount(amount);
        sourceService.save(applicationId, List.of(source));
    }

    private void seedBusinessContext() {
        PmProject project = new PmProject();
        project.setId(PROJECT_ID);
        project.setTenantId(TENANT_ID);
        project.setProjectCode("PAYMENT-CLOSED-LOOP-IT");
        project.setProjectName("付款闭环集成测试项目");
        project.setStatus("ACTIVE");
        projectMapper.insert(project);

        CostSubject subject = new CostSubject();
        subject.setId(SUBJECT_ID);
        subject.setTenantId(TENANT_ID);
        subject.setParentId(0L);
        subject.setSubjectCode("PAYMENT-CLOSED-LOOP-SUBJECT");
        subject.setSubjectName("付款闭环科目");
        subject.setSubjectType("DETAIL");
        subject.setAccountCategory("COST");
        subject.setLevel(1);
        subject.setSortOrder(1);
        subject.setStatus("ENABLE");
        subjectMapper.insert(subject);

        MdPartner partner = new MdPartner();
        partner.setId(PARTNER_ID);
        partner.setTenantId(TENANT_ID);
        partner.setPartnerCode("PAYMENT-CLOSED-LOOP-PARTNER");
        partner.setPartnerName("付款闭环收款对象");
        partner.setPartnerType("SUBCONTRACTOR");
        partner.setBankName("付款闭环银行");
        partner.setBankAccount("6222000012345678");
        partner.setContactPhone("13812345678");
        partner.setStatus("ENABLE");
        partnerMapper.insert(partner);

        CtContract contract = new CtContract();
        contract.setId(CONTRACT_ID);
        contract.setTenantId(TENANT_ID);
        contract.setProjectId(PROJECT_ID);
        contract.setContractCode("PAYMENT-CLOSED-LOOP-CONTRACT");
        contract.setContractName("付款闭环合同");
        contract.setContractType("SUBCONTRACT");
        contract.setPartyAId(PARTNER_ID);
        contract.setPartyBId(PARTNER_ID);
        contract.setContractAmount(new BigDecimal("5000.00"));
        contract.setCurrentAmount(new BigDecimal("5000.00"));
        contract.setPaidAmount(BigDecimal.ZERO);
        contract.setContractStatus("PERFORMING");
        contract.setApprovalStatus("APPROVED");
        contract.setVersion(0);
        contractMapper.insert(contract);

        ProjectBudget budget = new ProjectBudget();
        budget.setId(BUDGET_ID);
        budget.setTenantId(TENANT_ID);
        budget.setProjectId(PROJECT_ID);
        budget.setBudgetCode("BUD-PAYMENT-CLOSED-LOOP");
        budget.setVersionNo("V1");
        budget.setBudgetName("付款闭环预算");
        budget.setTotalAmount(new BigDecimal("1000.00"));
        budget.setApprovalStatus("APPROVED");
        budget.setStatus("ACTIVE");
        budget.setActiveFlag(1);
        budget.setActiveToken(PROJECT_ID);
        budget.setEffectiveAt(LocalDateTime.now());
        budget.setVersion(0);
        budgetMapper.insert(budget);

        ProjectBudgetLine line = new ProjectBudgetLine();
        line.setId(BUDGET_LINE_ID);
        line.setTenantId(TENANT_ID);
        line.setBudgetId(BUDGET_ID);
        line.setProjectId(PROJECT_ID);
        line.setCostSubjectId(SUBJECT_ID);
        line.setBudgetAmount(new BigDecimal("1000.00"));
        line.setReservedAmount(BigDecimal.ZERO);
        line.setConsumedAmount(BigDecimal.ZERO);
        line.setVersion(0);
        lineMapper.insert(line);

        jdbcTemplate.update("""
                INSERT INTO contract_budget_allocation
                    (id, tenant_id, project_id, contract_id, budget_line_id, allocated_amount,
                     reserved_amount, consumed_amount, version, deleted_flag)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0, 0, 0)
                """, CONTRACT_BUDGET_ALLOCATION_ID, TENANT_ID, PROJECT_ID, CONTRACT_ID,
                BUDGET_LINE_ID, new BigDecimal("1000.00"));

        FundAccount account = new FundAccount();
        account.setId(FUND_ACCOUNT_ID);
        account.setTenantId(TENANT_ID);
        account.setAccountCode("PAYMENT-CLOSED-LOOP-ACCOUNT");
        account.setAccountName("付款闭环测试账户");
        account.setAccountType("BANK");
        account.setOpeningDate(LocalDate.now().minusYears(1));
        account.setOpeningBalance(new BigDecimal("10000.00"));
        account.setEnabledFlag(1);
        account.setVersion(0);
        fundAccountMapper.insert(account);
    }

    private void seedAccountingSubjects() {
        Object[][] subjects = {
                {98300120L, "1002-BANK", "银行存款", "ASSET", 10},
                {98300121L, "1122-AR", "应收账款", "ASSET", 20},
                {98300122L, "1123-PREPAY", "预付账款", "ASSET", 30},
                {98300123L, "2202-AP", "应付账款", "LIABILITY", 40},
                {98300124L, "2203-ADVANCE", "预收账款", "LIABILITY", 50}
        };
        for (Object[] subject : subjects) {
            jdbcTemplate.update("""
                    INSERT INTO cost_subject
                      (id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,
                       level,sort_order,status,remark,created_at,updated_at,deleted_flag)
                    SELECT ?,0,0,?,?, 'GENERAL_LEDGER',?,1,?,'ENABLE','PAYMENT_CLOSED_LOOP_FIXTURE',
                           CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
                    WHERE NOT EXISTS(SELECT 1 FROM cost_subject WHERE tenant_id=0 AND subject_code=? AND deleted_flag=0)
                    """, subject[0], subject[1], subject[2], subject[3], subject[4], subject[1]);
        }
    }

    private void attach(String businessType, Long businessId) {
        attachCashJournal(businessId, switch (businessType) {
            case "INVOICE" -> "ELECTRONIC_INVOICE";
            case "PAYMENT" -> "PAYMENT_PROOF";
            case "CASH_JOURNAL" -> "BANK_RECEIPT";
            default -> "OTHER";
        }, "CLEAN", businessType);
    }

    private void attachCashJournal(Long businessId, String documentType, String virusScanStatus) {
        attachCashJournal(businessId, documentType, virusScanStatus, "CASH_JOURNAL");
    }

    private void attachCashJournal(
            Long businessId, String documentType, String virusScanStatus, String businessType) {
        SysFile file = new SysFile();
        file.setTenantId(TENANT_ID);
        file.setBusinessType(businessType);
        file.setBusinessId(businessId);
        file.setFileName(businessType + "-" + businessId + ".pdf");
        file.setOriginalName("测试凭证.pdf");
        file.setFileSize(100L);
        file.setContentType("application/pdf");
        file.setStoragePath(businessType + "/" + businessId + "/proof.pdf");
        file.setBucketName("test");
        file.setDocumentType(documentType);
        file.setVirusScanStatus(virusScanStatus);
        fileMapper.insert(file);
    }

    private void createAllocatedInvoice(Long payRecordId, String invoiceNo, String amount) {
        PayInvoice invoice = new PayInvoice();
        invoice.setPayRecordId(payRecordId);
        invoice.setInvoiceNo(invoiceNo);
        invoice.setInvoiceType("VAT_SPECIAL");
        invoice.setDocumentType("ELECTRONIC_INVOICE");
        invoice.setInvoiceAmount(new BigDecimal(amount));
        invoice.setInvoiceDate(LocalDate.now());
        Long invoiceId = invoiceService.create(invoice);
        InvoicePaymentAllocation allocation = new InvoicePaymentAllocation();
        allocation.setPayRecordId(payRecordId);
        allocation.setAllocatedAmount(new BigDecimal(amount));
        invoiceService.saveAllocations(invoiceId, List.of(allocation));
        attach("INVOICE", invoiceId);
        invoiceService.verify(invoiceId, "VERIFIED");
    }

    private WorkflowContext context(WfInstance instance) {
        WorkflowContext context = new WorkflowContext();
        context.setInstance(instance);
        return context;
    }

    private WfInstance instance(Long businessId) {
        return instance(businessId, 1);
    }

    private WfInstance instance(Long businessId, int round) {
        WfInstance instance = new WfInstance();
        instance.setTenantId(TENANT_ID);
        instance.setBusinessId(businessId);
        instance.setCurrentRound(round);
        return instance;
    }

    private void setContext() {
        UserContext.set(Jwts.claims().add("userId", 1L).add("username", "admin")
                .add("tenantId", TENANT_ID).add("roleCodes", List.of("ADMIN")).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "n/a", List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("payment:direct"))));
    }

    private void ensureWorkflowApprover() {
        jdbcTemplate.update("""
                INSERT INTO sys_user
                    (id,tenant_id,username,password,real_name,status,is_admin,created_by,remark)
                SELECT 1,0,'admin','test','付款闭环审批人','ENABLE',1,1,'payment-closed-loop-test'
                 WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id=1)
                """);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private void hardCleanup() {
        jdbcTemplate.update("DELETE FROM mandatory_audit_expectation WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM finance_audit_event WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM payment_document_link WHERE cash_journal_id IN (SELECT id FROM cash_journal_entry WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM cash_journal_change_log WHERE journal_entry_id IN (SELECT id FROM cash_journal_entry WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM accounting_entry_line WHERE entry_id IN (SELECT id FROM accounting_entry WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("UPDATE accounting_entry SET reversed_entry_id = NULL WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM accounting_entry WHERE project_id = ? AND original_entry_id IS NOT NULL", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM accounting_entry WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("UPDATE cash_journal_entry SET reverse_of_entry_id = NULL, reversal_entry_id = NULL WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM cash_journal_entry WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM payment_record_source_allocation WHERE pay_record_id IN (SELECT id FROM pay_record WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM sys_file WHERE business_id IN (SELECT id FROM pay_invoice WHERE project_id = ?) AND business_type = 'INVOICE'", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM invoice_payment_allocation WHERE invoice_id IN (SELECT id FROM pay_invoice WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM pay_invoice WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM payment_application_source WHERE pay_application_id IN (SELECT id FROM pay_application WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM sys_file WHERE business_id IN (SELECT id FROM pay_application WHERE project_id = ?) AND business_type = 'PAYMENT'", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM sys_file WHERE business_id IN (SELECT id FROM expense_application WHERE project_id = ?) AND business_type = 'EXPENSE'", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM budget_ledger WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM pay_application_basis WHERE pay_application_id IN (SELECT id FROM pay_application WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("UPDATE pay_record SET reversed_record_id = NULL WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM pay_record WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM pay_application WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM wf_record WHERE instance_id IN (SELECT id FROM wf_instance WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM wf_task WHERE instance_id IN (SELECT id FROM wf_instance WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM wf_node_instance WHERE instance_id IN (SELECT id FROM wf_instance WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM wf_cc WHERE instance_id IN (SELECT id FROM wf_instance WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM wf_instance WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM expense_application WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM settlement_sub_measure WHERE settlement_id IN (SELECT id FROM stl_settlement WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM stl_settlement WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM sub_measure_item WHERE measure_id IN (SELECT id FROM sub_measure WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM sub_measure WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM sub_task WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM contract_budget_allocation WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM project_budget_line WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM project_budget WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM fund_account WHERE id = ?", FUND_ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM ct_contract WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM pm_project WHERE id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM md_partner WHERE id = ?", PARTNER_ID);
        jdbcTemplate.update("DELETE FROM cost_subject WHERE id = ?", SUBJECT_ID);
        jdbcTemplate.update("DELETE FROM cost_subject WHERE remark = 'PAYMENT_CLOSED_LOOP_FIXTURE'");
    }
}
