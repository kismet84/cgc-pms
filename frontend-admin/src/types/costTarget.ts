/** 目标成本版本视图对象 */
export interface CostTargetVO {
  id: string
  projectId: string
  projectName?: string
  versionNo: string
  versionName: string
  totalTargetAmount: string
  /** 0=否, 1=是 — 同一项目仅允许一个生效版本 */
  isActive: number
  /** 审批状态：DRAFT / APPROVING / APPROVED / REJECTED */
  approvalStatus: string
  /** 业务状态：DRAFT / ACTIVE / CANCELLED */
  status: string
  effectiveDate?: string
  remark?: string
  createdTime?: string
  updatedTime?: string
  createdBy?: string
}

/** 目标成本明细项 */
export interface CostTargetItemVO {
  id?: string
  targetId?: string
  projectId?: string
  costSubjectId: string
  costSubjectName?: string
  costSubjectCode?: string
  targetAmount: string
  sortOrder?: number
  remark?: string
}

/** 目标成本查询参数 */
export interface CostTargetQueryParams {
  pageNo: number
  pageSize: number
  projectId?: string
  versionNo?: string
  approvalStatus?: string
  isActive?: number
}

/** 审批状态标签映射 */
export const APPROVAL_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}

export const APPROVAL_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  APPROVING: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
}

/** 业务状态标签映射 */
export const TARGET_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '已生效',
  CANCELLED: '已作废',
}

export const TARGET_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  ACTIVE: 'green',
  CANCELLED: 'red',
}
