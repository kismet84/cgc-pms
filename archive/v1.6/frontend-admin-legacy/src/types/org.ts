/** 公司 VO */
export interface OrgCompanyVO {
  id: string
  companyCode: string
  companyName: string
  status: string
  createdBy: string
  createdAt: string
  updatedAt: string
  remark?: string
}

/** 部门 VO */
export interface OrgDepartmentVO {
  id: string
  companyId: string
  parentId?: string
  deptCode: string
  deptName: string
  orderNum: number
  status: string
  createdBy: string
  createdAt: string
  updatedAt: string
  remark?: string
}

/** 部门树节点 VO（含 children） */
export interface OrgDepartmentTreeNodeVO {
  id: string
  companyId: string
  parentId?: string
  deptCode: string
  deptName: string
  orderNum: number
  status: string
  children: OrgDepartmentTreeNodeVO[]
}

/** 岗位 VO */
export interface OrgPositionVO {
  id: string
  companyId?: string
  departmentId?: string
  positionCode: string
  positionName: string
  status: string
  createdBy: string
  createdAt: string
  updatedAt: string
  remark?: string
}

/** 公司新增/编辑表单 */
export interface OrgCompanyForm {
  companyCode: string
  companyName: string
  status: string
  remark?: string
}

/** 部门新增/编辑表单 */
export interface OrgDepartmentForm {
  companyId: string
  parentId?: string
  deptCode: string
  deptName: string
  orderNum: number
  status: string
  remark?: string
}

/** 岗位新增/编辑表单 */
export interface OrgPositionForm {
  companyId?: string
  departmentId?: string
  positionCode: string
  positionName: string
  status: string
  remark?: string
}
