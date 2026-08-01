import {
  FINANCE_API,
  type AccountingEntryPage,
  type AccountingEntryQuery,
  type CashJournalPage,
  type CashJournalQuery,
  type ExpenseApplicationPage,
  type ExpenseApplicationQuery,
  type InvoicePage,
  type InvoiceQuery,
  type PaymentApplicationPage,
  type PaymentApplicationQuery,
  type PaymentApplicationBasisRecord,
  type PaymentApplicationSourceRecord,
  type PayRecordOptionPage,
  type RevenueQuery,
  type RevenueRecord,
  type OwnerSettlementRecord,
  type ReceivableRecord,
  type SalesInvoiceRecord,
  type CollectionRecord,
  type OwnerSettlementCommand,
  type SalesInvoiceCommand,
  type CollectionCommand,
  type PaymentApplicationCommand,
  type PayRecordWritebackCommand,
  type ExpenseApplicationCommand,
  type InvoiceCommand,
  type FinanceOperationsWorkspace,
  type FundAccountRecord,
  type FundAccountCommand,
  type CashForecastCycleRecord,
  type CashForecastTrace,
  type AccountingEntryDetail,
  type FinancePeriodRecord,
  type FinancialCloseTrace,
  type FinancialStatement,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

export const loadPaymentApplications = (
  query: PaymentApplicationQuery = {},
  signal?: AbortSignal,
) => apiRequest<PaymentApplicationPage>(withQuery(FINANCE_API.payments, query), { signal })
export const loadPaymentApplication = (id: string, signal?: AbortSignal) =>
  apiRequest<PaymentApplicationPage['records'][number]>(`/pay-applications/${requiredId(id)}`, {
    signal,
  })

export const loadExpenseApplications = (
  query: ExpenseApplicationQuery = {},
  signal?: AbortSignal,
) => apiRequest<ExpenseApplicationPage>(withQuery(FINANCE_API.expenses, query), { signal })
export const loadExpenseApplication = (id: string, signal?: AbortSignal) =>
  apiRequest<ExpenseApplicationPage['records'][number]>(`/expenses/${requiredId(id)}`, { signal })

export const loadInvoices = (query: InvoiceQuery = {}, signal?: AbortSignal) =>
  apiRequest<InvoicePage>(withQuery(FINANCE_API.invoices, query), { signal })
export const loadInvoice = (id: string, signal?: AbortSignal) =>
  apiRequest<InvoicePage['records'][number]>(`/invoices/${requiredId(id)}`, { signal })
export const loadPayRecordOptions = (signal?: AbortSignal) =>
  apiRequest<PayRecordOptionPage>('/pay-records?pageNo=1&pageSize=200&payStatus=SUCCESS', {
    signal,
  })
export const writebackPayment = (body: PayRecordWritebackCommand) =>
  apiRequest<PayRecordOptionPage['records'][number]>('/pay-records/writeback', {
    method: 'POST',
    body,
  })
export const reversePaymentRecord = (
  id: string,
  body: {
    reversalType: 'REVERSAL' | 'REFUND'
    externalTxnNo: string
    reversedAt: string
    reason: string
  },
) =>
  apiRequest<PayRecordOptionPage['records'][number]>(`/pay-records/${requiredId(id)}/reverse`, {
    method: 'POST',
    body,
  })

export const loadCashJournal = (query: CashJournalQuery = {}, signal?: AbortSignal) =>
  apiRequest<CashJournalPage>(withQuery(FINANCE_API.journal, query), { signal })

export const loadAccountingEntries = (query: AccountingEntryQuery = {}, signal?: AbortSignal) =>
  apiRequest<AccountingEntryPage>(withQuery('/accounting-entry/workspace', query), { signal })
export const loadAccountingEntryDetail = (id: string, signal?: AbortSignal) =>
  apiRequest<AccountingEntryDetail>(`/accounting-entry/workspace/${requiredId(id)}`, { signal })

export const loadFinanceOperationsWorkspace = (projectId?: string, signal?: AbortSignal) =>
  apiRequest<FinanceOperationsWorkspace>(
    withQuery('/finance-operations/workspace', { projectId }),
    { signal },
  )
export const rebuildFinanceSnapshot = (projectId: string) =>
  apiRequest<void>(`/finance-operations/snapshots/${requiredId(projectId)}/rebuild`, {
    method: 'POST',
  })
export const generateFinanceAlerts = () =>
  apiRequest<void>('/finance-operations/alerts/generate', { method: 'POST' })
export const handleFinanceAlert = (id: string, status: 'RESOLVED' | 'IGNORED', note: string) =>
  apiRequest<void>(`/finance-operations/alerts/${requiredId(id)}/handle`, {
    method: 'POST',
    body: { status, note },
  })

export const loadFundAccounts = (signal?: AbortSignal) =>
  apiRequest<FundAccountRecord[]>('/fund-accounts', { signal })
export const createFundAccount = (body: FundAccountCommand) =>
  apiRequest<FundAccountRecord>('/fund-accounts', { method: 'POST', body })
export const archiveCashJournal = (id: string) =>
  apiRequest<CashJournalPage['records'][number]>(
    `/cash-journal-entries/${requiredId(id)}/archive`,
    { method: 'POST' },
  )
export const reopenCashJournal = (id: string, reason: string) =>
  apiRequest<CashJournalPage['records'][number]>(`/cash-journal-entries/${requiredId(id)}/reopen`, {
    method: 'POST',
    body: { reason },
  })
export const reverseCashJournal = (id: string, reason: string) =>
  apiRequest<CashJournalPage['records'][number]>(
    `/cash-journal-entries/${requiredId(id)}/reverse`,
    { method: 'POST', body: { reason } },
  )

export const loadCashForecastCycles = (projectId?: string, signal?: AbortSignal) =>
  apiRequest<CashForecastCycleRecord[]>(withQuery('/cash-forecasts/workspace', { projectId }), {
    signal,
  })
export const loadCashForecastTrace = (id: string, signal?: AbortSignal) =>
  apiRequest<CashForecastTrace>(`/cash-forecasts/workspace/${requiredId(id)}`, { signal })
export const regenerateCashForecast = (id: string) =>
  apiRequest<void>(`/cash-forecasts/cycles/${requiredId(id)}/regenerate`, { method: 'POST' })
export const submitCashForecast = (id: string) =>
  apiRequest<void>(`/cash-forecasts/cycles/${requiredId(id)}/submit`, { method: 'POST' })
export const approveCashForecast = (id: string, approved: boolean, comment: string) =>
  apiRequest<void>(`/cash-forecasts/cycles/${requiredId(id)}/approve`, {
    method: 'POST',
    body: { approved, comment },
  })
export const refreshCashForecastActuals = (id: string) =>
  apiRequest<void>(`/cash-forecasts/cycles/${requiredId(id)}/actuals/refresh`, { method: 'POST' })

export const reviewAccountingEntry = (id: string, approved: boolean, comment?: string) =>
  apiRequest<void>(`/accounting-entry/${requiredId(id)}/review`, {
    method: 'PUT',
    body: { approved, comment },
  })
export const postAccountingEntry = (id: string) =>
  apiRequest<void>(`/accounting-entry/${requiredId(id)}/post`, { method: 'PUT' })
export const resubmitAccountingEntry = (id: string) =>
  apiRequest<void>(`/accounting-entry/${requiredId(id)}/resubmit`, { method: 'PUT' })
export const reverseAccountingEntry = (id: string, reason: string) =>
  apiRequest<void>(`/accounting-entry/${requiredId(id)}/reverse`, {
    method: 'PUT',
    body: { reason },
  })

export const loadFinancePeriods = (year?: number, signal?: AbortSignal) =>
  apiRequest<FinancePeriodRecord[]>(withQuery('/financial-close/workspace', { year }), { signal })
export const loadFinancialCloseTrace = (id: string, signal?: AbortSignal) =>
  apiRequest<FinancialCloseTrace>(`/financial-close/workspace/${requiredId(id)}`, { signal })
export const loadFinancialStatement = (year: number, month: number, signal?: AbortSignal) =>
  apiRequest<FinancialStatement>(`/financial-close/workspace/${year}/${month}/statements`, {
    signal,
  })
export const runFinancialCloseChecks = (year: number, month: number) =>
  apiRequest<void>(`/financial-close/periods/${year}/${month}/checks`, { method: 'POST' })
export const closeFinancePeriod = (year: number, month: number, comment?: string) =>
  apiRequest<void>(`/financial-close/periods/${year}/${month}/close`, {
    method: 'POST',
    body: { comment },
  })
export const reopenFinancePeriod = (year: number, month: number, reason: string) =>
  apiRequest<void>(`/financial-close/periods/${year}/${month}/reopen`, {
    method: 'POST',
    body: { reason },
  })

export const loadRevenueSettlements = (query: RevenueQuery = {}, signal?: AbortSignal) =>
  apiRequest<OwnerSettlementRecord[]>(withQuery(FINANCE_API.revenueSettlements, query), { signal })
export const loadReceivables = (query: RevenueQuery = {}, signal?: AbortSignal) =>
  apiRequest<ReceivableRecord[]>(withQuery(FINANCE_API.revenueReceivables, query), { signal })
export const loadSalesInvoices = (query: RevenueQuery = {}, signal?: AbortSignal) =>
  apiRequest<SalesInvoiceRecord[]>(withQuery(FINANCE_API.revenueSalesInvoices, query), { signal })
export const loadCollections = (query: RevenueQuery = {}, signal?: AbortSignal) =>
  apiRequest<CollectionRecord[]>(withQuery(FINANCE_API.revenueCollections, query), { signal })
export const createOwnerSettlement = (body: OwnerSettlementCommand) =>
  apiRequest<OwnerSettlementRecord>(FINANCE_API.revenueSettlements, { method: 'POST', body })
export const submitOwnerSettlement = (id: string) =>
  apiRequest<OwnerSettlementRecord>(`${FINANCE_API.revenueSettlements}/${requiredId(id)}/submit`, {
    method: 'POST',
  })
export const createSalesInvoice = (body: SalesInvoiceCommand) =>
  apiRequest<SalesInvoiceRecord>(FINANCE_API.revenueSalesInvoices, { method: 'POST', body })
export const createCollection = (body: CollectionCommand) =>
  apiRequest<CollectionRecord>(FINANCE_API.revenueCollections, { method: 'POST', body })
export const creditReceivable = (
  id: string,
  amount: string,
  reason: string,
  idempotencyKey: string,
) =>
  apiRequest<RevenueRecord>(`${FINANCE_API.revenueReceivables}/${requiredId(id)}/credit`, {
    method: 'POST',
    body: { amount, reason, idempotencyKey },
  })
export const reverseCollection = (id: string, reason: string, idempotencyKey: string) =>
  apiRequest<RevenueRecord>(`${FINANCE_API.revenueCollections}/${requiredId(id)}/reverse`, {
    method: 'POST',
    body: { reason, idempotencyKey },
  })
export const createPayment = (body: PaymentApplicationCommand) =>
  apiRequest<string>('/pay-applications', { method: 'POST', body })
export interface PaymentSourceOptionRecord {
  sourceType: string
  sourceRefId: string
  documentCode: string
  sourceTotalAmount: string
  committedAmount: string
  availableAmount: string
}
export const loadPaymentSourceOptions = (
  query: {
    projectId: string
    contractId: string
    partnerId: string
    payType: string
    expenseCategory?: string
  },
  signal?: AbortSignal,
) =>
  apiRequest<PaymentSourceOptionRecord[]>(withQuery('/pay-applications/source-options', query), {
    signal,
  })
export const savePaymentSources = (
  id: string,
  body: Array<{ sourceType: string; sourceRefId: string; sourceAmount: string }>,
) =>
  apiRequest<void>(`/pay-applications/${requiredId(id)}/sources/batch`, {
    method: 'POST',
    body,
  })
export const loadPaymentSources = (id: string, signal?: AbortSignal) =>
  apiRequest<PaymentApplicationSourceRecord[]>(`/pay-applications/${requiredId(id)}/sources`, {
    signal,
  })
export const loadPaymentBasis = (id: string, signal?: AbortSignal) =>
  apiRequest<PaymentApplicationBasisRecord[]>(`/pay-applications/${requiredId(id)}/basis`, {
    signal,
  })
export const savePaymentBasis = (
  id: string,
  body: Array<{ basisType: string; basisId: string; basisAmount: string }>,
) =>
  apiRequest<void>(`/pay-applications/${requiredId(id)}/basis/batch`, {
    method: 'POST',
    body,
  })
export const updatePayment = (id: string, body: PaymentApplicationCommand) =>
  apiRequest<void>(`/pay-applications/${requiredId(id)}`, { method: 'PUT', body })
export const deletePayment = (id: string) =>
  apiRequest<void>(`/pay-applications/${requiredId(id)}`, { method: 'DELETE' })
export const submitPayment = (id: string) =>
  apiRequest<void>(`/pay-applications/${requiredId(id)}/submit`, { method: 'POST' })
export const createExpense = (body: ExpenseApplicationCommand) =>
  apiRequest<string>('/expenses', { method: 'POST', body })
export const updateExpense = (id: string, body: ExpenseApplicationCommand) =>
  apiRequest<void>(`/expenses/${requiredId(id)}`, { method: 'PUT', body })
export const deleteExpense = (id: string) =>
  apiRequest<void>(`/expenses/${requiredId(id)}`, { method: 'DELETE' })
export const submitExpense = (id: string) =>
  apiRequest<void>(`/expenses/${requiredId(id)}/submit`, { method: 'POST' })
export const createInvoice = (body: InvoiceCommand) =>
  apiRequest<string>('/invoices', { method: 'POST', body })
export const updateInvoice = (id: string, body: InvoiceCommand) =>
  apiRequest<void>(`/invoices/${requiredId(id)}`, { method: 'PUT', body })
export const deleteInvoice = (id: string) =>
  apiRequest<void>(`/invoices/${requiredId(id)}`, { method: 'DELETE' })
export const verifyInvoice = (id: string, verifyStatus: string) =>
  apiRequest<void>(`/invoices/${requiredId(id)}/verify`, { method: 'PUT', body: { verifyStatus } })
export const saveInvoiceAllocations = (
  id: string,
  body: Array<{ payRecordId: string; allocatedAmount: string }>,
) => apiRequest<void>(`/invoices/${requiredId(id)}/allocations/batch`, { method: 'POST', body })

function withQuery(path: string, query: object): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'number' && Number.isInteger(value) && value > 0)
      params.set(key, String(value))
    else if (typeof value === 'boolean') params.set(key, String(value))
    else if (typeof value === 'string' && value.trim()) params.set(key, value.trim())
  }
  return params.size ? `${path}?${params}` : path
}
function requiredId(id: string): string {
  if (!id.trim()) throw new Error('ID_REQUIRED')
  return encodeURIComponent(id.trim())
}
