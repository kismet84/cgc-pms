import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AlertLogVO } from '@/types/alert'
import { normalizeArray } from '@/utils/normalizeArray'
import {
  getAlertList,
  markAlertRead,
  batchEvaluate,
  type AlertListParams,
} from '@/api/modules/alert'

export const useAlertStore = defineStore('alert', () => {
  const alerts = ref<AlertLogVO[]>([])
  const loading = ref(false)
  const evaluating = ref(false)
  const markingRead = ref<Set<string>>(new Set())

  async function fetchAlerts(params: AlertListParams) {
    loading.value = true
    try {
      alerts.value = normalizeArray<AlertLogVO>(await getAlertList(params))
    } catch (err) {
      if (import.meta.env.DEV) {
        console.error('AlertStore: 加载预警列表失败', err)
      }
      alerts.value = []
      throw new Error('加载预警列表失败')
    } finally {
      loading.value = false
    }
  }

  async function markRead(id: string) {
    markingRead.value.add(id)
    try {
      const result = await markAlertRead(id)
      if (result.success) {
        const alert = alerts.value.find((a) => a.id === id)
        if (alert) {
          alert.isRead = 1
        }
      }
    } finally {
      markingRead.value.delete(id)
    }
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
    loading.value = false
    evaluating.value = false
    markingRead.value = new Set()
  }

  return {
    alerts,
    loading,
    evaluating,
    markingRead,
    fetchAlerts,
    markRead,
    triggerBatchEvaluate,
    resetState,
  }
})
