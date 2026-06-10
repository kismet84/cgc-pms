import { request } from '@/api/request'
import type { PageParams, PageResult } from '@/types/api'
import type { ProjectVO } from '@/types/project'

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
