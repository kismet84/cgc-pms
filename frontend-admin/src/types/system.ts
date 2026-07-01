export interface SysUserVO {
  id: string
  username: string
  realName: string
  email: string
  phone: string
  /** @see V34 — org_id on sys_user, nullable */
  orgId?: number | null
  status: string
  createdAt: string
}

export interface SysRoleVO {
  id: number | string
  roleCode: string
  roleName: string
  roleType?: string
  status: string
  dataScope?: string
  menuIds?: number[]
  createdAt: string
}

export interface SysMenuVO {
  id: string
  parentId: string
  menuName: string
  menuType: string
  path: string
  component: string
  perms?: string
  icon: string
  sort: number
  status: string
}

export interface MenuTreeVO extends SysMenuVO {
  children?: MenuTreeVO[]
}
