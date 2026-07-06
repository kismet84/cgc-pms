<script setup lang="ts">
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
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
</script>

<template>
  <div class="lg-search-bar">
    <div class="stock-search-fields">
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
        <a-select-option
          v-for="p in projectList"
          :key="p.id"
          :value="p.id"
          :label="p.projectName"
        >
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
    <div class="stock-search-keyword-row">
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
      <div class="stock-search-actions">
        <a-button type="primary" size="large" @click="emit('search')">查询</a-button>
        <a-button size="large" @click="emit('reset')">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.lg-search-bar {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
  padding: 16px;
  border-left: 4px solid var(--primary-soft);
}

.stock-search-fields,
.stock-search-keyword-row,
.stock-search-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.stock-search-keyword-row > :deep(.ant-input-affix-wrapper) {
  min-width: 260px;
  flex: 1 1 360px;
}

.stock-search-fields > :deep(.ant-select) {
  min-width: 180px;
  flex: 1 1 200px;
}

.stock-search-actions {
  flex: 0 0 auto;
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
}
</style>
