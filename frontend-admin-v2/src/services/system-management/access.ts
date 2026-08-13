import { apiRequest } from '@/services/request'
import { normalizePage, params, requiredId, type PageResult } from './support'

export interface UserRecord {
  id: string
  username: string
  realName?: string
  phone?: string
  email?: string
  orgId?: string
  status: string
  roleNames: string[]
  roleIds: string[]
  createdAt?: string
}

export interface UserCommand {
  username?: string
  password?: string
  realName?: string
  phone?: string
  email?: string
  orgId?: string | null
  roleIds?: string[]
}

export interface RoleRecord {
  id: string
  roleCode: string
  roleName: string
  roleType?: string
  status: string
  dataScope: string
  roleLevel?: number
  userCount?: number
  menuIds: string[]
}

export const VISIBLE_ROLE_CODES = [
  'COMPANY_OWNER',
  'COMPANY_FINANCE',
  'PROJECT_MANAGER',
  'PROJECT_ACCOUNTANT',
  'TECHNICAL_LEAD',
  'SAFETY_LEAD',
  'CONSTRUCTION_LEAD',
  'PROCUREMENT_LEAD',
  'EMPLOYEE',
] as const

export async function loadUsers(
  query: {
    pageNo: number
    pageSize: number
    username?: string
    realName?: string
    status?: string
    roleId?: string
  },
  signal?: AbortSignal,
): Promise<PageResult<UserRecord>> {
  const page = await apiRequest<PageResult<UserRecord>>(`/system/users?${params(query)}`, {
    signal,
  })
  return normalizePage(page, normalizeUser)
}

export function loadUser(id: string): Promise<UserRecord> {
  return apiRequest<UserRecord>(`/system/users/${requiredId(id)}`).then(normalizeUser)
}

export function createUser(command: UserCommand): Promise<string> {
  return apiRequest<string, UserCommand>('/system/users', { method: 'POST', body: command }).then(
    String,
  )
}

export function updateUser(id: string, command: UserCommand): Promise<void> {
  return apiRequest<void, UserCommand>(`/system/users/${requiredId(id)}`, {
    method: 'PUT',
    body: command,
  })
}

export function updateUserStatus(id: string, status: string): Promise<void> {
  return apiRequest<void, { status: string }>(`/system/users/${requiredId(id)}/status`, {
    method: 'PATCH',
    body: { status },
  })
}

export function deleteUser(id: string): Promise<void> {
  return apiRequest<void>(`/system/users/${requiredId(id)}`, { method: 'DELETE' })
}

export function assignUserRoles(id: string, roleIds: string[]): Promise<void> {
  return apiRequest<void, { roleIds: string[] }>(`/system/users/${requiredId(id)}/roles`, {
    method: 'PUT',
    body: { roleIds },
  })
}

export function loadRoles(): Promise<RoleRecord[]> {
  return apiRequest<RoleRecord[]>('/system/roles').then((rows) => {
    const order = new Map<string, number>(VISIBLE_ROLE_CODES.map((code, index) => [code, index]))
    return rows
      .map(normalizeRole)
      .filter((role) => order.has(role.roleCode))
      .sort((left, right) => order.get(left.roleCode)! - order.get(right.roleCode)!)
  })
}

export function loadRole(id: string): Promise<RoleRecord> {
  return apiRequest<RoleRecord>(`/system/roles/${requiredId(id)}`).then(normalizeRole)
}

export function assignRoleMenus(id: string, menuIds: string[]): Promise<void> {
  return apiRequest<void, { menuIds: string[] }>(`/system/roles/${requiredId(id)}/menus`, {
    method: 'PUT',
    body: { menuIds },
  })
}

function normalizeUser(row: UserRecord): UserRecord {
  return {
    ...row,
    id: String(row.id),
    orgId: row.orgId == null ? undefined : String(row.orgId),
    roleNames: row.roleNames ?? [],
    roleIds: (row.roleIds ?? []).map(String),
  }
}

function normalizeRole(row: RoleRecord): RoleRecord {
  return {
    ...row,
    id: String(row.id),
    userCount: Number(row.userCount ?? 0),
    menuIds: (row.menuIds ?? []).map(String),
  }
}
