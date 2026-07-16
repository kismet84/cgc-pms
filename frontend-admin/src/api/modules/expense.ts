import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { ExpenseApplicationVO } from '@/types/expense'

export function getExpenseList(params: Record<string, unknown>) {
  return request<PageResult<ExpenseApplicationVO>>({ url: '/expenses', method: 'get', params })
}

export function getExpenseDetail(id: string) {
  return request<ExpenseApplicationVO>({ url: `/expenses/${id}`, method: 'get' })
}

export function createExpense(data: Partial<ExpenseApplicationVO>) {
  return request<string>({ url: '/expenses', method: 'post', data })
}

export function updateExpense(id: string, data: Partial<ExpenseApplicationVO>) {
  return request<void>({ url: `/expenses/${id}`, method: 'put', data })
}

export function deleteExpense(id: string) {
  return request<void>({ url: `/expenses/${id}`, method: 'delete' })
}

export function submitExpense(id: string) {
  return request<void>({ url: `/expenses/${id}/submit`, method: 'post' })
}
