<script setup lang="ts">
import { getDictDataByCode } from '@/api/modules/dict'
import { ref, reactive, onMounted, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import type { CostSubjectTreeNode, CostSubjectVO } from '@/types/costSubject'
import { normalizeArray } from '@/utils/normalizeArray'
import {
  getCostSubjectTree,
  createCostSubject,
  updateCostSubject,
  deleteCostSubject,
  toggleCostSubjectStatus,
} from '@/api/modules/costSubject'

// ─── 科目类别标签 ──────────────────────────────

const ACCOUNT_CATEGORY_LABEL: Record<string, string> = {
  COST: '成本',
  REVENUE: '收入',
  SETTLEMENT: '结算',
}
const ACCOUNT_CATEGORY_COLOR: Record<string, string> = {
  COST: 'blue',
  REVENUE: 'success',
  SETTLEMENT: 'orange',
}

// ─── Tree state ──────────────────────────────────────────

const treeData = ref<CostSubjectTreeNode[]>([])
const treeLoading = ref(false)
const selectedKeys = ref<string[]>([])
const expandedKeys = ref<string[]>([])

const selectedNode = computed<CostSubjectTreeNode | null>(() => {
  if (selectedKeys.value.length === 0) return null
  return findNode(treeData.value, selectedKeys.value[0])
})

function findNode(nodes: CostSubjectTreeNode[], id: string): CostSubjectTreeNode | null {
  for (const node of nodes) {
    if (node.id === id) return node
    if (node.children) {
      const found = findNode(node.children, id)
      if (found) return found
    }
  }
  return null
}

// ─── Form state ──────────────────────────────────────────

const formMode = ref<'view' | 'create' | 'edit'>('view')
const formVisible = ref(false)
const formLoading = ref(false)
const formData = reactive<Partial<CostSubjectVO>>({
  parentId: null,
  subjectCode: '',
  subjectName: '',
  subjectType: 'MATERIAL',
  accountCategory: 'COST',
  level: 1,
  sortOrder: 0,
  status: 'ENABLE',
})

const subjectTypeOptions = ref<{ dictLabel: string; dictValue: string }[]>([])
const fallbackSubjectTypeOptions = [
  { dictLabel: '材料费', dictValue: 'MATERIAL' },
  { dictLabel: '人工费', dictValue: 'LABOR' },
  { dictLabel: '机械费', dictValue: 'MACHINERY' },
  { dictLabel: '分包费', dictValue: 'SUBCONTRACT' },
  { dictLabel: '其他费用', dictValue: 'OTHER' },
]
async function fetchSubjectTypes() {
  try {
    const options = normalizeArray<{ dictLabel: string; dictValue: string }>(
      await getDictDataByCode('cost_subject_type'),
    )
    subjectTypeOptions.value = options.length ? options : fallbackSubjectTypeOptions
  } catch (e: unknown) {
    console.error(e)
    subjectTypeOptions.value = fallbackSubjectTypeOptions
  }
}

// ─── Tree methods ────────────────────────────────────────

async function fetchTree() {
  treeLoading.value = true
  try {
    // 不传 category，一次性加载全部科目
    treeData.value = normalizeArray<CostSubjectTreeNode>(await getCostSubjectTree())
  } catch (e: unknown) {
    console.error(e)
    message.error('加载科目树失败')
    treeData.value = []
  } finally {
    treeLoading.value = false
  }
}

function handleTreeSelect(keys: string[]) {
  selectedKeys.value = keys
  formMode.value = 'view'
}

// ─── CRUD methods ────────────────────────────────────────

function handleAddRoot() {
  formMode.value = 'create'
  formData.parentId = null
  formData.subjectCode = ''
  formData.subjectName = ''
  formData.subjectType = 'MATERIAL'
  formData.accountCategory = 'COST'
  formData.level = 1
  formData.sortOrder = 0
  formData.status = 'ENABLE'
  formVisible.value = true
}

function handleAddChild() {
  if (!selectedNode.value) {
    message.warning('请先在左侧树中选择一个父节点')
    return
  }
  formMode.value = 'create'
  formData.parentId = selectedNode.value.id
  formData.subjectCode = ''
  formData.subjectName = ''
  formData.subjectType = 'MATERIAL'
  formData.level = (selectedNode.value.level || 0) + 1
  formData.sortOrder = 0
  formData.status = 'ENABLE'
  formVisible.value = true
}

function handleEdit() {
  if (!selectedNode.value) {
    message.warning('请先在左侧树中选择要编辑的节点')
    return
  }
  formMode.value = 'edit'
  const node = selectedNode.value
  formData.parentId = node.parentId
  formData.subjectCode = node.subjectCode
  formData.subjectName = node.subjectName
  formData.subjectType = node.subjectType
  formData.level = node.level
  formData.sortOrder = node.sortOrder
  formData.status = node.status
  formVisible.value = true
}

async function handleFormSubmit() {
  if (!formData.subjectCode?.trim() || !formData.subjectName?.trim()) {
    message.warning('请填写科目编码和科目名称')
    return
  }
  formLoading.value = true
  try {
    if (formMode.value === 'create') {
      await createCostSubject(formData)
      message.success('新增成功')
    } else if (formMode.value === 'edit') {
      await updateCostSubject(selectedKeys.value[0], formData)
      message.success('更新成功')
    }
    formVisible.value = false
    await fetchTree()
    // Restore selection
    const newId = formMode.value === 'create' ? undefined : selectedKeys.value[0]
    if (newId) selectedKeys.value = [newId]
  } catch (e: unknown) {
    console.error(e)
    message.error('保存失败，请稍后重试')
  } finally {
    formLoading.value = false
  }
}

function handleToggleStatus() {
  if (!selectedNode.value) {
    message.warning('请先选择要切换的节点')
    return
  }
  const node = selectedNode.value
  const action = node.status === 'ENABLE' ? '停用' : '启用'
  Modal.confirm({
    title: `确认${action}`,
    content: `确定${action}科目「${node.subjectName}」吗？`,
    okText: `确认${action}`,
    cancelText: '取消',
    onOk: async () => {
      try {
        await toggleCostSubjectStatus(node.id)
        message.success(`${action}成功`)
        await fetchTree()
        selectedKeys.value = [node.id]
      } catch (e: unknown) {
        console.error(e)
        message.error(`${action}失败`)
      }
    },
  })
}

function handleDelete() {
  if (!selectedNode.value) {
    message.warning('请先选择要删除的节点')
    return
  }
  const node = selectedNode.value
  const hasChildren = node.children && node.children.length > 0
  Modal.confirm({
    title: '确认删除',
    content: hasChildren
      ? `科目「${node.subjectName}」下有子节点，当前不支持直接删除，请先删除或调整其子节点。`
      : `确定删除科目「${node.subjectName}」吗？`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteCostSubject(node.id)
        message.success('已删除')
        selectedKeys.value = []
        await fetchTree()
      } catch (e: unknown) {
        console.error(e)
        message.error('删除失败')
      }
    },
  })
}

// ─── Lifecycle ───────────────────────────────────────────

onMounted(() => {
  fetchTree()
  fetchSubjectTypes()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page cost-subject-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="cl-breadcrumb">
          <a-breadcrumb-item>成本管理</a-breadcrumb-item>
          <a-breadcrumb-item>成本科目</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
      <div style="display: flex; gap: 8px; align-items: center">
        <a-button type="primary" @click="handleAddRoot">新增根节点</a-button>
        <a-button @click="handleAddChild" :disabled="!selectedNode">新增子节点</a-button>
      </div>
    </div>

    <div class="cs-layout">
      <!-- Left: Tree -->
      <div class="cs-tree-panel">
        <div class="cs-tree-header">
          <span class="cs-tree-title">科目树</span>
          <a-button type="link" size="small" @click="fetchTree">刷新</a-button>
        </div>
        <div class="cs-tree-body" v-loading="treeLoading">
          <a-tree
            v-if="treeData.length > 0"
            :tree-data="treeData"
            :field-names="{ children: 'children', title: 'subjectName', key: 'id' }"
            :selected-keys="selectedKeys"
            :expanded-keys="expandedKeys"
            show-line
            block-node
            @select="handleTreeSelect"
            @expand="(keys: string[]) => (expandedKeys = keys)"
          >
            <template #title="{ subjectCode, subjectName }">
              <span class="cs-tree-node">
                <span class="cs-tree-code">{{ subjectCode }}</span>
                <span class="cs-tree-name">{{ subjectName }}</span>
              </span>
            </template>
          </a-tree>
          <a-empty v-else description="暂无科目数据" />
        </div>
      </div>

      <!-- Right: Detail -->
      <div class="cs-detail-panel">
        <template v-if="selectedNode">
          <div class="cs-detail-header">
            <span class="cs-detail-title">{{ selectedNode.subjectName }}</span>
            <a-space>
              <a-button size="small" @click="handleEdit">编辑</a-button>
              <a-button
                size="small"
                :type="selectedNode.status === 'ENABLE' ? 'default' : 'primary'"
                @click="handleToggleStatus"
              >
                {{ selectedNode.status === 'ENABLE' ? '停用' : '启用' }}
              </a-button>
              <a-button
                size="small"
                danger
                :disabled="(selectedNode.children?.length ?? 0) > 0"
                @click="handleDelete"
              >
                删除
              </a-button>
            </a-space>
          </div>
          <a-descriptions :column="2" size="small" bordered>
            <a-descriptions-item label="科目编码">{{
              selectedNode.subjectCode
            }}</a-descriptions-item>
            <a-descriptions-item label="科目名称">{{
              selectedNode.subjectName
            }}</a-descriptions-item>
            <a-descriptions-item label="科目类型">
              <a-tag>{{
                subjectTypeOptions.find((o) => o.dictValue === selectedNode.subjectType)
                  ?.dictLabel ?? selectedNode.subjectType
              }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="层级">{{ selectedNode.level }}</a-descriptions-item>
            <a-descriptions-item label="排序">{{ selectedNode.sortOrder }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="selectedNode.status === 'ENABLE' ? 'success' : 'error'">
                {{ selectedNode.status === 'ENABLE' ? '启用' : '停用' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="父节点ID">{{
              selectedNode.parentId || '无（根节点）'
            }}</a-descriptions-item>
            <a-descriptions-item label="子节点数">{{
              selectedNode.children?.length ?? 0
            }}</a-descriptions-item>
          </a-descriptions>
        </template>
        <template v-else>
          <div class="cs-detail-empty">
            <a-empty description="请从左侧树中选择一个科目查看详情" />
          </div>
        </template>
      </div>
    </div>

    <!-- Form Modal -->
    <a-modal
      v-model:open="formVisible"
      :title="formMode === 'create' ? '新增成本科目' : '编辑成本科目'"
      :width="800"
      @ok="handleFormSubmit"
      :confirm-loading="formLoading"
    >
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="科目编码" required>
              <a-input v-model:value="formData.subjectCode" placeholder="如：MAT-001" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="科目名称" required>
              <a-input v-model:value="formData.subjectName" placeholder="如：钢材费" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="科目类型">
              <a-select
                v-model:value="formData.subjectType"
                :options="
                  subjectTypeOptions.map((o) => ({ value: o.dictValue, label: o.dictLabel }))
                "
                placeholder="请选择类型"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="排序">
              <a-input-number v-model:value="formData.sortOrder" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="父节点">
              <a-input :value="formData.parentId || '根节点'" disabled />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="层级">
              <a-input-number
                v-model:value="formData.level"
                :min="1"
                disabled
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.cs-layout {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
  min-height: 560px;
}

.cs-tree-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--surface);
  overflow: hidden;
  border: 0;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}

.cs-tree-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 56px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-subtle);
}

.cs-tree-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
}

.cs-tree-body {
  flex: 1;
  min-height: 0;
  padding: 16px 20px;
  overflow: auto;
}

.cs-tree-node {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  max-width: 100%;
  min-width: 0;
}

.cs-tree-code {
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--muted);
  font-family: var(--font-family);
  font-variant-numeric: tabular-nums;
}

.cs-tree-name {
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  color: var(--text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cs-detail-panel {
  min-width: 0;
  min-height: 0;
  padding: 20px;
  background: var(--surface);
  overflow: auto;
  border: 0;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}

.cs-detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-subtle);
}

.cs-detail-title {
  min-width: 0;
  overflow: hidden;
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cs-detail-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, rgba(24, 144, 255, 0.06), transparent 46%), #fff;
}

@media (max-width: 980px) {
  .cs-layout {
    grid-template-columns: 1fr;
  }
}
</style>
