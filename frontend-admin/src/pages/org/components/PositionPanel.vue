<script setup lang="ts">
import type { OrgPositionVO, OrgCompanyVO } from '@/types/org'
import type { FlatDeptItem } from '../utils'
import { MoreOutlined, PlusOutlined } from '@ant-design/icons-vue'

defineProps<{
  canAdd: boolean
  canEdit: boolean
  canDelete: boolean
  loading: boolean
  data: OrgPositionVO[]
  total: number
  pageNo: number
  pageSize: number
  filter: {
    companyId: string | undefined
    departmentId: string | undefined
    positionCode: string
    positionName: string
    status: string | undefined
  }
  gridColumns: Record<string, unknown>[]
  companyOptions: OrgCompanyVO[]
  filterDeptList: FlatDeptItem[]
  flatDeptList: FlatDeptItem[]
  companyData: OrgCompanyVO[]
}>()

const emit = defineEmits<{
  search: []
  reset: []
  'update:pageNo': [value: number]
  'update:pageSize': [value: number]
  pageChange: [page: number]
  pageSizeChange: [cur: number, size: number]
  add: []
  edit: [record: OrgPositionVO]
  delete: [record: OrgPositionVO]
}>()

function getCompanyName(companyId: string | undefined, companyData: OrgCompanyVO[]): string {
  if (!companyId) return '-'
  return companyData.find((c) => c.id === companyId)?.companyName ?? '-'
}

function getDeptName(departmentId: string | undefined, flatDeptList: FlatDeptItem[]): string {
  if (!departmentId) return '-'
  return flatDeptList.find((d) => d.id === departmentId)?.name ?? '-'
}
</script>

<template>
  <section class="org-panel org-position-panel">
    <div class="org-panel-header">
      <div>
        <span class="org-panel-title">岗位管理</span>
        <p>岗位作为项目成员职责和流程节点的稳定枚举。</p>
      </div>
      <a-button v-if="canAdd" type="primary" size="small" @click="emit('add')">
        <template #icon><PlusOutlined /></template>
        新增
      </a-button>
    </div>

    <div class="lg-search-bar position">
      <a-select
        v-model:value="filter.companyId"
        placeholder="所属公司"
        size="small"
        allow-clear
        style="width: 140px"
      >
        <a-select-option v-for="c in companyOptions" :key="c.id" :value="c.id">
          {{ c.companyName }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="filter.departmentId"
        placeholder="所属部门"
        size="small"
        allow-clear
        style="width: 160px"
      >
        <a-select-option v-for="d in filterDeptList" :key="d.id" :value="d.id">
          {{ d.name }}
        </a-select-option>
      </a-select>
      <a-input
        v-model:value="filter.positionCode"
        placeholder="岗位编号"
        size="small"
        allow-clear
        @press-enter="emit('search')"
      />
      <a-input
        v-model:value="filter.positionName"
        placeholder="岗位名称"
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
    >
      <template #posCompanyId="{ row }: { row: Record<string, unknown> }">
        {{ getCompanyName(row.companyId as string | undefined, companyData) }}
      </template>
      <template #posDeptId="{ row }: { row: Record<string, unknown> }">
        {{ getDeptName(row.departmentId as string | undefined, flatDeptList) }}
      </template>
      <template #posStatus="{ row }: { row: Record<string, unknown> }">
        <a-tag :color="row.status === 'ENABLED' ? 'success' : 'default'">
          {{ row.status === 'ENABLED' ? '启用' : '禁用' }}
        </a-tag>
      </template>
      <template #posOps="{ row }: { row: Record<string, unknown> }">
        <a-dropdown :trigger="['click']">
          <a-button class="lg-row-action-trigger" size="small" type="text">
            <MoreOutlined />
          </a-button>
          <template #overlay>
            <a-menu>
              <a-menu-item v-if="canEdit" @click="emit('edit', row as OrgPositionVO)">
                编辑
              </a-menu-item>
              <a-menu-item v-if="canDelete" danger @click="emit('delete', row as OrgPositionVO)">
                删除
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
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
