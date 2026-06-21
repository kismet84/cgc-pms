import { defineStore } from 'pinia'
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import type {
  SettlementVO,
  SettlementItemVO,
  SettlementVariationItemVO,
  SettlementPaymentItemVO,
  SettlementCostItemVO,
  SettlementAttachmentVO,
  SettlementApprovalRecordVO,
} from '@/types/settlement'
import {
  getSettlementDetail,
  createSettlement as createSettlementApi,
  updateSettlement as updateSettlementApi,
  getSettlementItems,
  saveSettlementItems,
  getSettlementVariations,
  getSettlementPayments,
  getSettlementCosts,
  getSettlementAttachments,
  getSettlementApprovalRecords,
} from '@/api/modules/settlement'

export const useSettlementStore = defineStore('settlement', () => {
  const currentSettlement = ref<SettlementVO | null>(null)
  const items = ref<SettlementItemVO[]>([])
  const variations = ref<SettlementVariationItemVO[]>([])
  const payments = ref<SettlementPaymentItemVO[]>([])
  const costs = ref<SettlementCostItemVO[]>([])
  const attachments = ref<SettlementAttachmentVO[]>([])
  const approvalRecords = ref<SettlementApprovalRecordVO[]>([])

  const loading = ref(false)
  const saving = ref(false)
  const itemsLoading = ref(false)
  const variationsLoading = ref(false)
  const paymentsLoading = ref(false)
  const costsLoading = ref(false)
  const attachmentsLoading = ref(false)
  const recordsLoading = ref(false)

  async function fetchSettlement(id: string) {
    loading.value = true
    try {
      currentSettlement.value = await getSettlementDetail(id)
    } catch (error) {
      message.error('加载结算信息失败')
      throw error
    } finally {
      loading.value = false
    }
  }

  async function createSettlement(data: Partial<SettlementVO>) {
    saving.value = true
    try {
      await createSettlementApi(data)
    } catch (error) {
      message.error('创建结算失败')
      throw error
    } finally {
      saving.value = false
    }
  }

  async function updateSettlement(id: string, data: Partial<SettlementVO>) {
    saving.value = true
    try {
      await updateSettlementApi(id, data)
    } catch (error) {
      message.error('更新结算失败')
      throw error
    } finally {
      saving.value = false
    }
  }

  async function fetchItems(settlementId: string) {
    itemsLoading.value = true
    try {
      items.value = await getSettlementItems(settlementId)
    } catch (error) {
      message.error('加载结算明细失败')
      throw error
    } finally {
      itemsLoading.value = false
    }
  }

  async function saveItems(settlementId: string, itemsList: SettlementItemVO[]) {
    saving.value = true
    try {
      await saveSettlementItems(settlementId, itemsList)
      items.value = itemsList
    } catch (error) {
      message.error('保存结算明细失败')
      throw error
    } finally {
      saving.value = false
    }
  }

  async function fetchVariations(settlementId: string) {
    variationsLoading.value = true
    try {
      variations.value = await getSettlementVariations(settlementId)
    } catch (error) {
      message.error('加载变更明细失败')
      throw error
    } finally {
      variationsLoading.value = false
    }
  }

  async function fetchPayments(settlementId: string) {
    paymentsLoading.value = true
    try {
      payments.value = await getSettlementPayments(settlementId)
    } catch (error) {
      message.error('加载付款明细失败')
      throw error
    } finally {
      paymentsLoading.value = false
    }
  }

  async function fetchCosts(settlementId: string) {
    costsLoading.value = true
    try {
      costs.value = await getSettlementCosts(settlementId)
    } catch (error) {
      message.error('加载成本明细失败')
      throw error
    } finally {
      costsLoading.value = false
    }
  }

  async function fetchAttachments(settlementId: string) {
    attachmentsLoading.value = true
    try {
      attachments.value = await getSettlementAttachments(settlementId)
    } catch (error) {
      message.error('加载附件失败')
      throw error
    } finally {
      attachmentsLoading.value = false
    }
  }

  async function fetchApprovalRecords(settlementId: string) {
    recordsLoading.value = true
    try {
      approvalRecords.value = await getSettlementApprovalRecords(settlementId)
    } catch (error) {
      message.error('加载审批记录失败')
      throw error
    } finally {
      recordsLoading.value = false
    }
  }

  function resetState() {
    currentSettlement.value = null
    items.value = []
    variations.value = []
    payments.value = []
    costs.value = []
    attachments.value = []
    approvalRecords.value = []
    loading.value = false
    saving.value = false
    itemsLoading.value = false
    variationsLoading.value = false
    paymentsLoading.value = false
    costsLoading.value = false
    attachmentsLoading.value = false
    recordsLoading.value = false
  }

  return {
    currentSettlement,
    items,
    variations,
    payments,
    costs,
    attachments,
    approvalRecords,
    loading,
    saving,
    itemsLoading,
    variationsLoading,
    paymentsLoading,
    costsLoading,
    attachmentsLoading,
    recordsLoading,
    fetchSettlement,
    createSettlement,
    updateSettlement,
    fetchItems,
    saveItems,
    fetchVariations,
    fetchPayments,
    fetchCosts,
    fetchAttachments,
    fetchApprovalRecords,
    resetState,
  }
})
