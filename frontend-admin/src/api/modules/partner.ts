import { request } from '@/api/request'
import type { PageParams, PageResult } from '@/types/api'
import type { PartnerVO } from '@/types/partner'

/** 合作方列表分页查询 */
export function getPartnerList(params: PageParams) {
  return request<PageResult<PartnerVO>>({
    url: '/partners',
    method: 'get',
    params,
  })
}

/** 合作方详情 */
export function getPartnerDetail(id: string) {
  return request<PartnerVO>({
    url: `/partners/${id}`,
    method: 'get',
  })
}

/** 新建合作方 */
export function createPartner(data: Partial<PartnerVO>) {
  return request<void>({
    url: '/partners',
    method: 'post',
    data,
  })
}

/** 更新合作方 */
export function updatePartner(id: string, data: Partial<PartnerVO>) {
  return request<void>({
    url: `/partners/${id}`,
    method: 'put',
    data,
  })
}
