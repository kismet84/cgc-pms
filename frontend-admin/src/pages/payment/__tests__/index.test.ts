import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const mocks = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
  writeback: vi.fn(),
  getCashJournalList: vi.fn(),
  routeQuery: {} as Record<string, string>,
  roles: ['USER'] as string[],
  permissions: new Set<string>(),
}))

vi.mock('ant-design-vue', () => ({
  message: { success: mocks.success, error: mocks.error, warning: mocks.warning },
  Modal: { confirm: vi.fn() },
}))

vi.mock('pinia', async () => {
  const { ref } = await import('vue')
  return { storeToRefs: () => ({ projects: ref([]), contracts: ref([]) }) }
})

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/payment', query: mocks.routeQuery }),
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    roles: mocks.roles,
    hasPermission: (code: string) => mocks.permissions.has('*') || mocks.permissions.has(code),
  }),
}))

vi.mock('@/stores/reference', () => ({
  useReferenceStore: () => ({
    fetchProjects: vi.fn().mockResolvedValue([]),
    fetchContracts: vi.fn().mockResolvedValue([]),
    fetchPartners: vi.fn().mockResolvedValue([]),
  }),
}))

vi.mock('@/api/modules/payment', () => ({
  createApplication: vi.fn(),
  deleteApplication: vi.fn(),
  doWriteback: mocks.writeback,
  getApplicationDetail: vi.fn(),
  getApplicationList: vi.fn().mockResolvedValue({
    records: [
      {
        id: 'pay-app-1',
        applyCode: 'PAY-001',
        applyAmount: '100.00',
        approvedAmount: '100.00',
        actualPayAmount: '0.00',
        payType: 'PROGRESS',
        payStatus: 'UNPAID',
        approvalStatus: 'APPROVED',
      },
    ],
    total: 1,
  }),
  getBasisList: vi.fn(),
  saveBasis: vi.fn(),
  submitForApproval: vi.fn(),
  updateApplication: vi.fn(),
}))

vi.mock('@/api/modules/cashbook', () => ({ getCashJournalList: mocks.getCashJournalList }))
vi.mock('@/api/modules/receipt', () => ({
  getReceiptItems: vi.fn().mockResolvedValue([]),
  getReceiptList: vi.fn().mockResolvedValue({ records: [] }),
}))
vi.mock('@/api/modules/subcontract', () => ({
  getMeasureItems: vi.fn().mockResolvedValue([]),
  getMeasureList: vi.fn().mockResolvedValue({ records: [] }),
}))
vi.mock('@/utils/dict', () => ({
  fetchDictData: vi.fn(),
  getDictLabelSync: (_code: string, value: string) => value,
  getDictTagColorSync: () => 'default',
}))
vi.mock('@/composables/useColumnSettings', async () => {
  const { ref } = await import('vue')
  return {
    useColumnSettings: (_key: string, columns: { value: unknown[] }) => ({
      visibleColumns: columns,
      columnSettings: ref([]),
      colVisible: ref(false),
      toggleCol: vi.fn(),
    }),
  }
})

import PaymentPage from '../index.vue'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
const configSource = readFileSync(resolve(currentDir, '../pageConfig.ts'), 'utf-8')

const stubs = {
  PaymentOverviewPanel: { template: '<section><slot /></section>' },
  PaymentFormModal: true,
  ColumnSettingsButton: true,
  LgEmptyState: { template: '<div><slot /></div>' },
  VxeGrid: {
    props: ['data'],
    template: '<div><slot v-if="data[0]" name="action" :row="data[0]" /></div>',
  },
  ADropdown: { template: '<div><slot /><slot name="overlay" /></div>' },
  AMenu: { template: '<div><slot /></div>' },
  AMenuItem: {
    emits: ['click'],
    template: '<button @click="$emit(\'click\')"><slot /></button>',
  },
  AModal: {
    props: ['open'],
    emits: ['ok', 'cancel'],
    template:
      '<div v-if="open" data-testid="writeback-modal"><slot /><button data-testid="writeback-confirm" @click="$emit(\'ok\')">confirm</button></div>',
  },
  AForm: { template: '<form><slot /></form>' },
  AFormItem: { template: '<label><slot /></label>' },
  AInputNumber: {
    props: ['value'],
    emits: ['update:value'],
    template:
      '<input data-testid="pay-amount" :value="value" @input="$emit(\'update:value\', Number($event.target.value))" />',
  },
  ADatePicker: {
    props: ['value'],
    emits: ['update:value'],
    template:
      '<input data-testid="pay-date" :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
  },
  ASelect: { template: '<select><slot /></select>' },
  ASelectOption: { template: '<option><slot /></option>' },
  AInput: {
    props: ['value', 'placeholder'],
    emits: ['update:value'],
    template:
      '<input :placeholder="placeholder" :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
  },
  AButton: { template: '<button><slot /></button>' },
  APagination: true,
  ATag: { template: '<span><slot /></span>' },
}

async function mountPaymentPage() {
  const wrapper = mount(PaymentPage, { global: { stubs } })
  await flushPromises()
  return wrapper
}

async function openAndFillWriteback(wrapper: Awaited<ReturnType<typeof mountPaymentPage>>) {
  const writebackButton = wrapper.findAll('button').find((button) => button.text() === '付款回写')
  expect(writebackButton).toBeDefined()
  await writebackButton!.trigger('click')
  await wrapper.get('[data-testid="pay-amount"]').setValue('100')
  await wrapper.get('[data-testid="pay-date"]').setValue('2026-07-11')
  await wrapper.get('input[placeholder="银行或支付渠道唯一流水号"]').setValue('BANK-001')
}

describe('payment page quality guardrails', () => {
  it('avoids silent catch blocks in critical payment actions', () => {
    expect(source).not.toContain('catch {')
    expect(source).not.toMatch(/catch\s*\(e\)\s*\{/)
    expect(source).toContain("message.warning('验收单依据加载失败，可稍后重试')")
    expect(source).toContain("message.warning('分包计量依据加载失败，可稍后重试')")
    expect(source).toContain("getErrorMessage(e, '删除失败，请稍后重试')")
    expect(source).toContain("getErrorMessage(e, '操作失败，请稍后重试')")
    expect(source).toContain("getErrorMessage(e, '提交审批失败，请稍后重试')")
    expect(source).toContain("getErrorMessage(e, '回写失败，请稍后重试')")
  })

  it('extracts static payment page config out of the giant component', () => {
    expect(source).toContain("from './pageConfig'")
    expect(configSource).toContain('export const APPROVAL_STATUS_LABEL')
    expect(configSource).toContain('export const APPROVAL_STATUS_COLOR')
    expect(configSource).toContain('export const PAYMENT_GRID_COLUMNS')
  })

  it('keeps the page shell split into local subcomponents', () => {
    expect(source).toContain("from './components/PaymentOverviewPanel.vue'")
    expect(source).toContain("from './components/PaymentFormModal.vue'")
    expect(source).toContain('<PaymentOverviewPanel')
    expect(source).toContain('<PaymentFormModal')
  })
})

describe('payment writeback result boundaries', () => {
  beforeEach(() => {
    mocks.success.mockReset()
    mocks.error.mockReset()
    mocks.warning.mockReset()
    mocks.writeback.mockReset()
    mocks.getCashJournalList.mockReset()
    mocks.routeQuery = {}
    mocks.roles = ['USER']
    mocks.permissions = new Set(['cashbook:journal:query'])
  })

  it('keeps the modal and entered form values when authoritative writeback fails', async () => {
    mocks.writeback.mockRejectedValue(new Error('银行拒绝付款'))
    const wrapper = await mountPaymentPage()

    await openAndFillWriteback(wrapper)
    await wrapper.get('[data-testid="writeback-confirm"]').trigger('click')
    await flushPromises()

    expect(mocks.error).toHaveBeenCalledWith('银行拒绝付款')
    expect(wrapper.find('[data-testid="writeback-modal"]').exists()).toBe(true)
    expect((wrapper.get('[data-testid="pay-amount"]').element as HTMLInputElement).value).toBe(
      '100',
    )
    expect(
      (wrapper.get('input[placeholder="银行或支付渠道唯一流水号"]').element as HTMLInputElement)
        .value,
    ).toBe('BANK-001')
    expect(mocks.getCashJournalList).not.toHaveBeenCalled()
  })

  it('clears a stale linked journal before a new lookup and keeps lookup failure non-blocking', async () => {
    mocks.routeQuery = { payRecordId: 'old-pay-record' }
    mocks.writeback.mockResolvedValue({ id: 'new-pay-record' })
    let rejectLookup!: (reason: unknown) => void
    const pendingLookup = new Promise((_, reject) => {
      rejectLookup = reject
    })
    mocks.getCashJournalList
      .mockResolvedValueOnce({
        records: [{ id: 'old-journal', entryNo: 'OLD-JOURNAL', status: 'PENDING_ARCHIVE' }],
      })
      .mockReturnValueOnce(pendingLookup)
    const wrapper = await mountPaymentPage()
    expect(wrapper.text()).toContain('OLD-JOURNAL')

    await openAndFillWriteback(wrapper)
    await wrapper.get('[data-testid="writeback-confirm"]').trigger('click')
    await flushPromises()

    expect(mocks.success).toHaveBeenCalledWith('回写成功')
    expect(mocks.error).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="writeback-modal"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('OLD-JOURNAL')

    rejectLookup(new Error('403'))
    await flushPromises()
    expect(mocks.warning).toHaveBeenCalledWith('付款成功，关联日记账暂不可查看')
  })

  it('does not query linked journals without cashbook view access', async () => {
    mocks.permissions = new Set(['payment:record:writeback'])
    mocks.writeback.mockResolvedValue({ id: 'new-pay-record' })
    const wrapper = await mountPaymentPage()

    await openAndFillWriteback(wrapper)
    await wrapper.get('[data-testid="writeback-confirm"]').trigger('click')
    await flushPromises()

    expect(mocks.success).toHaveBeenCalledWith('回写成功')
    expect(mocks.getCashJournalList).not.toHaveBeenCalled()
  })

  it.each(['ADMIN', 'SUPER_ADMIN'])(
    'allows %s to view the linked journal without an explicit query permission',
    async (role) => {
      mocks.roles = [role]
      mocks.permissions = new Set(['payment:record:writeback'])
      mocks.writeback.mockResolvedValue({ id: 'new-pay-record' })
      mocks.getCashJournalList.mockResolvedValue({ records: [] })
      const wrapper = await mountPaymentPage()

      await openAndFillWriteback(wrapper)
      await wrapper.get('[data-testid="writeback-confirm"]').trigger('click')
      await flushPromises()

      expect(mocks.getCashJournalList).toHaveBeenCalledWith(
        expect.objectContaining({ sourceId: 'new-pay-record' }),
      )
    },
  )

  it('handles a linked journal query failure from the route without an unhandled rejection', async () => {
    mocks.routeQuery = { payRecordId: 'missing-pay-record' }
    mocks.getCashJournalList.mockRejectedValue(new Error('403'))

    const wrapper = await mountPaymentPage()
    await flushPromises()

    expect(wrapper.text()).not.toContain('关联资金流水')
    expect(mocks.warning).toHaveBeenCalledWith('关联日记账暂不可查看')
    expect(mocks.error).not.toHaveBeenCalled()
  })
})
