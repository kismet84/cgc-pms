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
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowContext;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.accounting.mapper.AccountingEntryMapper;
import com.cgcpms.accounting.mapper.AccountingEntryLineMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
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
    @Autowired private StlSettlementMapper settlementMapper;
    @Autowired private SubMeasureMapper subMeasureMapper;
    @Autowired private SubTaskMapper subTaskMapper;
    @Autowired private InvoiceService invoiceService;
    @Autowired private PaymentTraceService traceService;
    @Autowired private PaymentReversalService reversalService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private WorkflowEngine workflowEngine;

    @BeforeEach
    void setUp() {
        setContext();
        hardCleanup();
        seedBusinessContext();
        doAnswer(invocation -> {
            WfInstance instance = new WfInstance();
            Long businessId = invocation.getArgument(4);
            instance.setId(990000000L + businessId);
            instance.setTenantId(TENANT_ID);
            instance.setBusinessId(businessId);
            instance.setCurrentRound(1);
            instance.setTemplateId(50010L);
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
        assertEquals(PaymentIntegrityConstants.CLOSED_LOOP_V1, submitted.getIntegrityVersion());
        assertMoney("600.00", lineMapper.selectById(BUDGET_LINE_ID).getReservedAmount());

        paymentHandler.onRejected(context(instance(applicationId)));
        assertEquals("REJECTED", applicationMapper.selectById(applicationId).getApprovalStatus());
        assertMoney("0.00", lineMapper.selectById(BUDGET_LINE_ID).getReservedAmount());
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
    }

    @Test
    @DisplayName("来源合计不等于申请金额和预算不足均失败关闭")
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
        assertEquals("BUDGET_INSUFFICIENT", insufficient.getCode());
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

        ProjectBudgetLine line = lineMapper.selectById(BUDGET_LINE_ID);
        assertMoney("200.00", line.getReservedAmount());
        assertMoney("300.00", line.getConsumedAmount());
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
        assertEquals("DRAFT", entry.getEntryStatus());
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

        var trace = traceService.byCashJournal(journal.getId());
        assertEquals(applicationId, trace.getPaymentApplication().getId());
        assertEquals(1, trace.getPaymentRecords().size());
        assertEquals(1, trace.getCashJournals().size());
        assertEquals(1, trace.getInvoices().size());
        assertEquals(1, trace.getAccountingEntries().size());
        assertEquals(2, trace.getAccountingEntryLines().size());
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

        assertEquals("REVERSED", payRecordService.getById(paidId).getPayStatus());
        assertEquals("REVERSAL", reversal.getPayStatus());
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
        PayApplication app = new PayApplication();
        app.setProjectId(PROJECT_ID);
        app.setContractId(CONTRACT_ID);
        app.setPartnerId(PARTNER_ID);
        app.setCostSubjectId(SUBJECT_ID);
        app.setBudgetLineId(BUDGET_LINE_ID);
        app.setExpenseCategory("LABOR");
        app.setApplyAmount(amount);
        app.setPayType("BANK_TRANSFER");
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

    private void attach(String businessType, Long businessId) {
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
        file.setDocumentType(switch (businessType) {
            case "INVOICE" -> "ELECTRONIC_INVOICE";
            case "PAYMENT" -> "PAYMENT_PROOF";
            default -> "OTHER";
        });
        fileMapper.insert(file);
    }

    private WorkflowContext context(WfInstance instance) {
        WorkflowContext context = new WorkflowContext();
        context.setInstance(instance);
        return context;
    }

    private WfInstance instance(Long businessId) {
        WfInstance instance = new WfInstance();
        instance.setTenantId(TENANT_ID);
        instance.setBusinessId(businessId);
        instance.setCurrentRound(1);
        return instance;
    }

    private void setContext() {
        UserContext.set(Jwts.claims().add("userId", 1L).add("username", "admin")
                .add("tenantId", TENANT_ID).add("roleCodes", List.of("ADMIN")).build());
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private void hardCleanup() {
        jdbcTemplate.update("DELETE FROM cash_journal_change_log WHERE journal_entry_id IN (SELECT id FROM cash_journal_entry WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("UPDATE cash_journal_entry SET reverse_of_entry_id = NULL, reversal_entry_id = NULL WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM cash_journal_entry WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM payment_record_source_allocation WHERE pay_record_id IN (SELECT id FROM pay_record WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM accounting_entry_line WHERE entry_id IN (SELECT id FROM accounting_entry WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("UPDATE accounting_entry SET reversed_entry_id = NULL WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM accounting_entry WHERE project_id = ? AND original_entry_id IS NOT NULL", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM accounting_entry WHERE project_id = ?", PROJECT_ID);
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
        jdbcTemplate.update("DELETE FROM ct_contract WHERE id = ?", CONTRACT_ID);
        jdbcTemplate.update("DELETE FROM pm_project WHERE id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM md_partner WHERE id = ?", PARTNER_ID);
        jdbcTemplate.update("DELETE FROM cost_subject WHERE id = ?", SUBJECT_ID);
    }
}
