import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import AlertPage from '../index.vue'

const { mockGetAlertSubscription, mockUpdateAlertSubscription } = vi.hoisted(() => ({
  mockGetAlertSubscription: vi.fn().mockResolvedValue({
    defaultSubscription: {
      enabled: true,
      channels: ['IN_APP'],
      domains: ['PURCHASE'],
      minSeverity: 'LOW',
      notifyOnStatusChanged: true,
    },
    rawUserOverrides: null,
    effectiveSubscription: {
      enabled: true,
      channels: ['IN_APP'],
      domains: ['PURCHASE'],
      minSeverity: 'LOW',
      notifyOnStatusChanged: true,
    },
    availableOptions: {
      domains: ['PURCHASE'],
      channels: ['IN_APP'],
      minSeverityOptions: ['LOW', 'MEDIUM', 'HIGH'],
    },
  }),
  mockUpdateAlertSubscription: vi.fn().mockResolvedValue(undefined),
}))

// ── Mock stores ──
const mockAlertStore = {
  alerts: [],
  total: 0,
  pageNo: 1,
  pageSize: 20,
  fetchAlerts: vi.fn().mockResolvedValue(undefined),
  batchMarkRead: vi.fn().mockResolvedValue(0),
  markRead: vi.fn().mockResolvedValue(undefined),
  changeStatus: vi.fn().mockResolvedValue(undefined),
  evaluating: false,
  loading: false,
  markingRead: new Set<string>(),
  triggerBatchEvaluate: vi.fn().mockResolvedValue({ alertsGenerated: 0 }),
}
const mockReferenceStore = {
  projects: [],
  fetchProjects: vi.fn().mockResolvedValue(undefined),
}
const mockUserStore = {
  roles: ['PURCHASE_MANAGER'],
  userInfo: { roleName: '采购经理' },
}

vi.mock('@/stores/alert', () => ({
  useAlertStore: () => mockAlertStore,
}))
vi.mock('@/stores/reference', () => ({
  useReferenceStore: () => mockReferenceStore,
}))
vi.mock('@/stores/user', () => ({
  useUserStore: () => mockUserStore,
}))
vi.mock('@/api/modules/alert', () => ({
  getAlertSubscription: mockGetAlertSubscription,
  updateAlertSubscription: mockUpdateAlertSubscription,
}))

// ── Mock ant-design-vue message ──
vi.mock('ant-design-vue', async () => {
  const actual = await vi.importActual('ant-design-vue')
  return {
    ...actual,
    message: { success: vi.fn(), error: vi.fn(), info: vi.fn(), warning: vi.fn() },
  }
})

describe('alert/index.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('页面正常渲染', async () => {
    const wrapper = mount(AlertPage, {
      global: {
        stubs: {
          VxeGrid: { template: '<div class="vxe-grid-stub"><slot /></div>' },
          VxeGridInstance: true,
          VxeColumn: true,
          'a-card': { template: '<div class="stub-card"><slot /></div>' },
          'a-input': { template: '<input />' },
          'a-select': { template: '<select><slot /></select>' },
          'a-select-option': { template: '<option><slot /></option>' },
          'a-button': { template: '<button><slot /></button>' },
          'a-tag': { template: '<span><slot /></span>' },
          'a-space': { template: '<div><slot /></div>' },
          'a-tooltip': { template: '<div><slot /></div>' },
          'a-pagination': { template: '<div />' },
          'a-badge': { template: '<span><slot /></span>' },
          'a-breadcrumb': { template: '<div><slot /></div>' },
          'a-breadcrumb-item': { template: '<span><slot /></span>' },
          'a-dropdown': { template: '<div><slot /><slot name="overlay" /></div>' },
          'a-menu': { template: '<div><slot /></div>' },
          'a-menu-item': { template: '<button><slot /></button>' },
          'a-range-picker': { template: '<div class="range-picker-stub"></div>' },
          'a-switch': { template: '<button class="switch-stub"></button>' },
          'a-modal': { template: '<div><slot /></div>' },
          'a-checkbox-group': { template: '<div><slot /></div>' },
          'a-checkbox': { template: '<label><slot /></label>' },
          'a-radio-group': { template: '<div><slot /></div>' },
          'a-radio-button': { template: '<button><slot /></button>' },
          'a-spin': { template: '<div><slot /></div>' },
          SearchOutlined: true,
          ReloadOutlined: true,
        },
      },
    })

    await flushPromises()

    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain('当前页未读标已读')
    expect(mockAlertStore.fetchAlerts).toHaveBeenCalledWith(
      expect.objectContaining({
        alertDomain: 'PURCHASE',
        pageNo: 1,
        pageNum: 1,
        pageSize: 20,
      }),
    )
    expect(mockGetAlertSubscription).toHaveBeenCalled()
  })

  it('源码包含后端分页、筛选和业务单据入口', () => {
    const pageSource = readFileSync(resolve(__dirname, '../index.vue'), 'utf-8')

    expect(pageSource).toContain('onlyDefaultScope')
    expect(pageSource).toContain('processStatus')
    expect(pageSource).toContain('triggeredAtRange')
    expect(pageSource).toContain('alertDomain')
    expect(pageSource).toContain('alertCategory')
    expect(pageSource).toContain('ruleType')
    expect(pageSource).toContain('当前页未读标已读')
    expect(pageSource).toContain('查看业务单据')
    expect(pageSource).toContain('标为已处理')
    expect(pageSource).toContain('标为失效')
    expect(pageSource).toContain('归档')
    expect(pageSource).toContain('pageNo: pageNo.value')
    expect(pageSource).toContain('pageSize: pageSize.value')
    expect(pageSource).toContain('sourceType ?? record.businessType')
    expect(pageSource).toContain('sourceId ?? record.businessId')
    expect(pageSource).toContain('resolveRoleDefaultPreset')
    expect(pageSource).toContain('resolveSearchAlertDomain')
    expect(pageSource).toContain('filter.onlyDefaultScope && preset.alertDomain')
    expect(pageSource).toContain("return { alertDomain: 'PURCHASE' }")
    expect(pageSource).toContain('.alert-search-bar')
    expect(pageSource).toContain('flex-wrap: wrap')
    expect(pageSource).toContain('.alert-default-scope-switch')
    expect(pageSource).toContain('z-index: 1')
    expect(pageSource).toContain(':disabled="!hasDefaultScopeDomain"')
    expect(pageSource).toContain('ALERT_PROCESS_STATUS_LABELS')
    expect(pageSource).toContain('getAlertTagLabel')
    expect(pageSource).toContain('通知订阅')
    expect(pageSource).toContain('getAlertSubscription')
    expect(pageSource).toContain('updateAlertSubscription')
    expect(pageSource).toContain('availableSubscriptionDomains')
    expect(pageSource).toContain('notifyOnStatusChanged')
    expect(pageSource).toContain('最低严重度')
  })
})
