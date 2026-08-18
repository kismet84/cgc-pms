<script setup lang="ts">
import { computed, ref } from 'vue'
import type {
  DocumentCatalogField,
  DocumentDesignSchema,
  DocumentFlowSection,
} from '@/services/system-management'

const props = defineProps<{
  modelValue: DocumentDesignSchema
  fields: DocumentCatalogField[]
  previewHtml?: string
  previewLoading?: boolean
  previewError?: string
  disabled?: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: DocumentDesignSchema]
  'update:valid': [value: boolean]
}>()

const selectedId = ref(props.modelValue.sections?.[0]?.id ?? '')
const fieldSearch = ref('')
let sequence = 0
const sections = computed(() => props.modelValue.sections ?? [])
const selected = computed(() => sections.value.find((item) => item.id === selectedId.value))
const filteredFields = computed(() => {
  const keyword = fieldSearch.value.trim().toLowerCase()
  return props.fields.filter(
    (field) => !keyword || `${field.label} ${field.path}`.toLowerCase().includes(keyword),
  )
})
const scalarFields = computed(() => filteredFields.value.filter((field) => !field.collectionPath))
const collectionGroups = computed(() => {
  const groups = new Map<string, DocumentCatalogField[]>()
  filteredFields.value
    .filter((field) => field.collectionPath)
    .forEach((field) => {
      groups.set(field.collectionPath!, [...(groups.get(field.collectionPath!) ?? []), field])
    })
  return [...groups.entries()]
})

function commit(next: DocumentFlowSection[]): void {
  const value = { ...props.modelValue, layoutVersion: 2 as const, tables: [], sections: next }
  emit('update:modelValue', value)
  emit('update:valid', next.length > 0 && next.every(validSection))
}

function nextId(prefix: string): string {
  sequence += 1
  return `${prefix}-${Date.now().toString(36)}-${sequence}`
}

function addField(field: DocumentCatalogField): void {
  if (props.disabled) return
  if (field.collectionPath) {
    const existing = sections.value.find(
      (item): item is Extract<DocumentFlowSection, { type: 'COLLECTION_TABLE' }> =>
        item.type === 'COLLECTION_TABLE' && item.collectionPath === field.collectionPath,
    )
    if (existing) {
      if (!existing.columns.some((column) => column.fieldPath === field.path)) {
        updateSection(existing.id, {
          columns: [...existing.columns, { fieldPath: field.path, header: field.label }].slice(
            0,
            8,
          ),
        })
      }
      selectedId.value = existing.id
      return
    }
    const section: DocumentFlowSection = {
      id: nextId('table'),
      type: 'COLLECTION_TABLE',
      title: '业务明细',
      collectionPath: field.collectionPath,
      columns: [{ fieldPath: field.path, header: field.label }],
    }
    selectedId.value = section.id
    commit([...sections.value, section])
    return
  }
  const grid = sections.value.find(
    (item): item is Extract<DocumentFlowSection, { type: 'FIELD_GRID' }> =>
      item.type === 'FIELD_GRID',
  )
  if (grid) {
    if (!grid.cells.some((cell) => cell.fieldPath === field.path)) {
      updateSection(grid.id, {
        cells: [...grid.cells, { label: field.label, fieldPath: field.path }],
      })
    }
    selectedId.value = grid.id
    return
  }
  const section: DocumentFlowSection = {
    id: nextId('grid'),
    type: 'FIELD_GRID',
    title: '业务信息',
    columns: 2,
    cells: [{ label: field.label, fieldPath: field.path }],
  }
  selectedId.value = section.id
  commit([section, ...sections.value])
}

function addSection(type: DocumentFlowSection['type']): void {
  if (props.disabled) return
  const section: DocumentFlowSection =
    type === 'FIELD_GRID'
      ? {
          id: nextId('grid'),
          type,
          title: '信息表',
          columns: 2,
          cells: [{ label: '说明', text: '待填写' }],
        }
      : type === 'COLLECTION_TABLE'
        ? collectionSection()
        : type === 'NOTE'
          ? { id: nextId('note'), type, title: '说明', text: '请输入说明内容' }
          : {
              id: nextId('signature'),
              type,
              title: '签认栏',
              labels: ['编制', '复核', '审批', '日期'],
            }
  selectedId.value = section.id
  commit([...sections.value, section])
}

function collectionSection(): DocumentFlowSection {
  const [collectionPath, fields] = collectionGroups.value[0] ?? []
  if (!collectionPath || !fields?.length) {
    return { id: nextId('note'), type: 'NOTE', title: '提示', text: '当前业务没有集合字段。' }
  }
  return {
    id: nextId('table'),
    type: 'COLLECTION_TABLE',
    title: '业务明细',
    collectionPath,
    columns: fields.slice(0, 6).map((field) => ({ fieldPath: field.path, header: field.label })),
  }
}

function updateSection(id: string, patch: Record<string, unknown>): void {
  commit(
    sections.value.map((item) =>
      item.id === id ? ({ ...item, ...patch } as DocumentFlowSection) : item,
    ),
  )
}

function move(id: string, offset: number): void {
  const next = [...sections.value]
  const index = next.findIndex((item) => item.id === id)
  const target = index + offset
  if (index < 0 || target < 0 || target >= next.length) return
  ;[next[index], next[target]] = [next[target]!, next[index]!]
  commit(next)
}

function remove(id: string): void {
  const next = sections.value.filter((item) => item.id !== id)
  selectedId.value = next[0]?.id ?? ''
  commit(next)
}

function validSection(section: DocumentFlowSection): boolean {
  if (!section.id || !section.type) return false
  if (section.type === 'FIELD_GRID') return section.cells.length > 0
  if (section.type === 'COLLECTION_TABLE')
    return Boolean(section.collectionPath && section.columns.length)
  if (section.type === 'NOTE') return Boolean(section.fieldPath || section.text)
  return section.labels.length >= 2
}
</script>

<template>
  <div class="flow-designer">
    <aside class="flow-designer__library">
      <div class="panel-tabs"><strong>组件库</strong><span>字段库</span></div>
      <div class="component-grid">
        <button type="button" @click="addSection('FIELD_GRID')">信息表</button>
        <button type="button" @click="addSection('COLLECTION_TABLE')">动态明细</button>
        <button type="button" @click="addSection('NOTE')">说明</button>
        <button type="button" @click="addSection('SIGNATURE_GRID')">手签栏</button>
      </div>
      <input v-model="fieldSearch" class="field-search" placeholder="搜索字段或路径" />
      <div class="field-list">
        <button
          v-for="field in scalarFields"
          :key="field.path"
          type="button"
          @click="addField(field)"
        >
          <span>{{ field.label }}</span
          ><small>{{ field.path }}</small>
        </button>
        <template v-for="[group, fields] in collectionGroups" :key="group">
          <p class="field-group">{{ group }} · 集合</p>
          <button v-for="field in fields" :key="field.path" type="button" @click="addField(field)">
            <span>{{ field.label }}</span
            ><small>{{ field.path }}</small>
          </button>
        </template>
      </div>
    </aside>

    <section class="flow-designer__stage" aria-label="设计画布">
      <div class="stage-toolbar">
        <span>A4 {{ modelValue.page.orientation === 'LANDSCAPE' ? '横向' : '纵向' }}</span>
        <span>{{ sections.length }} 个流式区块</span>
      </div>
      <div
        class="paper"
        :class="{ 'paper--landscape': modelValue.page.orientation === 'LANDSCAPE' }"
      >
        <iframe v-if="previewHtml" title="单据实时预览" :srcdoc="previewHtml" />
        <div v-else class="paper-outline">
          <h1>业务单据</h1>
          <button
            v-for="section in sections"
            :key="section.id"
            type="button"
            class="section-outline"
            :class="{ 'section-outline--selected': section.id === selectedId }"
            @click="selectedId = section.id"
          >
            <strong>{{ section.title || section.type }}</strong>
            <span v-if="section.type === 'FIELD_GRID'">{{
              section.cells.map((cell) => cell.label).join(' · ')
            }}</span>
            <span v-else-if="section.type === 'COLLECTION_TABLE'">{{
              section.columns.map((column) => column.header).join(' ｜ ')
            }}</span>
            <span v-else-if="section.type === 'SIGNATURE_GRID'">{{
              section.labels.join(' ｜ ')
            }}</span>
            <span v-else>{{ section.fieldPath || section.text }}</span>
          </button>
        </div>
      </div>
      <div v-if="previewLoading" class="stage-state">正在生成服务端预览…</div>
      <div v-else-if="previewError" class="stage-state stage-state--error">{{ previewError }}</div>
    </section>

    <aside class="flow-designer__properties">
      <div class="panel-tabs"><strong>属性</strong><span>排版</span></div>
      <template v-if="selected">
        <label>区块类型<input :value="selected.type" disabled /></label>
        <label
          >区块标题<input
            :value="selected.title"
            @input="
              updateSection(selected.id, { title: ($event.target as HTMLInputElement).value })
            "
        /></label>
        <label v-if="selected.type === 'FIELD_GRID'"
          >列数
          <select
            :value="selected.columns"
            @change="
              updateSection(selected.id, {
                columns: Number(($event.target as HTMLSelectElement).value),
              })
            "
          >
            <option :value="1">1 列</option>
            <option :value="2">2 列</option>
            <option :value="3">3 列</option>
          </select>
        </label>
        <label v-if="selected.type === 'NOTE' && !selected.fieldPath"
          >说明文字
          <textarea
            :value="selected.text"
            @input="
              updateSection(selected.id, { text: ($event.target as HTMLTextAreaElement).value })
            "
          />
        </label>
        <label v-if="selected.type === 'SIGNATURE_GRID'"
          >签认项
          <input
            :value="selected.labels.join('、')"
            @input="
              updateSection(selected.id, {
                labels: ($event.target as HTMLInputElement).value.split('、').filter(Boolean),
              })
            "
          />
        </label>
        <div class="property-actions">
          <button type="button" @click="move(selected.id, -1)">上移</button>
          <button type="button" @click="move(selected.id, 1)">下移</button>
          <button type="button" class="danger" @click="remove(selected.id)">删除</button>
        </div>
      </template>
      <p v-else class="empty-tip">选择画布区块后编辑属性。</p>
    </aside>
  </div>
</template>

<style scoped>
.flow-designer {
  display: grid;
  grid-template-columns: 240px minmax(520px, 1fr) 250px;
  height: calc(100vh - 190px);
  min-height: 620px;
  border: 1px solid var(--v2-color-border);
  background: var(--v2-color-canvas);
  overflow: hidden;
}
.flow-designer > aside {
  background: var(--v2-color-surface);
  min-width: 0;
  overflow: auto;
}
.flow-designer__library {
  border-right: 1px solid var(--v2-color-border);
}
.flow-designer__properties {
  border-left: 1px solid var(--v2-color-border);
  padding-bottom: 20px;
}
.panel-tabs {
  display: flex;
  gap: 22px;
  align-items: center;
  height: 48px;
  padding: 0 16px;
  border-bottom: 1px solid var(--v2-color-border-subtle);
  color: var(--v2-color-text-secondary);
}
.panel-tabs strong {
  color: var(--v2-color-primary);
}
.component-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 14px;
}
.component-grid button,
.field-list button,
.property-actions button {
  border: 1px solid var(--v2-color-border);
  background: var(--v2-color-surface);
  border-radius: 4px;
  color: var(--v2-color-text);
  cursor: pointer;
}
.component-grid button {
  height: 54px;
}
.field-search {
  box-sizing: border-box;
  width: calc(100% - 28px);
  height: 34px;
  margin: 0 14px 10px;
  padding: 0 10px;
  border: 1px solid var(--v2-color-border);
  border-radius: 4px;
}
.field-list {
  padding: 0 14px 20px;
}
.field-list button {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: 8px;
  margin-bottom: 6px;
  text-align: left;
}
.field-list small {
  color: var(--v2-color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
}
.field-group {
  margin: 14px 0 8px;
  color: var(--v2-color-text-secondary);
  font-size: 12px;
}
.flow-designer__stage {
  position: relative;
  min-width: 0;
  overflow: auto;
  padding: 48px 34px 34px;
}
.stage-toolbar {
  position: absolute;
  inset: 0 0 auto;
  height: 42px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 18px;
  background: var(--v2-color-surface);
  border-bottom: 1px solid var(--v2-color-border);
  color: var(--v2-color-text-secondary);
  font-size: 13px;
}
.paper {
  width: 640px;
  min-height: 905px;
  margin: auto;
  background: var(--v2-color-surface);
  box-shadow: var(--v2-shadow-float);
}
.paper--landscape {
  width: 900px;
  min-height: 636px;
}
.paper iframe {
  width: 100%;
  height: 905px;
  border: 0;
}
.paper--landscape iframe {
  height: 636px;
}
.paper-outline {
  padding: 44px;
}
.paper-outline h1 {
  text-align: center;
  font-size: 24px;
  margin: 0 0 30px;
}
.section-outline {
  display: block;
  width: 100%;
  min-height: 58px;
  margin: 0 0 14px;
  padding: 12px;
  border: 1px solid var(--v2-color-text-secondary);
  background: var(--v2-color-surface);
  text-align: left;
}
.section-outline strong,
.section-outline span {
  display: block;
}
.section-outline span {
  margin-top: 8px;
  color: var(--v2-color-text-secondary);
  font-size: 12px;
}
.section-outline--selected {
  outline: 2px solid var(--v2-color-primary);
  outline-offset: 2px;
}
.stage-state {
  position: sticky;
  left: 50%;
  bottom: 16px;
  width: max-content;
  max-width: 80%;
  transform: translateX(-50%);
  padding: 8px 14px;
  background: var(--v2-color-text);
  color: var(--v2-color-surface);
  border-radius: 4px;
}
.stage-state--error {
  background: var(--v2-color-danger);
}
.flow-designer__properties label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px 0;
  color: var(--v2-color-text-secondary);
  font-size: 13px;
}
.flow-designer__properties input,
.flow-designer__properties select,
.flow-designer__properties textarea {
  box-sizing: border-box;
  width: 100%;
  padding: 8px;
  border: 1px solid var(--v2-color-border);
  border-radius: 4px;
  background: var(--v2-color-surface);
}
.flow-designer__properties textarea {
  min-height: 110px;
}
.property-actions {
  display: flex;
  gap: 8px;
  padding: 18px 16px;
}
.property-actions button {
  padding: 7px 10px;
}
.property-actions .danger {
  color: var(--v2-color-danger);
}
.empty-tip {
  padding: 20px;
  color: var(--v2-color-text-muted);
}
@media (max-width: 1200px) {
  .flow-designer {
    grid-template-columns: 210px minmax(480px, 1fr) 220px;
  }
}
</style>
