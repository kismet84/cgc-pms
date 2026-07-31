import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import OrgPage from '../index.vue'

// ── Mock stores ──
const mockUserStore = {
  hasPermission: vi.fn().mockReturnValue(true),
}
vi.mock('@/stores/user', () => ({
  useUserStore: () => mockUserStore,
}))

// ── Mock org API ──
const { mockPageResult } = vi.hoisted(() => ({
  mockPageResult: { records: [], total: 0, pages: 0, current: 1, size: 20 },
}))

vi.mock('@/api/modules/org', () => ({
  getCompanyList: vi.fn().mockResolvedValue(mockPageResult),
  createCompany: vi.fn().mockResolvedValue({}),
  updateCompany: vi.fn().mockResolvedValue({}),
  deleteCompany: vi.fn().mockResolvedValue({}),
  getDepartmentTree: vi.fn().mockResolvedValue([]),
  createDepartment: vi.fn().mockResolvedValue({}),
  updateDepartment: vi.fn().mockResolvedValue({}),
  deleteDepartment: vi.fn().mockResolvedValue({}),
  getPositionList: vi.fn().mockResolvedValue(mockPageResult),
  createPosition: vi.fn().mockResolvedValue({}),
  updatePosition: vi.fn().mockResolvedValue({}),
  deletePosition: vi.fn().mockResolvedValue({}),
}))

// ── Mock ant-design-vue ──
vi.mock('ant-design-vue', async () => {
  const actual = await vi.importActual('ant-design-vue')
  return {
    ...actual,
    message: { success: vi.fn(), error: vi.fn(), info: vi.fn(), warning: vi.fn() },
    Modal: { confirm: vi.fn() },
  }
})

describe('org/index.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('组织页面正常渲染', () => {
    const wrapper = mount(OrgPage, {
      global: {
        stubs: {
          'a-card': { template: '<div class="stub-card"><slot /></div>' },
          'a-tabs': { template: '<div><slot /></div>' },
          'a-tab-pane': { template: '<div><slot /></div>' },
          'a-table': { template: '<table><slot /></table>' },
          'a-tree': { template: '<div />' },
          'a-input': { template: '<input />' },
          'a-input-search': { template: '<input />' },
          'a-button': { template: '<button><slot /></button>' },
          'a-tag': { template: '<span><slot /></span>' },
          'a-space': { template: '<div><slot /></div>' },
          'a-modal': { template: '<div><slot /></div>' },
          'a-form': { template: '<form><slot /></form>' },
          'a-form-item': { template: '<div><slot /></div>' },
          'a-select': { template: '<select><slot /></select>' },
          'a-select-option': { template: '<option><slot /></option>' },
          'a-switch': { template: '<input type="checkbox" />' },
          'a-tooltip': { template: '<div><slot /></div>' },
          'a-pagination': { template: '<div />' },
          'a-empty': { template: '<div />' },
          'a-input-number': { template: '<input type="number" />' },
          'a-textarea': { template: '<textarea />' },
          'a-spin': { template: '<div><slot /></div>' },
          'a-breadcrumb': { template: '<div><slot /></div>' },
          'a-breadcrumb-item': { template: '<span><slot /></span>' },
          BankOutlined: true,
          ClusterOutlined: true,
          TeamOutlined: true,
          PlusOutlined: true,
          SafetyCertificateOutlined: true,
        },
      },
    })

    expect(wrapper.exists()).toBe(true)
    expect(wrapper.html()).toBeTruthy()
  })
})
