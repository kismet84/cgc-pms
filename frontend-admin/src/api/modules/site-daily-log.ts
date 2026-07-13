import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { SiteDailyLogCommand, SiteDailyLogVO } from '@/types/site-daily-log'

export interface SiteDailyLogQuery {
  pageNo?: number
  pageSize?: number
  projectId?: string
  startDate?: string
  endDate?: string
  status?: string
}

export function getSiteDailyLogs(params: SiteDailyLogQuery) {
  return request<PageResult<SiteDailyLogVO>>({ url: '/site-daily-logs', method: 'get', params })
}

export function getSiteDailyLog(id: string) {
  return request<SiteDailyLogVO>({ url: `/site-daily-logs/${id}`, method: 'get' })
}

export function createSiteDailyLog(data: SiteDailyLogCommand) {
  return request<string>({ url: '/site-daily-logs', method: 'post', data })
}

export function updateSiteDailyLog(id: string, data: SiteDailyLogCommand) {
  return request<void>({ url: `/site-daily-logs/${id}`, method: 'put', data })
}

export function submitSiteDailyLog(id: string) {
  return request<void>({ url: `/site-daily-logs/${id}/submit`, method: 'post' })
}
