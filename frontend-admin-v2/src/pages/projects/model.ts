import type {
  ProjectMemberCommand,
  ProjectRecord,
  ProjectUpsertCommand,
} from '@cgc-pms/frontend-contracts'

export const PROJECT_ROLE_OPTIONS = [
  { value: 'PROJECT_MANAGER', label: '项目经理' },
  { value: 'PROJECT_ACCOUNTANT', label: '项目会计' },
  { value: 'TECHNICAL_LEAD', label: '技术负责人' },
  { value: 'SAFETY_LEAD', label: '安全负责人' },
  { value: 'CONSTRUCTION_LEAD', label: '施工负责人' },
  { value: 'PROCUREMENT_LEAD', label: '采购负责人' },
  { value: 'EMPLOYEE', label: '员工' },
]

const HISTORICAL_PROJECT_ROLE_LABELS: Record<string, string> = {
  PM: '项目经理',
  CM: '商务经理',
  CSTM: '成本经理',
  MAT: '材料员',
  SUBC: '分包经理',
  FIN: '财务',
  OTH: '其他',
}

export function projectRoleLabel(value: string): string {
  return (
    PROJECT_ROLE_OPTIONS.find((item) => item.value === value)?.label ??
    HISTORICAL_PROJECT_ROLE_LABELS[value] ??
    value
  )
}

export function projectRoleOptions(currentValue = '') {
  if (!currentValue || PROJECT_ROLE_OPTIONS.some((item) => item.value === currentValue)) {
    return PROJECT_ROLE_OPTIONS
  }
  return [
    ...PROJECT_ROLE_OPTIONS,
    {
      value: currentValue,
      label: `${projectRoleLabel(currentValue)}（历史角色，只读）`,
      disabled: true,
    },
  ]
}

export function emptyProjectCommand(): ProjectUpsertCommand {
  return {
    projectName: '',
    projectType: '',
    projectAddress: '',
    ownerUnit: '',
    supervisorUnit: '',
    designUnit: '',
    plannedStartDate: '',
    plannedEndDate: '',
    remark: '',
  }
}

export function projectCommand(project: ProjectRecord): ProjectUpsertCommand {
  const result = emptyProjectCommand()
  for (const key of Object.keys(result) as Array<keyof ProjectUpsertCommand>) {
    const value = project[key]
    if (typeof value === 'string') result[key] = value
  }
  return result
}

export function cleanProjectCommand(value: ProjectUpsertCommand): ProjectUpsertCommand {
  const result: ProjectUpsertCommand = {
    projectName: value.projectName.trim(),
    projectType: value.projectType.trim(),
  }
  for (const key of [
    'projectAddress',
    'ownerUnit',
    'supervisorUnit',
    'designUnit',
    'plannedStartDate',
    'plannedEndDate',
    'remark',
  ] as const) {
    const normalized = value[key]?.trim()
    if (normalized) result[key] = normalized
  }
  return result
}

export function cleanMemberCommand(value: ProjectMemberCommand): ProjectMemberCommand {
  const result: ProjectMemberCommand = {
    userId: value.userId.trim(),
    roleCode: value.roleCode.trim(),
  }
  for (const key of ['positionName', 'startDate', 'endDate', 'status', 'remark'] as const) {
    const normalized = value[key]?.trim()
    if (normalized) result[key] = normalized
  }
  return result
}

export function isSuperAdmin(roles: readonly string[]): boolean {
  return roles.some((role) => role.toUpperCase() === 'SUPER_ADMIN')
}
