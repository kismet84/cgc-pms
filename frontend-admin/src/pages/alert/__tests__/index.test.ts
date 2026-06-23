import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AlertPage from '../index.vue'

// ── Mock stores ──
const mockAlertStore = {
  alerts: [],
  fetchAlerts: vi.fn().mockResolvedValue(undefined),
  stats: {},
  fetchStats: vi.fn().mockResolvedValue(undefined),
}
const mockReferenceStore = {
  projects: [],
  fetchProjects: vi.fn().mockResolvedValue(undefined),
}

vi.mock('@/stores/alert', () => ({
  useAlertStore: () => mockAlertStore,
}))
vi.mock('@/stores/reference', () => ({
  useReferenceStore: () => mockReferenceStore,
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
          SearchOutlined: true,
          ReloadOutlined: true,
        },
      },
    })

    await flushPromises()

    expect(wrapper.exists()).toBe(true)
    expect(wrapper.html()).toBeTruthy()
    // 验证 fetchAlerts 在 onMounted 时被调用
    expect(mockAlertStore.fetchAlerts).toHaveBeenCalled()
  })
})
