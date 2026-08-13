<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type {
  DocumentCanvasElement,
  DocumentCanvasTable,
  DocumentCatalogField,
  DocumentDesignSchema,
  DocumentPageOrientation,
} from '@/services/system-management'
import {
  applyCanvasLayout,
  canApplyCanvasLayout,
  flowLayoutConflict,
  overflowItemIds,
  pageSizeFor,
  roundMm as round,
  safeCanvasBounds,
  scaleTableColumns,
  snapCanvasMove,
  validDocumentDesignSchema,
  type CanvasItem,
  type LayoutAction,
} from './documentCanvasEngine'
import DocumentFieldLibrary from './DocumentFieldLibrary.vue'
import DocumentPropertiesPanel from './DocumentPropertiesPanel.vue'
import type {
  ComponentPreset,
  DocumentCanvasControls,
  DocumentPropertiesCommand,
} from './documentCanvasPanels'

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

const selectedId = ref('')
const selectedIds = ref<string[]>([])
const controls = reactive<DocumentCanvasControls>({
  zoom: '75',
  viewMode: 'DESIGN',
  gridVisible: true,
  snapToGrid: false,
  smartGuides: true,
  alignmentReference: 'SELECTION',
  spacingMm: '5',
})
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

const pageSize = computed(() => pageSizeFor(props.modelValue.page.orientation))
const scale = computed(() => Math.max(0.4, Math.min(1.25, Number(controls.zoom) / 100 || 0.75)))
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
const overflowIds = computed(() => overflowItemIds(props.modelValue))
const layoutConflict = computed(() => flowLayoutConflict(props.modelValue))

function commit(patch: Partial<DocumentDesignSchema>): void {
  const value = { ...props.modelValue, ...patch }
  emit('update:modelValue', value)
  emit('update:valid', validDocumentDesignSchema(value))
}

watch(
  () => props.modelValue,
  (value) => emit('update:valid', validDocumentDesignSchema(value)),
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
    const firstCollectionField = props.fields.find((field) => field.collectionPath)
    if (firstCollectionField) addTableColumn(firstCollectionField)
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

function canApplyLayout(action: LayoutAction): boolean {
  return canApplyCanvasLayout(
    action,
    selectedItems.value,
    props.modelValue.tables,
    controls.alignmentReference,
  )
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

function applyLayout(action: LayoutAction): void {
  if (!canApplyLayout(action)) return
  const gap = Math.max(0, Math.min(50, Number(controls.spacingMm) || 0))
  commitItems(
    applyCanvasLayout({
      action,
      items: selectedItems.value,
      primaryId: selectedId.value,
      alignmentReference: controls.alignmentReference,
      canvasBounds: safeCanvasBounds(props.modelValue),
      gap,
    }),
  )
}

function snapMove(
  dx: number,
  dy: number,
  moving: CanvasItem[],
  pxPerMm: number,
): { dx: number; dy: number } {
  const movingIds = new Set(moving.map((item) => item.id))
  const result = snapCanvasMove({
    dx,
    dy,
    moving,
    references: [
      safeCanvasBounds(props.modelValue),
      ...canvasItems.value.filter((item) => !movingIds.has(item.id)),
    ],
    pxPerMm,
    snapToGrid: controls.snapToGrid,
    smartGuides: controls.smartGuides,
  })
  guideLines.value = result.guideLines
  return result
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
  const itemPatch = controls.snapToGrid
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

function handlePropertiesCommand(command: DocumentPropertiesCommand): void {
  switch (command.type) {
    case 'toggle-orientation':
      toggleOrientation()
      return
    case 'update-margin':
      updateMargin(command.value)
      return
    case 'apply-layout':
      applyLayout(command.action)
      return
    case 'update-selected':
      updateSelected(command.key, command.value)
      return
    case 'update-table-column':
      updateTableColumn(command.index, command.patch)
      return
    case 'move-table-column':
      moveTableColumn(command.index, command.offset)
      return
    case 'remove-table-column':
      removeTableColumn(command.index)
      return
    case 'remove-selected':
      removeSelected()
  }
}

function nextId(prefix: string): string {
  sequence += 1
  return `${prefix}-${Date.now().toString(36)}-${sequence}`
}
</script>

<template>
  <div class="document-canvas">
    <DocumentFieldLibrary
      :fields="fields"
      :disabled="disabled"
      @add-component="addComponent"
      @add-field="addField"
    />

    <section class="document-canvas__workspace" aria-label="A4 设计画布">
      <div class="document-canvas__status" aria-live="polite">
        <span>{{ controls.viewMode === 'DESIGN' ? '设计模式' : 'HTML 预览' }}</span>
        <span>{{ pageSize.width }} × {{ pageSize.height }} mm</span>
        <span>{{ controls.zoom }}%</span>
        <span v-if="selectedIds.length">已选 {{ selectedIds.length }} 个</span>
      </div>
      <p v-if="overflowIds.length" class="document-canvas__warning" role="alert">
        {{ overflowIds.length }} 个元素越出页面安全区域，保存已阻止。
      </p>
      <p v-else-if="layoutConflict" class="document-canvas__warning" role="alert">
        {{ layoutConflict }}，保存已阻止。
      </p>
      <div
        class="document-canvas__viewport"
        :class="{ 'is-preview': controls.viewMode === 'PREVIEW' }"
      >
        <div
          v-if="controls.viewMode === 'DESIGN'"
          class="document-canvas__page"
          :class="{ 'has-grid': controls.gridVisible }"
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

    <DocumentPropertiesPanel
      :model-value="modelValue"
      :controls="controls"
      :selected-id="selectedId"
      :selected-ids="selectedIds"
      :preview-html="previewHtml"
      :preview-loading="previewLoading"
      :preview-business-id="previewBusinessId"
      @command="handlePropertiesCommand"
      @update:preview-business-id="emit('update:previewBusinessId', $event)"
    />
  </div>
</template>

<style src="./document-canvas.css"></style>
