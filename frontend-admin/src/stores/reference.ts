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
  contractStatus?: string
  approvalStatus?: string
}

export interface FetchPartnersParams {
  partnerType?: string
}

export interface FetchMaterialsParams {
  status?: string
}

/** Cache TTL in milliseconds (5 minutes) */
const CACHE_TTL = 5 * 60 * 1000

const STORAGE_PREFIX = 'cgc_pms_reference_cache'

type CacheKey = 'projects' | 'contracts' | 'partners' | 'materials'

interface CachePayload<T> {
  savedAt: number
  items: T[]
}

function isExpired(ts: number | null): boolean {
  if (ts == null) return true
  return Date.now() - ts > CACHE_TTL
}

function storageKey(key: CacheKey) {
  return `${STORAGE_PREFIX}:${key}`
}

function loadCache<T>(key: CacheKey): CachePayload<T> | null {
  try {
    const raw = localStorage.getItem(storageKey(key))
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<CachePayload<T>>
    if (!Array.isArray(parsed.items) || typeof parsed.savedAt !== 'number') return null
    if (Date.now() - parsed.savedAt > CACHE_TTL) return null
    return { savedAt: parsed.savedAt, items: parsed.items }
  } catch {
    return null
  }
}

function persistCache<T>(key: CacheKey, items: T[], savedAt = Date.now()) {
  try {
    const payload: CachePayload<T> = { savedAt, items }
    localStorage.setItem(storageKey(key), JSON.stringify(payload))
  } catch {
    // ignore storage failures — in-memory refs still work
  }
}

function clearCache(key: CacheKey) {
  try {
    localStorage.removeItem(storageKey(key))
  } catch {
    // ignore
  }
}

/**
 * Pagination helper — fetches all pages until total is reached.
 * First page determines pageSize and total; subsequent pages are requested
 * in parallel batches for speed.
 */
async function fetchAllPages<T>(
  fetcher: (
    pageNo: number,
    pageSize: number,
  ) => Promise<{ total?: number | string; records?: T[]; data?: T[] }>,
  pageSize = 1000,
): Promise<T[]> {
  const first = await fetcher(1, pageSize)
  const records: T[] = (first.records ?? first.data ?? []) as T[]
  const total = Number(first.total ?? 0)
  if (total <= pageSize) return records

  const totalPages = Math.ceil(total / pageSize)
  const promises: Promise<T[]>[] = []
  for (let p = 2; p <= totalPages; p++) {
    promises.push(fetcher(p, pageSize).then((r) => (r.records ?? r.data ?? []) as T[]))
  }
  const chunks = await Promise.all(promises)
  return records.concat(...chunks)
}

export const useReferenceStore = defineStore('reference', () => {
  // ── Data refs ──
  const projectsCache = loadCache<ProjectVO>('projects')
  const contractsCache = loadCache<ContractVO>('contracts')
  const partnersCache = loadCache<PartnerVO>('partners')
  const materialsCache = loadCache<MaterialVO>('materials')

  const projects = ref<ProjectVO[] | null>(projectsCache?.items ?? null)
  const contracts = ref<ContractVO[] | null>(contractsCache?.items ?? null)
  const partners = ref<PartnerVO[] | null>(partnersCache?.items ?? null)
  const materials = ref<MaterialVO[] | null>(materialsCache?.items ?? null)

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
    projectsPromise = fetchAllPages((pageNo, pageSize) => getProjectList({ pageNo, pageSize }))
      .then((all) => {
        projects.value = all
        projectsFetchedAt = Date.now()
        persistCache('projects', all, projectsFetchedAt)
        projectsPromise = null
        return all
      })
      .catch((err) => {
        projectsPromise = null
        throw err
      })
    return projectsPromise
  }

  async function fetchContracts(params?: FetchContractsParams): Promise<ContractVO[]> {
    // Filtered queries should refresh the in-memory list that pages read from,
    // but must not become the base TTL cache for later unfiltered callers.
    if (params && (params.projectId || params.contractType || params.contractStatus || params.approvalStatus)) {
      const res = await getContractLedger({ pageNo: 1, pageSize: 50, ...params })
      const filtered = (res.records ?? res.data ?? res) as ContractVO[]
      contracts.value = filtered
      contractsFetchedAt = null
      return filtered
    }
    // Base (unfiltered) query — cached + deduped + TTL
    if (contracts.value && !isExpired(contractsFetchedAt)) return contracts.value
    if (contractsPromise) return contractsPromise
    contractsPromise = fetchAllPages((pageNo, pageSize) => getContractLedger({ pageNo, pageSize }))
      .then((all) => {
        contracts.value = all
        contractsFetchedAt = Date.now()
        persistCache('contracts', all, contractsFetchedAt)
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
    // When filters are provided, skip cache — do not pollute base cache
    if (params && params.partnerType) {
      const res = await getPartnerList({ pageNo: 1, pageSize: 50, ...params })
      return (res.records ?? res.data ?? res) as PartnerVO[]
    }
    if (partners.value && !isExpired(partnersFetchedAt)) return partners.value
    if (partnersPromise) return partnersPromise
    partnersPromise = fetchAllPages((pageNo, pageSize) =>
      getPartnerList({ pageNum: pageNo, pageSize }),
    )
      .then((all) => {
        partners.value = all
        partnersFetchedAt = Date.now()
        persistCache('partners', all, partnersFetchedAt)
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
    // When filters are provided, skip cache — do not pollute base cache
    if (params && params.status) {
      const res = await getMaterialList({ pageNo: 1, pageSize: 50, ...params })
      return (res.records ?? res.data ?? res) as MaterialVO[]
    }
    if (materials.value && !isExpired(materialsFetchedAt)) return materials.value
    if (materialsPromise) return materialsPromise
    materialsPromise = fetchAllPages((pageNo, pageSize) =>
      getMaterialList({ pageNum: pageNo, pageSize }),
    )
      .then((all) => {
        materials.value = all
        materialsFetchedAt = Date.now()
        persistCache('materials', all, materialsFetchedAt)
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
    projectsFetchedAt = null
    clearCache('projects')
  }

  function invalidateContracts() {
    contracts.value = null
    contractsFetchedAt = null
    clearCache('contracts')
  }

  function invalidatePartners() {
    partners.value = null
    partnersFetchedAt = null
    clearCache('partners')
  }

  function invalidateMaterials() {
    materials.value = null
    materialsFetchedAt = null
    clearCache('materials')
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
