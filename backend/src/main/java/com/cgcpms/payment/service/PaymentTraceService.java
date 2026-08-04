package com.cgcpms.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.accounting.mapper.AccountingEntryLineMapper;
import com.cgcpms.accounting.mapper.AccountingEntryMapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.entity.BudgetLedger;
import com.cgcpms.budget.entity.ContractBudgetAllocation;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.mapper.BudgetLedgerMapper;
import com.cgcpms.budget.mapper.ContractBudgetAllocationMapper;
import com.cgcpms.budget.mapper.ProjectBudgetLineMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
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
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
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
    private final ContractBudgetAllocationMapper contractBudgetAllocationMapper;
    private final ProjectBudgetLineMapper projectBudgetLineMapper;
    private final ProjectBudgetMapper projectBudgetMapper;
    private final CostSubjectMapper costSubjectMapper;
    private final MatReceiptItemMapper receiptItemMapper;
    private final MatReceiptMapper receiptMapper;
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
                SELECT link.id,target.id AS cash_journal_id,
                       link.cash_journal_id AS evidence_cash_journal_id,
                       link.file_id,link.document_type,link.created_at
                  FROM cash_journal_entry target
                  JOIN payment_document_link link
                    ON link.tenant_id=target.tenant_id
                   AND link.cash_journal_id=COALESCE(target.reverse_of_entry_id,target.id)
                 WHERE target.tenant_id=? AND target.id IN (%s)
                 ORDER BY target.id,link.created_at,link.id
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

        ProjectBudgetLine budgetLine = app.getBudgetLineId() == null
                ? null : projectBudgetLineMapper.selectById(app.getBudgetLineId());
        ProjectBudget budget = budgetLine == null ? null : projectBudgetMapper.selectById(budgetLine.getBudgetId());
        CostSubject subject = app.getCostSubjectId() == null
                ? null : costSubjectMapper.selectById(app.getCostSubjectId());
        ContractBudgetAllocation contractAllocation = app.getContractBudgetAllocationId() == null
                ? null : contractBudgetAllocationMapper.selectById(app.getContractBudgetAllocationId());
        trace.setProjectBudgetLine(budgetLine);
        trace.setProjectBudget(budget);
        trace.setCostSubject(subject);
        trace.setContractBudgetAllocation(contractAllocation);

        List<Long> receiptItemIds = sources.stream().map(PaymentApplicationSource::getReceiptItemId)
                .filter(Objects::nonNull).distinct().toList();
        List<MatReceiptItem> receiptItems = receiptItemIds.isEmpty() ? List.of()
                : receiptItemMapper.selectByIds(receiptItemIds).stream()
                        .filter(item -> Objects.equals(item.getTenantId(), tenantId)).toList();
        List<Long> receiptIds = receiptItems.stream().map(MatReceiptItem::getReceiptId)
                .filter(Objects::nonNull).distinct().toList();
        List<MatReceipt> receipts = receiptIds.isEmpty() ? List.of()
                : receiptMapper.selectByIds(receiptIds).stream()
                        .filter(item -> Objects.equals(item.getTenantId(), tenantId)).toList();
        trace.setMaterialReceiptItems(receiptItems);
        trace.setMaterialReceipts(receipts);

        BigDecimal netReserved = ledgerNet(ledgers, "RESERVE", "RESTORE_RESERVATION")
                .subtract(ledgerNet(ledgers, "RELEASE", "CONSUME"));
        BigDecimal netConsumed = ledgerNet(ledgers, "CONSUME")
                .subtract(ledgerNet(ledgers, "RESTORE_RESERVATION", "REVERSE"));
        BigDecimal netPaid = sources.stream().map(PaymentApplicationSource::getPaidAmount)
                .map(this::money).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netCashOutflow = trace.getCashJournals().stream()
                .filter(item -> item.getArchivedAt() != null || "ARCHIVED".equals(item.getStatus()))
                .map(item -> "OUT".equals(item.getDirection()) ? money(item.getAmount())
                        : money(item.getAmount()).negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        trace.setBudgetConservation(Map.of(
                "netReserved", money(netReserved).toPlainString(),
                "netConsumed", money(netConsumed).toPlainString(),
                "netPaid", money(netPaid).toPlainString(),
                "netCashOutflow", money(netCashOutflow).toPlainString()));

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
        validateSourceContexts(trace, tenantId, projectId, contractId);
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
        validateBudgetAndReceiptFacts(trace, tenantId, projectId, contractId);
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
        Map<Long, CashJournalEntry> journals = trace.getCashJournals().stream()
                .collect(java.util.stream.Collectors.toMap(CashJournalEntry::getId, item -> item));
        for (PayRecord record : records.values()) {
            long journalCount = trace.getCashJournals().stream()
                    .filter(item -> Objects.equals(item.getPayRecordId(), record.getId())).count();
            if (Set.of("SUCCESS", "REVERSED").contains(record.getPayStatus())) {
                BigDecimal allocated = trace.getPaymentSourceAllocations().stream()
                        .filter(item -> Objects.equals(item.getPayRecordId(), record.getId()))
                        .map(PaymentRecordSourceAllocation::getAllocatedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                long entryCount = trace.getAccountingEntries().stream()
                        .filter(item -> Objects.equals(item.getPayRecordId(), record.getId())
                                && "PAYMENT".equals(item.getEntryType())).count();
                if (allocated.compareTo(record.getPayAmount()) != 0 || journalCount != 1 || entryCount != 1) {
                    throw incomplete("成功付款缺少完整来源分摊、现金日记或付款凭证");
                }
            } else if ("REVERSAL".equals(record.getPayStatus())) {
                PayRecord original = records.get(record.getReversedRecordId());
                if (original == null || !"REVERSED".equals(original.getPayStatus())
                        || !Objects.equals(original.getReversedRecordId(), record.getId())) {
                    throw incomplete("冲销付款记录与原付款记录关系不一致");
                }
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
        if (trace.getCashJournals().stream().anyMatch(item -> item.getReverseOfEntryId() != null
                && (journals.get(item.getReverseOfEntryId()) == null
                || !Objects.equals(journals.get(item.getReverseOfEntryId()).getReversalEntryId(), item.getId())
                || !Objects.equals(journals.get(item.getReverseOfEntryId()).getAmount(), item.getAmount())
                || Objects.equals(journals.get(item.getReverseOfEntryId()).getDirection(), item.getDirection())))) {
            throw incomplete("反向现金日记与原日记关系不一致");
        }
        for (PayRecord original : records.values().stream()
                .filter(item -> "REVERSED".equals(item.getPayStatus())).toList()) {
            PayRecord reversal = records.get(original.getReversedRecordId());
            CashJournalEntry originalJournal = trace.getCashJournals().stream()
                    .filter(item -> Objects.equals(item.getPayRecordId(), original.getId())).findFirst().orElse(null);
            CashJournalEntry reversalJournal = reversal == null ? null : trace.getCashJournals().stream()
                    .filter(item -> Objects.equals(item.getPayRecordId(), reversal.getId())).findFirst().orElse(null);
            AccountingEntry originalEntry = trace.getAccountingEntries().stream()
                    .filter(item -> Objects.equals(item.getPayRecordId(), original.getId())
                            && "PAYMENT".equals(item.getEntryType())).findFirst().orElse(null);
            AccountingEntry reversalEntry = reversal == null ? null : trace.getAccountingEntries().stream()
                    .filter(item -> Objects.equals(item.getPayRecordId(), reversal.getId())
                            && "PAYMENT_REVERSAL".equals(item.getEntryType())).findFirst().orElse(null);
            if (reversal == null || originalJournal == null || originalEntry == null) {
                throw incomplete("冲销付款链缺少原始事实或冲销记录");
            }
            boolean archivedReversal = originalJournal.getReversalEntryId() != null;
            if (archivedReversal) {
                if (reversalJournal == null || reversalEntry == null
                        || !Objects.equals(reversalJournal.getReverseOfEntryId(), originalJournal.getId())
                        || !Objects.equals(originalJournal.getReversalEntryId(), reversalJournal.getId())
                        || !Objects.equals(originalEntry.getReversedEntryId(), reversalEntry.getId())
                        || !Objects.equals(reversalEntry.getOriginalEntryId(), originalEntry.getId())
                        || !Objects.equals(reversal.getPaidAt(), reversalJournal.getArchivedAt())) {
                    throw incomplete("已归档付款冲销链缺少双向现金日记或会计凭证关系");
                }
            } else if (reversalJournal != null || reversalEntry != null
                    || !"REVERSED".equals(originalEntry.getEntryStatus())
                    || originalEntry.getReversedEntryId() != null) {
                throw incomplete("归档前付款冲销链状态不一致");
            }
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
        if (trace.getCashJournals().stream()
                .noneMatch(item -> CashbookConstants.Status.PENDING_ARCHIVE.equals(item.getStatus()))) {
            BigDecimal netPaid = new BigDecimal(trace.getBudgetConservation().get("netPaid"));
            BigDecimal netCashOutflow = new BigDecimal(trace.getBudgetConservation().get("netCashOutflow"));
            if (netPaid.compareTo(netCashOutflow) != 0) {
                throw incomplete("归档付款净实付与现金流出不守恒");
            }
        }
    }

    private void validateBudgetAndReceiptFacts(PaymentTraceVO trace, Long tenantId,
                                               Long projectId, Long contractId) {
        PayApplication app = trace.getPaymentApplication();
        if ("DRAFT".equals(app.getApprovalStatus())) return;
        ProjectBudgetLine line = trace.getProjectBudgetLine();
        ProjectBudget budget = trace.getProjectBudget();
        CostSubject subject = trace.getCostSubject();
        ContractBudgetAllocation allocation = trace.getContractBudgetAllocation();
        if (line == null || budget == null || subject == null || allocation == null
                || !Objects.equals(line.getTenantId(), tenantId)
                || !Objects.equals(line.getProjectId(), projectId)
                || !Objects.equals(line.getId(), app.getBudgetLineId())
                || !Objects.equals(budget.getTenantId(), tenantId)
                || !Objects.equals(budget.getProjectId(), projectId)
                || !Objects.equals(budget.getId(), line.getBudgetId())
                || !Objects.equals(subject.getTenantId(), tenantId)
                || !Objects.equals(subject.getId(), app.getCostSubjectId())
                || !Objects.equals(subject.getId(), line.getCostSubjectId())
                || !Objects.equals(allocation.getTenantId(), tenantId)
                || !Objects.equals(allocation.getProjectId(), projectId)
                || !Objects.equals(allocation.getContractId(), contractId)
                || !Objects.equals(allocation.getBudgetLineId(), line.getId())
                || !Objects.equals(allocation.getId(), app.getContractBudgetAllocationId())) {
            throw incomplete("付款申请的合同预算、项目预算或成本科目关系不一致");
        }
        BigDecimal lineUsed = money(line.getReservedAmount()).add(money(line.getConsumedAmount()));
        BigDecimal allocationUsed = money(allocation.getReservedAmount()).add(money(allocation.getConsumedAmount()));
        if (lineUsed.compareTo(money(line.getBudgetAmount())) > 0
                || allocationUsed.compareTo(money(allocation.getAllocatedAmount())) > 0
                || allocationUsed.compareTo(money(app.getApplyAmount())) < 0) {
            throw incomplete("预算净占用与消费不守恒");
        }
        List<PaymentApplicationSource> receiptSources = trace.getApplicationSources().stream()
                .filter(source -> "MAT_RECEIPT".equals(source.getSourceType())).toList();
        if (receiptSources.size() != trace.getMaterialReceiptItems().size()) {
            throw incomplete("材料验收付款来源缺少验收明细");
        }
        Map<Long, MatReceiptItem> items = trace.getMaterialReceiptItems().stream()
                .collect(java.util.stream.Collectors.toMap(MatReceiptItem::getId, item -> item));
        Map<Long, MatReceipt> receipts = trace.getMaterialReceipts().stream()
                .collect(java.util.stream.Collectors.toMap(MatReceipt::getId, item -> item));
        if (receiptSources.stream().anyMatch(source -> {
            MatReceiptItem item = items.get(source.getReceiptItemId());
            MatReceipt receipt = item == null ? null : receipts.get(item.getReceiptId());
            return item == null || receipt == null
                    || !Objects.equals(item.getBudgetLineId(), line.getId())
                    || !Objects.equals(receipt.getTenantId(), tenantId)
                    || !Objects.equals(receipt.getProjectId(), projectId)
                    || !Objects.equals(receipt.getContractId(), contractId);
        })) {
            throw incomplete("材料验收付款来源跨预算、项目或合同");
        }
        BigDecimal netReserved = new BigDecimal(trace.getBudgetConservation().get("netReserved"));
        BigDecimal netConsumed = new BigDecimal(trace.getBudgetConservation().get("netConsumed"));
        BigDecimal sourceTotal = trace.getApplicationSources().stream()
                .map(PaymentApplicationSource::getSourceAmount).map(this::money)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (netReserved.signum() < 0 || netConsumed.signum() < 0
                || netReserved.add(netConsumed).compareTo(sourceTotal) < 0) {
            throw incomplete("预算台账净占用与消费不足以覆盖付款来源");
        }
    }

    private void validateSourceContexts(PaymentTraceVO trace, Long tenantId,
                                        Long projectId, Long contractId) {
        PayApplication app = trace.getPaymentApplication();
        Map<Long, ExpenseApplication> expenses = trace.getExpenses().stream()
                .collect(java.util.stream.Collectors.toMap(ExpenseApplication::getId, item -> item));
        Map<Long, StlSettlement> settlements = trace.getSettlements().stream()
                .collect(java.util.stream.Collectors.toMap(StlSettlement::getId, item -> item));
        Map<Long, SubMeasure> measures = trace.getSubMeasures().stream()
                .collect(java.util.stream.Collectors.toMap(SubMeasure::getId, item -> item));
        Map<Long, SubTask> tasks = trace.getSubTasks().stream()
                .collect(java.util.stream.Collectors.toMap(SubTask::getId, item -> item));
        Map<Long, MatReceiptItem> receiptItems = trace.getMaterialReceiptItems().stream()
                .collect(java.util.stream.Collectors.toMap(MatReceiptItem::getId, item -> item));
        Map<Long, MatReceipt> receipts = trace.getMaterialReceipts().stream()
                .collect(java.util.stream.Collectors.toMap(MatReceipt::getId, item -> item));

        for (PaymentApplicationSource source : trace.getApplicationSources()) {
            boolean valid = switch (source.getSourceType()) {
                case "DIRECT" -> Objects.equals(source.getSourceRefId(), app.getId())
                        && source.getExpenseId() == null && source.getSettlementId() == null
                        && source.getSubMeasureId() == null && source.getReceiptItemId() == null;
                case "EXPENSE" -> {
                    ExpenseApplication expense = expenses.get(source.getExpenseId());
                    yield Objects.equals(source.getSourceRefId(), source.getExpenseId())
                            && source.getSettlementId() == null && source.getSubMeasureId() == null
                            && source.getReceiptItemId() == null && expense != null
                            && Objects.equals(expense.getTenantId(), tenantId)
                            && Objects.equals(expense.getProjectId(), projectId)
                            && Objects.equals(expense.getContractId(), contractId)
                            && Objects.equals(expense.getPayeePartnerId(), app.getPartnerId())
                            && Objects.equals(expense.getCostSubjectId(), app.getCostSubjectId())
                            && Objects.equals(expense.getBudgetLineId(), app.getBudgetLineId());
                }
                case "SETTLEMENT" -> {
                    StlSettlement settlement = settlements.get(source.getSettlementId());
                    yield Objects.equals(source.getSourceRefId(), source.getSettlementId())
                            && source.getExpenseId() == null && source.getSubMeasureId() == null
                            && source.getReceiptItemId() == null && settlement != null
                            && Objects.equals(settlement.getTenantId(), tenantId)
                            && Objects.equals(settlement.getProjectId(), projectId)
                            && Objects.equals(settlement.getContractId(), contractId)
                            && Objects.equals(settlement.getPartnerId(), app.getPartnerId());
                }
                case "SUB_MEASURE" -> {
                    SubMeasure measure = measures.get(source.getSubMeasureId());
                    SubTask task = measure == null || measure.getSubTaskId() == null
                            ? null : tasks.get(measure.getSubTaskId());
                    yield Objects.equals(source.getSourceRefId(), source.getSubMeasureId())
                            && source.getExpenseId() == null && source.getSettlementId() == null
                            && source.getReceiptItemId() == null && measure != null
                            && Objects.equals(measure.getTenantId(), tenantId)
                            && Objects.equals(measure.getProjectId(), projectId)
                            && Objects.equals(measure.getContractId(), contractId)
                            && Objects.equals(measure.getPartnerId(), app.getPartnerId())
                            && (measure.getSubTaskId() == null || task != null
                            && Objects.equals(task.getTenantId(), tenantId)
                            && Objects.equals(task.getProjectId(), projectId)
                            && Objects.equals(task.getContractId(), contractId)
                            && Objects.equals(task.getPartnerId(), app.getPartnerId()));
                }
                case "MAT_RECEIPT" -> {
                    MatReceiptItem item = receiptItems.get(source.getReceiptItemId());
                    MatReceipt receipt = item == null ? null : receipts.get(item.getReceiptId());
                    yield Objects.equals(source.getSourceRefId(), source.getReceiptItemId())
                            && source.getExpenseId() == null && source.getSettlementId() == null
                            && source.getSubMeasureId() == null && item != null && receipt != null
                            && Objects.equals(item.getTenantId(), tenantId)
                            && Objects.equals(item.getBudgetLineId(), app.getBudgetLineId())
                            && Objects.equals(receipt.getTenantId(), tenantId)
                            && Objects.equals(receipt.getProjectId(), projectId)
                            && Objects.equals(receipt.getContractId(), contractId)
                            && Objects.equals(receipt.getPartnerId(), app.getPartnerId());
                }
                default -> false;
            };
            if (!valid) throw incomplete("付款来源与申请业务关系不一致");
        }
    }

    private BigDecimal ledgerNet(List<BudgetLedger> ledgers, String... entryTypes) {
        Set<String> types = Set.of(entryTypes);
        return ledgers.stream().filter(item -> types.contains(item.getEntryType()))
                .map(BudgetLedger::getAmount).map(this::money)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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
