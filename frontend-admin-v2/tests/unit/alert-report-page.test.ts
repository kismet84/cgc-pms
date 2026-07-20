import type { ReportCatalogItem } from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ReportCatalogPage from '@/pages/workbench/ReportCatalogPage.vue'
import { loadReportCatalog } from '@/services/reports'

vi.mock('@/services/reports', () => ({ loadReportCatalog: vi.fn() }))

beforeEach(() => {
  setActivePinia(createPinia())
  vi.mocked(loadReportCatalog).mockReset()
})

describe('M2 report page', () => {
  it('renders api-only and unknown reports without clickable page entries', async () => {
    const item = (overrides: Partial<ReportCatalogItem>): ReportCatalogItem => ({
      code: 'alert-center',
      name: '预警中心',
      catalog: 'alert',
      sourceType: 'page',
      target: '/alert',
      permissionCode: 'alert:view',
      filterSummary: '预警筛选',
      exportSupport: true,
      status: 'available',
      ...overrides,
    })
    vi.mocked(loadReportCatalog).mockResolvedValue([
      item({}),
      item({ code: 'alert-api', name: '预警统计接口', sourceType: 'api', status: 'api_only' }),
      item({ code: 'unknown', name: '未知页面', target: '/unknown' }),
    ])
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/dashboard/reports', component: ReportCatalogPage },
        { path: '/alert', component: { template: '<div />' } },
      ],
    })
    await router.push('/dashboard/reports')
    await router.isReady()
    const wrapper = mount(ReportCatalogPage, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.findAll('button').filter((button) => button.text() === '打开')).toHaveLength(0)
    expect(wrapper.text()).toContain('API-only')
    expect(wrapper.text().match(/无页面入口/g)).toHaveLength(3)
  })
})
