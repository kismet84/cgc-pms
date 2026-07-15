import { request } from '@/api/request'
import type { PageParams, PageResult } from '@/types/api'
import type { ProjectOverviewVO, ProjectVO, MemberVO, MemberFormParams } from '@/types/project'

/** 项目列表分页查询 */
export function getProjectList(params: PageParams) {
  return request<PageResult<ProjectVO>>({
    url: '/projects',
    method: 'get',
    params,
  })
}

/** 项目详情 */
export function getProjectDetail(id: string) {
  return request<ProjectVO>({
    url: `/projects/${id}`,
    method: 'get',
  })
}

/** 新建项目 */
export function createProject(data: Partial<ProjectVO>) {
  return request<void>({
    url: '/projects',
    method: 'post',
    data,
  })
}

/** 更新项目 */
export function updateProject(id: string, data: Partial<ProjectVO>) {
  return request<void>({
    url: `/projects/${id}`,
    method: 'put',
    data,
  })
}

/** 归档项目 */
export function archiveProject(id: string) {
  return request<void>({
    url: `/projects/${id}/archive`,
    method: 'put',
  })
}

/** 项目总览数据 */
export function getProjectOverview(projectId: string) {
  return request<ProjectOverviewVO>({
    url: `/projects/${projectId}/overview`,
    method: 'get',
  })
}

// ── 项目成员 ──

/** 项目成员列表分页查询 */
export function getMemberList(projectId: string, params: PageParams) {
  return request<PageResult<MemberVO>>({
    url: `/projects/${projectId}/members`,
    method: 'get',
    params,
  })
}

/** 添加项目成员 */
export function addMember(projectId: string, data: MemberFormParams) {
  return request<number>({
    url: `/projects/${projectId}/members`,
    method: 'post',
    data,
  })
}

/** 更新项目成员 */
export function updateMember(projectId: string, memberId: string, data: MemberFormParams) {
  return request<void>({
    url: `/projects/${projectId}/members/${memberId}`,
    method: 'put',
    data,
  })
}

/** 删除项目成员 */
export function removeMember(projectId: string, memberId: string) {
  return request<void>({
    url: `/projects/${projectId}/members/${memberId}`,
    method: 'delete',
  })
}

/** 删除项目 */
export function deleteProject(id: string) {
  return request<void>({
    url: `/projects/${id}`,
    method: 'delete',
  })
}
