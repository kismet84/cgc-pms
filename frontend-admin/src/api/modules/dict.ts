import { request } from '@/api/request'
import type { PageParams, PageResult } from '@/types/api'
import type { DictTypeVO, DictDataVO } from '@/types/dict'

/* ========== 字典类型 ========== */

/** 字典类型分页查询 */
export function getDictTypeList(
  params: PageParams & { dictCode?: string; dictName?: string; status?: string },
) {
  return request<PageResult<DictTypeVO>>({
    url: '/system/dict/types',
    method: 'get',
    params,
  })
}

/** 字典类型详情 */
export function getDictTypeDetail(id: string) {
  return request<DictTypeVO>({
    url: `/system/dict/types/${id}`,
    method: 'get',
  })
}

/** 新增字典类型 */
export function createDictType(data: { dictCode: string; dictName: string; status: string }) {
  return request<number>({
    url: '/system/dict/types',
    method: 'post',
    data,
  })
}

/** 更新字典类型 */
export function updateDictType(
  id: string,
  data: { dictCode: string; dictName: string; status: string },
) {
  return request<void>({
    url: `/system/dict/types/${id}`,
    method: 'put',
    data,
  })
}

/** 删除字典类型 */
export function deleteDictType(id: string) {
  return request<void>({
    url: `/system/dict/types/${id}`,
    method: 'delete',
  })
}

/* ========== 字典数据 ========== */

/** 字典数据分页查询 */
export function getDictDataList(
  params: PageParams & { typeId?: string; dictLabel?: string; status?: string },
) {
  return request<PageResult<DictDataVO>>({
    url: '/system/dict/data',
    method: 'get',
    params,
  })
}

/** 字典数据详情 */
export function getDictDataDetail(id: string) {
  return request<DictDataVO>({
    url: `/system/dict/data/${id}`,
    method: 'get',
  })
}

/** 新增字典数据 */
export function createDictData(data: {
  dictTypeId: string
  dictLabel: string
  dictValue: string
  cssClass?: string
  listClass?: string
  orderNum?: number
  status: string
}) {
  return request<number>({
    url: '/system/dict/data',
    method: 'post',
    data,
  })
}

/** 更新字典数据 */
export function updateDictData(
  id: string,
  data: {
    dictTypeId: string
    dictLabel: string
    dictValue: string
    cssClass?: string
    listClass?: string
    orderNum?: number
    status: string
  },
) {
  return request<void>({
    url: `/system/dict/data/${id}`,
    method: 'put',
    data,
  })
}

/** 根据字典编码获取字典数据列表（用于业务页面动态下拉） */
export function getDictDataByCode(dictCode: string) {
  return request<DictDataVO[]>({
    url: `/system/dict/data/by-code/${dictCode}`,
    method: 'get',
  })
}

/** 删除字典数据 */
export function deleteDictData(id: string) {
  return request<void>({
    url: `/system/dict/data/${id}`,
    method: 'delete',
  })
}
