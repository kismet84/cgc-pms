/** 后端统一响应结构 */
export interface ApiResponse<T = unknown> {
  code: string
  message: string
  traceId: string
  data: T
}

/** 分页请求参数 */
export interface PageParams {
  pageNum: number
  pageSize: number
  [key: string]: unknown
}

/** 分页响应数据（后端统一分页结构） */
export interface PageResult<T = unknown> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}
