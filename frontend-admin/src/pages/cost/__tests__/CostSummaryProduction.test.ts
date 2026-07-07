import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { computed, defineComponent, h } from 'vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../summary.vue'), 'utf-8')
const tablePanelSource = readFileSync(
  resolve(currentDir, '../components/CostSummaryTablePanel.vue'),
  'utf-8',
)
const analysisRailSource = readFileSync(
  resolve(currentDir, '../components/CostSummaryAnalysisRail.vue'),
  'utf-8',
)

const {
  mockGetProjectList,
  mockGetCostSummary,
  mockRefreshCostSummary,
  mockRouterPush,
  mockToggleCol,
} = vi.hoisted(() => ({
  mockGetProjectList: vi.fn(),
  mockGetCostSummary: vi.fn(),
  mockRefreshCostSummary: vi.fn(),
  mockRouterPush: vi.fn(),
  mockToggleCol: vi.fn(),
}))

vi.mock('@/api/modules/project', () => ({
  getProjectList: mockGetProjectList,
}))

vi.mock('@/api/modules/cost', () => ({
  getCostSummary: mockGetCostSummary,
  refreshCostSummary: mockRefreshCostSummary,
}))

vi.mock('@/composables/useColumnSettings', () => ({
  useColumnSettings: () => ({
    visibleColumns: computed(() => []),
    columnSettings: [{ key: 'costSubjectName', label: '成本科目' }],
    colVisible: { costSubjectName: true },
    toggleCol: mockToggleCol,
  }),
}))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRouter: () => ({
      push: mockRouterPush,
    }),
  }
})

vi.mock('ant-design-vue', async () => {
  const actual = await vi.importActual('ant-design-vue')
  return {
    ...actual,
    message: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
    },
  }
})

import CostSummaryPage from '../summary.vue'

const ASelectStub = defineComponent({
  name: 'ASelectStub',
  props: {
    value: { type: String, default: undefined },
  },
  emits: ['update:value', 'change'],
  setup(props, { emit, attrs, slots }) {
    return () =>
      h(
        'select',
        {
          class: ['stub-select', attrs.class],
          value: props.value,
          onChange: (event: Event) => {
            const nextValue = (event.target as HTMLSelectElement).value || undefined
            emit('update:value', nextValue)
            emit('change', nextValue)
          },
        },
        [h('option', { value: '' }, ''), slots.default?.()],
      )
  },
})

const ASelectOptionStub = defineComponent({
  name: 'ASelectOptionStub',
  props: {
    value: { type: String, required: true },
  },
  setup(props, { slots }) {
    return () => h('option', { value: props.value }, slots.default?.())
  },
})

const AButtonStub = defineComponent({
  name: 'AButtonStub',
  emits: ['click'],
  setup(_, { emit, attrs, slots }) {
    return () =>
      h(
        'button',
        {
          class: attrs.class,
          onClick: () => emit('click'),
        },
        slots.default?.(),
      )
  },
})

const AInputStub = defineComponent({
  name: 'AInputStub',
  props: {
    value: { type: String, default: '' },
  },
  emits: ['update:value', 'pressEnter'],
  setup(props, { emit, attrs, slots }) {
    return () =>
      h('div', { class: attrs.class }, [
        slots.prefix?.(),
        h('input', {
          class: 'stub-input',
          value: props.value,
          onInput: (event: Event) =>
            emit('update:value', (event.target as HTMLInputElement).value),
          onKeydown: (event: KeyboardEvent) => {
            if (event.key === 'Enter') emit('pressEnter')
          },
        }),
      ])
  },
})

const stubs = {
  'a-breadcrumb': { template: '<div><slot /></div>' },
  'a-breadcrumb-item': { template: '<span><slot /></span>' },
  'a-select': ASelectStub,
  'a-select-option': ASelectOptionStub,
  'a-input': AInputStub,
  'a-button': AButtonStub,
  'a-tag': { template: '<span class="stub-tag"><slot /></span>' },
  'a-empty': { template: '<div class="stub-empty">暂无科目明细</div>' },
  'a-spin': { template: '<div class="stub-spin"></div>' },
  'vxe-grid': { template: '<div class="stub-grid"></div>' },
  ColumnSettingsButton: { template: '<div class="stub-column-settings">列设置</div>' },
  CheckCircleOutlined: true,
  FileSearchOutlined: true,
  LinkOutlined: true,
  ReloadOutlined: true,
  WarningOutlined: true,
}

function createSummary() {
  return {
    projectName: '项目A',
    targetCost: '100000',
    contractLockedCost: '80000',
    actualCost: '76000',
    paidAmount: '50000',
    dynamicCost: '82000',
    costDeviation: '2000',
    subjects: [
      {
        costSubjectId: 's1',
        costSubjectName: '人工费',
        targetCost: '40000',
        contractLockedCost: '38000',
        actualCost: '36000',
        paidAmount: '25000',
        dynamicCost: '42000',
        costDeviation: '2000',
      },
    ],
  }
}

const originalInnerWidth = window.innerWidth
let wrappers: VueWrapper[] = []

function mountPage(width = 1280) {
  window.innerWidth = width
  const wrapper = mount(CostSummaryPage, {
    global: {
      stubs,
    },
  })
  wrappers.push(wrapper)
  return wrapper
}

describe('CostSummary production guards', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    wrappers = []
    mockGetProjectList.mockResolvedValue({
      records: [{ id: 'p1', projectName: '项目A' }],
      total: 1,
    })
    mockGetCostSummary.mockResolvedValue(createSummary())
    mockRefreshCostSummary.mockResolvedValue(createSummary())
  })

  afterEach(() => {
    for (const wrapper of wrappers) {
      wrapper.unmount()
    }
    wrappers = []
    window.innerWidth = originalInnerWidth
  })

  it('挂载时项目列表请求实际使用 pageNo 而不是 pageNum', async () => {
    mountPage()
    await flushPromises()

    expect(mockGetProjectList).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 50,
    })
    expect(mockGetProjectList.mock.calls[0][0]).not.toHaveProperty('pageNum')
  })

  it('移动端卡片真实渲染可读字段，且隐藏桌面列设置', async () => {
    const wrapper = mountPage(500)
    await flushPromises()

    await wrapper.find('select.stub-select').setValue('p1')
    await flushPromises()

    expect(mockGetCostSummary).toHaveBeenCalledWith('p1')
    expect(wrapper.find('.cost-summary-mobile-list').exists()).toBe(true)
    expect(wrapper.find('.cost-summary-table').exists()).toBe(false)
    expect(wrapper.find('.stub-column-settings').exists()).toBe(false)
    expect(wrapper.text()).toContain('人工费')
    expect(wrapper.text()).toContain('成本目标：4.00 万元')
    expect(wrapper.text()).toContain('动态成本：4.20 万元')
    expect(wrapper.text()).toContain('成本偏差：')
    expect(wrapper.text()).toContain('+0.20 万元')
  })

  it('桌面端保留列设置按钮，源码继续守住互斥结构和真实字段', async () => {
    const wrapper = mountPage(1280)
    await flushPromises()

    await wrapper.find('select.stub-select').setValue('p1')
    await flushPromises()

    expect(wrapper.find('.stub-column-settings').exists()).toBe(true)
    expect(wrapper.find('.cost-summary-mobile-list').exists()).toBe(false)
    expect(wrapper.find('.cost-summary-table').exists()).toBe(true)

    expect(source).toContain('const res = await getProjectList({ pageNo: 1, pageSize: 50 })')
    expect(source).not.toContain('getProjectList({ pageNum: 1, pageSize: 50 })')
    expect(source).toContain('<CostSummaryTablePanel')
    expect(tablePanelSource).toMatch(/<ColumnSettingsButton[\s\S]*v-if="!isMobile"/)
    expect(tablePanelSource).toMatch(/<div v-if="isMobile" class="cost-summary-mobile-list">/)
    expect(tablePanelSource).toMatch(/<div v-else class="lg-table-wrap cost-summary-table">/)
    expect(tablePanelSource).toContain("{{ row.costSubjectName || '-' }}")
    expect(tablePanelSource).toContain('成本目标：{{ fmtAmount(row.targetCost) }} 万元')
    expect(tablePanelSource).toContain('动态成本：{{ fmtAmount(row.dynamicCost) }} 万元')
    expect(tablePanelSource).toContain('{{ fmtDeviation(row.costDeviation) }} 万元')
  })

  it('入口页源码挂载本地表格面板和分析栏组件，避免模板重新膨胀', () => {
    expect(source).toContain("import CostSummaryTablePanel from './components/CostSummaryTablePanel.vue'")
    expect(source).toContain("import CostSummaryAnalysisRail from './components/CostSummaryAnalysisRail.vue'")
    expect(source).toContain('<CostSummaryTablePanel')
    expect(source).toContain('<CostSummaryAnalysisRail')
    expect(analysisRailSource).toContain('class="cost-source-rail-row"')
    expect(analysisRailSource).toContain("['cost-conclusion-row', `is-${item.tone}`]")
  })
})
