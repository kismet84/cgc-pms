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

/** Cache TTL in milliseconds (5 minutes) */
const CACHE_TTL = 5 * 60 * 1000

function isExpired(ts: number | null): boolean {
  if (ts == null) return true
  return Date.now() - ts > CACHE_TTL
}

/**
 * Pagination helper — fetches all pages until total is reached.
 * First page determines pageSize and total; subsequent pages are requested
 * in parallel batches for speed.
 */
async function fetchAllPages<T>(
  fetcher: (pageNo: number, pageSize: number) => Promise<{ total?: number | string; records?: T[]; data?: T[] }>,
  pageSize = 200,
): Promise<T[]> {
  const first = await fetcher(1, pageSize)
  const records: T[] = (first.records ?? first.data ?? []) as T[]
  const total = Number(first.total ?? 0)
  if (total <= pageSize) return records

  const totalPages = Math.ceil(total / pageSize)
  const promises: Promise<T[]>[] = []
  for (let p = 2; p <= totalPages; p++) {
    promises.push(
      fetcher(p, pageSize).then((r) => (r.records ?? r.data ?? []) as T[]),
    )
  }
  const chunks = await Promise.all(promises)
  return records.concat(...chunks)
}

export const useReferenceStore = defineStore('reference', () => {
  // ── Data refs ──
  const projects = ref<ProjectVO[] | null>(null)
  const contracts = ref<ContractVO[] | null>(null)
  const partners = ref<PartnerVO[] | null>(null)
  const materials = ref<MaterialVO[] | null>(null)

  // ── Timestamps for TTL ──
  let projectsFetchedAt: number | null = null
  let contractsFetchedAt: number | null = null
  let partnersFetchedAt: number | null = null
  let materialsFetchedAt: number | null = null

  // ── In-flight dedup promises (base queries only) ──
  let projectsPromise: Promise<ProjectVO[]> | null = null
  let contractsPromise: Promise<ContractVO[]> | null = null
  let partnersPromise: Promise<PartnerVO[]> | null = null
  let materialsPromise: Promise<MaterialVO[]> | null = null

  // ── Fetch methods ──

  async function fetchProjects(): Promise<ProjectVO[]> {
    if (projects.value && !isExpired(projectsFetchedAt)) return projects.value
    if (projectsPromise) return projectsPromise
    projectsPromise = fetchAllPages((pageNo, pageSize) =>
      getProjectList({ pageNo, pageSize })
    ).then((all) => {
      projects.value = all
      projectsFetchedAt = Date.now()
      projectsPromise = null
      return all
    }).catch((err) => {
      projectsPromise = null
      throw err
    })
    return projectsPromise
  }

  async function fetchContracts(params?: FetchContractsParams): Promise<ContractVO[]> {
    // When filters are provided, skip cache — do not pollute base cache
    if (params && (params.projectId || params.contractType)) {
      const res = await getContractLedger({ pageNo: 1, pageSize: 50, ...params })
      return (res.records ?? res.data ?? res) as ContractVO[]
    }
    // Base (unfiltered) query — cached + deduped + TTL
    if (contracts.value && !isExpired(contractsFetchedAt)) return contracts.value
    if (contractsPromise) return contractsPromise
    contractsPromise = fetchAllPages((pageNo, pageSize) =>
      getContractLedger({ pageNo, pageSize })
    ).then((all) => {
      contracts.value = all
      contractsFetchedAt = Date.now()
      contractsPromise = null
      return contracts.value
    }).catch((err) => {
      contractsPromise = null
      throw err
    })
    return contractsPromise
  }

  async function fetchPartners(params?: FetchPartnersParams): Promise<PartnerVO[]> {
    // When filters are provided, skip cache — do not pollute base cache
    if (params && params.partnerType) {
      const res = await getPartnerList({ pageNo: 1, pageSize: 50, ...params })
      return (res.records ?? res.data ?? res) as PartnerVO[]
    }
    if (partners.value && !isExpired(partnersFetchedAt)) return partners.value
    if (partnersPromise) return partnersPromise
    partnersPromise = fetchAllPages((pageNo, pageSize) =>
      getPartnerList({ pageNum: pageNo, pageSize })
    ).then((all) => {
      partners.value = all
      partnersFetchedAt = Date.now()
      partnersPromise = null
      return partners.value
    }).catch((err) => {
      partnersPromise = null
      throw err
    })
    return partnersPromise
  }

  async function fetchMaterials(params?: FetchMaterialsParams): Promise<MaterialVO[]> {
    // When filters are provided, skip cache — do not pollute base cache
    if (params && params.status) {
      const res = await getMaterialList({ pageNo: 1, pageSize: 50, ...params })
      return (res.records ?? res.data ?? res) as MaterialVO[]
    }
    if (materials.value && !isExpired(materialsFetchedAt)) return materials.value
    if (materialsPromise) return materialsPromise
    materialsPromise = fetchAllPages((pageNo, pageSize) =>
      getMaterialList({ pageNum: pageNo, pageSize })
    ).then((all) => {
      materials.value = all
      materialsFetchedAt = Date.now()
      materialsPromise = null
      return materials.value
    }).catch((err) => {
      materialsPromise = null
      throw err
    })
    return materialsPromise
  }

  // ── Invalidate methods ──

  function invalidateProjects() {
    projects.value = null
    projectsFetchedAt = null
  }

  function invalidateContracts() {
    contracts.value = null
    contractsFetchedAt = null
  }

  function invalidatePartners() {
    partners.value = null
    partnersFetchedAt = null
  }

  function invalidateMaterials() {
    materials.value = null
    materialsFetchedAt = null
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
