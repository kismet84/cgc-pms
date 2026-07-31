import { defineStore } from 'pinia'
import { ref } from 'vue'
import { message } from 'ant-design-vue'
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
    } catch (error) {
      message.error('加载成本目标失败')
      throw error
    } finally {
      loading.value = false
    }
  }

  async function createTarget(data: Partial<CostTargetVO>) {
    saving.value = true
    try {
      const id = await createCostTargetApi(data)
      return id
    } catch (error) {
      message.error('创建成本目标失败')
      throw error
    } finally {
      saving.value = false
    }
  }

  async function updateTarget(id: string, data: Partial<CostTargetVO>) {
    saving.value = true
    try {
      await updateCostTargetApi(id, data)
    } catch (error) {
      message.error('更新成本目标失败')
      throw error
    } finally {
      saving.value = false
    }
  }

  async function fetchItems(targetId: string) {
    itemsLoading.value = true
    try {
      items.value = await getCostTargetItems(targetId)
    } catch (error) {
      message.error('加载成本目标明细失败')
      throw error
    } finally {
      itemsLoading.value = false
    }
  }

  async function saveItems(targetId: string, itemsList: CostTargetItemVO[]) {
    saving.value = true
    try {
      await saveCostTargetItems(targetId, itemsList)
      items.value = itemsList
    } catch (error) {
      message.error('保存成本目标明细失败')
      throw error
    } finally {
      saving.value = false
    }
  }

  async function submitForApproval(targetId: string) {
    saving.value = true
    try {
      await submitCostTargetForApproval(targetId)
    } catch (error) {
      message.error('提交审批失败')
      throw error
    } finally {
      saving.value = false
    }
  }

  async function doActivate(targetId: string) {
    saving.value = true
    try {
      await activateCostTarget(targetId)
    } catch (error) {
      message.error('激活成本目标失败')
      throw error
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
