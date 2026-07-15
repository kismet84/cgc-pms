import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AccountingEntryDetailVO, AccountingEntryVO } from '@/types/accounting'

const mocks = vi.hoisted(() => ({
  getList: vi.fn(),
  getDetail: vi.fn(),
  post: vi.fn(),
  reverse: vi.fn(),
  confirm: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
  roles: ['FINANCE'] as string[],
  permissions: ['accounting:query'] as string[],
}))

vi.mock('ant-design-vue', async () => {
  const actual = await vi.importActual<typeof import('ant-design-vue')>('ant-design-vue')
  return {
    ...actual,
    Modal: { confirm: mocks.confirm },
    message: { success: mocks.success, error: mocks.error },
  }
})

vi.mock('@/api/modules/accounting', () => ({
  getAccountingEntries: mocks.getList,
  getAccountingEntryDetail: mocks.getDetail,
  postAccountingEntry: mocks.post,
  reverseAccountingEntry: mocks.reverse,
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    roles: mocks.roles,
    hasPermission: (permission: string) =>
      mocks.permissions.includes('*') || mocks.permissions.includes(permission),
  }),
}))

import AccountingEntryPage from '../index.vue'

const currentDir = dirname(fileURLToPath(import.meta.url))

function entry(overrides: Partial<AccountingEntryVO> = {}): AccountingEntryVO {
  return {
    id: '101',
    entryCode: 'ACC-20260713-001',
    entryDate: '2026-07-13',
    entryType: 'COST',
    sourceType: 'CONTRACT',
    sourceId: '88',
    entryStatus: 'DRAFT',
    totalDebit: '100.00',
    totalCredit: '100.00',
    ...overrides,
  }
}

function detail(record: AccountingEntryVO): AccountingEntryDetailVO {
  return {
    entry: record,
    lines: [
      {
        id: '201',
        entryId: record.id,
        lineNo: 1,
        direction: 'DEBIT',
        costSubjectId: '301',
        amount: '100.00',
        summary: '测试分录',
      },
    ],
    subjectNames: { '301': '合同成本' },
  }
}

const tableStub = {
  props: ['dataSource'],
  template: `
    <div>
      <template v-for="record in dataSource" :key="record.id">
        <slot name="bodyCell" :column="{ key: 'actions' }" :record="record" />
      </template>
    </div>
  `,
}

const buttonStub = {
  template: '<button v-bind="$attrs"><slot name="icon" /><slot /></button>',
}

function mountPage() {
  return mount(AccountingEntryPage, {
    global: {
      stubs: {
        ATable: tableStub,
        AButton: buttonStub,
        ASpace: { template: '<div><slot /></div>' },
        ATag: { template: '<span><slot /></span>' },
        AAlert: true,
        AInput: true,
        ASelect: true,
        ASelectOption: true,
        APagination: true,
        ADrawer: true,
        ASpin: true,
        ADescriptions: true,
        ADescriptionsItem: true,
        ABreadcrumb: true,
        ABreadcrumbItem: true,
        EyeOutlined: true,
        ReloadOutlined: true,
      },
    },
  })
}

describe('accounting entry page', () => {
  beforeEach(() => {
    mocks.roles = ['FINANCE']
    mocks.permissions = ['accounting:query']
    mocks.getList.mockReset().mockResolvedValue({
      records: [entry(), entry({ id: '102', entryCode: 'ACC-002', entryStatus: 'POSTED' })],
      total: 2,
      pageNo: 1,
      pageSize: 20,
    })
    mocks.getDetail.mockReset().mockImplementation(async (id: string) => detail(entry({ id })))
    mocks.post.mockReset().mockResolvedValue(undefined)
    mocks.reverse.mockReset().mockResolvedValue(undefined)
    mocks.confirm.mockReset()
    mocks.success.mockReset()
    mocks.error.mockReset()
  })

  it('loads the tenant-scoped accounting entry list and opens detail', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(mocks.getList).toHaveBeenCalledWith({ pageNo: 1, pageSize: 20 })
    await wrapper.get('[data-testid="detail-101"]').trigger('click')
    await flushPromises()
    expect(mocks.getDetail).toHaveBeenCalledWith('101')
  })

  it('keeps query-only finance users from seeing post and reverse actions', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="post-101"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="reverse-102"]').exists()).toBe(false)
  })

  it('shows state-valid actions with accounting:edit and executes confirmed post', async () => {
    mocks.permissions = ['accounting:query', 'accounting:edit']
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="post-101"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="reverse-102"]').exists()).toBe(true)

    await wrapper.get('[data-testid="post-101"]').trigger('click')
    const options = mocks.confirm.mock.calls[0][0] as { onOk: () => Promise<void> }
    await options.onOk()

    expect(mocks.post).toHaveBeenCalledWith('101')
    expect(mocks.success).toHaveBeenCalledWith('凭证过账成功')
    expect(mocks.getList).toHaveBeenCalledTimes(2)
  })

  it('does not expose the unsupported generation endpoint as a user action', () => {
    const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
    expect(source).not.toContain('generateAccounting')
    expect(source).not.toContain('/generate')
    expect(source).toContain('凭证生成入口暂未开放')
  })

  it('uses the ledger layout with KPI context and an analysis rail', () => {
    const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
    expect(source).toContain('class="lg-list-page lg-page app-page accounting-entry-page"')
    expect(source).toContain('class="lg-kpi-strip accounting-entry-kpis"')
    expect(source).toContain('class="lg-list-table-panel accounting-entry-table-panel"')
    expect(source).toContain('class="lg-analysis-rail accounting-entry-analysis-rail"')
    expect(source).not.toContain('<h1>')
    expect(source).not.toContain('<a-alert')
    expect(source.indexOf('class="lg-kpi-strip accounting-entry-kpis"')).toBeLessThan(
      source.indexOf('class="lg-search-bar accounting-entry-filter"'),
    )
    expect(source.indexOf('class="lg-search-bar accounting-entry-filter"')).toBeLessThan(
      source.indexOf('class="lg-list-table-panel accounting-entry-table-panel"'),
    )
    expect(source).toMatch(/\.accounting-entry-kpis\s*\{[^}]*gap:\s*0;/s)
    expect(source).toMatch(
      /\.accounting-entry-analysis-rail\s*\{[^}]*display:\s*flex\s*!important;/s,
    )
    expect(source).toMatch(/\.accounting-entry-table-panel\s*\{[^}]*min-width:\s*0;/s)
  })
})
