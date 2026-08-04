import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AccessControlPage from '@/pages/system/AccessControlPage.vue'
import DataMaintenancePage from '@/pages/system/DataMaintenancePage.vue'
import {
  bindDefaultDocumentVersion,
  loadAuditLogs,
  loadDataMaintenancePreview,
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

  it('renders the server data-maintenance preview without exposing a destructive action', async () => {
    vi.mocked(apiRequest).mockResolvedValue({
      database: 'cgc_pms_dev',
      policyFingerprint: 'sha256:test-policy',
      eligible: false,
      blockers: ['检测到生产环境标记'],
      retainedGroups: [{ code: 'IDENTITY_ACCESS', tableCount: 4, rowCount: 28 }],
      clearTableCount: 12,
      clearRowCount: 345,
      sysFileCount: 6,
      ignoredViews: ['v_project_summary'],
    })
    const wrapper = mount(DataMaintenancePage)
    await flushPromises()

    expect(apiRequest).toHaveBeenCalledOnce()
    expect(apiRequest).toHaveBeenCalledWith('/system/data-maintenance/preview')
    expect(wrapper.text()).toContain('cgc_pms_dev')
    expect(wrapper.text()).toContain('检测到生产环境标记')
    expect(wrapper.text()).toContain('IDENTITY_ACCESS')
    expect(wrapper.text()).toContain('sha256:test-policy')
    expect(wrapper.text()).toContain('12 张表 / 345 行 / 6 个文件')
    expect(wrapper.text()).toContain('4 张表 / 28 行')
    expect(wrapper.text()).toContain('v_project_summary')
    expect(wrapper.text()).toContain(
      'pwsh -NoProfile -File scripts/database/clear-business-data.ps1 -Database cgc_pms_dev',
    )
    expect(wrapper.text()).not.toContain('确认清空')
    expect(
      vi
        .mocked(apiRequest)
        .mock.calls.some(
          ([path, options]) => path.includes('clear-database') || options?.method === 'DELETE',
        ),
    ).toBe(false)
  })

  it('uses only the read-only data-maintenance preview contract', async () => {
    vi.mocked(apiRequest).mockResolvedValue({})
    await loadDataMaintenancePreview()
    expect(apiRequest).toHaveBeenCalledWith('/system/data-maintenance/preview')
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
    const dailyLog = wrapper.get('input[aria-label="施工履约权限"]')
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
    expect(wrapper.findAll('tbody tr')).toHaveLength(4)

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

  it('uses one role-user workspace without losing memberships', async () => {
    let dualStatus = 'ENABLE'
    vi.mocked(apiRequest).mockImplementation(async (path, options) => {
      if (path === '/system/users/2/status' && options?.method === 'PATCH') {
        dualStatus = String(options.body?.status)
        return undefined
      }
      if (path.startsWith('/system/users?')) {
        const roleId = new URLSearchParams(path.split('?')[1]).get('roleId')
        const records =
          roleId === '2'
            ? [
                {
                  id: 2,
                  username: 'dual.user',
                  realName: '双岗成员',
                  status: dualStatus,
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
                  status: dualStatus,
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

    expect(wrapper.findAll('.v2-card')).toHaveLength(2)
    expect(wrapper.findAll('.user-workspace > section')).toHaveLength(2)
    expect(wrapper.get('#user-workspace-roles-title').text()).toBe('1. 角色')
    expect(wrapper.get('#user-workspace-users-title').text()).toBe('2. 用户')
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
    const statusSwitch = projectManagerTable.get('button[role="switch"]')
    expect(statusSwitch.attributes('aria-checked')).toBe('true')
    expect(statusSwitch.attributes('disabled')).toBeUndefined()
    expect(statusSwitch.classes()).toContain('is-enabled')
    expect(projectManagerTable.find('input[role="switch"]').exists()).toBe(false)
    expect(
      projectManagerTable
        .findAll('.access-control-page__actions button')
        .some((candidate) => ['启用', '停用'].includes(candidate.text())),
    ).toBe(false)

    await statusSwitch.trigger('click')
    await flushPromises()
    const confirmDialog = document.body.querySelector<HTMLElement>('.v2-confirm-dialog')!
    expect(confirmDialog.textContent).toContain('确认更新用户状态')
    ;[...confirmDialog.querySelectorAll('button')]
      .find((candidate) => candidate.textContent?.trim() === '停用')!
      .click()
    await flushPromises()

    expect(apiRequest).toHaveBeenCalledWith('/system/users/2/status', {
      method: 'PATCH',
      body: { status: 'DISABLE' },
    })
    expect(
      wrapper.get('.user-workspace__table button[role="switch"]').attributes('aria-checked'),
    ).toBe('false')
    expect(wrapper.get('.user-workspace__table button[role="switch"]').classes()).toContain(
      'is-disabled',
    )

    useSessionStore().replaceUserInfo({
      userId: '3',
      username: 'viewer',
      roles: ['USER'],
      permissions: ['system:user:query'],
    })
    await flushPromises()
    expect(wrapper.get('.user-workspace__table button[role="switch"]').attributes('disabled')).toBe(
      '',
    )
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

  it('updates an editable role through the shared status pill and rereads the list', async () => {
    vi.mocked(apiRequest).mockImplementation(async (path, options) => {
      if (path === '/system/roles') {
        return [
          {
            id: 2,
            roleCode: 'PROJECT_MANAGER',
            roleName: '项目经理',
            roleType: 'CUSTOM',
            status: 'ENABLE',
            dataScope: 'SELF',
            menuIds: [],
          },
        ]
      }
      if (path === '/system/roles/2') {
        if (options?.method === 'PUT') return undefined
        return {
          id: 2,
          roleCode: 'PROJECT_MANAGER',
          roleName: '项目经理',
          roleType: 'CUSTOM',
          status: 'ENABLE',
          dataScope: 'SELF',
          menuIds: [],
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

    const statusSwitch = wrapper.get('button[role="switch"]')
    expect(statusSwitch.attributes('aria-checked')).toBe('true')
    expect(wrapper.find('input[role="switch"]').exists()).toBe(false)
    await statusSwitch.trigger('click')
    await flushPromises()
    const confirmDialog = document.body.querySelector<HTMLElement>('.v2-confirm-dialog')!
    ;[...confirmDialog.querySelectorAll('button')]
      .find((candidate) => candidate.textContent?.trim() === '停用')!
      .click()
    await flushPromises()

    expect(apiRequest).toHaveBeenCalledWith('/system/roles/2', {
      method: 'PUT',
      body: {
        roleCode: 'PROJECT_MANAGER',
        roleName: '项目经理',
        status: 'DISABLE',
        dataScope: 'SELF',
      },
    })
    expect(
      vi.mocked(apiRequest).mock.calls.filter(([path]) => path === '/system/roles'),
    ).toHaveLength(2)
    wrapper.unmount()
  })
})
