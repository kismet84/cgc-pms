import { request } from '@/api/request'
import type { PageParams, PageResult } from '@/types/api'
import type {
  CreateMenuPayload,
  CreateRolePayload,
  MenuTreeVO,
  SysMenuVO,
  SysRoleVO,
  UpdateMenuPayload,
} from '@/types/system'

/** 获取菜单树 */
export function getMenuTree() {
  return request<MenuTreeVO[]>({
    url: '/system/menus/tree',
    method: 'get',
  })
}

/** 获取平铺菜单列表 */
export function getMenuList() {
  return request<SysMenuVO[]>({
    url: '/system/menus',
    method: 'get',
  })
}

/** 获取菜单详情 */
export function getMenuDetail(menuId: number | string) {
  return request<SysMenuVO>({
    url: `/system/menus/${menuId}`,
    method: 'get',
  })
}

/** 新建菜单 */
export function createMenu(data: CreateMenuPayload) {
  return request<number | string>({
    url: '/system/menus',
    method: 'post',
    data,
  })
}

/** 修改菜单 */
export function updateMenu(menuId: number | string, data: UpdateMenuPayload) {
  return request<void>({
    url: `/system/menus/${menuId}`,
    method: 'put',
    data,
  })
}

/** 删除菜单 */
export function deleteMenu(menuId: number | string) {
  return request<void>({
    url: `/system/menus/${menuId}`,
    method: 'delete',
  })
}

/** 获取角色列表 */
export function getRoles() {
  return request<SysRoleVO[]>({
    url: '/system/roles',
    method: 'get',
  })
}

/** 获取角色详情 */
export function getRoleDetail(roleId: number | string) {
  return request<SysRoleVO>({
    url: `/system/roles/${roleId}`,
    method: 'get',
  })
}

/** 新建普通自定义角色 */
export function createRole(data: CreateRolePayload) {
  return request<number | string>({
    url: '/system/roles',
    method: 'post',
    data,
  })
}

/** 删除未绑定用户的普通自定义角色 */
export function deleteRole(roleId: number | string) {
  return request<void>({
    url: `/system/roles/${roleId}`,
    method: 'delete',
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
