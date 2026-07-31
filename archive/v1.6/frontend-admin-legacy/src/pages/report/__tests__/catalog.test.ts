import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReportCatalogItem } from '@/api/modules/report'

const mocks = vi.hoisted(() => ({
  getReportCatalog: vi.fn(),
  routerPush: vi.fn(),
  userStore: {
    roles: [] as string[],
    permissions: new Set<string>(),
    hasPermission(permission: string) {
      return this.permissions.has(permission)
    },
  },
}))

vi.mock('@/api/modules/report', () => ({
  getReportCatalog: mocks.getReportCatalog,
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => mocks.userStore,
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mocks.routerPush,
    resolve: (target: string) => ({
      matched: ['/dashboard', '/alert'].includes(target) ? [{}] : [],
    }),
  }),
}))

import ReportCatalogPage from '../catalog.vue'

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

const ATableStub = defineComponent({
  name: 'ATableStub',
  props: {
    columns: Array,
    dataSource: Array,
  },
  setup(props, { slots }) {
    return () => {
      const columns = (props.columns as Array<Record<string, string>>) ?? []
      const rows = (props.dataSource as Array<Record<string, unknown>>) ?? []

      return h('table', { class: 'table-stub' }, [
        h(
          'thead',
          columns.map((column) => h('th', column.title)),
        ),
        h(
          'tbody',
          rows.length
            ? rows.map((record) =>
                h(
                  'tr',
                  columns.map((column) =>
                    h(
                      'td',
                      slots.bodyCell?.({ column, record }) ??
                        String(record[column.dataIndex as string] ?? ''),
                    ),
                  ),
                ),
              )
            : h('tr', h('td', slots.emptyText?.())),
        ),
      ])
    }
  },
})

const stubs = {
  'a-breadcrumb': { template: '<nav><slot /></nav>' },
  'a-breadcrumb-item': { template: '<span><slot /></span>' },
  'a-button': { template: '<button><slot name="icon" /><slot /></button>' },
  'a-table': ATableStub,
  'a-tag': { template: '<span><slot /></span>' },
  'a-empty': { props: ['description'], template: '<span>{{ description }}</span>' },
  ApiOutlined: true,
  ExportOutlined: true,
  LinkOutlined: true,
  ReloadOutlined: true,
}

function item(overrides: Partial<ReportCatalogItem>): ReportCatalogItem {
  return {
    code: 'dashboard-management',
    name: '管理驾驶舱',
    catalog: 'dashboard',
    sourceType: 'page',
    target: '/dashboard',
    permissionCode: 'dashboard:view',
    filterSummary: '按当前用户驾驶舱口径展示',
    exportSupport: false,
    status: 'available',
    ...overrides,
  }
}

async function mountCatalog(items: ReportCatalogItem[]) {
  mocks.getReportCatalog.mockResolvedValue(items)
  const wrapper = mount(ReportCatalogPage, { global: { stubs } })
  await flushPromises()
  return wrapper
}

describe('ReportCatalogPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.userStore.roles = []
    mocks.userStore.permissions = new Set<string>()
  })

  it('renders visible page and API-only report entries for admin users', async () => {
    mocks.userStore.roles = ['ADMIN']

    const wrapper = await mountCatalog([
      item({ name: '管理驾驶舱', target: '/dashboard', exportSupport: false }),
      item({
        code: 'alert-center',
        name: '预警中心',
        catalog: 'alert',
        target: '/alert',
        permissionCode: 'alert:view',
        exportSupport: true,
      }),
      item({
        code: 'workflow-efficiency',
        name: '审批效率统计',
        catalog: 'workflow',
        sourceType: 'api',
        target: '/workflow/statistics/efficiency',
        permissionCode: '',
        filterSummary: '按当前登录用户本人审批记录统计',
        exportSupport: false,
        status: 'api_only',
      }),
    ])

    const text = wrapper.text()
    expect(text).toContain('报表目录')
    expect(text).toContain('可见报表3')
    expect(text).toContain('页面型2')
    expect(text).toContain('API-only1')
    expect(text).toContain('管理驾驶舱')
    expect(text).toContain('预警中心')
    expect(text).toContain('审批效率统计')
    expect(text).toContain('支持导出')
    expect(text).toContain('无导出入口')
    expect(text).toContain('/workflow/statistics/efficiency')
  })

  it('renders a stable empty state when no report is visible', async () => {
    const wrapper = await mountCatalog([])

    const text = wrapper.text()
    expect(text).toContain('可见报表0')
    expect(text).toContain('页面型0')
    expect(text).toContain('API-only0')
    expect(text).toContain('当前暂无可见报表')
  })

  it('renders a stable empty state when permissions hide every report', async () => {
    const wrapper = await mountCatalog([
      item({ name: '管理驾驶舱', permissionCode: 'dashboard:view' }),
      item({
        code: 'alerts-processing-report',
        name: '预警处理统计',
        catalog: 'alert',
        sourceType: 'api',
        target: '/alerts/processing-report',
        permissionCode: 'alert:view',
        status: 'api_only',
      }),
    ])

    const text = wrapper.text()
    expect(text).toContain('可见报表0')
    expect(text).toContain('当前暂无可见报表')
    expect(text).not.toContain('管理驾驶舱')
    expect(text).not.toContain('预警处理统计')
  })

  it('shows a retry entry after catalog load fails and clears it after retry succeeds', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    mocks.userStore.permissions = new Set(['dashboard:view'])
    mocks.getReportCatalog
      .mockRejectedValueOnce(new Error('catalog failed'))
      .mockResolvedValueOnce([item({ name: '管理驾驶舱', target: '/dashboard' })])

    const wrapper = mount(ReportCatalogPage, { global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('加载报表目录失败，请稍后重试。')
    await wrapper.find('.report-retry-button').trigger('click')
    await flushPromises()

    expect(mocks.getReportCatalog).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).not.toContain('加载报表目录失败，请稍后重试。')
    expect(wrapper.text()).toContain('管理驾驶舱')
    consoleError.mockRestore()
  })

  it('filters restricted entries and keeps API-only fallback non-clickable', async () => {
    mocks.userStore.permissions = new Set(['dashboard:view'])

    const wrapper = await mountCatalog([
      item({ name: '管理驾驶舱', target: '/dashboard' }),
      item({
        code: 'alert-center',
        name: '预警中心',
        catalog: 'alert',
        target: '/alert',
        permissionCode: 'alert:view',
        exportSupport: true,
      }),
      item({
        code: 'workflow-efficiency',
        name: '审批效率统计',
        catalog: 'workflow',
        sourceType: 'api',
        target: '/workflow/statistics/efficiency',
        permissionCode: '',
        exportSupport: false,
        status: 'api_only',
      }),
    ])

    const text = wrapper.text()
    expect(text).toContain('管理驾驶舱')
    expect(text).toContain('审批效率统计')
    expect(text).toContain('API-only')
    expect(text).toContain('无导出入口')
    expect(text).not.toContain('预警中心')
    expect(wrapper.findAll('button').some((button) => button.text().includes('审批效率统计'))).toBe(
      false,
    )
  })
})
