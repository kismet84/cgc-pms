import type { OrgDepartmentTreeNodeVO } from '@/types/org'

/** 展平后的部门节点（供 select 使用） */
export interface FlatDeptItem {
  id: string
  name: string
  companyId: string
}

/** 将部门树展平为 select 可用的列表（含层级前缀 + companyId） */
export function flattenDeptTree(nodes: unknown, prefix = ''): FlatDeptItem[] {
  const result: FlatDeptItem[] = []
  for (const node of Array.isArray(nodes) ? (nodes as OrgDepartmentTreeNodeVO[]) : []) {
    const label = prefix ? `${prefix} / ${node.deptName}` : node.deptName
    result.push({ id: node.id, name: label, companyId: node.companyId })
    if (node.children?.length) {
      result.push(...flattenDeptTree(node.children, label))
    }
  }
  return result
}

/** 递归统计部门树节点总数 */
export function countDeptNodes(nodes: unknown): number {
  return (Array.isArray(nodes) ? (nodes as OrgDepartmentTreeNodeVO[]) : []).reduce(
    (sum, node) => sum + 1 + countDeptNodes(node.children ?? []),
    0,
  )
}

/** 按公司 + 关键词过滤部门树（保持树结构） */
export function filterDeptNodes(
  nodes: unknown,
  companyId?: string | null,
  keyword = '',
): OrgDepartmentTreeNodeVO[] {
  return (Array.isArray(nodes) ? (nodes as OrgDepartmentTreeNodeVO[]) : [])
    .map((node) => {
      const children = filterDeptNodes(node.children ?? [], companyId, keyword)
      const matchesCompany = !companyId || node.companyId === companyId || children.length > 0
      const matchesKeyword =
        !keyword ||
        node.deptName.includes(keyword) ||
        node.deptCode.includes(keyword) ||
        children.length > 0

      if (!matchesCompany || !matchesKeyword) return null
      return { ...node, children }
    })
    .filter((node): node is OrgDepartmentTreeNodeVO => node !== null)
}

/** 在部门树中按 ID 查找节点 */
export function findDeptNode(
  nodes: unknown,
  id: string,
): OrgDepartmentTreeNodeVO | null {
  for (const node of Array.isArray(nodes) ? (nodes as OrgDepartmentTreeNodeVO[]) : []) {
    if (node.id === id) return node
    if (node.children && node.children.length > 0) {
      const found = findDeptNode(node.children, id)
      if (found) return found
    }
  }
  return null
}
