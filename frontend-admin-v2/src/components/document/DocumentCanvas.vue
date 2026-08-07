<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { V2ActionMenu, V2Button, V2Input } from '@/components'
import type {
  DocumentCanvasElement,
  DocumentCanvasTable,
  DocumentCatalogField,
  DocumentDesignSchema,
  DocumentPageOrientation,
} from '@/services/system-management'

const props = withDefaults(
  defineProps<{
    modelValue: DocumentDesignSchema
    fields: DocumentCatalogField[]
    disabled?: boolean
    previewHtml?: string
    previewLoading?: boolean
    previewError?: string
    previewBusinessId?: string
  }>(),
  {
    disabled: false,
    previewHtml: '',
    previewLoading: false,
    previewError: '',
    previewBusinessId: '',
  },
)
const emit = defineEmits<{
  'update:modelValue': [value: DocumentDesignSchema]
  'update:valid': [value: boolean]
  'update:previewBusinessId': [value: string]
}>()

const search = ref('')
const zoom = ref('75')
const selectedId = ref('')
const selectedIds = ref<string[]>([])
const viewMode = ref<'DESIGN' | 'PREVIEW'>('DESIGN')
const gridVisible = ref(true)
const snapToGrid = ref(false)
const smartGuides = ref(true)
const alignmentReference = ref<'SELECTION' | 'CANVAS' | 'KEY'>('SELECTION')
const spacingMm = ref('5')
const alignmentReferences = [
  ['SELECTION', '选区'],
  ['CANVAS', '画布'],
  ['KEY', '主组件'],
] as const
const componentPresets = [
  { key: 'TITLE', label: '标题', description: '居中大标题' },
  { key: 'TEXT', label: '文本', description: '普通说明文字' },
  { key: 'DIVIDER', label: '分割线', description: '横向分隔内容' },
  { key: 'TABLE', label: '表格', description: '业务明细表' },
  { key: 'HEADER', label: '页眉', description: '每页重复' },
  { key: 'FOOTER', label: '页脚', description: '每页重复' },
] as const
type ComponentPreset = (typeof componentPresets)[number]['key']
type CanvasItem = DocumentCanvasElement | DocumentCanvasTable
type ComponentAlignment = 'TOP' | 'MIDDLE' | 'BOTTOM' | 'LEFT' | 'CENTER' | 'RIGHT'
type LayoutAction =
  | ComponentAlignment
  | 'DISTRIBUTE_HORIZONTAL'
  | 'DISTRIBUTE_VERTICAL'
  | 'SPACE_HORIZONTAL'
  | 'SPACE_VERTICAL'
  | 'ATTACH_HORIZONTAL'
  | 'ATTACH_VERTICAL'
  | 'EQUAL_WIDTH'
  | 'EQUAL_HEIGHT'
  | 'EQUAL_SIZE'
  | 'ARRANGE_HORIZONTAL'
  | 'ARRANGE_VERTICAL'
  | 'ARRANGE_GRID'
  | 'ROUND_MM'
type ItemRect = { xMm: number; yMm: number; widthMm: number; heightMm: number }
const layoutGroups = [
  {
    label: '对齐',
    options: [
      ['TOP', '上'],
      ['MIDDLE', '垂直居中'],
      ['BOTTOM', '下'],
      ['LEFT', '左'],
      ['CENTER', '水平居中'],
      ['RIGHT', '右'],
    ],
  },
  {
    label: '分布与间距',
    options: [
      ['DISTRIBUTE_HORIZONTAL', '水平分布'],
      ['DISTRIBUTE_VERTICAL', '垂直分布'],
      ['SPACE_HORIZONTAL', '水平定距'],
      ['SPACE_VERTICAL', '垂直定距'],
      ['ATTACH_HORIZONTAL', '水平贴边'],
      ['ATTACH_VERTICAL', '垂直贴边'],
    ],
  },
  {
    label: '尺寸',
    options: [
      ['EQUAL_WIDTH', '等宽'],
      ['EQUAL_HEIGHT', '等高'],
      ['EQUAL_SIZE', '等尺寸'],
    ],
  },
  {
    label: '批量排列',
    options: [
      ['ARRANGE_HORIZONTAL', '横向'],
      ['ARRANGE_VERTICAL', '纵向'],
      ['ARRANGE_GRID', '网格'],
    ],
  },
  { label: '精度', options: [['ROUND_MM', '整数毫米']] },
] as const satisfies readonly {
  label: string
  options: readonly (readonly [LayoutAction, string])[]
}[]
let sequence = 0
let interaction:
  | {
      id: string
      kind: 'move' | 'resize'
      startX: number
      startY: number
      initial: { xMm: number; yMm: number; widthMm: number; heightMm?: number }
      initialItems: CanvasItem[]
      pxPerMm: number
    }
  | undefined
let boxSelection:
  | {
      startX: number
      startY: number
      pageRect: DOMRect
    }
  | undefined
const selectionBox = ref<{ xMm: number; yMm: number; widthMm: number; heightMm: number }>()
const guideLines = ref<{ xMm?: number; yMm?: number }>({})

const pageSize = computed(() =>
  props.modelValue.page.orientation === 'PORTRAIT'
    ? { width: 210, height: 297 }
    : { width: 297, height: 210 },
)
const scale = computed(() => Math.max(0.4, Math.min(1.25, Number(zoom.value) / 100 || 0.75)))
const groupedFields = computed(() => {
  const keyword = search.value.trim().toLocaleLowerCase()
  const groups = new Map<string, DocumentCatalogField[]>()
  props.fields
    .filter(
      (field) =>
        !keyword ||
        field.label.toLocaleLowerCase().includes(keyword) ||
        field.path.toLocaleLowerCase().includes(keyword),
    )
    .sort((a, b) => Number(a.sortOrder ?? 0) - Number(b.sortOrder ?? 0))
    .forEach((field) => {
      const group = field.group || (field.collectionPath ? '业务明细' : '基本信息')
      groups.set(group, [...(groups.get(group) ?? []), field])
    })
  return [...groups.entries()]
})
const selectedElement = computed(
  () => props.modelValue.elements.find((item) => item.id === selectedId.value) ?? null,
)
const selectedTable = computed(
  () => props.modelValue.tables.find((item) => item.id === selectedId.value) ?? null,
)
const canvasItems = computed<CanvasItem[]>(() => [
  ...props.modelValue.elements,
  ...props.modelValue.tables,
])
const selectedItems = computed(() =>
  canvasItems.value.filter((item) => selectedIds.value.includes(item.id)),
)
const firstCollectionField = computed(() => props.fields.find((field) => field.collectionPath))
const overflowIds = computed(() => {
  const margin = props.modelValue.page.marginMm
  const elementIds = props.modelValue.elements
    .filter(
      (item) =>
        item.xMm < margin.left ||
        item.yMm < margin.top ||
        item.xMm + item.widthMm > pageSize.value.width - margin.right ||
        item.yMm + item.heightMm > pageSize.value.height - margin.bottom,
    )
    .map((item) => item.id)
  const tableIds = props.modelValue.tables
    .filter(
      (item) =>
        item.xMm < margin.left ||
        item.yMm < margin.top ||
        item.xMm + item.widthMm > pageSize.value.width - margin.right ||
        item.yMm + item.heightMm > pageSize.value.height - margin.bottom,
    )
    .map((item) => item.id)
  return [...elementIds, ...tableIds]
})
const layoutConflict = computed(() => flowLayoutConflict(props.modelValue))

function commit(patch: Partial<DocumentDesignSchema>): void {
  const value = { ...props.modelValue, ...patch }
  emit('update:modelValue', value)
  emit('update:valid', valid(value))
}

function valid(value: DocumentDesignSchema): boolean {
  const margin = value.page.marginMm
  const size =
    value.page.orientation === 'PORTRAIT'
      ? { width: 210, height: 297 }
      : { width: 297, height: 210 }
  const count = value.elements.length + value.tables.length
  return (
    count > 0 &&
    count <= 200 &&
    value.elements.every(
      (item) =>
        item.xMm >= margin.left &&
        item.yMm >= margin.top &&
        item.xMm + item.widthMm <= size.width - margin.right &&
        item.yMm + item.heightMm <= size.height - margin.bottom,
    ) &&
    value.tables.every(
      (item) =>
        item.xMm >= margin.left &&
        item.yMm >= margin.top &&
        item.xMm + item.widthMm <= size.width - margin.right &&
        item.yMm + item.heightMm <= size.height - margin.bottom,
    ) &&
    !flowLayoutConflict(value)
  )
}

function flowLayoutConflict(value: DocumentDesignSchema): string {
  const tables = [...value.tables].sort((a, b) => a.yMm - b.yMm)
  for (let index = 1; index < tables.length; index += 1) {
    const previous = tables[index - 1]!
    const current = tables[index]!
    if (current.yMm < previous.yMm + previous.heightMm) {
      return `明细表 ${current.id} 与 ${previous.id} 的设计占位重叠`
    }
  }
  for (const table of tables) {
    const conflict = value.elements.find(
      (element) =>
        (element.repeat ?? 'BODY') === 'BODY' &&
        table.xMm < element.xMm + element.widthMm &&
        table.xMm + table.widthMm > element.xMm &&
        element.yMm + element.heightMm > table.yMm,
    )
    if (conflict) return `流式明细表 ${table.id} 可能与正文元素 ${conflict.id} 重叠`
  }
  return ''
}

watch(
  () => props.modelValue,
  (value) => emit('update:valid', valid(value)),
  { deep: true, immediate: true },
)

function changeOrientation(orientation: DocumentPageOrientation): void {
  commit({ page: { ...props.modelValue.page, orientation } })
}

function toggleOrientation(): void {
  changeOrientation(props.modelValue.page.orientation === 'PORTRAIT' ? 'LANDSCAPE' : 'PORTRAIT')
}

function updateMargin(value: string): void {
  const margin = Math.max(0, Math.min(30, Number(value) || 0))
  commit({
    page: {
      ...props.modelValue.page,
      marginMm: { top: margin, right: margin, bottom: margin, left: margin },
    },
  })
}

function selectOnly(id: string): void {
  selectedId.value = id
  selectedIds.value = id ? [id] : []
}

function focusItem(id: string): void {
  if (!selectedIds.value.includes(id)) selectOnly(id)
}

function addField(field: DocumentCatalogField, position?: { xMm: number; yMm: number }): void {
  if (props.disabled) return
  if (field.collectionPath) {
    addTableColumn(field, position)
    return
  }
  const existing = props.modelValue.elements.find((item) => item.fieldPath === field.path)
  if (existing) {
    selectOnly(existing.id)
    return
  }
  const offset = props.modelValue.elements.length * 8
  const element: DocumentCanvasElement = {
    id: nextId('field'),
    type: 'FIELD',
    fieldPath: field.path,
    text: field.label,
    xMm: round(position?.xMm ?? props.modelValue.page.marginMm.left + (offset % 80)),
    yMm: round(position?.yMm ?? props.modelValue.page.marginMm.top + (offset % 120)),
    widthMm: 60,
    heightMm: 12,
    fontSizePt: 12,
    align: 'LEFT',
    repeat: 'BODY',
    zIndex: props.modelValue.elements.length,
  }
  selectOnly(element.id)
  commit({
    elements: [...props.modelValue.elements, element],
  })
}

function addComponent(preset: ComponentPreset): void {
  if (preset === 'TABLE') {
    if (firstCollectionField.value) addTableColumn(firstCollectionField.value)
    return
  }
  const margin = props.modelValue.page.marginMm
  const divider = preset === 'DIVIDER'
  const footer = preset === 'FOOTER'
  const header = preset === 'HEADER'
  const title = preset === 'TITLE'
  const element: DocumentCanvasElement = {
    id: nextId(divider ? 'divider' : 'text'),
    type: divider ? 'DIVIDER' : 'TEXT',
    text: divider
      ? undefined
      : title
        ? '单据标题'
        : header
          ? '公司名称'
          : footer
            ? '第 1 页'
            : '说明文字',
    xMm: margin.left,
    yMm: footer ? pageSize.value.height - margin.bottom - 10 : margin.top,
    widthMm: Math.min(120, pageSize.value.width - margin.left - margin.right),
    heightMm: divider ? 2 : title ? 14 : 10,
    fontSizePt: title ? 18 : header || footer ? 10 : 12,
    align: title || header || footer ? 'CENTER' : 'LEFT',
    repeat: header ? 'HEADER' : footer ? 'FOOTER' : 'BODY',
    zIndex: props.modelValue.elements.length,
  }
  selectOnly(element.id)
  commit({ elements: [...props.modelValue.elements, element] })
}

function addTableColumn(
  field: DocumentCatalogField,
  position?: { xMm: number; yMm: number },
): void {
  const collectionPath = field.collectionPath!
  const table = props.modelValue.tables.find((item) => item.collectionPath === collectionPath)
  if (table?.columns.some((column) => column.fieldPath === field.path)) {
    selectOnly(table.id)
    return
  }
  const columnCount = (table?.columns.length ?? 0) + 1
  const widthMm =
    table?.widthMm ??
    Math.min(
      170,
      pageSize.value.width -
        props.modelValue.page.marginMm.left -
        props.modelValue.page.marginMm.right,
    )
  const columns = [
    ...(table?.columns ?? []),
    { fieldPath: field.path, header: field.label, widthMm: 35 },
  ].map((column) => ({ ...column, widthMm: round(widthMm / columnCount) }))
  columns[columns.length - 1]!.widthMm = round(
    widthMm - columns.slice(0, -1).reduce((sum, column) => sum + column.widthMm, 0),
  )
  const next: DocumentCanvasTable = table
    ? { ...table, columns }
    : {
        id: nextId('table'),
        collectionPath,
        xMm: round(position?.xMm ?? props.modelValue.page.marginMm.left),
        yMm: round(position?.yMm ?? 120),
        widthMm,
        heightMm: 38,
        columns,
      }
  selectOnly(next.id)
  commit({
    tables: table
      ? props.modelValue.tables.map((item) => (item.id === table.id ? next : item))
      : [...props.modelValue.tables, next],
  })
}

function onFieldDrag(event: DragEvent, field: DocumentCatalogField): void {
  event.dataTransfer?.setData('application/x-document-field', field.path)
}

function onDrop(event: DragEvent): void {
  const path = event.dataTransfer?.getData('application/x-document-field')
  const field = props.fields.find((item) => item.path === path)
  const target = event.currentTarget as HTMLElement
  if (!field || !target) return
  const rect = target.getBoundingClientRect()
  addField(field, {
    xMm: ((event.clientX - rect.left) / rect.width) * pageSize.value.width,
    yMm: ((event.clientY - rect.top) / rect.height) * pageSize.value.height,
  })
}

function startInteraction(
  event: PointerEvent,
  item: DocumentCanvasElement | DocumentCanvasTable,
  kind: 'move' | 'resize',
): void {
  if (props.disabled) return
  const page = (event.currentTarget as HTMLElement).closest('.document-canvas__page') as HTMLElement
  const initialItems =
    kind === 'move' && selectedIds.value.includes(item.id) ? selectedItems.value : [item]
  interaction = {
    id: item.id,
    kind,
    startX: event.clientX,
    startY: event.clientY,
    initial: {
      xMm: item.xMm,
      yMm: item.yMm,
      widthMm: item.widthMm,
      heightMm: 'heightMm' in item ? item.heightMm : undefined,
    },
    initialItems: initialItems.map((candidate) => ({ ...candidate })),
    pxPerMm: page.getBoundingClientRect().width / pageSize.value.width,
  }
  if (kind === 'resize' || !selectedIds.value.includes(item.id)) selectOnly(item.id)
  ;(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
}

function startBoxSelection(event: PointerEvent): void {
  if (props.disabled) return
  const page = event.currentTarget as HTMLElement
  const pageRect = page.getBoundingClientRect()
  const point = pagePoint(event, pageRect)
  boxSelection = { startX: point.xMm, startY: point.yMm, pageRect }
  selectionBox.value = { xMm: point.xMm, yMm: point.yMm, widthMm: 0, heightMm: 0 }
  selectOnly('')
  page.setPointerCapture?.(event.pointerId)
}

function moveBoxSelection(event: PointerEvent): void {
  if (!boxSelection) return
  const point = pagePoint(event, boxSelection.pageRect)
  const box = {
    xMm: Math.min(boxSelection.startX, point.xMm),
    yMm: Math.min(boxSelection.startY, point.yMm),
    widthMm: Math.abs(point.xMm - boxSelection.startX),
    heightMm: Math.abs(point.yMm - boxSelection.startY),
  }
  selectionBox.value = box
  const ids = canvasItems.value
    .filter(
      (item) =>
        item.xMm < box.xMm + box.widthMm &&
        item.xMm + item.widthMm > box.xMm &&
        item.yMm < box.yMm + box.heightMm &&
        item.yMm + item.heightMm > box.yMm,
    )
    .map((item) => item.id)
  selectedIds.value = ids
  selectedId.value = ids.at(-1) ?? ''
}

function stopBoxSelection(): void {
  boxSelection = undefined
  selectionBox.value = undefined
}

function pagePoint(event: PointerEvent, rect: DOMRect): { xMm: number; yMm: number } {
  return {
    xMm: ((event.clientX - rect.left) / rect.width) * pageSize.value.width,
    yMm: ((event.clientY - rect.top) / rect.height) * pageSize.value.height,
  }
}

function itemBounds(items: CanvasItem[]): ItemRect {
  const left = Math.min(...items.map((item) => item.xMm))
  const top = Math.min(...items.map((item) => item.yMm))
  const right = Math.max(...items.map((item) => item.xMm + item.widthMm))
  const bottom = Math.max(...items.map((item) => item.yMm + item.heightMm))
  return { xMm: left, yMm: top, widthMm: right - left, heightMm: bottom - top }
}

function safeBounds(): ItemRect {
  const margin = props.modelValue.page.marginMm
  return {
    xMm: margin.left,
    yMm: margin.top,
    widthMm: pageSize.value.width - margin.left - margin.right,
    heightMm: pageSize.value.height - margin.top - margin.bottom,
  }
}

function primaryItem(): CanvasItem | undefined {
  return selectedItems.value.find((item) => item.id === selectedId.value) ?? selectedItems.value[0]
}

function canApplyLayout(action: LayoutAction): boolean {
  const count = selectedItems.value.length
  if (
    selectedItems.value.some((item) =>
      props.modelValue.tables.some((table) => table.id === item.id),
    ) &&
    ![
      'LEFT',
      'CENTER',
      'RIGHT',
      'DISTRIBUTE_HORIZONTAL',
      'SPACE_HORIZONTAL',
      'ATTACH_HORIZONTAL',
      'EQUAL_WIDTH',
    ].includes(action)
  )
    return false
  if (action === 'ROUND_MM') return count > 0
  if (action.startsWith('DISTRIBUTE_')) return count >= 3
  if (
    ['TOP', 'MIDDLE', 'BOTTOM', 'LEFT', 'CENTER', 'RIGHT'].includes(action) &&
    alignmentReference.value === 'CANVAS'
  )
    return count > 0
  return count >= 2
}

function scaleTableColumns(table: DocumentCanvasTable): DocumentCanvasTable {
  if (!table.columns.length) return table
  const total = table.columns.reduce((sum, column) => sum + column.widthMm, 0) || 1
  const columns = table.columns.map((column) => ({
    ...column,
    widthMm: round((column.widthMm / total) * table.widthMm),
  }))
  columns[columns.length - 1]!.widthMm = round(
    table.widthMm - columns.slice(0, -1).reduce((sum, column) => sum + column.widthMm, 0),
  )
  return { ...table, columns }
}

function commitItems(items: CanvasItem[]): void {
  const updates = new Map(items.map((item) => [item.id, item]))
  commit({
    elements: props.modelValue.elements.map(
      (item) => (updates.get(item.id) as DocumentCanvasElement | undefined) ?? item,
    ),
    tables: props.modelValue.tables.map((item) => {
      const updated = updates.get(item.id) as DocumentCanvasTable | undefined
      return updated && updated.widthMm !== item.widthMm
        ? scaleTableColumns(updated)
        : (updated ?? item)
    }),
  })
}

function alignItems(alignment: ComponentAlignment): CanvasItem[] {
  const reference =
    alignmentReference.value === 'CANVAS'
      ? safeBounds()
      : alignmentReference.value === 'KEY' && primaryItem()
        ? itemBounds([primaryItem()!])
        : itemBounds(selectedItems.value)
  const right = reference.xMm + reference.widthMm
  const bottom = reference.yMm + reference.heightMm
  return selectedItems.value.map((item) => ({
    ...item,
    xMm:
      alignment === 'LEFT'
        ? reference.xMm
        : alignment === 'CENTER'
          ? round(reference.xMm + (reference.widthMm - item.widthMm) / 2)
          : alignment === 'RIGHT'
            ? round(right - item.widthMm)
            : item.xMm,
    yMm:
      alignment === 'TOP'
        ? reference.yMm
        : alignment === 'MIDDLE'
          ? round(reference.yMm + (reference.heightMm - item.heightMm) / 2)
          : alignment === 'BOTTOM'
            ? round(bottom - item.heightMm)
            : item.yMm,
  }))
}

function sequenceItems(axis: 'x' | 'y', gap: number, align: boolean): CanvasItem[] {
  const key = axis === 'x' ? 'xMm' : 'yMm'
  const size = axis === 'x' ? 'widthMm' : 'heightMm'
  const ordered = [...selectedItems.value].sort((a, b) => a[key] - b[key])
  const bounds = itemBounds(ordered)
  let cursor = axis === 'x' ? bounds.xMm : bounds.yMm
  return ordered.map((item) => {
    const next = {
      ...item,
      [key]: round(cursor),
      ...(align ? (axis === 'x' ? { yMm: bounds.yMm } : { xMm: bounds.xMm }) : {}),
    }
    cursor += item[size] + gap
    return next
  })
}

function distributeItems(axis: 'x' | 'y'): CanvasItem[] {
  const key = axis === 'x' ? 'xMm' : 'yMm'
  const size = axis === 'x' ? 'widthMm' : 'heightMm'
  const ordered = [...selectedItems.value].sort((a, b) => a[key] - b[key])
  const first = ordered[0]!
  const last = ordered.at(-1)!
  const span = last[key] + last[size] - first[key]
  const total = ordered.reduce((sum, item) => sum + item[size], 0)
  const gap = (span - total) / (ordered.length - 1)
  let cursor = first[key]
  return ordered.map((item) => {
    const next = { ...item, [key]: round(cursor) }
    cursor += item[size] + gap
    return next
  })
}

function applyLayout(action: LayoutAction): void {
  if (!canApplyLayout(action)) return
  const gap = Math.max(0, Math.min(50, Number(spacingMm.value) || 0))
  let next: CanvasItem[]
  if (['TOP', 'MIDDLE', 'BOTTOM', 'LEFT', 'CENTER', 'RIGHT'].includes(action)) {
    next = alignItems(action as ComponentAlignment)
  } else if (action === 'DISTRIBUTE_HORIZONTAL' || action === 'DISTRIBUTE_VERTICAL') {
    next = distributeItems(action.endsWith('HORIZONTAL') ? 'x' : 'y')
  } else if (action === 'SPACE_HORIZONTAL' || action === 'SPACE_VERTICAL') {
    next = sequenceItems(action.endsWith('HORIZONTAL') ? 'x' : 'y', gap, false)
  } else if (action === 'ATTACH_HORIZONTAL' || action === 'ATTACH_VERTICAL') {
    next = sequenceItems(action.endsWith('HORIZONTAL') ? 'x' : 'y', 0, false)
  } else if (action === 'ARRANGE_HORIZONTAL' || action === 'ARRANGE_VERTICAL') {
    next = sequenceItems(action.endsWith('HORIZONTAL') ? 'x' : 'y', gap, true)
  } else if (action === 'ARRANGE_GRID') {
    const bounds = itemBounds(selectedItems.value)
    const columns = Math.ceil(Math.sqrt(selectedItems.value.length))
    const width = Math.max(...selectedItems.value.map((item) => item.widthMm))
    const height = Math.max(...selectedItems.value.map((item) => item.heightMm))
    next = selectedItems.value.map((item, index) => ({
      ...item,
      xMm: round(bounds.xMm + (index % columns) * (width + gap)),
      yMm: round(bounds.yMm + Math.floor(index / columns) * (height + gap)),
    }))
  } else if (action === 'ROUND_MM') {
    next = selectedItems.value.map((item) => ({
      ...item,
      xMm: Math.round(item.xMm),
      yMm: Math.round(item.yMm),
      widthMm: Math.max(12, Math.round(item.widthMm)),
      heightMm: Math.max(8, Math.round(item.heightMm)),
    }))
  } else {
    const primary = primaryItem()!
    next = selectedItems.value.map((item) => ({
      ...item,
      widthMm: action === 'EQUAL_WIDTH' || action === 'EQUAL_SIZE' ? primary.widthMm : item.widthMm,
      heightMm:
        action === 'EQUAL_HEIGHT' || action === 'EQUAL_SIZE' ? primary.heightMm : item.heightMm,
    }))
  }
  commitItems(next)
}

function snapMove(
  dx: number,
  dy: number,
  moving: CanvasItem[],
  pxPerMm: number,
): { dx: number; dy: number } {
  const bounds = itemBounds(moving)
  if (snapToGrid.value) {
    dx = Math.round((bounds.xMm + dx) / 5) * 5 - bounds.xMm
    dy = Math.round((bounds.yMm + dy) / 5) * 5 - bounds.yMm
  }
  guideLines.value = {}
  if (!smartGuides.value) return { dx, dy }
  const movingIds = new Set(moving.map((item) => item.id))
  const references = [safeBounds(), ...canvasItems.value.filter((item) => !movingIds.has(item.id))]
  const xTargets = references.flatMap((item) => [
    item.xMm,
    item.xMm + item.widthMm / 2,
    item.xMm + item.widthMm,
  ])
  const yTargets = references.flatMap((item) => [
    item.yMm,
    item.yMm + item.heightMm / 2,
    item.yMm + item.heightMm,
  ])
  const threshold = 6 / pxPerMm
  const nearest = (anchors: number[], targets: number[]) => {
    let match: { delta: number; target: number } | undefined
    for (const anchor of anchors)
      for (const target of targets) {
        const delta = target - anchor
        if (Math.abs(delta) <= threshold && (!match || Math.abs(delta) < Math.abs(match.delta)))
          match = { delta, target }
      }
    return match
  }
  const xMatch = nearest(
    [bounds.xMm + dx, bounds.xMm + bounds.widthMm / 2 + dx, bounds.xMm + bounds.widthMm + dx],
    xTargets,
  )
  const yMatch = nearest(
    [bounds.yMm + dy, bounds.yMm + bounds.heightMm / 2 + dy, bounds.yMm + bounds.heightMm + dy],
    yTargets,
  )
  if (xMatch) {
    dx += xMatch.delta
    guideLines.value.xMm = xMatch.target
  }
  if (yMatch) {
    dy += yMatch.delta
    guideLines.value.yMm = yMatch.target
  }
  return { dx, dy }
}

function moveInteraction(event: PointerEvent): void {
  if (!interaction) return
  let dx = (event.clientX - interaction.startX) / interaction.pxPerMm
  let dy = (event.clientY - interaction.startY) / interaction.pxPerMm
  if (interaction.kind === 'move') {
    ;({ dx, dy } = snapMove(dx, dy, interaction.initialItems, interaction.pxPerMm))
    commitItems(
      interaction.initialItems.map((item) => ({
        ...item,
        xMm: round(item.xMm + dx),
        yMm: round(item.yMm + dy),
      })),
    )
    return
  }
  const itemPatch = snapToGrid.value
    ? {
        widthMm: Math.max(12, Math.round((interaction.initial.widthMm + dx) / 5) * 5),
        heightMm: Math.max(8, Math.round(((interaction.initial.heightMm ?? 8) + dy) / 5) * 5),
      }
    : {
        widthMm: Math.max(12, round(interaction.initial.widthMm + dx)),
        heightMm: Math.max(8, round((interaction.initial.heightMm ?? 8) + dy)),
      }
  if (props.modelValue.elements.some((item) => item.id === interaction!.id)) {
    commit({
      elements: props.modelValue.elements.map((item) =>
        item.id === interaction!.id ? { ...item, ...itemPatch } : item,
      ),
    })
  } else {
    commit({
      tables: props.modelValue.tables.map((item) =>
        item.id === interaction!.id
          ? resizeTableColumns({ ...item, ...itemPatch } as DocumentCanvasTable)
          : item,
      ),
    })
  }
}

function resizeTableColumns(table: DocumentCanvasTable): DocumentCanvasTable {
  return interaction?.kind === 'resize' ? scaleTableColumns(table) : table
}

function stopInteraction(): void {
  interaction = undefined
  guideLines.value = {}
}

function updateSelected(key: keyof DocumentCanvasElement, value: string): void {
  if (!selectedElement.value) return
  const numericKeys = new Set<keyof DocumentCanvasElement>([
    'xMm',
    'yMm',
    'widthMm',
    'heightMm',
    'fontSizePt',
    'zIndex',
  ])
  const next = numericKeys.has(key) ? Number(value) || 0 : value
  commit({
    elements: props.modelValue.elements.map((item) =>
      item.id === selectedElement.value!.id ? { ...item, [key]: next } : item,
    ),
  })
}

function removeSelected(): void {
  const ids = new Set(selectedIds.value)
  commit({
    elements: props.modelValue.elements.filter((item) => !ids.has(item.id)),
    tables: props.modelValue.tables.filter((item) => !ids.has(item.id)),
  })
  selectOnly('')
}

function updateTableColumn(index: number, patch: { header?: string; widthMm?: number }): void {
  const table = selectedTable.value
  if (!table) return
  let columns = table.columns.map((column, current) =>
    current === index ? { ...column, ...patch } : { ...column },
  )
  if (patch.widthMm !== undefined) {
    const desired = Math.max(
      5,
      Math.min(table.widthMm - 5 * (columns.length - 1), Number(patch.widthMm) || 5),
    )
    const others = columns.filter((_, current) => current !== index)
    const otherTotal = others.reduce((sum, column) => sum + column.widthMm, 0) || others.length
    const remaining = table.widthMm - desired
    columns = columns.map((column, current) =>
      current === index
        ? { ...column, widthMm: round(desired) }
        : { ...column, widthMm: round((column.widthMm / otherTotal) * remaining) },
    )
    const correction = round(
      table.widthMm - columns.reduce((sum, column) => sum + column.widthMm, 0),
    )
    const correctionIndex = index === columns.length - 1 ? 0 : columns.length - 1
    columns[correctionIndex]!.widthMm = round(columns[correctionIndex]!.widthMm + correction)
  }
  commit({
    tables: props.modelValue.tables.map((item) =>
      item.id === table.id ? { ...item, columns } : item,
    ),
  })
}

function moveTableColumn(index: number, offset: -1 | 1): void {
  const table = selectedTable.value
  const target = index + offset
  if (!table || target < 0 || target >= table.columns.length) return
  const columns = [...table.columns]
  ;[columns[index], columns[target]] = [columns[target]!, columns[index]!]
  commit({
    tables: props.modelValue.tables.map((item) =>
      item.id === table.id ? { ...item, columns } : item,
    ),
  })
}

function removeTableColumn(index: number): void {
  const table = selectedTable.value
  if (!table) return
  if (table.columns.length === 1) {
    removeSelected()
    return
  }
  const columns = table.columns.filter((_, current) => current !== index)
  const total = columns.reduce((sum, column) => sum + column.widthMm, 0)
  const normalized = columns.map((column) => ({
    ...column,
    widthMm: round((column.widthMm / total) * table.widthMm),
  }))
  normalized[normalized.length - 1]!.widthMm = round(
    table.widthMm - normalized.slice(0, -1).reduce((sum, column) => sum + column.widthMm, 0),
  )
  commit({
    tables: props.modelValue.tables.map((item) =>
      item.id === table.id ? { ...item, columns: normalized } : item,
    ),
  })
}

function nextId(prefix: string): string {
  sequence += 1
  return `${prefix}-${Date.now().toString(36)}-${sequence}`
}

function round(value: number): number {
  return Math.round(value * 10) / 10
}
</script>

<template>
  <div class="document-canvas">
    <aside class="document-canvas__fields" aria-label="字段目录">
      <section class="document-canvas__library" aria-labelledby="document-component-library">
        <h3 id="document-component-library">组件库</h3>
        <div class="document-canvas__library-grid">
          <button
            v-for="preset in componentPresets"
            :key="preset.key"
            type="button"
            class="document-canvas__component"
            :disabled="disabled || (preset.key === 'TABLE' && !firstCollectionField)"
            @click="addComponent(preset.key)"
          >
            <strong>{{ preset.label }}</strong>
            <small>{{ preset.description }}</small>
          </button>
        </div>
      </section>
      <h3>业务字段</h3>
      <V2Input v-model="search" type="search" label="搜索字段" placeholder="名称或路径" />
      <p class="document-canvas__hint">点击或拖入字段；集合字段自动创建明细表。</p>
      <p v-if="!groupedFields.length" class="document-canvas__empty">没有匹配字段</p>
      <section v-for="[group, items] in groupedFields" :key="group">
        <h3>{{ group }}</h3>
        <button
          v-for="field in items"
          :key="field.path"
          type="button"
          class="document-canvas__field"
          draggable="true"
          :disabled="disabled"
          @click="addField(field)"
          @dragstart="onFieldDrag($event, field)"
        >
          <span>{{ field.label }}</span>
        </button>
      </section>
    </aside>

    <section class="document-canvas__workspace" aria-label="A4 设计画布">
      <div class="document-canvas__status" aria-live="polite">
        <span>{{ viewMode === 'DESIGN' ? '设计模式' : 'HTML 预览' }}</span>
        <span>{{ pageSize.width }} × {{ pageSize.height }} mm</span>
        <span>{{ zoom }}%</span>
        <span v-if="selectedIds.length">已选 {{ selectedIds.length }} 个</span>
      </div>
      <p v-if="overflowIds.length" class="document-canvas__warning" role="alert">
        {{ overflowIds.length }} 个元素越出页面安全区域，保存已阻止。
      </p>
      <p v-else-if="layoutConflict" class="document-canvas__warning" role="alert">
        {{ layoutConflict }}，保存已阻止。
      </p>
      <div class="document-canvas__viewport" :class="{ 'is-preview': viewMode === 'PREVIEW' }">
        <div
          v-if="viewMode === 'DESIGN'"
          class="document-canvas__page"
          :class="{ 'has-grid': gridVisible }"
          :style="{
            width: `${pageSize.width}mm`,
            height: `${pageSize.height}mm`,
            transform: `scale(${scale})`,
            '--margin-top': `${modelValue.page.marginMm.top}mm`,
            '--margin-right': `${modelValue.page.marginMm.right}mm`,
            '--margin-bottom': `${modelValue.page.marginMm.bottom}mm`,
            '--margin-left': `${modelValue.page.marginMm.left}mm`,
          }"
          @dragover.prevent
          @drop.prevent="onDrop"
          @pointerdown.self="startBoxSelection"
          @pointermove="moveBoxSelection"
          @pointerup="stopBoxSelection"
          @pointercancel="stopBoxSelection"
        >
          <div class="document-canvas__safe-area" aria-hidden="true"></div>
          <div
            v-if="guideLines.xMm !== undefined"
            class="document-canvas__guide is-vertical"
            aria-hidden="true"
            :style="{ left: `${guideLines.xMm}mm` }"
          ></div>
          <div
            v-if="guideLines.yMm !== undefined"
            class="document-canvas__guide is-horizontal"
            aria-hidden="true"
            :style="{ top: `${guideLines.yMm}mm` }"
          ></div>
          <div
            v-if="selectionBox"
            class="document-canvas__selection-box"
            aria-hidden="true"
            :style="{
              left: `${selectionBox.xMm}mm`,
              top: `${selectionBox.yMm}mm`,
              width: `${selectionBox.widthMm}mm`,
              height: `${selectionBox.heightMm}mm`,
            }"
          ></div>
          <div
            v-for="element in modelValue.elements"
            :key="element.id"
            class="document-canvas__element"
            :class="{
              'is-selected': selectedIds.includes(element.id),
              'is-primary': selectedId === element.id && selectedIds.length > 1,
              'is-overflow': overflowIds.includes(element.id),
              'is-divider': element.type === 'DIVIDER',
            }"
            :style="{
              left: `${element.xMm}mm`,
              top: `${element.yMm}mm`,
              width: `${element.widthMm}mm`,
              height: `${element.heightMm}mm`,
              fontSize: `${element.fontSizePt ?? 12}pt`,
              textAlign: (element.align ?? 'LEFT').toLowerCase(),
              zIndex: element.zIndex ?? 0,
            }"
            tabindex="0"
            @pointerdown="startInteraction($event, element, 'move')"
            @pointermove="moveInteraction"
            @pointerup="stopInteraction"
            @pointercancel="stopInteraction"
            @focus="focusItem(element.id)"
          >
            <hr v-if="element.type === 'DIVIDER'" />
            <template v-else>
              <span>{{ element.text }}</span
              ><code v-if="element.fieldPath" v-text="'{{' + element.fieldPath + '}}'"></code>
            </template>
            <button
              type="button"
              class="document-canvas__resize"
              aria-label="调整元素尺寸"
              @pointerdown.stop="startInteraction($event, element, 'resize')"
              @pointermove.stop="moveInteraction"
              @pointerup.stop="stopInteraction"
              @pointercancel.stop="stopInteraction"
            ></button>
          </div>
          <div
            v-for="table in modelValue.tables"
            :key="table.id"
            class="document-canvas__table"
            :class="{
              'is-selected': selectedIds.includes(table.id),
              'is-primary': selectedId === table.id && selectedIds.length > 1,
              'is-overflow': overflowIds.includes(table.id),
            }"
            :style="{
              left: `${table.xMm}mm`,
              top: `${table.yMm}mm`,
              width: `${table.widthMm}mm`,
              height: `${table.heightMm}mm`,
            }"
            tabindex="0"
            @pointerdown="startInteraction($event, table, 'move')"
            @pointermove="moveInteraction"
            @pointerup="stopInteraction"
            @pointercancel="stopInteraction"
            @focus="focusItem(table.id)"
          >
            <table class="document-canvas__table-content">
              <thead>
                <tr>
                  <th
                    v-for="column in table.columns"
                    :key="column.fieldPath"
                    :style="{ width: `${column.widthMm}mm` }"
                  >
                    {{ column.header }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td v-for="column in table.columns" :key="column.fieldPath">
                    <code
                      v-text="'{{' + column.fieldPath.slice(table.collectionPath.length + 1) + '}}'"
                    ></code>
                  </td>
                </tr>
              </tbody>
            </table>
            <button
              type="button"
              class="document-canvas__resize"
              aria-label="调整明细表尺寸"
              @pointerdown.stop="startInteraction($event, table, 'resize')"
              @pointermove.stop="moveInteraction"
              @pointerup.stop="stopInteraction"
              @pointercancel.stop="stopInteraction"
            ></button>
          </div>
        </div>
        <div
          v-else
          class="document-canvas__preview-page"
          :style="{
            width: `${pageSize.width}mm`,
            height: `${pageSize.height}mm`,
            transform: `scale(${scale})`,
          }"
        >
          <p v-if="previewLoading" class="document-canvas__preview-state">正在生成预览…</p>
          <p v-else-if="previewError" class="document-canvas__preview-state is-error" role="alert">
            {{ previewError }}
          </p>
          <iframe v-else title="业务单据 HTML 预览" sandbox="" :srcdoc="previewHtml"></iframe>
        </div>
      </div>
    </section>

    <aside class="document-canvas__properties" aria-label="元素属性">
      <section class="document-canvas__toolbar" aria-label="画布工具">
        <V2Button
          data-testid="orientation-toggle"
          size="small"
          variant="secondary"
          :aria-label="`当前${modelValue.page.orientation === 'PORTRAIT' ? '纵向' : '横向'}，点击切换纸张方向`"
          @click="toggleOrientation"
        >
          {{ modelValue.page.orientation === 'PORTRAIT' ? '纵向 A4' : '横向 A4' }}
        </V2Button>
        <V2Button
          data-testid="grid-toggle"
          size="small"
          variant="secondary"
          @click="gridVisible = !gridVisible"
        >
          {{ gridVisible ? '隐藏网格' : '显示网格' }}
        </V2Button>
        <label
          >统一边距(mm)<input
            :value="modelValue.page.marginMm.top"
            type="number"
            min="0"
            max="30"
            @input="updateMargin(($event.target as HTMLInputElement).value)"
        /></label>
        <label
          >缩放<select v-model="zoom">
            <option value="50">50%</option>
            <option value="75">75%</option>
            <option value="100">100%</option>
          </select></label
        >
        <label v-if="viewMode === 'DESIGN'"
          >间距(mm)<input
            v-model="spacingMm"
            data-testid="layout-spacing"
            type="number"
            min="0"
            max="50"
        /></label>
        <V2ActionMenu
          v-if="viewMode === 'DESIGN'"
          class="document-canvas__alignment"
          label="组件排版"
          :trigger-text="selectedIds.length > 1 ? `排版（${selectedIds.length}）` : '排版'"
        >
          <section class="document-canvas__alignment-group">
            <strong>对齐基准</strong>
            <div>
              <V2Button
                v-for="reference in alignmentReferences"
                :key="reference[0]"
                size="small"
                :variant="alignmentReference === reference[0] ? 'primary' : 'secondary'"
                :aria-pressed="alignmentReference === reference[0]"
                :data-testid="`align-reference-${reference[0].toLowerCase()}`"
                @click="alignmentReference = reference[0]"
              >
                {{ reference[1] }}
              </V2Button>
            </div>
          </section>
          <section
            v-for="group in layoutGroups"
            :key="group.label"
            class="document-canvas__alignment-group"
          >
            <strong>{{ group.label }}</strong>
            <div>
              <V2Button
                v-for="option in group.options"
                :key="option[0]"
                size="small"
                variant="secondary"
                :disabled="!canApplyLayout(option[0])"
                :data-testid="`layout-${option[0].toLowerCase().replaceAll('_', '-')}`"
                :aria-label="option[1]"
                @click="applyLayout(option[0])"
              >
                {{ option[1] }}
              </V2Button>
            </div>
          </section>
          <section class="document-canvas__alignment-group">
            <strong>移动辅助</strong>
            <div>
              <V2Button
                data-testid="snap-grid"
                size="small"
                :variant="snapToGrid ? 'primary' : 'secondary'"
                :aria-pressed="snapToGrid"
                @click="snapToGrid = !snapToGrid"
              >
                5mm 吸附
              </V2Button>
              <V2Button
                data-testid="smart-guides"
                size="small"
                :variant="smartGuides ? 'primary' : 'secondary'"
                :aria-pressed="smartGuides"
                @click="smartGuides = !smartGuides"
              >
                智能参考线
              </V2Button>
            </div>
          </section>
        </V2ActionMenu>
        <V2Button
          data-testid="preview-toggle"
          size="small"
          :variant="viewMode === 'PREVIEW' ? 'primary' : 'secondary'"
          :disabled="viewMode === 'DESIGN' && !previewHtml && !previewLoading"
          @click="viewMode = viewMode === 'DESIGN' ? 'PREVIEW' : 'DESIGN'"
        >
          {{ viewMode === 'DESIGN' ? '预览' : '返回设计' }}
        </V2Button>
      </section>
      <div class="document-canvas__property-fields">
        <template v-if="viewMode === 'PREVIEW'">
          <h3>预览设置</h3>
          <V2Input
            :model-value="previewBusinessId"
            label="真实业务对象 ID"
            type="number"
            placeholder="留空使用示例数据"
            @update:model-value="emit('update:previewBusinessId', $event)"
          />
          <p class="document-canvas__hint">预览与正式生成使用同一服务端编译链。</p>
        </template>
        <template v-else>
          <h3>属性</h3>
        </template>
        <template v-if="viewMode === 'DESIGN' && selectedElement">
          <V2Input
            :model-value="selectedElement.text"
            label="显示名称"
            @update:model-value="updateSelected('text', $event)"
          />
          <V2Input
            :model-value="String(selectedElement.xMm)"
            type="number"
            label="X(mm)"
            @update:model-value="updateSelected('xMm', $event)"
          />
          <V2Input
            :model-value="String(selectedElement.yMm)"
            type="number"
            label="Y(mm)"
            @update:model-value="updateSelected('yMm', $event)"
          />
          <V2Input
            :model-value="String(selectedElement.widthMm)"
            type="number"
            label="宽(mm)"
            @update:model-value="updateSelected('widthMm', $event)"
          />
          <V2Input
            :model-value="String(selectedElement.heightMm)"
            type="number"
            label="高(mm)"
            @update:model-value="updateSelected('heightMm', $event)"
          />
          <V2Input
            :model-value="String(selectedElement.fontSizePt ?? 12)"
            type="number"
            label="字号(pt)"
            @update:model-value="updateSelected('fontSizePt', $event)"
          />
          <V2Input
            :model-value="String(selectedElement.zIndex ?? 0)"
            type="number"
            label="层级"
            @update:model-value="updateSelected('zIndex', $event)"
          />
          <label
            >对齐<select
              :value="selectedElement.align"
              @change="updateSelected('align', ($event.target as HTMLSelectElement).value)"
            >
              <option value="LEFT">左</option>
              <option value="CENTER">中</option>
              <option value="RIGHT">右</option>
            </select></label
          >
          <label
            >跨页区域<select
              :value="selectedElement.repeat ?? 'BODY'"
              @change="updateSelected('repeat', ($event.target as HTMLSelectElement).value)"
            >
              <option value="BODY">正文</option>
              <option value="HEADER">重复页眉</option>
              <option value="FOOTER">重复页脚</option>
            </select></label
          >
        </template>
        <template v-else-if="viewMode === 'DESIGN' && selectedTable">
          <p>{{ selectedTable.collectionPath }} 明细列</p>
          <p class="document-canvas__hint">
            Y 为首表锚点或表间设计间距；高度为最小占位，实际行数会向后推流式表格。
          </p>
          <div
            v-for="(column, index) in selectedTable.columns"
            :key="column.fieldPath"
            class="document-canvas__column-property"
          >
            <code>{{ column.fieldPath }}</code>
            <V2Input
              :model-value="column.header"
              label="列标题"
              @update:model-value="updateTableColumn(index, { header: $event })"
            />
            <V2Input
              :model-value="String(column.widthMm)"
              type="number"
              label="列宽(mm)"
              @update:model-value="updateTableColumn(index, { widthMm: Number($event) })"
            />
            <div>
              <V2Button size="small" variant="secondary" @click="moveTableColumn(index, -1)"
                >前移</V2Button
              >
              <V2Button size="small" variant="secondary" @click="moveTableColumn(index, 1)"
                >后移</V2Button
              >
              <V2Button size="small" variant="danger" @click="removeTableColumn(index)"
                >删除列</V2Button
              >
            </div>
          </div>
        </template>
        <p v-else-if="viewMode === 'DESIGN'">选择画布元素后编辑属性</p>
        <V2Button
          v-if="viewMode === 'DESIGN' && selectedId"
          size="small"
          variant="danger"
          @click="removeSelected"
          >删除所选</V2Button
        >
      </div>
    </aside>
  </div>
</template>

<style scoped>
.document-canvas {
  display: grid;
  grid-template-columns: 16rem minmax(32rem, 1fr) 18rem;
  gap: var(--v2-space-3);
  min-height: 38rem;
}
.document-canvas__fields,
.document-canvas__property-fields {
  display: grid;
  align-content: start;
  gap: var(--v2-space-2);
  max-height: 70vh;
  overflow: auto;
}
.document-canvas__fields section {
  display: grid;
  gap: var(--v2-space-1);
}
.document-canvas__fields > section:not(.document-canvas__library) {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.document-canvas__fields > section:not(.document-canvas__library) > h3 {
  grid-column: 1 / -1;
}
.document-canvas__properties {
  display: grid;
  align-content: start;
  gap: var(--v2-space-3);
  min-width: 0;
}
.document-canvas__property-fields {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  max-height: calc(70vh - 13rem);
}
.document-canvas__property-fields > :is(h3, p, .document-canvas__column-property, .v2-button) {
  grid-column: 1 / -1;
}
.document-canvas__library {
  padding-bottom: var(--v2-space-3);
  border-bottom: 1px solid var(--v2-color-border);
}
.document-canvas__library-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-2);
}
.document-canvas__component {
  display: grid;
  gap: var(--v2-space-1);
  min-height: 4.25rem;
  padding: var(--v2-space-2);
  color: var(--v2-color-text);
  font: inherit;
  text-align: left;
  cursor: pointer;
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
  box-shadow: var(--v2-shadow-control);
}
.document-canvas__component:hover:not(:disabled),
.document-canvas__field:hover:not(:disabled) {
  background: var(--v2-color-surface-hover);
  border-color: var(--v2-color-primary);
}
.document-canvas__component small,
.document-canvas__hint {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
  line-height: var(--v2-line-height-ui);
}
.document-canvas h3 {
  margin: var(--v2-space-2) 0 0;
  font-size: 0.9rem;
}
.document-canvas__field {
  display: grid;
  gap: 0.15rem;
  padding: var(--v2-space-2);
  text-align: left;
  cursor: grab;
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-sm);
}
.document-canvas__workspace {
  min-width: 0;
}
.document-canvas__toolbar {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: flex-end;
  gap: var(--v2-space-2);
  padding-bottom: var(--v2-space-3);
  border-bottom: 1px solid var(--v2-color-border);
}
.document-canvas__toolbar > :deep(.v2-button),
.document-canvas__alignment,
.document-canvas__alignment :deep(.v2-action-menu__trigger) {
  width: 100%;
}
.document-canvas__alignment {
  align-self: flex-end;
}
.document-canvas__alignment :deep(.v2-action-menu__trigger) {
  min-height: 2rem;
}
.document-canvas__alignment :deep(.v2-action-menu__content) {
  min-width: 20rem;
  max-height: clamp(12rem, calc(100vh - 21rem), 28rem);
  overflow-y: auto;
  overscroll-behavior: contain;
}
.document-canvas__alignment-group {
  display: grid;
  gap: var(--v2-space-1);
}
.document-canvas__alignment-group + .document-canvas__alignment-group {
  padding-top: var(--v2-space-2);
  border-top: 1px solid var(--v2-color-border);
}
.document-canvas__alignment-group > strong {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}
.document-canvas__alignment-group > div {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--v2-space-1);
}
.document-canvas__alignment-group :deep(.v2-button) {
  justify-content: center;
}
.document-canvas__status {
  display: flex;
  gap: var(--v2-space-3);
  padding: var(--v2-space-1) var(--v2-space-2);
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
  background: var(--v2-color-surface-subtle);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md) var(--v2-radius-md) 0 0;
}
.document-canvas__toolbar label,
.document-canvas__properties label {
  display: grid;
  gap: 0.2rem;
  font-size: 0.8rem;
}
.document-canvas__toolbar input,
.document-canvas__toolbar select,
.document-canvas__properties select {
  width: 100%;
  min-height: 2rem;
  box-sizing: border-box;
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-sm);
}
.document-canvas__viewport {
  min-height: 34rem;
  padding: var(--v2-space-5);
  overflow: auto;
  background: var(--v2-color-canvas);
  border: 1px solid var(--v2-color-border);
  border-top: 0;
}
.document-canvas__page,
.document-canvas__preview-page {
  position: relative;
  box-sizing: border-box;
  margin: 0 auto;
  overflow: hidden;
  transform-origin: top center;
  background: white;
  box-shadow: var(--v2-shadow-md);
}
.document-canvas__page {
  touch-action: none;
}
.document-canvas__page.has-grid {
  background-image:
    linear-gradient(rgb(37 99 235 / 12%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(37 99 235 / 12%) 1px, transparent 1px),
    linear-gradient(rgb(37 99 235 / 7%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(37 99 235 / 7%) 1px, transparent 1px);
  background-size:
    25mm 25mm,
    25mm 25mm,
    5mm 5mm,
    5mm 5mm;
}
.document-canvas__preview-page iframe {
  width: 100%;
  height: 100%;
  background: white;
  border: 0;
}
.document-canvas__preview-state {
  display: grid;
  min-height: 16rem;
  margin: 0;
  color: var(--v2-color-text-muted);
  place-items: center;
}
.document-canvas__preview-state.is-error {
  color: var(--v2-color-danger-text);
}
.document-canvas__safe-area {
  position: absolute;
  inset: var(--margin-top) var(--margin-right) var(--margin-bottom) var(--margin-left);
  pointer-events: none;
  border: 1px dashed var(--v2-color-text-muted);
}
.document-canvas__selection-box {
  position: absolute;
  z-index: 999;
  box-sizing: border-box;
  pointer-events: none;
  background: var(--v2-color-primary-soft);
  border: 1px dashed var(--v2-color-primary);
}
.document-canvas__guide {
  position: absolute;
  z-index: 1000;
  pointer-events: none;
  background: var(--v2-color-danger);
}
.document-canvas__guide.is-vertical {
  top: 0;
  bottom: 0;
  width: 1px;
}
.document-canvas__guide.is-horizontal {
  right: 0;
  left: 0;
  height: 1px;
}
.document-canvas__element,
.document-canvas__table {
  position: absolute;
  box-sizing: border-box;
  overflow: visible;
  cursor: move;
  user-select: none;
  background: var(--v2-color-primary-soft);
  outline: 1px solid var(--v2-color-primary);
}
.document-canvas__element {
  display: grid;
  align-content: center;
  padding: 1mm;
}
.document-canvas__element code {
  overflow: hidden;
  font-size: 0.65em;
  color: var(--v2-color-text-secondary);
  text-overflow: ellipsis;
}
.document-canvas__element.is-divider {
  padding: 0;
  overflow: visible;
  background: transparent;
  border: 0;
}
.document-canvas__element.is-divider hr {
  width: 100%;
  margin: 0;
  border: 0;
  border-top: 0.3mm solid var(--v2-color-text);
}
.document-canvas__table-content {
  width: 100%;
  min-height: 100%;
  color: #000;
  font-family: sans-serif;
  font-size: 12pt;
  table-layout: fixed;
  border-collapse: collapse;
  background: white;
}
.document-canvas__table-content th,
.document-canvas__table-content td {
  box-sizing: content-box;
  padding: 1mm;
  border: 0.2mm solid #333;
}
.document-canvas__table-content code {
  font-family: inherit;
  font-size: inherit;
}
.document-canvas__element.is-selected,
.document-canvas__table.is-selected {
  outline: 2px solid var(--v2-color-primary);
}
.document-canvas__element.is-primary,
.document-canvas__table.is-primary {
  outline-style: double;
  outline-width: 3px;
}
.document-canvas__element.is-overflow,
.document-canvas__table.is-overflow {
  background: var(--v2-color-danger-soft);
  outline-color: var(--v2-color-danger);
}
.document-canvas__resize {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 10px;
  height: 10px;
  cursor: nwse-resize;
  background: var(--v2-color-primary);
  border: 0;
}
.document-canvas__warning {
  padding: var(--v2-space-2);
  color: var(--v2-color-danger-text);
  background: var(--v2-color-danger-soft);
}
.document-canvas__empty {
  color: var(--v2-color-text-muted);
}
.document-canvas__column-property {
  display: grid;
  gap: var(--v2-space-1);
  padding-top: var(--v2-space-2);
  border-top: 1px solid var(--v2-color-border);
}
.document-canvas__column-property > div {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-1);
}
@media (max-width: 1100px) {
  .document-canvas {
    grid-template-columns: 13rem minmax(28rem, 1fr);
  }
  .document-canvas__properties {
    grid-column: 1 / -1;
  }
}
@media (max-width: 760px) {
  .document-canvas {
    grid-template-columns: minmax(0, 1fr);
  }
  .document-canvas__properties {
    grid-column: auto;
  }
  .document-canvas__property-fields {
    grid-template-columns: 1fr;
  }
  .document-canvas__fields > section:not(.document-canvas__library) {
    grid-template-columns: 1fr;
  }
  .document-canvas__viewport {
    padding: var(--v2-space-2);
  }
}
</style>
