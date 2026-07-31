<script setup lang="ts">
import { ref } from 'vue'
import { FilterOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import type { WarehouseVO } from '@/types/inventory'
import type { SelectOption } from '@/types/ui'

defineProps<{
  keyword: string
  projectId?: string
  warehouseId?: string
  materialId?: string
  projectList: { id: string; projectName: string }[]
  warehouseList: WarehouseVO[]
  materialList: { id: string; materialName: string; materialCode?: string }[]
}>()

const emit = defineEmits<{
  'update:keyword': [value: string]
  'update:projectId': [value: string | undefined]
  'update:warehouseId': [value: string | undefined]
  'update:materialId': [value: string | undefined]
  search: []
  reset: []
}>()

const filterPanelOpen = ref(false)
</script>

<template>
  <div class="lg-search-bar procurement-subcontract-query-panel">
    <div
      class="stock-search-fields procurement-subcontract-filter-panel"
      :class="{ 'is-open': filterPanelOpen }"
    >
      <a-select
        :value="projectId"
        placeholder="全部项目"
        allow-clear
        size="large"
        show-search
        :filter-option="
          (input: string, option: SelectOption) =>
            option.label?.toLowerCase().includes(input.toLowerCase())
        "
        @update:value="(v: string | undefined) => emit('update:projectId', v)"
      >
        <a-select-option v-for="p in projectList" :key="p.id" :value="p.id" :label="p.projectName">
          {{ p.projectName }}
        </a-select-option>
      </a-select>
      <a-select
        :value="warehouseId"
        placeholder="全部仓库"
        allow-clear
        size="large"
        show-search
        :filter-option="
          (input: string, option: SelectOption) =>
            option.label?.toLowerCase().includes(input.toLowerCase())
        "
        @update:value="(v: string | undefined) => emit('update:warehouseId', v)"
      >
        <a-select-option
          v-for="w in warehouseList"
          :key="w.id"
          :value="w.id"
          :label="w.warehouseName"
        >
          {{ w.warehouseName }}
        </a-select-option>
      </a-select>
      <a-select
        :value="materialId"
        placeholder="全部物料"
        allow-clear
        size="large"
        show-search
        :filter-option="
          (input: string, option: SelectOption) =>
            option.label?.toLowerCase().includes(input.toLowerCase())
        "
        @update:value="(v: string | undefined) => emit('update:materialId', v)"
      >
        <a-select-option
          v-for="m in materialList"
          :key="m.id"
          :value="m.id"
          :label="`${m.materialName} ${m.materialCode ?? ''}`"
        >
          {{ m.materialName }} <span style="color: var(--muted)">({{ m.materialCode }})</span>
        </a-select-option>
      </a-select>
    </div>
    <div class="stock-search-keyword-row procurement-subcontract-query-row">
      <a-input
        :value="keyword"
        placeholder="搜索流水编号、来源单号"
        allow-clear
        size="large"
        @update:value="(v: string) => emit('update:keyword', v)"
        @press-enter="emit('search')"
      >
        <template #prefix><SearchOutlined style="color: var(--text-secondary)" /></template>
      </a-input>
      <div class="stock-search-actions procurement-subcontract-query-actions">
        <a-button
          class="procurement-subcontract-desktop-action"
          type="primary"
          size="large"
          @click="emit('search')"
          >搜索</a-button
        >
        <a-button
          class="procurement-subcontract-desktop-action"
          size="large"
          @click="emit('reset')"
        >
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
        <a-button
          class="procurement-subcontract-filter-toggle"
          size="large"
          :aria-expanded="filterPanelOpen"
          @click="filterPanelOpen = !filterPanelOpen"
        >
          <template #icon><FilterOutlined /></template>
          筛选
        </a-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.lg-search-bar {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
  gap: 12px;
  margin: 0;
  height: auto;
}

.stock-search-fields,
.stock-search-keyword-row,
.stock-search-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  min-width: 0;
}

.stock-search-fields,
.stock-search-keyword-row {
  width: 100%;
}

.stock-search-keyword-row > :deep(.ant-input-affix-wrapper) {
  min-width: 320px;
  flex: 1 1 320px;
}

.stock-search-fields > :deep(.ant-select) {
  min-width: 180px;
  flex: 1 1 180px;
}

.stock-search-actions {
  flex: 0 0 auto;
  flex-wrap: wrap;
  margin-left: auto;
  min-width: 0;
}

@media (max-width: 900px) {
  .lg-search-bar,
  .stock-search-fields,
  .stock-search-keyword-row,
  .stock-search-actions {
    display: flex;
    align-items: stretch;
    flex-direction: column;
  }

  .stock-search-actions {
    width: 100%;
    margin-left: 0;
  }

  .stock-search-actions :deep(.ant-btn) {
    flex: 1;
  }
}
</style>
