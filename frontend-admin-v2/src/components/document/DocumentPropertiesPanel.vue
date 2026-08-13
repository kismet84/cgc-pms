<script setup lang="ts">
import { computed } from 'vue'
import { V2ActionMenu, V2Button, V2Input } from '@/components'
import type { DocumentCanvasElement, DocumentDesignSchema } from '@/services/system-management'
import {
  canApplyCanvasLayout,
  type AlignmentReference,
  type CanvasItem,
  type LayoutAction,
} from './documentCanvasEngine'
import type { DocumentCanvasControls, DocumentPropertiesCommand } from './documentCanvasPanels'

const props = defineProps<{
  modelValue: DocumentDesignSchema
  controls: DocumentCanvasControls
  selectedId: string
  selectedIds: string[]
  previewHtml: string
  previewLoading: boolean
  previewBusinessId: string
}>()

const emit = defineEmits<{
  command: [command: DocumentPropertiesCommand]
  'update:previewBusinessId': [value: string]
}>()

const zoom = computed({
  get: () => props.controls.zoom,
  set: (value: string) => (props.controls.zoom = value),
})
const viewMode = computed({
  get: () => props.controls.viewMode,
  set: (value) => (props.controls.viewMode = value),
})
const gridVisible = computed({
  get: () => props.controls.gridVisible,
  set: (value: boolean) => (props.controls.gridVisible = value),
})
const spacingMm = computed({
  get: () => props.controls.spacingMm,
  set: (value: string) => (props.controls.spacingMm = value),
})
const alignmentReference = computed({
  get: () => props.controls.alignmentReference,
  set: (value: AlignmentReference) => (props.controls.alignmentReference = value),
})
const snapToGrid = computed({
  get: () => props.controls.snapToGrid,
  set: (value: boolean) => (props.controls.snapToGrid = value),
})
const smartGuides = computed({
  get: () => props.controls.smartGuides,
  set: (value: boolean) => (props.controls.smartGuides = value),
})
const selectedElement = computed(
  () => props.modelValue.elements.find((item) => item.id === props.selectedId) ?? null,
)
const selectedTable = computed(
  () => props.modelValue.tables.find((item) => item.id === props.selectedId) ?? null,
)
const selectedItems = computed<CanvasItem[]>(() =>
  [...props.modelValue.elements, ...props.modelValue.tables].filter((item) =>
    props.selectedIds.includes(item.id),
  ),
)
const alignmentReferences = [
  ['SELECTION', '选区'],
  ['CANVAS', '画布'],
  ['KEY', '主组件'],
] as const
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

function canApplyLayout(action: LayoutAction): boolean {
  return canApplyCanvasLayout(
    action,
    selectedItems.value,
    props.modelValue.tables,
    alignmentReference.value,
  )
}

function toggleOrientation(): void {
  emit('command', { type: 'toggle-orientation' })
}

function updateMargin(value: string): void {
  emit('command', { type: 'update-margin', value })
}

function applyLayout(action: LayoutAction): void {
  emit('command', { type: 'apply-layout', action })
}

function updateSelected(key: keyof DocumentCanvasElement, value: string): void {
  emit('command', { type: 'update-selected', key, value })
}

function updateTableColumn(index: number, patch: { header?: string; widthMm?: number }): void {
  emit('command', { type: 'update-table-column', index, patch })
}

function moveTableColumn(index: number, offset: -1 | 1): void {
  emit('command', { type: 'move-table-column', index, offset })
}

function removeTableColumn(index: number): void {
  emit('command', { type: 'remove-table-column', index })
}

function removeSelected(): void {
  emit('command', { type: 'remove-selected' })
}
</script>

<template>
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
</template>
