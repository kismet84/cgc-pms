import { request } from '@/api/request'
import type { PageParams, PageResult } from '@/types/api'
import type { MenuTreeVO } from '@/types/system'

/** 获取菜单树 */
export function getMenuTree() {
  return request<MenuTreeVO[]>({
    url: '/system/menus/tree',
    method: 'get',
  })
}

/** 用户列表分页查询 */
export interface SysUserBrief {
  id: string
  username: string
  realName: string
  phone?: string
  status?: string
}

export function getUserList(params: PageParams) {
  return request<PageResult<SysUserBrief>>({
    url: '/system/users',
    method: 'get',
    params,
  })
}
