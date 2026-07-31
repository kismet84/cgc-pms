export const DICT_STATUS_LABEL: Record<string, string> = {
  ENABLE: '启用',
  ENABLED: '启用',
  DISABLE: '禁用',
  DISABLED: '禁用',
}

export const DICT_DATA_GRID_COLUMNS = [
  { field: 'dictLabel', title: '字典标签', minWidth: 140, slots: { default: 'dictLabel' } },
  { field: 'dictValue', title: '字典键值', width: 140 },
  { field: 'orderNum', title: '排序', width: 80, align: 'right' as const },
  { field: 'listClass', title: '标签颜色', width: 100 },
  { field: 'status', title: '状态', width: 88, slots: { default: 'status' } },
  { field: 'createdAt', title: '创建时间', width: 170 },
  { title: '操作', width: 76, slots: { default: 'ops' } },
] as const

export const DICT_TAG_STYLE_OPTIONS = [
  { value: 'default', label: '默认（灰）' },
  { value: 'primary', label: '主要（蓝）' },
  { value: 'success', label: '成功（绿）' },
  { value: 'warning', label: '警告（橙）' },
  { value: 'danger', label: '危险（红）' },
  { value: 'purple', label: '紫色' },
  { value: 'cyan', label: '青色' },
  { value: 'geekblue', label: '极客蓝' },
  { value: 'magenta', label: '品红' },
] as const
