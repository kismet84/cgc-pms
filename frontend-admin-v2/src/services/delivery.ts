import {
  DELIVERY_API,
  type CorrectiveActionCommand,
  type CorrectiveActionRecord,
  type DailyProgressCommand,
  type PeriodPlanCommand,
  type PeriodPlanDetail,
  type PeriodPlanItemCommand,
  type PeriodPlanRecord,
  type ScheduleCommand,
  type ScheduleDetail,
  type ScheduleRecord,
  type ScheduleSnapshotRecord,
  type ScheduleTraceRecord,
  type SiteDailyLogCommand,
  type SiteDailyLogPage,
  type SiteDailyLogQuery,
  type SiteDailyLogRecord,
  type SiteDailyQualitySafetyRecord,
  type SiteFileRecord,
  type WbsTaskCommand,
  type WbsTaskRecord,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

export function loadSchedules(projectId: string, signal?: AbortSignal): Promise<ScheduleRecord[]> {
  return apiRequest<Record<string, unknown>[]>(
    `${DELIVERY_API.schedules}?projectId=${encodeURIComponent(requiredId(projectId))}`,
    { signal },
  ).then((rows) => rows.map(normalizeSchedule))
}

export function loadSchedule(id: string, signal?: AbortSignal): Promise<ScheduleDetail> {
  return apiRequest<Record<string, unknown>>(DELIVERY_API.schedule(requiredId(id)), {
    signal,
  }).then(normalizeScheduleDetail)
}

export function createSchedule(command: ScheduleCommand): Promise<ScheduleDetail> {
  return apiRequest<Record<string, unknown>, ScheduleCommand>(DELIVERY_API.schedules, {
    method: 'POST',
    body: command,
  }).then(normalizeScheduleDetail)
}

export function replaceWbsTasks(
  scheduleId: string,
  expectedVersion: number,
  tasks: WbsTaskCommand[],
): Promise<ScheduleDetail> {
  return apiRequest<Record<string, unknown>, { expectedVersion: number; tasks: WbsTaskCommand[] }>(
    DELIVERY_API.scheduleTasks(requiredId(scheduleId)),
    { method: 'PUT', body: { expectedVersion, tasks } },
  ).then(normalizeScheduleDetail)
}

export function submitSchedule(scheduleId: string): Promise<ScheduleDetail> {
  return apiRequest<Record<string, unknown>>(DELIVERY_API.scheduleSubmit(requiredId(scheduleId)), {
    method: 'POST',
  }).then(normalizeScheduleDetail)
}

export function createPeriodPlan(
  scheduleId: string,
  command: PeriodPlanCommand,
): Promise<PeriodPlanRecord> {
  return apiRequest<Record<string, unknown>, PeriodPlanCommand>(
    DELIVERY_API.schedulePeriods(requiredId(scheduleId)),
    { method: 'POST', body: command },
  ).then(normalizePeriodPlan)
}

export function loadPeriodPlan(id: string, signal?: AbortSignal): Promise<PeriodPlanDetail> {
  return apiRequest<Record<string, unknown>>(DELIVERY_API.period(requiredId(id)), {
    signal,
  }).then(normalizePeriodPlanDetail)
}

export function replacePeriodPlanItems(
  periodId: string,
  expectedVersion: number,
  items: PeriodPlanItemCommand[],
): Promise<PeriodPlanDetail> {
  return apiRequest<
    Record<string, unknown>,
    { expectedVersion: number; items: PeriodPlanItemCommand[] }
  >(DELIVERY_API.periodItems(requiredId(periodId)), {
    method: 'PUT',
    body: { expectedVersion, items },
  }).then(normalizePeriodPlanDetail)
}

export function submitPeriodPlan(periodId: string): Promise<PeriodPlanDetail> {
  return apiRequest<Record<string, unknown>>(DELIVERY_API.periodSubmit(requiredId(periodId)), {
    method: 'POST',
  }).then(normalizePeriodPlanDetail)
}

export function loadDailyProgress(
  dailyLogId: string,
  signal?: AbortSignal,
): Promise<Array<ReturnType<typeof normalizeDailyProgress>>> {
  return apiRequest<Record<string, unknown>[]>(DELIVERY_API.dailyProgress(requiredId(dailyLogId)), {
    signal,
  }).then((rows) => rows.map(normalizeDailyProgress))
}

export function replaceDailyProgress(
  dailyLogId: string,
  items: DailyProgressCommand[],
): Promise<Array<ReturnType<typeof normalizeDailyProgress>>> {
  return apiRequest<Record<string, unknown>[], { items: DailyProgressCommand[] }>(
    DELIVERY_API.dailyProgress(requiredId(dailyLogId)),
    { method: 'PUT', body: { items } },
  ).then((rows) => rows.map(normalizeDailyProgress))
}

export function calculateScheduleSnapshot(
  scheduleId: string,
  date: string,
): Promise<ScheduleSnapshotRecord> {
  const safeDate = requiredText(date, '快照日期不能为空')
  return apiRequest<Record<string, unknown>>(
    `${DELIVERY_API.scheduleSnapshots(requiredId(scheduleId))}?date=${encodeURIComponent(safeDate)}`,
    { method: 'POST' },
  ).then(normalizeSnapshot)
}

export function createCorrectiveAction(
  scheduleId: string,
  command: CorrectiveActionCommand,
): Promise<CorrectiveActionRecord> {
  return apiRequest<Record<string, unknown>, CorrectiveActionCommand>(
    DELIVERY_API.correctiveActions(requiredId(scheduleId)),
    { method: 'POST', body: command },
  ).then(normalizeCorrectiveAction)
}

export function submitCorrectiveAction(id: string): Promise<CorrectiveActionRecord> {
  return apiRequest<Record<string, unknown>>(DELIVERY_API.correctiveSubmit(requiredId(id)), {
    method: 'POST',
  }).then(normalizeCorrectiveAction)
}

export function loadScheduleTrace(id: string, signal?: AbortSignal): Promise<ScheduleTraceRecord> {
  return apiRequest<Record<string, unknown>>(DELIVERY_API.scheduleTrace(requiredId(id)), {
    signal,
  }).then(normalizeScheduleTrace)
}

export function loadSiteDailyLogs(
  query: SiteDailyLogQuery,
  signal?: AbortSignal,
): Promise<SiteDailyLogPage> {
  return apiRequest<SiteDailyLogPage>(withQuery(DELIVERY_API.siteDailyLogs, query), { signal })
}

export function loadSiteDailyLog(id: string, signal?: AbortSignal): Promise<SiteDailyLogRecord> {
  return apiRequest<SiteDailyLogRecord>(DELIVERY_API.siteDailyLog(requiredId(id)), { signal })
}

export function loadSiteDailyQualitySafety(
  id: string,
  signal?: AbortSignal,
): Promise<SiteDailyQualitySafetyRecord[]> {
  return apiRequest<SiteDailyQualitySafetyRecord[]>(
    DELIVERY_API.siteDailyQualitySafety(requiredId(id)),
    { signal },
  )
}

export function createSiteDailyLog(command: SiteDailyLogCommand): Promise<string> {
  return apiRequest<string, SiteDailyLogCommand>(DELIVERY_API.siteDailyLogs, {
    method: 'POST',
    body: command,
  })
}

export function updateSiteDailyLog(id: string, command: SiteDailyLogCommand): Promise<void> {
  return apiRequest<void, SiteDailyLogCommand>(DELIVERY_API.siteDailyLog(requiredId(id)), {
    method: 'PUT',
    body: command,
  })
}

export function submitSiteDailyLog(id: string): Promise<void> {
  return apiRequest<void>(DELIVERY_API.siteDailySubmit(requiredId(id)), { method: 'POST' })
}

export function listSiteFiles(
  businessType: string,
  businessId: string,
  signal?: AbortSignal,
): Promise<SiteFileRecord[]> {
  const params = new URLSearchParams({
    businessType: requiredText(businessType, '业务类型不能为空'),
    businessId: requiredId(businessId),
  })
  return apiRequest<SiteFileRecord[]>(`${DELIVERY_API.files}?${params.toString()}`, { signal })
}

export function uploadSiteFile(
  file: File,
  businessType: string,
  businessId: string,
  documentType?: string,
): Promise<SiteFileRecord> {
  const formData = new FormData()
  formData.append('file', file)
  const params = new URLSearchParams({
    businessType: requiredText(businessType, '业务类型不能为空'),
    businessId: requiredId(businessId),
  })
  const safeDocumentType = documentType?.trim()
  if (safeDocumentType) params.set('documentType', safeDocumentType)
  return apiRequest<SiteFileRecord, FormData>(`${DELIVERY_API.fileUpload}?${params.toString()}`, {
    method: 'POST',
    body: formData,
  })
}

export function getSiteFileUrl(id: string): Promise<string> {
  return apiRequest<string>(DELIVERY_API.fileUrl(requiredId(id)))
}

export function deleteSiteFile(id: string): Promise<void> {
  return apiRequest<void>(DELIVERY_API.fileDelete(requiredId(id)), { method: 'DELETE' })
}

function normalizeScheduleDetail(row: Record<string, unknown>): ScheduleDetail {
  return {
    ...normalizeSchedule(row),
    tasks: array(row.tasks).map(normalizeTask),
    periodPlans: array(row.periodPlans).map(normalizePeriodPlan),
    latestSnapshot: object(row.latestSnapshot) ? normalizeSnapshot(row.latestSnapshot) : null,
    correctiveActions: array(row.correctiveActions).map(normalizeCorrectiveAction),
  }
}

function normalizeSchedule(row: Record<string, unknown>): ScheduleRecord {
  return {
    id: requiredString(row, 'id'),
    projectId: requiredString(row, 'projectId', 'project_id'),
    planCode: requiredString(row, 'planCode', 'plan_code'),
    planName: requiredString(row, 'planName', 'plan_name'),
    planType: requiredString(row, 'planType', 'plan_type') as ScheduleRecord['planType'],
    versionNo: requiredNumber(row, 'versionNo', 'version_no'),
    parentPlanId: optionalString(row, 'parentPlanId', 'parent_plan_id'),
    correctiveActionId: optionalString(row, 'correctiveActionId', 'corrective_action_id'),
    plannedStartDate: requiredString(row, 'plannedStartDate', 'planned_start_date'),
    plannedEndDate: requiredString(row, 'plannedEndDate', 'planned_end_date'),
    status: requiredString(row, 'status') as ScheduleRecord['status'],
    version: optionalNumber(row, 'version'),
    approvalInstanceId: optionalString(row, 'approvalInstanceId', 'approval_instance_id'),
    activatedAt: optionalString(row, 'activatedAt', 'activated_at'),
    remark: optionalString(row, 'remark'),
  }
}

function normalizeTask(row: Record<string, unknown>): WbsTaskRecord {
  return {
    id: requiredString(row, 'id'),
    schedulePlanId: requiredString(row, 'schedulePlanId', 'schedule_plan_id'),
    parentTaskId: optionalString(row, 'parentTaskId', 'parent_task_id'),
    predecessorTaskId: optionalString(row, 'predecessorTaskId', 'predecessor_task_id'),
    taskCode: requiredString(row, 'taskCode', 'task_code'),
    taskName: requiredString(row, 'taskName', 'task_name'),
    workArea: optionalString(row, 'workArea', 'work_area'),
    responsibleUserId: optionalString(row, 'responsibleUserId', 'responsible_user_id'),
    plannedStartDate: requiredString(row, 'plannedStartDate', 'planned_start_date'),
    plannedEndDate: requiredString(row, 'plannedEndDate', 'planned_end_date'),
    weightPercent: requiredDecimalString(row, 'weightPercent', 'weight_percent'),
    plannedQuantity: optionalDecimalString(row, 'plannedQuantity', 'planned_quantity'),
    unit: optionalString(row, 'unit'),
    actualStartDate: optionalString(row, 'actualStartDate', 'actual_start_date'),
    actualEndDate: optionalString(row, 'actualEndDate', 'actual_end_date'),
    actualQuantity: optionalDecimalString(row, 'actualQuantity', 'actual_quantity'),
    actualProgress: decimalString(row, 'actualProgress', 'actual_progress') ?? '0',
    status: requiredString(row, 'status'),
    sortOrder: optionalNumber(row, 'sortOrder', 'sort_order'),
    parentTaskCode: optionalString(row, 'parentTaskCode', 'parent_task_code'),
    predecessorTaskCode: optionalString(row, 'predecessorTaskCode', 'predecessor_task_code'),
    remark: optionalString(row, 'remark'),
  }
}

function normalizePeriodPlanDetail(row: Record<string, unknown>): PeriodPlanDetail {
  return {
    ...normalizePeriodPlan(row),
    items: array(row.items).map(normalizePeriodItem),
  }
}

function normalizePeriodPlan(row: Record<string, unknown>): PeriodPlanRecord {
  return {
    id: requiredString(row, 'id'),
    projectId: requiredString(row, 'projectId', 'project_id'),
    schedulePlanId: requiredString(row, 'schedulePlanId', 'schedule_plan_id'),
    parentPeriodPlanId: optionalString(row, 'parentPeriodPlanId', 'parent_period_plan_id'),
    periodType: requiredString(row, 'periodType', 'period_type') as PeriodPlanRecord['periodType'],
    periodCode: requiredString(row, 'periodCode', 'period_code'),
    periodName: requiredString(row, 'periodName', 'period_name'),
    startDate: requiredString(row, 'startDate', 'start_date'),
    endDate: requiredString(row, 'endDate', 'end_date'),
    status: requiredString(row, 'status') as PeriodPlanRecord['status'],
    version: optionalNumber(row, 'version'),
    approvalInstanceId: optionalString(row, 'approvalInstanceId', 'approval_instance_id'),
    remark: optionalString(row, 'remark'),
  }
}

function normalizePeriodItem(row: Record<string, unknown>): PeriodPlanDetail['items'][number] {
  return {
    id: requiredString(row, 'id'),
    wbsTaskId: requiredString(row, 'wbsTaskId', 'wbs_task_id'),
    taskCode: requiredString(row, 'taskCode', 'task_code'),
    taskName: requiredString(row, 'taskName', 'task_name'),
    targetProgress: requiredDecimalString(row, 'targetProgress', 'target_progress'),
    plannedQuantity: optionalDecimalString(row, 'plannedQuantity', 'planned_quantity'),
    actualProgress: decimalString(row, 'actualProgress', 'actual_progress') ?? '0',
  }
}

function normalizeSnapshot(row: Record<string, unknown>): ScheduleSnapshotRecord {
  return {
    id: requiredString(row, 'id'),
    projectId: requiredString(row, 'projectId', 'project_id'),
    schedulePlanId: requiredString(row, 'schedulePlanId', 'schedule_plan_id'),
    snapshotDate: requiredString(row, 'snapshotDate', 'snapshot_date'),
    sourceDailyLogId: optionalString(row, 'sourceDailyLogId', 'source_daily_log_id'),
    plannedProgress: requiredDecimalString(row, 'plannedProgress', 'planned_progress'),
    actualProgress: requiredDecimalString(row, 'actualProgress', 'actual_progress'),
    deviationPercent: requiredDecimalString(row, 'deviationPercent', 'deviation_percent'),
    laggingTaskCount: requiredNumber(row, 'laggingTaskCount', 'lagging_task_count'),
    status: requiredString(row, 'status') as ScheduleSnapshotRecord['status'],
  }
}

function normalizeCorrectiveAction(row: Record<string, unknown>): CorrectiveActionRecord {
  return {
    id: requiredString(row, 'id'),
    schedulePlanId: requiredString(row, 'schedulePlanId', 'schedule_plan_id'),
    snapshotId: requiredString(row, 'snapshotId', 'snapshot_id'),
    actionCode: requiredString(row, 'actionCode', 'action_code'),
    reason: requiredString(row, 'reason'),
    actionPlan: requiredString(row, 'actionPlan', 'action_plan'),
    responsibleUserId: requiredString(row, 'responsibleUserId', 'responsible_user_id'),
    dueDate: requiredString(row, 'dueDate', 'due_date'),
    status: requiredString(row, 'status'),
    approvalInstanceId: optionalString(row, 'approvalInstanceId', 'approval_instance_id'),
    revisionSchedulePlanId: optionalString(
      row,
      'revisionSchedulePlanId',
      'revision_schedule_plan_id',
    ),
    remark: optionalString(row, 'remark'),
  }
}

function normalizeScheduleTrace(row: Record<string, unknown>): ScheduleTraceRecord {
  const wbsTasks = array(row.wbsTasks)
  const periodPlans = array(row.periodPlans)
  const dailyProgress = array(row.dailyProgress)
  const snapshots = array(row.snapshots)
  const alerts = array(row.alerts)
  const correctiveActions = array(row.correctiveActions)
  const revisions = array(row.revisions)
  return {
    schedule: normalizeSchedule(objectValue(row, 'schedule')),
    wbsTasks: wbsTasks.map(normalizeTask),
    periodPlans: periodPlans.map(normalizePeriodPlan),
    dailyProgress: dailyProgress.map(normalizeDailyProgress),
    snapshots: snapshots.map(normalizeSnapshot),
    alerts,
    correctiveActions: correctiveActions.map(normalizeCorrectiveAction),
    revisions: revisions.map(normalizeSchedule),
  }
}

function normalizeDailyProgress(row: Record<string, unknown>) {
  return {
    id: optionalString(row, 'id'),
    dailyLogId: requiredString(row, 'dailyLogId', 'daily_log_id'),
    schedulePlanId: optionalString(row, 'schedulePlanId', 'schedule_plan_id'),
    weeklyPlanId: optionalString(row, 'weeklyPlanId', 'weekly_plan_id'),
    wbsTaskId: requiredString(row, 'wbsTaskId', 'wbs_task_id'),
    taskCode: requiredString(row, 'taskCode', 'task_code'),
    taskName: requiredString(row, 'taskName', 'task_name'),
    previousProgress: decimalString(row, 'previousProgress', 'previous_progress') ?? '0',
    currentProgress: requiredDecimalString(row, 'currentProgress', 'current_progress'),
    completedQuantity: requiredDecimalString(row, 'completedQuantity', 'completed_quantity'),
    workDescription: requiredString(row, 'workDescription', 'work_description'),
  }
}

function withQuery(path: string, query: SiteDailyLogQuery): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'number') {
      if (Number.isInteger(value) && value > 0) params.set(key, String(value))
      continue
    }
    const normalized = value?.trim()
    if (normalized) params.set(key, normalized)
  }
  const search = params.toString()
  return search ? `${path}?${search}` : path
}

function requiredId(value: string): string {
  return requiredText(value, 'ID不能为空')
}

function requiredText(value: string, message: string): string {
  const normalized = value.trim()
  if (!normalized) throw new TypeError(message)
  return normalized
}

function array(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value) ? value.filter(object) : []
}

function object(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function objectValue(row: Record<string, unknown>, key: string): Record<string, unknown> {
  const value = row[key]
  if (!object(value)) throw new TypeError(`缺少对象字段 ${key}`)
  return value
}

function requiredString(row: Record<string, unknown>, ...keys: string[]): string {
  const value = optionalString(row, ...keys)
  if (value == null) throw new TypeError(`缺少字段 ${keys[0]}`)
  return value
}

function optionalString(row: Record<string, unknown>, ...keys: string[]): string | null {
  for (const key of keys) {
    const value = row[key]
    if (value == null) continue
    const normalized = String(value).trim()
    return normalized ? normalized : null
  }
  return null
}

function requiredNumber(row: Record<string, unknown>, ...keys: string[]): number {
  const value = optionalNumber(row, ...keys)
  if (value == null) throw new TypeError(`缺少数字字段 ${keys[0]}`)
  return value
}

function optionalNumber(row: Record<string, unknown>, ...keys: string[]): number | undefined {
  for (const key of keys) {
    const value = row[key]
    if (value == null) continue
    const numeric = Number(value)
    if (Number.isFinite(numeric)) return numeric
  }
  return undefined
}

function requiredDecimalString(row: Record<string, unknown>, ...keys: string[]): string {
  const value = decimalString(row, ...keys)
  if (value == null) throw new TypeError(`缺少金额/进度字段 ${keys[0]}`)
  return value
}

function optionalDecimalString(row: Record<string, unknown>, ...keys: string[]): string | null {
  return decimalString(row, ...keys) ?? null
}

function decimalString(row: Record<string, unknown>, ...keys: string[]): string | undefined {
  for (const key of keys) {
    const value = row[key]
    if (value == null) continue
    const normalized = String(value).trim()
    if (normalized) return normalized
  }
  return undefined
}
