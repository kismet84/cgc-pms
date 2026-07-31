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

export interface CreateRolePayload {
  roleCode: string
  roleName: string
  status?: 'ENABLE' | 'DISABLE'
  dataScope?: 'ALL' | 'DEPT' | 'DEPT_AND_CHILD' | 'SELF' | 'CUSTOM'
}

export interface UpdateRolePayload {
  /** Existing immutable code required by the backend entity validation contract. */
  roleCode: string
  roleName: string
  status: 'ENABLE' | 'DISABLE'
  dataScope: 'ALL' | 'DEPT' | 'DEPT_AND_CHILD' | 'SELF' | 'CUSTOM'
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

export interface UpdateMenuPayload {
  parentId: number | string
  menuName: string
  menuType: MenuType
  path: string
  component: string
  perms: string
  icon: string
  orderNum: number
  status: string
  visible: number
}
