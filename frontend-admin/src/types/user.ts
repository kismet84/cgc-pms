/** 登录请求参数 */
export interface LoginParams {
  username: string
  password: string
  remember?: boolean
}

/** 登录响应 */
export interface LoginResult {
  token: string
  refreshToken: string
  userInfo: UserInfo
}

/** 用户信息 */
export interface UserInfo {
  userId: string
  username: string
  realName: string
  avatar?: string
  roles: string[]
  permissions: string[]
  /** 部门/角色显示名，如“项目经理” */
  roleName?: string
}
