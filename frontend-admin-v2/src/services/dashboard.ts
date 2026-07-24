import {
  DASHBOARD_COST_BREAKDOWN_CONTRACT,
  DASHBOARD_ROLE_CONTRACTS,
  normalizeDashboardMonth,
  resolveDashboardRoles,
  type CostBreakdownVO,
  type DashboardDataByRole,
  type DashboardRole,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

export interface DashboardAccess {
  roles: string[]
  permissions: string[]
}

export interface DashboardQuery {
  projectId?: string | null
  period?: string | null
}

export class DashboardRequestError extends Error {
  constructor(readonly code: 'DASHBOARD_ROLE_FORBIDDEN' | 'DASHBOARD_PROJECT_REQUIRED') {
    super(code)
    this.name = 'DashboardRequestError'
  }
}

export async function loadDashboard<R extends DashboardRole>(
  role: R,
  query: DashboardQuery,
  access: DashboardAccess,
  signal?: AbortSignal,
): Promise<DashboardDataByRole[R]> {
  if (!resolveDashboardRoles(access.roles, access.permissions).includes(role)) {
    throw new DashboardRequestError('DASHBOARD_ROLE_FORBIDDEN')
  }

  const contract = DASHBOARD_ROLE_CONTRACTS[role]
  const params = new URLSearchParams()
  const projectId = query.projectId?.trim()
  if (projectId) params.set('projectId', projectId)
  if (supportsMonth(role)) {
    const month = normalizeDashboardMonth(query.period)
    if (month) params.set('month', month)
  }
  const search = params.size ? `?${params}` : ''
  return apiRequest<DashboardDataByRole[R]>(`${contract.endpoint}${search}`, { signal })
}

export async function loadCostBreakdown(
  projectId: string,
  access: DashboardAccess,
  signal?: AbortSignal,
): Promise<CostBreakdownVO> {
  const allowed =
    access.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN') ||
    access.permissions.includes('*') ||
    access.permissions.includes(DASHBOARD_COST_BREAKDOWN_CONTRACT.permission)
  if (!allowed) throw new DashboardRequestError('DASHBOARD_ROLE_FORBIDDEN')
  if (!projectId.trim()) throw new DashboardRequestError('DASHBOARD_PROJECT_REQUIRED')
  return apiRequest<CostBreakdownVO>(DASHBOARD_COST_BREAKDOWN_CONTRACT.endpoint(projectId), {
    signal,
  })
}

function supportsMonth(role: DashboardRole): boolean {
  return ['pm', 'cost', 'purchase', 'production', 'chiefEngineer'].includes(role)
}
