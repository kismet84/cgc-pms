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
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfRecord;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
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
        return byApplication(applicationId);
    }

    public PaymentTraceVO byPayRecord(Long payRecordId) {
        PayRecord record = recordMapper.selectById(payRecordId);
        if (record == null || !Objects.equals(record.getTenantId(), UserContext.getCurrentTenantId())) {
            throw new BusinessException("PAY_RECORD_NOT_FOUND", "付款记录不存在");
        }
        return byApplication(record.getPayApplicationId());
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
        trace.setExpenses(expenseIds.isEmpty() ? List.of() : expenseMapper.selectByIds(expenseIds));
        trace.setSettlements(settlementIds.isEmpty() ? List.of() : settlementMapper.selectByIds(settlementIds));

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
        return trace;
    }
}
