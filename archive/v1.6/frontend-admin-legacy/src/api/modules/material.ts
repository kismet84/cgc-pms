import { request } from '@/api/request'
import type { PageParams, PageResult } from '@/types/api'
import type { MaterialVO } from '@/types/material'

/** 材料列表分页查询 */
export function getMaterialList(params: PageParams) {
  return request<PageResult<MaterialVO>>({
    url: '/materials',
    method: 'get',
    params,
  })
}

/** 材料详情 */
export function getMaterialDetail(id: string) {
  return request<MaterialVO>({
    url: `/materials/${id}`,
    method: 'get',
  })
}

/** 新建材料 */
export function createMaterial(data: Partial<MaterialVO>) {
  return request<string>({
    url: '/materials',
    method: 'post',
    data,
  })
}

/** 更新材料 */
export function updateMaterial(id: string, data: Partial<MaterialVO>) {
  return request<void>({
    url: `/materials/${id}`,
    method: 'put',
    data,
  })
}

/** 更新材料状态 */
export function updateMaterialStatus(id: string, status: string) {
  return request<void>({
    url: `/materials/${id}/status`,
    method: 'put',
    params: { status },
  })
}
