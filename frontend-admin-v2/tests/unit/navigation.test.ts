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
    expect(visibleNavigation(['*'])).toHaveLength(8)
    expect(
      visibleNavigation(['dashboard:view', 'project:query']).map((domain) => domain.label),
    ).toEqual(['工作台', '项目履约'])
    expect(visibleNavigation([]).map((domain) => domain.label)).toEqual(['工作台'])
    expect(visibleNavigation([])[0]?.workspaces.map((workspace) => workspace.label)).toEqual([
      '我的工作',
      '报表中心',
    ])
  })

  it('uses exact permission codes for routes and keeps object paths in their workspace', () => {
    expect(permissionForPath('/system/users')).toBe('system:user:query')
    expect(firstAccessiblePath(['audit:query'])).toBe('/approval/todo')
    expect(findWorkspace('/project/42/overview')?.workspace.label).toBe('项目管理')
    expect(findWorkspace('/contract/C-100/edit')?.workspace.label).toBe('合同与变更')
  })
})
