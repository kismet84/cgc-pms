import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { computed, defineComponent, h } from 'vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
const editSource = readFileSync(resolve(currentDir, '../edit.vue'), 'utf-8')
const analysisRailSource = readFileSync(
  resolve(currentDir, '../components/CostTargetAnalysisRail.vue'),
  'utf-8',
)
const routerSource = readFileSync(resolve(currentDir, '../../../router/index.ts'), 'utf-8')

const { mockGetCostTargetList, mockFetchProjects, mockToggleCol, mockModalConfirm } = vi.hoisted(
  () => ({
    mockGetCostTargetList: vi.fn(),
    mockFetchProjects: vi.fn().mockResolvedValue(undefined),
    mockToggleCol: vi.fn(),
    mockModalConfirm: vi.fn(),
  }),
)

vi.mock('@/api/modules/costTarget', () => ({
  getCostTargetList: mockGetCostTargetList,
  activateCostTarget: vi.fn(),
  deleteCostTarget: vi.fn(),
}))

vi.mock('@/stores/reference', () => ({
  useReferenceStore: () => ({
    projects: [{ id: 'p1', projectName: '项目A' }],
    fetchProjects: mockFetchProjects,
  }),
}))

vi.mock('@/composables/useColumnSettings', () => ({
  useColumnSettings: () => ({
    visibleColumns: computed(() => []),
    columnSettings: [{ key: 'versionNo', label: '版本号' }],
    colVisible: { versionNo: true },
    toggleCol: mockToggleCol,
  }),
}))

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
    Modal: {
      confirm: mockModalConfirm,
    },
  }
})

import CostTargetPage from '../index.vue'

const ASelectStub = defineComponent({
  name: 'ASelectStub',
  props: {
    value: { type: [String, Number], default: undefined },
  },
  emits: ['update:value', 'change'],
  setup(props, { emit, attrs, slots }) {
    return () =>
      h(
        'select',
        {
          class: ['stub-select', attrs.class],
          value: props.value as string | number | undefined,
          onChange: (event: Event) => {
            const nextValue = (event.target as HTMLSelectElement).value
            const normalized = nextValue === '' ? undefined : nextValue
            const finalValue =
              normalized === undefined
                ? undefined
                : attrs.class?.toString().includes('ct-search-select') &&
                    attrs.placeholder === '启用状态'
                  ? Number(normalized)
                  : normalized
            emit('update:value', finalValue)
            emit('change', finalValue)
          },
        },
        [h('option', { value: '' }, ''), slots.default?.()],
      )
  },
})

const ASelectOptionStub = defineComponent({
  name: 'ASelectOptionStub',
  props: {
    value: { type: [String, Number], required: true },
  },
  setup(props, { slots }) {
    return () => h('option', { value: String(props.value) }, slots.default?.())
  },
})

const AInputStub = defineComponent({
  name: 'AInputStub',
  props: {
    value: { type: String, default: '' },
  },
  emits: ['update:value', 'press-enter'],
  setup(props, { emit, attrs }) {
    return () =>
      h('input', {
        class: attrs.class,
        value: props.value,
        onInput: (event: Event) => emit('update:value', (event.target as HTMLInputElement).value),
        onKeyup: (event: KeyboardEvent) => {
          if (event.key === 'Enter') emit('press-enter')
        },
      })
  },
})

const AButtonStub = defineComponent({
  name: 'AButtonStub',
  emits: ['click'],
  setup(_, { emit, slots, attrs }) {
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

const AModalStub = defineComponent({
  name: 'AModalStub',
  props: {
    open: { type: Boolean, default: false },
  },
  setup(props, { slots }) {
    return () => (props.open ? h('div', { class: 'stub-modal' }, slots.default?.()) : null)
  },
})

const VxeGridStub = defineComponent({
  name: 'VxeGridStub',
  props: {
    data: { type: Array, default: () => [] },
  },
  setup(props, { slots }) {
    return () =>
      h(
        'div',
        { class: 'stub-grid' },
        (props.data as Array<Record<string, unknown>>).map((row) =>
          h('div', { class: 'stub-grid-row', 'data-row-id': String(row.id ?? '') }, [
            slots.ops?.({ row }),
          ]),
        ),
      )
  },
})

const stubs = {
  'a-breadcrumb': { template: '<div><slot /></div>' },
  'a-breadcrumb-item': { template: '<span><slot /></span>' },
  'a-input': AInputStub,
  'a-select': ASelectStub,
  'a-select-option': ASelectOptionStub,
  'a-button': AButtonStub,
  'a-tag': { template: '<span class="stub-tag"><slot /></span>' },
  'a-dropdown': { template: '<div><slot /><slot name="overlay" /></div>' },
  'a-menu': { template: '<div><slot /></div>' },
  'a-menu-item': { template: '<button><slot /></button>' },
  'a-pagination': { template: '<div class="stub-pagination"></div>' },
  'a-empty': { template: '<div class="stub-empty"><slot />暂无成本目标版本</div>' },
  'a-spin': { template: '<div class="stub-spin"></div>' },
  'a-modal': AModalStub,
  'vxe-grid': VxeGridStub,
  ColumnSettingsButton: { template: '<div class="stub-column-settings">列设置</div>' },
  CostTargetEditPage: {
    props: ['mode'],
    template: '<div class="stub-edit-page" :data-mode="mode"></div>',
  },
  ClockCircleOutlined: true,
  PlusOutlined: true,
  ReloadOutlined: true,
  CheckCircleOutlined: true,
  FileTextOutlined: true,
  SafetyCertificateOutlined: true,
  SearchOutlined: true,
  MoreOutlined: true,
  WarningOutlined: true,
}

function createRecord() {
  return {
    id: '1',
    projectId: 'p1',
    projectName: '项目A',
    versionNo: 'V1',
    versionName: '首版',
    totalTargetAmount: '100000',
    isActive: 1,
    approvalStatus: 'APPROVED',
    status: 'ACTIVE',
  }
}

function createInactiveApprovedRecord() {
  return {
    ...createRecord(),
    id: '2',
    versionNo: 'V2',
    versionName: '待切换版',
    isActive: 0,
  }
}

const originalInnerWidth = window.innerWidth
let wrappers: VueWrapper[] = []

function mountPage(width = 1280) {
  window.innerWidth = width
  const wrapper = mount(CostTargetPage, {
    global: {
      stubs,
    },
  })
  wrappers.push(wrapper)
  return wrapper
}

describe('CostTarget production guards', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    wrappers = []
    mockGetCostTargetList.mockResolvedValue({
      records: [createRecord(), createInactiveApprovedRecord()],
      total: 2,
    })
  })

  afterEach(() => {
    for (const wrapper of wrappers) {
      wrapper.unmount()
    }
    wrappers = []
    window.innerWidth = originalInnerWidth
  })

  it('approvalStatus 和 isActive 控件变更会触发重新查询', async () => {
    const wrapper = mountPage()
    await flushPromises()
    mockGetCostTargetList.mockClear()

    const selects = wrapper.findAll('select.stub-select')
    await selects[1].setValue('APPROVING')
    await flushPromises()
    expect(mockGetCostTargetList).toHaveBeenLastCalledWith(
      expect.objectContaining({
        pageNo: 1,
        approvalStatus: 'APPROVING',
      }),
    )

    await selects[2].setValue('0')
    await flushPromises()
    expect(mockGetCostTargetList).toHaveBeenLastCalledWith(
      expect.objectContaining({
        pageNo: 1,
        approvalStatus: 'APPROVING',
        isActive: 0,
      }),
    )
  })

  it('重置会清空 approvalStatus 和 isActive 后重新查询', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const selects = wrapper.findAll('select.stub-select')
    await selects[1].setValue('APPROVED')
    await selects[2].setValue('1')
    await flushPromises()
    mockGetCostTargetList.mockClear()

    const resetButton = wrapper.findAll('button').find((button) => button.text().includes('重置'))
    expect(resetButton).toBeTruthy()
    await resetButton!.trigger('click')
    await flushPromises()

    expect(mockGetCostTargetList).toHaveBeenCalledWith(
      expect.objectContaining({
        pageNo: 1,
        approvalStatus: undefined,
        isActive: undefined,
      }),
    )
  })

  it('移动端与桌面端互斥，且移动端隐藏列设置按钮', async () => {
    const mobileWrapper = mountPage(500)
    await flushPromises()

    expect(mobileWrapper.find('.ct-mobile-list').exists()).toBe(true)
    expect(mobileWrapper.find('.ct-table-wrap').exists()).toBe(false)
    expect(mobileWrapper.find('.stub-column-settings').exists()).toBe(false)
    expect(mobileWrapper.text()).toContain('V1')
    expect(mobileWrapper.text()).toContain('首版')
    expect(mobileWrapper.text()).toContain('当前启用')

    const desktopWrapper = mountPage(1280)
    await flushPromises()

    expect(desktopWrapper.find('.ct-mobile-list').exists()).toBe(false)
    expect(desktopWrapper.find('.ct-table-wrap').exists()).toBe(true)
    expect(desktopWrapper.find('.stub-column-settings').exists()).toBe(true)
  })

  it('源码继续守住查询绑定、互斥结构和移动端隐藏列设置', () => {
    expect(source).toMatch(/v-model:value="filter\.approvalStatus"[\s\S]*@change="handleSearch"/)
    expect(source).toMatch(/v-model:value="filter\.isActive"[\s\S]*@change="handleSearch"/)
    expect(source).toMatch(
      /function handleReset\(\) \{[\s\S]*filter\.approvalStatus = undefined[\s\S]*filter\.isActive = undefined[\s\S]*fetchData\(\)/,
    )
    expect(source).toMatch(/<ColumnSettingsButton[\s\S]*v-if="!isMobile"/)
    expect(source).toMatch(/<div v-if="isMobile" class="ct-mobile-list">/)
    expect(source).toMatch(/<div v-else class="lg-table-wrap ct-table-wrap">/)
  })

  it('右侧分析栏拆到页面私有组件后仍保留原 class 和刷新交互', () => {
    expect(source).toMatch(
      /import\s+CostTargetAnalysisRail\s+from\s+['"]\.\/components\/CostTargetAnalysisRail\.vue['"]/,
    )
    expect(source).toMatch(
      /<CostTargetAnalysisRail[\s\S]*:total="total"[\s\S]*:target-status-summary="targetStatusSummary"[\s\S]*:target-version-summary="targetVersionSummary"[\s\S]*:recent-targets="recentTargets"[\s\S]*@refresh="fetchData"/,
    )
    expect(analysisRailSource).toMatch(
      /<aside class="lg-analysis-rail ct-analysis-rail" aria-label="成本目标辅助分析">/,
    )
    expect(analysisRailSource).toMatch(
      /<a-button type="link" size="small" @click="emit\('refresh'\)">刷新<\/a-button>/,
    )
    expect(analysisRailSource).toMatch(/\.ct-analysis-panel\s*\{[\s\S]*display:\s*flex;/)
  })

  it('桌面表格继续使用纵向流式布局，避免工具栏和分页覆盖操作列', () => {
    expect(source).toMatch(
      /\.ct-main-column\s*\{[\s\S]*display:\s*flex;[\s\S]*flex-direction:\s*column;/,
    )
    expect(source).toMatch(
      /\.ct-table-panel\s*\{[\s\S]*display:\s*flex;[\s\S]*flex-direction:\s*column;[\s\S]*min-height:\s*0;/,
    )
    expect(source).toMatch(/\.ct-table-toolbar\s*\{[\s\S]*flex:\s*0 0 auto;/)
    expect(source).toMatch(
      /\.ct-table-wrap\s*\{[\s\S]*flex:\s*1 1 auto;[\s\S]*min-height:\s*0;[\s\S]*min-height:\s*520px;/,
    )
    expect(source).toMatch(/\.ct-table-panel > \.lg-pagination\s*\{[\s\S]*flex:\s*0 0 auto;/)
    expect(source).not.toMatch(/\.ct-table-toolbar\s*\{[\s\S]*position:\s*(absolute|fixed|sticky);/)
    expect(source).not.toMatch(
      /\.ct-table-panel > \.lg-pagination\s*\{[\s\S]*position:\s*(absolute|fixed|sticky);/,
    )
  })

  it('桌面操作列仍可触发查看、编辑和切换版本', async () => {
    const wrapper = mountPage(1280)
    await flushPromises()

    expect(wrapper.find('.stub-modal').exists()).toBe(false)

    const viewAction = wrapper
      .findAll('button')
      .find((button) => button.text().includes('查看详情'))
    expect(viewAction).toBeTruthy()
    await viewAction!.trigger('click')
    await flushPromises()
    expect(wrapper.find('.stub-modal').exists()).toBe(true)
    expect(wrapper.find('.stub-edit-page').exists()).toBe(true)
    expect(wrapper.find('.stub-edit-page').attributes('data-mode')).toBe('view')
    expect(mockModalConfirm).not.toHaveBeenCalled()

    const editAction = wrapper.findAll('button').find((button) => button.text().includes('编辑'))
    expect(editAction).toBeTruthy()
    await editAction!.trigger('click')
    await flushPromises()
    expect(wrapper.find('.stub-modal').exists()).toBe(true)
    expect(wrapper.find('.stub-edit-page').exists()).toBe(true)
    expect(wrapper.find('.stub-edit-page').attributes('data-mode')).toBe('edit')

    const activateAction = wrapper
      .findAll('button')
      .find((button) => button.text().includes('切换版本'))
    expect(activateAction).toBeTruthy()
    await activateAction!.trigger('click')
    expect(mockModalConfirm).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '确认切换版本？',
      }),
    )
  })

  it('路由权限映射仍然锁定为新的三个权限码', () => {
    expect(routerSource).toContain("CostLedger: 'cost:ledger:query'")
    expect(routerSource).toContain("CostSummary: 'cost:summary:view'")
    expect(routerSource).toContain("CostSubject: 'cost:query'")
  })

  it('源码保留桌面独立查看入口，并传递 view 模式', () => {
    expect(source).toMatch(/const targetModalMode = ref<'create' \| 'edit' \| 'view'>\('create'\)/)
    expect(source).toMatch(
      /function handleView\(row: CostTargetVO\) \{[\s\S]*targetModalMode\.value = 'view'/,
    )
    expect(source).toMatch(/<a-menu-item @click="handleView\(row\)">查看详情<\/a-menu-item>/)
    expect(source).toMatch(
      /<a-button type="link" class="ct-mobile-card-link" @click="handleView\(row\)">/,
    )
  })

  it('详情态源码必须严格只读，且不能走保存提交入口', () => {
    expect(editSource).toMatch(/mode\?: 'create' \| 'edit' \| 'view'/)
    expect(editSource).toMatch(/const isView = computed\(\(\) => props\.mode === 'view'\)/)
    expect(editSource).toMatch(/if \(isView\.value \|\| saving\.value\) return/)
    expect(editSource).toMatch(
      /const closeText = computed\(\(\) => \(isView\.value \? '关闭' : '取消'\)\)/,
    )
    expect(editSource).toMatch(
      /<a-button v-if="!isView" :loading="saving" @click="handleSave">保存<\/a-button>/,
    )
    expect(editSource).toMatch(
      /<a-button v-if="!isView" type="primary" :loading="saving && !submitting" @click="handleSubmit">/,
    )
    expect(editSource).toMatch(/<div v-if="!isView" class="cte-toolbar">/)
    expect(editSource).toMatch(/:disabled="isView"/)
    expect(editSource).toMatch(/pageTitle = computed\(\(\) => \{[\s\S]*'成本目标详情'/)
  })
})
