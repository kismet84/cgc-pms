<script setup lang="ts">
import type { OrgCompanyVO } from '@/types/org'
import { PlusOutlined } from '@ant-design/icons-vue'

defineProps<{
  canAdd: boolean
  canEdit: boolean
  canDelete: boolean
  loading: boolean
  data: OrgCompanyVO[]
  total: number
  pageNo: number
  pageSize: number
  filter: {
    companyCode: string
    companyName: string
    status: string | undefined
  }
  gridColumns: Record<string, unknown>[]
}>()

const emit = defineEmits<{
  search: []
  reset: []
  'update:pageNo': [value: number]
  'update:pageSize': [value: number]
  pageChange: [page: number]
  pageSizeChange: [cur: number, size: number]
  rowClick: [record: OrgCompanyVO]
  add: []
  edit: [record: OrgCompanyVO]
  delete: [record: OrgCompanyVO]
}>()
</script>

<template>
  <section class="org-panel org-company-panel">
    <div class="org-panel-header">
      <div>
        <span class="org-panel-title">公司管理</span>
        <p>点击公司行后，右侧部门架构自动聚焦。</p>
      </div>
      <a-button v-if="canAdd" type="primary" size="small" @click="emit('add')">
        <template #icon><PlusOutlined /></template>
        新增
      </a-button>
    </div>

    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.companyCode"
        placeholder="公司编号"
        size="small"
        allow-clear
        @press-enter="emit('search')"
      />
      <a-input
        v-model:value="filter.companyName"
        placeholder="公司名称"
        size="small"
        allow-clear
        @press-enter="emit('search')"
      />
      <a-select v-model:value="filter.status" placeholder="状态" size="small" allow-clear>
        <a-select-option value="ENABLED">启用</a-select-option>
        <a-select-option value="DISABLED">禁用</a-select-option>
      </a-select>
      <a-button type="primary" size="small" @click="emit('search')">查询</a-button>
      <a-button size="small" @click="emit('reset')">重置</a-button>
    </div>

    <vxe-grid
      class="org-table"
      :data="data"
      :columns="gridColumns"
      :loading="loading"
      :column-config="{ resizable: true }"
      stripe
      border="inner"
      size="small"
      max-height="480"
      @row-click="
        ({ row }: { row: Record<string, unknown> }) => emit('rowClick', row as OrgCompanyVO)
      "
    >
      <template #companyStatus="{ row }: { row: Record<string, unknown> }">
        <a-tag :color="row.status === 'ENABLED' ? 'success' : 'default'">
          {{ row.status === 'ENABLED' ? '启用' : '禁用' }}
        </a-tag>
      </template>
      <template #companyOps="{ row }: { row: Record<string, unknown> }">
        <a-button v-if="canEdit" size="small" type="link" @click="emit('edit', row as OrgCompanyVO)"
          >编辑</a-button
        >
        <a-button
          v-if="canDelete"
          size="small"
          type="link"
          danger
          @click="emit('delete', row as OrgCompanyVO)"
          >删除</a-button
        >
      </template>
    </vxe-grid>

    <div class="org-panel-footer">
      <span>共 {{ total }} 条</span>
      <a-pagination
        :current="pageNo"
        :page-size="pageSize"
        :total="total"
        size="small"
        :page-size-options="['10', '20', '50']"
        show-size-changer
        @change="emit('pageChange', $event)"
        @show-size-change="(cur: number, size: number) => emit('pageSizeChange', cur, size)"
      />
    </div>
  </section>
</template>
