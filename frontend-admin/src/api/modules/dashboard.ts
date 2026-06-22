import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type {
  ProjectManagerDashboardVO,
  BusinessManagerDashboardVO,
  CostManagerDashboardVO,
  FinanceDashboardVO,
  ManagementDashboardVO,
  CostBreakdownVO,
} from '@/types/dashboard'
import type { ContractVO } from '@/types/contract'

/** Project Manager dashboard view */
export function getProjectManagerView(projectId?: string) {
  return request<ProjectManagerDashboardVO>({
    url: '/dashboard/project-manager',
    method: 'get',
    params: projectId ? { projectId } : undefined,
  })
}

/** Business Manager dashboard view */
export function getBusinessManagerView(projectId?: string) {
  return request<BusinessManagerDashboardVO>({
    url: '/dashboard/business-manager',
    method: 'get',
    params: projectId ? { projectId } : undefined,
  })
}

/** Cost Manager dashboard view */
export function getCostManagerView(projectId?: string) {
  return request<CostManagerDashboardVO>({
    url: '/dashboard/cost-manager',
    method: 'get',
    params: projectId ? { projectId } : undefined,
  })
}

/** Finance dashboard view */
export function getFinanceView(projectId?: string) {
  return request<FinanceDashboardVO>({
    url: '/dashboard/finance',
    method: 'get',
    params: projectId ? { projectId } : undefined,
  })
}

/** Management dashboard view (tenant-wide) */
export function getManagementView() {
  return request<ManagementDashboardVO>({
    url: '/dashboard/management',
    method: 'get',
  })
}

/** Cost breakdown drill-down by project */
export function getCostBreakdown(projectId: string) {
  return request<CostBreakdownVO>({
    url: `/dashboard/project/${projectId}/cost-breakdown`,
    method: 'get',
  })
}

/** Fetch all contracts for a project (used for pie chart type distribution) */
export function getProjectContracts(projectId: string) {
  return request<PageResult<ContractVO>>({
    url: '/contracts',
    method: 'get',
    params: { projectId, pageNum: 1, pageSize: 1000 },
  })
}
