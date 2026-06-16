export interface CostSubjectVO {
  id: string
  parentId: string | null
  subjectCode: string
  subjectName: string
  subjectType: string
  level: number
  sortOrder: number
  status: string
}

export interface CostSubjectTreeNode extends CostSubjectVO {
  children?: CostSubjectTreeNode[]
}
