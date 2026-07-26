package com.cgcpms.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.accounting.mapper.AccountingEntryLineMapper;
import com.cgcpms.accounting.mapper.AccountingEntryMapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.entity.BudgetLedger;
import com.cgcpms.budget.mapper.BudgetLedgerMapper;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.expense.entity.ExpenseApplication;
import com.cgcpms.expense.mapper.ExpenseApplicationMapper;
import com.cgcpms.invoice.entity.InvoicePaymentAllocation;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.InvoicePaymentAllocationMapper;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.entity.PaymentApplicationSource;
import com.cgcpms.payment.entity.PaymentRecordSourceAllocation;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.mapper.PaymentApplicationSourceMapper;
import com.cgcpms.payment.mapper.PaymentRecordSourceAllocationMapper;
import com.cgcpms.payment.vo.PaymentTraceVO;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.entity.SettlementSubMeasure;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.settlement.mapper.SettlementSubMeasureMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubTask;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfRecord;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentTraceService {
    private final CashJournalEntryMapper cashJournalMapper;
    private final PayRecordMapper recordMapper;
    private final PayApplicationMapper applicationMapper;
    private final PaymentApplicationSourceMapper sourceMapper;
    private final PaymentRecordSourceAllocationMapper sourceAllocationMapper;
    private final ExpenseApplicationMapper expenseMapper;
    private final StlSettlementMapper settlementMapper;
    private final SettlementSubMeasureMapper settlementSubMeasureMapper;
    private final SubMeasureMapper subMeasureMapper;
    private final SubTaskMapper subTaskMapper;
    private final PmProjectMapper projectMapper;
    private final CtContractMapper contractMapper;
    private final WfInstanceMapper instanceMapper;
    private final WfRecordMapper workflowRecordMapper;
    private final PayInvoiceMapper invoiceMapper;
    private final InvoicePaymentAllocationMapper invoiceAllocationMapper;
    private final BudgetLedgerMapper budgetLedgerMapper;
    private final AccountingEntryMapper accountingEntryMapper;
    private final AccountingEntryLineMapper accountingLineMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final JdbcTemplate jdbc;

    public PaymentTraceVO byCashJournal(Long cashJournalId) {
        CashJournalEntry journal = cashJournalMapper.selectById(cashJournalId);
        if (journal == null || !Objects.equals(journal.getTenantId(), UserContext.getCurrentTenantId())) {
            throw new BusinessException("CASH_JOURNAL_NOT_FOUND", "资金流水不存在");
        }
        Long applicationId = journal.getPayApplicationId();
        if (applicationId == null && journal.getPayRecordId() != null) {
            PayRecord record = recordMapper.selectById(journal.getPayRecordId());
            applicationId = record == null ? null : record.getPayApplicationId();
        }
        if (applicationId == null) {
            throw new BusinessException("PAYMENT_TRACE_INCOMPLETE", "现金日记缺少付款申请显式关系");
        }
        PaymentTraceVO trace = byApplication(applicationId);
        if (!Objects.equals(journal.getProjectId(), trace.getPaymentApplication().getProjectId())
                || !Objects.equals(journal.getContractId(), trace.getPaymentApplication().getContractId())
                || trace.getCashJournals().stream().noneMatch(item -> Objects.equals(item.getId(), journal.getId()))) {
            throw incomplete("现金日记与付款申请关系不一致");
        }
        return trace;
    }

    public PaymentTraceVO byPayRecord(Long payRecordId) {
        PayRecord record = recordMapper.selectById(payRecordId);
        if (record == null || !Objects.equals(record.getTenantId(), UserContext.getCurrentTenantId())) {
            throw new BusinessException("PAY_RECORD_NOT_FOUND", "付款记录不存在");
        }
        PaymentTraceVO trace = byApplication(record.getPayApplicationId());
        if (trace.getPaymentRecords().stream().noneMatch(item -> Objects.equals(item.getId(), record.getId()))) {
            throw incomplete("付款记录与付款申请关系不一致");
        }
        return trace;
    }

    public List<PaymentTraceVO> byExpense(Long expenseId) {
        ExpenseApplication expense = expenseMapper.selectById(expenseId);
        requireTenantProject(expense == null ? null : expense.getTenantId(),
                expense == null ? null : expense.getProjectId(), "EXPENSE_NOT_FOUND", "费用申请不存在");
        return traces(sourceMapper.selectList(new LambdaQueryWrapper<PaymentApplicationSource>()
                .eq(PaymentApplicationSource::getTenantId, tenant())
                .eq(PaymentApplicationSource::getExpenseId, expenseId)).stream()
                .map(PaymentApplicationSource::getPayApplicationId).toList(), "费用申请尚未形成付款链");
    }

    public List<PaymentTraceVO> bySettlement(Long settlementId) {
        StlSettlement settlement = settlementMapper.selectById(settlementId);
        requireTenantProject(settlement == null ? null : settlement.getTenantId(),
                settlement == null ? null : settlement.getProjectId(), "SETTLEMENT_NOT_FOUND", "结算申请不存在");
        return traces(sourceMapper.selectList(new LambdaQueryWrapper<PaymentApplicationSource>()
                .eq(PaymentApplicationSource::getTenantId, tenant())
                .eq(PaymentApplicationSource::getSettlementId, settlementId)).stream()
                .map(PaymentApplicationSource::getPayApplicationId).toList(), "结算申请尚未形成付款链");
    }

    public List<PaymentTraceVO> byApproval(Long approvalInstanceId) {
        WfInstance instance = instanceMapper.selectById(approvalInstanceId);
        requireTenantProject(instance == null ? null : instance.getTenantId(),
                instance == null ? null : instance.getProjectId(), "APPROVAL_INSTANCE_NOT_FOUND", "审批实例不存在");
        if (!"PAY_REQUEST".equals(instance.getBusinessType())) {
            throw incomplete("审批实例不是付款申请审批");
        }
        return traces(applicationMapper.selectList(new LambdaQueryWrapper<PayApplication>()
                .eq(PayApplication::getTenantId, tenant())
                .eq(PayApplication::getApprovalInstanceId, approvalInstanceId)).stream()
                .map(PayApplication::getId).toList(), "审批实例未绑定付款申请");
    }

    public List<PaymentTraceVO> byInvoice(Long invoiceId) {
        PayInvoice invoice = invoiceMapper.selectById(invoiceId);
        requireTenantProject(invoice == null ? null : invoice.getTenantId(),
                invoice == null ? null : invoice.getProjectId(), "INVOICE_NOT_FOUND", "发票不存在");
        Set<Long> applicationIds = new HashSet<>();
        if (invoice.getPayApplicationId() != null) applicationIds.add(invoice.getPayApplicationId());
        invoiceAllocationMapper.selectList(new LambdaQueryWrapper<InvoicePaymentAllocation>()
                .eq(InvoicePaymentAllocation::getTenantId, tenant())
                .eq(InvoicePaymentAllocation::getInvoiceId, invoiceId)).stream()
                .map(InvoicePaymentAllocation::getPayApplicationId).filter(Objects::nonNull)
                .forEach(applicationIds::add);
        List<PaymentTraceVO> result = traces(applicationIds, "发票未绑定付款分配");
        if (result.stream().anyMatch(trace -> trace.getInvoices().stream()
                .noneMatch(item -> Objects.equals(item.getId(), invoiceId)))) {
            throw incomplete("发票分配与付款链关系不一致");
        }
        return result;
    }

    public List<PaymentTraceVO> byVoucher(Long accountingEntryId) {
        AccountingEntry entry = accountingEntryMapper.selectById(accountingEntryId);
        requireTenantProject(entry == null ? null : entry.getTenantId(),
                entry == null ? null : entry.getProjectId(), "ACCOUNTING_ENTRY_NOT_FOUND", "会计凭证不存在");
        Long applicationId = entry.getPayApplicationId();
        if (applicationId == null && entry.getPayRecordId() != null) {
            PayRecord record = recordMapper.selectById(entry.getPayRecordId());
            applicationId = record == null ? null : record.getPayApplicationId();
        }
        List<PaymentTraceVO> result = traces(applicationId == null ? List.of() : List.of(applicationId),
                "会计凭证未绑定付款申请");
        if (result.getFirst().getAccountingEntries().stream()
                .noneMatch(item -> Objects.equals(item.getId(), accountingEntryId))) {
            throw incomplete("会计凭证与付款链关系不一致");
        }
        return result;
    }

    public List<PaymentTraceVO> byContract(Long contractId) {
        CtContract contract = contractMapper.selectById(contractId);
        requireTenantProject(contract == null ? null : contract.getTenantId(),
                contract == null ? null : contract.getProjectId(), "CONTRACT_NOT_FOUND", "合同不存在");
        return traces(applicationMapper.selectList(new LambdaQueryWrapper<PayApplication>()
                .eq(PayApplication::getTenantId, tenant())
                .eq(PayApplication::getContractId, contractId)).stream()
                .map(PayApplication::getId).toList(), "合同尚未形成付款链");
    }

    public List<PaymentTraceVO> byProject(Long projectId) {
        PmProject project = projectMapper.selectById(projectId);
        requireTenantProject(project == null ? null : project.getTenantId(),
                project == null ? null : project.getId(), "PROJECT_NOT_FOUND", "项目不存在");
        return traces(applicationMapper.selectList(new LambdaQueryWrapper<PayApplication>()
                .eq(PayApplication::getTenantId, tenant())
                .eq(PayApplication::getProjectId, projectId)).stream()
                .map(PayApplication::getId).toList(), "项目尚未形成付款链");
    }

    public PaymentTraceVO byApplication(Long applicationId) {
        Long tenantId = UserContext.getCurrentTenantId();
        PayApplication app = applicationMapper.selectById(applicationId);
        if (app == null || !Objects.equals(app.getTenantId(), tenantId)) {
            throw new BusinessException("PAY_APP_NOT_FOUND", "付款申请不存在");
        }
        projectAccessChecker.checkAccess(app.getProjectId(), "查看付款全链路");
        PaymentTraceVO trace = new PaymentTraceVO();
        trace.setPaymentApplication(app);
        PmProject project = projectMapper.selectById(app.getProjectId());
        CtContract contract = contractMapper.selectById(app.getContractId());
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)
                || contract == null || !Objects.equals(contract.getTenantId(), tenantId)
                || !Objects.equals(contract.getProjectId(), app.getProjectId())) {
            throw incomplete("付款申请的项目或合同关系不一致");
        }
        trace.setProject(project);
        trace.setContract(contract);

        WfInstance instance = app.getApprovalInstanceId() == null ? null : instanceMapper.selectById(app.getApprovalInstanceId());
        trace.setApprovalInstance(instance);
        trace.setApprovalRecords(instance == null ? List.of() : workflowRecordMapper.selectList(
                new LambdaQueryWrapper<WfRecord>().eq(WfRecord::getTenantId, tenantId)
                        .eq(WfRecord::getInstanceId, instance.getId()).orderByAsc(WfRecord::getCreatedAt)));

        List<PaymentApplicationSource> sources = sourceMapper.selectList(new LambdaQueryWrapper<PaymentApplicationSource>()
                .eq(PaymentApplicationSource::getTenantId, tenantId)
                .eq(PaymentApplicationSource::getPayApplicationId, applicationId));
        trace.setApplicationSources(sources);
        List<Long> expenseIds = sources.stream().map(PaymentApplicationSource::getExpenseId).filter(Objects::nonNull).distinct().toList();
        List<Long> settlementIds = sources.stream().map(PaymentApplicationSource::getSettlementId).filter(Objects::nonNull).distinct().toList();
        List<Long> directMeasureIds = sources.stream().map(PaymentApplicationSource::getSubMeasureId)
                .filter(Objects::nonNull).distinct().toList();
        trace.setExpenses(expenseIds.isEmpty() ? List.of() : expenseMapper.selectByIds(expenseIds));
        trace.setSettlements(settlementIds.isEmpty() ? List.of() : settlementMapper.selectByIds(settlementIds));
        List<SettlementSubMeasure> settlementMeasureLinks = settlementIds.isEmpty() ? List.of()
                : settlementSubMeasureMapper.selectList(new LambdaQueryWrapper<SettlementSubMeasure>()
                    .eq(SettlementSubMeasure::getTenantId, tenantId)
                    .in(SettlementSubMeasure::getSettlementId, settlementIds));
        trace.setSettlementSubMeasures(settlementMeasureLinks);
        Set<Long> measureIds = new HashSet<>(directMeasureIds);
        settlementMeasureLinks.stream().map(SettlementSubMeasure::getSubMeasureId).forEach(measureIds::add);
        List<SubMeasure> measures = measureIds.isEmpty() ? List.of() : subMeasureMapper.selectByIds(measureIds).stream()
                .filter(measure -> Objects.equals(measure.getTenantId(), tenantId)).toList();
        trace.setSubMeasures(measures);
        List<Long> taskIds = measures.stream().map(SubMeasure::getSubTaskId).filter(Objects::nonNull).distinct().toList();
        trace.setSubTasks(taskIds.isEmpty() ? List.of() : subTaskMapper.selectByIds(taskIds).stream()
                .filter(task -> Objects.equals(task.getTenantId(), tenantId)).toList());

        List<PayRecord> records = recordMapper.selectList(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, tenantId).eq(PayRecord::getPayApplicationId, applicationId)
                .orderByAsc(PayRecord::getPaidAt));
        trace.setPaymentRecords(records);
        List<Long> recordIds = records.stream().map(PayRecord::getId).toList();
        trace.setPaymentSourceAllocations(recordIds.isEmpty() ? List.of() : sourceAllocationMapper.selectList(
                new LambdaQueryWrapper<PaymentRecordSourceAllocation>()
                        .eq(PaymentRecordSourceAllocation::getTenantId, tenantId)
                        .in(PaymentRecordSourceAllocation::getPayRecordId, recordIds)));
        trace.setCashJournals(recordIds.isEmpty() ? List.of() : cashJournalMapper.selectList(
                new LambdaQueryWrapper<CashJournalEntry>().eq(CashJournalEntry::getTenantId, tenantId)
                        .in(CashJournalEntry::getPayRecordId, recordIds)));
        List<Long> journalIds = trace.getCashJournals().stream().map(CashJournalEntry::getId).toList();
        trace.setPaymentDocuments(journalIds.isEmpty() ? List.of() : jdbc.queryForList("""
                SELECT id,cash_journal_id,file_id,document_type,created_at
                  FROM payment_document_link
                 WHERE tenant_id=? AND cash_journal_id IN (%s)
                 ORDER BY created_at,id
                """.formatted("?,".repeat(journalIds.size()).replaceFirst(",$", "")),
                args(tenantId, journalIds)));

        List<InvoicePaymentAllocation> invoiceAllocations = recordIds.isEmpty() ? List.of() : invoiceAllocationMapper.selectList(
                new LambdaQueryWrapper<InvoicePaymentAllocation>().eq(InvoicePaymentAllocation::getTenantId, tenantId)
                        .in(InvoicePaymentAllocation::getPayRecordId, recordIds));
        trace.setInvoiceAllocations(invoiceAllocations);
        List<Long> invoiceIds = invoiceAllocations.stream().map(InvoicePaymentAllocation::getInvoiceId).distinct().toList();
        trace.setInvoices(invoiceIds.isEmpty() ? List.of() : invoiceMapper.selectByIds(invoiceIds));

        Set<String> businessKeys = new HashSet<>();
        businessKeys.add("PAY_REQUEST:" + applicationId);
        expenseIds.forEach(id -> businessKeys.add("EXPENSE:" + id));
        List<BudgetLedger> ledgers = budgetLedgerMapper.selectList(new LambdaQueryWrapper<BudgetLedger>()
                .eq(BudgetLedger::getTenantId, tenantId).eq(BudgetLedger::getProjectId, app.getProjectId())
                .orderByAsc(BudgetLedger::getCreatedAt)).stream()
                .filter(l -> businessKeys.contains(l.getBusinessType() + ":" + l.getBusinessId())).toList();
        trace.setBudgetLedgers(ledgers);

        List<AccountingEntry> entries = recordIds.isEmpty() ? List.of() : accountingEntryMapper.selectList(
                new LambdaQueryWrapper<AccountingEntry>().eq(AccountingEntry::getTenantId, tenantId)
                        .in(AccountingEntry::getPayRecordId, recordIds));
        trace.setAccountingEntries(entries);
        List<Long> entryIds = entries.stream().map(AccountingEntry::getId).toList();
        trace.setAccountingEntryLines(entryIds.isEmpty() ? List.of() : accountingLineMapper.selectList(
                new LambdaQueryWrapper<AccountingEntryLine>().eq(AccountingEntryLine::getTenantId, tenantId)
                        .in(AccountingEntryLine::getEntryId, entryIds)));
        validate(trace, tenantId);
        return trace;
    }

    private void validate(PaymentTraceVO trace, Long tenantId) {
        PayApplication app = trace.getPaymentApplication();
        Long projectId = app.getProjectId();
        Long contractId = app.getContractId();
        if (!"DRAFT".equals(app.getApprovalStatus()) && trace.getApprovalInstance() == null) {
            throw incomplete("付款申请缺少审批实例");
        }
        if (trace.getApprovalInstance() != null && (!Objects.equals(trace.getApprovalInstance().getTenantId(), tenantId)
                || !Objects.equals(trace.getApprovalInstance().getProjectId(), projectId)
                || !Objects.equals(trace.getApprovalInstance().getBusinessId(), app.getId())
                || !"PAY_REQUEST".equals(trace.getApprovalInstance().getBusinessType()))) {
            throw incomplete("审批实例与付款申请关系不一致");
        }
        if (!"DRAFT".equals(app.getApprovalStatus()) && trace.getApprovalRecords().isEmpty()) {
            throw incomplete("付款申请缺少审批记录");
        }
        if (trace.getApprovalRecords().stream().anyMatch(record ->
                !Objects.equals(record.getTenantId(), tenantId)
                        || !Objects.equals(record.getInstanceId(), trace.getApprovalInstance().getId())
                        || !Objects.equals(record.getBusinessId(), app.getId())
                        || !"PAY_REQUEST".equals(record.getBusinessType()))) {
            throw incomplete("审批记录与付款申请关系不一致");
        }
        if (!"DRAFT".equals(app.getApprovalStatus())
                && (trace.getApplicationSources().isEmpty() || trace.getBudgetLedgers().isEmpty())) {
            throw incomplete("付款申请缺少来源或预算台账");
        }
        if (trace.getApplicationSources().stream().anyMatch(source ->
                !Objects.equals(source.getTenantId(), tenantId)
                        || !Objects.equals(source.getPayApplicationId(), app.getId())
                        || ("DIRECT".equals(source.getSourceType())
                        && !Objects.equals(source.getSourceRefId(), app.getId())))) {
            throw incomplete("付款来源关系不一致");
        }
        BigDecimal sourceTotal = trace.getApplicationSources().stream()
                .map(PaymentApplicationSource::getSourceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!"DRAFT".equals(app.getApprovalStatus()) && sourceTotal.compareTo(app.getApplyAmount()) != 0) {
            throw incomplete("付款来源金额合计与申请金额不一致");
        }
        Set<String> budgetKeys = new HashSet<>();
        budgetKeys.add("PAY_REQUEST:" + app.getId());
        trace.getApplicationSources().stream().map(PaymentApplicationSource::getExpenseId)
                .filter(Objects::nonNull).forEach(id -> budgetKeys.add("EXPENSE:" + id));
        if (trace.getBudgetLedgers().stream().anyMatch(ledger ->
                !Objects.equals(ledger.getTenantId(), tenantId)
                        || !Objects.equals(ledger.getProjectId(), projectId)
                        || !budgetKeys.contains(ledger.getBusinessType() + ":" + ledger.getBusinessId()))) {
            throw incomplete("预算台账与付款申请关系不一致");
        }
        BigDecimal payRequestSourceTotal = trace.getApplicationSources().stream()
                .filter(source -> source.getExpenseId() == null)
                .map(PaymentApplicationSource::getSourceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (reservedAmount(trace, "PAY_REQUEST", app.getId()).compareTo(payRequestSourceTotal) < 0
                || trace.getApplicationSources().stream()
                .filter(source -> source.getExpenseId() != null)
                .anyMatch(source -> reservedAmount(trace, "EXPENSE", source.getExpenseId())
                        .compareTo(source.getSourceAmount()) < 0)) {
            throw incomplete("预算台账缺少付款来源对应的完整占用金额");
        }
        if (trace.getExpenses().size() != trace.getApplicationSources().stream()
                .map(PaymentApplicationSource::getExpenseId).filter(Objects::nonNull).distinct().count()
                || trace.getExpenses().stream().anyMatch(item -> !Objects.equals(item.getTenantId(), tenantId)
                || !Objects.equals(item.getProjectId(), projectId))) {
            throw incomplete("费用来源缺失或跨项目");
        }
        if (trace.getSettlements().size() != trace.getApplicationSources().stream()
                .map(PaymentApplicationSource::getSettlementId).filter(Objects::nonNull).distinct().count()
                || trace.getSettlements().stream().anyMatch(item -> !Objects.equals(item.getTenantId(), tenantId)
                || !Objects.equals(item.getProjectId(), projectId))) {
            throw incomplete("结算来源缺失或跨项目");
        }
        Map<Long, PayRecord> records = trace.getPaymentRecords().stream()
                .collect(java.util.stream.Collectors.toMap(PayRecord::getId, item -> item));
        if (records.values().stream().anyMatch(item -> !Objects.equals(item.getTenantId(), tenantId)
                || !Objects.equals(item.getProjectId(), projectId)
                || !Objects.equals(item.getContractId(), contractId)
                || !Objects.equals(item.getPayApplicationId(), app.getId()))) {
            throw incomplete("付款记录关系不一致");
        }
        if (trace.getCashJournals().stream().anyMatch(item -> !Objects.equals(item.getTenantId(), tenantId)
                || !Objects.equals(item.getProjectId(), projectId)
                || !Objects.equals(item.getContractId(), contractId)
                || !Objects.equals(item.getPayApplicationId(), app.getId())
                || !records.containsKey(item.getPayRecordId()))) {
            throw incomplete("现金日记关系不一致");
        }
        for (PayRecord record : records.values()) {
            if (!Set.of("SUCCESS", "REVERSED").contains(record.getPayStatus())) continue;
            BigDecimal allocated = trace.getPaymentSourceAllocations().stream()
                    .filter(item -> Objects.equals(item.getPayRecordId(), record.getId()))
                    .map(PaymentRecordSourceAllocation::getAllocatedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long journalCount = trace.getCashJournals().stream()
                    .filter(item -> Objects.equals(item.getPayRecordId(), record.getId())).count();
            long entryCount = trace.getAccountingEntries().stream()
                    .filter(item -> Objects.equals(item.getPayRecordId(), record.getId())
                            && "PAYMENT".equals(item.getEntryType())).count();
            if (allocated.compareTo(record.getPayAmount()) != 0 || journalCount != 1 || entryCount != 1) {
                throw incomplete("成功付款缺少完整来源分摊、现金日记或付款凭证");
            }
        }
        Set<Long> documentedJournalIds = trace.getPaymentDocuments().stream()
                .map(this::cashJournalId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (trace.getCashJournals().stream().anyMatch(item -> "ARCHIVED".equals(item.getStatus())
                && !documentedJournalIds.contains(item.getId()))) {
            throw incomplete("已归档现金日记缺少付款证据");
        }
        Map<Long, PayInvoice> invoices = trace.getInvoices().stream()
                .collect(java.util.stream.Collectors.toMap(PayInvoice::getId, item -> item));
        if (invoices.values().stream().anyMatch(item -> !Objects.equals(item.getTenantId(), tenantId)
                || !Objects.equals(item.getProjectId(), projectId)
                || !Objects.equals(item.getContractId(), contractId))
                || trace.getInvoiceAllocations().stream().anyMatch(item ->
                !Objects.equals(item.getTenantId(), tenantId)
                        || !Objects.equals(item.getPayApplicationId(), app.getId())
                        || !records.containsKey(item.getPayRecordId())
                        || !invoices.containsKey(item.getInvoiceId()))) {
            throw incomplete("发票分配关系不一致");
        }
        if (trace.getAccountingEntries().stream().anyMatch(item ->
                !Objects.equals(item.getTenantId(), tenantId)
                        || !Objects.equals(item.getProjectId(), projectId)
                        || !Objects.equals(item.getContractId(), contractId)
                        || !Objects.equals(item.getPayApplicationId(), app.getId())
                        || !records.containsKey(item.getPayRecordId()))) {
            throw incomplete("会计凭证关系不一致");
        }
        for (AccountingEntry entry : trace.getAccountingEntries()) {
            List<AccountingEntryLine> lines = trace.getAccountingEntryLines().stream()
                    .filter(item -> Objects.equals(item.getEntryId(), entry.getId())).toList();
            BigDecimal debit = lines.stream().filter(item -> "DEBIT".equals(item.getDirection()))
                    .map(AccountingEntryLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credit = lines.stream().filter(item -> "CREDIT".equals(item.getDirection()))
                    .map(AccountingEntryLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (lines.size() < 2 || debit.compareTo(credit) != 0
                    || debit.compareTo(entry.getTotalDebit()) != 0
                    || credit.compareTo(entry.getTotalCredit()) != 0) {
                throw incomplete("会计凭证缺少平衡分录");
            }
        }
    }

    private Long cashJournalId(Map<String, Object> document) {
        return document.entrySet().stream()
                .filter(item -> "cash_journal_id".equalsIgnoreCase(item.getKey()))
                .map(Map.Entry::getValue)
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::longValue)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal reservedAmount(PaymentTraceVO trace, String businessType, Long businessId) {
        return trace.getBudgetLedgers().stream()
                .filter(ledger -> businessType.equals(ledger.getBusinessType())
                        && Objects.equals(businessId, ledger.getBusinessId())
                        && "RESERVE".equals(ledger.getEntryType()))
                .map(BudgetLedger::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<PaymentTraceVO> traces(Collection<Long> applicationIds, String emptyMessage) {
        List<Long> ids = applicationIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) throw incomplete(emptyMessage);
        return ids.stream().map(this::byApplication).toList();
    }

    private void requireTenantProject(Long entityTenantId, Long projectId, String code, String message) {
        if (!Objects.equals(entityTenantId, tenant()) || projectId == null) {
            throw new BusinessException(code, message);
        }
        projectAccessChecker.checkAccess(projectId, "查看付款全链路");
    }

    private Long tenant() {
        return UserContext.getCurrentTenantId();
    }

    private Object[] args(Long tenantId, List<Long> ids) {
        Object[] values = new Object[ids.size() + 1];
        values[0] = tenantId;
        for (int i = 0; i < ids.size(); i++) values[i + 1] = ids.get(i);
        return values;
    }

    private BusinessException incomplete(String message) {
        return new BusinessException("PAYMENT_TRACE_INCOMPLETE", message);
    }
}
