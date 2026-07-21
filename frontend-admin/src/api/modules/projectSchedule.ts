import { request } from '@/api/request'

export type ScheduleRow = Record<string, unknown>

export interface ScheduleRequest {
  projectId: string
  planCode: string
  planName: string
  plannedStartDate: string
  plannedEndDate: string
  remark?: string
}

export interface WbsTaskRequest {
  taskCode: string
  taskName: string
  parentTaskCode?: string
  predecessorTaskCode?: string
  workArea?: string
  responsibleUserId?: string
  plannedStartDate: string
  plannedEndDate: string
  weightPercent: number
  plannedQuantity?: number
  unit?: string
  remark?: string
}

export interface PeriodPlanRequest {
  schedulePlanId: string
  periodType: 'MONTHLY' | 'WEEKLY'
  parentPeriodPlanId?: string
  periodCode: string
  periodName: string
  startDate: string
  endDate: string
  remark?: string
}

export interface PeriodItemRequest {
  wbsTaskId: string
  targetProgress: number
  plannedQuantity?: number
}

export interface DailyProgressRequest {
  wbsTaskId: string
  currentProgress: number
  completedQuantity: number
  workDescription: string
}

export interface CorrectiveActionRequest {
  snapshotId: string
  actionCode: string
  reason: string
  actionPlan: string
  responsibleUserId: string
  dueDate: string
  remark?: string
}

export function getProjectSchedules(projectId?: string) {
  return request<ScheduleRow[]>({ url: '/project-schedules', method: 'get', params: { projectId } })
}
export function getProjectSchedule(id: string) {
  return request<ScheduleRow>({ url: `/project-schedules/${id}`, method: 'get' })
}
export function createProjectSchedule(data: ScheduleRequest) {
  return request<ScheduleRow>({ url: '/project-schedules', method: 'post', data })
}
export function replaceWbsTasks(id: string, expectedVersion: number, tasks: WbsTaskRequest[]) {
  return request<ScheduleRow>({
    url: `/project-schedules/${id}/tasks`,
    method: 'put',
    data: { expectedVersion, tasks },
  })
}
export function submitProjectSchedule(id: string) {
  return request<ScheduleRow>({ url: `/project-schedules/${id}/submit`, method: 'post' })
}
export function createPeriodPlan(scheduleId: string, data: PeriodPlanRequest) {
  return request<ScheduleRow>({
    url: `/project-schedules/${scheduleId}/period-plans`,
    method: 'post',
    data,
  })
}
export function getPeriodPlan(id: string) {
  return request<ScheduleRow>({ url: `/project-schedules/period-plans/${id}`, method: 'get' })
}
export function replacePeriodPlanItems(id: string, expectedVersion: number, items: PeriodItemRequest[]) {
  return request<ScheduleRow>({
    url: `/project-schedules/period-plans/${id}/items`,
    method: 'put',
    data: { expectedVersion, items },
  })
}
export function submitPeriodPlan(id: string) {
  return request<ScheduleRow>({
    url: `/project-schedules/period-plans/${id}/submit`,
    method: 'post',
  })
}
export function getDailyProgress(dailyLogId: string) {
  return request<ScheduleRow[]>({
    url: `/project-schedules/daily-logs/${dailyLogId}/progress`,
    method: 'get',
  })
}
export function replaceDailyProgress(dailyLogId: string, items: DailyProgressRequest[]) {
  return request<ScheduleRow[]>({
    url: `/project-schedules/daily-logs/${dailyLogId}/progress`,
    method: 'put',
    data: { items },
  })
}
export function calculateProgressSnapshot(id: string, date: string) {
  return request<ScheduleRow>({
    url: `/project-schedules/${id}/snapshots`,
    method: 'post',
    params: { date },
  })
}
export function createCorrectiveAction(id: string, data: CorrectiveActionRequest) {
  return request<ScheduleRow>({
    url: `/project-schedules/${id}/corrective-actions`,
    method: 'post',
    data,
  })
}
export function submitCorrectiveAction(id: string) {
  return request<ScheduleRow>({
    url: `/project-schedules/corrective-actions/${id}/submit`,
    method: 'post',
  })
}
export function getProjectScheduleTrace(id: string) {
  return request<ScheduleRow>({ url: `/project-schedules/${id}/trace`, method: 'get' })
}
