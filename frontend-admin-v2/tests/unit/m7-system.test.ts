import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { V2ConfirmDialog, V2Input } from '@/components'
import AccessControlPage from '@/pages/system/AccessControlPage.vue'
import DataMaintenancePage from '@/pages/system/DataMaintenancePage.vue'
import {
  bindDefaultDocumentVersion,
  clearNonProductionDatabase,
  loadAuditLogs,
  loadUsers,
} from '@/services/system-management'
import { apiRequest } from '@/services/request'
import { useSessionStore } from '@/stores/session'

vi.mock('@/services/request', () => ({
  apiRequest: vi.fn(),
  isApiClientError: vi.fn(() => false),
}))

beforeEach(() => {
  setActivePinia(createPinia())
  vi.mocked(apiRequest).mockReset()
})

describe('M7 system management contracts', () => {
  it('normalizes server identifiers without deriving list facts locally', async () => {
    vi.mocked(apiRequest).mockResolvedValue({
      pageNo: '1',
      pageSize: '20',
      total: '1',
      records: [{ id: 7, username: 'server.user', status: 'ENABLE', roleIds: [1] }],
    })

    const page = await loadUsers({ pageNo: 1, pageSize: 20, username: 'server user' })

    expect(apiRequest).toHaveBeenCalledWith(
      '/system/users?pageNo=1&pageSize=20&username=server+user',
      { signal: undefined },
    )
    expect(page).toEqual({
      pageNo: 1,
      pageSize: 20,
      total: 1,
      records: [
        {
          id: '7',
          username: 'server.user',
          status: 'ENABLE',
          roleIds: ['1'],
          roleNames: [],
          orgId: undefined,
        },
      ],
    })
  })

  it('uses read-only audit and optimistic default-binding endpoints', async () => {
    vi.mocked(apiRequest).mockResolvedValueOnce({
      pageNo: 1,
      pageSize: 20,
      total: 1,
      records: [{ id: 9, userId: 7 }],
    })
    await loadAuditLogs({ pageNo: 1, pageSize: 20, businessType: 'PAYMENT' })
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/audit-logs?pageNo=1&pageSize=20&businessType=PAYMENT',
      { signal: undefined },
    )

    vi.mocked(apiRequest).mockResolvedValueOnce(undefined)
    await bindDefaultDocumentVersion('12', 3)
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/document-templates/versions/12/default?expectedLockVersion=3',
      { method: 'PUT' },
    )
  })

  it('requires typed acknowledgement and final confirmation before mocked database clear', async () => {
    vi.mocked(apiRequest).mockResolvedValue('已清空 0 张业务数据表')
    const wrapper = mount(DataMaintenancePage)
    const action = wrapper
      .findAll('button')
      .find((button) => button.text() === '清空非生产业务数据')!

    expect(action.attributes('disabled')).toBeDefined()
    expect(apiRequest).not.toHaveBeenCalled()
    await wrapper.findComponent(V2Input).find('input').setValue('CLEAR_NON_PROD_DATABASE')
    await wrapper.get('input[type="checkbox"]').setValue(true)
    await action.trigger('click')
    expect(wrapper.findComponent(V2ConfirmDialog).props('open')).toBe(true)
    expect(apiRequest).not.toHaveBeenCalled()

    wrapper.findComponent(V2ConfirmDialog).vm.$emit('confirm')
    await flushPromises()

    expect(apiRequest).toHaveBeenCalledWith(
      '/system/clear-database?confirm=CLEAR_NON_PROD_DATABASE',
      { method: 'DELETE' },
    )
  })

  it('keeps the destructive service callable only through the exact backend contract', async () => {
    vi.mocked(apiRequest).mockResolvedValue('ok')
    await expect(clearNonProductionDatabase()).resolves.toBe('ok')
    expect(apiRequest).toHaveBeenCalledOnce()
  })

  it('configures role permissions through the collapsible directory-menu-button tree', async () => {
    let projectManagerMenuIds = ['12']
    vi.mocked(apiRequest).mockImplementation(async (path, options) => {
      if (path === '/system/roles') {
        return [
          {
            id: 1,
            roleCode: 'PROJECT_MANAGER',
            roleName: '项目经理',
            status: 'ENABLE',
            dataScope: 'SELF',
            menuIds: projectManagerMenuIds,
          },
          {
            id: 2,
            roleCode: 'BUSINESS_MANAGER',
            roleName: '商务经理',
            status: 'ENABLE',
            dataScope: 'SELF',
            menuIds: ['10'],
          },
        ]
      }
      if (path === '/system/menus') {
        return [
          {
            id: 10,
            parentId: 0,
            menuName: '项目管理',
            menuType: 'DIR',
            orderNum: 1,
            status: 'ENABLE',
            visible: 1,
          },
          {
            id: 11,
            parentId: 10,
            menuName: '项目列表',
            menuType: 'MENU',
            path: '/project/list',
            orderNum: 1,
            status: 'ENABLE',
            visible: 1,
          },
          {
            id: 12,
            parentId: 11,
            menuName: '新建项目',
            menuType: 'BUTTON',
            perms: 'project:create',
            orderNum: 1,
            status: 'ENABLE',
            visible: 1,
          },
          {
            id: 803,
            parentId: 0,
            menuName: '项目查询',
            menuType: 'BUTTON',
            perms: 'project:query',
            orderNum: 2,
            status: 'ENABLE',
            visible: 1,
          },
          {
            id: 20,
            parentId: 0,
            menuName: '计划与现场',
            menuType: 'DIR',
            orderNum: 2,
            status: 'ENABLE',
            visible: 1,
          },
          {
            id: 21,
            parentId: 20,
            menuName: '项目计划',
            menuType: 'MENU',
            path: '/project-schedule',
            perms: 'schedule:query',
            orderNum: 1,
            status: 'ENABLE',
            visible: 1,
          },
          {
            id: 22,
            parentId: 21,
            menuName: '现场日报',
            menuType: 'MENU',
            path: '/site/daily-log',
            perms: 'site:daily:query',
            orderNum: 2,
            status: 'ENABLE',
            visible: 1,
          },
          {
            id: 730,
            parentId: 0,
            menuName: '消息中心',
            menuType: 'DIR',
            orderNum: 3,
            status: 'ENABLE',
            visible: 0,
          },
          {
            id: 761,
            parentId: 730,
            menuName: '顶栏通知中心',
            menuType: 'DIR',
            orderNum: 1,
            status: 'ENABLE',
            visible: 0,
          },
          {
            id: 763,
            parentId: 761,
            menuName: '查看消息',
            menuType: 'BUTTON',
            perms: 'notification:view',
            orderNum: 1,
            status: 'ENABLE',
            visible: 1,
          },
          {
            id: 503,
            parentId: 0,
            menuName: '旧菜单配置',
            menuType: 'MENU',
            path: 'menu',
            orderNum: 9,
            status: 'ENABLE',
            visible: 0,
          },
        ]
      }
      if (path === '/system/roles/1/menus' && options?.method === 'PUT') {
        projectManagerMenuIds = [...((options.body as { menuIds: string[] }).menuIds ?? [])]
        return undefined
      }
      if (path === '/system/roles/1') {
        return {
          id: 1,
          roleCode: 'PROJECT_MANAGER',
          roleName: '项目经理',
          status: 'ENABLE',
          dataScope: 'SELF',
          menuIds: projectManagerMenuIds,
        }
      }
      if (path === '/system/roles/2') {
        return {
          id: 2,
          roleCode: 'BUSINESS_MANAGER',
          roleName: '商务经理',
          status: 'ENABLE',
          dataScope: 'SELF',
          menuIds: ['10'],
        }
      }
      throw new Error(`unexpected request: ${path}`)
    })
    useSessionStore().replaceUserInfo({
      userId: '1',
      username: 'admin',
      roles: ['SUPER_ADMIN'],
      permissions: [],
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/system/permissions', component: AccessControlPage }],
    })
    await router.push('/system/permissions')
    await router.isReady()
    const wrapper = mount(AccessControlPage, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.find('.access-control-page__description').exists()).toBe(false)
    expect(wrapper.get('.permission-role-list__item').text()).toContain('项目经理1 项')
    expect(wrapper.get('.permission-role-list__item').text()).not.toContain('PROJECT_MANAGER')
    expect(wrapper.get('.permission-role-list').attributes('role')).toBe('group')
    expect(wrapper.get('.permission-role-list__item').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('table[data-table-identity="contextual"]').classes()).toContain(
      'v2-table--compact',
    )
    const domain = wrapper.get('input[aria-label="项目履约权限"]')
    const directory = wrapper.get('input[aria-label="项目管理权限"]')
    const list = wrapper.get('input[aria-label="项目列表权限"]')
    const create = wrapper.get('input[aria-label="新建项目权限"]')
    const projectQuery = wrapper.get('input[aria-label="项目查询权限"]')
    const dailyLog = wrapper.get('input[aria-label="现场日报权限"]')
    expect((domain.element as HTMLInputElement).indeterminate).toBe(true)
    expect((directory.element as HTMLInputElement).indeterminate).toBe(true)
    expect((list.element as HTMLInputElement).indeterminate).toBe(true)
    expect((create.element as HTMLInputElement).checked).toBe(true)
    expect((projectQuery.element as HTMLInputElement).checked).toBe(false)
    expect(dailyLog.attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).toContain('全局功能')
    expect(wrapper.text()).toContain('顶栏通知中心')
    expect(wrapper.text()).toContain('待治理配置（非导航入口）')
    expect(wrapper.text()).not.toContain('未纳入导航栏')

    await create.setValue(false)
    await create.setValue(true)
    const saveButton = wrapper.findAll('button').find((button) => button.text() === '保存权限')!
    expect(saveButton.attributes('disabled')).toBeDefined()

    await directory.setValue(true)
    expect((directory.element as HTMLInputElement).checked).toBe(true)
    await saveButton.trigger('click')
    await flushPromises()

    expect(apiRequest).toHaveBeenCalledWith('/system/roles/1/menus', {
      method: 'PUT',
      body: { menuIds: expect.arrayContaining(['10', '11', '12', '803']) },
    })
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '全部收起')!
      .trigger('click')
    await flushPromises()
    expect(wrapper.findAll('tbody tr')).toHaveLength(3)

    await wrapper
      .findAll('.permission-role-list__item')
      .find((button) => button.text().includes('商务经理'))!
      .trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '全部展开')!
      .trigger('click')
    expect(
      (wrapper.get('input[aria-label="项目管理权限"]').element as HTMLInputElement).checked,
    ).toBe(false)
  })

  it('uses the permission-workspace pattern to select role users without losing memberships', async () => {
    vi.mocked(apiRequest).mockImplementation(async (path) => {
      if (path.startsWith('/system/users?')) {
        const roleId = new URLSearchParams(path.split('?')[1]).get('roleId')
        const records =
          roleId === '2'
            ? [
                {
                  id: 2,
                  username: 'dual.user',
                  realName: '双岗成员',
                  status: 'ENABLE',
                  roleIds: [1, 2],
                  roleNames: ['超级管理员', '项目经理'],
                },
              ]
            : [
                {
                  id: 1,
                  username: 'admin',
                  realName: '平台管理员',
                  status: 'ENABLE',
                  roleIds: [1],
                  roleNames: ['超级管理员'],
                },
                {
                  id: 2,
                  username: 'dual.user',
                  realName: '双岗成员',
                  status: 'ENABLE',
                  roleIds: [1, 2],
                  roleNames: ['超级管理员', '项目经理'],
                },
              ]
        return {
          pageNo: 1,
          pageSize: 10,
          total: records.length,
          records,
        }
      }
      if (path === '/system/roles') {
        return [
          {
            id: 1,
            roleCode: 'SUPER_ADMIN',
            roleName: '超级管理员',
            status: 'ENABLE',
            dataScope: 'ALL',
            userCount: 2,
            menuIds: [],
          },
          {
            id: 2,
            roleCode: 'PROJECT_MANAGER',
            roleName: '项目经理',
            status: 'ENABLE',
            dataScope: 'SELF',
            userCount: 1,
            menuIds: [],
          },
        ]
      }
      throw new Error(`unexpected request: ${path}`)
    })
    useSessionStore().replaceUserInfo({
      userId: '1',
      username: 'admin',
      roles: ['SUPER_ADMIN'],
      permissions: [],
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/system/users', component: AccessControlPage }],
    })
    await router.push('/system/users')
    await router.isReady()
    const wrapper = mount(AccessControlPage, { global: { plugins: [router] } })
    await flushPromises()

    const roleButtons = wrapper.findAll('.user-role-list__item')
    expect(roleButtons).toHaveLength(2)
    expect(roleButtons[0]!.text()).toContain('超级管理员2 人')
    expect(roleButtons[1]!.text()).toContain('项目经理1 人')
    expect(roleButtons[0]!.attributes('aria-pressed')).toBe('true')
    const userTable = wrapper.get('.user-workspace__table')
    expect(userTable.classes()).toContain('v2-table--compact')
    expect(userTable.findAll('thead th').map((header) => header.text())).toEqual([
      '用户名',
      '姓名',
      '联系方式',
      '状态',
      '操作',
    ])
    expect(userTable.findAll('tbody tr')).toHaveLength(2)
    expect(userTable.text()).toContain('平台管理员')
    expect(userTable.text()).toContain('双岗成员')

    await roleButtons[1]!.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.user-role-list__item')[1]!.attributes('aria-pressed')).toBe('true')
    const projectManagerTable = wrapper.get('.user-workspace__table')
    expect(projectManagerTable.findAll('tbody tr')).toHaveLength(1)
    expect(projectManagerTable.text()).toContain('双岗成员')
    expect(projectManagerTable.text()).not.toContain('平台管理员')
    expect(wrapper.text()).toContain('共 1 条')
    expect(apiRequest).toHaveBeenCalledWith('/system/users?pageNo=1&pageSize=10&roleId=2', {
      signal: expect.any(AbortSignal),
    })
  })

  it('keeps role editing separate from permission assignment', async () => {
    vi.mocked(apiRequest).mockImplementation(async (path, options) => {
      if (path === '/system/roles') {
        return [
          {
            id: 1,
            roleCode: 'PROJECT_MANAGER',
            roleName: '项目经理',
            roleType: 'CUSTOM',
            status: 'ENABLE',
            dataScope: 'DEPT_AND_CHILD',
            menuIds: ['12'],
          },
        ]
      }
      if (path === '/system/roles/1' && options?.method === 'PUT') return undefined
      if (path === '/system/roles/1') {
        return {
          id: 1,
          roleCode: 'PROJECT_MANAGER',
          roleName: '项目经理',
          roleType: 'CUSTOM',
          status: 'ENABLE',
          dataScope: 'DEPT_AND_CHILD',
          menuIds: ['12'],
        }
      }
      throw new Error(`unexpected request: ${path}`)
    })
    useSessionStore().replaceUserInfo({
      userId: '1',
      username: 'admin',
      roles: ['SUPER_ADMIN'],
      permissions: [],
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/system/roles', component: AccessControlPage }],
    })
    await router.push('/system/roles')
    await router.isReady()
    const wrapper = mount(AccessControlPage, {
      attachTo: document.body,
      global: { plugins: [router] },
    })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '编辑')!
      .trigger('click')
    await flushPromises()
    const dialog = document.querySelector<HTMLElement>('.v2-dialog__panel')!
    expect(dialog.textContent).not.toContain('数据范围')
    expect(dialog.textContent).not.toContain('菜单与权限')
    expect(dialog.textContent).not.toContain('contract:submit')

    ;[...dialog.querySelectorAll('button')]
      .find((button) => button.textContent?.trim() === '保存')!
      .click()
    await flushPromises()

    expect(apiRequest).toHaveBeenCalledWith('/system/roles/1', {
      method: 'PUT',
      body: {
        roleCode: 'PROJECT_MANAGER',
        roleName: '项目经理',
        status: 'ENABLE',
        dataScope: 'DEPT_AND_CHILD',
      },
    })
    expect(
      vi
        .mocked(apiRequest)
        .mock.calls.some(
          ([path, options]) => path === '/system/roles/1/menus' && options?.method === 'PUT',
        ),
    ).toBe(false)
    expect(vi.mocked(apiRequest).mock.calls.some(([path]) => path === '/system/menus')).toBe(false)
    wrapper.unmount()
  })
})
