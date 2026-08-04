import type { UserInfo } from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { V2Input, V2StatusToggle } from '@/components'
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

  it('keeps fixed target-cost subjects read-only and removes the default-ratio card', async () => {
    route.path = '/cost/subject/taxonomy'
    useSessionStore().replaceUserInfo(user(['cost:query', 'cost:edit']))
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
            children: [
              {
                id: '901001',
                parentId: '13',
                subjectCode: '5401.03.01',
                subjectName: '人工成本',
                subjectType: 'LABOR',
                accountCategory: 'COST',
                level: 3,
                sortOrder: 1,
                status: 'ENABLE',
                defaultTargetRatio: '25',
                children: [],
              },
            ],
          },
        ],
      },
    ])

    const wrapper = mount(CostSubjectPage)
    await flushPromises()
    expect(wrapper.text()).not.toContain('项目目标成本默认比例')
    expect(wrapper.text()).not.toContain('保存10类比例')
    expect(wrapper.text()).not.toContain(
      '固定十类，系统维护；仅允许编辑名称和排序，不支持新增、停用或删除',
    )
    expect(wrapper.findAll('button').some((button) => button.text() === '新增子科目')).toBe(false)
    const editButton = wrapper.findAll('button').find((button) => button.text() === '编辑')!
    expect(editButton.exists()).toBe(true)
    expect(wrapper.findAll('button').some((button) => button.text() === '停用')).toBe(false)
    expect(wrapper.findAll('button').some((button) => button.text() === '删除')).toBe(false)
    expect(
      wrapper
        .findAllComponents(V2StatusToggle)
        .filter((toggle) => toggle.props('ariaLabel')?.includes('人工成本'))
        .every((toggle) => toggle.props('disabled')),
    ).toBe(true)
    await editButton.trigger('click')
    const fields = wrapper.findAllComponents(V2Input)
    expect(fields.find((field) => field.props('label') === '科目编码')?.props('disabled')).toBe(
      true,
    )
    expect(
      fields.find((field) => field.props('label') === '科目名称')?.props('disabled'),
    ).toBeFalsy()
    expect(fields.find((field) => field.props('label') === '科目类型')?.props('disabled')).toBe(
      true,
    )
    expect(fields.find((field) => field.props('label') === '父科目标识')?.props('disabled')).toBe(
      true,
    )
    expect(fields.find((field) => field.props('label') === '排序')?.props('disabled')).toBeFalsy()
    expect(costSubject.updateCostSubject).not.toHaveBeenCalled()
    wrapper.unmount()
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

  it('confirms ordinary cost-subject status through the dedicated API and rereads facts', async () => {
    route.path = '/cost/subject/taxonomy'
    useSessionStore().replaceUserInfo(user(['cost:query', 'cost:edit']))
    const wrapper = mount(CostSubjectPage, { attachTo: document.body })
    await flushPromises()

    await wrapper.find('[aria-label="停用成本科目 投标费用"]').trigger('click')
    expect(costSubject.toggleCostSubjectStatus).not.toHaveBeenCalled()
    const confirm = Array.from(document.body.querySelectorAll<HTMLButtonElement>('button')).find(
      (button) => button.textContent?.trim() === '确认',
    )!
    confirm.click()
    await flushPromises()

    expect(costSubject.toggleCostSubjectStatus).toHaveBeenCalledWith('111')
    expect(costSubject.loadCostSubject).toHaveBeenCalledWith('111')
    expect(costSubject.loadCostSubjectTree).toHaveBeenCalledTimes(2)
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

  it('formats server amounts to two decimals and hides transfer actions without write permission', async () => {
    route.path = '/cost/subject/trace'
    useSessionStore().replaceUserInfo(user(['cost:subject:audit:query']))
    const wrapper = mount(CostSubjectPage)
    await flushPromises()

    expect(wrapper.text()).toContain('¥125.23')
    expect(wrapper.text()).toContain('服务端投标项目')
    expect(wrapper.findAll('button').map((button) => button.text())).not.toEqual(
      expect.arrayContaining(['投标成本转入', '财务费用分摊', '冲销']),
    )
    expect(costSubject.loadBidTransfers).toHaveBeenCalledOnce()
    expect(costSubject.loadFinanceAllocations).toHaveBeenCalledOnce()
    expect(costSubject.loadCostSubjectTree).not.toHaveBeenCalled()
  })
})
