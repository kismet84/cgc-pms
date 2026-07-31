import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { MenuTreeVO, SysMenuVO } from '@/types/system'

const mocks = vi.hoisted(() => ({
  getMenuTree: vi.fn(),
  getRoles: vi.fn(),
  getMenuDetail: vi.fn(),
  getMenuList: vi.fn(),
  updateMenu: vi.fn(),
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
  getMenuDetail: mocks.getMenuDetail,
  getMenuList: mocks.getMenuList,
  updateMenu: mocks.updateMenu,
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

const menuDetail: SysMenuVO = {
  id: '11',
  parentId: '10',
  menuName: '菜单概览',
  menuType: 'MENU',
  path: '/system/overview',
  component: 'system/overview/index',
  perms: '',
  icon: 'menu',
  orderNum: 3,
  status: 'ENABLE',
  visible: 1,
}

const flatMenus: SysMenuVO[] = [
  {
    id: '10',
    parentId: '0',
    menuName: '系统管理',
    menuType: 'DIR',
    path: '/system',
    component: '',
    perms: '',
    icon: '',
    orderNum: 1,
    status: 'ENABLE',
    visible: 1,
  },
  menuDetail,
  {
    id: '12',
    parentId: '10',
    menuName: '查询按钮',
    menuType: 'BUTTON',
    path: '',
    component: '',
    perms: 'system:menu:query',
    icon: '',
    orderNum: 4,
    status: 'DISABLE',
    visible: 0,
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
  emits: ['update:value', 'change'],
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
    '<select v-bind="$attrs" :value="value" @change="$emit(\'update:value\', $event.target.value); $emit(\'change\', $event.target.value)"><option v-for="node in flattenTreeOptions(treeData || [])" :key="node.value" :value="node.value">{{ node.title }}</option></select>',
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
          emits: ['cancel'],
          template:
            "<div v-if=\"open\" :data-testid=\"title === '删除菜单' ? 'delete-menu-modal' : title === '修改菜单' ? 'update-menu-modal' : title === '菜单详情' ? 'detail-menu-modal' : title === '菜单列表' ? 'menu-list-modal' : 'unknown-menu-modal'\"><button data-testid=\"modal-cancel\" @click=\"$emit('cancel')\">close</button><slot /></div>",
        },
        AAlert: {
          props: ['message'],
          template:
            '<div v-bind="$attrs">{{ message }}<slot name="message" /><slot name="description" /></div>',
        },
        AForm: { template: '<form><slot /></form>' },
        AFormItem: { template: '<label><slot /></label>' },
        ABreadcrumb: true,
        ABreadcrumbItem: true,
        ATag: true,
        VxeGrid: true,
        DeleteOutlined: true,
        ReloadOutlined: true,
        EyeOutlined: true,
        EditOutlined: true,
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
    mocks.getMenuDetail.mockReset().mockResolvedValue(menuDetail)
    mocks.getMenuList.mockReset().mockResolvedValue(flatMenus)
    mocks.updateMenu.mockReset().mockResolvedValue(undefined)
    mocks.deleteMenu.mockReset().mockResolvedValue(undefined)
    mocks.success.mockReset()
    mocks.error.mockReset()
  })

  it('hides the entry for a non-admin', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="update-menu-open"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="delete-menu-open"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="detail-menu-open"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="menu-list-open"]').exists()).toBe(false)
    expect(mocks.getMenuList).toHaveBeenCalledOnce()
  })

  it('does not expose a local create entry even with system:menu:add', async () => {
    mocks.permissions = ['system:menu:add']
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).not.toContain('新建权限')
  })

  it('keeps the admin-only route boundary even with system:menu:delete', async () => {
    mocks.permissions = ['system:menu:delete']
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="delete-menu-open"]').exists()).toBe(false)
  })

  it('keeps the admin-only route boundary even with system:menu:edit', async () => {
    mocks.permissions = ['system:menu:edit']
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="update-menu-open"]').exists()).toBe(false)
  })

  it('keeps the admin-only detail boundary even with system:menu:query', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="detail-menu-open"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="menu-list-open"]').exists()).toBe(false)
  })

  it.each([
    { roles: ['ADMIN'], permissions: [] },
    { roles: ['SUPER_ADMIN'], permissions: [] },
  ])('shows the entry for an authorized identity', async ({ roles, permissions }) => {
    mocks.roles = roles
    mocks.permissions = permissions
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).not.toContain('新建权限')
    expect(wrapper.find('[data-testid="update-menu-open"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="delete-menu-open"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="detail-menu-open"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="menu-list-open"]').exists()).toBe(true)
    expect(mocks.getMenuList).toHaveBeenCalledOnce()
  })

  it('loads governance status and refreshes the complete flat menu list on demand', async () => {
    mocks.roles = ['ADMIN']
    let resolveList: ((value: SysMenuVO[]) => void) | undefined
    mocks.getMenuList
      .mockReset()
      .mockResolvedValueOnce(flatMenus)
      .mockImplementationOnce(
        () =>
          new Promise<SysMenuVO[]>((resolve) => {
            resolveList = resolve
          }),
      )
    const wrapper = mountPage()
    await flushPromises()

    expect(mocks.getMenuList).toHaveBeenCalledOnce()
    await wrapper.get('[data-testid="menu-list-open"]').trigger('click')
    await wrapper.vm.$nextTick()
    expect(mocks.getMenuList).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="menu-list-loading"]').exists()).toBe(true)

    resolveList?.(flatMenus)
    await flushPromises()

    const rows = wrapper.findAll('[data-testid="menu-list-row"]')
    expect(rows).toHaveLength(3)
    const listText = wrapper.get('[data-testid="menu-list-table"]').text()
    expect(listText).toContain('系统管理')
    expect(listText).toContain('目录')
    expect(listText).toContain('菜单概览')
    expect(listText).toContain('/system/overview')
    expect(listText).toContain('查询按钮')
    expect(listText).toContain('system:menu:query')
    expect(listText).toContain('按钮')
    expect(listText).toContain('停用')
    expect(listText).toContain('隐藏')
    expect(listText).not.toContain('tenantId')
    expect(listText).not.toContain('deletedFlag')
    expect(listText).not.toContain('createdAt')
    expect(listText).not.toContain('children')
  })

  it('shows an empty state for an empty flat menu list', async () => {
    mocks.roles = ['SUPER_ADMIN']
    mocks.getMenuList.mockResolvedValueOnce(flatMenus).mockResolvedValueOnce([])
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="menu-list-open"]').trigger('click')
    await flushPromises()

    expect(mocks.getMenuList).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="menu-list-empty"]').text()).toContain('暂无菜单')
  })

  it('keeps the list entry open and shows an understandable error when loading fails', async () => {
    mocks.roles = ['ADMIN']
    mocks.getMenuList
      .mockResolvedValueOnce(flatMenus)
      .mockRejectedValueOnce(new Error('菜单列表暂不可用'))
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="menu-list-open"]').trigger('click')
    await flushPromises()

    expect(mocks.getMenuList).toHaveBeenCalledTimes(2)
    expect(mocks.error).toHaveBeenCalledWith('菜单列表暂不可用')
    expect(wrapper.get('[data-testid="menu-list-error"]').text()).toContain('菜单列表暂不可用')
    expect(wrapper.find('[data-testid="menu-list-modal"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="menu-list-open"]').exists()).toBe(true)
  })

  it('refreshes deterministically when the flat menu list is reopened', async () => {
    mocks.roles = ['ADMIN']
    mocks.getMenuList
      .mockReset()
      .mockResolvedValueOnce(flatMenus)
      .mockResolvedValueOnce([flatMenus[0]])
      .mockResolvedValueOnce([flatMenus[2]])
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.get('[data-testid="menu-list-open"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="menu-list-table"]').text()).toContain('系统管理')

    await wrapper.get('[data-testid="modal-cancel"]').trigger('click')
    expect(wrapper.find('[data-testid="menu-list-modal"]').exists()).toBe(false)
    await wrapper.get('[data-testid="menu-list-open"]').trigger('click')
    await flushPromises()

    expect(mocks.getMenuList).toHaveBeenCalledTimes(3)
    const listText = wrapper.get('[data-testid="menu-list-table"]').text()
    expect(listText).toContain('查询按钮')
    expect(listText).not.toContain('系统管理')
  })

  it('offers every menu-tree node as a detail target and loads the selected detail once', async () => {
    mocks.roles = ['ADMIN']
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="detail-menu-open"]').trigger('click')

    const options = wrapper.findAll('[data-testid="detail-menu-target"] option')
    expect(options.map((option) => option.attributes('value'))).toEqual(['10', '11', '12'])
    expect(options.map((option) => option.text())).toEqual(['系统管理', '菜单概览', '查询按钮'])

    await wrapper.get('[data-testid="detail-menu-target"]').setValue('11')
    await flushPromises()

    expect(mocks.getMenuDetail).toHaveBeenCalledOnce()
    expect(mocks.getMenuDetail).toHaveBeenCalledWith('11')
    expect(wrapper.get('[data-testid="detail-menu-name"]').text()).toBe('菜单概览')
    const detailText = wrapper.get('[data-testid="detail-menu-content"]').text()
    expect(detailText).toContain('菜单')
    expect(detailText).toContain('/system/overview')
    expect(detailText).toContain('system/overview/index')
    expect(detailText).toContain('menu')
    expect(detailText).toContain('启用')
    expect(detailText).toContain('可见')
    expect(detailText).not.toContain('tenantId')
    expect(detailText).not.toContain('createdAt')
    expect(detailText).not.toContain('children')
  })

  it('clears the previous detail while loading a new target', async () => {
    mocks.roles = ['ADMIN']
    let resolveSecondDetail: ((value: SysMenuVO) => void) | undefined
    mocks.getMenuDetail
      .mockReset()
      .mockResolvedValueOnce(menuDetail)
      .mockImplementationOnce(
        () =>
          new Promise<SysMenuVO>((resolve) => {
            resolveSecondDetail = resolve
          }),
      )
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="detail-menu-open"]').trigger('click')
    await wrapper.get('[data-testid="detail-menu-target"]').setValue('11')
    await flushPromises()
    expect(wrapper.get('[data-testid="detail-menu-name"]').text()).toBe('菜单概览')

    await wrapper.get('[data-testid="detail-menu-target"]').setValue('12')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[data-testid="detail-menu-content"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="detail-menu-loading"]').exists()).toBe(true)
    resolveSecondDetail?.({ ...menuDetail, id: '12', menuName: '查询按钮', menuType: 'BUTTON' })
    await flushPromises()
    expect(wrapper.get('[data-testid="detail-menu-name"]').text()).toBe('查询按钮')
  })

  it('keeps the selected target and shows an understandable error when detail loading fails', async () => {
    mocks.roles = ['ADMIN']
    mocks.getMenuDetail.mockRejectedValueOnce(new Error('菜单不存在或不可访问'))
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="detail-menu-open"]').trigger('click')
    await wrapper.get('[data-testid="detail-menu-target"]').setValue('12')
    await flushPromises()

    expect(mocks.getMenuDetail).toHaveBeenCalledOnce()
    expect(mocks.error).toHaveBeenCalledWith('菜单不存在或不可访问')
    expect(wrapper.get('[data-testid="detail-menu-error"]').text()).toContain(
      '菜单不存在或不可访问',
    )
    expect(wrapper.find('[data-testid="detail-menu-content"]').exists()).toBe(false)
    expect(
      (wrapper.get('[data-testid="detail-menu-target"]').element as HTMLSelectElement).value,
    ).toBe('12')
  })

  it('offers the complete menu tree as update targets and loads the current business fields', async () => {
    mocks.roles = ['ADMIN']
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="update-menu-open"]').trigger('click')

    const options = wrapper.findAll('[data-testid="update-menu-target"] option')
    expect(options.map((option) => option.attributes('value'))).toEqual(['10', '11', '12'])

    await wrapper.get('[data-testid="update-menu-target"]').setValue('11')
    await flushPromises()

    expect(mocks.getMenuDetail).toHaveBeenCalledOnce()
    expect(mocks.getMenuDetail).toHaveBeenCalledWith('11')
    expect(
      (wrapper.get('[data-testid="update-menu-name"]').element as HTMLInputElement).value,
    ).toBe('菜单概览')
    expect(
      (wrapper.get('[data-testid="update-menu-perms"]').element as HTMLInputElement).value,
    ).toBe('')
  })

  it('clears the previous update form while a new target detail is loading', async () => {
    mocks.roles = ['ADMIN']
    let resolveSecondDetail: ((value: SysMenuVO) => void) | undefined
    mocks.getMenuDetail
      .mockReset()
      .mockResolvedValueOnce(menuDetail)
      .mockImplementationOnce(
        () =>
          new Promise<SysMenuVO>((resolve) => {
            resolveSecondDetail = resolve
          }),
      )
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="update-menu-open"]').trigger('click')
    await wrapper.get('[data-testid="update-menu-target"]').setValue('11')
    await flushPromises()
    expect(wrapper.get('[data-testid="update-menu-name"]').attributes('value')).toBe('菜单概览')

    await wrapper.get('[data-testid="update-menu-target"]').setValue('12')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[data-testid="update-menu-name"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="update-menu-modal"]').text()).toContain('正在加载菜单详情')
    resolveSecondDetail?.({ ...menuDetail, id: '12', menuName: '查询按钮', menuType: 'BUTTON' })
    await flushPromises()
    expect(wrapper.get('[data-testid="update-menu-name"]').attributes('value')).toBe('查询按钮')
  })

  it('submits business fields only and refreshes the tree, flat list, and detail', async () => {
    mocks.roles = ['SUPER_ADMIN']
    const updatedDetail: SysMenuVO = {
      ...menuDetail,
      menuName: '菜单概览（修改）',
      path: '/system/overview-v2',
      perms: 'system:menu:edit',
      orderNum: 8,
      status: 'DISABLE',
      visible: 0,
    }
    const updatedTree: MenuTreeVO[] = [
      {
        ...menuTree[0],
        children: menuTree[0].children?.map((menu) =>
          String(menu.id) === '11' ? { ...menu, ...updatedDetail } : menu,
        ),
      },
    ]
    mocks.getMenuDetail
      .mockReset()
      .mockResolvedValueOnce(menuDetail)
      .mockResolvedValueOnce(updatedDetail)
    mocks.getMenuTree.mockReset().mockResolvedValueOnce(menuTree).mockResolvedValueOnce(updatedTree)
    mocks.getMenuList
      .mockReset()
      .mockResolvedValueOnce(flatMenus)
      .mockResolvedValueOnce([updatedDetail])
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="update-menu-open"]').trigger('click')
    await wrapper.get('[data-testid="update-menu-target"]').setValue('11')
    await flushPromises()
    await wrapper.get('[data-testid="update-menu-name"]').setValue(' 菜单概览（修改） ')
    await wrapper.get('[data-testid="update-menu-path"]').setValue(' /system/overview-v2 ')
    await wrapper.get('[data-testid="update-menu-perms"]').setValue(' system:menu:edit ')
    await wrapper.get('[data-testid="update-menu-order"]').setValue('8')
    await wrapper.get('[data-testid="update-menu-status"]').setValue('DISABLE')
    await wrapper.get('[data-testid="update-menu-visible"]').setValue('0')
    await wrapper.get('[data-testid="update-menu-submit"]').trigger('click')
    await flushPromises()

    expect(mocks.updateMenu).toHaveBeenCalledWith('11', {
      parentId: '10',
      menuName: '菜单概览（修改）',
      menuType: 'MENU',
      path: '/system/overview-v2',
      component: 'system/overview/index',
      perms: 'system:menu:edit',
      icon: 'menu',
      orderNum: 8,
      status: 'DISABLE',
      visible: 0,
    })
    const payload = mocks.updateMenu.mock.calls[0][1]
    expect(Object.keys(payload).sort()).toEqual(
      [
        'parentId',
        'menuName',
        'menuType',
        'path',
        'component',
        'perms',
        'icon',
        'orderNum',
        'status',
        'visible',
      ].sort(),
    )
    expect(mocks.getMenuTree).toHaveBeenCalledTimes(2)
    expect(mocks.getMenuList).toHaveBeenCalledTimes(2)
    expect(mocks.getMenuDetail).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="update-menu-name"]').attributes('value')).toBe(
      '菜单概览（修改）',
    )
    expect(mocks.success).toHaveBeenCalledWith('菜单“菜单概览（修改）”已修改')
  })

  it('keeps the update form and target when the update is rejected', async () => {
    mocks.roles = ['ADMIN']
    mocks.updateMenu.mockRejectedValueOnce(new Error('父菜单不可用'))
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="update-menu-open"]').trigger('click')
    await wrapper.get('[data-testid="update-menu-target"]').setValue('11')
    await flushPromises()
    await wrapper.get('[data-testid="update-menu-name"]').setValue('保留的修改值')
    await wrapper.get('[data-testid="update-menu-submit"]').trigger('click')
    await flushPromises()

    expect(mocks.getMenuTree).toHaveBeenCalledOnce()
    expect(mocks.getMenuList).toHaveBeenCalledOnce()
    expect(mocks.getMenuDetail).toHaveBeenCalledOnce()
    expect(mocks.success).not.toHaveBeenCalled()
    expect(mocks.error).toHaveBeenCalledWith('父菜单不可用')
    expect(wrapper.get('[data-testid="update-menu-error"]').text()).toContain('父菜单不可用')
    expect(
      (wrapper.get('[data-testid="update-menu-target"]').element as HTMLSelectElement).value,
    ).toBe('11')
    expect(wrapper.get('[data-testid="update-menu-name"]').attributes('value')).toBe('保留的修改值')
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
    expect(mocks.error).toHaveBeenCalledWith('菜单已删除，但刷新权限清单失败：刷新菜单树失败')
    expect(wrapper.find('[data-testid="delete-menu-modal"]').exists()).toBe(true)
    expect(
      (wrapper.get('[data-testid="delete-menu-target"]').element as HTMLSelectElement).value,
    ).toBe('11')
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
    expect(
      (wrapper.get('[data-testid="delete-menu-target"]').element as HTMLSelectElement).value,
    ).toBe('12')
  })

  it('keeps the existing route and does not add sort or destructive bypass actions', () => {
    const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
    const routerSource = readFileSync(resolve(currentDir, '../../../../router/index.ts'), 'utf-8')
    const navigationSource = readFileSync(
      resolve(currentDir, '../../../../router/navigation.ts'),
      'utf-8',
    )

    expect(routerSource).toContain("path: 'permissions'")
    expect(routerSource).toContain("name: 'SystemPermissions'")
    expect(navigationSource).toContain("key: '/system-management/access-control'")
    expect(navigationSource).toContain("key: '/system/permissions'")
    expect(navigationSource).toContain("label: '权限清单'")
    expect(source).toContain('updateMenu')
    expect(source).toContain('deleteMenu')
    expect(source).toContain('getMenuDetail')
    expect(source).not.toContain('<h1>')
    expect(source).not.toContain('统一查看与维护系统功能权限及接口授权')
    expect(source).not.toContain('createMenu')
    expect(source).not.toContain('新建权限')
    expect(source).not.toContain('级联删除选项')
    expect(source).not.toContain('强制解绑选项')
    expect(source).not.toContain('拖拽排序')
  })
})
