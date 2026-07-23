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
    expect(
      visibleNavigation(['*'])[0]?.workspaces.find((workspace) => workspace.id === 'cockpit')?.tabs,
    ).toMatchObject([{ path: '/dashboard', label: '驾驶舱' }])
    expect(findWorkspace('/project/42/overview')?.workspace.label).toBe('项目管理')
    expect(findWorkspace('/project-schedule/11')?.workspace.label).toBe('计划与现场')
    for (const path of ['/quality-safety', '/technical-management', '/project-closeout']) {
      expect(
        findWorkspace(path)?.workspace.tabs.find((tab) => tab.path === path)?.workspaceContext,
      ).toEqual({ project: true, period: false })
    }
    expect(
      findWorkspace('/project-closeout')?.workspace.tabs.find(
        (tab) => tab.path === '/project-closeout',
      )?.workspaceContext,
    ).toEqual({ project: true, period: false })
    expect(
      findWorkspace('/variation/order')?.workspace.tabs.find(
        (tab) => tab.path === '/variation/order',
      )?.workspaceContext,
    ).toEqual({ project: true, period: true })
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
    expect(
      findWorkspace('/contract/ledger')?.workspace.tabs.find(
        (tab) => tab.path === '/contract/ledger',
      )?.workspaceContext,
    ).toEqual({ project: true, period: true })
    expect(
      findWorkspace('/bid-cost')?.workspace.tabs.find((tab) => tab.path === '/bid-cost')
        ?.workspaceContext,
    ).toEqual({ project: true, period: true })
    expect(findWorkspace('/cost-target/81/edit')?.workspace.label).toBe('投标与成本目标')
    expect(
      findWorkspace('/cost-target/index')?.workspace.tabs.find(
        (tab) => tab.path === '/cost-target/index',
      )?.workspaceContext,
    ).toEqual({ project: true, period: false })
    for (const path of ['/cost/ledger', '/cost/summary', '/cost/control']) {
      expect(
        findWorkspace(path)?.workspace.tabs.find((tab) => tab.path === path)?.workspaceContext,
      ).toEqual({ project: true, period: true })
    }
    for (const path of ['/budget', '/production-measurement']) {
      expect(
        findWorkspace(path)?.workspace.tabs.find((tab) => tab.path === path)?.workspaceContext,
      ).toEqual({ project: true, period: true })
    }
  })
})
