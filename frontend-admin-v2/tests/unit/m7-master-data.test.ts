import type { UserInfo } from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { V2Input, V2Select, V2StatusToggle } from '@/components'
import MaterialDictionaryPage from '@/pages/master-data/MaterialDictionaryPage.vue'
import OrganizationPage from '@/pages/master-data/OrganizationPage.vue'
import PartnerPage from '@/pages/master-data/PartnerPage.vue'
import * as masterData from '@/services/master-data'
import { useSessionStore } from '@/stores/session'

vi.mock('@/services/master-data', () => ({
  loadPartnerTypes: vi.fn(),
  loadPartners: vi.fn(),
  loadPartner: vi.fn(),
  createPartner: vi.fn(),
  updatePartner: vi.fn(),
  deletePartner: vi.fn(),
  loadCompanies: vi.fn(),
  loadDepartmentTree: vi.fn(),
  loadPositions: vi.fn(),
  saveCompany: vi.fn(),
  deleteCompany: vi.fn(),
  saveDepartment: vi.fn(),
  deleteDepartment: vi.fn(),
  savePosition: vi.fn(),
  deletePosition: vi.fn(),
  loadMaterials: vi.fn(),
  loadMaterial: vi.fn(),
  loadMaterialCategories: vi.fn(),
  createMaterial: vi.fn(),
  updateMaterial: vi.fn(),
  updateMaterialStatus: vi.fn(),
  downloadMaterialImportTemplate: vi.fn(),
  importMaterials: vi.fn(),
}))

function user(permissions: string[], roles = ['USER']): UserInfo {
  return { userId: '7', username: 'master.user', roles, permissions }
}

function confirmOpenDialog(): void {
  const confirm = Array.from(document.body.querySelectorAll<HTMLButtonElement>('button')).find(
    (button) => button.textContent?.trim() === '确认',
  )
  if (!confirm) throw new Error('确认按钮不存在')
  confirm.click()
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  vi.mocked(masterData.loadPartnerTypes).mockResolvedValue([
    { dictLabel: '供应商', dictValue: 'SUPPLIER', status: 'ENABLE' },
  ])
  vi.mocked(masterData.loadPartners).mockResolvedValue({
    records: [
      {
        id: '101',
        partnerCode: 'PTN-101',
        partnerName: '服务端合作方',
        partnerType: 'SUPPLIER',
        contactName: '联系人',
        status: 'ENABLE',
      },
    ],
    total: 1,
    pageNo: 1,
    pageSize: 10,
  })
  vi.mocked(masterData.loadPartner).mockResolvedValue({
    id: '101',
    partnerCode: 'PTN-101',
    partnerName: '服务端合作方',
    partnerType: 'SUPPLIER',
    contactName: '联系人',
    contactPhone: '13800000000',
    bankAccount: '6222000000000000',
    status: 'ENABLE',
  })
  vi.mocked(masterData.loadCompanies).mockResolvedValue({
    records: [
      { id: '1', companyCode: 'C1', companyName: '一公司', status: 'ENABLE' },
      { id: '10', companyCode: 'C2', companyName: '二公司', status: 'ENABLE' },
    ],
    total: 2,
    pageNo: 1,
    pageSize: 200,
  })
  vi.mocked(masterData.loadDepartmentTree).mockResolvedValue([
    {
      id: '2',
      companyId: '1',
      parentId: '0',
      deptCode: 'D1',
      deptName: '工程部',
      orderNum: 0,
      status: 'ENABLE',
      children: [],
    },
    {
      id: '4',
      companyId: '10',
      parentId: '0',
      deptCode: 'D2',
      deptName: '财务部',
      orderNum: 0,
      status: 'ENABLE',
      children: [],
    },
  ])
  vi.mocked(masterData.loadPositions).mockImplementation(async (query) => ({
    records:
      query.departmentId === '4'
        ? [
            {
              id: '5',
              companyId: '10',
              departmentId: '4',
              positionCode: 'P2',
              positionName: '会计',
              status: 'ENABLE',
            },
          ]
        : [
            {
              id: '3',
              companyId: '1',
              departmentId: '2',
              positionCode: 'P1',
              positionName: '经理',
              status: 'ENABLE',
            },
          ],
    total: 1,
    pageNo: query.pageNo,
    pageSize: query.pageSize,
  }))
  vi.mocked(masterData.loadMaterialCategories).mockResolvedValue([
    { id: '9', categoryCode: 'STEEL', categoryName: '钢材', status: 'ENABLE' },
  ])
  vi.mocked(masterData.loadMaterials).mockResolvedValue({
    records: [
      {
        id: '8',
        materialCode: 'MAT-8',
        materialName: '钢筋',
        categoryId: '9',
        defaultTaxRate: '13.00',
        taxInclusiveInfoPrice: '43.000000',
        purchasePrice: '41.5000',
        status: 'ENABLE',
      },
    ],
    total: 1,
    pageNo: 1,
    pageSize: 10,
  })
  vi.mocked(masterData.loadMaterial).mockResolvedValue({
    id: '8',
    materialCode: 'MAT-8',
    materialName: '钢筋',
    categoryId: '9',
    defaultTaxRate: '13.00',
    taxInclusiveInfoPrice: '43.000000',
    infoPricePeriod: '2026-07',
    infoPriceSource: '武汉市建设工程综合价格信息',
    infoPriceVerificationStatus: '已人工校正',
    infoPriceReviewRequired: 1,
    purchasePrice: '41.5000',
    purchasePriceReceiptItemId: '9001',
    purchasePriceDate: '2026-07-20',
    status: 'ENABLE',
  })
  vi.mocked(masterData.downloadMaterialImportTemplate).mockResolvedValue(new Blob(['xlsx']))
  vi.mocked(masterData.importMaterials).mockResolvedValue({
    total: 3,
    created: 1,
    priceUpdated: 1,
    conflictsCreated: 0,
    skipped: 0,
    failed: 1,
    errors: [{ row: 4, code: 'ROW_INVALID', message: '价格无效' }],
  })
})

afterEach(() => {
  document.body.innerHTML = ''
})

describe('M7 master-data pages', () => {
  it('keeps partner list free of phone and bank account, loading them only for edit', async () => {
    useSessionStore().replaceUserInfo(user(['partner:query', 'partner:edit']))
    const wrapper = mount(PartnerPage, { attachTo: document.body })
    await flushPromises()

    expect(wrapper.text()).toContain('服务端合作方')
    expect(wrapper.find('.v2-card--page-heading .v2-page-heading__filters').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('查询条件')
    expect(wrapper.text()).not.toContain('13800000000')
    expect(wrapper.text()).not.toContain('6222000000000000')
    expect(masterData.loadPartner).not.toHaveBeenCalled()

    const editButton = wrapper.findAll('button').find((button) => button.text() === '编辑')
    expect(editButton).toBeDefined()
    await editButton!.trigger('click')
    await flushPromises()

    expect(masterData.loadPartner).toHaveBeenCalledWith('101')
    expect(document.body.textContent).toContain('联系电话')
  })

  it('opens server facts in a partner detail dialog', async () => {
    useSessionStore().replaceUserInfo(user(['partner:query']))
    const wrapper = mount(PartnerPage, { attachTo: document.body })
    await flushPromises()

    const recordLink = wrapper.findAll('button').find((button) => button.text() === 'PTN-101')!
    expect(recordLink.classes()).toContain('v2-table__record-link')
    await recordLink.trigger('click')
    await flushPromises()

    expect(masterData.loadPartner).toHaveBeenCalledWith('101')
    const dialog = document.body.querySelector('[role="dialog"]')!
    expect(dialog.textContent).toContain('合作方详情')
    expect(dialog.textContent).toContain('PTN-101')
    expect(dialog.textContent).toContain('13800000000')
  })

  it('normalizes a numeric partner id before the required post-create read', async () => {
    useSessionStore().replaceUserInfo(user(['partner:query', 'partner:add']))
    vi.mocked(masterData.createPartner).mockResolvedValue(101)
    const wrapper = mount(PartnerPage, { attachTo: document.body })
    await flushPromises()

    const addButton = wrapper.findAll('button').find((button) => button.text() === '新增合作方')
    await addButton!.trigger('click')
    await wrapper
      .findAllComponents(V2Input)
      .find((input) => input.props('label') === '合作方名称' && input.props('required'))!
      .find('input')
      .setValue('新合作方')
    await wrapper
      .findAllComponents(V2Select)
      .find((select) => select.props('label') === '合作方类型' && select.props('required'))!
      .find('select')
      .setValue('SUPPLIER')
    document
      .querySelector('#partner-form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flushPromises()

    expect(masterData.createPartner).toHaveBeenCalledOnce()
    expect(vi.mocked(masterData.createPartner).mock.calls[0]![0]).not.toHaveProperty('partnerCode')
    expect(masterData.loadPartner).toHaveBeenCalledWith('101')
    expect(masterData.loadPartners).toHaveBeenCalledTimes(2)
  })

  it('keeps the server-generated partner code read-only', async () => {
    useSessionStore().replaceUserInfo(user(['partner:query', 'partner:add']))
    const wrapper = mount(PartnerPage, { attachTo: document.body })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '新增合作方')!
      .trigger('click')
    const code = wrapper
      .findAllComponents(V2Input)
      .find((input) => input.props('label') === '合作方编号' && input.props('disabled'))!

    expect(code.props('disabled')).toBe(true)
    expect(code.props('hint')).toBeUndefined()
  })

  it('shows backend-authorized master-data writes to administrator roles', async () => {
    useSessionStore().replaceUserInfo(user([], ['ADMIN']))

    const partner = mount(PartnerPage)
    await flushPromises()
    expect(partner.get('h1').text()).toBe('客户管理')
    expect(partner.text()).toContain('新增合作方')
    partner.unmount()

    const organization = mount(OrganizationPage)
    await flushPromises()
    expect(organization.text()).toContain('新增公司')
    organization.unmount()

    const material = mount(MaterialDictionaryPage)
    await flushPromises()
    expect(material.text()).toContain('新增材料')
  })

  it('confirms partner status, sends the complete server detail and rereads it', async () => {
    useSessionStore().replaceUserInfo(user(['partner:query', 'partner:edit']))
    const wrapper = mount(PartnerPage, { attachTo: document.body })
    await flushPromises()

    await wrapper.findComponent('[aria-label="停用合作方 服务端合作方"]').trigger('click')
    expect(masterData.updatePartner).not.toHaveBeenCalled()
    confirmOpenDialog()
    await flushPromises()

    expect(masterData.updatePartner).toHaveBeenCalledWith(
      '101',
      expect.objectContaining({
        partnerCode: 'PTN-101',
        partnerName: '服务端合作方',
        partnerType: 'SUPPLIER',
        contactPhone: '13800000000',
        bankAccount: '6222000000000000',
        defaultLeadDays: null,
        status: 'DISABLE',
      }),
    )
    expect(masterData.loadPartner).toHaveBeenCalledTimes(2)
    expect(masterData.loadPartners).toHaveBeenCalledTimes(2)
  })

  it('loads company, department and position facts together and hides writes without permissions', async () => {
    useSessionStore().replaceUserInfo(user(['org:list']))
    const wrapper = mount(OrganizationPage)
    await flushPromises()

    expect(masterData.loadCompanies).toHaveBeenCalledOnce()
    expect(masterData.loadDepartmentTree).toHaveBeenCalledOnce()
    expect(masterData.loadPositions).toHaveBeenCalledWith(
      expect.objectContaining({ companyId: '1', departmentId: '2' }),
      expect.any(AbortSignal),
    )
    expect(wrapper.findAll('.v2-card')).toHaveLength(2)
    expect(wrapper.findAll('.org-page__columns > section')).toHaveLength(3)
    expect(wrapper.get('#org-companies-title').text()).toBe('1. 公司')
    expect(wrapper.get('#org-departments-title').text()).toBe('2. 部门')
    expect(wrapper.get('#org-positions-title').text()).toBe('3. 岗位')
    expect(wrapper.text()).toContain('一公司')
    expect(wrapper.text()).toContain('工程部')
    expect(wrapper.text()).toContain('经理')
    expect(wrapper.text()).not.toContain('财务部')
    expect(wrapper.text()).not.toContain('新增公司')
    expect(wrapper.text()).not.toContain('删除')

    const secondCompany = wrapper
      .findAll('.org-page__select')
      .find((button) => button.text().includes('二公司'))!
    await secondCompany.trigger('click')
    await flushPromises()

    expect(secondCompany.attributes('aria-pressed')).toBe('true')
    expect(wrapper.text()).toContain('财务部')
    expect(wrapper.text()).toContain('会计')
    expect(wrapper.text()).not.toContain('工程部')
    expect(masterData.loadPositions).toHaveBeenLastCalledWith(
      expect.objectContaining({ companyId: '10', departmentId: '4' }),
      undefined,
    )
    expect(
      wrapper.findAllComponents(V2StatusToggle).every((toggle) => toggle.props('disabled')),
    ).toBe(true)
  })

  it('confirms and rereads company, department and position status updates', async () => {
    useSessionStore().replaceUserInfo(user(['org:list', 'org:edit']))
    const wrapper = mount(OrganizationPage, { attachTo: document.body })
    await flushPromises()

    for (const [label, save] of [
      ['停用公司 一公司', masterData.saveCompany],
      ['停用部门 工程部', masterData.saveDepartment],
      ['停用岗位 经理', masterData.savePosition],
    ] as const) {
      await wrapper.find(`[aria-label="${label}"]`).trigger('click')
      expect(save).not.toHaveBeenCalled()
      confirmOpenDialog()
      await flushPromises()
      expect(save).toHaveBeenCalledOnce()
    }

    expect(masterData.saveCompany).toHaveBeenCalledWith(
      '1',
      expect.objectContaining({ companyCode: 'C1', companyName: '一公司', status: 'DISABLE' }),
    )
    expect(masterData.saveDepartment).toHaveBeenCalledWith(
      '2',
      expect.objectContaining({
        companyId: '1',
        parentId: '0',
        deptCode: 'D1',
        deptName: '工程部',
        orderNum: 0,
        status: 'DISABLE',
      }),
    )
    expect(masterData.savePosition).toHaveBeenCalledWith(
      '3',
      expect.objectContaining({
        companyId: '1',
        departmentId: '2',
        positionCode: 'P1',
        positionName: '经理',
        status: 'DISABLE',
      }),
    )
    expect(masterData.loadCompanies).toHaveBeenCalledTimes(4)
    expect(masterData.loadDepartmentTree).toHaveBeenCalledTimes(4)
  })

  it('uses server material totals and strings, without local KPI calculations', async () => {
    useSessionStore().replaceUserInfo(user(['material:dict:list']))
    const wrapper = mount(MaterialDictionaryPage)
    await flushPromises()

    expect(masterData.loadMaterials).toHaveBeenCalledWith(
      expect.objectContaining({ pageNo: 1, pageSize: 10 }),
      expect.any(AbortSignal),
    )
    expect(wrapper.find('.v2-card--page-heading .v2-page-heading__filters').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('查询条件')
    expect(wrapper.text()).toContain('共 1 项')
    expect(wrapper.text()).toContain('13.00')
    expect(wrapper.text()).toContain('¥43.00')
    expect(wrapper.text()).toContain('¥41.50')
    expect(wrapper.text()).toContain('导出模板')
    expect(wrapper.text()).not.toContain('导入资料')
    expect(wrapper.text()).not.toMatch(/单位分布|启用材料|已维护税率/)
    expect(wrapper.text()).not.toContain('新增材料')
  })

  it('confirms material status through the dedicated API and rereads server facts', async () => {
    useSessionStore().replaceUserInfo(user(['material:dict:list', 'material:dict:edit']))
    const wrapper = mount(MaterialDictionaryPage, { attachTo: document.body })
    await flushPromises()

    await wrapper.find('[aria-label="停用材料 钢筋"]').trigger('click')
    expect(masterData.updateMaterialStatus).not.toHaveBeenCalled()
    confirmOpenDialog()
    await flushPromises()

    expect(masterData.updateMaterialStatus).toHaveBeenCalledWith('8', 'DISABLE')
    expect(masterData.loadMaterial).toHaveBeenCalledWith('8')
    expect(masterData.loadMaterials).toHaveBeenCalledTimes(2)
  })

  it('imports a standard workbook with FormData service and shows all row errors', async () => {
    useSessionStore().replaceUserInfo(
      user(['material:dict:list', 'material:dict:add', 'material:dict:edit']),
    )
    const wrapper = mount(MaterialDictionaryPage, { attachTo: document.body })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((item) => item.text() === '导入资料')!
      .trigger('click')
    const input = document.body.querySelector('input[type="file"]') as HTMLInputElement
    const file = new File(['xlsx'], 'materials.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    Object.defineProperty(input, 'files', { configurable: true, value: [file] })
    input.dispatchEvent(new Event('change', { bubbles: true }))
    await flushPromises()
    const start = Array.from(document.body.querySelectorAll('button')).find(
      (item) => item.textContent === '开始导入',
    )!
    start.click()
    await flushPromises()

    expect(masterData.importMaterials).toHaveBeenCalledWith(file)
    expect(masterData.loadMaterials).toHaveBeenCalledTimes(2)
    expect(document.body.textContent).toContain('失败 1')
    expect(document.body.textContent).toContain('ROW_INVALID')
    expect(document.body.textContent).toContain('价格无效')
  })
})
