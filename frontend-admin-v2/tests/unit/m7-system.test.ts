import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PermissionListPage from '@/pages/system/access-control/PermissionListPage.vue'
import RoleManagementPage from '@/pages/system/access-control/RoleManagementPage.vue'
import UserManagementPage from '@/pages/system/access-control/UserManagementPage.vue'
import DataMaintenancePage from '@/pages/system/DataMaintenancePage.vue'
import {
  bindDefaultDocumentVersion,
  loadAuditLogs,
  loadDataMaintenancePreview,
  loadRoles,
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
            roleCode: 'TECHNICAL_LEAD',
            roleName: '技术负责人',
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
          roleCode: 'TECHNICAL_LEAD',
          roleName: '技术负责人',
          status: 'ENABLE',
          dataScope: 'SELF',
          menuIds: ['10'],
        }
      }
      throw new Error(`unexpected request: ${path}`)
    })
    useSessionStore().replaceUserInfo({
      tenantId: '1001',
      userId: '1',
      username: 'admin',
      roles: ['SUPER_ADMIN'],
      permissions: [],
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/system/permissions', component: PermissionListPage }],
    })
    await router.push('/system/permissions')
    await router.isReady()
    const wrapper = mount(PermissionListPage, { global: { plugins: [router] } })
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
      .find((button) => button.text().includes('技术负责人'))!
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
      if (path === '/system/users/1') {
        return {
          id: 1,
          username: 'admin',
          realName: '平台管理员',
          phone: '13800000001',
          email: 'admin@example.com',
          orgId: '总部',
          status: 'ENABLE',
          roleIds: [1, 99],
          roleNames: ['公司财务', '超级管理员'],
          createdAt: '2026-08-01 09:00:00',
        }
      }
      if (path === '/system/users/2') {
        return {
          id: 2,
          username: 'dual.user',
          realName: '双岗成员',
          phone: '13800000002',
          email: 'dual@example.com',
          orgId: '项目部',
          status: dualStatus,
          roleIds: [1, 2, 99],
          roleNames: ['公司财务', '项目经理', '超级管理员'],
          createdAt: '2026-08-02 10:00:00',
        }
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
                  roleIds: [1, 2, 99],
                  roleNames: ['公司财务', '项目经理', '超级管理员'],
                },
              ]
            : [
                {
                  id: 1,
                  username: 'admin',
                  realName: '平台管理员',
                  status: 'ENABLE',
                  roleIds: [1, 99],
                  roleNames: ['公司财务', '超级管理员'],
                },
                {
                  id: 2,
                  username: 'dual.user',
                  realName: '双岗成员',
                  status: dualStatus,
                  roleIds: [1, 2, 99],
                  roleNames: ['公司财务', '项目经理', '超级管理员'],
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
            roleCode: 'COMPANY_FINANCE',
            roleName: '公司财务',
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
          {
            id: 99,
            roleCode: 'SUPER_ADMIN',
            roleName: '超级管理员',
            status: 'ENABLE',
            dataScope: 'ALL',
            userCount: 2,
            menuIds: [],
          },
        ]
      }
      throw new Error(`unexpected request: ${path}`)
    })
    useSessionStore().replaceUserInfo({
      tenantId: '1001',
      userId: '1',
      username: 'admin',
      roles: ['SUPER_ADMIN'],
      permissions: [],
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/system/users', component: UserManagementPage }],
    })
    await router.push('/system/users')
    await router.isReady()
    const wrapper = mount(UserManagementPage, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.findAll('.v2-card')).toHaveLength(2)
    expect(wrapper.findAll('.user-workspace > section')).toHaveLength(3)
    expect(wrapper.get('#user-workspace-roles-title').text()).toBe('1. 角色')
    expect(wrapper.get('#user-workspace-users-title').text()).toBe('2. 用户')
    expect(wrapper.get('#user-workspace-detail-title').text()).toBe('3. 详情')
    const roleButtons = wrapper.findAll('.user-role-list__item')
    expect(roleButtons).toHaveLength(2)
    expect(roleButtons[0]!.text()).toContain('公司财务2 人')
    expect(roleButtons[1]!.text()).toContain('项目经理1 人')
    expect(wrapper.text()).not.toContain('超级管理员')
    expect(roleButtons[0]!.attributes('aria-pressed')).toBe('true')
    const userList = wrapper.get('.user-workspace__list')
    expect(userList.attributes('role')).toBe('listbox')
    expect(userList.findAll('[role="option"]')).toHaveLength(2)
    expect(userList.findAll('[role="option"]')[0]!.attributes('aria-selected')).toBe('true')
    expect(userList.text()).toContain('平台管理员')
    expect(userList.text()).toContain('双岗成员')
    expect(wrapper.get('.user-workspace__details').text()).toContain('admin@example.com')
    expect(wrapper.get('.user-workspace__details').text()).toContain('总部')
    expect(apiRequest).toHaveBeenCalledWith('/system/users/1')

    await roleButtons[1]!.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.user-role-list__item')[1]!.attributes('aria-pressed')).toBe('true')
    const projectManagerList = wrapper.get('.user-workspace__list')
    expect(projectManagerList.findAll('[role="option"]')).toHaveLength(1)
    expect(projectManagerList.text()).toContain('双岗成员')
    expect(projectManagerList.text()).not.toContain('平台管理员')
    expect(wrapper.get('.user-workspace__details').text()).toContain('dual@example.com')
    expect(wrapper.text()).toContain('共 1 条')
    expect(apiRequest).toHaveBeenCalledWith('/system/users?pageNo=1&pageSize=10&roleId=2', {
      signal: expect.any(AbortSignal),
    })
    const statusSwitch = projectManagerList.get('button[role="switch"]')
    expect(statusSwitch.attributes('aria-checked')).toBe('true')
    expect(statusSwitch.attributes('disabled')).toBeUndefined()
    expect(statusSwitch.classes()).toContain('is-enabled')
    expect(projectManagerList.find('input[role="switch"]').exists()).toBe(false)
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
      wrapper.get('.user-workspace__list button[role="switch"]').attributes('aria-checked'),
    ).toBe('false')
    expect(wrapper.get('.user-workspace__list button[role="switch"]').classes()).toContain(
      'is-disabled',
    )
    expect(wrapper.get('.user-workspace__details').text()).toContain('停用')

    useSessionStore().replaceUserInfo({
      tenantId: '1001',
      userId: '3',
      username: 'viewer',
      roles: ['USER'],
      permissions: ['system:user:query'],
    })
    await flushPromises()
    expect(wrapper.get('.user-workspace__list button[role="switch"]').attributes('disabled')).toBe(
      '',
    )
    expect(wrapper.find('.user-workspace__user-actions .v2-action-menu').exists()).toBe(false)
  })

  it('isolates stale user detail responses and clears detail for an empty page', async () => {
    let resolveFirstDetail!: (value: unknown) => void
    const firstDetail = new Promise((resolve) => {
      resolveFirstDetail = resolve
    })
    vi.mocked(apiRequest).mockImplementation(async (path) => {
      if (path === '/system/roles') {
        return [
          {
            id: 1,
            roleCode: 'PROJECT_MANAGER',
            roleName: '项目经理',
            status: 'ENABLE',
            dataScope: 'SELF',
            userCount: 2,
            menuIds: [],
          },
        ]
      }
      if (path.startsWith('/system/users?')) {
        const query = new URLSearchParams(path.split('?')[1])
        const records = query.get('username')
          ? []
          : [
              {
                id: 1,
                username: 'slow.user',
                realName: '慢响应用户',
                status: 'ENABLE',
                roleIds: [1],
                roleNames: ['项目经理'],
              },
              {
                id: 2,
                username: 'fast.user',
                realName: '快响应用户',
                status: 'ENABLE',
                roleIds: [1],
                roleNames: ['项目经理'],
              },
            ]
        return { pageNo: 1, pageSize: 10, total: records.length, records }
      }
      if (path === '/system/users/1') return firstDetail
      if (path === '/system/users/2') {
        return {
          id: 2,
          username: 'fast.user',
          realName: '快响应用户',
          status: 'ENABLE',
          roleIds: [1],
          roleNames: ['项目经理'],
          email: 'fast@example.com',
        }
      }
      throw new Error(`unexpected request: ${path}`)
    })
    useSessionStore().replaceUserInfo({
      tenantId: '1001',
      userId: '9',
      username: 'viewer',
      roles: ['USER'],
      permissions: ['system:user:query'],
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/system/users', component: UserManagementPage }],
    })
    await router.push('/system/users')
    await router.isReady()
    const wrapper = mount(UserManagementPage, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('正在读取用户详情')
    await wrapper.findAll('[role="option"]')[1]!.trigger('click')
    await flushPromises()
    expect(wrapper.get('.user-workspace__details').text()).toContain('fast@example.com')
    expect(wrapper.findAll('[role="option"]')[1]!.attributes('aria-selected')).toBe('true')

    resolveFirstDetail({
      id: 1,
      username: 'slow.user',
      realName: '慢响应用户',
      status: 'ENABLE',
      roleIds: [1],
      roleNames: ['项目经理'],
      email: 'stale@example.com',
    })
    await flushPromises()
    expect(wrapper.get('.user-workspace__details').text()).toContain('fast@example.com')
    expect(wrapper.text()).not.toContain('stale@example.com')

    await wrapper.get('input[placeholder="用户名"]').setValue('nobody')
    await wrapper.get('form.v2-page-heading__filters').trigger('submit')
    await flushPromises()
    expect(wrapper.find('.user-workspace__list').exists()).toBe(false)
    expect(wrapper.text()).toContain('当前角色暂无用户')
    expect(wrapper.text()).toContain('暂无用户详情')
  })

  it('shows only fixed business roles and exposes no role mutation controls', async () => {
    vi.mocked(apiRequest).mockResolvedValue([
      {
        id: 9,
        roleCode: 'EMPLOYEE',
        roleName: '员工',
        roleType: 'SYSTEM',
        status: 'ENABLE',
        dataScope: 'PROJECT_MEMBER',
        menuIds: [],
      },
      {
        id: 99,
        roleCode: 'SUPER_ADMIN',
        roleName: '超级管理员',
        status: 'ENABLE',
        dataScope: 'ALL',
        menuIds: [],
      },
      {
        id: 2,
        roleCode: 'PROJECT_MANAGER',
        roleName: '项目经理',
        roleType: 'SYSTEM',
        status: 'ENABLE',
        dataScope: 'PROJECT_MEMBER',
        menuIds: [],
      },
      {
        id: 10,
        roleCode: 'CUSTOM_ROLE',
        roleName: '自定义角色',
        status: 'ENABLE',
        dataScope: 'SELF',
        menuIds: [],
      },
      {
        id: 1,
        roleCode: 'COMPANY_OWNER',
        roleName: '公司老板',
        roleType: 'SYSTEM',
        status: 'ENABLE',
        dataScope: 'ALL',
        menuIds: [],
      },
    ])

    const roles = await loadRoles()
    expect(roles.map((role) => role.roleCode)).toEqual([
      'COMPANY_OWNER',
      'PROJECT_MANAGER',
      'EMPLOYEE',
    ])

    useSessionStore().replaceUserInfo({
      tenantId: '1001',
      userId: '1',
      username: 'admin',
      roles: ['SUPER_ADMIN'],
      permissions: [],
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/system/roles', component: RoleManagementPage }],
    })
    await router.push('/system/roles')
    await router.isReady()
    const wrapper = mount(RoleManagementPage, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('固定角色清单')
    expect(wrapper.text()).toContain('公司老板')
    expect(wrapper.text()).toContain('项目经理')
    expect(wrapper.text()).toContain('员工')
    expect(wrapper.text()).not.toContain('超级管理员')
    expect(wrapper.text()).not.toContain('自定义角色')
    expect(wrapper.find('button[role="switch"]').exists()).toBe(false)
    expect(wrapper.findAll('button').map((button) => button.text())).not.toEqual(
      expect.arrayContaining(['新增角色', '编辑', '删除']),
    )
  })

  it('warns that company-finance menu changes do not remove the admin bypass', async () => {
    vi.mocked(apiRequest).mockImplementation(async (path) => {
      if (path === '/system/roles') {
        return [
          {
            id: 1,
            roleCode: 'COMPANY_FINANCE',
            roleName: '公司财务',
            roleType: 'SYSTEM',
            status: 'ENABLE',
            dataScope: 'ALL',
            menuIds: ['10'],
          },
        ]
      }
      if (path === '/system/roles/1') {
        return {
          id: 1,
          roleCode: 'COMPANY_FINANCE',
          roleName: '公司财务',
          roleType: 'SYSTEM',
          status: 'ENABLE',
          dataScope: 'ALL',
          menuIds: ['10'],
        }
      }
      if (path === '/system/menus')
        return [
          {
            id: 10,
            parentId: 0,
            menuName: '报表查询',
            menuType: 'BUTTON',
            perms: 'report:catalog:query',
            status: 'ENABLE',
            visible: 1,
            orderNum: 1,
          },
        ]
      throw new Error(`unexpected request: ${path}`)
    })
    useSessionStore().replaceUserInfo({
      tenantId: '1001',
      userId: '1',
      username: 'admin',
      roles: ['SUPER_ADMIN'],
      permissions: [],
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/system/permissions', component: PermissionListPage }],
    })
    await router.push('/system/permissions')
    await router.isReady()
    const wrapper = mount(PermissionListPage, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('财务权限提示')
    expect(wrapper.text()).toContain('移除菜单权限不会撤销超级管理员旁路能力。')
  })
})
