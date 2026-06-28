import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useReferenceStore } from '@/stores/reference'

// Mock API modules
vi.mock('@/api/modules/contract', () => ({
  getContractLedger: vi.fn(),
}))
vi.mock('@/api/modules/partner', () => ({
  getPartnerList: vi.fn(),
}))
vi.mock('@/api/modules/material', () => ({
  getMaterialList: vi.fn(),
}))
vi.mock('@/api/modules/project', () => ({
  getProjectList: vi.fn(),
}))

import { getContractLedger } from '@/api/modules/contract'
import { getPartnerList } from '@/api/modules/partner'
import { getMaterialList } from '@/api/modules/material'

const mockedGetContracts = getContractLedger as ReturnType<typeof vi.fn>
const mockedGetPartners = getPartnerList as ReturnType<typeof vi.fn>
const mockedGetMaterials = getMaterialList as ReturnType<typeof vi.fn>

describe('reference store cache isolation', () => {
  let store: ReturnType<typeof useReferenceStore>

  beforeEach(() => {
    setActivePinia(createPinia())
    store = useReferenceStore()
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2025-01-01T00:00:00Z'))
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
    localStorage.clear()
  })

  it('hydrates cached reference data from localStorage', async () => {
    localStorage.setItem(
      'cgc_pms_reference_cache:projects',
      JSON.stringify({ savedAt: Date.now(), items: [{ id: 'p1', projectName: '项目A' }] }),
    )

    vi.resetModules()
    const { useReferenceStore: freshUseReferenceStore } = await import('@/stores/reference')
    setActivePinia(createPinia())
    store = freshUseReferenceStore()
    expect(store.projects).toEqual([{ id: 'p1', projectName: '项目A' }])
  })

  describe('fetchContracts', () => {
    const allContracts = [
      { id: 'c1', contractName: '合同A', projectId: 'p1', contractType: 'GC' },
      { id: 'c2', contractName: '合同B', projectId: 'p2', contractType: 'SUB' },
    ]
    const filteredContracts = [
      { id: 'c1', contractName: '合同A', projectId: 'p1', contractType: 'GC' },
    ]

    it('filtered fetchContracts should NOT pollute base cache — contracts ref stays untouched', async () => {
      // First, populate the base cache with unfiltered data
      mockedGetContracts.mockResolvedValueOnce({ records: allContracts })
      await store.fetchContracts({})
      expect(store.contracts).toEqual(allContracts)

      // Then do a filtered query — it should return filtered data but NOT overwrite contracts ref
      mockedGetContracts.mockResolvedValueOnce({ records: filteredContracts })
      const filtered = await store.fetchContracts({ projectId: 'p1' })
      expect(filtered).toEqual(filteredContracts)

      // The key fix: contracts ref should still contain the full base set
      expect(store.contracts).toEqual(allContracts)
    })

    it('filtered query no longer writes filtered results to contracts ref', async () => {
      // Filtered query should return data but not touch the shared contracts ref
      mockedGetContracts.mockResolvedValueOnce({ records: filteredContracts })
      const filtered = await store.fetchContracts({ projectId: 'p1' })

      expect(filtered).toEqual(filteredContracts)
      // contracts ref should remain null (not polluted with filtered subset)
      expect(store.contracts).toBeNull()
    })

    it('base unfiltered query populates and uses cache correctly', async () => {
      mockedGetContracts.mockResolvedValueOnce({ records: allContracts })
      const first = await store.fetchContracts({})
      expect(first).toEqual(allContracts)
      expect(store.contracts).toEqual(allContracts)

      // Second call within TTL returns cached data (no second mock call needed)
      // If cache works, it won't call the mock again
      const cached = await store.fetchContracts({})
      expect(cached).toEqual(allContracts)
    })
  })

  describe('fetchPartners', () => {
    const allPartners = [
      { id: 'p1', partnerName: '合作方A', partnerType: 'SUPPLIER' },
      { id: 'p2', partnerName: '合作方B', partnerType: 'SUBCONTRACTOR' },
    ]
    const filteredPartners = [{ id: 'p1', partnerName: '合作方A', partnerType: 'SUPPLIER' }]

    it('filtered fetchPartners should NOT pollute base cache', async () => {
      // Populate base cache first
      mockedGetPartners.mockResolvedValueOnce({ records: allPartners })
      await store.fetchPartners({})
      expect(store.partners).toEqual(allPartners)

      // Filtered query should not change partners ref
      mockedGetPartners.mockResolvedValueOnce({ records: filteredPartners })
      const filtered = await store.fetchPartners({ partnerType: 'SUPPLIER' })

      expect(filtered).toEqual(filteredPartners)
      // partners ref should still contain the full base set
      expect(store.partners).toEqual(allPartners)
    })
  })

  describe('fetchMaterials', () => {
    const allMaterials = [
      { id: 'm1', materialName: '材料A', status: 'ACTIVE' },
      { id: 'm2', materialName: '材料B', status: 'INACTIVE' },
    ]
    const filteredMaterials = [{ id: 'm1', materialName: '材料A', status: 'ACTIVE' }]

    it('filtered fetchMaterials should NOT pollute base cache', async () => {
      // Populate base cache first
      mockedGetMaterials.mockResolvedValueOnce({ records: allMaterials })
      await store.fetchMaterials({})
      expect(store.materials).toEqual(allMaterials)

      // Filtered query should not change materials ref
      mockedGetMaterials.mockResolvedValueOnce({ records: filteredMaterials })
      const filtered = await store.fetchMaterials({ status: 'ACTIVE' })

      expect(filtered).toEqual(filteredMaterials)
      // materials ref should still contain the full base set
      expect(store.materials).toEqual(allMaterials)
    })
  })
})
