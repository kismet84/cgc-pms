import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type {
  ProjectManagerDashboardVO,
  BusinessManagerDashboardVO,
  CostManagerDashboardVO,
  FinanceDashboardVO,
  ManagementDashboardVO,
  CostBreakdownVO,
  PurchaseManagerDashboardVO,
  ProductionManagerDashboardVO,
  ChiefEngineerDashboardVO,
} from '@/types/dashboard'
import type { ContractVO } from '@/types/contract'

function dashboardParams(projectId?: string, month?: string) {
  const params: Record<string, string> = {}
  if (projectId) params.projectId = projectId
  if (month) params.month = month
  return Object.keys(params).length ? params : undefined
}

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
export function getCostManagerView(projectId?: string, month?: string) {
  return request<CostManagerDashboardVO>({
    url: '/dashboard/cost-manager',
    method: 'get',
    params: dashboardParams(projectId, month),
  })
}

/** Purchase Manager dashboard view */
export function getPurchaseManagerView(projectId?: string) {
  return request<PurchaseManagerDashboardVO>({
    url: '/dashboard/purchase-manager',
    method: 'get',
    params: projectId ? { projectId } : undefined,
  })
}

/** Production Manager dashboard view */
export function getProductionManagerView(projectId?: string) {
  return request<ProductionManagerDashboardVO>({
    url: '/dashboard/production-manager',
    method: 'get',
    params: projectId ? { projectId } : undefined,
  })
}

/** Chief Engineer dashboard view */
export function getChiefEngineerView(projectId?: string) {
  return request<ChiefEngineerDashboardVO>({
    url: '/dashboard/chief-engineer',
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
