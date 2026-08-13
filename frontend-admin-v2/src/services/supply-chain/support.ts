import { SUPPLY_CHAIN_API } from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

export const POST_METHOD = 'POST'
export const PUT_METHOD = 'PUT'
export const DELETE_METHOD = 'DELETE'

export function post<T>(path: string): Promise<T> {
  return apiRequest<T>(path, { method: POST_METHOD })
}

export function createId<B>(path: string, body: B): Promise<string> {
  return apiRequest<string | number, B>(path, { method: POST_METHOD, body }).then(String)
}

export function saveItems<B>(path: string, id: string, body: B): Promise<void> {
  return apiRequest<void, B>(`${resourcePath(path, id)}/items/batch`, {
    method: POST_METHOD,
    body,
  })
}

export function deleteResource(path: string, id: string): Promise<void> {
  return apiRequest<void>(resourcePath(path, id), { method: DELETE_METHOD })
}

export function resourcePath(path: string, id: string): string {
  return `${path}/${encodedId(id, '业务ID')}`
}

export function eventPath(id: string, action: string): string {
  return `${SUPPLY_CHAIN_API.supplierSourcingEvents}/${encodedId(id, '招采事件ID')}/${action}`
}

export function encodedId(value: string, label: string): string {
  return encodeURIComponent(requiredId(value, label))
}

export function withQuery(path: string, query: object): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'number') {
      if (Number.isInteger(value) && value > 0) params.set(key, String(value))
    } else if (typeof value === 'string' && value.trim()) {
      params.set(key, value.trim())
    }
  }
  const encoded = params.toString()
  return encoded ? `${path}?${encoded}` : path
}

export function requiredId(value: string, label: string): string {
  const normalized = value.trim()
  if (!normalized) throw new TypeError(`${label}不能为空`)
  return normalized
}
