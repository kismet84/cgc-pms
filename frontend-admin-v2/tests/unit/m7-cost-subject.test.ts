import type { UserInfo } from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Component } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { V2Input, V2Select, V2StatusToggle } from '@/components'
import CostSubjectRulesPage from '@/pages/master-data/cost-subject/CostSubjectRulesPage.vue'
import CostSubjectScopePage from '@/pages/master-data/cost-subject/CostSubjectScopePage.vue'
import CostSubjectTaxonomyPage from '@/pages/master-data/cost-subject/CostSubjectTaxonomyPage.vue'
import CostSubjectTracePage from '@/pages/master-data/cost-subject/CostSubjectTracePage.vue'
import * as costSubject from '@/services/cost-subject'
import { useSessionStore } from '@/stores/session'

vi.mock('@/services/cost-subject', () => ({
  cancelBidTransferRequest: vi.fn(),
  cancelFinanceAllocationRequest: vi.fn(),
  cancelProjectConfigRequest: vi.fn(),
  activateMappingVersion: vi.fn(),
  cancelRecalculationBatch: vi.fn(),
  createAssignmentRule: vi.fn(),
  createBidTransferRequest: vi.fn(),
  createCostSubject: vi.fn(),
  createFinanceAllocationRequest: vi.fn(),
  createMappingVersion: vi.fn(),
  createOverheadAllocationRule: vi.fn(),
  createProjectConfigRequest: vi.fn(),
  createRecalculationBatch: vi.fn(),
  createReversalRequest: vi.fn(),
  deleteCostSubject: vi.fn(),
  diffRulePlan: vi.fn(),
  generateInitialRulePlan: vi.fn(),
  executeOverheadAllocation: vi.fn(),
  loadAssignmentRules: vi.fn(),
  loadAccountingCatalogOverview: vi.fn(),
  loadBidTransfers: vi.fn(),
  loadBidTransferRequests: vi.fn(),
  loadCostSubject: vi.fn(),
  loadCostSubjectReconciliation: vi.fn(),
  loadCostSubjectTree: vi.fn(),
  loadFinanceAllocations: vi.fn(),
  loadFinanceAllocationRequests: vi.fn(),
  loadGovernanceFormOptions: vi.fn(),
  loadMappingVersions: vi.fn(),
  loadOverheadAllocationRules: vi.fn(),
  loadProjectConfiguration: vi.fn(),
  loadProjectScopes: vi.fn(),
  loadRecalculationBatches: vi.fn(),
  loadReversalRequests: vi.fn(),
  loadSubjectImpact: vi.fn(),
  overrideClassification: vi.fn(),
  reverseBidTransfer: vi.fn(),
  reverseFinanceAllocation: vi.fn(),
  reviewAccountingLegacySubject: vi.fn(),
  saveProjectScope: vi.fn(),
  submitBidTransferRequest: vi.fn(),
  submitFinanceAllocationRequest: vi.fn(),
  submitProjectConfigRequest: vi.fn(),
  submitRecalculationBatch: vi.fn(),
  submitReversalRequest: vi.fn(),
  submitRulePlan: vi.fn(),
  setOverheadAllocationRuleStatus: vi.fn(),
  toggleCostSubjectStatus: vi.fn(),
  updateCostSubject: vi.fn(),
  updateOverheadAllocationRule: vi.fn(),
  trialRulePlan: vi.fn(),
  validateRulePlan: vi.fn(),
}))

function user(permissions: string[]): UserInfo {
  return { tenantId: '1001', userId: '7', username: 'cost.user', roles: ['USER'], permissions }
}

async function mountRouted(component: Component) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component }],
  })
  await router.push('/')
  await router.isReady()
  return mount(component, { global: { plugins: [router] } })
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
    {
      id: '2',
      parentId: '0',
      subjectCode: '1122-AR',
      subjectName: '应收账款',
      subjectType: 'GENERAL_LEDGER',
      accountCategory: 'ASSET',
      level: 1,
      sortOrder: 20,
      status: 'ENABLE',
      children: [],
    },
  ])
  vi.mocked(costSubject.loadAccountingCatalogOverview).mockResolvedValue({
    policies: [
      {
        subjectCode: '1122',
        subjectName: '应收账款',
        projectRequirement: 'REQUIRED',
        contractRequirement: 'REQUIRED',
        partnerRequirement: 'REQUIRED',
        departmentRequirement: 'NONE',
        employeeRequirement: 'NONE',
      },
    ],
    carryoverMappings: [
      {
        categoryCode: 'MATERIAL',
        categoryName: '材料',
        fulfillmentCode: '1451.01',
        fulfillmentName: '材料费',
        expenseCode: '6401.01',
        expenseName: '材料成本',
        status: 'ENABLE',
      },
    ],
    legacyReviews: [
      {
        sourceSubjectCode: '1122-AR',
        sourceSubjectName: '应收账款（历史别名）',
        suggestedSubjectCode: '1122',
        reviewStatus: 'PENDING',
        reviewNote: '只读保留，不直接改写历史凭证',
      },
    ],
    reportRoutes: [{ label: '科目余额表', path: '/financial-close' }],
  })
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
      projectId: 'P-1',
      projectCode: 'XM-001',
      projectName: '服务端项目',
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
  vi.mocked(costSubject.loadBidTransferRequests).mockResolvedValue([
    {
      id: '41',
      requestCode: 'BTR-001',
      bidCostId: 'BID-1',
      bidCode: 'TB-001',
      projectId: 'P-1',
      projectCode: 'XM-001',
      projectName: '服务端项目',
      targetId: 'TARGET-1',
      targetVersionNo: 'V1',
      targetVersionName: '首版目标成本',
      mappingVersionId: 'MAP-1',
      totalAmount: '81.2300',
      status: 'DRAFT',
    },
  ])
  vi.mocked(costSubject.loadFinanceAllocations).mockResolvedValue([])
  vi.mocked(costSubject.loadRecalculationBatches).mockResolvedValue([])
  vi.mocked(costSubject.loadReversalRequests).mockResolvedValue([])
  vi.mocked(costSubject.loadGovernanceFormOptions).mockResolvedValue({
    projects: [
      {
        id: 'P-1',
        projectCode: 'XM-001',
        projectName: '服务端项目',
        projectStatus: 'ACTIVE',
      },
    ],
    costSubjects: [
      {
        id: '111',
        subjectCode: '5401.04.19',
        subjectName: '财务费用',
        subjectType: 'FINANCE',
        status: 'ENABLE',
      },
      {
        id: '112',
        subjectCode: '5401.04.20',
        subjectName: '项目间接费',
        subjectType: 'OVERHEAD',
        status: 'ENABLE',
        overheadRuleStatus: 'DISABLE',
      },
    ],
    rulePlans: [],
    bidCosts: [],
    targetVersions: [],
    financeSources: [],
    pendingClassifications: [],
  })
  vi.mocked(costSubject.loadProjectConfiguration).mockResolvedValue({
    project: {
      id: 'P-1',
      projectCode: 'XM-001',
      projectName: '服务端项目',
      projectStatus: 'ACTIVE',
      mainContractCode: 'HT-001',
      mainContractName: '主合同',
      targetVersionNo: 'V1',
      targetVersionName: '首版目标成本',
      targetAmount: '1000',
    },
    subjects: [],
    requests: [],
  })
  vi.mocked(costSubject.loadFinanceAllocationRequests).mockResolvedValue([
    {
      id: '51',
      requestCode: 'FAR-001',
      projectId: 'P-1',
      projectCode: 'XM-001',
      projectName: '服务端项目',
      sourceType: 'ACCOUNTING_ENTRY_LINE',
      sourceId: 'V-1',
      sourceCode: 'PZ-001',
      sourceAmount: '10.5',
      allocationBasis: 'BENEFIT_AMOUNT',
      accountingPeriod: '2026-08',
      costSubjectId: '111',
      costSubjectCode: '5401.04.19',
      costSubjectName: '财务费用',
      status: 'SUBMITTED',
      approvalInstanceId: 'WF-51',
    },
  ])
  vi.mocked(costSubject.loadOverheadAllocationRules).mockResolvedValue({
    records: [
      {
        id: '801',
        costSubjectId: '112',
        allocationBasis: 'DIRECT_LABOR',
        allocationCycle: 'PER_OCCURRENCE',
        status: 'ENABLE',
      },
    ],
    total: 1,
    pageNo: 1,
    pageSize: 100,
  })
  vi.mocked(costSubject.createOverheadAllocationRule).mockResolvedValue('802')
  vi.mocked(costSubject.updateOverheadAllocationRule).mockResolvedValue(undefined)
  vi.mocked(costSubject.executeOverheadAllocation).mockResolvedValue({
    period: '2026-08-31',
    ruleCount: 1,
    createdRunCount: 1,
    duplicateRunCount: 0,
    costItemCount: 2,
    allocatedAmount: '100.00',
    idempotent: false,
  })
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
    useSessionStore().replaceUserInfo(user(['cost:query']))
    const wrapper = mount(CostSubjectTaxonomyPage)
    await flushPromises()

    expect(wrapper.findAll('.v2-card')).toHaveLength(6)
    expect(wrapper.findAll('.cost-subject-page__taxonomy > section')).toHaveLength(3)
    expect(wrapper.get('#account-category-title').text()).toBe('1. 科目大类')
    expect(wrapper.get('#account-subject-catalog-title').text()).toBe('2. 科目目录')
    expect(wrapper.get('#account-subject-detail-title').text()).toBe('3. 科目详情')
    expect(wrapper.get('h1').text()).toBe('会计科目')
    expect(wrapper.text()).toContain('招投标及前期费用')
    expect(wrapper.text()).toContain('投标费用')
    expect(wrapper.text()).toContain('下级测试科目')
    expect(wrapper.text()).toContain('1122-AR')
    expect(wrapper.text()).toContain('应收账款')
    expect(wrapper.text()).not.toContain('请选择科目')
    expect(wrapper.text()).not.toContain('ROOT')
    expect(wrapper.text()).not.toContain('新增一级科目')
    expect(costSubject.loadCostSubjectTree).toHaveBeenCalledOnce()
    expect(costSubject.loadAccountingCatalogOverview).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('项目、合同、往来单位、部门、员工使用辅助核算维度')
    expect(wrapper.text()).toContain('1451.01')
    expect(wrapper.text()).toContain('6401.01')
    expect(wrapper.text()).toContain('只读保留，不直接改写历史凭证')
    expect(wrapper.text()).toContain('科目余额表')
    expect(costSubject.loadMappingVersions).not.toHaveBeenCalled()
    expect(costSubject.loadBidTransfers).not.toHaveBeenCalled()
  })

  it('lets authorized finance users confirm a pending historical subject mapping', async () => {
    useSessionStore().replaceUserInfo(user(['cost:query', 'accounting:subject-review']))
    vi.mocked(costSubject.reviewAccountingLegacySubject).mockResolvedValue(undefined)
    const wrapper = mount(CostSubjectTaxonomyPage, { attachTo: document.body })
    await flushPromises()

    const open = wrapper.findAll('button').find((button) => button.text().includes('确认映射'))
    expect(open).toBeTruthy()
    await open?.trigger('click')
    await flushPromises()
    const confirm = Array.from(
      document.body.querySelectorAll<HTMLButtonElement>('button'),
    ).findLast((button) => button.textContent?.trim() === '确认映射')!
    confirm.click()
    await flushPromises()

    expect(costSubject.reviewAccountingLegacySubject).toHaveBeenCalledWith('1122-AR', 'CONFIRMED')
  })

  it('keeps the fixed accounting structure read-only for administrators', async () => {
    useSessionStore().replaceUserInfo({
      ...user([]),
      roles: ['SUPER_ADMIN'],
    })
    const wrapper = mount(CostSubjectTaxonomyPage)
    await flushPromises()

    expect(wrapper.text()).not.toContain('新增一级科目')
    expect(wrapper.text()).not.toContain('新增子科目')
    expect(wrapper.text()).not.toContain('删除')
  })

  it('keeps fixed target-cost subjects read-only and removes the default-ratio card', async () => {
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

    const wrapper = mount(CostSubjectTaxonomyPage)
    await flushPromises()
    expect(wrapper.text()).not.toContain('项目目标成本默认比例')
    expect(wrapper.text()).not.toContain('保存10类比例')
    expect(wrapper.text()).not.toContain(
      '固定十类，系统维护；仅允许编辑名称和排序，不支持新增、停用或删除',
    )
    const governed = wrapper
      .findAll('.cost-subject-page__list-item')
      .find((item) => item.text().includes('5401.03.01'))!
    await governed.get('button.cost-subject-page__select').trigger('click')
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

  it('links catalog selection to the subject detail', async () => {
    useSessionStore().replaceUserInfo(user(['cost:query']))
    const wrapper = mount(CostSubjectTaxonomyPage)
    await flushPromises()

    expect(wrapper.text()).toContain('5401.01.01')
    const purchase = wrapper
      .findAll('.cost-subject-page__list-item')
      .find((item) => item.text().includes('5401.02'))!
    await purchase.get('button.cost-subject-page__select').trigger('click')
    expect(purchase.classes()).toContain('is-selected')
    expect(wrapper.get('.cost-subject-page__facts').text()).toContain('5401.02')
    expect(wrapper.get('.cost-subject-page__facts').text()).toContain('采购阶段成本')
  })

  it('confirms ordinary cost-subject status through the dedicated API and rereads facts', async () => {
    useSessionStore().replaceUserInfo(user(['cost:query', 'cost:edit']))
    const wrapper = mount(CostSubjectTaxonomyPage, { attachTo: document.body })
    await flushPromises()

    await wrapper.find('[aria-label="停用会计科目 投标费用"]').trigger('click')
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
    useSessionStore().replaceUserInfo(
      user(['cost:subject:mapping:query', 'cost:subject:rule:query']),
    )
    const wrapper = mount(CostSubjectRulesPage, { attachTo: document.body })
    await flushPromises()

    expect(wrapper.text()).toContain('服务端映射版本')
    expect(wrapper.text()).toContain('RULE-001')
    expect(wrapper.text()).not.toContain('新建映射版本')
    expect(costSubject.loadMappingVersions).toHaveBeenCalledOnce()
    expect(costSubject.loadAssignmentRules).toHaveBeenCalledOnce()
    expect(costSubject.loadCostSubjectTree).not.toHaveBeenCalled()
    expect(costSubject.loadBidTransfers).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('shows failed validation details beside the selected rule plan', async () => {
    useSessionStore().replaceUserInfo(
      user(['cost:subject:mapping:query', 'cost:subject:rule:query', 'cost:subject:mapping:edit']),
    )
    vi.mocked(costSubject.validateRulePlan).mockResolvedValue({
      passed: false,
      itemCount: 25,
      ruleCount: 9,
      invalidSubjectCount: 0,
      conflicts: [],
      missingSourceTypes: ['VAR_ORDER', 'CT_CHANGE', 'CT_CONTRACT'],
    })
    const wrapper = mount(CostSubjectRulesPage, { attachTo: document.body })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '系统校验')!
      .trigger('click')
    await flushPromises()

    expect(costSubject.validateRulePlan).toHaveBeenCalledWith('2')
    expect(wrapper.text()).toContain('最近校验报告')
    expect(wrapper.text()).toContain('系统校验未通过')
    expect(wrapper.text()).toContain('缺少 3 类来源：VAR_ORDER、CT_CHANGE、CT_CONTRACT')
    expect(wrapper.text().indexOf('最近校验报告')).toBeLessThan(
      wrapper.text().indexOf('方案规则明细'),
    )
    wrapper.unmount()
  })

  it('creates, disables and executes overhead rules without manual identifiers', async () => {
    useSessionStore().replaceUserInfo(
      user([
        'cost:subject:mapping:query',
        'cost:subject:rule:query',
        'overhead:query',
        'overhead:add',
        'overhead:edit',
        'overhead:execute',
      ]),
    )
    const wrapper = mount(CostSubjectRulesPage, { attachTo: document.body })
    await flushPromises()

    expect(wrapper.text()).toContain('项目间接费')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '编辑')!
      .trigger('click')
    document
      .querySelector('#overhead-rule-form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flushPromises()
    expect(costSubject.updateOverheadAllocationRule).toHaveBeenCalledWith('801', {
      costSubjectId: '112',
      allocationBasis: 'DIRECT_LABOR',
      allocationCycle: 'MONTHLY',
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '停用')!
      .trigger('click')
    await flushPromises()
    expect(costSubject.setOverheadAllocationRuleStatus).toHaveBeenCalledWith('801', 'DISABLE')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '新建间接费规则')!
      .trigger('click')
    const overheadSubject = wrapper
      .findAllComponents(V2Select)
      .find((select) => select.props('label') === '间接费科目')!
    overheadSubject.vm.$emit('update:modelValue', '112')
    document
      .querySelector('#overhead-rule-form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flushPromises()
    expect(costSubject.createOverheadAllocationRule).toHaveBeenCalledWith({
      costSubjectId: '112',
      allocationBasis: 'DIRECT_LABOR',
      allocationCycle: 'MONTHLY',
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '按期间执行')!
      .trigger('click')
    document
      .querySelector('#overhead-execute-form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flushPromises()
    const now = new Date()
    const expectedPeriod = new Date(now.getFullYear(), now.getMonth(), 0)
    expect(costSubject.executeOverheadAllocation).toHaveBeenCalledWith(
      `${expectedPeriod.getFullYear()}-${String(expectedPeriod.getMonth() + 1).padStart(2, '0')}-${String(expectedPeriod.getDate()).padStart(2, '0')}`,
    )
    expect(document.body.textContent).toContain('100.00')
    wrapper.unmount()
  })

  it('places project scope controls in the page heading', async () => {
    useSessionStore().replaceUserInfo(user(['cost:subject:scope:query']))
    const wrapper = await mountRouted(CostSubjectScopePage)
    await flushPromises()

    expect(
      wrapper.findAllComponents(V2Select).some((select) => select.props('label') === '项目'),
    ).toBe(true)
    expect(wrapper.text()).toContain('项目成本配置')
  })

  it('locks code, hierarchy and status for formal accounting subjects', async () => {
    useSessionStore().replaceUserInfo(user(['cost:query', 'cost:edit', 'cost:add']))
    vi.mocked(costSubject.loadCostSubjectTree).mockResolvedValue([
      {
        id: '3052',
        parentId: '0',
        subjectCode: '1122',
        subjectName: '应收账款',
        subjectType: 'GENERAL_LEDGER',
        accountCategory: 'ASSET',
        level: 1,
        sortOrder: 1,
        status: 'ENABLE',
        ledgerFlag: 1,
        children: [],
      },
    ])
    const wrapper = mount(CostSubjectTaxonomyPage)
    await flushPromises()

    expect(wrapper.text()).not.toContain('新增一级科目')
    expect(wrapper.text()).not.toContain('新增子科目')
    expect(wrapper.text()).not.toContain('删除')
    expect(wrapper.find('[aria-label="会计科目 应收账款 状态由系统维护"]').exists()).toBe(true)
    expect(costSubject.createCostSubject).not.toHaveBeenCalled()
  })

  it('formats server amounts to two decimals and hides transfer actions without write permission', async () => {
    useSessionStore().replaceUserInfo(user(['cost:subject:audit:query']))
    const wrapper = await mountRouted(CostSubjectTracePage)
    await flushPromises()

    await wrapper.findAll('[role="tab"]')[2]!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('¥125.23')
    expect(wrapper.text()).toContain('¥81.23')
    expect(wrapper.text()).toContain('BTR-001')
    expect(wrapper.text()).toContain('服务端投标项目')
    expect(wrapper.text()).toContain('TB-001')
    expect(wrapper.text()).toContain('XM-001 · 服务端项目')
    expect(wrapper.text()).toContain('V1 · 首版目标成本')
    await wrapper.findAll('[role="tab"]')[3]!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('FAR-001')
    expect(wrapper.text()).toContain('已过账借方凭证明细 · PZ-001')
    expect(wrapper.text()).toContain('5401.04.19 · 财务费用')
    expect(wrapper.findAll('button').map((button) => button.text())).not.toEqual(
      expect.arrayContaining(['新建转入申请', '新建分摊申请', '提交审批', '冲销']),
    )
    expect(costSubject.loadBidTransferRequests).toHaveBeenCalledOnce()
    expect(costSubject.loadFinanceAllocationRequests).toHaveBeenCalledOnce()
    expect(costSubject.loadBidTransfers).toHaveBeenCalledOnce()
    expect(costSubject.loadFinanceAllocations).toHaveBeenCalledOnce()
    expect(costSubject.loadCostSubjectTree).not.toHaveBeenCalled()
  })

  it('creates workflow drafts without approval ids and submits eligible requests separately', async () => {
    useSessionStore().replaceUserInfo(
      user([
        'cost:subject:audit:query',
        'cost:subject:bid-transfer',
        'cost:subject:transfer:submit',
        'cost:subject:finance-allocate',
        'cost:classification:override',
        'cost:subject:allocation:submit',
      ]),
    )
    const wrapper = await mountRouted(CostSubjectTracePage)
    await flushPromises()

    await wrapper.findAll('[role="tab"]')[2]!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('新建转入申请')
    await wrapper.findAll('[role="tab"]')[3]!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('新建分摊申请')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '新建分摊申请')!
      .trigger('click')
    await flushPromises()
    const targetSubjectSelect = wrapper
      .findAllComponents(V2Select)
      .find((select) => select.props('label') === '目标末级成本科目')!
    expect(targetSubjectSelect.props('options')).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ value: '111', disabled: false }),
        expect.objectContaining({
          value: '112',
          disabled: true,
          label: expect.stringContaining('间接费规则已停用'),
        }),
      ]),
    )
    useSessionStore().replaceUserInfo(
      user([
        'cost:subject:audit:query',
        'cost:subject:transfer:submit',
        'cost:subject:allocation:submit',
      ]),
    )
    await flushPromises()
    await wrapper.findAll('[role="tab"]')[2]!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('新建转入申请')
    const submitButtons = wrapper.findAll('button').filter((button) => button.text() === '提交审批')
    expect(submitButtons).toHaveLength(1)
    await submitButtons[0]!.trigger('click')
    await flushPromises()
    expect(costSubject.submitBidTransferRequest).toHaveBeenCalledWith('41')

    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/master-data/cost-subject/CostSubjectTracePage.vue'),
      'utf8',
    )
    const transferForm = source.match(/<form id="transfer-form"[\s\S]*?<\/form>/)?.[0] ?? ''
    const allocationForm = source.match(/<form id="allocation-form"[\s\S]*?<\/form>/)?.[0] ?? ''
    expect(transferForm).not.toContain('approvalInstanceId')
    expect(allocationForm).not.toContain('approvalInstanceId')
    expect(source).not.toContain('reverseForm.approvalInstanceId')
  })
})
