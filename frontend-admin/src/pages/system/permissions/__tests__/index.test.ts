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
  template:
    '<select v-bind="$attrs" :value="value" @change="$emit(\'update:value\', $event.target.value)"><option value="0">根节点</option><option value="10">系统管理</option></select>',
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
          props: ['open'],
          template: '<div v-if="open" data-testid="create-menu-modal"><slot /></div>',
        },
        AForm: { template: '<form><slot /></form>' },
        AFormItem: { template: '<label><slot /></label>' },
        ABreadcrumb: true,
        ABreadcrumbItem: true,
        ATag: true,
        VxeGrid: true,
        PlusOutlined: true,
        ReloadOutlined: true,
      },
    },
  })
}

describe('permission governance menu creation', () => {
  beforeEach(() => {
    mocks.roles = ['USER']
    mocks.permissions = ['system:menu:query']
    mocks.getMenuTree.mockReset().mockResolvedValue(menuTree)
    mocks.getRoles.mockReset().mockResolvedValue([])
    mocks.createMenu.mockReset().mockResolvedValue('100')
    mocks.success.mockReset()
    mocks.error.mockReset()
  })

  it('hides the entry for a non-admin', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="create-menu-open"]').exists()).toBe(false)
  })

  it('keeps the admin-only route boundary even with system:menu:add', async () => {
    mocks.permissions = ['system:menu:add']
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="create-menu-open"]').exists()).toBe(false)
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

  it('keeps the existing route and does not add edit, delete, or sort actions', () => {
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
    expect(source).not.toContain('deleteMenu')
    expect(source).not.toContain('拖拽排序')
  })
})
