export interface NavigationAccess {
  permission?: string
  permissions?: string[]
  adminOnly?: boolean
  superAdminOnly?: boolean
  adminBypassesPermission?: boolean
}

export interface WorkspaceTab extends NavigationAccess {
  path: string
  label: string
  migration?: 'pending'
}

export interface NavigationWorkspace extends NavigationAccess {
  id: string
  label: string
  defaultPath: string
  matchPrefixes?: string[]
  tabs: WorkspaceTab[]
}

export interface NavigationDomain {
  id: string
  label: string
  badge: string
  workspaces: NavigationWorkspace[]
}

export interface VisibleWorkspace extends NavigationWorkspace {
  tabs: WorkspaceTab[]
}

export interface VisibleDomain extends Omit<NavigationDomain, 'workspaces'> {
  workspaces: VisibleWorkspace[]
}

export interface WorkspaceMatch {
  domain: NavigationDomain
  workspace: NavigationWorkspace
}
