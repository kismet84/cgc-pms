import { request } from '@/api/request'
import type { PageParams, PageResult } from '@/types/api'
import type {
  OrgCompanyVO,
  OrgDepartmentForm,
  OrgDepartmentTreeNodeVO,
  OrgDepartmentVO,
  OrgPositionVO,
} from '@/types/org'

// ─── Companies ───────────────────────────────────────────

export function getCompanyList(
  params: PageParams & {
    companyCode?: string
    companyName?: string
    status?: string
  },
) {
  return request<PageResult<OrgCompanyVO>>({
    url: '/org/companies',
    method: 'get',
    params,
  })
}

export function getCompanyDetail(id: string) {
  return request<OrgCompanyVO>({
    url: `/org/companies/${id}`,
    method: 'get',
  })
}

export function createCompany(data: {
  companyCode: string
  companyName: string
  status: string
  remark?: string
}) {
  return request<number>({
    url: '/org/companies',
    method: 'post',
    data,
  })
}

export function updateCompany(
  id: string,
  data: {
    companyCode: string
    companyName: string
    status: string
    remark?: string
  },
) {
  return request<void>({
    url: `/org/companies/${id}`,
    method: 'put',
    data,
  })
}

export function deleteCompany(id: string) {
  return request<void>({
    url: `/org/companies/${id}`,
    method: 'delete',
  })
}

// ─── Departments ─────────────────────────────────────────

/** 获取部门树 */
export function getDepartmentTree() {
  return request<OrgDepartmentTreeNodeVO[]>({
    url: '/org/departments/tree',
    method: 'get',
  })
}

/** 部门分页列表 */
export function getDepartmentList(
  params: PageParams & {
    companyId?: string
    deptCode?: string
    deptName?: string
    status?: string
  },
) {
  return request<PageResult<OrgDepartmentVO>>({
    url: '/org/departments',
    method: 'get',
    params,
  })
}

export function getDepartmentDetail(id: string) {
  return request<OrgDepartmentVO>({
    url: `/org/departments/${id}`,
    method: 'get',
  })
}

export function createDepartment(data: OrgDepartmentForm) {
  return request<number>({
    url: '/org/departments',
    method: 'post',
    data,
  })
}

export function updateDepartment(id: string, data: OrgDepartmentForm) {
  return request<void>({
    url: `/org/departments/${id}`,
    method: 'put',
    data,
  })
}

export function deleteDepartment(id: string) {
  return request<void>({
    url: `/org/departments/${id}`,
    method: 'delete',
  })
}

// ─── Positions ───────────────────────────────────────────

export function getPositionList(
  params: PageParams & {
    companyId?: string
    departmentId?: string
    positionCode?: string
    positionName?: string
    status?: string
  },
) {
  return request<PageResult<OrgPositionVO>>({
    url: '/org/positions',
    method: 'get',
    params,
  })
}

export function getPositionDetail(id: string) {
  return request<OrgPositionVO>({
    url: `/org/positions/${id}`,
    method: 'get',
  })
}

export function createPosition(data: {
  companyId?: string
  departmentId?: string
  positionCode: string
  positionName: string
  status: string
  remark?: string
}) {
  return request<number>({
    url: '/org/positions',
    method: 'post',
    data,
  })
}

export function updatePosition(
  id: string,
  data: {
    companyId?: string
    departmentId?: string
    positionCode: string
    positionName: string
    status: string
    remark?: string
  },
) {
  return request<void>({
    url: `/org/positions/${id}`,
    method: 'put',
    data,
  })
}

export function deletePosition(id: string) {
  return request<void>({
    url: `/org/positions/${id}`,
    method: 'delete',
  })
}
