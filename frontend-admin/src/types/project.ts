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
