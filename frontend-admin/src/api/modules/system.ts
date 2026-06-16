import { request } from '@/api/request'
import type { PageParams, PageResult } from '@/types/api'
import type { MenuTreeVO, SysRoleVO } from '@/types/system'

/** 获取菜单树 */
export function getMenuTree() {
  return request<MenuTreeVO[]>({
    url: '/system/menus/tree',
    method: 'get',
  })
}

/** 获取角色列表 */
export function getRoles() {
  return request<SysRoleVO[]>({
    url: '/system/roles',
    method: 'get',
  })
}

/** 更新角色菜单权限 */
export function updateRoleMenus(roleId: number | string, menuIds: number[]) {
  return request<void>({
    url: `/system/roles/${roleId}/menus`,
    method: 'put',
    data: { menuIds },
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
