import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ProjectVO } from '@/types/project'
import type { ContractVO } from '@/types/contract'
import type { PartnerVO } from '@/types/partner'
import type { MaterialVO } from '@/types/material'
import { getProjectList } from '@/api/modules/project'
import { getContractLedger } from '@/api/modules/contract'
import { getPartnerList } from '@/api/modules/partner'
import { getMaterialList } from '@/api/modules/material'

export interface FetchContractsParams {
  projectId?: string
  contractType?: string
}

export interface FetchPartnersParams {
  partnerType?: string
}

export interface FetchMaterialsParams {
  status?: string
}

export const useReferenceStore = defineStore('reference', () => {
  // ── Data refs ──
  const projects = ref<ProjectVO[] | null>(null)
  const contracts = ref<ContractVO[] | null>(null)
  const partners = ref<PartnerVO[] | null>(null)
  const materials = ref<MaterialVO[] | null>(null)

  // ── In-flight dedup promises (base queries only) ──
  let projectsPromise: Promise<ProjectVO[]> | null = null
  let contractsPromise: Promise<ContractVO[]> | null = null
  let partnersPromise: Promise<PartnerVO[]> | null = null
  let materialsPromise: Promise<MaterialVO[]> | null = null

  // ── Fetch methods ──

  async function fetchProjects(): Promise<ProjectVO[]> {
    if (projects.value) return projects.value
    if (projectsPromise) return projectsPromise
    projectsPromise = getProjectList({ pageNum: 1, pageSize: 50 })
      .then((res) => {
        projects.value = res.records ?? res.data ?? res
        projectsPromise = null
        return projects.value
      })
      .catch((err) => {
        projectsPromise = null
        throw err
      })
    return projectsPromise
  }

  async function fetchContracts(params?: FetchContractsParams): Promise<ContractVO[]> {
    // When filters are provided, skip cache — this is a filtered query
    if (params && (params.projectId || params.contractType)) {
      const res = await getContractLedger({ pageNo: 1, pageSize: 50, ...params })
      const data = (res.records ?? res.data ?? res) as ContractVO[]
      contracts.value = data
      return data
    }
    // Base (unfiltered) query — cached + deduped
    if (contracts.value) return contracts.value
    if (contractsPromise) return contractsPromise
    contractsPromise = getContractLedger({ pageNo: 1, pageSize: 50 })
      .then((res) => {
        contracts.value = res.records ?? res.data ?? res
        contractsPromise = null
        return contracts.value
      })
      .catch((err) => {
        contractsPromise = null
        throw err
      })
    return contractsPromise
  }

  async function fetchPartners(params?: FetchPartnersParams): Promise<PartnerVO[]> {
    if (params && params.partnerType) {
      const res = await getPartnerList({ pageNum: 1, pageSize: 50, ...params })
      const data = (res.records ?? res.data ?? res) as PartnerVO[]
      partners.value = data
      return data
    }
    if (partners.value) return partners.value
    if (partnersPromise) return partnersPromise
    partnersPromise = getPartnerList({ pageNum: 1, pageSize: 50 })
      .then((res) => {
        partners.value = res.records ?? res.data ?? res
        partnersPromise = null
        return partners.value
      })
      .catch((err) => {
        partnersPromise = null
        throw err
      })
    return partnersPromise
  }

  async function fetchMaterials(params?: FetchMaterialsParams): Promise<MaterialVO[]> {
    if (params && params.status) {
      const res = await getMaterialList({ pageNum: 1, pageSize: 50, ...params })
      const data = (res.records ?? res.data ?? res) as MaterialVO[]
      materials.value = data
      return data
    }
    if (materials.value) return materials.value
    if (materialsPromise) return materialsPromise
    materialsPromise = getMaterialList({ pageNum: 1, pageSize: 50 })
      .then((res) => {
        materials.value = res.records ?? res.data ?? res
        materialsPromise = null
        return materials.value
      })
      .catch((err) => {
        materialsPromise = null
        throw err
      })
    return materialsPromise
  }

  // ── Invalidate methods ──

  function invalidateProjects() {
    projects.value = null
  }

  function invalidateContracts() {
    contracts.value = null
  }

  function invalidatePartners() {
    partners.value = null
  }

  function invalidateMaterials() {
    materials.value = null
  }

  return {
    projects,
    contracts,
    partners,
    materials,
    fetchProjects,
    fetchContracts,
    fetchPartners,
    fetchMaterials,
    invalidateProjects,
    invalidateContracts,
    invalidatePartners,
    invalidateMaterials,
  }
})
