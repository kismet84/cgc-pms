import { hasAdminRole } from '@/stores/session'

import type { NavigationAccess, NavigationDomain, VisibleDomain, WorkspaceMatch } from './types'

export function hasAccess(
  roles: readonly string[],
  permissions: readonly string[],
  access: NavigationAccess,
): boolean {
  if (access.superAdminOnly && !roles.includes('SUPER_ADMIN')) return false
  if (access.adminOnly && !hasAdminRole(roles)) return false
  if (access.adminBypassesPermission && hasAdminRole(roles)) return true
  if (access.permissions?.length) {
    return (
      permissions.includes('*') || access.permissions.some((item) => permissions.includes(item))
    )
  }
  return !access.permission || permissions.includes('*') || permissions.includes(access.permission)
}

export function filterVisibleNavigation(
  domains: readonly NavigationDomain[],
  roles: readonly string[],
  permissions: readonly string[],
): VisibleDomain[] {
  return domains.flatMap((domain) => {
    const workspaces = domain.workspaces.flatMap((workspace) => {
      if (!hasAccess(roles, permissions, workspace)) return []
      const tabs = workspace.tabs.filter((tab) => hasAccess(roles, permissions, tab))
      return tabs.length ? [{ ...workspace, tabs }] : []
    })
    return workspaces.length ? [{ ...domain, workspaces }] : []
  })
}

function prefixMatches(path: string, prefix: string): boolean {
  return path === prefix || path.startsWith(`${prefix}/`)
}

export function findWorkspaceIn(
  domains: readonly NavigationDomain[],
  path: string,
): WorkspaceMatch | undefined {
  for (const domain of domains) {
    for (const workspace of domain.workspaces) {
      if (workspace.tabs.some((tab) => tab.path === path)) return { domain, workspace }
    }
  }
  for (const domain of domains) {
    for (const workspace of domain.workspaces) {
      if (workspace.matchPrefixes?.some((prefix) => prefixMatches(path, prefix))) {
        return { domain, workspace }
      }
    }
  }
  return undefined
}

export function firstAccessiblePathIn(
  domains: readonly NavigationDomain[],
  roles: readonly string[],
  permissions: readonly string[],
): string | undefined {
  return filterVisibleNavigation(domains, roles, permissions)[0]?.workspaces[0]?.tabs[0]?.path
}

export function permissionForPathIn(
  domains: readonly NavigationDomain[],
  path: string,
): string | undefined {
  for (const domain of domains) {
    for (const workspace of domain.workspaces) {
      const tab = workspace.tabs.find((candidate) => candidate.path === path)
      if (tab) return tab.permission
    }
  }
  return undefined
}
