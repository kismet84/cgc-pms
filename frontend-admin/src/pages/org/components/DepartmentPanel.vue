<script setup lang="ts">
import type { OrgDepartmentTreeNodeVO } from '@/types/org'

defineProps<{
  canAdd: boolean
  canEdit: boolean
  canDelete: boolean
  treeLoading: boolean
  filteredTree: OrgDepartmentTreeNodeVO[]
  selectedKeys: string[]
  keyword: string
  currentCompanyName: string
  departmentCount: number
  selectedCompanyId: string | null
}>()

const emit = defineEmits<{
  'update:selectedKeys': [value: string[]]
  'update:keyword': [value: string]
  'update:selectedCompanyId': [value: string | null]
  select: []
  add: []
  edit: []
  delete: []
}>()
</script>

<template>
  <section class="org-panel org-dept-panel">
    <div class="org-panel-header">
      <div>
        <span class="org-panel-title">部门架构</span>
        <p>{{ currentCompanyName }} · {{ departmentCount }} 个部门节点</p>
      </div>
      <div class="org-panel-actions">
        <a-button v-if="canAdd" size="small" @click="emit('add')">新增</a-button>
        <a-button v-if="canEdit" size="small" :disabled="!selectedKeys.length" @click="emit('edit')"
          >编辑</a-button
        >
        <a-button
          v-if="canDelete"
          size="small"
          danger
          :disabled="!selectedKeys.length"
          @click="emit('delete')"
          >删除</a-button
        >
      </div>
    </div>

    <div class="org-dept-focus">
      <div>
        <span>当前范围</span>
        <strong>{{ currentCompanyName }}</strong>
      </div>
      <a-button
        v-if="selectedCompanyId"
        size="small"
        type="link"
        @click="emit('update:selectedCompanyId', null)"
      >
        查看全部
      </a-button>
    </div>

    <div class="lg-search-bar one-line">
      <a-input
        :value="keyword"
        placeholder="搜索部门名称 / 编号"
        size="small"
        allow-clear
        @update:value="emit('update:keyword', $event)"
      />
    </div>

    <div class="org-tree-wrap">
      <a-spin :spinning="treeLoading">
        <a-tree
          :selected-keys="selectedKeys"
          :tree-data="filteredTree"
          :field-names="{ title: 'deptName', key: 'id', children: 'children' }"
          default-expand-all
          block-node
          @select="emit('select')"
          @update:selectedKeys="emit('update:selectedKeys', $event)"
        />
        <div v-if="!treeLoading && !filteredTree.length" class="org-empty-hint">暂无部门节点</div>
      </a-spin>
    </div>
  </section>
</template>
