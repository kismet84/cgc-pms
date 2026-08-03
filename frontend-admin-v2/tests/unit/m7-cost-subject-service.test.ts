import { beforeEach, describe, expect, it, vi } from 'vitest'
import { loadCostSubjectTree } from '@/services/cost-subject'
import { apiRequest } from '@/services/request'

vi.mock('@/services/request', () => ({ apiRequest: vi.fn() }))

describe('cost subject service', () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset())

  it('normalizes numeric target ratios to decimal strings', async () => {
    vi.mocked(apiRequest).mockResolvedValue([
      {
        id: 901001,
        parentId: 900060,
        subjectCode: '5401.03.01',
        subjectName: '人工成本',
        subjectType: 'LABOR',
        accountCategory: 'COST',
        level: 3,
        sortOrder: 1,
        status: 'ENABLE',
        defaultTargetRatio: 25,
        children: [],
      },
    ])

    const [subject] = await loadCostSubjectTree()

    expect(subject?.id).toBe('901001')
    expect(subject?.defaultTargetRatio).toBe('25')
  })
})
