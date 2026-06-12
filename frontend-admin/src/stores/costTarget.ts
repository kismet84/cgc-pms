import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { CostTargetVO, CostTargetItemVO } from '@/types/costTarget'
import {
  getCostTargetDetail,
  createCostTarget as createCostTargetApi,
  updateCostTarget as updateCostTargetApi,
  getCostTargetItems,
  saveCostTargetItems,
  submitCostTargetForApproval,
  activateCostTarget,
} from '@/api/modules/costTarget'

export const useCostTargetStore = defineStore('costTarget', () => {
  const currentTarget = ref<CostTargetVO | null>(null)
  const items = ref<CostTargetItemVO[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const itemsLoading = ref(false)

  async function fetchTarget(id: string) {
    loading.value = true
    try {
      currentTarget.value = await getCostTargetDetail(id)
    } finally {
      loading.value = false
    }
  }

  async function createTarget(data: Partial<CostTargetVO>) {
    saving.value = true
    try {
      const id = await createCostTargetApi(data)
      return id
    } finally {
      saving.value = false
    }
  }

  async function updateTarget(id: string, data: Partial<CostTargetVO>) {
    saving.value = true
    try {
      await updateCostTargetApi(id, data)
    } finally {
      saving.value = false
    }
  }

  async function fetchItems(targetId: string) {
    itemsLoading.value = true
    try {
      items.value = await getCostTargetItems(targetId)
    } finally {
      itemsLoading.value = false
    }
  }

  async function saveItems(targetId: string, itemsList: CostTargetItemVO[]) {
    saving.value = true
    try {
      await saveCostTargetItems(targetId, itemsList)
      items.value = itemsList
    } finally {
      saving.value = false
    }
  }

  async function submitForApproval(targetId: string) {
    saving.value = true
    try {
      await submitCostTargetForApproval(targetId)
    } finally {
      saving.value = false
    }
  }

  async function doActivate(targetId: string) {
    saving.value = true
    try {
      await activateCostTarget(targetId)
    } finally {
      saving.value = false
    }
  }

  function resetState() {
    currentTarget.value = null
    items.value = []
    loading.value = false
    saving.value = false
    itemsLoading.value = false
  }

  return {
    currentTarget,
    items,
    loading,
    saving,
    itemsLoading,
    fetchTarget,
    createTarget,
    updateTarget,
    fetchItems,
    saveItems,
    submitForApproval,
    doActivate,
    resetState,
  }
})
