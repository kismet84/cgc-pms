import { expect, test, type Page, type Route } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'

const file = {
  id: 'catalog-1',
  projectId: 'project-1',
  projectName: '江南项目',
  fileCode: 'FILE-P001-20260805-001',
  displayName: '施工组织设计',
  categoryCode: 'TECHNICAL',
  categoryName: '技术资料',
  sourceKind: 'MANAGED',
  maintainMode: 'MANAGED',
  versions: [
    {
      id: 'version-1',
      versionNo: 1,
      sysFileId: 'file-1',
      submitterName: '张三',
      createdAt: '2026-08-05T08:00:00Z',
      virusScanStatus: 'CLEAN',
      previewStatus: 'READY',
    },
  ],
}

async function json(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify({
      code: status === 200 ? '0' : 'UNEXPECTED_API',
      message: 'result',
      data,
    }),
  })
}

async function installApi(
  page: Page,
  permissions: string[],
  unexpected: string[],
  previewed: () => void,
) {
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const { pathname } = new URL(request.url())
    if (pathname === '/api/auth/userinfo') {
      return json(route, {
        tenantId: '1001',
        userId: '1',
        username: 'tester',
        realName: '测试用户',
        roles: ['USER'],
        permissions,
      })
    }
    if (pathname === '/api/auth/refresh') return json(route, null, 401)
    if (pathname === '/api/profile/preferences') {
      return json(route, {
        sidebarCollapsed: false,
        notificationEnabled: true,
        theme: 'light',
        tableDensity: 'middle',
      })
    }
    if (pathname === '/api/communications/unread-count') return json(route, { count: 0 })
    if (pathname === '/api/communications/stream' || pathname === '/api/notifications/stream') {
      return route.fulfill({ status: 204 })
    }
    if (pathname === '/api/project-context/options') {
      return json(route, [
        { id: 'project-1', projectCode: 'P001', projectName: '江南项目', status: 'ACTIVE' },
      ])
    }
    if (pathname === '/api/system/dict/data/by-code/file_category') {
      return json(route, [
        {
          id: '1',
          dictTypeId: '1',
          dictLabel: '技术资料',
          dictValue: 'TECHNICAL',
          orderNum: 1,
          status: 'ENABLE',
        },
      ])
    }
    if (pathname === '/api/project-files' && request.method() === 'GET') {
      return json(route, { records: [file], total: 1, pageNo: 1, pageSize: 10 })
    }
    if (pathname === '/api/project-files/versions/version-1/preview') {
      previewed()
      return json(route, { status: 'UNSUPPORTED', message: '该格式不支持在线预览' })
    }
    unexpected.push(`${request.method()} ${pathname}`)
    return json(route, null, 500)
  })
}

test('keeps project file access and management permissions explicit', async ({ page, browser }) => {
  const unexpected: string[] = []
  let previewRequests = 0
  const runtimeErrors = captureRuntimeErrors(page)
  await installApi(page, ['project:file:query'], unexpected, () => previewRequests++)
  await page.goto('/project/files')

  await expect(page.getByRole('heading', { name: '文件中心' })).toBeVisible()
  await expect(page.getByRole('region', { name: '项目文件列表' })).toContainText(
    'FILE-P001-20260805-001',
  )
  await expect(page.getByRole('button', { name: '新建文件' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '上传新版本' })).toHaveCount(0)
  await page.getByRole('button', { name: 'FILE-P001-20260805-001' }).click()
  await expect.poll(() => previewRequests).toBe(1)
  await expect(page.getByRole('alert')).toContainText('该格式不支持在线预览')
  expect(unexpected).toEqual([])
  expect(runtimeErrors).toEqual([])

  const manager = await browser.newPage()
  const managerUnexpected: string[] = []
  await installApi(
    manager,
    ['project:file:query', 'project:file:manage'],
    managerUnexpected,
    () => undefined,
  )
  await manager.goto('/project/files')
  await expect(manager.getByRole('button', { name: '新建文件' })).toBeVisible()
  await expect(manager.getByRole('button', { name: '上传新版本' })).toBeVisible()
  expect(managerUnexpected).toEqual([])
  await manager.close()
})
