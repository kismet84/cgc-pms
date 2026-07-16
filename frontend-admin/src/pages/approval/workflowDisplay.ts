import { fetchDictData, getDictLabelSync, getDictTagColorSync } from '@/utils/dict'

// 字典常量 - 工作流实例状态
export const WF_INSTANCE_RUNNING = 'RUNNING'
export const WF_INSTANCE_APPROVED = 'APPROVED'
export const WF_INSTANCE_REJECTED = 'REJECTED'
export const WF_INSTANCE_WITHDRAWN = 'WITHDRAWN'
export const WF_INSTANCE_VOIDED = 'VOIDED'

export interface WorkflowBusinessEntryRegistryItem {
  businessType: string
  displayName: string
  permissionCode: string
  targetRoute: (businessId: string) => string
  openMode: 'route'
  forbiddenPolicy: 'disabled-with-tooltip'
}

export const workflowBusinessEntryRegistry: WorkflowBusinessEntryRegistryItem[] = [
  {
    businessType: 'CONTRACT',
    displayName: '合同审批',
    permissionCode: 'contract:query',
    targetRoute: (businessId: string) => `/contract/${businessId}`,
    openMode: 'route',
    forbiddenPolicy: 'disabled-with-tooltip',
  },
  {
    businessType: 'CONTRACT_APPROVAL',
    displayName: '合同审批',
    permissionCode: 'contract:query',
    targetRoute: (businessId: string) => `/contract/${businessId}`,
    openMode: 'route',
    forbiddenPolicy: 'disabled-with-tooltip',
  },
  {
    businessType: 'PURCHASE_REQUEST',
    displayName: '采购申请',
    permissionCode: 'purchase:request:list',
    targetRoute: (businessId: string) => `/inventory/purchase-request?businessId=${businessId}`,
    openMode: 'route',
    forbiddenPolicy: 'disabled-with-tooltip',
  },
  {
    businessType: 'SUB_MEASURE',
    displayName: '分包计量',
    permissionCode: 'subcontract:measure:query',
    targetRoute: (businessId: string) => `/subcontract/measure?businessId=${businessId}`,
    openMode: 'route',
    forbiddenPolicy: 'disabled-with-tooltip',
  },
  {
    businessType: 'PRODUCTION_MEASUREMENT',
    displayName: '产值计量',
    permissionCode: 'measurement:query',
    targetRoute: (businessId: string) => `/production-measurement?businessId=${businessId}`,
    openMode: 'route',
    forbiddenPolicy: 'disabled-with-tooltip',
  },
  {
    businessType: 'PROJECT_SCHEDULE',
    displayName: '项目基线/修订计划',
    permissionCode: 'schedule:query',
    targetRoute: (businessId: string) =>
      `/project-schedule?businessType=PROJECT_SCHEDULE&businessId=${businessId}`,
    openMode: 'route',
    forbiddenPolicy: 'disabled-with-tooltip',
  },
  {
    businessType: 'PROJECT_PERIOD_PLAN',
    displayName: '项目月周计划',
    permissionCode: 'schedule:query',
    targetRoute: (businessId: string) =>
      `/project-schedule?businessType=PROJECT_PERIOD_PLAN&businessId=${businessId}`,
    openMode: 'route',
    forbiddenPolicy: 'disabled-with-tooltip',
  },
  {
    businessType: 'PROJECT_CORRECTIVE_ACTION',
    displayName: '项目进度纠偏',
    permissionCode: 'schedule:query',
    targetRoute: (businessId: string) =>
      `/project-schedule?businessType=PROJECT_CORRECTIVE_ACTION&businessId=${businessId}`,
    openMode: 'route',
    forbiddenPolicy: 'disabled-with-tooltip',
  },
]

const workflowBusinessEntryMap = new Map(
  workflowBusinessEntryRegistry.map((entry) => [entry.businessType, entry]),
)

export const workflowBusinessTypeLabels: Record<string, string> = {
  ...Object.fromEntries(
    workflowBusinessEntryRegistry.map((entry) => [entry.businessType, entry.displayName]),
  ),
  PAY_APPLICATION: '付款申请',
  PAY_REQUEST: '付款申请',
  PURCHASE_ORDER: '采购订单',
  MATERIAL_RECEIPT: '材料验收',
  MAT_RECEIPT: '材料验收',
  MATERIAL_REQUISITION: '材料领用',
  VAR_ORDER: '签证变更',
  CT_CHANGE: '合同变更',
  SETTLEMENT: '结算审批',
  COST_TARGET: '成本目标',
  CONTRACT_REVENUE: '合同收入',
  OWNER_SETTLEMENT: '业主结算',
  TECH_ITEM: '技术事项',
}

export const coreBusinessTypeOptions = workflowBusinessEntryRegistry
  .filter((entry) => entry.businessType !== 'CONTRACT')
  .map((entry) => ({ label: entry.displayName, value: entry.businessType }))

export const instanceStatusOptions = [
  { label: '审批中', value: WF_INSTANCE_RUNNING },
  { label: '已通过', value: WF_INSTANCE_APPROVED },
  { label: '已驳回', value: WF_INSTANCE_REJECTED },
  { label: '已撤回', value: WF_INSTANCE_WITHDRAWN },
]

const instanceStatusMap: Record<string, { text: string; color: string }> = {
  [WF_INSTANCE_RUNNING]: { text: '审批中', color: 'processing' },
  [WF_INSTANCE_APPROVED]: { text: '已通过', color: 'success' },
  [WF_INSTANCE_REJECTED]: { text: '已驳回', color: 'error' },
  [WF_INSTANCE_WITHDRAWN]: { text: '已撤回', color: 'default' },
  [WF_INSTANCE_VOIDED]: { text: '已作废', color: 'default' },
}

export function preloadWorkflowDisplayDicts() {
  return Promise.all([
    fetchDictData('wf_instance_status'),
    fetchDictData('wf_task_status'),
    fetchDictData('wf_node_status'),
  ])
}

export function getWorkflowBusinessTypeLabel(value: unknown): string {
  const key = String(value ?? '').trim()
  if (!key) return '未知业务类型'
  return workflowBusinessTypeLabels[key] ?? '未知业务类型'
}

export function getWorkflowInstanceStatusMeta(status: unknown) {
  const key = String(status ?? '').trim()
  if (!key) return { text: '未知状态', color: 'default' }
  return {
    text: getDictLabelSync('wf_instance_status', key, mapStatusText(instanceStatusMap)),
    color: getDictTagColorSync('wf_instance_status', key, mapStatusColor(instanceStatusMap)),
  }
}

export function getWorkflowTaskStatusMeta(
  status: unknown,
  fallback: Record<string, { text: string; color: string }>,
) {
  const key = String(status ?? '').trim()
  if (!key) return { text: '未知任务状态', color: 'default' }
  return {
    text: getDictLabelSync('wf_task_status', key, mapStatusText(fallback)),
    color: getDictTagColorSync('wf_task_status', key, mapStatusColor(fallback)),
  }
}

export function getWorkflowNodeStatusMeta(
  status: unknown,
  fallback: Record<string, { text: string; color: string }>,
) {
  const key = String(status ?? '').trim()
  if (!key) return { text: '未知节点状态', color: 'default' }
  return {
    text: getDictLabelSync('wf_node_status', key, mapStatusText(fallback)),
    color: getDictTagColorSync('wf_node_status', key, mapStatusColor(fallback)),
  }
}

function mapStatusText(source: Record<string, { text: string; color: string }>) {
  return Object.fromEntries(Object.entries(source).map(([key, value]) => [key, value.text]))
}

function mapStatusColor(source: Record<string, { text: string; color: string }>) {
  return Object.fromEntries(Object.entries(source).map(([key, value]) => [key, value.color]))
}

export function getWorkflowBusinessEntryPath(
  record: {
    businessId?: unknown
    businessType?: unknown
  } | null,
) {
  const businessId = String(record?.businessId ?? '').trim()
  if (!businessId) return ''
  return getWorkflowBusinessEntry(record)?.targetRoute(businessId) ?? ''
}

export function getWorkflowBusinessEntry(
  record: {
    businessType?: unknown
  } | null,
) {
  const key = String(record?.businessType ?? '').trim()
  return workflowBusinessEntryMap.get(key) ?? null
}

export function getWorkflowBusinessEntryPermission(
  record: {
    businessType?: unknown
  } | null,
) {
  return getWorkflowBusinessEntry(record)?.permissionCode ?? ''
}

export function canAccessWorkflowBusinessEntry(
  record: { businessType?: unknown } | null,
  hasPermission: (code: string) => boolean,
  roles: string[] = [],
) {
  const permission = getWorkflowBusinessEntryPermission(record)
  if (!permission) return false
  return roles.includes('ADMIN') || roles.includes('SUPER_ADMIN') || hasPermission(permission)
}
