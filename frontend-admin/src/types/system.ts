export interface SysUserVO {
  id: string
  username: string
  realName: string
  email: string
  phone: string
  orgId: string
  status: string
  createdAt: string
}

export interface SysRoleVO {
  id: string
  roleCode: string
  roleName: string
  status: string
  createdAt: string
}

export interface SysMenuVO {
  id: string
  parentId: string
  menuName: string
  menuType: string
  path: string
  component: string
  icon: string
  sort: number
  status: string
}

export interface MenuTreeVO extends SysMenuVO {
  children?: MenuTreeVO[]
}
