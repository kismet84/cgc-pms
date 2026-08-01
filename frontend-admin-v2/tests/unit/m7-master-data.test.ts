import type { UserInfo } from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { V2Input, V2Select } from '@/components'
import MaterialDictionaryPage from '@/pages/master-data/MaterialDictionaryPage.vue'
import OrganizationPage from '@/pages/master-data/OrganizationPage.vue'
import PartnerDetailPage from '@/pages/master-data/PartnerDetailPage.vue'
import PartnerPage from '@/pages/master-data/PartnerPage.vue'
import * as masterData from '@/services/master-data'
import { useSessionStore } from '@/stores/session'

const routerPush = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '101' } }),
  useRouter: () => ({ push: routerPush }),
}))

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
}))

function user(permissions: string[], roles = ['USER']): UserInfo {
  return { userId: '7', username: 'master.user', roles, permissions }
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
    records: [{ id: '1', companyCode: 'C1', companyName: '一公司', status: 'ENABLE' }],
    total: 1,
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
  ])
  vi.mocked(masterData.loadPositions).mockResolvedValue({
    records: [
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
    pageNo: 1,
    pageSize: 200,
  })
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
    status: 'ENABLE',
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

  it('navigates from the record code to the partner detail page', async () => {
    useSessionStore().replaceUserInfo(user(['partner:query']))
    const wrapper = mount(PartnerPage, { attachTo: document.body })
    await flushPromises()

    const recordLink = wrapper.findAll('button').find((button) => button.text() === 'PTN-101')!
    expect(recordLink.classes()).toContain('v2-table__record-link')
    await recordLink.trigger('click')
    await flushPromises()

    expect(routerPush).toHaveBeenCalledWith({
      name: 'V2ShellPartnerDetail',
      params: { id: '101' },
    })
    expect(masterData.loadPartner).not.toHaveBeenCalled()
  })

  it('loads server facts on the partner detail page', async () => {
    const wrapper = mount(PartnerDetailPage)
    await flushPromises()

    expect(masterData.loadPartner).toHaveBeenCalledWith('101')
    expect(wrapper.text()).toContain('合作方详情')
    expect(wrapper.text()).toContain('PTN-101')
    expect(wrapper.text()).toContain('13800000000')
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
    expect(masterData.loadPartner).toHaveBeenCalledWith('101')
    expect(masterData.loadPartners).toHaveBeenCalledTimes(2)
  })

  it('shows backend-authorized master-data writes to administrator roles', async () => {
    useSessionStore().replaceUserInfo(user([], ['ADMIN']))

    const partner = mount(PartnerPage)
    await flushPromises()
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

  it('loads company, department and position facts together and hides writes without permissions', async () => {
    useSessionStore().replaceUserInfo(user(['org:list']))
    const wrapper = mount(OrganizationPage)
    await flushPromises()

    expect(masterData.loadCompanies).toHaveBeenCalledOnce()
    expect(masterData.loadDepartmentTree).toHaveBeenCalledOnce()
    expect(masterData.loadPositions).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('一公司')
    expect(wrapper.text()).toContain('工程部')
    expect(wrapper.text()).toContain('经理')
    expect(wrapper.text()).not.toContain('新增公司')
    expect(wrapper.text()).not.toContain('删除')
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
    expect(wrapper.text()).not.toMatch(/单位分布|启用材料|已维护税率/)
    expect(wrapper.text()).not.toContain('新增材料')
  })
})
