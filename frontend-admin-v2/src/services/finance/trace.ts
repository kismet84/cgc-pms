import type { PaymentTraceRecord } from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'
import { requiredId } from './support'

export const loadPaymentTraceByApplication = (id: string, signal?: AbortSignal) =>
  apiRequest<PaymentTraceRecord>(`/payment-traces/applications/${requiredId(id)}`, { signal })
export const loadPaymentTraceByPayRecord = (id: string, signal?: AbortSignal) =>
  apiRequest<PaymentTraceRecord>(`/payment-traces/pay-records/${requiredId(id)}`, { signal })
export const loadPaymentTraceByCashJournal = (id: string, signal?: AbortSignal) =>
  apiRequest<PaymentTraceRecord>(`/payment-traces/cash-journals/${requiredId(id)}`, { signal })
export const loadPaymentTraceByInvoice = (id: string, signal?: AbortSignal) =>
  apiRequest<PaymentTraceRecord[]>(`/payment-traces/invoices/${requiredId(id)}`, { signal })
export const loadPaymentTraceByVoucher = (id: string, signal?: AbortSignal) =>
  apiRequest<PaymentTraceRecord[]>(`/payment-traces/vouchers/${requiredId(id)}`, { signal })
export const loadPaymentTraceByExpense = (id: string, signal?: AbortSignal) =>
  apiRequest<PaymentTraceRecord[]>(`/payment-traces/expenses/${requiredId(id)}`, { signal })
