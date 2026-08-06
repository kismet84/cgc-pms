<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { V2Button, V2Input } from '@/components'
import type {
  DocumentCanvasElement,
  DocumentCanvasTable,
  DocumentCatalogField,
  DocumentDesignSchema,
  DocumentPageOrientation,
} from '@/services/system-management'

const props = defineProps<{
  modelValue: DocumentDesignSchema
  fields: DocumentCatalogField[]
  disabled?: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: DocumentDesignSchema]
  'update:valid': [value: boolean]
}>()

const search = ref('')
const zoom = ref('75')
const selectedId = ref('')
let sequence = 0
let interaction:
  | {
      id: string
      kind: 'move' | 'resize'
      startX: number
      startY: number
      initial: { xMm: number; yMm: number; widthMm: number; heightMm?: number }
      pxPerMm: number
    }
  | undefined

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
    )
  )
}

watch(
  () => props.modelValue,
  (value) => emit('update:valid', valid(value)),
  { deep: true, immediate: true },
)

function changeOrientation(orientation: DocumentPageOrientation): void {
  commit({ page: { ...props.modelValue.page, orientation } })
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

function addField(field: DocumentCatalogField, position?: { xMm: number; yMm: number }): void {
  if (props.disabled) return
  if (field.collectionPath) {
    addTableColumn(field, position)
    return
  }
  const existing = props.modelValue.elements.find((item) => item.fieldPath === field.path)
  if (existing) {
    selectedId.value = existing.id
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
  selectedId.value = element.id
  commit({
    elements: [...props.modelValue.elements, element],
  })
}

function addText(): void {
  const element: DocumentCanvasElement = {
    id: nextId('text'),
    type: 'TEXT',
    text: '单据标题',
    xMm: props.modelValue.page.marginMm.left,
    yMm: props.modelValue.page.marginMm.top,
    widthMm: 80,
    heightMm: 14,
    fontSizePt: 16,
    align: 'CENTER',
    repeat: 'BODY',
    zIndex: props.modelValue.elements.length,
  }
  selectedId.value = element.id
  commit({ elements: [...props.modelValue.elements, element] })
}

function addTableColumn(
  field: DocumentCatalogField,
  position?: { xMm: number; yMm: number },
): void {
  const collectionPath = field.collectionPath!
  const table = props.modelValue.tables.find((item) => item.collectionPath === collectionPath)
  if (table?.columns.some((column) => column.fieldPath === field.path)) {
    selectedId.value = table.id
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
  selectedId.value = next.id
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
    pxPerMm: page.getBoundingClientRect().width / pageSize.value.width,
  }
  selectedId.value = item.id
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function moveInteraction(event: PointerEvent): void {
  if (!interaction) return
  const dx = (event.clientX - interaction.startX) / interaction.pxPerMm
  const dy = (event.clientY - interaction.startY) / interaction.pxPerMm
  const itemPatch =
    interaction.kind === 'move'
      ? { xMm: round(interaction.initial.xMm + dx), yMm: round(interaction.initial.yMm + dy) }
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
  if (interaction?.kind !== 'resize') return table
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

function stopInteraction(): void {
  interaction = undefined
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
  commit({
    elements: props.modelValue.elements.filter((item) => item.id !== selectedId.value),
    tables: props.modelValue.tables.filter((item) => item.id !== selectedId.value),
  })
  selectedId.value = ''
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
      <V2Input v-model="search" type="search" label="搜索字段" placeholder="名称或路径" />
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
          :title="field.path"
          @click="addField(field)"
          @dragstart="onFieldDrag($event, field)"
        >
          <span>{{ field.label }}</span>
          <code>{{ field.path }}</code>
        </button>
      </section>
    </aside>

    <section class="document-canvas__workspace" aria-label="A4 设计画布">
      <div class="document-canvas__toolbar">
        <span>方向</span>
        <V2Button
          size="small"
          :variant="modelValue.page.orientation === 'PORTRAIT' ? 'primary' : 'secondary'"
          @click="changeOrientation('PORTRAIT')"
          >纵向</V2Button
        >
        <V2Button
          size="small"
          :variant="modelValue.page.orientation === 'LANDSCAPE' ? 'primary' : 'secondary'"
          @click="changeOrientation('LANDSCAPE')"
          >横向</V2Button
        >
        <V2Button size="small" variant="secondary" :disabled="disabled" @click="addText">
          添加文本
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
      </div>
      <p v-if="overflowIds.length" class="document-canvas__warning" role="alert">
        {{ overflowIds.length }} 个元素越出页面安全区域，保存已阻止。
      </p>
      <div class="document-canvas__viewport">
        <div
          class="document-canvas__page"
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
        >
          <div class="document-canvas__safe-area" aria-hidden="true"></div>
          <div
            v-for="element in modelValue.elements"
            :key="element.id"
            class="document-canvas__element"
            :class="{
              'is-selected': element.id === selectedId,
              'is-overflow': overflowIds.includes(element.id),
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
            @focus="selectedId = element.id"
          >
            <span>{{ element.text }}</span
            ><code v-if="element.fieldPath" v-text="'{{' + element.fieldPath + '}}'"></code>
            <button
              type="button"
              class="document-canvas__resize"
              aria-label="调整元素尺寸"
              @pointerdown.stop="startInteraction($event, element, 'resize')"
              @pointermove.stop="moveInteraction"
              @pointerup.stop="stopInteraction"
            ></button>
          </div>
          <div
            v-for="table in modelValue.tables"
            :key="table.id"
            class="document-canvas__table"
            :class="{
              'is-selected': table.id === selectedId,
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
            @focus="selectedId = table.id"
          >
            <strong>{{ table.collectionPath }} 明细表</strong>
            <div class="document-canvas__table-columns">
              <span v-for="column in table.columns" :key="column.fieldPath">{{
                column.header
              }}</span>
            </div>
            <button
              type="button"
              class="document-canvas__resize"
              aria-label="调整明细表尺寸"
              @pointerdown.stop="startInteraction($event, table, 'resize')"
              @pointermove.stop="moveInteraction"
              @pointerup.stop="stopInteraction"
            ></button>
          </div>
        </div>
      </div>
    </section>

    <aside class="document-canvas__properties" aria-label="元素属性">
      <h3>属性</h3>
      <template v-if="selectedElement">
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
      <template v-else-if="selectedTable">
        <p>{{ selectedTable.collectionPath }} 明细列</p>
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
      <p v-else>选择画布元素后编辑属性</p>
      <V2Button v-if="selectedId" size="small" variant="danger" @click="removeSelected"
        >删除所选</V2Button
      >
    </aside>
  </div>
</template>

<style scoped>
.document-canvas {
  display: grid;
  grid-template-columns: 15rem minmax(32rem, 1fr) 13rem;
  gap: var(--v2-space-3);
  min-height: 38rem;
}
.document-canvas__fields,
.document-canvas__properties {
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
.document-canvas__field code {
  overflow: hidden;
  color: var(--v2-color-text-muted);
  font-size: 0.7rem;
  text-overflow: ellipsis;
}
.document-canvas__workspace {
  min-width: 0;
}
.document-canvas__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: var(--v2-space-2);
  margin-bottom: var(--v2-space-2);
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
  min-height: 2rem;
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-sm);
}
.document-canvas__viewport {
  min-height: 34rem;
  padding: var(--v2-space-5);
  overflow: auto;
  background: var(--v2-color-surface-subtle);
}
.document-canvas__page {
  position: relative;
  box-sizing: border-box;
  margin: 0 auto;
  overflow: hidden;
  touch-action: none;
  transform-origin: top center;
  background: white;
  box-shadow: var(--v2-shadow-md);
}
.document-canvas__safe-area {
  position: absolute;
  inset: var(--margin-top) var(--margin-right) var(--margin-bottom) var(--margin-left);
  pointer-events: none;
  border: 1px dashed var(--v2-color-text-muted);
}
.document-canvas__element,
.document-canvas__table {
  position: absolute;
  box-sizing: border-box;
  overflow: hidden;
  cursor: move;
  user-select: none;
  background: var(--v2-color-primary-soft);
  border: 1px solid var(--v2-color-primary);
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
.document-canvas__table strong {
  display: block;
  padding: 1mm;
  font-size: 10px;
}
.document-canvas__table-columns {
  display: flex;
  height: calc(100% - 6mm);
}
.document-canvas__table-columns span {
  flex: 1;
  padding: 1mm;
  font-size: 9px;
  border: 1px solid var(--v2-color-border);
}
.document-canvas__element.is-selected,
.document-canvas__table.is-selected {
  outline: 2px solid var(--v2-color-primary);
}
.document-canvas__element.is-overflow,
.document-canvas__table.is-overflow {
  background: var(--v2-color-danger-soft);
  border-color: var(--v2-color-danger);
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
  .document-canvas__viewport {
    padding: var(--v2-space-2);
  }
}
</style>
