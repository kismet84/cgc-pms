export const DICT_STATUS_LABEL: Record<string, string> = {
  ENABLE: '启用',
  ENABLED: '启用',
  DISABLE: '禁用',
  DISABLED: '禁用',
}

export const DICT_DATA_GRID_COLUMNS = [
  { field: 'dictLabel', title: '字典标签', minWidth: 140 },
  { field: 'dictValue', title: '字典键值', width: 140 },
  { field: 'orderNum', title: '排序', width: 80, align: 'right' as const },
  { field: 'cssClass', title: '样式类名', width: 120 },
  { field: 'statusLabel', title: '状态', width: 88 },
  { field: 'createdAt', title: '创建时间', width: 170 },
  { title: '操作', width: 76, slots: { default: 'ops' } },
] as const
