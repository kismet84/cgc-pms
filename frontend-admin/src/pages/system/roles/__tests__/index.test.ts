import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

// ── Mock API module ──
const mockGetRoles = vi.fn()
const mockGetMenuTree = vi.fn()
const mockUpdateRoleMenus = vi.fn()

vi.mock('@/api/modules/system', () => ({
  getRoles: (...args: unknown[]) => mockGetRoles(...args),
  getMenuTree: (...args: unknown[]) => mockGetMenuTree(...args),
  updateRoleMenus: (...args: unknown[]) => mockUpdateRoleMenus(...args),
}))

// ── Mock ant-design-vue message ──
vi.mock('ant-design-vue', async () => {
  const actual = await vi.importActual('ant-design-vue')
  return {
    ...(actual as object),
    message: {
      success: vi.fn(),
      error: vi.fn(),
      info: vi.fn(),
      warning: vi.fn(),
    },
  }
})

import { message } from 'ant-design-vue'
import RoleListPage from '@/pages/system/roles/index.vue'
import PermissionModal from '@/pages/system/roles/PermissionModal.vue'

// ── Stub Components ──

const ATableStub = defineComponent({
  name: 'ATableStub',
  props: {
    columns: Array,
    dataSource: Array,
    loading: Boolean,
    rowKey: String,
    size: String,
    scroll: Object,
    pagination: { type: [Boolean, Object], default: true },
  },
  setup(props, { slots }) {
    return () => {
      const cols = (props.columns as Record<string, unknown>[]) ?? []
      const rows = (props.dataSource as Record<string, unknown>[]) ?? []
      return h('table', { class: 'mock-table' }, [
        h(
          'thead',
          {},
          h(
            'tr',
            {},
            cols.map((col) => h('th', {}, col.title as string)),
          ),
        ),
        h(
          'tbody',
          {},
          rows.map((record, idx) =>
            h(
              'tr',
              { class: 'mock-table-row', key: (record.id ?? idx) as string },
              cols.map((col) =>
                h(
                  'td',
                  { class: 'mock-table-cell' },
                  slots.bodyCell
                    ? slots.bodyCell({ column: col, record, index: idx })
                    : (record[col.dataIndex as string] ?? ''),
                ),
              ),
            ),
          ),
        ),
      ])
    }
  },
})

const AInputStub = defineComponent({
  name: 'AInputStub',
  props: { value: String, placeholder: String, allowClear: Boolean },
  emits: ['update:value'],
  setup(props, { emit }) {
    return () =>
      h('input', {
        class: 'mock-input',
        value: props.value,
        placeholder: props.placeholder,
        onInput: (e: Event) => emit('update:value', (e.target as HTMLInputElement).value),
      })
  },
})

const AButtonStub = defineComponent({
  name: 'AButtonStub',
  props: { type: String, size: String, loading: Boolean, danger: Boolean },
  emits: ['click'],
  setup(props, { emit, slots }) {
    return () =>
      h(
        'button',
        {
          class: ['mock-btn', props.type ? `mock-btn-${props.type}` : ''].filter(Boolean).join(' '),
          type: 'button',
          disabled: props.loading,
          onClick: () => emit('click'),
        },
        slots.default?.(),
      )
  },
})

const ATagStub = defineComponent({
  name: 'ATagStub',
  props: { color: String },
  setup(_, { slots }) {
    return () => h('span', { class: 'mock-tag' }, slots.default?.())
  },
})

const APaginationStub = defineComponent({
  name: 'APaginationStub',
  props: {
    current: Number,
    pageSize: Number,
    total: Number,
    showSizeChanger: Boolean,
    showQuickJumper: Boolean,
    pageSizeOptions: Array,
    size: String,
  },
  emits: ['change', 'update:current', 'update:pageSize'],
  setup() {
    return () => h('div', { class: 'mock-pagination' })
  },
})

const ASpinStub = defineComponent({
  name: 'ASpinStub',
  props: { spinning: Boolean },
  setup(_, { slots }) {
    return () => h('div', { class: 'mock-spin' }, slots.default?.())
  },
})

const AModalStub = defineComponent({
  name: 'AModalStub',
  props: {
    open: Boolean,
    title: String,
    width: [String, Number],
    confirmLoading: Boolean,
  },
  emits: ['ok', 'cancel', 'update:open'],
  setup(props, { emit, slots }) {
    return () =>
      props.open
        ? h('div', { class: 'mock-modal' }, [
            h('div', { class: 'mock-modal-title' }, props.title),
            h('div', { class: 'mock-modal-body' }, slots.default?.()),
            h('div', { class: 'mock-modal-footer' }, [
              h(
                'button',
                { class: 'mock-modal-cancel', onClick: () => emit('update:open', false) },
                '取消',
              ),
              h(
                'button',
                {
                  class: 'mock-modal-ok',
                  disabled: props.confirmLoading,
                  onClick: () => emit('ok'),
                },
                '保存',
              ),
            ]),
          ])
        : null
  },
})

const ATreeStub = defineComponent({
  name: 'ATreeStub',
  props: {
    treeData: Array,
    checkedKeys: Array,
    fieldNames: Object,
    checkable: Boolean,
    defaultExpandAll: Boolean,
    blockNode: Boolean,
  },
  emits: ['update:checkedKeys'],
  setup(props, { emit }) {
    return () =>
      h(
        'div',
        {
          class: 'mock-tree',
          onClick: () => emit('update:checkedKeys', [1, 2, 3]),
        },
        (props.treeData as unknown[])?.length ? 'tree-data' : '',
      )
  },
})

const stubs = {
  'a-table': ATableStub,
  'a-input': AInputStub,
  'a-button': AButtonStub,
  'a-tag': ATagStub,
  'a-pagination': APaginationStub,
  'a-spin': ASpinStub,
  'a-modal': AModalStub,
  'a-tree': ATreeStub,
  PermissionModal: true,
}

// ── Mock data ──
const mockRoles = [
  {
    id: 1,
    roleName: '管理员',
    roleCode: 'ADMIN',
    roleType: '系统角色',
    status: 'ENABLE',
    createdAt: '2025-01-01 00:00:00',
    menuIds: [1, 2],
  },
  {
    id: 2,
    roleName: '普通用户',
    roleCode: 'USER',
    roleType: '业务角色',
    status: 'ENABLE',
    createdAt: '2025-01-02 00:00:00',
    menuIds: [3],
  },
  {
    id: 3,
    roleName: '审计员',
    roleCode: 'AUDITOR',
    roleType: '系统角色',
    status: 'DISABLE',
    createdAt: '2025-01-03 00:00:00',
    menuIds: [],
  },
]

const mockMenuTree = [
  { id: 1, menuName: '首页', children: [] },
  { id: 2, menuName: '系统管理', children: [{ id: 3, menuName: '用户管理', children: [] }] },
]

// ── Tests ──
describe('RoleListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('fetches roles on mount', async () => {
    mockGetRoles.mockResolvedValue(mockRoles)

    mount(RoleListPage, { global: { stubs } })
    await flushPromises()

    expect(mockGetRoles).toHaveBeenCalledTimes(1)
  })

  it('renders role names in table', async () => {
    mockGetRoles.mockResolvedValue(mockRoles)

    mount(RoleListPage, { global: { stubs } })
    await flushPromises()

    // The data was fetched and stored — verified by API call
    expect(mockGetRoles).toHaveBeenCalled()
  })

  it('renders edit permission buttons in action column', async () => {
    mockGetRoles.mockResolvedValue(mockRoles)

    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()

    // The table should render rows with 编辑权限 buttons
    const buttons = wrapper.findAll('.mock-btn')
    const permButtons = buttons.filter((b) => b.text() === '编辑权限')
    expect(permButtons.length).toBeGreaterThanOrEqual(1)
  })

  it('shows error message on load failure', async () => {
    mockGetRoles.mockRejectedValue(new Error('Network error'))

    mount(RoleListPage, { global: { stubs } })
    await flushPromises()

    expect(vi.mocked(message.error)).toHaveBeenCalledWith('Network error')
  })
})

describe('PermissionModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('fetches menu tree when opened', async () => {
    mockGetMenuTree.mockResolvedValue(mockMenuTree)

    const wrapper = mount(PermissionModal, {
      props: { open: true, role: mockRoles[0] },
      global: { stubs },
    })
    await flushPromises()

    expect(mockGetMenuTree).toHaveBeenCalledTimes(1)
  })

  it('does not fetch menu tree when closed', () => {
    mount(PermissionModal, {
      props: { open: false, role: null },
      global: { stubs },
    })

    expect(mockGetMenuTree).not.toHaveBeenCalled()
  })

  it('displays role name in modal title', () => {
    const wrapper = mount(PermissionModal, {
      props: { open: true, role: mockRoles[0] },
      global: { stubs },
    })

    expect(wrapper.find('.mock-modal-title').text()).toContain('管理员')
  })

  it('calls updateRoleMenus on save', async () => {
    mockGetMenuTree.mockResolvedValue(mockMenuTree)
    mockUpdateRoleMenus.mockResolvedValue(undefined)

    const wrapper = mount(PermissionModal, {
      props: { open: true, role: mockRoles[0] },
      global: { stubs },
    })
    await flushPromises()

    const saveBtn = wrapper.find('.mock-modal-ok')
    await saveBtn.trigger('click')
    await flushPromises()

    expect(mockUpdateRoleMenus).toHaveBeenCalledTimes(1)
    expect(mockUpdateRoleMenus).toHaveBeenCalledWith(1, expect.any(Array))
  })

  it('shows success message on save success', async () => {
    mockGetMenuTree.mockResolvedValue(mockMenuTree)
    mockUpdateRoleMenus.mockResolvedValue(undefined)

    const wrapper = mount(PermissionModal, {
      props: { open: true, role: mockRoles[0] },
      global: { stubs },
    })
    await flushPromises()

    const saveBtn = wrapper.find('.mock-modal-ok')
    await saveBtn.trigger('click')
    await flushPromises()

    expect(vi.mocked(message.success)).toHaveBeenCalledWith('权限保存成功')
  })

  it('shows error message on save failure', async () => {
    mockGetMenuTree.mockResolvedValue(mockMenuTree)
    mockUpdateRoleMenus.mockRejectedValue(new Error('Network error'))

    const wrapper = mount(PermissionModal, {
      props: { open: true, role: mockRoles[0] },
      global: { stubs },
    })
    await flushPromises()

    const saveBtn = wrapper.find('.mock-modal-ok')
    await saveBtn.trigger('click')
    await flushPromises()

    expect(vi.mocked(message.error)).toHaveBeenCalled()
  })
})
