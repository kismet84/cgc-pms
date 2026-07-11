import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, reactive } from 'vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import type { PageResult } from '@/types/api'
import type { AlertLogVO, AlertSubscriptionResponse } from '@/types/alert'
import AlertPage from '../index.vue'
import AlertDetailPanel from '../components/AlertDetailPanel.vue'
import AlertFilterPanel from '../components/AlertFilterPanel.vue'
import AlertSubscriptionModal from '../components/AlertSubscriptionModal.vue'
import AlertTablePanel from '../components/AlertTablePanel.vue'

const currentDir = dirname(fileURLToPath(import.meta.url))
const alertPageSource = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
const downloadUtilSource = readFileSync(resolve(currentDir, '../../../utils/download.ts'), 'utf-8')

const {
  mockExportAlertAudit,
  mockGetAlertList,
  mockGetAlertSubscription,
  mockUpdateAlertSubscription,
  mockDownloadBlobFile,
  mockRouterPush,
  mockRouterReplace,
  mockRoute,
  mockMessage,
} = vi.hoisted(
  () => ({
    mockExportAlertAudit: vi.fn(),
    mockGetAlertList: vi.fn(),
    mockGetAlertSubscription: vi.fn(),
    mockUpdateAlertSubscription: vi.fn(),
    mockDownloadBlobFile: vi.fn(),
    mockRouterPush: vi.fn(),
    mockRouterReplace: vi.fn(),
    mockRoute: {
      path: '/alert',
      query: {},
      meta: {},
    },
    mockMessage: {
      success: vi.fn(),
      error: vi.fn(),
      info: vi.fn(),
      warning: vi.fn(),
    },
  }),
)

function buildSubscriptionResponse(
  overrides: Partial<AlertSubscriptionResponse> = {},
): AlertSubscriptionResponse {
  return {
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
    ...overrides,
  }
}

function createAlertRecord(overrides: Partial<AlertLogVO> = {}): AlertLogVO {
  return {
    id: 'ALERT-001',
    projectId: 'P-01',
    ruleType: 'CONTRACT_OVERDUE',
    alertDomain: 'PURCHASE',
    alertCategory: 'PURCHASE',
    severity: 'HIGH',
    isRead: 0,
    processStatus: 'OPEN',
    triggeredAt: '2026-07-07 10:00:00',
    message: '采购订单逾期',
    statusRemark: '',
    processedAt: '',
    archivedAt: '',
    sourceType: 'PURCHASE_ORDER',
    sourceId: 'PO-9',
    businessType: 'PURCHASE_ORDER',
    businessId: 'PO-9',
    contractId: 'CT-1',
    ...overrides,
  } as AlertLogVO
}

function createPagedAlertResponse(
  records: AlertLogVO[],
  overrides: Partial<PageResult<AlertLogVO>> = {},
): PageResult<AlertLogVO> {
  return {
    records,
    total: records.length,
    pageNo: 1,
    pageSize: records.length || 20,
    ...overrides,
  }
}

const mockAlertStore = {
  alerts: [] as AlertLogVO[],
  total: 0,
  pageNo: 1,
  pageSize: 20,
  fetchAlerts: vi.fn().mockResolvedValue(undefined),
  batchMarkRead: vi.fn().mockResolvedValue({ successIds: [] }),
  batchChangeStatus: vi.fn().mockResolvedValue({ successIds: [] }),
  markRead: vi.fn().mockResolvedValue(undefined),
  changeStatus: vi.fn().mockResolvedValue(undefined),
  evaluating: false,
  loading: false,
  markingRead: new Set<string>(),
  triggerBatchEvaluate: vi.fn().mockResolvedValue({ alertsGenerated: 0 }),
}
const mockReferenceStore = {
  projects: [] as Array<{ id: string | number; projectCode: string; projectName: string }>,
  fetchProjects: vi.fn().mockResolvedValue(undefined),
}
const mockUserStore = {
  roles: ['PURCHASE_MANAGER'],
  userInfo: { roleName: '采购经理', nickname: '测试用户' },
  hasPermission: vi.fn((code: string) => code === 'alert:view' || code === 'alert:edit'),
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
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockRouterPush, replace: mockRouterReplace }),
  useRoute: () => mockRoute,
}))
vi.mock('@/api/modules/alert', () => ({
  exportAlertAudit: mockExportAlertAudit,
  getAlertList: mockGetAlertList,
  getAlertSubscription: mockGetAlertSubscription,
  updateAlertSubscription: mockUpdateAlertSubscription,
}))
vi.mock('@/utils/download', () => ({
  downloadBlobFile: mockDownloadBlobFile,
}))

vi.mock('ant-design-vue', async () => {
  const actual = await vi.importActual('ant-design-vue')
  return {
    ...actual,
    message: mockMessage,
  }
})

const AButtonStub = defineComponent({
  props: {
    type: { type: String, default: '' },
    size: { type: String, default: '' },
    disabled: { type: Boolean, default: false },
  },
  emits: ['click'],
  template:
    '<button :data-type="type" :data-size="size" :disabled="disabled" @click="$emit(\'click\', $event)"><slot /></button>',
})

const AInputStub = defineComponent({
  props: {
    value: { type: String, default: '' },
    placeholder: { type: String, default: '' },
  },
  emits: ['update:value'],
  template:
    '<label class="a-input-stub"><slot name="prefix" /><input :value="value" :placeholder="placeholder" @input="$emit(\'update:value\', $event.target.value)" /></label>',
})

const ATextareaStub = defineComponent({
  props: {
    value: { type: String, default: '' },
    maxlength: { type: Number, default: undefined },
    placeholder: { type: String, default: '' },
  },
  emits: ['update:value'],
  template:
    '<textarea :value="value" :maxlength="maxlength" :placeholder="placeholder" @input="$emit(\'update:value\', $event.target.value)"></textarea>',
})

const ACheckboxStub = defineComponent({
  props: {
    checked: { type: Boolean, default: false },
  },
  emits: ['update:checked', 'change'],
  template:
    '<label class="a-checkbox-stub"><input type="checkbox" :checked="checked" @change="$emit(\'update:checked\', $event.target.checked); $emit(\'change\', { target: { checked: $event.target.checked } })" /><slot /></label>',
})

const ASwitchStub = defineComponent({
  props: {
    checked: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
  },
  emits: ['update:checked'],
  template:
    '<label class="a-switch-stub"><input type="checkbox" :checked="checked" :disabled="disabled" @change="$emit(\'update:checked\', $event.target.checked)" /></label>',
})

const ASelectStub = defineComponent({
  props: {
    options: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    placeholder: { type: String, default: '' },
  },
  template:
    '<div class="a-select-stub" :data-loading="String(loading)" :data-placeholder="placeholder">{{ options.length }}</div>',
})

const ATagStub = defineComponent({
  template: '<span class="a-tag-stub"><slot /></span>',
})

const APaginationStub = defineComponent({
  template: '<div class="a-pagination-stub"></div>',
})

const AModalStub = defineComponent({
  props: {
    open: { type: Boolean, default: false },
    title: { type: String, default: '' },
    confirmLoading: { type: Boolean, default: false },
  },
  emits: ['update:open', 'ok'],
  template:
    '<div class="a-modal-stub" :data-open="String(open)"><div class="a-modal-title">{{ title }}</div><slot /><button class="modal-ok" @click="$emit(\'ok\')">确认</button><button class="modal-close" @click="$emit(\'update:open\', false)">关闭</button></div>',
})

const VxeGridStub = defineComponent({
  props: {
    data: { type: Array, default: () => [] },
  },
  template: `
    <div class="vxe-grid-stub">
      <slot name="selectionHeader" />
      <template v-if="data.length">
        <slot name="selection" :row="data[0]" />
        <slot name="id" :row="data[0]" />
        <slot name="projectId" :row="data[0]" />
        <slot name="alertDomain" :row="data[0]" />
        <slot name="ruleType" :row="data[0]" />
        <slot name="alertCategory" :row="data[0]" />
        <slot name="severity" :row="data[0]" />
        <slot name="processStatus" :row="data[0]" />
        <slot name="isRead" :row="data[0]" />
        <slot name="triggeredAt" :row="data[0]" />
        <slot name="message" :row="data[0]" />
        <slot name="action" :row="data[0]" />
      </template>
    </div>
  `,
})

const AlertFilterPanelHarness = defineComponent({
  props: {
    filter: { type: Object, required: true },
    handleSearch: { type: Function, required: true },
    handleReset: { type: Function, required: true },
  },
  setup(props) {
    const applySearch = () => {
      const filter = props.filter as {
        keyword: string
        projectId?: string
        alertDomain?: string
        severity?: string
        isRead?: number
        processStatus?: string
        onlyDefaultScope: boolean
      }
      filter.keyword = '  关键字  '
      filter.projectId = 'P-01'
      filter.alertDomain = 'CONTRACT'
      filter.severity = 'HIGH'
      filter.isRead = 0
      filter.processStatus = 'PROCESSED'
      filter.onlyDefaultScope = true
      ;(props.handleSearch as () => void)()
    }
    return { applySearch }
  },
  template:
    '<div class="filter-harness"><button class="preset-search" @click="applySearch">搜索</button><button class="preset-reset" @click="handleReset()">重置</button></div>',
})

const AlertTablePanelHarness = defineComponent({
  props: {
    alerts: { type: Array, default: () => [] },
    openDetail: { type: Function, required: true },
  },
  template:
    '<div class="table-harness"><button class="open-first" @click="alerts.length && openDetail(alerts[0])">打开第一条</button></div>',
})

const AlertDetailPanelHarness = defineComponent({
  props: {
    activeRecord: { type: Object, default: null },
    statusRemarkDraft: { type: String, default: '' },
    canOpenBusinessEntry: { type: Function, required: true },
    openBusinessEntry: { type: Function, required: true },
    openSubscriptionModal: { type: Function, required: true },
    handleSaveActiveResult: { type: Function, required: true },
  },
  emits: ['update:statusRemarkDraft'],
  template: `
    <div class="detail-harness">
      <div class="detail-record-id">{{ activeRecord?.id ?? 'EMPTY' }}</div>
      <div class="business-visible">{{ activeRecord ? String(canOpenBusinessEntry(activeRecord)) : 'false' }}</div>
      <button class="update-remark" @click="$emit('update:statusRemarkDraft', '  已处理备注  ')">备注</button>
      <button class="save-active" @click="handleSaveActiveResult()">保存</button>
      <button class="open-subscription" @click="openSubscriptionModal()">订阅</button>
      <button v-if="activeRecord && canOpenBusinessEntry(activeRecord)" class="open-business-entry" @click="openBusinessEntry(activeRecord)">业务单据</button>
    </div>
  `,
})

const AlertSubscriptionModalHarness = defineComponent({
  props: {
    open: { type: Boolean, default: false },
    form: { type: Object, required: true },
    availableSubscriptionChannels: { type: Array, default: () => [] },
    availableSubscriptionDomains: { type: Array, default: () => [] },
    availableSeverityOptions: { type: Array, default: () => [] },
    handleSaveSubscription: { type: Function, required: true },
  },
  emits: ['update:open'],
  setup(props, { emit }) {
    const tweakForm = () => {
      const form = props.form as {
        enabled: boolean
        channels: string[]
        domains: string[]
        minSeverity: string
        notifyOnStatusChanged: boolean
      }
      form.enabled = true
      form.channels = ['IN_APP', 'EMAIL']
      form.domains = ['PURCHASE', 'CONTRACT']
      form.minSeverity = 'LOW'
      form.notifyOnStatusChanged = true
    }
    const close = () => emit('update:open', false)
    return { tweakForm, close }
  },
  template: `
    <div class="subscription-harness">
      <div class="modal-open">{{ String(open) }}</div>
      <button class="tweak-form" @click="tweakForm">调整表单</button>
      <button class="save-subscription" @click="handleSaveSubscription()">保存订阅</button>
      <button class="close-subscription" @click="close">关闭订阅</button>
    </div>
  `,
})

function createGlobalStubs(overrides: Record<string, unknown> = {}) {
  return {
    ColumnSettingsButton: { template: '<button class="column-settings-stub">列设置</button>' },
    LgEmptyState: { template: '<div class="lg-empty-state-stub"><slot /></div>' },
    VxeGrid: VxeGridStub,
    VxeGridInstance: true,
    VxeColumn: true,
    'a-card': { template: '<div class="stub-card"><slot /></div>' },
    'a-input': AInputStub,
    'a-select': ASelectStub,
    'a-select-option': { template: '<option><slot /></option>' },
    'a-button': AButtonStub,
    'a-tag': ATagStub,
    'a-space': { template: '<div><slot /></div>' },
    'a-tooltip': { template: '<div><slot /></div>' },
    'a-pagination': APaginationStub,
    'a-badge': { template: '<span><slot /></span>' },
    'a-breadcrumb': { template: '<div><slot /></div>' },
    'a-breadcrumb-item': { template: '<span><slot /></span>' },
    'a-dropdown': { template: '<div><slot /><slot name="overlay" /></div>' },
    'a-menu': { template: '<div><slot /></div>' },
    'a-menu-item': { template: '<button><slot /></button>' },
    'a-result': { template: '<div class="a-result-stub"><slot /><slot name="extra" /></div>' },
    'a-range-picker': { template: '<div class="range-picker-stub"></div>' },
    'a-switch': ASwitchStub,
    'a-modal': AModalStub,
    'a-checkbox-group': { template: '<div class="a-checkbox-group-stub"><slot /></div>' },
    'a-checkbox': ACheckboxStub,
    'a-radio-group': { template: '<div class="a-radio-group-stub"><slot /></div>' },
    'a-radio-button': { template: '<button class="a-radio-button-stub"><slot /></button>' },
    'a-spin': { template: '<div class="a-spin-stub"><slot /></div>' },
    'a-textarea': ATextareaStub,
    SearchOutlined: true,
    ReloadOutlined: true,
    BellOutlined: true,
    FolderOpenOutlined: true,
    InboxOutlined: true,
    ThunderboltOutlined: true,
    WarningOutlined: true,
    ...overrides,
  }
}

function mountAlertPage(stubs: Record<string, unknown> = {}) {
  return mount(AlertPage, {
    global: {
      stubs: createGlobalStubs(stubs),
    },
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  mockAlertStore.alerts = []
  mockAlertStore.total = 0
  mockAlertStore.loading = false
  mockAlertStore.pageNo = 1
  mockAlertStore.pageSize = 20
  mockReferenceStore.projects = []
  mockUserStore.roles = ['PURCHASE_MANAGER']
  mockUserStore.hasPermission.mockImplementation(
    (code: string) => code === 'alert:view' || code === 'alert:edit',
  )
  mockRoute.path = '/alert'
  mockRoute.query = {}
  mockRoute.meta = {}
  mockGetAlertList.mockResolvedValue(createPagedAlertResponse([]))
  mockExportAlertAudit.mockResolvedValue(undefined)
  mockGetAlertSubscription.mockResolvedValue(buildSubscriptionResponse())
  mockUpdateAlertSubscription.mockResolvedValue(
    buildSubscriptionResponse({
      effectiveSubscription: {
        enabled: false,
        channels: ['IN_APP'],
        domains: ['PURCHASE'],
        minSeverity: 'MEDIUM',
        notifyOnStatusChanged: false,
      },
    }),
  )
  mockDownloadBlobFile.mockReset()
})

describe('alert/index.vue', () => {
  it('页面正常渲染并以默认查询参数拉取列表与订阅', async () => {
    mockAlertStore.alerts = [createAlertRecord()]
    mockAlertStore.total = 1

    const wrapper = mountAlertPage()
    await flushPromises()

    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain('仅看默认范围')
    expect(wrapper.text()).toContain('接收通知')
    expect(wrapper.text()).toContain('标记已读')
    expect(wrapper.text()).toContain('保存处理结果')
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

  it('父页真实挂载四个 alert 子组件并保持关键结构顺序', async () => {
    mockAlertStore.alerts = [createAlertRecord()]
    mockAlertStore.total = 1
    mockReferenceStore.projects = [{ id: 'P-01', projectCode: 'PRJ-01', projectName: '测试项目' }]

    const wrapper = mountAlertPage()
    await flushPromises()

    expect(wrapper.find('.alert-filter-panel').exists()).toBe(true)
    expect(wrapper.find('.alert-table-panel').exists()).toBe(true)
    expect(wrapper.find('.alert-detail-panel').exists()).toBe(true)
    expect(wrapper.find('.a-modal-stub').exists()).toBe(true)

    const filterActions = wrapper.findAll('.alert-filter-actions button').map((item) => item.text())
    const toolbarButtons = wrapper.findAll('.alert-toolbar-left button').map((item) => item.text())
    const detailButtons = wrapper.findAll('.alert-detail-actions button').map((item) => item.text())

    expect(filterActions).toEqual(['搜索', '重置'])
    expect(toolbarButtons).toEqual(['批量处理', '标记已读', '归档', '导出'])
    expect(detailButtons).toEqual(['标记已读', '归档', '查看业务单据', '保存处理结果'])
    expect(wrapper.find('.alert-filter-scope').text()).toContain('仅看默认范围')
    expect(wrapper.find('.alert-message-button').text()).toContain('采购订单逾期')
    expect(wrapper.find('.alert-subscription-header').text()).toContain('通知渠道')
    expect(wrapper.find('.a-modal-title').text()).toBe('通知订阅')
  })

  it('搜索条件仍通过编排层传给列表 API', async () => {
    const wrapper = mountAlertPage({
      AlertFilterPanel: AlertFilterPanelHarness,
      AlertTablePanel: AlertTablePanelHarness,
      AlertDetailPanel: AlertDetailPanelHarness,
      AlertSubscriptionModal: AlertSubscriptionModalHarness,
    })

    await flushPromises()
    mockAlertStore.fetchAlerts.mockClear()

    await wrapper.get('.preset-search').trigger('click')

    expect(mockAlertStore.fetchAlerts).toHaveBeenCalledWith(
      expect.objectContaining({
        keyword: '关键字',
        projectId: 'P-01',
        severity: 'HIGH',
        isRead: 0,
        processStatus: 'PROCESSED',
        alertDomain: 'PURCHASE',
        onlyDefaultScope: true,
        pageNo: 1,
        pageNum: 1,
        pageSize: 20,
      }),
    )
    expect(mockRouterReplace).toHaveBeenLastCalledWith({
      path: '/alert',
      query: expect.objectContaining({
        keyword: '关键字',
        projectId: 'P-01',
        severity: 'HIGH',
        isRead: '0',
        processStatus: 'PROCESSED',
        onlyDefaultScope: '1',
        pageNo: '1',
        pageSize: '20',
      }),
    })
  })

  it('表格详情、备注保存、订阅保存和业务单据跳转链路仍可达', async () => {
    mockAlertStore.alerts = [createAlertRecord()]
    mockAlertStore.total = 1

    const wrapper = mountAlertPage({
      AlertFilterPanel: AlertFilterPanelHarness,
      AlertTablePanel: AlertTablePanelHarness,
      AlertDetailPanel: AlertDetailPanelHarness,
      AlertSubscriptionModal: AlertSubscriptionModalHarness,
    })

    await flushPromises()

    expect(wrapper.get('.detail-record-id').text()).toBe('ALERT-001')

    await wrapper.get('.open-first').trigger('click')
    expect(wrapper.get('.detail-record-id').text()).toBe('ALERT-001')
    expect(wrapper.get('.business-visible').text()).toBe('true')

    await wrapper.get('.update-remark').trigger('click')
    await wrapper.get('.save-active').trigger('click')

    expect(mockAlertStore.changeStatus).toHaveBeenCalledWith('ALERT-001', 'PROCESSED', '已处理备注')

    await wrapper.get('.open-subscription').trigger('click')
    expect(wrapper.get('.modal-open').text()).toBe('true')

    await wrapper.get('.tweak-form').trigger('click')
    await wrapper.get('.save-subscription').trigger('click')
    await flushPromises()

    expect(mockUpdateAlertSubscription).toHaveBeenCalledWith({
      enabled: true,
      channels: ['IN_APP'],
      domains: ['PURCHASE'],
      minSeverity: 'LOW',
      notifyOnStatusChanged: true,
    })
    expect(wrapper.get('.modal-open').text()).toBe('false')

    await wrapper.get('.open-business-entry').trigger('click')
    expect(mockRouterPush).toHaveBeenCalledWith('/purchase/order?businessId=PO-9')
  })

  it('资金日记账逾期预警跳转到对应流水', async () => {
    mockAlertStore.alerts = [
      createAlertRecord({
        alertDomain: 'FINANCE',
        alertCategory: 'CASH_JOURNAL_CLOSURE',
        ruleType: 'CASH_JOURNAL_ARCHIVE_OVERDUE',
        sourceType: 'CASH_JOURNAL',
        sourceId: '101',
        businessType: 'CASH_JOURNAL',
        businessId: '101',
      }),
    ]
    mockAlertStore.total = 1

    const wrapper = mountAlertPage({
      AlertFilterPanel: AlertFilterPanelHarness,
      AlertTablePanel: AlertTablePanelHarness,
      AlertDetailPanel: AlertDetailPanelHarness,
      AlertSubscriptionModal: AlertSubscriptionModalHarness,
    })
    await flushPromises()
    await wrapper.get('.open-first').trigger('click')
    await wrapper.get('.open-business-entry').trigger('click')

    expect(mockRouterPush).toHaveBeenCalledWith('/cash-journal?entryId=101')
  })

  it('订阅弹窗展示与保存都会收敛到默认边界内，不放大渠道/域/严重度/状态变更范围', async () => {
    mockAlertStore.alerts = [createAlertRecord()]
    mockAlertStore.total = 1
    mockGetAlertSubscription.mockResolvedValue(
      buildSubscriptionResponse({
        defaultSubscription: {
          enabled: true,
          channels: ['IN_APP'],
          domains: ['PURCHASE'],
          minSeverity: 'MEDIUM',
          notifyOnStatusChanged: false,
        },
        effectiveSubscription: {
          enabled: true,
          channels: ['IN_APP', 'EMAIL', 'WECHAT', 'SMS'],
          domains: ['PURCHASE'],
          minSeverity: 'MEDIUM',
          notifyOnStatusChanged: false,
        },
        availableOptions: {
          channels: ['IN_APP', 'EMAIL', 'WECHAT', 'SMS'],
          domains: ['PURCHASE', 'CONTRACT'],
          minSeverityOptions: ['LOW', 'MEDIUM', 'HIGH'],
        },
      }),
    )

    const wrapper = mountAlertPage({
      AlertFilterPanel: AlertFilterPanelHarness,
      AlertTablePanel: AlertTablePanelHarness,
      AlertDetailPanel: AlertDetailPanelHarness,
      AlertSubscriptionModal: AlertSubscriptionModalHarness,
    })

    await flushPromises()

    const modal = wrapper.findComponent(AlertSubscriptionModalHarness)
    expect(modal.props('availableSubscriptionChannels')).toEqual(['IN_APP'])
    expect(modal.props('availableSubscriptionDomains')).toEqual(['PURCHASE'])
    expect(modal.props('availableSeverityOptions')).toEqual(['MEDIUM', 'HIGH'])

    await wrapper.get('.open-subscription').trigger('click')
    await wrapper.get('.tweak-form').trigger('click')
    await wrapper.get('.save-subscription').trigger('click')
    await flushPromises()

    expect(mockUpdateAlertSubscription).toHaveBeenCalledWith({
      enabled: true,
      channels: ['IN_APP'],
      domains: ['PURCHASE'],
      minSeverity: 'MEDIUM',
      notifyOnStatusChanged: false,
    })
  })

  it('订阅明细在订阅数据未返回前不回退展示占位渠道', async () => {
    mockAlertStore.alerts = [createAlertRecord()]
    mockAlertStore.total = 1
    let resolveSubscription: ((value: AlertSubscriptionResponse) => void) | null = null
    mockGetAlertSubscription.mockReturnValue(
      new Promise<AlertSubscriptionResponse>((resolve) => {
        resolveSubscription = resolve
      }),
    )

    const wrapper = mountAlertPage()

    const channelLines = wrapper.findAll('.alert-subscription-line').map((item) => item.text())

    expect(channelLines).toEqual([])
    expect(wrapper.text()).not.toContain('邮件')
    expect(wrapper.text()).not.toContain('企业微信')
    expect(wrapper.text()).not.toContain('短信')

    resolveSubscription?.(buildSubscriptionResponse())
    await flushPromises()

    expect(wrapper.findAll('.alert-subscription-line').map((item) => item.text())).toEqual([
      expect.stringContaining('站内信'),
    ])
    expect(alertPageSource).not.toContain("['IN_APP', 'EMAIL', 'WECHAT', 'SMS']")
  })

  it('订阅明细只展示默认允许的站内信，不展示占位渠道', async () => {
    mockAlertStore.alerts = [createAlertRecord()]
    mockAlertStore.total = 1
    mockGetAlertSubscription.mockResolvedValue(
      buildSubscriptionResponse({
        effectiveSubscription: {
          enabled: true,
          channels: ['IN_APP', 'EMAIL', 'WECHAT', 'SMS'],
          domains: ['PURCHASE'],
          minSeverity: 'LOW',
          notifyOnStatusChanged: true,
        },
        availableOptions: {
          domains: ['PURCHASE'],
          channels: ['IN_APP', 'EMAIL', 'WECHAT', 'SMS'],
          minSeverityOptions: ['LOW', 'MEDIUM', 'HIGH'],
        },
      }),
    )

    const wrapper = mountAlertPage()

    await flushPromises()

    expect(wrapper.findAll('.alert-subscription-line').map((item) => item.text())).toEqual([
      expect.stringContaining('站内信'),
    ])
    expect(wrapper.text()).not.toContain('邮件')
    expect(wrapper.text()).not.toContain('企业微信')
    expect(wrapper.text()).not.toContain('短信')
  })

  it('导出复用当前筛选条件跨页抓取全量结果，空列表时按钮禁用', async () => {
    mockAlertStore.alerts = [
      createAlertRecord({
        id: 'ALERT-001',
        projectId: 'P-01',
        alertDomain: 'PURCHASE',
        ruleType: 'CONTRACT_OVERDUE',
        alertCategory: 'PURCHASE_DELIVERY',
        severity: 'HIGH',
        processStatus: 'OPEN',
        message: '首条消息\n包含换行',
      }),
      createAlertRecord({
        id: 'ALERT-002',
        projectId: 'P-02',
        alertDomain: 'CONTRACT',
        ruleType: 'CONTRACT_EXPIRING',
        alertCategory: 'OTHER',
        severity: 'LOW',
        processStatus: 'ARCHIVED',
        isRead: 1,
        triggeredAt: '2026-07-08 09:00:00',
        message: '第二条消息',
      }),
    ]
    mockAlertStore.total = 2
    mockGetAlertList
      .mockResolvedValueOnce(
        createPagedAlertResponse(
          [
            createAlertRecord({
              id: 'ALERT-010',
              projectId: 'P-01',
              alertDomain: 'PURCHASE',
              ruleType: 'CONTRACT_OVERDUE',
              alertCategory: 'PURCHASE_DELIVERY',
              severity: 'HIGH',
              processStatus: 'OPEN',
              message: '首条导出消息\n包含换行',
            }),
          ],
          { total: 2, pageNo: 1, pageSize: 1 },
        ),
      )
      .mockResolvedValueOnce(
        createPagedAlertResponse(
          [
            createAlertRecord({
              id: 'ALERT-011',
              projectId: 'P-02',
              alertDomain: 'CONTRACT',
              ruleType: 'CONTRACT_EXPIRING',
              alertCategory: 'OTHER',
              severity: 'LOW',
              processStatus: 'ARCHIVED',
              isRead: 1,
              triggeredAt: '2026-07-08 09:00:00',
              message: '第二条导出消息',
            }),
          ],
          { total: 2, pageNo: 2, pageSize: 1 },
        ),
      )
    mockReferenceStore.projects = [
      { id: 'P-01', projectCode: 'PRJ-01', projectName: '测试项目一' },
      { id: 'P-02', projectCode: 'PRJ-02', projectName: '测试项目二' },
    ]

    const wrapper = mountAlertPage()
    await flushPromises()

    const exportButton = wrapper
      .findAll('.alert-toolbar-left button')
      .find((item) => item.text() === '导出')
    expect(exportButton).toBeTruthy()
    expect(exportButton!.attributes('disabled')).toBeUndefined()

    await exportButton!.trigger('click')
    await flushPromises()

    expect(mockGetAlertList).toHaveBeenCalledTimes(2)
    expect(mockGetAlertList).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({
        alertDomain: 'PURCHASE',
        onlyDefaultScope: true,
        pageNo: 1,
        pageNum: 1,
        pageSize: 200,
      }),
    )
    expect(mockGetAlertList).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        alertDomain: 'PURCHASE',
        onlyDefaultScope: true,
        pageNo: 2,
        pageNum: 2,
        pageSize: 1,
      }),
    )
    expect(mockDownloadBlobFile).toHaveBeenCalledTimes(1)
    expect(mockExportAlertAudit).toHaveBeenCalledTimes(1)
    expect(mockExportAlertAudit).toHaveBeenCalledWith({
      filterSignature: expect.stringMatching(/^alert-export-[a-f0-9]{1,19}$/),
      recordCount: 2,
    })
    expect(mockExportAlertAudit.mock.calls[0]?.[0]?.filterSignature).not.toContain('关键字')
    expect(mockExportAlertAudit.mock.calls[0]?.[0]?.filterSignature).not.toContain('首条导出消息')
    const [blob, filename] = mockDownloadBlobFile.mock.calls[0] as [Blob, string]
    const csv = await blob.text()
    expect(filename).toMatch(/^alerts-\d{4}-\d{2}-\d{2}\.csv$/)
    expect(csv).toContain('"告警ID","项目","规则域","规则类型","细分类","严重度","处理状态","已读","触发时间","消息摘要"')
    expect(csv).toContain('"ALERT-010","PRJ-01 测试项目一","采购类","合同超期","采购交付","高","待处理","未读","2026-07-07 10:00:00","首条导出消息 包含换行"')
    expect(csv).toContain('"ALERT-011","PRJ-02 测试项目二","合同类","合同到期","其他","低","已归档","已读","2026-07-08 09:00:00","第二条导出消息"')
    expect(mockMessage.success).toHaveBeenCalledWith('已导出当前筛选结果')

    mockAlertStore.alerts = []
    mockAlertStore.total = 0
    const emptyWrapper = mountAlertPage()
    await flushPromises()
    const emptyExportButton = emptyWrapper
      .findAll('.alert-toolbar-left button')
      .find((item) => item.text() === '导出')
    expect(emptyExportButton).toBeTruthy()
    expect(emptyExportButton!.attributes('disabled')).toBeDefined()
  })

  it('审计确认失败时不阻断已完成下载，并给出非阻塞提示', async () => {
    mockAlertStore.alerts = [createAlertRecord()]
    mockAlertStore.total = 1
    mockGetAlertList.mockResolvedValueOnce(
      createPagedAlertResponse([
        createAlertRecord({
          id: 'ALERT-020',
          message: '导出仍应成功',
        }),
      ]),
    )
    mockExportAlertAudit.mockRejectedValueOnce(new Error('audit failed'))
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})

    const wrapper = mountAlertPage()
    await flushPromises()

    const exportButton = wrapper
      .findAll('.alert-toolbar-left button')
      .find((item) => item.text() === '导出')
    expect(exportButton).toBeTruthy()

    await exportButton!.trigger('click')
    await flushPromises()

    expect(mockDownloadBlobFile).toHaveBeenCalledTimes(1)
    expect(mockMessage.success).toHaveBeenCalledWith('已导出当前筛选结果')
    expect(mockMessage.warning).toHaveBeenCalledWith('导出已完成，审计确认补记失败')
    expect(warnSpy).toHaveBeenCalledWith(
      'alert export audit confirm failed',
      expect.any(Error),
    )

    warnSpy.mockRestore()
  })

  it('导出总量超过阈值时提示收窄筛选，不继续请求后续分页也不下载', async () => {
    mockAlertStore.alerts = [
      createAlertRecord({
        id: 'ALERT-001',
        projectId: 'P-01',
        alertDomain: 'PURCHASE',
        ruleType: 'CONTRACT_OVERDUE',
      }),
    ]
    mockAlertStore.total = 1001
    const wrapper = mountAlertPage()
    await flushPromises()

    const exportButton = wrapper
      .findAll('.alert-toolbar-left button')
      .find((item) => item.text() === '导出')
    expect(exportButton).toBeTruthy()
    expect(exportButton!.attributes('disabled')).toBeUndefined()

    await exportButton!.trigger('click')
    await flushPromises()

    expect(mockGetAlertList).not.toHaveBeenCalled()
    expect(mockDownloadBlobFile).not.toHaveBeenCalled()
    expect(mockMessage.warning).toHaveBeenCalledWith('导出条数过多，请先收窄筛选条件后重试')
    expect(mockMessage.success).not.toHaveBeenCalled()
  })

  it('导出时以第一页真实 total 为准做阈值保护，不继续请求后续分页也不下载', async () => {
    mockAlertStore.alerts = [
      createAlertRecord({
        id: 'ALERT-001',
        projectId: 'P-01',
        alertDomain: 'PURCHASE',
        ruleType: 'CONTRACT_OVERDUE',
      }),
    ]
    mockAlertStore.total = 2
    mockGetAlertList.mockResolvedValueOnce(
      createPagedAlertResponse(
        [
          createAlertRecord({
            id: 'ALERT-010',
            projectId: 'P-01',
            alertDomain: 'PURCHASE',
            ruleType: 'CONTRACT_OVERDUE',
          }),
        ],
        { total: 1001, pageNo: 1, pageSize: 200 },
      ),
    )

    const wrapper = mountAlertPage()
    await flushPromises()

    const exportButton = wrapper
      .findAll('.alert-toolbar-left button')
      .find((item) => item.text() === '导出')
    expect(exportButton).toBeTruthy()
    expect(exportButton!.attributes('disabled')).toBeUndefined()

    await exportButton!.trigger('click')
    await flushPromises()

    expect(mockGetAlertList).toHaveBeenCalledTimes(1)
    expect(mockGetAlertList).toHaveBeenCalledWith(
      expect.objectContaining({
        pageNo: 1,
        pageNum: 1,
        pageSize: 200,
      }),
    )
    expect(mockDownloadBlobFile).not.toHaveBeenCalled()
    expect(mockMessage.warning).toHaveBeenCalledWith('导出条数过多，请先收窄筛选条件后重试')
    expect(mockMessage.success).not.toHaveBeenCalled()
  })

  it('入口文件只保留编排，关键结构拆到子组件', () => {
    const pageSource = readFileSync(resolve(__dirname, '../index.vue'), 'utf-8')
    const filterPanelSource = readFileSync(
      resolve(__dirname, '../components/AlertFilterPanel.vue'),
      'utf-8',
    )
    const tablePanelSource = readFileSync(
      resolve(__dirname, '../components/AlertTablePanel.vue'),
      'utf-8',
    )
    const detailPanelSource = readFileSync(
      resolve(__dirname, '../components/AlertDetailPanel.vue'),
      'utf-8',
    )
    const subscriptionModalSource = readFileSync(
      resolve(__dirname, '../components/AlertSubscriptionModal.vue'),
      'utf-8',
    )

    expect(pageSource).toContain('<AlertFilterPanel')
    expect(pageSource).toContain('<AlertTablePanel')
    expect(pageSource).toContain('<AlertDetailPanel')
    expect(pageSource).toContain('<AlertSubscriptionModal')
    expect(pageSource).toContain('onlyDefaultScope')
    expect(pageSource).toContain('processStatus')
    expect(pageSource).toContain('triggeredAtRange')
    expect(pageSource).toContain('alertDomain')
    expect(pageSource).toContain('ruleType')
    expect(pageSource).toContain(
      "import { readPositiveIntQuery, readStringQuery, replaceListQuery } from '@/composables/listPageQuery'",
    )
    expect(pageSource).toContain('const route = useRoute()')
    expect(pageSource).toContain('const hasLoaded = ref(false)')
    expect(pageSource).toContain('const listError = ref<string | null>(null)')
    expect(pageSource).toContain('function hydrateFromRouteQuery()')
    expect(pageSource).toContain('async function syncRouteQuery()')
    expect(pageSource).toContain('await router.replace({ path: route.path, query: nextQuery })')
    expect(pageSource).toContain('pageNo: pageNo.value')
    expect(pageSource).toContain('pageSize: pageSize.value')
    expect(pageSource).toContain('record.bizType ?? record.businessType ?? record.sourceType')
    expect(pageSource).toContain('record.bizId ?? record.businessId ?? record.sourceId')
    expect(pageSource).toContain('buildAlertBusinessPath(record)')
    expect(pageSource).toContain('resolveRoleDefaultPreset')
    expect(pageSource).toContain('resolveSearchAlertDomain')
    expect(pageSource).toContain('filter.onlyDefaultScope && preset.alertDomain')
    expect(pageSource).toContain("return { alertDomain: 'PURCHASE', onlyDefaultScope: true }")
    expect(pageSource).toContain('ALERT_PROCESS_STATUS_LABELS')
    expect(pageSource).toContain('getAlertTagLabel')
    expect(pageSource).toContain('getAlertSubscription')
    expect(pageSource).toContain('updateAlertSubscription')
    expect(pageSource).toContain('availableSubscriptionDomains')
    expect(pageSource).toContain('notifyOnStatusChanged')
    expect(pageSource).not.toContain('placeholder="预警分类"')
    expect(pageSource).not.toContain('placeholder="规则类型"')
    expect(pageSource).toContain("field: 'message'")
    expect(pageSource).toContain("slots: { default: 'message' }")
    expect(pageSource).toContain("field: 'triggeredAt'")
    expect(pageSource).toContain("slots: { default: 'triggeredAt' }")
    expect(pageSource).toContain('@media (max-width: 1200px)')
    expect(pageSource).not.toContain('@media (max-width: 1440px)')
    expect(pageSource).toContain('grid-template-columns: minmax(0, 1fr) 360px')

    expect(filterPanelSource).toContain('仅看默认范围')
    expect(filterPanelSource).toContain('告警ID/消息摘要/业务单据号')
    expect(filterPanelSource).toContain('.alert-filter-scope')

    expect(tablePanelSource).toContain('标记已读')
    expect(tablePanelSource).toContain("handleChangeStatus(row, 'PROCESSED')")
    expect(tablePanelSource).toContain('<template #triggeredAt="{ row }">')
    expect(tablePanelSource).toContain('class="alert-message-button"')
    expect(tablePanelSource).toContain('<a-result status="error" title="预警列表加载失败" :sub-title="listError">')
    expect(tablePanelSource).toContain('<LgEmptyState description="暂无符合条件的预警记录">')
    expect(tablePanelSource).toContain('<a-button type="primary" @click="handleRetry">重试</a-button>')
    expect(tablePanelSource).toContain('.alert-toolbar-left')
    expect(tablePanelSource).toContain('flex-wrap: wrap')

    expect(detailPanelSource).toContain('查看业务单据')
    expect(detailPanelSource).toContain('openBusinessEntry(activeRecord)')
    expect(detailPanelSource).toContain('保存处理结果')
    expect(detailPanelSource).toContain('通知订阅')
    expect(detailPanelSource).toContain('position: sticky')

    expect(subscriptionModalSource).toContain('接收通知')
    expect(subscriptionModalSource).toContain('最低严重度')
    expect(subscriptionModalSource).toContain('notifyOnStatusChanged')
  })
})

describe('alert 子组件 DOM/结构证据', () => {
  it('过滤面板保持关键 class、按钮顺序和默认范围区块', () => {
    const wrapper = mount(AlertFilterPanel, {
      props: {
        filter: reactive({
          keyword: '',
          projectId: undefined,
          alertDomain: 'PURCHASE',
          ruleType: undefined,
          severity: undefined,
          isRead: undefined,
          processStatus: undefined,
          triggeredAtRange: null,
          onlyDefaultScope: true,
        }),
        projectsLoading: false,
        projectOptions: [{ id: 'P-01', projectCode: 'PRJ-01', projectName: '测试项目' }],
        processStatusOptions: [{ value: 'OPEN', label: '待处理' }],
        hasDefaultScopeDomain: true,
        handleSearch: vi.fn(),
        handleReset: vi.fn(),
      },
      global: {
        stubs: createGlobalStubs(),
      },
    })

    const labels = wrapper
      .findAll('.alert-filter-grid .alert-filter-item > label')
      .map((item) => item.text())
    const actions = wrapper.findAll('.alert-filter-actions button').map((item) => item.text())

    expect(wrapper.find('.alert-filter-panel').exists()).toBe(true)
    expect(wrapper.find('.alert-filter-grid').exists()).toBe(true)
    expect(wrapper.find('.alert-filter-foot').exists()).toBe(true)
    expect(wrapper.find('.alert-filter-scope').exists()).toBe(true)
    expect(labels).toEqual(['项目', '严重度', '已读状态', '处理状态', '触发时间'])
    expect(actions).toEqual(['搜索', '重置'])
    expect(wrapper.find('input[placeholder="告警ID/消息摘要/业务单据号"]').exists()).toBe(true)
  })

  it('表格面板保持工具栏顺序、消息按钮和分页结构', () => {
    const wrapper = mount(AlertTablePanel, {
      props: {
        alerts: [createAlertRecord()],
        columnSettings: [{ key: 'message', label: '消息摘要' }],
        colVisible: { message: true },
        tableColumns: [{ field: 'message' }],
        loading: false,
        tableHeight: '400px',
        allPageSelected: false,
        pageSelectionIndeterminate: false,
        total: 1,
        pageNo: 1,
        pageSize: 20,
        selectedCount: 1,
        listError: null,
        showEmptyState: false,
        hasActiveFilters: false,
        exportDisabled: false,
        toggleCol: vi.fn(),
        togglePageSelection: vi.fn(),
        isRowSelected: vi.fn(() => false),
        toggleRowSelection: vi.fn(),
        openDetail: vi.fn(),
        getProjectName: vi.fn(() => 'PRJ-01 测试项目'),
        getAlertDomainLabel: vi.fn(() => '采购'),
        getAlertTagLabel: vi.fn(() => '采购'),
        getProcessStatusLabel: vi.fn(() => '待处理'),
        formatSeverityText: vi.fn(() => 'HIGH'),
        formatDateTime: vi.fn(() => '2026-07-07 10:00:00'),
        getAlertMessageText: vi.fn(() => '采购订单逾期'),
        handleMarkRead: vi.fn(),
        handleChangeStatus: vi.fn(),
        handleBatchStatus: vi.fn(),
        handleBatchMarkRead: vi.fn(),
        handlePageChange: vi.fn(),
        handlePageSizeChange: vi.fn(),
        handleReset: vi.fn(),
        handleRetry: vi.fn(),
        exportCurrentView: vi.fn(),
        canManageAlerts: true,
        canExportAlerts: true,
      },
      global: {
        stubs: createGlobalStubs(),
      },
    })

    const toolbarButtons = wrapper.findAll('.alert-toolbar-left button').map((item) => item.text())
    const actionButtons = wrapper.findAll('.alert-row-actions button').map((item) => item.text())

    expect(wrapper.find('.alert-table-panel').exists()).toBe(true)
    expect(wrapper.find('.alert-toolbar-left').exists()).toBe(true)
    expect(wrapper.find('.alert-message-button').exists()).toBe(true)
    expect(wrapper.find('.alert-pagination').exists()).toBe(true)
    expect(wrapper.find('.alert-grid-wrap').exists()).toBe(true)
    expect(toolbarButtons).toEqual(['批量处理', '标记已读', '归档', '导出'])
    expect(actionButtons).toEqual(['标记已读', '处理', '归档', '详情'])
  })

  it('无编辑/导出权限时隐藏预警批量、导出和行级写操作入口', () => {
    const wrapper = mount(AlertTablePanel, {
      props: {
        alerts: [createAlertRecord()],
        columnSettings: [{ key: 'message', label: '消息摘要' }],
        colVisible: { message: true },
        tableColumns: [{ field: 'message' }],
        loading: false,
        tableHeight: '400px',
        allPageSelected: false,
        pageSelectionIndeterminate: false,
        total: 1,
        pageNo: 1,
        pageSize: 20,
        selectedCount: 1,
        listError: null,
        showEmptyState: false,
        hasActiveFilters: false,
        exportDisabled: false,
        toggleCol: vi.fn(),
        togglePageSelection: vi.fn(),
        isRowSelected: vi.fn(() => false),
        toggleRowSelection: vi.fn(),
        openDetail: vi.fn(),
        getProjectName: vi.fn(() => 'PRJ-01 测试项目'),
        getAlertDomainLabel: vi.fn(() => '采购'),
        getAlertTagLabel: vi.fn(() => '采购'),
        getProcessStatusLabel: vi.fn(() => '待处理'),
        formatSeverityText: vi.fn(() => 'HIGH'),
        formatDateTime: vi.fn(() => '2026-07-07 10:00:00'),
        getAlertMessageText: vi.fn(() => '采购订单逾期'),
        handleMarkRead: vi.fn(),
        handleChangeStatus: vi.fn(),
        handleBatchStatus: vi.fn(),
        handleBatchMarkRead: vi.fn(),
        handlePageChange: vi.fn(),
        handlePageSizeChange: vi.fn(),
        handleReset: vi.fn(),
        handleRetry: vi.fn(),
        exportCurrentView: vi.fn(),
        canManageAlerts: false,
        canExportAlerts: false,
      },
      global: {
        stubs: createGlobalStubs(),
      },
    })

    const toolbarButtons = wrapper.findAll('.alert-toolbar-left button').map((item) => item.text())
    const actionButtons = wrapper.findAll('.alert-row-actions button').map((item) => item.text())

    expect(toolbarButtons).toEqual([])
    expect(actionButtons).toEqual(['详情'])
    expect(wrapper.find('.alert-toolbar-meta').exists()).toBe(false)
  })

  it('导出使用共享下载 helper，避免浏览器点击后无下载事件', () => {
    expect(downloadUtilSource).toContain('document.body.appendChild(link)')
    expect(downloadUtilSource).toContain('setTimeout(() => URL.revokeObjectURL(url), 1000)')
    expect(alertPageSource).toContain("import { downloadBlobFile } from '@/utils/download'")
    expect(alertPageSource).toContain('const EXPORT_MAX_RECORDS = 1000')
    expect(alertPageSource).toContain("getAlertList(buildAlertListParams(1, 200))")
    expect(alertPageSource).toContain('downloadBlobFile(blob, `alerts-${new Date().toISOString().slice(0, 10)}.csv`)')
    expect(alertPageSource).toContain('buildAlertExportFilterSignature')
    expect(alertPageSource).toContain("keyword=${params.keyword ? 'present' : 'empty'}")
    expect(alertPageSource).toContain('void confirmExportAudit(exportParams, exportAlerts.length)')
  })

  it('详情面板保持详情/订阅结构，备注双向更新和业务按钮条件', async () => {
    const handleMarkRead = vi.fn()
    const handleChangeStatus = vi.fn()
    const openBusinessEntry = vi.fn()
    const openSubscriptionModal = vi.fn()
    const handleSaveActiveResult = vi.fn()

    const wrapper = mount(AlertDetailPanel, {
      props: {
        activeRecord: createAlertRecord(),
        statusRemarkDraft: '',
        currentOperator: '测试用户',
        subscriptionRows: [{ channel: 'IN_APP', label: '站内', enabled: true, minSeverity: 'LOW' }],
        subscriptionSummaryText: '站内通知',
        formatSeverityText: (value: string) => value,
        formatDateTime: (value: unknown) => String(value || '-'),
        getAlertDomainLabel: () => '采购',
        getProjectName: () => 'PRJ-01 测试项目',
        getAlertMessageText: (value: unknown) => String(value),
        getProcessStatusLabel: () => '待处理',
        openSubscriptionModal,
        handleMarkRead,
        handleChangeStatus,
        canOpenBusinessEntry: () => true,
        openBusinessEntry,
        handleSaveActiveResult,
      },
      global: {
        stubs: createGlobalStubs(),
      },
    })

    const sectionTitles = wrapper.findAll('.alert-section-title').map((item) => item.text())
    const actionButtons = wrapper.findAll('.alert-detail-actions button').map((item) => item.text())

    expect(wrapper.find('.alert-detail-panel').exists()).toBe(true)
    expect(sectionTitles).toEqual(['基本信息', '告警内容', '状态备注', '处理信息', '通知订阅'])
    expect(wrapper.find('.alert-subscription-header').exists()).toBe(true)
    expect(wrapper.findAll('.alert-subscription-header span').map((item) => item.text())).toEqual([
      '通知渠道',
      '是否启用',
      '最低严重度',
    ])
    expect(actionButtons).toEqual(['标记已读', '归档', '查看业务单据', '保存处理结果'])

    await wrapper.get('textarea').setValue('新的备注')
    expect(wrapper.emitted('update:statusRemarkDraft')).toEqual([['新的备注']])

    await wrapper.get('.alert-detail-actions button:nth-child(3)').trigger('click')
    expect(openBusinessEntry).toHaveBeenCalledTimes(1)
  })

  it('详情面板在不可跳转记录上保持隐藏业务单据入口', () => {
    const wrapper = mount(AlertDetailPanel, {
      props: {
        activeRecord: createAlertRecord({
          sourceType: '',
          sourceId: '',
          businessType: '',
          businessId: '',
          contractId: '',
        }),
        statusRemarkDraft: '',
        currentOperator: '测试用户',
        subscriptionRows: [],
        formatSeverityText: (value: string) => value,
        formatDateTime: (value: unknown) => String(value || '-'),
        getAlertDomainLabel: () => '采购',
        getProjectName: () => 'PRJ-01 测试项目',
        getAlertMessageText: (value: unknown) => String(value),
        getProcessStatusLabel: () => '待处理',
        openSubscriptionModal: vi.fn(),
        handleMarkRead: vi.fn(),
        handleChangeStatus: vi.fn(),
        canOpenBusinessEntry: () => false,
        openBusinessEntry: vi.fn(),
        handleSaveActiveResult: vi.fn(),
      },
      global: {
        stubs: createGlobalStubs(),
      },
    })

    expect(wrapper.text()).not.toContain('查看业务单据')
  })

  it('订阅弹窗保持表单结构并触发保存事件', async () => {
    const handleSaveSubscription = vi.fn()
    const form = reactive({
      enabled: true,
      channels: ['IN_APP', 'EMAIL', 'WECHAT', 'SMS'],
      domains: ['PURCHASE'],
      minSeverity: 'LOW',
      notifyOnStatusChanged: true,
    })

    const wrapper = mount(AlertSubscriptionModal, {
      props: {
        open: true,
        loading: false,
        saving: false,
        form,
        availableSubscriptionChannels: ['IN_APP'],
        availableSubscriptionDomains: ['PURCHASE'],
        availableSeverityOptions: ['LOW', 'MEDIUM', 'HIGH'],
        defaultSubscriptionEnabled: true,
        defaultStatusChangeEnabled: true,
        handleSaveSubscription,
      },
      global: {
        stubs: createGlobalStubs(),
      },
    })

    expect(wrapper.find('.alert-subscription-form').exists()).toBe(true)
    expect(wrapper.findAll('.alert-subscription-row')).toHaveLength(5)
    expect(wrapper.findAll('.alert-subscription-row.is-block')).toHaveLength(3)
    expect(wrapper.text()).toContain('接收通知')
    expect(wrapper.text()).toContain('通知渠道')
    expect(wrapper.text()).toContain('站内信')
    expect(wrapper.text()).not.toContain('邮件')
    expect(wrapper.text()).not.toContain('企业微信')
    expect(wrapper.text()).not.toContain('短信')
    expect(wrapper.text()).toContain('预警域')
    expect(wrapper.text()).toContain('最低严重度')
    expect(wrapper.text()).toContain('状态变更通知')

    await wrapper.get('.modal-ok').trigger('click')
    expect(handleSaveSubscription).toHaveBeenCalledTimes(1)
  })
})
