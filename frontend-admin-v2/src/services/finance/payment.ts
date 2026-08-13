import {
  FINANCE_API,
  type ExpenseApplicationCommand,
  type ExpenseApplicationPage,
  type ExpenseApplicationQuery,
  type InvoiceCommand,
  type InvoicePage,
  type InvoiceQuery,
  type PayRecordOptionPage,
  type PayRecordWritebackCommand,
  type PaymentApplicationBasisRecord,
  type PaymentApplicationCommand,
  type PaymentApplicationPage,
  type PaymentApplicationQuery,
  type PaymentApplicationSourceRecord,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'
import { requiredId, withQuery } from './support'
import type { PaymentSourceOptionRecord } from './types'

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

export const createPayment = (body: PaymentApplicationCommand) =>
  apiRequest<string>('/pay-applications', { method: 'POST', body })
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
