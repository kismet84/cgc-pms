package com.cgcpms.payment.service;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.budget.entity.BudgetLedger;
import com.cgcpms.budget.entity.ContractBudgetAllocation;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.expense.entity.ExpenseApplication;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.entity.PaymentApplicationSource;
import com.cgcpms.payment.entity.PaymentRecordSourceAllocation;
import com.cgcpms.payment.vo.PaymentTraceVO;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubTask;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Validates one fully loaded payment trace without performing additional reads. */
final class PaymentTraceValidator {

    void validate(PaymentTraceVO trace, Long tenantId) {
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

    private BusinessException incomplete(String message) {
        return new BusinessException("PAYMENT_TRACE_INCOMPLETE", message);
    }
}
