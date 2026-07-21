import type {
  ProjectMemberCommand,
  ProjectRecord,
  ProjectUpsertCommand,
} from '@cgc-pms/frontend-contracts'

export const PROJECT_ROLE_OPTIONS = [
  ['PM', '项目经理'],
  ['CM', '商务经理'],
  ['CSTM', '成本经理'],
  ['MAT', '材料员'],
  ['SUBC', '分包经理'],
  ['FIN', '财务'],
  ['OTH', '其他'],
].map(([value, label]) => ({ value: value!, label: label! }))

export function emptyProjectCommand(): ProjectUpsertCommand {
  return {
    projectName: '',
    projectType: '',
    projectAddress: '',
    ownerUnit: '',
    supervisorUnit: '',
    designUnit: '',
    contractAmount: '',
    targetCost: '',
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
    'contractAmount',
    'targetCost',
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
