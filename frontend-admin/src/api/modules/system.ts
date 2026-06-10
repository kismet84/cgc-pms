import { request } from '@/api/request'
import type { MenuTreeVO } from '@/types/system'

/** 获取菜单树 */
export function getMenuTree() {
  return request<MenuTreeVO[]>({
    url: '/system/menus/tree',
    method: 'get',
  })
}
