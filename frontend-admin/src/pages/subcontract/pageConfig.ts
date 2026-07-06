export const SUBCONTRACT_TASK_STATUS_LABEL: Record<string, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  SUSPENDED: '已暂停',
}

export const SUBCONTRACT_TASK_STATUS_COLOR: Record<string, string> = {
  NOT_STARTED: 'default',
  IN_PROGRESS: 'processing',
  COMPLETED: 'success',
  SUSPENDED: 'warning',
}

export const SUBCONTRACT_TASK_GRID_COLUMNS = [
  { field: 'taskCode', title: '任务编号', minWidth: 150, slots: { default: 'taskCode' } },
  {
    field: 'taskName',
    title: '任务名称',
    minWidth: 140,
    slots: { default: 'taskName' },
    ellipsis: true,
  },
  { field: 'projectName', title: '项目名称', minWidth: 150, ellipsis: true },
  { field: 'contractName', title: '合同名称', minWidth: 150, ellipsis: true },
  { field: 'partnerName', title: '分包商', minWidth: 140, ellipsis: true },
  { field: 'workArea', title: '施工区域', minWidth: 120, ellipsis: true },
  { field: 'progressPercent', title: '进度', width: 90, slots: { default: 'progressPercent' } },
  { field: 'status', title: '状态', width: 88, slots: { default: 'status' } },
  { field: 'plannedStartDate', title: '计划开始', width: 112 },
  { field: 'plannedEndDate', title: '计划结束', width: 112 },
  { title: '操作', width: 76, slots: { default: 'action' } },
] as const
