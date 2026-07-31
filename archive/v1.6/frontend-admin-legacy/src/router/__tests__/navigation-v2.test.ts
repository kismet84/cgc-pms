import { describe, expect, it } from 'vitest'
import router from '@/router'
import { findWorkspaceByPath, navigationItems } from '@/router/navigation'

describe('CGC-PMS V2 navigation architecture', () => {
  it('defines exactly the approved eight business domains', () => {
    expect(navigationItems.map((item) => item.label)).toEqual([
      '工作台',
      '项目履约',
      '商务合约',
      '供应链与物资',
      '分包与结算',
      '资金财务',
      '基础资料',
      '系统管理',
    ])
  })

  it('keeps every workspace within the five-tab limit', () => {
    const workspaces = navigationItems.flatMap((item) => item.children)

    expect(workspaces).toHaveLength(30)
    expect(workspaces.flatMap((workspace) => workspace.tabs)).toHaveLength(57)
    for (const workspace of workspaces) {
      expect(workspace.tabs.length, workspace.label).toBeGreaterThan(0)
      expect(workspace.tabs.length, workspace.label).toBeLessThanOrEqual(5)
      expect(workspace.tabs.some((tab) => tab.key === workspace.defaultPath)).toBe(true)
    }
  })

  it('keeps tab permissions aligned with route permissions', () => {
    for (const tab of navigationItems.flatMap((item) =>
      item.children.flatMap((child) => child.tabs),
    )) {
      expect(router.resolve(tab.key).matched.length, tab.key).toBeGreaterThan(0)
      expect(router.resolve(tab.key).meta.permission, tab.key).toBe(tab.permission)
    }
  })

  it('maps representative list, process and object routes to their V2 workspaces', () => {
    expect(findWorkspaceByPath('/inventory/purchase-request')?.workspace.label).toBe('采购执行')
    expect(findWorkspaceByPath('/purchase/receipt')?.workspace.label).toBe('采购执行')
    expect(findWorkspaceByPath('/cost/control')?.workspace.label).toBe('成本核算与控制')
    expect(findWorkspaceByPath('/cost/subject/rules')?.workspace.label).toBe('成本科目中心')
    expect(findWorkspaceByPath('/approval/12345')?.workspace.label).toBe('我的工作')
    expect(findWorkspaceByPath('/approval/process')?.workspace.label).toBe('流程配置')
    expect(findWorkspaceByPath('/project/42/members')?.workspace.label).toBe('项目管理')
    expect(findWorkspaceByPath('/contract/42/edit')?.workspace.label).toBe('合同与变更')
    expect(findWorkspaceByPath('/settlement/42')?.workspace.label).toBe('结算管理')
    expect(findWorkspaceByPath('/system/document-templates')?.workspace.label).toBe('系统配置')
  })

  it('assigns every routed page to a V2 workspace or an approved global route', () => {
    const globalPaths = new Set([
      '/login',
      '/403',
      '/profile',
      '/settings',
      '/help',
      '/:pathMatch(.*)*',
    ])
    const pageRoutes = router
      .getRoutes()
      .filter((route) => route.path !== '/' && Boolean(route.components?.default))

    expect(pageRoutes).toHaveLength(73)
    for (const route of pageRoutes) {
      expect(
        globalPaths.has(route.path) || Boolean(findWorkspaceByPath(route.path)),
        `${route.path} must have a V2 assignment`,
      ).toBe(true)
    }
  })
})
