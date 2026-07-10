import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { computed, defineComponent, h } from 'vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../task.vue'), 'utf-8')
const configSource = readFileSync(resolve(currentDir, '../pageConfig.ts'), 'utf-8')
const originalTimezone = process.env.TZ

const { mockGetSubTaskList, mockRouterReplace, mockFetchProjects, mockFetchContracts, mockFetchPartners } =
  vi.hoisted(() => ({
    mockGetSubTaskList: vi.fn(),
    mockRouterReplace: vi.fn(),
    mockFetchProjects: vi.fn(),
    mockFetchContracts: vi.fn(),
    mockFetchPartners: vi.fn(),
  }))

vi.mock('@/api/modules/subcontract', () => ({
  getSubTaskList: mockGetSubTaskList,
  createSubTask: vi.fn(),
  updateSubTask: vi.fn(),
  deleteSubTask: vi.fn(),
}))

vi.mock('@/stores/reference', () => ({
  useReferenceStore: () => ({
    projects: [],
    contracts: [],
    fetchProjects: mockFetchProjects,
    fetchContracts: mockFetchContracts,
    fetchPartners: mockFetchPartners,
  }),
}))

vi.mock('pinia', () => ({
  storeToRefs: () => ({
    projects: computed(() => []),
    contracts: computed(() => []),
  }),
}))

vi.mock('@/composables/useColumnSettings', () => ({
  useColumnSettings: () => ({
    visibleColumns: computed(() => []),
    columnSettings: [],
    colVisible: {},
    toggleCol: vi.fn(),
  }),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/subcontract/task', query: {} }),
  useRouter: () => ({ replace: mockRouterReplace }),
}))

vi.mock('ant-design-vue', async () => {
  const actual = await vi.importActual('ant-design-vue')
  return {
    ...actual,
    message: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    Modal: { confirm: vi.fn() },
  }
})

import SubcontractTaskPage from '../task.vue'

const BasicStub = defineComponent({
  name: 'BasicStub',
  setup(_, { slots }) {
    return () => h('div', slots.default?.())
  },
})

const ButtonStub = defineComponent({
  name: 'ButtonStub',
  emits: ['click'],
  setup(_, { emit, slots }) {
    return () => h('button', { type: 'button', onClick: () => emit('click') }, slots.default?.())
  },
})

const ProgressStub = defineComponent({
  name: 'ProgressStub',
  props: { percent: { type: Number, default: 0 } },
  setup(props) {
    return () => h('span', { class: 'stub-progress' }, `${props.percent}%`)
  },
})

const WbsStubs = {
  'a-breadcrumb': BasicStub,
  'a-breadcrumb-item': BasicStub,
  'a-input': BasicStub,
  'a-select': BasicStub,
  'a-select-option': BasicStub,
  'a-button': ButtonStub,
  'a-tag': BasicStub,
  'a-dropdown': BasicStub,
  'a-menu': BasicStub,
  'a-menu-item': BasicStub,
  'a-pagination': BasicStub,
  'a-modal': BasicStub,
  'a-form': BasicStub,
  'a-form-item': BasicStub,
  'a-date-picker': BasicStub,
  'a-input-number': BasicStub,
  'a-textarea': BasicStub,
  'a-result': BasicStub,
  'a-progress': ProgressStub,
  'vxe-grid': BasicStub,
  ColumnSettingsButton: BasicStub,
  LgEmptyState: BasicStub,
  CheckCircleOutlined: BasicStub,
  ClockCircleOutlined: BasicStub,
  FileDoneOutlined: BasicStub,
  MoreOutlined: BasicStub,
  PauseCircleOutlined: BasicStub,
  PlusOutlined: BasicStub,
  ReloadOutlined: BasicStub,
  SearchOutlined: BasicStub,
  SyncOutlined: BasicStub,
}

describe('subcontract task page quality guardrails', () => {
  beforeAll(() => {
    process.env.TZ = 'America/Los_Angeles'
  })

  afterAll(() => {
    if (originalTimezone === undefined) delete process.env.TZ
    else process.env.TZ = originalTimezone
  })

  beforeEach(() => {
    vi.clearAllMocks()
    mockRouterReplace.mockResolvedValue(undefined)
    mockFetchProjects.mockResolvedValue(undefined)
    mockFetchContracts.mockResolvedValue(undefined)
    mockFetchPartners.mockResolvedValue(undefined)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders read-only WBS/gantt rows and empty fallback from API data', async () => {
    mockGetSubTaskList
      .mockResolvedValueOnce({
        records: [
          {
            id: '1',
            taskCode: '1.1',
            taskName: '土方开挖',
            plannedStartDate: '2026-07-01',
            plannedEndDate: '2026-07-10',
            actualStartDate: '2026-07-02',
            actualEndDate: '2026-07-09',
            progressPercent: '35.50',
            status: 'IN_PROGRESS',
          },
          {
            id: '2',
            taskCode: '1.2',
            taskName: '支护施工',
            actualStartDate: '2026-07-11',
            progressPercent: '0',
            status: 'NOT_STARTED',
          },
          {
            id: '3',
            taskCode: '1.3',
            taskName: '反向计划样例',
            plannedStartDate: '2026-08-10',
            plannedEndDate: '2026-08-01',
            actualStartDate: '2026-08-03',
            actualEndDate: '2026-08-08',
            progressPercent: '80',
            status: 'SUSPENDED',
          },
        ],
        total: 3,
      })
      .mockResolvedValueOnce({ records: [], total: 0 })

    const wrapper = mount(SubcontractTaskPage, { global: { stubs: WbsStubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('1.1')
    expect(wrapper.text()).toContain('土方开挖')
    expect(wrapper.text()).toContain('计划：2026-07-01 ~ 2026-07-10')
    expect(wrapper.text()).toContain('实际：2026-07-02 ~ 2026-07-09')
    expect(wrapper.text()).toContain('35.5%')
    expect(wrapper.text()).toContain('进行中')
    expect(wrapper.text()).toContain('1.2')
    expect(wrapper.text()).toContain('支护施工')
    expect(wrapper.text()).toContain('未设置计划日期')
    expect(wrapper.text()).toContain('1.3')
    expect(wrapper.text()).toContain('反向计划样例')
    expect(wrapper.text()).toContain('计划：2026-08-10 ~ 2026-08-01')
    expect(wrapper.text()).toContain('已暂停')

    await wrapper.findAll('button').find((button) => button.text().includes('刷新'))!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('暂无任务可生成 WBS/甘特概览')
  })

  it('marks only overdue unfinished WBS rows as delayed with a controlled clock', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 6, 15, 23, 30))
    mockGetSubTaskList.mockResolvedValue({
      records: [
        {
          id: 'overdue',
          taskCode: '2.1',
          taskName: '逾期未完成',
          plannedEndDate: '2026-07-14',
          progressPercent: '60',
          status: 'IN_PROGRESS',
        },
        {
          id: 'today',
          taskCode: '2.2',
          taskName: '今天到期',
          plannedEndDate: '2026-07-15',
          progressPercent: '60',
          status: 'IN_PROGRESS',
        },
        {
          id: 'actual-end',
          taskCode: '2.3',
          taskName: '已有实际完成日期',
          plannedEndDate: '2026-07-01',
          actualEndDate: '2026-07-02',
          progressPercent: '60',
          status: 'IN_PROGRESS',
        },
        {
          id: 'completed',
          taskCode: '2.4',
          taskName: '状态已完成',
          plannedEndDate: '2026-07-01',
          progressPercent: '60',
          status: 'COMPLETED',
        },
        {
          id: 'full-progress',
          taskCode: '2.5',
          taskName: '进度已完成',
          plannedEndDate: '2026-07-01',
          progressPercent: '100',
          status: 'IN_PROGRESS',
        },
        {
          id: 'no-plan',
          taskCode: '2.6',
          taskName: '无计划日期',
          progressPercent: '0',
          status: 'NOT_STARTED',
        },
      ],
      total: 6,
    })

    const wrapper = mount(SubcontractTaskPage, { global: { stubs: WbsStubs } })
    await flushPromises()

    const rows = wrapper.findAll('.subcontract-task-wbs-row')
    expect(rows).toHaveLength(6)
    expect(rows.find((row) => row.text().includes('逾期未完成'))!.text()).toContain('已延期')
    expect(rows.find((row) => row.text().includes('今天到期'))!.text()).not.toContain('已延期')
    expect(rows.find((row) => row.text().includes('已有实际完成日期'))!.text()).not.toContain('已延期')
    expect(rows.find((row) => row.text().includes('状态已完成'))!.text()).not.toContain('已延期')
    expect(rows.find((row) => row.text().includes('进度已完成'))!.text()).not.toContain('已延期')
    const noPlanRow = rows.find((row) => row.text().includes('无计划日期'))!
    expect(noPlanRow.text()).not.toContain('已延期')
    expect(noPlanRow.text()).toContain('未设置计划日期')
  })

  it('extracts static status and grid config out of the giant component', () => {
    expect(source).toContain("from './pageConfig'")
    expect(configSource).toContain('export const SUBCONTRACT_TASK_STATUS_LABEL')
    expect(configSource).toContain('export const SUBCONTRACT_TASK_STATUS_COLOR')
    expect(configSource).toContain('export const SUBCONTRACT_TASK_GRID_COLUMNS')
  })

  it('keeps filters and pagination in route query for refresh persistence', () => {
    expect(source).toContain("import { useRoute, useRouter } from 'vue-router'")
    expect(source).toContain('const route = useRoute()')
    expect(source).toContain('readStringQuery(route.query.projectId)')
    expect(source).toContain('readStringQuery(route.query.status)')
    expect(source).toContain('readPositiveIntQuery(route.query.pageNo, 1)')
    expect(source).toContain('await router.replace({ path: route.path, query: nextQuery })')
  })

  it('renders explicit error and empty states with retry entry', () => {
    expect(source).toContain('const listError = ref<string | null>(null)')
    expect(source).toContain('const hasLoaded = ref(false)')
    expect(source).toContain('<a-result status="error" title="分包任务列表加载失败" :sub-title="listError">')
    expect(source).toContain('<LgEmptyState description="暂无符合条件的分包任务">')
    expect(source).toContain('@click="fetchData"')
  })

  it('derives a read-only WBS and gantt overview from current table data', () => {
    expect(source).toContain('const wbsTimelineRows = computed(() => {')
    expect(source).toContain('[...tableData.value].sort')
    expect(source).toContain('按 WBS 编码排序的平铺展示')
    expect(source).toContain('项目内 WBS 树与只读甘特展示')
    expect(source).toContain('暂无任务可生成 WBS/甘特概览')
  })

  it('shows required WBS fields and degrades missing plan dates explicitly', () => {
    expect(source).toContain("{{ item.row.taskCode || '-' }}")
    expect(source).toContain("{{ item.row.taskName || '-' }}")
    expect(source).toContain("{{ item.row.plannedStartDate || '未设置计划日期' }}")
    expect(source).toContain("{{ item.row.actualStartDate || '-' }}")
    expect(source).toContain(":percent=\"parseFloat(item.row.progressPercent || '0') || 0\"")
    expect(source).toContain('STATUS_LABEL[item.row.status]')
  })

  it('keeps gantt scope minimal without drag, dependency lines, or gantt libraries', () => {
    expect(source).toContain('function clampPercent(value: number)')
    expect(source).toContain("left: item.left + '%'")
    expect(source).toContain("width: item.width + '%'")
    expect(source).not.toMatch(/dhtmlx|frappe-gantt|gantt-task-react|gantt-elastic/i)
    expect(source).not.toMatch(/draggable|dragstart|dependency|dependencies|linkLine|依赖线/)
  })
})
