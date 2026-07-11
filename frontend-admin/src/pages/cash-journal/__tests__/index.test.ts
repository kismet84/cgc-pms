import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { flushPromises, mount, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CashJournalEntryVO } from '@/types/cashbook'

const mocks = vi.hoisted(() => ({
  warning: vi.fn(),
  getList: vi.fn(),
  getSummary: vi.fn(),
  getDetail: vi.fn(),
  getAccounts: vi.fn(),
  archive: vi.fn(),
  listFiles: vi.fn(),
  uploadFile: vi.fn(),
  deleteFile: vi.fn(),
  routeQuery: {} as Record<string, string>,
  roles: ['SUPER_ADMIN'] as string[],
  permissions: ['*'] as string[],
  fetchProjects: vi.fn(),
  fetchContracts: vi.fn(),
  projects: [] as Array<{ id: string; projectName: string }>,
  contracts: [] as Array<{ id: string; contractName: string }>,
}))

vi.mock('ant-design-vue', async () => {
  const actual = await vi.importActual<typeof import('ant-design-vue')>('ant-design-vue')
  return { ...actual, message: { success: vi.fn(), error: vi.fn(), warning: mocks.warning } }
})

vi.mock('@/api/modules/cashbook', () => ({
  getCashJournalList: mocks.getList,
  getCashJournalSummary: mocks.getSummary,
  getCashJournalDetail: mocks.getDetail,
  getFundAccounts: mocks.getAccounts,
  getManageableFundAccounts: vi.fn().mockResolvedValue([]),
  archiveCashJournalEntry: mocks.archive,
  createCashJournalEntry: vi.fn(),
  updateCashJournalEntry: vi.fn(),
  reverseCashJournalEntry: vi.fn(),
  reopenCashJournalEntry: vi.fn(),
  exportCashJournal: vi.fn(),
  createFundAccount: vi.fn(),
  updateFundAccount: vi.fn(),
  setFundAccountEnabled: vi.fn(),
}))

vi.mock('@/api/modules/file', () => ({
  listFiles: mocks.listFiles,
  uploadFile: mocks.uploadFile,
  deleteFile: mocks.deleteFile,
  getFileUrl: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/cash-journal', query: mocks.routeQuery }),
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    roles: mocks.roles,
    hasPermission: (permission: string) =>
      mocks.permissions.includes('*') || mocks.permissions.includes(permission),
  }),
}))

vi.mock('@/stores/reference', () => ({
  useReferenceStore: () => ({
    projects: mocks.projects,
    contracts: mocks.contracts,
    fetchProjects: mocks.fetchProjects,
    fetchContracts: mocks.fetchContracts,
  }),
}))

import CashJournalPage from '../index.vue'
import CashJournalDetailDrawer from '../components/CashJournalDetailDrawer.vue'

const currentDir = dirname(fileURLToPath(import.meta.url))

function entry(overrides: Partial<CashJournalEntryVO> = {}): CashJournalEntryVO {
  return {
    id: '101',
    entryNo: 'CJ-20260710-001',
    direction: 'OUT',
    amount: '10.00',
    businessDate: '2026-07-10',
    summary: '测试付款',
    sourceType: 'PAY_RECORD',
    sourceId: '88',
    status: 'PENDING_ARCHIVE',
    version: 0,
    createdAt: '2026-07-10T10:00:00',
    attachmentCount: 0,
    attachments: [],
    ...overrides,
  }
}

const drawerStubs = {
  ADrawer: { template: '<section><slot /></section>' },
  ADescriptions: { template: '<div><slot /></div>' },
  ADescriptionsItem: { template: '<div><slot /></div>' },
  ATag: { template: '<span><slot /></span>' },
  AButton: { template: '<button v-bind="$attrs"><slot /></button>' },
  AModal: { template: '<div><slot /></div><template v-if="$slots.footer"><slot name="footer" /></template>' },
  AList: { template: '<div><slot /></div>' },
  AListItem: { template: '<div><slot /></div>' },
  AUpload: { template: '<div><slot /></div>' },
  AEmpty: { template: '<div>empty</div>' },
}

describe('cash journal detail controls', () => {
  beforeEach(() => {
    mocks.warning.mockReset()
  })

  it('warns and does not archive when no attachment exists', async () => {
    const wrapper = mount(CashJournalDetailDrawer, {
      props: { open: true, entry: entry(), canMaintain: true, isSuperAdmin: true },
      global: { stubs: drawerStubs },
    })

    await wrapper.find('[data-testid="archive-button"]').trigger('click')

    expect(mocks.warning).toHaveBeenCalledWith('请先上传至少一个附件再归档')
    expect(wrapper.emitted('archive')).toBeUndefined()
  })

  it('keeps archived entries read-only and requires a reopen reason', async () => {
    const wrapper = mount(CashJournalDetailDrawer, {
      props: {
        open: true,
        entry: entry({ status: 'ARCHIVED', attachmentCount: 1, attachments: [{ id: 'f1' } as never] }),
        canMaintain: true,
        isSuperAdmin: true,
      },
      global: { stubs: drawerStubs },
    })

    expect(wrapper.find('[data-testid="file-upload"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="delete-file-f1"]').exists()).toBe(false)

    await wrapper.find('[data-testid="reopen-button"]').trigger('click')
    await wrapper.find('[data-testid="reopen-confirm"]').trigger('click')
    expect(mocks.warning).toHaveBeenCalledWith('请输入撤销归档原因')
    expect(wrapper.emitted('reopen')).toBeUndefined()

    await wrapper.find('[data-testid="reopen-reason"]').setValue('修正银行账号')
    await wrapper.find('[data-testid="reopen-confirm"]').trigger('click')
    expect(wrapper.emitted('reopen')?.[0]).toEqual(['修正银行账号'])
  })
})

describe('cash journal page', () => {
  beforeEach(() => {
    mocks.routeQuery = {}
    mocks.roles = ['SUPER_ADMIN']
    mocks.permissions = ['*']
    mocks.fetchProjects.mockReset().mockResolvedValue([])
    mocks.fetchContracts.mockReset().mockResolvedValue([])
    mocks.projects = []
    mocks.contracts = []
    mocks.getList.mockReset().mockResolvedValue({ records: [], total: 0, pageNo: 1, pageSize: 20 })
    mocks.getSummary.mockReset().mockResolvedValue({
      cashBalance: '1.00', bankBalance: '2.00', income: '3.00', expense: '4.00', pendingCount: 0,
    })
    mocks.getAccounts.mockReset().mockResolvedValue([])
    mocks.getDetail.mockReset().mockResolvedValue(entry())
  })

  it('loads the summary, journal list and accounts', async () => {
    shallowMount(CashJournalPage)
    await flushPromises()

    expect(mocks.getList).toHaveBeenCalled()
    expect(mocks.getSummary).toHaveBeenCalled()
    expect(mocks.getAccounts).toHaveBeenCalled()
  })

  it('opens a linked entry from the entryId query', async () => {
    mocks.routeQuery = { entryId: '101' }
    shallowMount(CashJournalPage)
    await flushPromises()

    expect(mocks.getDetail).toHaveBeenCalledWith('101')
  })

  it('keeps the core journal usable without loading unauthorized project and contract references', async () => {
    mocks.roles = ['FINANCE']
    mocks.permissions = [
      'cashbook:journal:query',
      'cashbook:journal:maintain',
      'cashbook:journal:export',
    ]
    mocks.projects = [{ id: 'p-secret', projectName: '不可见项目' }]
    mocks.contracts = [{ id: 'c-secret', contractName: '不可见合同' }]
    mocks.getList.mockResolvedValue({
      records: [entry({ projectId: 'p-secret', contractId: 'c-secret' })],
      total: 1,
      pageNo: 1,
      pageSize: 20,
    })

    const wrapper = mount(CashJournalPage, {
      global: {
        stubs: {
          CashJournalDetailDrawer: true,
          CashJournalFormModal: true,
          FundAccountModal: true,
        },
      },
    })
    await flushPromises()

    expect(mocks.getList).toHaveBeenCalled()
    expect(mocks.getSummary).toHaveBeenCalled()
    expect(mocks.fetchProjects).not.toHaveBeenCalled()
    expect(mocks.fetchContracts).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="project-filter"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="contract-filter"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="create-entry-button"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('不可见项目')
    expect(wrapper.text()).not.toContain('不可见合同')
  })

  it.each([
    ['project:query', true, false],
    ['contract:query', false, true],
  ])('loads only the authorized %s reference', async (permission, loadsProjects, loadsContracts) => {
    mocks.roles = ['FINANCE']
    mocks.permissions = ['cashbook:journal:query', permission]

    const wrapper = mount(CashJournalPage, {
      global: {
        stubs: {
          CashJournalDetailDrawer: true,
          CashJournalFormModal: true,
          FundAccountModal: true,
        },
      },
    })
    await flushPromises()

    expect(mocks.fetchProjects).toHaveBeenCalledTimes(loadsProjects ? 1 : 0)
    expect(mocks.fetchContracts).toHaveBeenCalledTimes(loadsContracts ? 1 : 0)
    expect(wrapper.find('[data-testid="project-filter"]').exists()).toBe(loadsProjects)
    expect(wrapper.find('[data-testid="contract-filter"]').exists()).toBe(loadsContracts)
  })

  it('contains the wide table scroll inside the page and wraps primary actions at 440px', () => {
    const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
    expect(source).toMatch(/\.cash-journal-page\s*\{[^}]*min-width:\s*0;[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\);/s)
    expect(source).toMatch(/\.cash-journal-table-card\s*\{[^}]*min-width:\s*0;/s)
    expect(source).toMatch(/\.cash-journal-table-wrap\s*\{[^}]*max-width:\s*100%;[^}]*overflow-x:\s*auto;/s)
    expect(source).toMatch(/@media \(max-width: 440px\)[\s\S]*?\.cash-journal-primary-actions\s*\{[^}]*position:\s*static;[^}]*width:\s*100%;/s)
    expect(source).toMatch(/@media \(max-width: 440px\)[\s\S]*?\.cash-journal-primary-actions :deep\(\.ant-btn\)\s*\{[^}]*flex:\s*1 1 132px;/s)
    expect(source).toMatch(/@media \(max-width: 440px\)[\s\S]*?\.filter-grid\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\);/s)
  })
})
