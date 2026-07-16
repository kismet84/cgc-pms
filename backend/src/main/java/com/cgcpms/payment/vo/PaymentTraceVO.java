package com.cgcpms.payment.vo;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.budget.entity.BudgetLedger;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.expense.entity.ExpenseApplication;
import com.cgcpms.invoice.entity.InvoicePaymentAllocation;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.entity.PaymentApplicationSource;
import com.cgcpms.payment.entity.PaymentRecordSourceAllocation;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.entity.SettlementSubMeasure;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubTask;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfRecord;
import lombok.Data;

import java.util.List;

@Data
public class PaymentTraceVO {
    private PmProject project;
    private CtContract contract;
    private PayApplication paymentApplication;
    private WfInstance approvalInstance;
    private List<WfRecord> approvalRecords;
    private List<PaymentApplicationSource> applicationSources;
    private List<ExpenseApplication> expenses;
    private List<StlSettlement> settlements;
    private List<SettlementSubMeasure> settlementSubMeasures;
    private List<SubMeasure> subMeasures;
    private List<SubTask> subTasks;
    private List<PayRecord> paymentRecords;
    private List<PaymentRecordSourceAllocation> paymentSourceAllocations;
    private List<CashJournalEntry> cashJournals;
    private List<PayInvoice> invoices;
    private List<InvoicePaymentAllocation> invoiceAllocations;
    private List<BudgetLedger> budgetLedgers;
    private List<AccountingEntry> accountingEntries;
    private List<AccountingEntryLine> accountingEntryLines;
}
