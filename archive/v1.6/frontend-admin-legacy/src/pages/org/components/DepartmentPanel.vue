<script setup lang="ts">
import { computed } from 'vue'
import type { OrgDepartmentTreeNodeVO } from '@/types/org'

const props = defineProps<{
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

const flatDepartments = computed(() => {
  const result: OrgDepartmentTreeNodeVO[] = []
  const walk = (nodes: OrgDepartmentTreeNodeVO[]) => {
    nodes.forEach((node) => {
      result.push(node)
      if (node.children?.length) walk(node.children)
    })
  }
  walk(props.filteredTree)
  return result
})

function handleDeptClick(id: string) {
  emit('update:selectedKeys', [id])
  emit('select')
}
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
      <span
        >{{ currentCompanyName }} / {{ currentCompanyName }} {{ departmentCount }} 个部门节点</span
      >
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
        <ul v-if="flatDepartments.length" class="org-list">
          <li
            v-for="dept in flatDepartments"
            :key="dept.id"
            :class="{ active: selectedKeys.includes(dept.id) }"
            @click="handleDeptClick(dept.id)"
          >
            {{ dept.deptName }}
          </li>
        </ul>
        <div v-else-if="!treeLoading" class="org-empty-hint">暂无部门节点</div>
      </a-spin>
    </div>
  </section>
</template>
