import type { CloseoutOverview, DictionaryItem, ProjectRecord } from '@cgc-pms/frontend-contracts'

export function dictionaryOptions(items: DictionaryItem[], currentValue = '') {
  const options = items
    .filter((item) => ['ACTIVE', 'ENABLE'].includes(item.status))
    .map((item) => ({ value: item.dictValue, label: item.dictLabel }))
  if (!currentValue || options.some((item) => item.value === currentValue)) return options
  const historical = items.find((item) => item.dictValue === currentValue)
  return [
    ...options,
    {
      value: currentValue,
      label: `${historical?.dictLabel ?? currentValue}（历史值，只读）`,
      disabled: true,
    },
  ]
}

export function dictionaryLabel(items: DictionaryItem[], value: string): string {
  return items.find((item) => item.dictValue === value)?.dictLabel ?? value
}

const APPROVAL_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
}

export function approvalStatus(value?: string | null): string {
  return value || 'DRAFT'
}

export function approvalStatusLabel(value?: string | null): string {
  return APPROVAL_STATUS_LABELS[approvalStatus(value)] ?? approvalStatus(value)
}

export function approvalStatusTone(value?: string | null) {
  const status = approvalStatus(value)
  return status === 'APPROVED'
    ? 'success'
    : status === 'REJECTED'
      ? 'danger'
      : status === 'APPROVING'
        ? 'info'
        : 'neutral'
}

const READINESS_LABELS: Record<string, string> = {
  PROJECT_STATE_NOT_READY: '项目状态不允许开工',
  PROJECT_INITIATION_BASIS_INVALID: '项目立项依据未确认',
  PROJECT_OWNER_CONTRACT_REQUIRED: '缺少已批准业主主合同',
  PROJECT_OWNER_CONTRACT_MISMATCH: '业主主合同与项目绑定不一致',
  COST_TARGET_ACTIVE_UNIQUE_REQUIRED: '缺少唯一生效目标成本',
  COST_TARGET_SOURCE_CONTRACT_MISMATCH: '目标成本与业主主合同不同源',
  COST_TARGET_CONTRACT_AMOUNT_MISMATCH: '目标成本合同金额快照不一致',
  PROJECT_BUDGET_ACTIVE_UNIQUE_REQUIRED: '缺少唯一生效项目预算',
  PROJECT_BUDGET_SOURCE_MISMATCH: '项目预算与目标成本不同源',
  PROJECT_WBS_ACTIVE_UNIQUE_REQUIRED: '缺少唯一生效WBS计划',
  PROJECT_COMMENCEMENT_REQUIRED: '缺少开工准入单',
  PROJECT_COMMENCEMENT_BASIS_FILE_REQUIRED: '缺少已通过扫描的开工依据附件',
  PROJECT_COMMENCEMENT_NOT_APPROVED: '开工准入尚未审批通过',
}

export function readinessLabel(code: string): string {
  return READINESS_LABELS[code] ?? code
}

export function projectStageGate(project: ProjectRecord, overview: CloseoutOverview) {
  const gates = overview.stageGates
  if (project.status === 'ACTIVE')
    return { label: '施工完成门', blockers: gates.constructionCompletion }
  if (project.status === 'COMPLETION') return { label: '进入质保门', blockers: gates.warrantyEntry }
  if (project.status === 'WARRANTY') return { label: '最终关闭门', blockers: gates.finalClose }
  return null
}

export function memberStatusLabel(status: string): string {
  return { ACTIVE: '在岗', INACTIVE: '离岗', ENABLE: '启用', DISABLE: '停用' }[status] ?? '状态缺失'
}
