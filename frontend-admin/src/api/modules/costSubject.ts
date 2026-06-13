import { request } from '@/api/request'

/** 成本科目树节点 */
export interface CostSubjectTreeNode {
  id: string
  subjectCode: string
  subjectName: string
  parentId: string | null
  level: number
  children?: CostSubjectTreeNode[]
}

/** 获取成本科目树 */
export function getCostSubjectTree() {
  return request<CostSubjectTreeNode[]>({
    url: '/cost-subjects/tree',
    method: 'get',
  })
}
