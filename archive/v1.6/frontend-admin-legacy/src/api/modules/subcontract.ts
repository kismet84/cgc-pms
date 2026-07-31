import { request } from '@/api/request'
import type { PageParams, PageResult } from '@/types/api'
import type { SubTaskVO } from '@/types/subcontract'

/** 分包任务列表分页查询 */
export function getSubTaskList(params: PageParams) {
  return request<PageResult<SubTaskVO>>({
    url: '/sub-tasks',
    method: 'get',
    params,
  })
}

/** 分包任务详情 */
export function getSubTaskDetail(id: string) {
  return request<SubTaskVO>({
    url: `/sub-tasks/${id}`,
    method: 'get',
  })
}

/** 新建分包任务 */
export function createSubTask(data: Partial<SubTaskVO>) {
  return request<string>({
    url: '/sub-tasks',
    method: 'post',
    data,
  })
}

/** 更新分包任务 */
export function updateSubTask(id: string, data: Partial<SubTaskVO>) {
  return request<void>({
    url: `/sub-tasks/${id}`,
    method: 'put',
    data,
  })
}

/** 删除分包任务 */
export function deleteSubTask(id: string) {
  return request<void>({
    url: `/sub-tasks/${id}`,
    method: 'delete',
  })
}

// --- 分包计量 ---
import type { SubMeasureVO, SubMeasureItemVO } from '@/types/subcontract'

/** 分包计量列表分页查询 */
export function getMeasureList(params: Record<string, unknown>) {
  return request<PageResult<SubMeasureVO>>({
    url: '/sub-measures',
    method: 'get',
    params,
  })
}

/** 分包计量详情 */
export function getMeasureDetail(id: string) {
  return request<SubMeasureVO>({
    url: `/sub-measures/${id}`,
    method: 'get',
  })
}

/** 新建分包计量 */
export function createMeasure(data: Partial<SubMeasureVO>) {
  return request<string>({
    url: '/sub-measures',
    method: 'post',
    data,
  })
}

/** 更新分包计量 */
export function updateMeasure(id: string, data: Partial<SubMeasureVO>) {
  return request<void>({
    url: `/sub-measures/${id}`,
    method: 'put',
    data,
  })
}

/** 删除分包计量 */
export function deleteMeasure(id: string) {
  return request<void>({
    url: `/sub-measures/${id}`,
    method: 'delete',
  })
}

/** 分包计量明细列表 */
export function getMeasureItems(id: string) {
  return request<SubMeasureItemVO[]>({
    url: `/sub-measures/${id}/items`,
    method: 'get',
  })
}

/** 批量保存分包计量明细 */
export function saveMeasureItems(id: string, items: SubMeasureItemVO[]) {
  return request<void>({
    url: `/sub-measures/${id}/items/batch`,
    method: 'post',
    data: items,
  })
}

/** 提交审批 */
export function submitMeasureForApproval(id: string) {
  return request<void>({
    url: `/sub-measures/${id}/submit`,
    method: 'post',
  })
}
