import {
  filterVisibleNavigation,
  findWorkspaceIn,
  firstAccessiblePathIn,
  hasAccess,
  permissionForPathIn,
} from './access'
import { masterDataDomain, systemManagementDomain } from './domains/administration'
import { commercialDomain } from './domains/commercial'
import { constructionDomain, deliveryDomain } from './domains/delivery'
import { financeDomain } from './domains/finance'
import { subcontractSettlementDomain, supplyDomain } from './domains/supply'
import { workbenchDomain } from './domains/workbench'
import type { NavigationDomain, VisibleDomain, WorkspaceMatch } from './types'

export { hasAccess }
export type {
  NavigationAccess,
  NavigationDomain,
  NavigationWorkspace,
  VisibleDomain,
  VisibleWorkspace,
  WorkspaceMatch,
  WorkspaceTab,
} from './types'

export const navigationDomains: NavigationDomain[] = [
  workbenchDomain,
  deliveryDomain,
  constructionDomain,
  commercialDomain,
  supplyDomain,
  subcontractSettlementDomain,
  financeDomain,
  masterDataDomain,
  systemManagementDomain,
]

export function visibleNavigation(
  roles: readonly string[],
  permissions: readonly string[],
): VisibleDomain[] {
  return filterVisibleNavigation(navigationDomains, roles, permissions)
}

export function findWorkspace(path: string): WorkspaceMatch | undefined {
  return findWorkspaceIn(navigationDomains, path)
}

export function firstAccessiblePath(
  roles: readonly string[],
  permissions: readonly string[],
): string | undefined {
  return firstAccessiblePathIn(navigationDomains, roles, permissions)
}

export function permissionForPath(path: string): string | undefined {
  return permissionForPathIn(navigationDomains, path)
}
