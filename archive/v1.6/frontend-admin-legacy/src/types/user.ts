/** 登录请求参数 */
export interface LoginParams {
  username: string
  password: string
  remember?: boolean
}

/** 登录响应 — 认证态由 HttpOnly cookie 承载，JSON body 只返回 userInfo */
export interface LoginResult {
  userInfo: UserInfo
}

/** 用户信息 */
export interface UserInfo {
  userId: string
  username: string
  realName?: string
  avatar?: string
  phone?: string
  email?: string
  roles: string[]
  permissions: string[]
  /** 部门/角色显示名，如"项目经理" */
  roleName?: string
}

/** 系统用户视图对象 */
export interface SysUserVO {
  id: string
  username: string
  realName: string
  phone?: string
  email?: string
  avatar?: string
  orgId?: string
  status: string
  isAdmin?: number
  roleNames?: string[]
  roleIds?: (number | string)[]
  createdAt?: string
  updatedAt?: string
}
