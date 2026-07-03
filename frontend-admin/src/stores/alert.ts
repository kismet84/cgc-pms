import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AlertLogVO } from '@/types/alert'
import { getAlertRuleCategory } from '@/types/alert'
import {
  getAlertList,
  markAlertRead,
  updateAlertStatus,
  batchEvaluate,
  type AlertListResponse,
  type AlertListParams,
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

  function isDefaultScopeAlert(alert: AlertLogVO): boolean {
    const value = alert.defaultScope ?? alert.isDefaultScope ?? alert.inDefaultScope
    return value === true || value === 1 || value === '1' || value === 'true'
  }

  function matchesLegacyFilters(alert: AlertLogVO, params: AlertListParams): boolean {
    if (params.projectId && String(alert.projectId) !== String(params.projectId)) return false
    if (params.severity && alert.severity !== params.severity) return false
    if (params.isRead !== undefined && alert.isRead !== params.isRead) return false
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
    const filtered = list.filter((alert) => matchesLegacyFilters(alert, params))
    const nextPageNo = Number(params.pageNo ?? params.pageNum ?? 1)
    const nextPageSize = Number(params.pageSize ?? 20)
    const start = (nextPageNo - 1) * nextPageSize
    alerts.value = filtered.slice(start, start + nextPageSize)
    total.value = filtered.length
    pageNo.value = nextPageNo
    pageSize.value = nextPageSize
  }

  function applyPagedResponse(result: Exclude<AlertListResponse, AlertLogVO[]>, params: AlertListParams) {
    alerts.value = Array.isArray(result?.records) ? result.records : []
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
    markingRead.value.add(normalizeId(id))
    try {
      const result = await markAlertRead(id)
      if (result.success) {
        const alert = alerts.value.find((a) => normalizeId(a.id) === normalizeId(id))
        if (alert) {
          alert.isRead = 1
        }
      }
    } finally {
      markingRead.value.delete(normalizeId(id))
    }
  }

  async function batchMarkRead(ids: Array<string | number>) {
    const uniqueIds = Array.from(
      new Set(ids.map((id) => normalizeId(id)).filter((id) => id.length > 0)),
    )
    if (!uniqueIds.length) return 0

    const results = await Promise.allSettled(uniqueIds.map((id) => markRead(id)))
    return results.filter((item) => item.status === 'fulfilled').length
  }

  async function changeStatus(
    id: string | number,
    processStatus: 'PROCESSED' | 'ARCHIVED' | 'INVALID',
    statusRemark?: string,
  ) {
    const normalizedId = normalizeId(id)
    markingRead.value.add(normalizedId)
    try {
      const result = await updateAlertStatus(normalizedId, { processStatus, statusRemark })
      if (result.success) {
        const alert = alerts.value.find((item) => normalizeId(item.id) === normalizedId)
        if (alert) {
          alert.processStatus = processStatus
          if (processStatus === 'PROCESSED') {
            alert.processedAt = new Date().toISOString()
          }
          if (processStatus === 'ARCHIVED' || processStatus === 'INVALID') {
            alert.archivedAt = new Date().toISOString()
          }
          alert.statusRemark = statusRemark
        }
      }
    } finally {
      markingRead.value.delete(normalizedId)
    }
  }

  function updateListState(nextAlerts: AlertLogVO[]) {
    alerts.value = nextAlerts
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
    batchMarkRead,
    changeStatus,
    updateListState,
    triggerBatchEvaluate,
    resetState,
  }
})
