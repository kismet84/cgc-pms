import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AlertLogVO } from '@/types/alert'
import { getAlertRuleCategory } from '@/types/alert'
import {
  getAlertList,
  markAlertRead,
  updateAlertStatus,
  batchMarkAlertRead,
  batchUpdateAlertStatus,
  batchEvaluate,
  acknowledgeAlert,
  type AlertListResponse,
  type AlertListParams,
  type AlertProcessStatus,
  type BatchAlertOperationResult,
} from '@/api/modules/alert'

export const useAlertStore = defineStore('alert', () => {
  const alerts = ref<AlertLogVO[]>([])
  const total = ref(0)
  const pageNo = ref(1)
  const pageSize = ref(20)
  const loading = ref(false)
  const evaluating = ref(false)
  const markingRead = ref<Set<string>>(new Set())

  function normalizeId(value: unknown): string {
    return String(value ?? '')
  }

  function normalizeIds(ids: Array<string | number>) {
    return Array.from(new Set(ids.map((id) => normalizeId(id)).filter((id) => id.length > 0)))
  }

  function markBusy(ids: string[]) {
    ids.forEach((id) => markingRead.value.add(id))
  }

  function clearBusy(ids: string[]) {
    ids.forEach((id) => markingRead.value.delete(id))
  }

  function applyReadState(ids: Array<string | number>) {
    const idSet = new Set(ids.map((id) => normalizeId(id)))
    alerts.value.forEach((alert) => {
      if (idSet.has(normalizeId(alert.id))) {
        alert.isRead = 1
      }
    })
  }

  function applyStatusState(
    ids: Array<string | number>,
    processStatus: AlertProcessStatus,
    statusRemark?: string,
  ) {
    const idSet = new Set(ids.map((id) => normalizeId(id)))
    const now = new Date().toISOString()
    alerts.value.forEach((alert) => {
      if (!idSet.has(normalizeId(alert.id))) return
      alert.processStatus = processStatus
      if (processStatus === 'PROCESSED') {
        alert.processedAt = now
        alert.handledAt = now
      }
      if (processStatus === 'ARCHIVED' || processStatus === 'INVALID') {
        alert.archivedAt = now
        alert.handledAt = now
      }
      alert.statusRemark = statusRemark
      alert.handledStatus = processStatus
    })
  }

  function normalizeAlert(alert: AlertLogVO): AlertLogVO {
    const businessType = alert.bizType ?? alert.businessType ?? alert.sourceType ?? undefined
    const businessId = alert.bizId ?? alert.businessId ?? alert.sourceId ?? undefined
    const handledStatus = alert.handledStatus ?? alert.processStatus ?? undefined
    const handledAt = alert.handledAt ?? alert.processedAt ?? alert.archivedAt ?? undefined
    const handledBy = alert.handledBy ?? undefined

    return {
      ...alert,
      bizType: alert.bizType ?? businessType,
      bizId: alert.bizId ?? businessId,
      sourceType: alert.sourceType ?? businessType,
      sourceId: alert.sourceId ?? businessId,
      businessType: alert.businessType ?? businessType,
      businessId: alert.businessId ?? businessId,
      processStatus: alert.processStatus ?? handledStatus,
      handledStatus,
      handledBy,
      processedAt: alert.processedAt ?? handledAt,
      handledAt,
    }
  }

  function normalizeBatchResult(
    result: BatchAlertOperationResult,
    fallbackIds: string[],
  ): BatchAlertOperationResult {
    const successIds =
      Array.isArray(result?.successIds) && result.successIds.length
        ? result.successIds.map((id) => normalizeId(id))
        : Number(result?.failed ?? 0) === 0
          ? fallbackIds
          : []
    const failures = Array.isArray(result?.failures)
      ? result.failures.map((item) => ({
          alertId: normalizeId(item.alertId),
          reason: String(item.reason ?? '未知原因'),
        }))
      : []
    return {
      total: Number(result?.total ?? fallbackIds.length),
      success: Number(result?.success ?? successIds.length),
      failed: Number(result?.failed ?? failures.length),
      successIds,
      failures,
    }
  }

  function isDefaultScopeAlert(alert: AlertLogVO): boolean {
    const value = alert.defaultScope ?? alert.isDefaultScope ?? alert.inDefaultScope
    return value === true || value === 1 || value === '1' || value === 'true'
  }

  function matchesLegacyFilters(alert: AlertLogVO, params: AlertListParams): boolean {
    if (params.projectId && String(alert.projectId) !== String(params.projectId)) return false
    if (params.severity && alert.severity !== params.severity) return false
    if (params.isRead !== undefined && alert.isRead !== params.isRead) return false
    if (params.processStatus && String(alert.processStatus ?? 'OPEN') !== params.processStatus)
      return false
    if (params.ruleType && alert.ruleType !== params.ruleType) return false
    if (
      params.alertDomain &&
      getAlertRuleCategory(alert.ruleType, alert.alertDomain, alert.category) !== params.alertDomain
    ) {
      return false
    }
    if (params.onlyDefaultScope && !isDefaultScopeAlert(alert)) return false

    const keyword = String(params.keyword ?? '')
      .trim()
      .toLowerCase()
    if (
      keyword &&
      ![
        alert.message,
        alert.ruleType,
        getAlertRuleCategory(alert.ruleType, alert.alertDomain, alert.category),
        String(alert.projectId),
        String(alert.bizId ?? alert.businessId ?? alert.sourceId ?? ''),
        String(alert.bizType ?? alert.businessType ?? alert.sourceType ?? ''),
      ]
        .join(' ')
        .toLowerCase()
        .includes(keyword)
    ) {
      return false
    }

    const triggeredAt = alert.triggeredAt ? new Date(alert.triggeredAt).getTime() : NaN
    const startAt = params.triggeredStart
      ? new Date(params.triggeredStart).getTime()
      : params.triggeredAtStart
        ? new Date(params.triggeredAtStart).getTime()
        : NaN
    const endAt = params.triggeredEnd
      ? new Date(params.triggeredEnd).getTime()
      : params.triggeredAtEnd
        ? new Date(params.triggeredAtEnd).getTime()
        : NaN
    if (!Number.isNaN(startAt) && !Number.isNaN(triggeredAt) && triggeredAt < startAt) return false
    if (!Number.isNaN(endAt) && !Number.isNaN(triggeredAt) && triggeredAt > endAt) return false

    return true
  }

  function applyLegacyPageFallback(list: AlertLogVO[], params: AlertListParams) {
    const normalized = list.map((alert) => normalizeAlert(alert))
    const filtered = normalized.filter((alert) => matchesLegacyFilters(alert, params))
    const nextPageNo = Number(params.pageNo ?? params.pageNum ?? 1)
    const nextPageSize = Number(params.pageSize ?? 20)
    const start = (nextPageNo - 1) * nextPageSize
    alerts.value = filtered.slice(start, start + nextPageSize)
    total.value = filtered.length
    pageNo.value = nextPageNo
    pageSize.value = nextPageSize
  }

  function applyPagedResponse(
    result: Exclude<AlertListResponse, AlertLogVO[]>,
    params: AlertListParams,
  ) {
    alerts.value = Array.isArray(result?.records)
      ? result.records.map((item) => normalizeAlert(item))
      : []
    total.value = Number(result?.total ?? alerts.value.length)
    pageNo.value = Number(result?.pageNo ?? params.pageNo ?? params.pageNum ?? 1)
    pageSize.value = Number(result?.pageSize ?? params.pageSize ?? 20)
  }

  async function fetchAlerts(params: AlertListParams) {
    loading.value = true
    try {
      const result = await getAlertList(params)
      if (Array.isArray(result)) {
        applyLegacyPageFallback(result, params)
      } else {
        applyPagedResponse(result, params)
      }
    } catch (err) {
      if (import.meta.env.DEV) {
        console.error('AlertStore: 加载预警列表失败', err)
      }
      alerts.value = []
      total.value = 0
      throw new Error('加载预警列表失败')
    } finally {
      loading.value = false
    }
  }

  async function markRead(id: string) {
    const normalizedId = normalizeId(id)
    markingRead.value.add(normalizedId)
    try {
      const result = await markAlertRead(id)
      if (result.success) {
        applyReadState([normalizedId])
      }
    } finally {
      markingRead.value.delete(normalizedId)
    }
  }

  async function acknowledge(id: string, remark?: string) {
    const normalizedId = normalizeId(id)
    markingRead.value.add(normalizedId)
    try {
      const result = await acknowledgeAlert(normalizedId, { remark })
      if (result.success) {
        const now = new Date().toISOString()
        alerts.value.forEach((alert) => {
          if (normalizeId(alert.id) !== normalizedId) return
          alert.isRead = 1
          alert.acknowledgedAt = now
        })
      }
      return result
    } finally {
      markingRead.value.delete(normalizedId)
    }
  }

  async function batchMarkRead(ids: Array<string | number>) {
    const uniqueIds = normalizeIds(ids)
    if (!uniqueIds.length) {
      return { total: 0, success: 0, failed: 0, successIds: [], failures: [] }
    }

    markBusy(uniqueIds)
    try {
      const result = normalizeBatchResult(
        await batchMarkAlertRead({ alertIds: uniqueIds }),
        uniqueIds,
      )
      applyReadState(result.successIds)
      return result
    } finally {
      clearBusy(uniqueIds)
    }
  }

  async function changeStatus(
    id: string | number,
    processStatus: AlertProcessStatus,
    statusRemark: string,
  ) {
    const normalizedId = normalizeId(id)
    markingRead.value.add(normalizedId)
    try {
      const result = await updateAlertStatus(normalizedId, { processStatus, statusRemark })
      if (result.success) {
        applyStatusState([normalizedId], processStatus, statusRemark)
      }
    } finally {
      markingRead.value.delete(normalizedId)
    }
  }

  async function batchChangeStatus(
    ids: Array<string | number>,
    processStatus: AlertProcessStatus,
    statusRemark: string,
  ) {
    const uniqueIds = normalizeIds(ids)
    if (!uniqueIds.length) {
      return { total: 0, success: 0, failed: 0, successIds: [], failures: [] }
    }

    markBusy(uniqueIds)
    try {
      const result = normalizeBatchResult(
        await batchUpdateAlertStatus({ alertIds: uniqueIds, processStatus, statusRemark }),
        uniqueIds,
      )
      applyStatusState(result.successIds, processStatus, statusRemark)
      return result
    } finally {
      clearBusy(uniqueIds)
    }
  }

  function updateListState(nextAlerts: AlertLogVO[]) {
    alerts.value = nextAlerts.map((item) => normalizeAlert(item))
  }

  async function triggerBatchEvaluate() {
    evaluating.value = true
    try {
      const result = await batchEvaluate()
      return result
    } finally {
      evaluating.value = false
    }
  }

  function resetState() {
    alerts.value = []
    total.value = 0
    pageNo.value = 1
    pageSize.value = 20
    loading.value = false
    evaluating.value = false
    markingRead.value = new Set()
  }

  return {
    alerts,
    total,
    pageNo,
    pageSize,
    loading,
    evaluating,
    markingRead,
    fetchAlerts,
    markRead,
    acknowledge,
    batchMarkRead,
    changeStatus,
    batchChangeStatus,
    updateListState,
    triggerBatchEvaluate,
    resetState,
  }
})
