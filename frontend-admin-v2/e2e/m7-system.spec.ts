import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type Route } from '@playwright/test'

type Identity = {
  tenantId: string
  userId: string
  username: string
  roles: string[]
  permissions: string[]
}

const superAdmin: Identity = {
  tenantId: '0',
  userId: '1',
  username: 'super.admin',
  roles: ['SUPER_ADMIN'],
  permissions: ['*'],
}

function success(route: Route, data: unknown) {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: '0', message: 'success', traceId: 'm7-system', data }),
  })
}

async function installMocks(page: Page, identity: Identity) {
  const traffic = { system: 0, audit: 0, preview: 0, clear: 0 }
  await page.route('**/api/**', (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path === '/api/auth/userinfo') return success(route, identity)
    if (path === '/api/auth/refresh') {
      return route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'AUTH_TOKEN_INVALID', message: 'unauthorized', data: null }),
      })
    }
    if (path === '/api/project-context/options') return success(route, [])
    if (path === '/api/system/clear-database') {
      traffic.clear += 1
      return route.fulfill({ status: 500, body: 'destructive endpoint must stay unused' })
    }
    if (path === '/api/system/data-maintenance/preview') {
      traffic.preview += 1
      return success(route, {
        database: 'cgc_pms_e2e',
        policyFingerprint: 'sha256:e2e-policy',
        eligible: true,
        blockers: [],
        retainedGroups: [{ code: 'IDENTITY_ACCESS', tableCount: 4, rowCount: 28 }],
        clearTableCount: 12,
        clearRowCount: 345,
        sysFileCount: 6,
        ignoredViews: ['v_project_summary'],
      })
    }
    if (path.startsWith('/api/system/')) traffic.system += 1
    if (path === '/api/audit-logs') traffic.audit += 1
    if (path === '/api/system/users') {
      return success(route, {
        pageNo: 1,
        pageSize: 10,
        total: 1,
        records: [
          {
            id: '7',
            username: 'server.user',
            realName: '服务端用户',
            status: 'ENABLE',
            roleNames: ['项目成员'],
            roleIds: ['3'],
          },
        ],
      })
    }
    if (path === '/api/system/roles') {
      return success(route, [
        {
          id: '3',
          roleCode: 'PROJECT_MEMBER',
          roleName: '服务端角色',
          roleType: 'CUSTOM',
          status: 'ENABLE',
          dataScope: 'SELF',
          menuIds: ['9'],
        },
      ])
    }
    if (path === '/api/system/roles/3') {
      return success(route, {
        id: '3',
        roleCode: 'PROJECT_MEMBER',
        roleName: '服务端角色',
        roleType: 'CUSTOM',
        status: 'ENABLE',
        dataScope: 'SELF',
        menuIds: ['9'],
      })
    }
    if (path === '/api/system/menus') {
      return success(route, [
        {
          id: '9',
          parentId: '0',
          menuName: '服务端权限项',
          menuType: 'MENU',
          path: '/system/permissions',
          perms: 'system:menu:query',
          orderNum: 1,
          status: 'ENABLE',
          visible: 1,
        },
      ])
    }
    if (path === '/api/system/dict/tree') {
      return success(route, [
        {
          id: '20',
          groupCode: 'SYSTEM_GOVERNANCE',
          groupName: '系统治理',
          orderNum: 1,
          status: 'ENABLE',
          types: [
            {
              id: '21',
              groupId: '20',
              dictCode: 'server_dict',
              dictName: '服务端字典',
              dictClass: 'SYSTEM',
              status: 'ENABLE',
              data: [
                {
                  id: '22',
                  dictTypeId: '21',
                  dictLabel: '服务端字典项',
                  dictValue: 'SERVER',
                  orderNum: 1,
                  status: 'ENABLE',
                },
              ],
            },
          ],
        },
      ])
    }
    if (path === '/api/audit-logs') {
      return success(route, {
        pageNo: 1,
        pageSize: 10,
        total: 1,
        records: [
          {
            id: '31',
            operationType: 'LOGIN',
            businessType: 'AUTH',
            requestPath: '/auth/login',
            successFlag: 1,
            durationMs: 12,
            createdAt: '2026-07-27 12:00:00',
          },
        ],
      })
    }
    if (path === '/api/document-templates/business-types') {
      return success(route, [
        {
          businessType: 'PAYMENT',
          displayName: '付款申请单',
          schemaVersion: 'payment.v2',
          providerReady: true,
          fieldCount: 2,
        },
      ])
    }
    if (path === '/api/document-templates/catalog') {
      return success(route, {
        businessType: 'PAYMENT',
        displayName: '付款申请单',
        schemaVersion: 'payment.v2',
        fields: [
          {
            path: 'payment.applyCode',
            label: '申请编号',
            valueType: 'TEXT',
            nullable: false,
            group: '基本信息',
            collectionPath: null,
            masked: false,
            sortOrder: 1,
          },
        ],
      })
    }
    if (path === '/api/document-templates/preview-html') {
      return success(route, { html: '<html><body>付款申请预览</body></html>' })
    }
    if (path === '/api/document-templates') {
      return success(route, [
        {
          id: '41',
          templateCode: 'PAYMENT_SERVER',
          templateName: '服务端付款模板',
          businessType: 'PAYMENT',
          enabled: 1,
          defaultVersionId: '42',
          defaultLockVersion: 2,
        },
      ])
    }
    if (path === '/api/document-templates/41') {
      return success(route, {
        template: {
          id: '41',
          templateCode: 'PAYMENT_SERVER',
          templateName: '服务端付款模板',
          businessType: 'PAYMENT',
          enabled: 1,
          defaultVersionId: '42',
          defaultLockVersion: 2,
        },
        versions: [
          {
            id: '42',
            templateId: '41',
            versionNo: 1,
            status: 'PUBLISHED',
            schemaVersion: 'payment.v1',
            templateContent: '<p>payment</p>',
            fieldManifest: '["payment.applyCode"]',
            contentHash: 'sha256:e2e',
          },
        ],
        defaultBinding: { templateId: '41', templateVersionId: '42', lockVersion: 2 },
      })
    }
    return route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'E2E_API_UNSTUBBED',
        message: `${request.method()} ${path}`,
        data: null,
      }),
    })
  })
  return traffic
}

test('system admin routes fail closed before business requests', async ({ page }) => {
  const ordinaryTraffic = await installMocks(page, {
    tenantId: '0',
    userId: '7',
    username: 'ordinary.user',
    roles: ['USER'],
    permissions: ['system:user:query'],
  })
  await page.goto('/system/users?source=e2e#list')
  await expect(page).toHaveURL(/\/forbidden\?from=/)
  expect(ordinaryTraffic.system).toBe(0)

  const adminPage = await page.context().newPage()
  const adminTraffic = await installMocks(adminPage, {
    tenantId: '0',
    userId: '8',
    username: 'admin.no.permission',
    roles: ['ADMIN'],
    permissions: [],
  })
  await adminPage.goto('/system/users')
  await expect(adminPage).toHaveURL(/\/forbidden\?from=/)
  expect(adminTraffic.system).toBe(0)
})

test('audit stays read-only and requires only its exact permission', async ({ page }) => {
  const traffic = await installMocks(page, {
    tenantId: '0',
    userId: '9',
    username: 'auditor',
    roles: ['USER'],
    permissions: ['audit:query'],
  })
  await page.goto('/system/audit?source=e2e#history')
  await expect(page).toHaveURL(/\/system\/audit\?source=e2e#history$/)
  await expect(page.getByRole('heading', { level: 1, name: '操作审计' })).toBeVisible()
  await expect(page.getByText('/auth/login')).toBeVisible()
  expect(traffic.audit).toBe(1)
  expect(traffic.clear).toBe(0)

  const denied = await page.context().newPage()
  const deniedTraffic = await installMocks(denied, {
    tenantId: '0',
    userId: '10',
    username: 'not.auditor',
    roles: ['USER'],
    permissions: [],
  })
  await denied.goto('/system/audit')
  await expect(denied).toHaveURL(/\/forbidden\?from=/)
  expect(deniedTraffic.audit).toBe(0)
})

test('super administrator reads all server facts while destructive traffic stays zero', async ({
  page,
}) => {
  const traffic = await installMocks(page, superAdmin)
  const routes = [
    ['/system/users', '用户管理', '服务端用户'],
    ['/system/roles', '角色管理', '服务端角色'],
    ['/system/permissions', '权限清单', 'system:menu:query'],
    ['/system/dict', '字典管理', '服务端字典项'],
    ['/system/audit', '操作审计', '/auth/login'],
    ['/system/document-templates', '业务单据模板', '服务端付款模板'],
    ['/system/data', '数据维护', 'cgc_pms_e2e'],
  ] as const

  for (const [path, heading, fact] of routes) {
    await page.goto(path)
    await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible()
    await expect(page.getByText(fact).first()).toBeVisible()
  }
  expect(traffic.clear).toBe(0)

  await page.goto('/system?source=e2e#root')
  await expect(page).toHaveURL(/\/system\/dict\?source=e2e#root$/)
})

test('data maintenance rejects ADMIN and remains accessible at three viewports for SUPER_ADMIN', async ({
  page,
}) => {
  const adminTraffic = await installMocks(page, {
    tenantId: '0',
    userId: '11',
    username: 'admin',
    roles: ['ADMIN'],
    permissions: ['*'],
  })
  await page.goto('/system/data')
  await expect(page).toHaveURL(/\/forbidden\?from=/)
  expect(adminTraffic.clear).toBe(0)

  const superPage = await page.context().newPage()
  const superTraffic = await installMocks(superPage, superAdmin)
  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 1024, height: 768 },
    { width: 390, height: 844 },
  ]) {
    await superPage.setViewportSize(viewport)
    await superPage.goto('/system/data')
    await expect(superPage.getByRole('heading', { level: 1, name: '数据维护' })).toBeVisible()
    await expect(superPage.getByText('12 张表 / 345 行 / 6 个文件')).toBeVisible()
    await expect(superPage.getByText('IDENTITY_ACCESS')).toBeVisible()
    await expect(superPage.getByText('v_project_summary')).toBeVisible()
    await expect(superPage.getByText('复制命令')).toBeVisible()
    await expect(superPage.getByText('清空非生产业务数据')).toHaveCount(0)
    expect(
      await superPage.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true)
    const axe = await new AxeBuilder({ page: superPage })
      .include('.data-maintenance-page')
      .analyze()
    expect(
      axe.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
    ).toEqual([])
  }
  expect(superTraffic.preview).toBe(3)
  expect(superTraffic.clear).toBe(0)
})
