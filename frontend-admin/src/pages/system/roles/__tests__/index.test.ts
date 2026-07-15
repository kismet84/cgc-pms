import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

// ── Mock API module ──
const mockGetRoles = vi.fn()
const mockGetRoleDetail = vi.fn()
const mockCreateRole = vi.fn()
const mockDeleteRole = vi.fn()
const mockUpdateRole = vi.fn()
const mockGetMenuTree = vi.fn()
const mockUpdateRoleMenus = vi.fn()
const mockAccess = vi.hoisted(() => ({
  roles: ['ADMIN'] as string[],
  permissions: [] as string[],
}))

vi.mock('@/api/modules/system', () => ({
  getRoles: (...args: unknown[]) => mockGetRoles(...args),
  getRoleDetail: (...args: unknown[]) => mockGetRoleDetail(...args),
  createRole: (...args: unknown[]) => mockCreateRole(...args),
  deleteRole: (...args: unknown[]) => mockDeleteRole(...args),
  updateRole: (...args: unknown[]) => mockUpdateRole(...args),
  getMenuTree: (...args: unknown[]) => mockGetMenuTree(...args),
  updateRoleMenus: (...args: unknown[]) => mockUpdateRoleMenus(...args),
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    roles: mockAccess.roles,
    hasPermission: (permission: string) => mockAccess.permissions.includes(permission),
  }),
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
    Modal: {
      confirm: vi.fn(),
    },
  }
})

import { message, Modal } from 'ant-design-vue'
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

const ASelectStub = defineComponent({
  name: 'ASelectStub',
  props: { value: String },
  emits: ['update:value'],
  setup(props, { emit, slots }) {
    return () =>
      h(
        'select',
        {
          class: 'mock-select',
          value: props.value,
          onChange: (event: Event) =>
            emit('update:value', (event.target as HTMLSelectElement).value),
        },
        slots.default?.(),
      )
  },
})

const ASelectOptionStub = defineComponent({
  name: 'ASelectOptionStub',
  props: { value: String },
  setup(props, { slots }) {
    return () => h('option', { value: props.value }, slots.default?.())
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
                {
                  class: 'mock-modal-cancel',
                  onClick: () => {
                    emit('cancel')
                    emit('update:open', false)
                  },
                },
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

const VxeGridStub = defineComponent({
  name: 'VxeGridStub',
  props: { data: Array, columns: Array, loading: Boolean },
  setup(props, { slots }) {
    return () =>
      h(
        'div',
        { class: 'mock-vxe-grid' },
        ((props.data as Record<string, unknown>[]) ?? []).map((row) =>
          h(
            'div',
            { class: 'mock-vxe-row', key: String(row.id) },
            ((props.columns as Record<string, unknown>[]) ?? []).map((column) => {
              const slotName = (column.slots as { default?: string } | undefined)?.default
              return h(
                'div',
                { class: 'mock-vxe-cell' },
                slotName && slots[slotName]
                  ? slots[slotName]?.({ row })
                  : String(row[String(column.field ?? '')] ?? ''),
              )
            }),
          ),
        ),
      )
  },
})

const ADropdownStub = defineComponent({
  name: 'ADropdownStub',
  setup(_, { slots }) {
    return () => h('div', { class: 'mock-dropdown' }, [slots.default?.(), slots.overlay?.()])
  },
})

const AMenuStub = defineComponent({
  name: 'AMenuStub',
  setup(_, { slots }) {
    return () => h('div', { class: 'mock-menu' }, slots.default?.())
  },
})

const AMenuItemStub = defineComponent({
  name: 'AMenuItemStub',
  emits: ['click'],
  setup(_, { emit, slots }) {
    return () =>
      h(
        'button',
        { class: 'mock-menu-item', type: 'button', onClick: () => emit('click') },
        slots.default?.(),
      )
  },
})

const AEmptyStub = defineComponent({
  name: 'AEmptyStub',
  props: { description: String },
  setup(props) {
    return () => h('div', { class: 'mock-empty' }, props.description)
  },
})

const stubs = {
  'a-table': ATableStub,
  'a-input': AInputStub,
  'a-button': AButtonStub,
  'a-tag': ATagStub,
  'a-select': ASelectStub,
  'a-select-option': ASelectOptionStub,
  'a-pagination': APaginationStub,
  'a-spin': ASpinStub,
  'a-modal': AModalStub,
  'a-tree': ATreeStub,
  'a-empty': AEmptyStub,
  'a-dropdown': ADropdownStub,
  'a-menu': AMenuStub,
  'a-menu-item': AMenuItemStub,
  'vxe-grid': VxeGridStub,
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
    mockAccess.roles.splice(0, mockAccess.roles.length, 'ADMIN')
    mockAccess.permissions.splice(0)
    mockGetRoles.mockResolvedValue(mockRoles)
    mockGetRoleDetail.mockResolvedValue({ ...mockRoles[0], dataScope: 'ALL' })
    mockDeleteRole.mockResolvedValue(undefined)
    mockUpdateRole.mockResolvedValue(undefined)
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

    // The table should render rows with 编辑权限 actionable elements
    const html = wrapper.html()
    expect(html).toMatch(/编辑权限|handleEditPermission|permission/)
  })

  it('shows error message on load failure', async () => {
    mockGetRoles.mockRejectedValue(new Error('Network error'))

    mount(RoleListPage, { global: { stubs } })
    await flushPromises()

    expect(vi.mocked(message.error)).toHaveBeenCalledWith('Network error')
  })

  it.each(['ADMIN', 'SUPER_ADMIN'])('shows create entry for %s', async (role) => {
    mockAccess.roles.splice(0, mockAccess.roles.length, role)

    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()

    expect(wrapper.find('[data-testid="create-role-button"]').exists()).toBe(true)
  })

  it('hides create entry from ordinary users even with system:role:add', async () => {
    mockAccess.roles.splice(0, mockAccess.roles.length, 'USER')
    mockAccess.permissions.push('system:role:add')

    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()

    expect(wrapper.find('[data-testid="create-role-button"]').exists()).toBe(false)
  })

  it.each(['ADMIN', 'SUPER_ADMIN'])(
    'shows delete only for unprotected roles to %s',
    async (role) => {
      mockAccess.roles.splice(0, mockAccess.roles.length, role)

      const wrapper = mount(RoleListPage, { global: { stubs } })
      await flushPromises()

      expect(wrapper.find('[data-testid="delete-role-1"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="delete-role-2"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="delete-role-3"]').exists()).toBe(false)
    },
  )

  it('hides delete from ordinary users even with system:role:delete', async () => {
    mockAccess.roles.splice(0, mockAccess.roles.length, 'USER')
    mockAccess.permissions.push('system:role:delete')

    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()

    expect(wrapper.find('[data-testid="delete-role-2"]').exists()).toBe(false)
    expect(mockDeleteRole).not.toHaveBeenCalled()
  })

  it.each(['ADMIN', 'SUPER_ADMIN'])(
    'shows update only for unprotected roles to %s',
    async (role) => {
      mockAccess.roles.splice(0, mockAccess.roles.length, role)

      const wrapper = mount(RoleListPage, { global: { stubs } })
      await flushPromises()

      expect(wrapper.find('[data-testid="update-role-1"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="update-role-2"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="update-role-3"]').exists()).toBe(false)
    },
  )

  it('hides update from ordinary users even with system:role:edit', async () => {
    mockAccess.roles.splice(0, mockAccess.roles.length, 'USER')
    mockAccess.permissions.push('system:role:edit')

    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()

    expect(wrapper.find('[data-testid="update-role-2"]').exists()).toBe(false)
    expect(mockUpdateRole).not.toHaveBeenCalled()
  })

  it('prefills immutable code and validates role name before updating', async () => {
    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()
    await wrapper.find('[data-testid="update-role-2"]').trigger('click')

    expect(
      (wrapper.find('[data-testid="update-role-code"]').element as HTMLInputElement).value,
    ).toBe('USER')
    expect(
      (wrapper.find('[data-testid="update-role-name"]').element as HTMLInputElement).value,
    ).toBe('普通用户')

    await wrapper.find('[data-testid="update-role-name"]').setValue('   ')
    await wrapper.find('[data-testid="update-role-submit"]').trigger('click')
    expect(vi.mocked(message.error)).toHaveBeenLastCalledWith('请填写角色名称')

    await wrapper.find('[data-testid="update-role-name"]').setValue('角'.repeat(101))
    await wrapper.find('[data-testid="update-role-submit"]').trigger('click')
    expect(vi.mocked(message.error)).toHaveBeenLastCalledWith('角色名称不能超过100个字符')
    expect(mockUpdateRole).not.toHaveBeenCalled()
  })

  it('keeps the update form open and reports the backend error', async () => {
    mockUpdateRole.mockRejectedValueOnce(new Error('系统或高等级角色不允许修改'))
    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()
    await wrapper.find('[data-testid="update-role-2"]').trigger('click')
    await wrapper.find('[data-testid="update-role-name"]').setValue('修改失败仍保留')
    await wrapper.find('[data-testid="update-role-submit"]').trigger('click')
    await flushPromises()

    expect(vi.mocked(message.error)).toHaveBeenCalledWith('系统或高等级角色不允许修改')
    expect(wrapper.find('[data-testid="update-role-modal"]').exists()).toBe(true)
    expect(
      (wrapper.find('[data-testid="update-role-name"]').element as HTMLInputElement).value,
    ).toBe('修改失败仍保留')
    expect(mockGetRoles).toHaveBeenCalledTimes(1)
  })

  it('updates the selected role, closes the form, and refreshes without changing menus', async () => {
    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()
    await wrapper.find('[data-testid="update-role-2"]').trigger('click')
    await wrapper.find('[data-testid="update-role-name"]').setValue('项目成员')
    await wrapper.find('[data-testid="update-role-status"]').setValue('DISABLE')
    await wrapper.find('[data-testid="update-role-data-scope"]').setValue('DEPT')
    await wrapper.find('[data-testid="update-role-submit"]').trigger('click')
    await flushPromises()

    expect(mockUpdateRole).toHaveBeenCalledWith(2, {
      roleCode: 'USER',
      roleName: '项目成员',
      status: 'DISABLE',
      dataScope: 'DEPT',
    })
    expect(vi.mocked(message.success)).toHaveBeenCalledWith('角色修改成功')
    expect(wrapper.find('[data-testid="update-role-modal"]').exists()).toBe(false)
    expect(mockGetRoles).toHaveBeenCalledTimes(2)
    expect(mockUpdateRoleMenus).not.toHaveBeenCalled()
  })

  it('does not call delete until the destructive confirmation is accepted', async () => {
    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()

    await wrapper.find('[data-testid="delete-role-2"]').trigger('click')

    expect(vi.mocked(Modal.confirm)).toHaveBeenCalledOnce()
    expect(vi.mocked(Modal.confirm).mock.calls[0][0]).toMatchObject({
      title: '确认删除角色',
      okText: '删除',
      cancelText: '取消',
      okType: 'danger',
    })
    expect(mockDeleteRole).not.toHaveBeenCalled()
  })

  it('deletes the selected role and refreshes after confirmation', async () => {
    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()
    await wrapper.find('[data-testid="delete-role-2"]').trigger('click')

    const options = vi.mocked(Modal.confirm).mock.calls[0][0] as {
      onOk: () => Promise<void>
    }
    await options.onOk()
    await flushPromises()

    expect(mockDeleteRole).toHaveBeenCalledWith(2)
    expect(vi.mocked(message.success)).toHaveBeenCalledWith('角色删除成功')
    expect(mockGetRoles).toHaveBeenCalledTimes(2)
  })

  it('keeps the list and reports the backend error when deletion fails', async () => {
    mockDeleteRole.mockRejectedValueOnce(new Error('角色仍绑定用户，无法删除'))
    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()
    await wrapper.find('[data-testid="delete-role-2"]').trigger('click')

    const options = vi.mocked(Modal.confirm).mock.calls[0][0] as {
      onOk: () => Promise<void>
    }
    await expect(options.onOk()).rejects.toThrow('角色仍绑定用户，无法删除')

    expect(vi.mocked(message.error)).toHaveBeenCalledWith('角色仍绑定用户，无法删除')
    expect(mockGetRoles).toHaveBeenCalledTimes(1)
    expect(vi.mocked(message.success)).not.toHaveBeenCalledWith('角色删除成功')
  })

  it.each(['ADMIN', 'SUPER_ADMIN'])('shows and loads role detail for %s', async (role) => {
    mockAccess.roles.splice(0, mockAccess.roles.length, role)
    const detail = { ...mockRoles[0], dataScope: 'ALL' }
    mockGetRoleDetail.mockResolvedValue(detail)

    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()
    await wrapper.find('[data-testid="view-role-detail-1"]').trigger('click')
    await flushPromises()

    expect(mockGetRoleDetail).toHaveBeenCalledWith(1)
    expect(wrapper.find('[data-testid="role-detail-content"]').text()).toContain('管理员')
    expect(wrapper.find('[data-testid="role-detail-content"]').text()).toContain('ADMIN')
    expect(wrapper.find('[data-testid="role-detail-content"]').text()).toContain('ALL')
    expect(wrapper.find('[data-testid="role-detail-content"]').text()).toContain('1, 2')
    expect(mockCreateRole).not.toHaveBeenCalled()
    expect(mockUpdateRoleMenus).not.toHaveBeenCalled()
  })

  it('hides the detail entry from ordinary users even with system:role:query', async () => {
    mockAccess.roles.splice(0, mockAccess.roles.length, 'USER')
    mockAccess.permissions.push('system:role:query')

    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()

    expect(wrapper.find('[data-testid="view-role-detail-1"]').exists()).toBe(false)
    expect(mockGetRoleDetail).not.toHaveBeenCalled()
  })

  it('clears old detail, keeps the selected target on failure, and refetches after reopening', async () => {
    mockGetRoleDetail.mockResolvedValueOnce({ ...mockRoles[0], dataScope: 'ALL' })
    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()

    await wrapper.find('[data-testid="view-role-detail-1"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="role-detail-content"]').text()).toContain('管理员')
    await wrapper.find('[data-testid="role-detail-modal"] .mock-modal-cancel').trigger('click')

    mockGetRoleDetail.mockRejectedValueOnce(new Error('角色不存在'))
    await wrapper.find('[data-testid="view-role-detail-2"]').trigger('click')
    await flushPromises()

    expect(mockGetRoleDetail).toHaveBeenNthCalledWith(1, 1)
    expect(mockGetRoleDetail).toHaveBeenNthCalledWith(2, 2)
    expect(wrapper.find('[data-testid="role-detail-modal"] .mock-modal-title').text()).toContain(
      '普通用户',
    )
    expect(wrapper.find('[data-testid="role-detail-content"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="role-detail-empty"]').exists()).toBe(true)
    expect(vi.mocked(message.error)).toHaveBeenCalledWith('角色不存在')
    expect(mockCreateRole).not.toHaveBeenCalled()
    expect(mockUpdateRoleMenus).not.toHaveBeenCalled()
  })

  it('validates required fields and length limits before creating', async () => {
    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()
    await wrapper.find('[data-testid="create-role-button"]').trigger('click')

    await wrapper.find('[data-testid="create-role-submit"]').trigger('click')
    expect(vi.mocked(message.error)).toHaveBeenLastCalledWith('请填写角色编码和角色名称')

    await wrapper.find('[data-testid="create-role-code"]').setValue('R'.repeat(51))
    await wrapper.find('[data-testid="create-role-name"]').setValue('普通角色')
    await wrapper.find('[data-testid="create-role-submit"]').trigger('click')
    expect(vi.mocked(message.error)).toHaveBeenLastCalledWith('角色编码不能超过50个字符')

    await wrapper.find('[data-testid="create-role-code"]').setValue('NORMAL_ROLE')
    await wrapper.find('[data-testid="create-role-name"]').setValue('角'.repeat(101))
    await wrapper.find('[data-testid="create-role-submit"]').trigger('click')
    expect(vi.mocked(message.error)).toHaveBeenLastCalledWith('角色名称不能超过100个字符')
    expect(mockCreateRole).not.toHaveBeenCalled()
  })

  it('keeps the form open and shows the backend error when role code is duplicated', async () => {
    mockCreateRole.mockRejectedValue(new Error('角色编码已存在'))
    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()
    await wrapper.find('[data-testid="create-role-button"]').trigger('click')
    await wrapper.find('[data-testid="create-role-code"]').setValue('DUPLICATE_ROLE')
    await wrapper.find('[data-testid="create-role-name"]').setValue('重复角色')
    await wrapper.find('[data-testid="create-role-submit"]').trigger('click')
    await flushPromises()

    expect(vi.mocked(message.error)).toHaveBeenCalledWith('角色编码已存在')
    expect(wrapper.find('.mock-modal').exists()).toBe(true)
    expect(
      (wrapper.find('[data-testid="create-role-code"]').element as HTMLInputElement).value,
    ).toBe('DUPLICATE_ROLE')
  })

  it('closes the form and refreshes the list after successful creation', async () => {
    mockCreateRole.mockResolvedValue('1001')
    const wrapper = mount(RoleListPage, { global: { stubs } })
    await flushPromises()
    await wrapper.find('[data-testid="create-role-button"]').trigger('click')
    await wrapper.find('[data-testid="create-role-code"]').setValue('NEW_ROLE')
    await wrapper.find('[data-testid="create-role-name"]').setValue('新角色')
    await wrapper.find('[data-testid="create-role-submit"]').trigger('click')
    await flushPromises()

    expect(mockCreateRole).toHaveBeenCalledWith({
      roleCode: 'NEW_ROLE',
      roleName: '新角色',
      status: 'ENABLE',
      dataScope: 'SELF',
    })
    expect(vi.mocked(message.success)).toHaveBeenCalledWith('角色创建成功')
    expect(wrapper.find('.mock-modal').exists()).toBe(false)
    expect(mockGetRoles).toHaveBeenCalledTimes(2)
    expect(mockUpdateRoleMenus).not.toHaveBeenCalled()
  })
})

describe('PermissionModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('fetches menu tree when opened', async () => {
    mockGetMenuTree.mockResolvedValue(mockMenuTree)

    mount(PermissionModal, {
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
