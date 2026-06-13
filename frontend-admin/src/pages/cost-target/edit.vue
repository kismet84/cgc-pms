<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import type { TreeSelectProps } from 'ant-design-vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { getProjectList } from '@/api/modules/project'
import { getCostSubjectTree } from '@/api/modules/costSubject'
import {
  createCostTarget,
  updateCostTarget,
  getCostTargetDetail,
  getCostTargetItems,
  saveCostTargetItems,
  submitCostTargetForApproval,
} from '@/api/modules/costTarget'
import type { CostTargetVO, CostTargetItemVO } from '@/types/costTarget'
import type { ProjectVO } from '@/types/project'

const router = useRouter()
const route = useRoute()

const editId = (route.params.id as string) || ''
const isEdit = ref(Boolean(editId))

// ---- Page state ----
const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)

// ---- Reference data ----
const projects = ref<ProjectVO[]>([])
const subjectTree = ref<TreeSelectProps['treeData']>([])

// ---- Form data ----
const formRef = ref<FormInstance>()
const formData = reactive({
  projectId: undefined as string | undefined,
  versionNo: '',
  versionName: '',
  effectiveDate: undefined as string | undefined,
  totalTargetAmount: undefined as number | undefined,
  remark: '',
})

const formRules: Record<string, Rule[]> = {
  projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }],
  versionNo: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
  versionName: [{ required: true, message: '请输入版本名称', trigger: 'blur' }],
  totalTargetAmount: [{ required: true, message: '请输入目标成本总额', trigger: 'blur' }],
}

// ---- Items table ----
interface EditableItem {
  _key: string
  costSubjectId: string | undefined
  costSubjectName: string
  costSubjectCode: string
  targetAmount: number | undefined
  sortOrder: number
}

function genKey(): string {
  return `cti_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

const items = ref<EditableItem[]>([])

function addRow() {
  items.value.push({
    _key: genKey(),
    costSubjectId: undefined,
    costSubjectName: '',
    costSubjectCode: '',
    targetAmount: undefined,
    sortOrder: items.value.length + 1,
  })
}

function removeRow(key: string) {
  const idx = items.value.findIndex((r) => r._key === key)
  if (idx !== -1) items.value.splice(idx, 1)
}

// ---- Subject tree ----
interface TreeNode {
  id: string
  subjectCode: string
  subjectName: string
  children?: TreeNode[]
}

function convertToTreeData(nodes: TreeNode[]): TreeSelectProps['treeData'] {
  return nodes.map((node) => ({
    value: node.id,
    title: `${node.subjectCode} ${node.subjectName}`,
    children: node.children ? convertToTreeData(node.children) : undefined,
  }))
}

async function fetchSubjectTree() {
  try {
    const data = await getCostSubjectTree()
    subjectTree.value = convertToTreeData(data)
  } catch {
    subjectTree.value = []
  }
}

function onSubjectChange(val: string | number | undefined, record: EditableItem) {
  if (!val) {
    record.costSubjectName = ''
    record.costSubjectCode = ''
    return
  }
  const valStr = String(val)
  const findNode = (
    nodes: TreeSelectProps['treeData'],
    targetVal: string,
  ): { title: string } | null => {
    for (const node of nodes || []) {
      if (String(node.value) === targetVal) return node as { title: string }
      if (node.children) {
        const found = findNode(node.children as TreeSelectProps['treeData'], targetVal)
        if (found) return found
      }
    }
    return null
  }
  const found = findNode(subjectTree.value, valStr)
  if (found) {
    const parts = (found.title as string).split(' ')
    record.costSubjectCode = parts[0] || ''
    record.costSubjectName = parts.slice(1).join(' ') || ''
  }
}

// ---- Load reference data ----
async function loadReferenceData() {
  try {
    const res = await getProjectList({ pageNum: 1, pageSize: 200 })
    projects.value = res.records
  } catch {
    projects.value = []
  }
}

// ---- Load existing data (edit mode) ----
async function loadExisting() {
  if (!editId) return
  loading.value = true
  try {
    const target = await getCostTargetDetail(editId)
    formData.projectId = String(target.projectId)
    formData.versionNo = target.versionNo
    formData.versionName = target.versionName
    formData.effectiveDate = target.effectiveDate
    formData.totalTargetAmount = Number(target.totalTargetAmount) || undefined
    formData.remark = target.remark || ''

    // Load items
    try {
      const existingItems = await getCostTargetItems(editId)
      items.value = existingItems.map((it, idx) => ({
        _key: genKey(),
        costSubjectId: String(it.costSubjectId),
        costSubjectName: it.costSubjectName || '',
        costSubjectCode: it.costSubjectCode || '',
        targetAmount: Number(it.targetAmount) || undefined,
        sortOrder: idx + 1,
      }))
    } catch {
      items.value = []
    }
  } catch {
    message.error('加载目标成本信息失败')
    router.push('/cost-target')
  } finally {
    loading.value = false
  }
}

// ---- Computed ----
const itemsTotal = computed(() =>
  items.value.reduce((s, r) => s + (Number(r.targetAmount) || 0), 0),
)

const totalMatch = computed(() => {
  const total = Number(formData.totalTargetAmount) || 0
  const sum = itemsTotal.value
  if (sum === 0) return true
  return Math.abs(total - sum) < 0.01
})

// ---- Validation ----
async function validateForm(): Promise<boolean> {
  try {
    await formRef.value?.validate()
  } catch {
    message.warning('请完善基本信息后再提交')
    return false
  }

  if (items.value.length === 0) {
    message.warning('请至少添加一条成本科目明细')
    return false
  }

  const emptyAmount = items.value.some(
    (r) => !r.costSubjectId || !r.targetAmount || Number(r.targetAmount) <= 0,
  )
  if (emptyAmount) {
    message.warning('存在未选择科目或未填写金额的明细项')
    return false
  }

  if (!totalMatch.value) {
    message.warning(
      `科目金额合计（${itemsTotal.value.toFixed(2)}）与目标成本总额（${Number(formData.totalTargetAmount || 0).toFixed(2)}）不一致`,
    )
    return false
  }

  return true
}

// ---- Build payloads ----
function buildTargetPayload(): Partial<CostTargetVO> {
  return {
    projectId: formData.projectId,
    versionNo: formData.versionNo,
    versionName: formData.versionName,
    effectiveDate: formData.effectiveDate,
    totalTargetAmount: String(formData.totalTargetAmount ?? 0),
    remark: formData.remark,
  }
}

function buildItemsPayload(): CostTargetItemVO[] {
  return items.value.map((r, idx) => ({
    costSubjectId: r.costSubjectId!,
    targetAmount: String(r.targetAmount ?? '0'),
    sortOrder: idx + 1,
  }))
}

// ---- Submit ----
async function doSubmit(withApproval: boolean) {
  if (!(await validateForm())) return

  saving.value = true
  try {
    let targetId = editId

    if (isEdit.value) {
      await updateCostTarget(targetId, buildTargetPayload())
    } else {
      const createdId = await createCostTarget(buildTargetPayload())
      targetId = String(createdId)
    }

    // Save items
    await saveCostTargetItems(targetId, buildItemsPayload())

    if (withApproval) {
      await submitCostTargetForApproval(targetId)
      message.success('目标成本已保存并提交审批')
    } else {
      message.success('目标成本已保存')
    }
    router.push('/cost-target')
  } catch {
    message.error('保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

function handleSave() {
  doSubmit(false)
}

function handleSubmit() {
  Modal.confirm({
    title: '确认提交审批？',
    content: '提交后将进入审批流程，审批通过后方可激活使用。',
    okText: '确认提交',
    cancelText: '取消',
    onOk: () => doSubmit(true),
  })
}

function handleCancel() {
  if (items.value.length > 0 || formData.versionName) {
    Modal.confirm({
      title: '放弃编辑？',
      content: '已填写的内容将不会保存。',
      okText: '确认放弃',
      cancelText: '继续编辑',
      onOk: () => router.push('/cost-target'),
    })
  } else {
    router.push('/cost-target')
  }
}

// ---- Table columns ----
const itemColumns = [
  { title: '序号', dataIndex: 'index', width: 60, align: 'center' as const },
  { title: '成本科目', dataIndex: 'costSubjectId', minWidth: 200 },
  { title: '目标金额(元)', dataIndex: 'targetAmount', width: 160, align: 'right' as const },
  { title: '操作', dataIndex: 'ops', width: 70, align: 'center' as const },
]

// ---- Helpers ----
function fmtMoney(val: number | undefined): string {
  return (Number(val) || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

onMounted(() => {
  loadReferenceData()
  fetchSubjectTree()
  if (isEdit.value) loadExisting()
})
</script>

<template>
  <div class="cte-page">
    <a-breadcrumb class="cte-breadcrumb">
      <a-breadcrumb-item>成本管理</a-breadcrumb-item>
      <a-breadcrumb-item>
        <a @click="router.push('/cost-target')">目标成本管理</a>
      </a-breadcrumb-item>
      <a-breadcrumb-item>{{ isEdit ? '编辑目标成本' : '新建目标成本' }}</a-breadcrumb-item>
    </a-breadcrumb>

    <a-spin :spinning="loading">
      <div class="cte-card">
        <!-- Basic info -->
        <div class="cte-section">
          <div class="cte-section-title">基本信息</div>
          <a-form ref="formRef" :model="formData" :rules="formRules" layout="vertical">
            <a-row :gutter="24">
              <a-col :span="8">
                <a-form-item label="所属项目" name="projectId">
                  <a-select
                    v-model:value="formData.projectId"
                    placeholder="请选择项目"
                    show-search
                    option-filter-prop="label"
                    allow-clear
                  >
                    <a-select-option
                      v-for="p in projects"
                      :key="p.id"
                      :value="p.id"
                      :label="p.projectName"
                    >
                      {{ p.projectName }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="版本号" name="versionNo">
                  <a-input
                    v-model:value="formData.versionNo"
                    placeholder="如 V1.0、2024Q1"
                    allow-clear
                  />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="版本名称" name="versionName">
                  <a-input
                    v-model:value="formData.versionName"
                    placeholder="如 2024年第一版目标成本"
                    allow-clear
                  />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="目标成本总额(元)" name="totalTargetAmount">
                  <a-input-number
                    v-model:value="formData.totalTargetAmount"
                    :min="0"
                    :precision="2"
                    placeholder="请输入目标成本总额"
                    style="width: 100%"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="生效日期" name="effectiveDate">
                  <a-date-picker
                    v-model:value="formData.effectiveDate"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="备注" name="remark">
                  <a-input v-model:value="formData.remark" placeholder="备注信息" allow-clear />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </div>

        <!-- Subject items -->
        <div class="cte-section">
          <div class="cte-section-title">
            成本科目明细
            <span class="cte-section-sub">
              合计：<b :class="{ 'cte-warn': !totalMatch }">{{ fmtMoney(itemsTotal) }}</b> 元
              <span v-if="!totalMatch" class="cte-warn-text">（与总额不一致）</span>
            </span>
          </div>

          <div class="cte-toolbar">
            <a-button type="dashed" size="small" @click="addRow">
              <template #icon><PlusOutlined /></template>
              添加科目
            </a-button>
          </div>

          <a-table
            :data-source="items"
            :columns="itemColumns"
            :pagination="false"
            row-key="_key"
            size="small"
            bordered
            :scroll="{ x: 800 }"
          >
            <template #bodyCell="{ column, record, index }">
              <template v-if="column.dataIndex === 'index'">
                {{ index + 1 }}
              </template>
              <template v-else-if="column.dataIndex === 'costSubjectId'">
                <a-tree-select
                  v-model:value="record.costSubjectId"
                  :tree-data="subjectTree"
                  placeholder="选择科目"
                  allow-clear
                  tree-node-filter-prop="title"
                  style="width: 100%"
                  size="small"
                  @change="(val: any) => onSubjectChange(val, record)"
                />
              </template>
              <template v-else-if="column.dataIndex === 'targetAmount'">
                <a-input-number
                  v-model:value="record.targetAmount"
                  :min="0"
                  :precision="2"
                  size="small"
                  style="width: 100%"
                  placeholder="金额"
                />
              </template>
              <template v-else-if="column.dataIndex === 'ops'">
                <a-popconfirm title="确认删除该行？" @confirm="removeRow(record._key)">
                  <a-button type="text" danger size="small">
                    <template #icon><DeleteOutlined /></template>
                  </a-button>
                </a-popconfirm>
              </template>
            </template>

            <template #footer>
              <div class="cte-footer">
                <span>合计：</span>
                <span class="cte-total" :class="{ 'cte-warn': !totalMatch }">
                  {{ fmtMoney(itemsTotal) }} 元
                </span>
              </div>
            </template>
          </a-table>

          <a-empty
            v-if="items.length === 0"
            description="暂无科目明细，请点击「添加科目」新增"
            class="cte-empty"
          />
        </div>

        <!-- Actions -->
        <div class="cte-actions">
          <a-space>
            <a-button type="primary" :loading="saving && !submitting" @click="handleSubmit">
              提交审批
            </a-button>
            <a-button :loading="saving" @click="handleSave"> 保存 </a-button>
            <a-button :disabled="saving" @click="handleCancel">取消</a-button>
          </a-space>
        </div>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.cte-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}
.cte-breadcrumb {
  margin-bottom: 16px;
  font-size: 14px;
}
.cte-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
  padding: 24px 28px;
}

/* Sections */
.cte-section {
  margin-bottom: 28px;
}
.cte-section:last-of-type {
  margin-bottom: 0;
}
.cte-section-title {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 16px;
  display: flex;
  align-items: baseline;
  gap: 14px;
}
.cte-section-sub {
  font-size: 13px;
  font-weight: 400;
  color: #6b7280;
}

/* Toolbar */
.cte-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
}

/* Table footer */
.cte-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: flex-end;
  font-size: 13px;
  color: #374151;
}
.cte-total {
  font-weight: 600;
}
.cte-warn {
  color: #ef4444;
}
.cte-warn-text {
  color: #ef4444;
  font-weight: 400;
  font-size: 12px;
}

/* Empty */
.cte-empty {
  padding: 12px 0;
}

/* Actions */
.cte-actions {
  display: flex;
  justify-content: center;
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}
</style>
