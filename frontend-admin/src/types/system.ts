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
  id: number | string
  parentId: number | string
  menuName: string
  menuType: MenuType
  path: string
  component: string
  perms?: string
  icon: string
  orderNum?: number
  status: string
  visible?: number
}

export interface MenuTreeVO extends SysMenuVO {
  children?: MenuTreeVO[]
}

export type MenuType = 'DIR' | 'MENU' | 'BUTTON'

export interface CreateMenuPayload {
  parentId: number | string
  menuName: string
  menuType: MenuType
  path?: string
  component?: string
  perms?: string
  icon?: string
  orderNum?: number
}
