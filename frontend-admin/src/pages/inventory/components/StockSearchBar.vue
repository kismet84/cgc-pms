<script setup lang="ts">
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import type { WarehouseVO } from '@/types/inventory'
import type { SelectOption } from '@/types/ui'

defineProps<{
  keyword: string
  warehouseId?: string
  materialId?: string
  warehouseList: WarehouseVO[]
  materialList: { id: string; materialName: string; materialCode?: string }[]
}>()

const emit = defineEmits<{
  'update:keyword': [value: string]
  'update:warehouseId': [value: string | undefined]
  'update:materialId': [value: string | undefined]
  search: []
  reset: []
}>()
</script>

<template>
  <div class="lg-search-bar">
    <a-input
      :value="keyword"
      placeholder="搜索流水编号、来源单号…"
      allow-clear
      size="large"
      @update:value="(v: string) => emit('update:keyword', v)"
      @press-enter="emit('search')"
    >
      <template #prefix><SearchOutlined style="color: #697380" /></template>
    </a-input>
    <a-select
      :value="warehouseId"
      placeholder="请选择仓库"
      allow-clear
      size="large"
      style="min-width: 180px"
      show-search
      :filter-option="
        (input: string, option: SelectOption) =>
          option.label?.toLowerCase().includes(input.toLowerCase())
      "
      @update:value="(v: string | undefined) => emit('update:warehouseId', v)"
    >
      <a-select-option v-for="w in warehouseList" :key="w.id" :value="w.id">
        {{ w.warehouseName }}
      </a-select-option>
    </a-select>
    <a-select
      :value="materialId"
      placeholder="选择物料"
      allow-clear
      size="large"
      style="min-width: 220px"
      show-search
      :filter-option="
        (input: string, option: SelectOption) =>
          option.label?.toLowerCase().includes(input.toLowerCase())
      "
      @update:value="(v: string | undefined) => emit('update:materialId', v)"
    >
      <a-select-option v-for="m in materialList" :key="m.id" :value="m.id">
        {{ m.materialName }} <span style="color: #9ca3af">({{ m.materialCode }})</span>
      </a-select-option>
    </a-select>
    <a-button type="primary" size="large" @click="emit('search')">查询</a-button>
    <a-button size="large" @click="emit('reset')">
      <template #icon><ReloadOutlined /></template>
      重置
    </a-button>
  </div>
</template>
