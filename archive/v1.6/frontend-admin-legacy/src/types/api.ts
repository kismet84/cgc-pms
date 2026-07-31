/** 后端统一响应结构 */
export interface ApiResponse<T = unknown> {
  code: string
  message: string
  traceId: string
  data: T
}

export type PageParamValue = string | number | boolean | null | undefined

/** 分页请求参数 — 优先使用 pageNo 与后端 PageResult 对齐；pageNum 为仅限部分老接口的别名 */
export interface PageParams extends Record<string, PageParamValue> {
  pageNo?: number
  pageNum?: number
  pageSize: number
}

/** 分页响应数据（后端统一分页结构） */
export interface PageResult<T = unknown> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}
