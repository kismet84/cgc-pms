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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentTraceService {
    private static final int TRACE_BATCH_SIZE = 100;
    private static final PaymentTraceValidator VALIDATOR = new PaymentTraceValidator();
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
        return traces(List.of(applicationId), "付款申请不存在").getFirst();
    }

    private List<PaymentTraceVO> assembleBatch(List<Long> applicationIds) {
        Long tenantId = tenant();
        List<PayApplication> applications = applicationMapper.selectList(new LambdaQueryWrapper<PayApplication>()
                .eq(PayApplication::getTenantId, tenantId)
                .in(PayApplication::getId, applicationIds)
                .orderByAsc(PayApplication::getId));
        if (!ids(applications, PayApplication::getId).equals(Set.copyOf(applicationIds))
                || applications.stream().anyMatch(item -> !Objects.equals(item.getTenantId(), tenantId))) {
            throw new BusinessException("PAY_APP_NOT_FOUND", "付款申请不存在");
        }

        Set<Long> projectIds = idSet(applications, PayApplication::getProjectId);
        List<PmProject> projects = projectMapper.selectList(new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getTenantId, tenantId)
                .in(!projectIds.isEmpty(), PmProject::getId, projectIds)
                .apply(projectIds.isEmpty(), "1 = 0")
                .orderByAsc(PmProject::getId));
        if (!ids(projects, PmProject::getId).equals(projectIds)) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        Set<Long> accessibleProjectIds = idSet(projectAccessChecker.filterAccessible(projects), PmProject::getId);
        if (!accessibleProjectIds.containsAll(projectIds)) {
            throw new BusinessException("PROJECT_ACCESS_DENIED", "无权查看付款全链路该项目");
        }

        Set<Long> contractIds = idSet(applications, PayApplication::getContractId);
        List<CtContract> contracts = contractMapper.selectList(new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getTenantId, tenantId)
                .in(!contractIds.isEmpty(), CtContract::getId, contractIds)
                .apply(contractIds.isEmpty(), "1 = 0")
                .orderByAsc(CtContract::getId));

        Set<Long> instanceIds = idSet(applications, PayApplication::getApprovalInstanceId);
        List<WfInstance> instances = instanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getTenantId, tenantId)
                .in(!instanceIds.isEmpty(), WfInstance::getId, instanceIds)
                .apply(instanceIds.isEmpty(), "1 = 0")
                .orderByAsc(WfInstance::getId));
        List<WfRecord> workflowRecords = workflowRecordMapper.selectList(new LambdaQueryWrapper<WfRecord>()
                .eq(WfRecord::getTenantId, tenantId)
                .in(!instanceIds.isEmpty(), WfRecord::getInstanceId, instanceIds)
                .apply(instanceIds.isEmpty(), "1 = 0")
                .orderByAsc(WfRecord::getCreatedAt, WfRecord::getId));

        List<PaymentApplicationSource> sources = sourceMapper.selectList(
                new LambdaQueryWrapper<PaymentApplicationSource>()
                        .eq(PaymentApplicationSource::getTenantId, tenantId)
                        .in(PaymentApplicationSource::getPayApplicationId, applicationIds)
                        .orderByAsc(PaymentApplicationSource::getId));
        Set<Long> expenseIds = idSet(sources, PaymentApplicationSource::getExpenseId);
        Set<Long> settlementIds = idSet(sources, PaymentApplicationSource::getSettlementId);
        Set<Long> directMeasureIds = idSet(sources, PaymentApplicationSource::getSubMeasureId);
        List<ExpenseApplication> expenses = expenseMapper.selectList(new LambdaQueryWrapper<ExpenseApplication>()
                .eq(ExpenseApplication::getTenantId, tenantId)
                .in(!expenseIds.isEmpty(), ExpenseApplication::getId, expenseIds)
                .apply(expenseIds.isEmpty(), "1 = 0")
                .orderByAsc(ExpenseApplication::getId));
        List<StlSettlement> settlements = settlementMapper.selectList(new LambdaQueryWrapper<StlSettlement>()
                .eq(StlSettlement::getTenantId, tenantId)
                .in(!settlementIds.isEmpty(), StlSettlement::getId, settlementIds)
                .apply(settlementIds.isEmpty(), "1 = 0")
                .orderByAsc(StlSettlement::getId));
        List<SettlementSubMeasure> settlementMeasureLinks = settlementSubMeasureMapper.selectList(
                new LambdaQueryWrapper<SettlementSubMeasure>()
                        .eq(SettlementSubMeasure::getTenantId, tenantId)
                        .in(!settlementIds.isEmpty(), SettlementSubMeasure::getSettlementId, settlementIds)
                        .apply(settlementIds.isEmpty(), "1 = 0")
                        .orderByAsc(SettlementSubMeasure::getSettlementId, SettlementSubMeasure::getId));
        Set<Long> measureIds = new HashSet<>(directMeasureIds);
        measureIds.addAll(idSet(settlementMeasureLinks, SettlementSubMeasure::getSubMeasureId));
        List<SubMeasure> measures = subMeasureMapper.selectList(new LambdaQueryWrapper<SubMeasure>()
                .eq(SubMeasure::getTenantId, tenantId)
                .in(!measureIds.isEmpty(), SubMeasure::getId, measureIds)
                .apply(measureIds.isEmpty(), "1 = 0")
                .orderByAsc(SubMeasure::getId));
        Set<Long> taskIds = idSet(measures, SubMeasure::getSubTaskId);
        List<SubTask> tasks = subTaskMapper.selectList(new LambdaQueryWrapper<SubTask>()
                .eq(SubTask::getTenantId, tenantId)
                .in(!taskIds.isEmpty(), SubTask::getId, taskIds)
                .apply(taskIds.isEmpty(), "1 = 0")
                .orderByAsc(SubTask::getId));

        List<PayRecord> paymentRecords = recordMapper.selectList(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, tenantId)
                .in(PayRecord::getPayApplicationId, applicationIds)
                .orderByAsc(PayRecord::getPaidAt, PayRecord::getId));
        Set<Long> recordIds = idSet(paymentRecords, PayRecord::getId);
        List<PaymentRecordSourceAllocation> sourceAllocations = sourceAllocationMapper.selectList(
                new LambdaQueryWrapper<PaymentRecordSourceAllocation>()
                        .eq(PaymentRecordSourceAllocation::getTenantId, tenantId)
                        .in(!recordIds.isEmpty(), PaymentRecordSourceAllocation::getPayRecordId, recordIds)
                        .apply(recordIds.isEmpty(), "1 = 0")
                        .orderByAsc(PaymentRecordSourceAllocation::getPayRecordId,
                                PaymentRecordSourceAllocation::getId));
        List<CashJournalEntry> cashJournals = cashJournalMapper.selectList(
                new LambdaQueryWrapper<CashJournalEntry>()
                        .eq(CashJournalEntry::getTenantId, tenantId)
                        .in(!recordIds.isEmpty(), CashJournalEntry::getPayRecordId, recordIds)
                        .apply(recordIds.isEmpty(), "1 = 0")
                        .orderByAsc(CashJournalEntry::getPayRecordId, CashJournalEntry::getId));
        Set<Long> journalIds = idSet(cashJournals, CashJournalEntry::getId);
        List<Map<String, Object>> paymentDocuments = paymentDocuments(tenantId, journalIds);

        List<InvoicePaymentAllocation> invoiceAllocations = invoiceAllocationMapper.selectList(
                new LambdaQueryWrapper<InvoicePaymentAllocation>()
                        .eq(InvoicePaymentAllocation::getTenantId, tenantId)
                        .in(!recordIds.isEmpty(), InvoicePaymentAllocation::getPayRecordId, recordIds)
                        .apply(recordIds.isEmpty(), "1 = 0")
                        .orderByAsc(InvoicePaymentAllocation::getPayRecordId,
                                InvoicePaymentAllocation::getId));
        Set<Long> invoiceIds = idSet(invoiceAllocations, InvoicePaymentAllocation::getInvoiceId);
        List<PayInvoice> invoices = invoiceMapper.selectList(new LambdaQueryWrapper<PayInvoice>()
                .eq(PayInvoice::getTenantId, tenantId)
                .in(!invoiceIds.isEmpty(), PayInvoice::getId, invoiceIds)
                .apply(invoiceIds.isEmpty(), "1 = 0")
                .orderByAsc(PayInvoice::getId));

        List<BudgetLedger> budgetLedgers = budgetLedgerMapper.selectList(new LambdaQueryWrapper<BudgetLedger>()
                .eq(BudgetLedger::getTenantId, tenantId)
                .in(!projectIds.isEmpty(), BudgetLedger::getProjectId, projectIds)
                .apply(projectIds.isEmpty(), "1 = 0")
                .and(keys -> {
                    keys.eq(BudgetLedger::getBusinessType, "PAY_REQUEST")
                            .in(BudgetLedger::getBusinessId, applicationIds);
                    if (!expenseIds.isEmpty()) {
                        keys.or().eq(BudgetLedger::getBusinessType, "EXPENSE")
                                .in(BudgetLedger::getBusinessId, expenseIds);
                    }
                })
                .orderByAsc(BudgetLedger::getCreatedAt, BudgetLedger::getId));

        Set<Long> budgetLineIds = idSet(applications, PayApplication::getBudgetLineId);
        List<ProjectBudgetLine> budgetLines = projectBudgetLineMapper.selectList(
                new LambdaQueryWrapper<ProjectBudgetLine>()
                        .eq(ProjectBudgetLine::getTenantId, tenantId)
                        .in(!budgetLineIds.isEmpty(), ProjectBudgetLine::getId, budgetLineIds)
                        .apply(budgetLineIds.isEmpty(), "1 = 0")
                        .orderByAsc(ProjectBudgetLine::getId));
        Set<Long> budgetIds = idSet(budgetLines, ProjectBudgetLine::getBudgetId);
        List<ProjectBudget> budgets = projectBudgetMapper.selectList(new LambdaQueryWrapper<ProjectBudget>()
                .eq(ProjectBudget::getTenantId, tenantId)
                .in(!budgetIds.isEmpty(), ProjectBudget::getId, budgetIds)
                .apply(budgetIds.isEmpty(), "1 = 0")
                .orderByAsc(ProjectBudget::getId));
        Set<Long> costSubjectIds = idSet(applications, PayApplication::getCostSubjectId);
        List<CostSubject> costSubjects = costSubjectMapper.selectList(new LambdaQueryWrapper<CostSubject>()
                .eq(CostSubject::getTenantId, tenantId)
                .in(!costSubjectIds.isEmpty(), CostSubject::getId, costSubjectIds)
                .apply(costSubjectIds.isEmpty(), "1 = 0")
                .orderByAsc(CostSubject::getId));
        Set<Long> contractAllocationIds = idSet(applications, PayApplication::getContractBudgetAllocationId);
        List<ContractBudgetAllocation> contractAllocations = contractBudgetAllocationMapper.selectList(
                new LambdaQueryWrapper<ContractBudgetAllocation>()
                        .eq(ContractBudgetAllocation::getTenantId, tenantId)
                        .in(!contractAllocationIds.isEmpty(), ContractBudgetAllocation::getId,
                                contractAllocationIds)
                        .apply(contractAllocationIds.isEmpty(), "1 = 0")
                        .orderByAsc(ContractBudgetAllocation::getId));

        Set<Long> receiptItemIds = idSet(sources, PaymentApplicationSource::getReceiptItemId);
        List<MatReceiptItem> receiptItems = receiptItemMapper.selectList(new LambdaQueryWrapper<MatReceiptItem>()
                .eq(MatReceiptItem::getTenantId, tenantId)
                .in(!receiptItemIds.isEmpty(), MatReceiptItem::getId, receiptItemIds)
                .apply(receiptItemIds.isEmpty(), "1 = 0")
                .orderByAsc(MatReceiptItem::getId));
        Set<Long> receiptIds = idSet(receiptItems, MatReceiptItem::getReceiptId);
        List<MatReceipt> receipts = receiptMapper.selectList(new LambdaQueryWrapper<MatReceipt>()
                .eq(MatReceipt::getTenantId, tenantId)
                .in(!receiptIds.isEmpty(), MatReceipt::getId, receiptIds)
                .apply(receiptIds.isEmpty(), "1 = 0")
                .orderByAsc(MatReceipt::getId));

        List<AccountingEntry> accountingEntries = accountingEntryMapper.selectList(
                new LambdaQueryWrapper<AccountingEntry>()
                        .eq(AccountingEntry::getTenantId, tenantId)
                        .in(!recordIds.isEmpty(), AccountingEntry::getPayRecordId, recordIds)
                        .apply(recordIds.isEmpty(), "1 = 0")
                        .orderByAsc(AccountingEntry::getId));
        Set<Long> entryIds = idSet(accountingEntries, AccountingEntry::getId);
        List<AccountingEntryLine> accountingLines = accountingLineMapper.selectList(
                new LambdaQueryWrapper<AccountingEntryLine>()
                        .eq(AccountingEntryLine::getTenantId, tenantId)
                        .in(!entryIds.isEmpty(), AccountingEntryLine::getEntryId, entryIds)
                        .apply(entryIds.isEmpty(), "1 = 0")
                        .orderByAsc(AccountingEntryLine::getEntryId, AccountingEntryLine::getId));

        Map<Long, PmProject> projectById = byId(projects, PmProject::getId);
        Map<Long, CtContract> contractById = byId(contracts, CtContract::getId);
        Map<Long, WfInstance> instanceById = byId(instances, WfInstance::getId);
        Map<Long, ProjectBudgetLine> budgetLineById = byId(budgetLines, ProjectBudgetLine::getId);
        Map<Long, ProjectBudget> budgetById = byId(budgets, ProjectBudget::getId);
        Map<Long, CostSubject> subjectById = byId(costSubjects, CostSubject::getId);
        Map<Long, ContractBudgetAllocation> contractAllocationById = byId(contractAllocations,
                ContractBudgetAllocation::getId);

        List<PaymentTraceVO> result = new ArrayList<>(applications.size());
        for (PayApplication application : applications) {
            PaymentTraceVO trace = new PaymentTraceVO();
            trace.setPaymentApplication(application);
            trace.setProject(projectById.get(application.getProjectId()));
            trace.setContract(contractById.get(application.getContractId()));
            if (trace.getProject() == null || trace.getContract() == null
                    || !Objects.equals(trace.getProject().getTenantId(), tenantId)
                    || !Objects.equals(trace.getContract().getTenantId(), tenantId)
                    || !Objects.equals(trace.getContract().getProjectId(), application.getProjectId())) {
                throw incomplete("付款申请的项目或合同关系不一致");
            }

            WfInstance instance = instanceById.get(application.getApprovalInstanceId());
            trace.setApprovalInstance(instance);
            trace.setApprovalRecords(instance == null ? List.of() : workflowRecords.stream()
                    .filter(item -> Objects.equals(item.getInstanceId(), instance.getId())).toList());

            List<PaymentApplicationSource> applicationSources = sources.stream()
                    .filter(item -> Objects.equals(item.getPayApplicationId(), application.getId())).toList();
            trace.setApplicationSources(applicationSources);
            Set<Long> applicationExpenseIds = idSet(applicationSources, PaymentApplicationSource::getExpenseId);
            Set<Long> applicationSettlementIds = idSet(applicationSources,
                    PaymentApplicationSource::getSettlementId);
            trace.setExpenses(filterByIds(expenses, ExpenseApplication::getId, applicationExpenseIds));
            trace.setSettlements(filterByIds(settlements, StlSettlement::getId, applicationSettlementIds));
            List<SettlementSubMeasure> applicationSettlementLinks = settlementMeasureLinks.stream()
                    .filter(item -> applicationSettlementIds.contains(item.getSettlementId())).toList();
            trace.setSettlementSubMeasures(applicationSettlementLinks);
            Set<Long> applicationMeasureIds = idSet(applicationSources,
                    PaymentApplicationSource::getSubMeasureId);
            applicationMeasureIds.addAll(idSet(applicationSettlementLinks, SettlementSubMeasure::getSubMeasureId));
            List<SubMeasure> applicationMeasures = filterByIds(measures, SubMeasure::getId, applicationMeasureIds);
            trace.setSubMeasures(applicationMeasures);
            trace.setSubTasks(filterByIds(tasks, SubTask::getId, idSet(applicationMeasures, SubMeasure::getSubTaskId)));

            List<PayRecord> applicationRecords = paymentRecords.stream()
                    .filter(item -> Objects.equals(item.getPayApplicationId(), application.getId())).toList();
            Set<Long> applicationRecordIds = idSet(applicationRecords, PayRecord::getId);
            trace.setPaymentRecords(applicationRecords);
            trace.setPaymentSourceAllocations(sourceAllocations.stream()
                    .filter(item -> applicationRecordIds.contains(item.getPayRecordId())).toList());
            List<CashJournalEntry> applicationJournals = cashJournals.stream()
                    .filter(item -> applicationRecordIds.contains(item.getPayRecordId())).toList();
            trace.setCashJournals(applicationJournals);
            Set<Long> applicationJournalIds = idSet(applicationJournals, CashJournalEntry::getId);
            trace.setPaymentDocuments(paymentDocuments.stream()
                    .filter(item -> applicationJournalIds.contains(cashJournalId(item))).toList());
            List<InvoicePaymentAllocation> applicationInvoiceAllocations = invoiceAllocations.stream()
                    .filter(item -> applicationRecordIds.contains(item.getPayRecordId())).toList();
            trace.setInvoiceAllocations(applicationInvoiceAllocations);
            trace.setInvoices(filterByIds(invoices, PayInvoice::getId,
                    idSet(applicationInvoiceAllocations, InvoicePaymentAllocation::getInvoiceId)));

            Set<String> businessKeys = new HashSet<>();
            businessKeys.add("PAY_REQUEST:" + application.getId());
            applicationExpenseIds.forEach(id -> businessKeys.add("EXPENSE:" + id));
            List<BudgetLedger> applicationLedgers = budgetLedgers.stream()
                    .filter(item -> Objects.equals(item.getProjectId(), application.getProjectId()))
                    .filter(item -> businessKeys.contains(item.getBusinessType() + ":" + item.getBusinessId()))
                    .toList();
            trace.setBudgetLedgers(applicationLedgers);
            ProjectBudgetLine budgetLine = budgetLineById.get(application.getBudgetLineId());
            trace.setProjectBudgetLine(budgetLine);
            trace.setProjectBudget(budgetLine == null ? null : budgetById.get(budgetLine.getBudgetId()));
            trace.setCostSubject(subjectById.get(application.getCostSubjectId()));
            trace.setContractBudgetAllocation(contractAllocationById.get(
                    application.getContractBudgetAllocationId()));

            Set<Long> applicationReceiptItemIds = idSet(applicationSources,
                    PaymentApplicationSource::getReceiptItemId);
            List<MatReceiptItem> applicationReceiptItems = filterByIds(receiptItems, MatReceiptItem::getId,
                    applicationReceiptItemIds);
            trace.setMaterialReceiptItems(applicationReceiptItems);
            trace.setMaterialReceipts(filterByIds(receipts, MatReceipt::getId,
                    idSet(applicationReceiptItems, MatReceiptItem::getReceiptId)));

            BigDecimal netReserved = ledgerNet(applicationLedgers, "RESERVE", "RESTORE_RESERVATION")
                    .subtract(ledgerNet(applicationLedgers, "RELEASE", "CONSUME"));
            BigDecimal netConsumed = ledgerNet(applicationLedgers, "CONSUME")
                    .subtract(ledgerNet(applicationLedgers, "RESTORE_RESERVATION", "REVERSE"));
            BigDecimal netPaid = applicationSources.stream().map(PaymentApplicationSource::getPaidAmount)
                    .map(this::money).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal netCashOutflow = applicationJournals.stream()
                    .filter(item -> item.getArchivedAt() != null || "ARCHIVED".equals(item.getStatus()))
                    .map(item -> "OUT".equals(item.getDirection()) ? money(item.getAmount())
                            : money(item.getAmount()).negate())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            trace.setBudgetConservation(Map.of(
                    "netReserved", money(netReserved).toPlainString(),
                    "netConsumed", money(netConsumed).toPlainString(),
                    "netPaid", money(netPaid).toPlainString(),
                    "netCashOutflow", money(netCashOutflow).toPlainString()));

            List<AccountingEntry> applicationEntries = accountingEntries.stream()
                    .filter(item -> applicationRecordIds.contains(item.getPayRecordId())).toList();
            trace.setAccountingEntries(applicationEntries);
            Set<Long> applicationEntryIds = idSet(applicationEntries, AccountingEntry::getId);
            trace.setAccountingEntryLines(accountingLines.stream()
                    .filter(item -> applicationEntryIds.contains(item.getEntryId()))
                    .toList());
            VALIDATOR.validate(trace, tenantId);
            result.add(trace);
        }
        return result;
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

    private List<PaymentTraceVO> traces(Collection<Long> applicationIds, String emptyMessage) {
        List<Long> ids = applicationIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) throw incomplete(emptyMessage);
        List<PaymentTraceVO> result = new ArrayList<>(ids.size());
        for (int start = 0; start < ids.size(); start += TRACE_BATCH_SIZE) {
            result.addAll(assembleBatch(ids.subList(start, Math.min(start + TRACE_BATCH_SIZE, ids.size()))));
        }
        return List.copyOf(result);
    }

    private List<Map<String, Object>> paymentDocuments(Long tenantId, Set<Long> journalIds) {
        if (journalIds.isEmpty()) {
            return jdbc.queryForList("""
                    SELECT link.id,target.id AS cash_journal_id,
                           link.cash_journal_id AS evidence_cash_journal_id,
                           link.file_id,link.document_type,link.created_at
                      FROM cash_journal_entry target
                      JOIN payment_document_link link
                        ON link.tenant_id=target.tenant_id
                       AND link.cash_journal_id=COALESCE(target.reverse_of_entry_id,target.id)
                     WHERE target.tenant_id=? AND 1=0
                     ORDER BY target.id,link.created_at,link.id
                    """, args(tenantId, List.of()));
        }
        List<Long> sortedIds = journalIds.stream().sorted().toList();
        return jdbc.queryForList("""
                SELECT link.id,target.id AS cash_journal_id,
                       link.cash_journal_id AS evidence_cash_journal_id,
                       link.file_id,link.document_type,link.created_at
                  FROM cash_journal_entry target
                  JOIN payment_document_link link
                    ON link.tenant_id=target.tenant_id
                   AND link.cash_journal_id=COALESCE(target.reverse_of_entry_id,target.id)
                 WHERE target.tenant_id=? AND target.id IN (%s)
                 ORDER BY target.id,link.created_at,link.id
                """.formatted("?,".repeat(sortedIds.size()).replaceFirst(",$", "")),
                args(tenantId, sortedIds));
    }

    private <T> Set<Long> ids(List<T> values, Function<T, Long> idExtractor) {
        return values.stream().map(idExtractor).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private <T> Set<Long> idSet(List<T> values, Function<T, Long> idExtractor) {
        return new HashSet<>(ids(values, idExtractor));
    }

    private <T> Map<Long, T> byId(List<T> values, Function<T, Long> idExtractor) {
        return values.stream().filter(item -> idExtractor.apply(item) != null)
                .collect(Collectors.toMap(idExtractor, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private <T> List<T> filterByIds(List<T> values, Function<T, Long> idExtractor, Set<Long> selectedIds) {
        if (selectedIds.isEmpty()) return List.of();
        return values.stream().filter(item -> selectedIds.contains(idExtractor.apply(item))).toList();
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
