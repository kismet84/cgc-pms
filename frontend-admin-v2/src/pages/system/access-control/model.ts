import type { MenuRecord, RoleRecord } from '@/services/system-management'

export const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'ENABLE', label: '启用' },
  { value: 'DISABLE', label: '停用' },
]

export function filterRoles(roles: RoleRecord[], query: string): RoleRecord[] {
  const normalized = query.trim().toLocaleLowerCase()
  if (!normalized) return roles
  return roles.filter(
    (role) =>
      role.roleName.toLocaleLowerCase().includes(normalized) ||
      role.roleCode.toLocaleLowerCase().includes(normalized),
  )
}

export function roleTypeLabel(value?: string): string {
  return value === 'SYSTEM' ? '系统角色' : '自定义角色'
}

export function dataScopeLabel(value: string): string {
  return (
    {
      ALL: '全部数据',
      PROJECT_MEMBER: '项目成员范围',
      COMPANY: '本公司',
      DEPT: '本部门',
      DEPT_AND_CHILD: '本部门及下级',
      SELF: '本人数据',
      CUSTOM: '自定义范围',
    }[value] ?? '未配置'
  )
}

export function menuTypeLabel(value: MenuRecord['menuType']): string {
  return { DIR: '目录', MENU: '菜单', BUTTON: '按钮' }[value]
}

export function setsEqual(left: Set<string>, right: Set<string>): boolean {
  return left.size === right.size && [...left].every((value) => right.has(value))
}
