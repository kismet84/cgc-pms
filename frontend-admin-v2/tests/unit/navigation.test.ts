import { describe, expect, it } from 'vitest'
import {
  findWorkspace,
  firstAccessiblePath,
  navigationDomains,
  permissionForPath,
  visibleNavigation,
} from '@/navigation/catalog'

describe('V2 eight-domain navigation contract', () => {
  it('defines exactly eight domains and unique tab paths', () => {
    expect(navigationDomains.map((domain) => domain.label)).toEqual([
      '工作台',
      '项目履约',
      '商务合约',
      '供应链与物资',
      '分包与结算',
      '资金财务',
      '基础资料',
      '系统管理',
    ])
    const paths = navigationDomains.flatMap((domain) =>
      domain.workspaces.flatMap((workspace) => workspace.tabs.map((tab) => tab.path)),
    )
    expect(new Set(paths).size).toBe(paths.length)
  })

  it('shows all domains to wildcard permission and only matching domains to ordinary users', () => {
    expect(visibleNavigation(['ADMIN'], ['*'])).toHaveLength(8)
    expect(
      visibleNavigation(['USER'], ['dashboard:view', 'project:query']).map(
        (domain) => domain.label,
      ),
    ).toEqual(['工作台', '项目履约'])
    expect(visibleNavigation(['USER'], []).map((domain) => domain.label)).toEqual(['工作台'])
    expect(
      visibleNavigation(['USER'], [])[0]?.workspaces.map((workspace) => workspace.label),
    ).toEqual(['我的工作', '报表中心'])
  })

  it('uses exact permission codes for routes and keeps object paths in their workspace', () => {
    expect(permissionForPath('/system/users')).toBe('system:user:query')
    expect(firstAccessiblePath(['USER'], ['audit:query'])).toBe('/approval/todo')
    expect(
      visibleNavigation(['ADMIN'], ['*'])[0]?.workspaces.find(
        (workspace) => workspace.id === 'cockpit',
      )?.tabs,
    ).toMatchObject([{ path: '/dashboard', label: '驾驶舱' }])
    expect(findWorkspace('/project/42/overview')?.workspace.label).toBe('项目管理')
    expect(findWorkspace('/project-schedule/11')?.workspace.label).toBe('计划与现场')
    expect(
      ['/contract/ledger', '/variation/order', '/bid-cost'].map((path) => ({
        path,
        label: findWorkspace(path)?.workspace.tabs.find((tab) => tab.path === path)?.label,
      })),
    ).toEqual([
      { path: '/contract/ledger', label: '合同台账' },
      { path: '/variation/order', label: '签证变更' },
      { path: '/bid-cost', label: '投标成本' },
    ])
    expect(findWorkspace('/contract/C-100/edit')?.workspace.label).toBe('合同与变更')
    expect(findWorkspace('/cost-target/81/edit')?.workspace.label).toBe('投标与成本目标')
    expect(findWorkspace('/partner/101')?.workspace.label).toBe('合作方管理')
    expect(permissionForPath('/supplier-sourcing')).toBe('supplier:sourcing:query')
    expect(findWorkspace('/supplier-sourcing')?.workspace.label).toBe('供应商管理')
    expect(permissionForPath('/inventory/purchase-request')).toBe('purchase:request:list')
    expect(permissionForPath('/purchase/order')).toBe('purchase:order:query')
    expect(permissionForPath('/purchase/receipt')).toBe('receipt:query')
    expect(findWorkspace('/purchase/order')?.workspace.label).toBe('采购执行')
  })

  it('uses the API-aligned admin gate for workflow configuration navigation', () => {
    const workflowVisible = (roles: string[], permissions: string[]) =>
      visibleNavigation(roles, permissions)
        .flatMap((domain) => domain.workspaces)
        .some((workspace) => workspace.id === 'workflow')

    expect(workflowVisible(['USER'], ['workflow:process:query'])).toBe(false)
    expect(workflowVisible(['ADMIN'], [])).toBe(true)
    expect(workflowVisible(['ADMIN'], ['workflow:process:query'])).toBe(true)
    expect(workflowVisible(['SUPER_ADMIN'], [])).toBe(true)
  })

  it('freezes system route and API permission layers', () => {
    expect(permissionForPath('/system/dict')).toBe('system:dict:list')
    expect(permissionForPath('/system/permissions')).toBe('system:menu:query')
    expect(permissionForPath('/system/audit')).toBe('audit:query')

    const workspaces = (roles: string[], permissions: string[]) =>
      visibleNavigation(roles, permissions)
        .flatMap((domain) => domain.workspaces)
        .map((workspace) => workspace.id)

    expect(workspaces(['USER'], ['system:user:query'])).not.toContain('access-control')
    expect(workspaces(['ADMIN'], ['system:user:query'])).toContain('access-control')
    expect(workspaces(['USER'], ['audit:query'])).toContain('audit')
    expect(workspaces(['ADMIN'], ['*'])).not.toContain('data')
    expect(workspaces(['SUPER_ADMIN'], [])).toContain('data')
  })
})
