import { defineStore } from 'pinia'
import { ref } from 'vue'
import type {
  ContractVO,
  ContractItem,
  ContractPaymentTerm,
  ContractApprovalRecord,
} from '@/types/contract'
import {
  getContractDetail,
  createContract as createContractApi,
  updateContract as updateContractApi,
  getContractItems,
  saveContractItems,
  getContractPaymentTerms,
  saveContractPaymentTerms,
  getContractApprovalRecords,
} from '@/api/modules/contract'

function normalizeArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[]
  if (value && typeof value === 'object') {
    const records = (value as { records?: unknown }).records
    if (Array.isArray(records)) return records as T[]
  }
  return []
}

export const useContractStore = defineStore('contract', () => {
  const currentContract = ref<ContractVO | null>(null)
  const items = ref<ContractItem[]>([])
  const paymentTerms = ref<ContractPaymentTerm[]>([])
  const approvalRecords = ref<ContractApprovalRecord[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const itemsLoading = ref(false)
  const termsLoading = ref(false)
  const recordsLoading = ref(false)

  async function fetchContract(id: string) {
    loading.value = true
    try {
      currentContract.value = await getContractDetail(id)
    } catch (e) {
      if (import.meta.env.DEV) {
        console.error('Contract store error:', e)
      }
      throw e
    } finally {
      loading.value = false
    }
  }

  async function createContract(data: Partial<ContractVO>) {
    saving.value = true
    try {
      await createContractApi(data)
    } catch (e) {
      if (import.meta.env.DEV) {
        console.error('Contract store error:', e)
      }
      throw e
    } finally {
      saving.value = false
    }
  }

  async function updateContract(id: string, data: Partial<ContractVO>) {
    saving.value = true
    try {
      await updateContractApi(id, data)
    } catch (e) {
      if (import.meta.env.DEV) {
        console.error('Contract store error:', e)
      }
      throw e
    } finally {
      saving.value = false
    }
  }

  async function fetchItems(contractId: string) {
    itemsLoading.value = true
    try {
      items.value = normalizeArray<ContractItem>(await getContractItems(contractId))
    } catch (e) {
      if (import.meta.env.DEV) {
        console.error('Contract store error:', e)
      }
      throw e
    } finally {
      itemsLoading.value = false
    }
  }

  async function saveItems(contractId: string, itemsList: ContractItem[]) {
    saving.value = true
    try {
      await saveContractItems(contractId, itemsList)
      items.value = itemsList
    } catch (e) {
      if (import.meta.env.DEV) {
        console.error('Contract store error:', e)
      }
      throw e
    } finally {
      saving.value = false
    }
  }

  async function fetchPaymentTerms(contractId: string) {
    termsLoading.value = true
    try {
      paymentTerms.value = normalizeArray<ContractPaymentTerm>(await getContractPaymentTerms(contractId))
    } catch (e) {
      if (import.meta.env.DEV) {
        console.error('Contract store error:', e)
      }
      throw e
    } finally {
      termsLoading.value = false
    }
  }

  async function savePaymentTerms(contractId: string, termsList: ContractPaymentTerm[]) {
    saving.value = true
    try {
      await saveContractPaymentTerms(contractId, termsList)
      paymentTerms.value = termsList
    } catch (e) {
      if (import.meta.env.DEV) {
        console.error('Contract store error:', e)
      }
      throw e
    } finally {
      saving.value = false
    }
  }

  async function fetchApprovalRecords(contractId: string) {
    recordsLoading.value = true
    try {
      approvalRecords.value = normalizeArray<ContractApprovalRecord>(
        await getContractApprovalRecords(contractId),
      )
    } catch (e) {
      if (import.meta.env.DEV) {
        console.error('Contract store error:', e)
      }
      throw e
    } finally {
      recordsLoading.value = false
    }
  }

  function resetState() {
    currentContract.value = null
    items.value = []
    paymentTerms.value = []
    approvalRecords.value = []
    loading.value = false
    saving.value = false
    itemsLoading.value = false
    termsLoading.value = false
    recordsLoading.value = false
  }

  return {
    currentContract,
    items,
    paymentTerms,
    approvalRecords,
    loading,
    saving,
    itemsLoading,
    termsLoading,
    recordsLoading,
    fetchContract,
    createContract,
    updateContract,
    fetchItems,
    saveItems,
    fetchPaymentTerms,
    savePaymentTerms,
    fetchApprovalRecords,
    resetState,
  }
})
