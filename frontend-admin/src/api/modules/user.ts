import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { SysUserVO } from '@/types/user'

/** 用户分页列表 */
export function getUserList(params: Record<string, unknown>) {
  return request<PageResult<SysUserVO>>({
    url: '/system/users',
    method: 'get',
    params,
  })
}

/** 创建用户 */
export function createUser(data: Record<string, unknown>) {
  return request<string>({
    url: '/system/users',
    method: 'post',
    data,
  })
}

/** 更新用户 */
export function updateUser(id: string, data: Record<string, unknown>) {
  return request<void>({
    url: `/system/users/${id}`,
    method: 'put',
    data,
  })
}

/** 启用/禁用用户 */
export function updateUserStatus(id: string, status: string) {
  return request<void>({
    url: `/system/users/${id}/status`,
    method: 'patch',
    data: { status },
  })
}

/** 删除用户 */
export function deleteUser(id: string) {
  return request<void>({
    url: `/system/users/${id}`,
    method: 'delete',
  })
}

/** 分配角色 */
export function assignUserRoles(id: string, roleIds: string[]) {
  return request<void>({
    url: `/system/users/${id}/roles`,
    method: 'put',
    data: { roleIds },
  })
}
