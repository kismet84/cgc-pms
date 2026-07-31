export interface ProjectVO {
  id: string
  projectCode: string
  projectName: string
  projectType: string
  projectAddress: string
  ownerUnit: string
  supervisorUnit: string
  designUnit: string
  contractAmount: string
  targetCost: string
  plannedStartDate: string
  plannedEndDate: string
  actualStartDate?: string
  actualEndDate?: string
  projectManagerId: string
  status: string
  approvalStatus: string
  createdBy: string
  createdAt: string
  updatedAt: string
  remark?: string
}

/** 项目成员 */
export interface MemberVO {
  id: string
  tenantId?: string
  projectId: string
  userId: string
  userName?: string
  roleCode: string
  positionName?: string
  startDate?: string
  endDate?: string
  status?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
  remark?: string
}

/** 添加/更新成员参数 */
export interface MemberFormParams {
  userId: string
  roleCode: string
  positionName?: string
  startDate?: string
  endDate?: string
  status?: string
}

/** Project overview — aggregation VO from GET /projects/{projectId}/overview */
export interface ProjectOverviewVO {
  projectId: string
  contractCount: string
  totalContractAmount: string
  dynamicCost: string
  paidAmount: string
  warningCount: string
  memberCount: string
  members: MemberBriefVO[]
}

export interface MemberBriefVO {
  userId: string
  userName: string
  roleCode: string
}
