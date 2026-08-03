import type { UserInfo } from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { V2Input } from '@/components'
import CostSubjectPage from '@/pages/master-data/CostSubjectPage.vue'
import * as costSubject from '@/services/cost-subject'
import { useSessionStore } from '@/stores/session'

const route = vi.hoisted(() => ({ path: '/cost/subject/taxonomy' }))

vi.mock('vue-router', () => ({ useRoute: () => route }))
vi.mock('@/services/cost-subject', () => ({
  activateMappingVersion: vi.fn(),
  createAssignmentRule: vi.fn(),
  createBidTransfer: vi.fn(),
  createCostSubject: vi.fn(),
  createFinanceAllocation: vi.fn(),
  createMappingVersion: vi.fn(),
  deleteCostSubject: vi.fn(),
  loadAssignmentRules: vi.fn(),
  loadBidTransfers: vi.fn(),
  loadCostSubject: vi.fn(),
  loadCostSubjectReconciliation: vi.fn(),
  loadCostSubjectTree: vi.fn(),
  loadFinanceAllocations: vi.fn(),
  loadMappingVersions: vi.fn(),
  loadProjectScopes: vi.fn(),
  loadSubjectImpact: vi.fn(),
  reverseBidTransfer: vi.fn(),
  reverseFinanceAllocation: vi.fn(),
  saveProjectScope: vi.fn(),
  toggleCostSubjectStatus: vi.fn(),
  updateCostSubject: vi.fn(),
  updateTargetCostRatios: vi.fn(),
}))

function user(permissions: string[]): UserInfo {
  return { userId: '7', username: 'cost.user', roles: ['USER'], permissions }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  vi.mocked(costSubject.loadCostSubjectTree).mockResolvedValue([
    {
      id: '1',
      parentId: '0',
      subjectCode: '5401',
      subjectName: '合同履约成本',
      subjectType: 'ROOT',
      accountCategory: 'COST',
      level: 1,
      sortOrder: 1,
      status: 'ENABLE',
      children: [
        {
          id: '11',
          parentId: '1',
          subjectCode: '5401.01',
          subjectName: '招投标及前期费用',
          subjectType: 'BID',
          accountCategory: 'COST',
          level: 2,
          sortOrder: 1,
          status: 'ENABLE',
          children: [
            {
              id: '111',
              parentId: '11',
              subjectCode: '5401.01.01',
              subjectName: '投标费用',
              subjectType: 'BID',
              accountCategory: 'COST',
              level: 3,
              sortOrder: 1,
              status: 'ENABLE',
              children: [
                {
                  id: '1111',
                  parentId: '111',
                  subjectCode: '5401.01.01.01',
                  subjectName: '下级测试科目',
                  subjectType: 'BID',
                  accountCategory: 'COST',
                  level: 4,
                  sortOrder: 1,
                  status: 'ENABLE',
                  children: [],
                },
              ],
            },
          ],
        },
        {
          id: '12',
          parentId: '1',
          subjectCode: '5401.02',
          subjectName: '采购阶段成本',
          subjectType: 'PURCHASE',
          accountCategory: 'COST',
          level: 2,
          sortOrder: 2,
          status: 'ENABLE',
          children: [
            {
              id: '121',
              parentId: '12',
              subjectCode: '5401.02.01',
              subjectName: '材料采购价差',
              subjectType: 'PURCHASE',
              accountCategory: 'COST',
              level: 3,
              sortOrder: 1,
              status: 'ENABLE',
              children: [],
            },
          ],
        },
      ],
    },
  ])
  vi.mocked(costSubject.loadMappingVersions).mockResolvedValue([
    {
      id: '2',
      versionCode: 'MAP-2026',
      versionName: '服务端映射版本',
      status: 'DRAFT',
      itemCount: 3,
    },
  ])
  vi.mocked(costSubject.loadAssignmentRules).mockResolvedValue([
    {
      id: '3',
      ruleCode: 'RULE-001',
      versionCode: 'MAP-2026',
      sourceType: 'CONTRACT',
      businessCategory: '*',
      costSubjectId: '1',
      subjectCode: 'COST',
      subjectName: '服务端成本域',
      priority: 100,
      status: 'ENABLE',
      effectiveFrom: '2026-07-27',
    },
  ])
  vi.mocked(costSubject.loadBidTransfers).mockResolvedValue([
    {
      id: '4',
      transferCode: 'BT-001',
      bidProjectName: '服务端投标项目',
      versionNo: 'V1',
      totalAmount: '125.2300',
      status: 'POSTED',
      approvalInstanceId: 'A-1',
    },
  ])
  vi.mocked(costSubject.loadFinanceAllocations).mockResolvedValue([])
  vi.mocked(costSubject.loadCostSubject).mockResolvedValue({
    id: '9',
    parentId: '0',
    subjectCode: 'NEW-COST',
    subjectName: '新成本科目',
    subjectType: 'MATERIAL',
    accountCategory: 'COST',
    level: 1,
    sortOrder: 0,
    status: 'ENABLE',
    children: [],
  })
})

describe('M7 cost-subject center', () => {
  it('loads only taxonomy facts and hides writes without exact permissions', async () => {
    route.path = '/cost/subject/taxonomy'
    useSessionStore().replaceUserInfo(user(['cost:query']))
    const wrapper = mount(CostSubjectPage)
    await flushPromises()

    expect(wrapper.findAll('.v2-card')).toHaveLength(2)
    expect(wrapper.findAll('.cost-subject-page__taxonomy > section')).toHaveLength(3)
    expect(wrapper.get('#cost-subject-first-level-title').text()).toBe('1. 一级科目')
    expect(wrapper.get('#cost-subject-second-level-title').text()).toBe('2. 二级科目')
    expect(wrapper.get('#cost-subject-detail-title').text()).toBe('3. 科目详情')
    expect(wrapper.text()).toContain('招投标及前期费用')
    expect(wrapper.text()).toContain('投标费用')
    expect(wrapper.text()).not.toContain('下级末级科目')
    expect(wrapper.text()).not.toContain('下级测试科目')
    expect(wrapper.text()).not.toContain('请选择科目')
    expect(wrapper.text()).not.toContain('ROOT')
    expect(wrapper.text()).not.toContain('新增一级科目')
    expect(costSubject.loadCostSubjectTree).toHaveBeenCalledOnce()
    expect(costSubject.loadMappingVersions).not.toHaveBeenCalled()
    expect(costSubject.loadBidTransfers).not.toHaveBeenCalled()
  })

  it('shows taxonomy writes to administrators without explicit permissions', async () => {
    route.path = '/cost/subject/taxonomy'
    useSessionStore().replaceUserInfo({
      ...user([]),
      roles: ['SUPER_ADMIN'],
    })
    const wrapper = mount(CostSubjectPage)
    await flushPromises()

    expect(wrapper.text()).toContain('新增一级科目')
  })

  it('accepts four-decimal ratios whose numeric sum only differs by floating-point noise', async () => {
    route.path = '/cost/subject/taxonomy'
    useSessionStore().replaceUserInfo(user(['cost:query', 'cost:edit']))
    const ratios = [...Array(9).fill('10.1'), '9.1']
    vi.mocked(costSubject.loadCostSubjectTree).mockResolvedValue([
      {
        id: '1',
        parentId: '0',
        subjectCode: '5401',
        subjectName: '合同履约成本',
        subjectType: 'ROOT',
        accountCategory: 'COST',
        level: 1,
        sortOrder: 1,
        status: 'ENABLE',
        children: [
          {
            id: '13',
            parentId: '1',
            subjectCode: '5401.03',
            subjectName: '项目目标成本',
            subjectType: 'TARGET_COST',
            accountCategory: 'COST',
            level: 2,
            sortOrder: 3,
            status: 'ENABLE',
            children: ratios.map((ratio, index) => ({
              id: String(901001 + index),
              parentId: '13',
              subjectCode: `5401.03.${String(index + 1).padStart(2, '0')}`,
              subjectName: `目标成本${index + 1}`,
              subjectType: 'TARGET_COST',
              accountCategory: 'COST',
              level: 3,
              sortOrder: index + 1,
              status: 'ENABLE',
              defaultTargetRatio: ratio,
              children: [],
            })),
          },
        ],
      },
    ])

    const wrapper = mount(CostSubjectPage)
    await flushPromises()
    const saveButton = wrapper.findAll('button').find((button) => button.text() === '保存10类比例')!

    expect(saveButton.attributes('disabled')).toBeUndefined()
    await saveButton.trigger('click')
    await flushPromises()
    expect(costSubject.updateTargetCostRatios).toHaveBeenCalledOnce()
  })

  it('links first-level selection to second-level subjects and detail', async () => {
    route.path = '/cost/subject/taxonomy'
    useSessionStore().replaceUserInfo(user(['cost:query']))
    const wrapper = mount(CostSubjectPage)
    await flushPromises()

    expect(wrapper.text()).toContain('5401.01.01')
    const purchase = wrapper
      .findAll('.cost-subject-page__list-item')
      .find((item) => item.text().includes('5401.02'))!
    await purchase.trigger('click')
    expect(purchase.classes()).toContain('is-selected')
    expect(wrapper.text()).toContain('5401.02.01')
    expect(wrapper.text()).not.toContain('5401.01.01')
  })

  it('loads mapping and rule facts without touching other tabs', async () => {
    route.path = '/cost/subject/rules'
    useSessionStore().replaceUserInfo(
      user(['cost:subject:mapping:query', 'cost:subject:rule:query']),
    )
    const wrapper = mount(CostSubjectPage)
    await flushPromises()

    expect(wrapper.text()).toContain('服务端映射版本')
    expect(wrapper.text()).toContain('RULE-001')
    expect(wrapper.text()).not.toContain('新建映射版本')
    expect(costSubject.loadMappingVersions).toHaveBeenCalledOnce()
    expect(costSubject.loadAssignmentRules).toHaveBeenCalledOnce()
    expect(costSubject.loadCostSubjectTree).not.toHaveBeenCalled()
    expect(costSubject.loadBidTransfers).not.toHaveBeenCalled()
  })

  it('places project scope controls in the page heading', async () => {
    route.path = '/cost/subject/scope'
    useSessionStore().replaceUserInfo(user(['cost:subject:scope:query']))
    const wrapper = mount(CostSubjectPage)
    await flushPromises()

    expect(wrapper.find('.v2-card--page-heading .v2-page-heading__filters').exists()).toBe(true)
    expect(
      wrapper.findAllComponents(V2Input).some((input) => input.props('label') === '项目标识'),
    ).toBe(true)
    expect(wrapper.text()).not.toContain('项目适用范围')
  })

  it('normalizes a created subject id and rereads detail plus taxonomy', async () => {
    route.path = '/cost/subject/taxonomy'
    useSessionStore().replaceUserInfo(user(['cost:query', 'cost:add']))
    vi.mocked(costSubject.createCostSubject).mockResolvedValue(9)
    const wrapper = mount(CostSubjectPage)
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '新增一级科目')!
      .trigger('click')
    const inputs = wrapper.findAllComponents(V2Input)
    await inputs
      .find((input) => input.props('label') === '科目编码')!
      .find('input')
      .setValue('NEW-COST')
    await inputs
      .find((input) => input.props('label') === '科目名称')!
      .find('input')
      .setValue('新成本科目')
    document
      .querySelector('#cost-subject-form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flushPromises()

    expect(costSubject.createCostSubject).toHaveBeenCalledOnce()
    expect(costSubject.loadCostSubject).toHaveBeenCalledWith('9')
    expect(costSubject.loadCostSubjectTree).toHaveBeenCalledTimes(2)
  })

  it('preserves server amount strings and hides transfer actions without write permission', async () => {
    route.path = '/cost/subject/trace'
    useSessionStore().replaceUserInfo(user(['cost:subject:audit:query']))
    const wrapper = mount(CostSubjectPage)
    await flushPromises()

    expect(wrapper.text()).toContain('125.2300')
    expect(wrapper.text()).toContain('服务端投标项目')
    expect(wrapper.findAll('button').map((button) => button.text())).not.toEqual(
      expect.arrayContaining(['投标成本转入', '财务费用分摊', '冲销']),
    )
    expect(costSubject.loadBidTransfers).toHaveBeenCalledOnce()
    expect(costSubject.loadFinanceAllocations).toHaveBeenCalledOnce()
    expect(costSubject.loadCostSubjectTree).not.toHaveBeenCalled()
  })
})
