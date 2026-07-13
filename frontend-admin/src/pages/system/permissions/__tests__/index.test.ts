import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { MenuTreeVO } from '@/types/system'

const mocks = vi.hoisted(() => ({
  getMenuTree: vi.fn(),
  getRoles: vi.fn(),
  createMenu: vi.fn(),
  deleteMenu: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
  roles: ['USER'] as string[],
  permissions: ['system:menu:query'] as string[],
}))

vi.mock('ant-design-vue', async () => {
  const actual = await vi.importActual<typeof import('ant-design-vue')>('ant-design-vue')
  return { ...actual, message: { success: mocks.success, error: mocks.error } }
})

vi.mock('@/api/modules/system', () => ({
  getMenuTree: mocks.getMenuTree,
  getRoles: mocks.getRoles,
  createMenu: mocks.createMenu,
  deleteMenu: mocks.deleteMenu,
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    roles: mocks.roles,
    hasPermission: (permission: string) =>
      mocks.permissions.includes('*') || mocks.permissions.includes(permission),
  }),
}))

import PermissionPage from '../index.vue'

const currentDir = dirname(fileURLToPath(import.meta.url))

const menuTree: MenuTreeVO[] = [
  {
    id: '10',
    parentId: '0',
    menuName: '系统管理',
    menuType: 'DIR',
    path: '/system',
    component: '',
    icon: '',
    status: 'ENABLE',
    children: [
      {
        id: '11',
        parentId: '10',
        menuName: '菜单概览',
        menuType: 'MENU',
        path: '/system/overview',
        component: 'system/overview/index',
        icon: '',
        status: 'ENABLE',
      },
      {
        id: '12',
        parentId: '10',
        menuName: '查询按钮',
        menuType: 'BUTTON',
        path: '',
        component: '',
        perms: 'system:menu:query',
        icon: '',
        status: 'ENABLE',
      },
    ],
  },
]

const menuTreeAfterDelete: MenuTreeVO[] = [
  {
    ...menuTree[0],
    children: menuTree[0].children?.filter((menu) => String(menu.id) !== '11'),
  },
]

const buttonStub = {
  template:
    '<button v-bind="$attrs" :disabled="$attrs.disabled"><slot name="icon" /><slot /></button>',
}

const inputStub = {
  props: ['value'],
  emits: ['update:value'],
  template:
    '<input v-bind="$attrs" :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
}

const selectStub = {
  props: ['value'],
  emits: ['update:value'],
  template:
    '<select v-bind="$attrs" :value="value" @change="$emit(\'update:value\', $event.target.value)"><slot /></select>',
}

const treeSelectStub = {
  props: ['value', 'treeData'],
  emits: ['update:value'],
  setup() {
    const flattenTreeOptions = (
      nodes: Array<{ title: string; value: number | string; children?: unknown[] }>,
    ): Array<{ title: string; value: number | string }> =>
      nodes.flatMap((node) => [
        node,
        ...flattenTreeOptions(
          (node.children ?? []) as Array<{
            title: string
            value: number | string
            children?: unknown[]
          }>,
        ),
      ])
    return { flattenTreeOptions }
  },
  template:
    '<select v-bind="$attrs" :value="value" @change="$emit(\'update:value\', $event.target.value)"><option v-for="node in flattenTreeOptions(treeData || [])" :key="node.value" :value="node.value">{{ node.title }}</option></select>',
}

function mountPage() {
  return mount(PermissionPage, {
    global: {
      stubs: {
        AButton: buttonStub,
        AInput: inputStub,
        AInputNumber: inputStub,
        ASelect: selectStub,
        ASelectOption: { props: ['value'], template: '<option :value="value"><slot /></option>' },
        ATreeSelect: treeSelectStub,
        AModal: {
          props: ['open', 'title'],
          template:
            '<div v-if="open" :data-testid="title === \'删除菜单\' ? \'delete-menu-modal\' : \'create-menu-modal\'"><slot /></div>',
        },
        AAlert: {
          template: '<div><slot name="message" /><slot name="description" /></div>',
        },
        AForm: { template: '<form><slot /></form>' },
        AFormItem: { template: '<label><slot /></label>' },
        ABreadcrumb: true,
        ABreadcrumbItem: true,
        ATag: true,
        VxeGrid: true,
        PlusOutlined: true,
        DeleteOutlined: true,
        ReloadOutlined: true,
      },
    },
  })
}

describe('permission governance menu management', () => {
  beforeEach(() => {
    mocks.roles = ['USER']
    mocks.permissions = ['system:menu:query']
    mocks.getMenuTree.mockReset().mockResolvedValue(menuTree)
    mocks.getRoles.mockReset().mockResolvedValue([])
    mocks.createMenu.mockReset().mockResolvedValue('100')
    mocks.deleteMenu.mockReset().mockResolvedValue(undefined)
    mocks.success.mockReset()
    mocks.error.mockReset()
  })

  it('hides the entry for a non-admin', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="create-menu-open"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="delete-menu-open"]').exists()).toBe(false)
  })

  it('keeps the admin-only route boundary even with system:menu:add', async () => {
    mocks.permissions = ['system:menu:add']
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="create-menu-open"]').exists()).toBe(false)
  })

  it('keeps the admin-only route boundary even with system:menu:delete', async () => {
    mocks.permissions = ['system:menu:delete']
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="delete-menu-open"]').exists()).toBe(false)
  })

  it.each([
    { roles: ['ADMIN'], permissions: [] },
    { roles: ['SUPER_ADMIN'], permissions: [] },
  ])('shows the entry for an authorized identity', async ({ roles, permissions }) => {
    mocks.roles = roles
    mocks.permissions = permissions
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="create-menu-open"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="delete-menu-open"]').exists()).toBe(true)
  })

  it('validates required fields before submitting', async () => {
    mocks.roles = ['ADMIN']
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="create-menu-open"]').trigger('click')
    await wrapper.get('[data-testid="create-menu-submit"]').trigger('click')

    expect(mocks.createMenu).not.toHaveBeenCalled()
    expect(mocks.error).toHaveBeenCalledWith('请填写菜单名称和菜单类型')
    expect(wrapper.find('[data-testid="create-menu-modal"]').exists()).toBe(true)
  })

  it('submits the selected parent and refreshes the tree on success', async () => {
    mocks.roles = ['ADMIN']
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="create-menu-open"]').trigger('click')
    await wrapper.get('[data-testid="create-menu-name"]').setValue('采购菜单')
    await wrapper.get('[data-testid="create-menu-parent"]').setValue('10')
    await wrapper.get('[data-testid="create-menu-perms"]').setValue('purchase:query')
    await wrapper.get('[data-testid="create-menu-submit"]').trigger('click')
    await flushPromises()

    expect(mocks.createMenu).toHaveBeenCalledWith({
      parentId: '10',
      menuName: '采购菜单',
      menuType: 'MENU',
      perms: 'purchase:query',
      orderNum: 0,
    })
    const payload = mocks.createMenu.mock.calls[0][0]
    expect(payload).not.toHaveProperty('id')
    expect(payload).not.toHaveProperty('tenantId')
    expect(payload).not.toHaveProperty('children')
    expect(payload).not.toHaveProperty('createdAt')
    expect(mocks.getMenuTree).toHaveBeenCalledTimes(2)
    expect(mocks.success).toHaveBeenCalledWith('菜单创建成功')
    expect(wrapper.find('[data-testid="create-menu-modal"]').exists()).toBe(false)
  })

  it('keeps the form open and does not refresh after a failed create', async () => {
    mocks.roles = ['ADMIN']
    mocks.createMenu.mockRejectedValueOnce(new Error('父菜单不存在'))
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="create-menu-open"]').trigger('click')
    await wrapper.get('[data-testid="create-menu-name"]').setValue('失败菜单')
    await wrapper.get('[data-testid="create-menu-submit"]').trigger('click')
    await flushPromises()

    expect(mocks.getMenuTree).toHaveBeenCalledTimes(1)
    expect(mocks.error).toHaveBeenCalledWith('父菜单不存在')
    expect(wrapper.find('[data-testid="create-menu-modal"]').exists()).toBe(true)
    expect(
      (wrapper.get('[data-testid="create-menu-name"]').element as HTMLInputElement).value,
    ).toBe('失败菜单')
  })

  it('offers directory, menu, button, and permissionless nodes as delete targets', async () => {
    mocks.roles = ['ADMIN']
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="delete-menu-open"]').trigger('click')

    const options = wrapper.findAll('[data-testid="delete-menu-target"] option')
    expect(options.map((option) => option.attributes('value'))).toEqual(['10', '11', '12'])
    expect(options.map((option) => option.text())).toEqual(['系统管理', '菜单概览', '查询按钮'])
  })

  it('requires a target before explicitly confirming deletion', async () => {
    mocks.roles = ['ADMIN']
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="delete-menu-open"]').trigger('click')
    await wrapper.get('[data-testid="delete-menu-submit"]').trigger('click')

    expect(mocks.deleteMenu).not.toHaveBeenCalled()
    expect(mocks.error).toHaveBeenCalledWith('请选择要删除的菜单')
    expect(wrapper.find('[data-testid="delete-menu-modal"]').exists()).toBe(true)
  })

  it('shows the target name, deletes it, refreshes menus and roles, then closes', async () => {
    mocks.roles = ['SUPER_ADMIN']
    const refreshedRoles = [
      {
        id: '20',
        roleCode: 'ADMIN',
        roleName: '管理员',
        status: 'ENABLE',
        menuIds: [12],
        createdAt: '2026-07-13T00:00:00',
      },
    ]
    mocks.getMenuTree
      .mockReset()
      .mockResolvedValueOnce(menuTree)
      .mockResolvedValueOnce(menuTreeAfterDelete)
    mocks.getRoles.mockReset().mockResolvedValueOnce([]).mockResolvedValueOnce(refreshedRoles)
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="delete-menu-open"]').trigger('click')
    await wrapper.get('[data-testid="delete-menu-target"]').setValue('11')

    expect(wrapper.get('[data-testid="delete-menu-warning"]').text()).toContain('菜单概览')
    await wrapper.get('[data-testid="delete-menu-submit"]').trigger('click')
    await flushPromises()

    expect(mocks.deleteMenu).toHaveBeenCalledWith('11')
    expect(mocks.getMenuTree).toHaveBeenCalledTimes(2)
    expect(mocks.getRoles).toHaveBeenCalledTimes(2)
    expect(menuTreeAfterDelete[0].children?.some((menu) => String(menu.id) === '11')).toBe(false)
    expect(mocks.success).toHaveBeenCalledWith('菜单“菜单概览”已删除')
    expect(wrapper.find('[data-testid="delete-menu-modal"]').exists()).toBe(false)
  })

  it('does not claim complete success when post-delete refresh fails', async () => {
    mocks.roles = ['ADMIN']
    mocks.getMenuTree
      .mockReset()
      .mockResolvedValueOnce(menuTree)
      .mockRejectedValueOnce(new Error('刷新菜单树失败'))
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="delete-menu-open"]').trigger('click')
    await wrapper.get('[data-testid="delete-menu-target"]').setValue('11')
    await wrapper.get('[data-testid="delete-menu-submit"]').trigger('click')
    await flushPromises()

    expect(mocks.deleteMenu).toHaveBeenCalledWith('11')
    expect(mocks.success).not.toHaveBeenCalled()
    expect(mocks.error).toHaveBeenCalledWith(
      '菜单已删除，但刷新权限清单失败：刷新菜单树失败',
    )
    expect(wrapper.find('[data-testid="delete-menu-modal"]').exists()).toBe(true)
    expect((wrapper.get('[data-testid="delete-menu-target"]').element as HTMLSelectElement).value).toBe(
      '11',
    )
  })

  it('does not claim complete success when the refreshed tree still contains the target', async () => {
    mocks.roles = ['ADMIN']
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="delete-menu-open"]').trigger('click')
    await wrapper.get('[data-testid="delete-menu-target"]').setValue('11')
    await wrapper.get('[data-testid="delete-menu-submit"]').trigger('click')
    await flushPromises()

    expect(mocks.success).not.toHaveBeenCalled()
    expect(mocks.error).toHaveBeenCalledWith(
      '菜单已删除，但刷新权限清单失败：刷新后的菜单树仍包含已删除目标',
    )
    expect(wrapper.find('[data-testid="delete-menu-modal"]').exists()).toBe(true)
  })

  it('keeps the target and does not refresh when deletion is rejected', async () => {
    mocks.roles = ['ADMIN']
    mocks.deleteMenu.mockRejectedValueOnce(new Error('菜单被角色引用，无法删除'))
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="delete-menu-open"]').trigger('click')
    await wrapper.get('[data-testid="delete-menu-target"]').setValue('12')
    await wrapper.get('[data-testid="delete-menu-submit"]').trigger('click')
    await flushPromises()

    expect(mocks.getMenuTree).toHaveBeenCalledTimes(1)
    expect(mocks.getRoles).toHaveBeenCalledTimes(1)
    expect(mocks.success).not.toHaveBeenCalled()
    expect(mocks.error).toHaveBeenCalledWith('菜单被角色引用，无法删除')
    expect(wrapper.find('[data-testid="delete-menu-modal"]').exists()).toBe(true)
    expect((wrapper.get('[data-testid="delete-menu-target"]').element as HTMLSelectElement).value).toBe(
      '12',
    )
  })

  it('keeps the existing route and does not add edit or sort actions', () => {
    const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
    const routerSource = readFileSync(resolve(currentDir, '../../../../router/index.ts'), 'utf-8')
    const navigationSource = readFileSync(
      resolve(currentDir, '../../../../router/navigation.ts'),
      'utf-8',
    )

    expect(routerSource).toContain("path: 'permissions'")
    expect(routerSource).toContain("name: 'SystemPermissions'")
    expect(navigationSource).toContain(
      "{ key: '/system/permissions', label: '权限清单', adminOnly: true }",
    )
    expect(source).not.toContain('updateMenu')
    expect(source).toContain('deleteMenu')
    expect(source).not.toContain('级联删除选项')
    expect(source).not.toContain('强制解绑选项')
    expect(source).not.toContain('拖拽排序')
  })
})
