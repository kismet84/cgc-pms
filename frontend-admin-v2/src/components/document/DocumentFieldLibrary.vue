<script setup lang="ts">
import { computed, ref } from 'vue'
import { V2Input } from '@/components'
import type { DocumentCatalogField } from '@/services/system-management'
import type { ComponentPreset } from './documentCanvasPanels'

const props = defineProps<{
  fields: DocumentCatalogField[]
  disabled: boolean
}>()

const emit = defineEmits<{
  'add-component': [preset: ComponentPreset]
  'add-field': [field: DocumentCatalogField]
}>()

const search = ref('')
const componentPresets = [
  { key: 'TITLE', label: '标题', description: '居中大标题' },
  { key: 'TEXT', label: '文本', description: '普通说明文字' },
  { key: 'DIVIDER', label: '分割线', description: '横向分隔内容' },
  { key: 'TABLE', label: '表格', description: '业务明细表' },
  { key: 'HEADER', label: '页眉', description: '每页重复' },
  { key: 'FOOTER', label: '页脚', description: '每页重复' },
] as const

const firstCollectionField = computed(() => props.fields.find((field) => field.collectionPath))
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

function onFieldDrag(event: DragEvent, field: DocumentCatalogField): void {
  event.dataTransfer?.setData('application/x-document-field', field.path)
}
</script>

<template>
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
          @click="emit('add-component', preset.key)"
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
        @click="emit('add-field', field)"
        @dragstart="onFieldDrag($event, field)"
      >
        <span>{{ field.label }}</span>
      </button>
    </section>
  </aside>
</template>
