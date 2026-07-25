import {
  FINANCE_API,
  type AccountingEntryPage,
  type AccountingEntryQuery,
  type CashJournalPage,
  type CashJournalQuery,
  type ExpenseApplicationPage,
  type ExpenseApplicationQuery,
  type InvoicePage,
  type PaymentApplicationPage,
  type PaymentApplicationQuery,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

export const loadPaymentApplications = (
  query: PaymentApplicationQuery = {},
  signal?: AbortSignal,
) => apiRequest<PaymentApplicationPage>(withQuery(FINANCE_API.payments, query), { signal })

export const loadExpenseApplications = (
  query: ExpenseApplicationQuery = {},
  signal?: AbortSignal,
) => apiRequest<ExpenseApplicationPage>(withQuery(FINANCE_API.expenses, query), { signal })

export const loadInvoices = (
  query: { pageNo?: number; pageSize?: number; invoiceNo?: string; verifyStatus?: string } = {},
  signal?: AbortSignal,
) => apiRequest<InvoicePage>(withQuery(FINANCE_API.invoices, query), { signal })

export const loadCashJournal = (query: CashJournalQuery = {}, signal?: AbortSignal) =>
  apiRequest<CashJournalPage>(withQuery(FINANCE_API.journal, query), { signal })

export const loadAccountingEntries = (query: AccountingEntryQuery = {}, signal?: AbortSignal) =>
  apiRequest<AccountingEntryPage>(withQuery(FINANCE_API.accountingEntries, query), { signal })

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
