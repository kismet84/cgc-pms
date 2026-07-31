<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import type { TreeSelectProps } from 'ant-design-vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { useReferenceStore } from '@/stores/reference'
import { getCostSubjectTree } from '@/api/modules/costSubject'
import { getUserList, type SysUserBrief } from '@/api/modules/system'
import {
  createCostTarget,
  updateCostTarget,
  getCostTargetDetail,
  getCostTargetItems,
  saveCostTargetItems,
  submitCostTargetForApproval,
} from '@/api/modules/costTarget'
import type { CostTargetVO, CostTargetItemVO } from '@/types/costTarget'

interface Props {
  embedded?: boolean
  targetId?: string
  mode?: 'create' | 'edit' | 'view'
}

interface Emits {
  (e: 'saved'): void
  (e: 'close'): void
}

const props = withDefaults(defineProps<Props>(), {
  embedded: false,
  targetId: '',
})
const emit = defineEmits<Emits>()

const router = useRouter()
const route = useRoute()

const isEmbedded = computed(() => props.embedded)
const editId = computed(() => props.targetId || String(route.params.id || ''))
const isEdit = computed(() => (props.mode ? props.mode === 'edit' : !!route.params.id))
const isView = computed(() => props.mode === 'view')
const hasExistingTarget = computed(() => (props.mode ? props.mode !== 'create' : !!route.params.id))
const pageTitle = computed(() => {
  if (isView.value) return '成本目标详情'
  return isEdit.value ? '编辑成本目标' : '新建成本目标'
})
const closeText = computed(() => (isView.value ? '关闭' : '取消'))
const pageSubtitle = computed(() =>
  isView.value
    ? '查看版本信息、金额校验结果与成本科目明细'
    : '统一填写基础信息、金额明细与审批备注',
)

// ---- Page state ----
const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const dirty = ref(false)
let initialLoadDone = false

// ---- beforeRouteLeave guard ----
if (!isEmbedded.value) {
  onBeforeRouteLeave((_to, _from, next) => {
    if (!dirty.value) {
      next()
      return
    }
    Modal.confirm({
      title: '未保存的更改',
      content: '当前成本目标有未保存的修改，确定离开吗？',
      okText: '确定离开',
      okType: 'danger',
      cancelText: '继续编辑',
      onOk: () => next(),
      onCancel: () => next(false),
    })
  })
}

// ---- beforeunload guard ----
function onWindowBeforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}
window.addEventListener('beforeunload', onWindowBeforeUnload)
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onWindowBeforeUnload)
})

// ---- Reference data ----
const referenceStore = useReferenceStore()
const projects = computed(() => referenceStore.projects ?? [])
const subjectTree = ref<TreeSelectProps['treeData']>([])
const userList = ref<SysUserBrief[]>([])

// ---- Form data ----
const formRef = ref<FormInstance>()
const formData = reactive({
  projectId: undefined as string | undefined,
  versionNo: '',
  versionName: '',
  effectiveDate: undefined as string | undefined,
  totalBidCostAmount: undefined as number | undefined,
  totalTargetAmount: undefined as number | undefined,
  totalResponsibilityAmount: undefined as number | undefined,
  remark: '',
})

const formRules: Record<string, Rule[]> = {
  projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }],
  versionNo: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
  versionName: [{ required: true, message: '请输入版本名称', trigger: 'blur' }],
  totalBidCostAmount: [{ required: true, message: '请输入投标成本总额', trigger: 'blur' }],
  totalTargetAmount: [{ required: true, message: '请输入成本目标总额', trigger: 'blur' }],
  totalResponsibilityAmount: [{ required: true, message: '请输入责任预算总额', trigger: 'blur' }],
}

// ---- Items table ----
interface EditableItem {
  _key: string
  costSubjectId: string | undefined
  costSubjectName: string
  costSubjectCode: string
  bidCostAmount: number | undefined
  targetAmount: number | undefined
  responsibilityAmount: number | undefined
  responsibleUserId: string | undefined
  responsibilityUnit: string
  sortOrder: number
}

const items = ref<EditableItem[]>([])

// ---- Dirty tracking ----
watch(
  () => [
    formData.projectId,
    formData.versionNo,
    formData.versionName,
    formData.effectiveDate,
    formData.totalBidCostAmount,
    formData.totalTargetAmount,
    formData.totalResponsibilityAmount,
    formData.remark,
  ],
  () => {
    if (initialLoadDone) dirty.value = true
  },
  { deep: false },
)
watch(
  items,
  () => {
    if (initialLoadDone) dirty.value = true
  },
  { deep: true },
)

function genKey(): string {
  return `cti_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

function addRow() {
  if (isView.value) return
  items.value.push({
    _key: genKey(),
    costSubjectId: undefined,
    costSubjectName: '',
    costSubjectCode: '',
    bidCostAmount: undefined,
    targetAmount: undefined,
    responsibilityAmount: undefined,
    responsibleUserId: undefined,
    responsibilityUnit: '',
    sortOrder: items.value.length + 1,
  })
}

function removeRow(key: string) {
  if (isView.value) return
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

function normalizeTreeNodes(data: unknown): TreeNode[] {
  if (Array.isArray(data)) return data as TreeNode[]
  if (data && typeof data === 'object') {
    const records = (data as { records?: unknown }).records
    if (Array.isArray(records)) return records as TreeNode[]
  }
  return []
}

function convertToTreeData(nodes: TreeNode[]): TreeSelectProps['treeData'] {
  return nodes.map((node) => ({
    value: node.id,
    title: `${node.subjectCode} ${node.subjectName}`,
    children: convertToTreeData(normalizeTreeNodes(node.children)),
  }))
}

async function fetchSubjectTree() {
  try {
    const data = await getCostSubjectTree()
    subjectTree.value = convertToTreeData(normalizeTreeNodes(data))
  } catch (e: unknown) {
    console.error(e)
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

// ---- Load existing data (edit mode) ----
async function loadExisting() {
  if (!editId.value) return
  loading.value = true
  try {
    const target = await getCostTargetDetail(editId.value)
    formData.projectId = String(target.projectId)
    formData.versionNo = target.versionNo
    formData.versionName = target.versionName
    formData.effectiveDate = target.effectiveDate
    formData.totalBidCostAmount = Number(target.totalBidCostAmount) || undefined
    formData.totalTargetAmount = Number(target.totalTargetAmount) || undefined
    formData.totalResponsibilityAmount = Number(target.totalResponsibilityAmount) || undefined
    formData.remark = target.remark || ''

    // Load items
    try {
      const existingItems = await getCostTargetItems(editId.value)
      const itemList = Array.isArray(existingItems) ? existingItems : []
      items.value = itemList.map((it, idx) => ({
        _key: genKey(),
        costSubjectId: String(it.costSubjectId),
        costSubjectName: it.costSubjectName || '',
        costSubjectCode: it.costSubjectCode || '',
        bidCostAmount: Number(it.bidCostAmount) || undefined,
        targetAmount: Number(it.targetAmount) || undefined,
        responsibilityAmount: Number(it.responsibilityAmount) || undefined,
        responsibleUserId: it.responsibleUserId ? String(it.responsibleUserId) : undefined,
        responsibilityUnit: it.responsibilityUnit || '',
        sortOrder: idx + 1,
      }))
    } catch (e: unknown) {
      console.error(e)
      items.value = []
    }
  } catch (e: unknown) {
    console.error(e)
    message.error('加载成本目标信息失败')
    finishClose()
  } finally {
    loading.value = false
    initialLoadDone = true
  }
}

// ---- Computed ----
const itemsTotal = computed(() =>
  items.value.reduce((s, r) => s + (Number(r.targetAmount) || 0), 0),
)
const bidItemsTotal = computed(() =>
  items.value.reduce((sum, item) => sum + (Number(item.bidCostAmount) || 0), 0),
)
const responsibilityItemsTotal = computed(() =>
  items.value.reduce((sum, item) => sum + (Number(item.responsibilityAmount) || 0), 0),
)

const totalMatch = computed(() => {
  const total = Number(formData.totalTargetAmount) || 0
  const sum = itemsTotal.value
  if (sum === 0) return true
  return Math.abs(total - sum) < 0.01
})
const bidTotalMatch = computed(
  () => Math.abs((Number(formData.totalBidCostAmount) || 0) - bidItemsTotal.value) < 0.01,
)
const responsibilityTotalMatch = computed(
  () =>
    Math.abs((Number(formData.totalResponsibilityAmount) || 0) - responsibilityItemsTotal.value) <
      0.01 &&
    Math.abs((Number(formData.totalTargetAmount) || 0) - responsibilityItemsTotal.value) < 0.01,
)
const summaryCards = computed(() => [
  {
    label: '当前模式',
    value: isView.value ? '查看' : isEdit.value ? '编辑' : '新建',
    tone: 'neutral',
  },
  {
    label: '科目明细',
    value: `${items.value.length} 条`,
    tone: items.value.length > 0 ? 'success' : 'neutral',
  },
  {
    label: '目标总额',
    value: `${fmtMoney(formData.totalTargetAmount)} 元`,
    tone: 'neutral',
  },
  {
    label: '金额校验',
    value:
      totalMatch.value && bidTotalMatch.value && responsibilityTotalMatch.value
        ? '已对齐'
        : '待调整',
    tone:
      totalMatch.value && bidTotalMatch.value && responsibilityTotalMatch.value
        ? 'success'
        : 'warning',
  },
])
const analysisChecklist = computed(() => [
  {
    label: '基础信息',
    value: formData.projectId && formData.versionNo && formData.versionName ? '已填写' : '待完善',
    done: Boolean(formData.projectId && formData.versionNo && formData.versionName),
  },
  {
    label: '金额汇总',
    value: `${fmtMoney(itemsTotal.value)} / ${fmtMoney(formData.totalTargetAmount)} 元`,
    done: totalMatch.value,
  },
  {
    label: '明细完整性',
    value:
      items.value.length > 0 &&
      items.value.every(
        (item) =>
          item.costSubjectId &&
          item.bidCostAmount !== undefined &&
          Number(item.targetAmount) > 0 &&
          Number(item.responsibilityAmount) > 0 &&
          item.responsibleUserId,
      )
        ? '可提交'
        : '待补齐',
    done:
      items.value.length > 0 &&
      items.value.every(
        (item) =>
          Boolean(item.costSubjectId) &&
          item.bidCostAmount !== undefined &&
          Number(item.targetAmount) > 0 &&
          Number(item.responsibilityAmount) > 0 &&
          Boolean(item.responsibleUserId),
      ),
  },
  {
    label: '离开提醒',
    value: dirty.value ? '存在未保存修改' : '当前已保存',
    done: !dirty.value,
  },
])

// ---- Validation ----
async function validateForm(): Promise<boolean> {
  try {
    await formRef.value?.validate()
  } catch (e: unknown) {
    console.error(e)
    message.warning('请完善基本信息后再提交')
    return false
  }

  if (items.value.length === 0) {
    message.warning('请至少添加一条成本科目明细')
    return false
  }

  const emptyAmount = items.value.some(
    (r) =>
      !r.costSubjectId ||
      r.bidCostAmount === undefined ||
      !r.targetAmount ||
      Number(r.targetAmount) <= 0 ||
      !r.responsibilityAmount ||
      Number(r.responsibilityAmount) <= 0 ||
      !r.responsibleUserId,
  )
  if (emptyAmount) {
    message.warning('每条明细必须填写投标成本、目标成本、责任预算和责任人')
    return false
  }

  if (!totalMatch.value) {
    message.warning(
      `科目金额合计（${itemsTotal.value.toFixed(2)}）与成本目标总额（${Number(formData.totalTargetAmount || 0).toFixed(2)}）不一致`,
    )
    return false
  }
  if (!bidTotalMatch.value) {
    message.warning('投标成本科目合计与投标成本总额不一致')
    return false
  }
  if (!responsibilityTotalMatch.value) {
    message.warning('责任预算必须完整分解且与目标成本总额一致')
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
    totalBidCostAmount: String(formData.totalBidCostAmount ?? 0),
    totalTargetAmount: String(formData.totalTargetAmount ?? 0),
    totalResponsibilityAmount: String(formData.totalResponsibilityAmount ?? 0),
    remark: formData.remark,
  }
}

function buildItemsPayload(): CostTargetItemVO[] {
  return items.value.map((r, idx) => ({
    costSubjectId: r.costSubjectId!,
    bidCostAmount: String(r.bidCostAmount ?? '0'),
    targetAmount: String(r.targetAmount ?? '0'),
    responsibilityAmount: String(r.responsibilityAmount ?? '0'),
    responsibleUserId: r.responsibleUserId!,
    responsibilityUnit: r.responsibilityUnit,
    sortOrder: idx + 1,
  }))
}

// ---- Submit ----
async function doSubmit(withApproval: boolean) {
  if (isView.value || saving.value) return
  if (!(await validateForm())) return

  saving.value = true
  submitting.value = withApproval
  try {
    let targetId = editId.value

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
      message.success('成本目标已保存并提交审批')
    } else {
      message.success('成本目标已保存')
    }
    emit('saved')
    dirty.value = false
    finishClose()
  } catch (e: unknown) {
    console.error(e)
    message.error('保存失败，请稍后重试')
  } finally {
    saving.value = false
    submitting.value = false
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
  if (isView.value) {
    finishClose()
    return
  }
  if (items.value.length > 0 || formData.versionName) {
    Modal.confirm({
      title: '放弃编辑？',
      content: '已填写的内容将不会保存。',
      okText: '确认放弃',
      cancelText: '继续编辑',
      onOk: () => finishClose(),
    })
  } else {
    finishClose()
  }
}

function finishClose() {
  if (isEmbedded.value) {
    emit('close')
    return
  }
  router.push('/cost-target')
}

// ---- Table columns ----
const itemColumns = computed(() => [
  { title: '序号', dataIndex: 'index', width: 46, align: 'center' as const },
  { title: '成本科目', dataIndex: 'costSubjectId', minWidth: 180 },
  { title: '投标成本(元)', dataIndex: 'bidCostAmount', width: 130, align: 'right' as const },
  { title: '目标金额(元)', dataIndex: 'targetAmount', width: 128, align: 'right' as const },
  { title: '责任预算(元)', dataIndex: 'responsibilityAmount', width: 130, align: 'right' as const },
  { title: '责任人', dataIndex: 'responsibleUserId', width: 140 },
  { title: '责任单位', dataIndex: 'responsibilityUnit', width: 150 },
  ...(isView.value
    ? []
    : [{ title: '操作', dataIndex: 'ops', width: 54, align: 'center' as const }]),
])

// ---- Helpers ----
function fmtMoney(val: number | undefined): string {
  return (Number(val) || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

onMounted(() => {
  referenceStore.fetchProjects()
  fetchSubjectTree()
  getUserList({ pageNo: 1, pageSize: 200 })
    .then((result) => (userList.value = result.records.filter((user) => user.status !== 'DISABLE')))
    .catch(() => (userList.value = []))
  if (hasExistingTarget.value && editId.value) loadExisting()
  else initialLoadDone = true
})
</script>

<template>
  <div :class="['cte-page app-page project-target-redesign', { 'cte-embedded': isEmbedded }]">
    <div v-if="!isEmbedded" class="pt-page-head">
      <div class="cte-head-main">
        <a-breadcrumb class="pt-breadcrumb">
          <a-breadcrumb-item>目标管理</a-breadcrumb-item>
          <a-breadcrumb-item>
            <a @click="router.push('/cost-target')">目标管理</a>
          </a-breadcrumb-item>
          <a-breadcrumb-item>{{ pageTitle }}</a-breadcrumb-item>
        </a-breadcrumb>
        <div class="cte-head-copy">
          <h1 class="cte-page-title">{{ pageTitle }}</h1>
          <p class="cte-page-subtitle">{{ pageSubtitle }}</p>
        </div>
      </div>
      <div class="pt-head-actions">
        <a-button :disabled="saving" @click="handleCancel">{{ closeText }}</a-button>
        <a-button v-if="!isView" :loading="saving" @click="handleSave">保存</a-button>
        <!-- <a-button v-if="!isView" type="primary" :loading="saving && !submitting" @click="handleSubmit"> -->
        <a-button
          v-if="!isView"
          type="primary"
          :loading="saving && !submitting"
          @click="handleSubmit"
        >
          提交审批
        </a-button>
      </div>
    </div>

    <a-spin :spinning="loading">
      <div class="cte-shell">
        <div class="cte-main">
          <section class="lg-kpi-strip cte-kpi-strip">
            <div
              v-for="card in summaryCards"
              :key="card.label"
              class="cte-kpi-card"
              :class="`is-${card.tone}`"
            >
              <span class="cte-kpi-label">{{ card.label }}</span>
              <strong class="cte-kpi-value">{{ card.value }}</strong>
            </div>
          </section>

          <a-form ref="formRef" :model="formData" :rules="formRules" layout="vertical">
            <section class="pt-panel cte-section">
              <div class="pt-panel-header">基础信息</div>
              <div class="pt-panel-body pt-form-grid">
                <a-form-item label="所属项目" name="projectId">
                  <a-select
                    v-model:value="formData.projectId"
                    placeholder="请选择项目"
                    show-search
                    option-filter-prop="label"
                    allow-clear
                    :disabled="isView"
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
                <a-form-item label="版本号" name="versionNo">
                  <a-input
                    v-model:value="formData.versionNo"
                    placeholder="如 V1.0、2024Q1"
                    allow-clear
                    :readonly="isView"
                    :disabled="isView"
                  />
                </a-form-item>
                <a-form-item label="版本名称" name="versionName">
                  <a-input
                    v-model:value="formData.versionName"
                    placeholder="如 2024年第一版成本目标"
                    allow-clear
                    :readonly="isView"
                    :disabled="isView"
                  />
                </a-form-item>
                <a-form-item label="生效日期" name="effectiveDate">
                  <a-date-picker
                    v-model:value="formData.effectiveDate"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                    :disabled="isView"
                  />
                </a-form-item>
              </div>
            </section>

            <section class="pt-panel cte-section">
              <div class="pt-panel-header">
                成本科目
                <span class="cte-section-sub">
                  合计：<b :class="{ 'cte-warn': !totalMatch }">{{ fmtMoney(itemsTotal) }}</b> 元
                  <span v-if="!totalMatch" class="cte-warn-text">（与总额不一致）</span>
                </span>
              </div>
              <div class="pt-panel-body">
                <div v-if="!isView" class="cte-toolbar">
                  <a-button type="dashed" size="small" @click="addRow">
                    <template #icon><PlusOutlined /></template>
                    添加科目
                  </a-button>
                  <span class="cte-toolbar-hint">按成本科目逐项维护，提交前必须保证金额对齐</span>
                </div>

                <a-table
                  :data-source="items"
                  :columns="itemColumns"
                  :pagination="false"
                  :scroll="{ x: 1150 }"
                  row-key="_key"
                  size="small"
                  bordered
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
                        :disabled="isView"
                        @change="(val: string | number | undefined) => onSubjectChange(val, record)"
                      />
                    </template>
                    <template
                      v-else-if="
                        ['bidCostAmount', 'targetAmount', 'responsibilityAmount'].includes(
                          String(column.dataIndex),
                        )
                      "
                    >
                      <a-input-number
                        v-model:value="record[column.dataIndex]"
                        :min="0"
                        :precision="2"
                        size="small"
                        style="width: 100%"
                        placeholder="金额"
                        :disabled="isView"
                      />
                    </template>
                    <template v-else-if="column.dataIndex === 'responsibleUserId'">
                      <a-select
                        v-model:value="record.responsibleUserId"
                        size="small"
                        show-search
                        option-filter-prop="label"
                        placeholder="选择责任人"
                        :disabled="isView"
                      >
                        <a-select-option
                          v-for="user in userList"
                          :key="user.id"
                          :value="String(user.id)"
                          :label="user.realName || user.username"
                        >
                          {{ user.realName || user.username }}
                        </a-select-option>
                      </a-select>
                    </template>
                    <template v-else-if="column.dataIndex === 'responsibilityUnit'">
                      <a-input
                        v-model:value="record.responsibilityUnit"
                        size="small"
                        placeholder="责任部门/班组"
                        :disabled="isView"
                      />
                    </template>
                    <template v-else-if="column.dataIndex === 'ops' && !isView">
                      <a-popconfirm title="确认删除该行？" @confirm="removeRow(record._key)">
                        <a-button type="text" danger size="small">
                          <template #icon><DeleteOutlined /></template>
                        </a-button>
                      </a-popconfirm>
                    </template>
                  </template>

                  <template #footer>
                    <div class="cte-footer">
                      <span>投标 / 目标 / 责任合计：</span>
                      <span class="cte-total" :class="{ 'cte-warn': !totalMatch }">
                        {{ fmtMoney(bidItemsTotal) }} / {{ fmtMoney(itemsTotal) }} /
                        {{ fmtMoney(responsibilityItemsTotal) }} 元
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
            </section>

            <section class="pt-panel cte-section">
              <div class="pt-panel-header">金额明细</div>
              <div class="pt-panel-body pt-form-grid cte-amount-grid">
                <a-form-item label="投标成本总额(元)" name="totalBidCostAmount">
                  <a-input-number
                    v-model:value="formData.totalBidCostAmount"
                    :min="0"
                    :precision="2"
                    placeholder="投标成本基准"
                    style="width: 100%"
                    :disabled="isView"
                  />
                </a-form-item>
                <a-form-item label="成本目标总额(元)" name="totalTargetAmount">
                  <a-input-number
                    v-model:value="formData.totalTargetAmount"
                    :min="0"
                    :precision="2"
                    placeholder="请输入成本目标总额"
                    style="width: 100%"
                    :disabled="isView"
                  />
                </a-form-item>
                <a-form-item label="责任预算总额(元)" name="totalResponsibilityAmount">
                  <a-input-number
                    v-model:value="formData.totalResponsibilityAmount"
                    :min="0"
                    :precision="2"
                    placeholder="须与目标成本一致"
                    style="width: 100%"
                    :disabled="isView"
                  />
                </a-form-item>
                <div class="cte-amount-check">
                  <span>投标 / 目标 / 责任明细合计</span>
                  <b
                    :class="{
                      'cte-warn': !totalMatch || !bidTotalMatch || !responsibilityTotalMatch,
                    }"
                  >
                    {{ fmtMoney(bidItemsTotal) }} / {{ fmtMoney(itemsTotal) }} /
                    {{ fmtMoney(responsibilityItemsTotal) }} 元
                  </b>
                </div>
              </div>
            </section>

            <section class="pt-panel cte-section">
              <div class="pt-panel-header">审批与备注</div>
              <div class="pt-panel-body">
                <a-form-item label="备注" name="remark">
                  <a-textarea
                    v-model:value="formData.remark"
                    :rows="2"
                    placeholder="请输入备注信息"
                    :readonly="isView"
                    :disabled="isView"
                  />
                </a-form-item>
              </div>
            </section>
          </a-form>
        </div>

        <aside class="cte-aside">
          <section class="cte-side-card">
            <div class="cte-side-title">提交检查</div>
            <div
              v-for="item in analysisChecklist"
              :key="item.label"
              class="cte-check-row"
              :class="{ 'is-done': item.done, 'is-pending': !item.done }"
            >
              <div>
                <div class="cte-check-label">{{ item.label }}</div>
                <div class="cte-check-value">{{ item.value }}</div>
              </div>
              <strong>{{ item.done ? '通过' : '待补' }}</strong>
            </div>
          </section>

          <section class="cte-side-card">
            <div class="cte-side-title">填写说明</div>
            <ul class="cte-note-list">
              <li>版本号与版本名称建议体现批次或周期，便于审批检索。</li>
              <li>投标、目标、责任预算均须按科目完整分解，责任预算总额必须等于目标成本。</li>
              <li>离开页面或关闭弹窗前，请先确认已保存，避免触发未保存拦截。</li>
            </ul>
          </section>
        </aside>
      </div>
    </a-spin>

    <div v-if="isEmbedded" class="cte-modal-actions">
      <a-button :disabled="saving" @click="handleCancel">{{ closeText }}</a-button>
      <template v-if="!isView">
        <a-button :loading="saving" @click="handleSave">保存</a-button>
        <a-button type="primary" :loading="saving" @click="handleSubmit">提交审批</a-button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.cte-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 0 12px;
}
.project-target-redesign.cte-embedded {
  background: transparent;
  min-height: 0;
  padding: 0;
  font-size: 12px;
}
.pt-page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.cte-head-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.cte-head-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.cte-page-title {
  margin: 0;
  color: var(--text);
  font-size: 26px;
  font-weight: 800;
  line-height: 34px;
}
.cte-page-subtitle {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
}
.cte-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}
.cte-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}
.cte-kpi-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  background: transparent;
  border: 0;
  box-shadow: none;
}
.cte-kpi-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  padding: 16px 18px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}
.cte-kpi-card.is-success {
  background: linear-gradient(180deg, #f6ffed 0%, var(--surface) 100%);
}
.cte-kpi-card.is-warning {
  background: linear-gradient(180deg, #fffbe6 0%, var(--surface) 100%);
}
.cte-kpi-label {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
}
.cte-kpi-value {
  color: var(--text);
  font-size: 20px;
  font-weight: 800;
  line-height: 28px;
  word-break: break-all;
}
.cte-modal-actions {
  position: sticky;
  bottom: 0;
  z-index: 2;
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin: 8px -16px 0;
  padding: 8px 16px;
  background: var(--surface);
  border-top: 1px solid var(--border-subtle);
}
.cte-section {
  margin-bottom: 8px;
}
.cte-section-sub {
  margin-left: auto;
  font-size: 12px;
  font-weight: 400;
  color: var(--muted);
}
.cte-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 6px;
}
.cte-toolbar-hint {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
}

/* Table footer */
.cte-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: flex-end;
  font-size: 12px;
  color: var(--text);
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
  font-size: 11px;
}
.cte-amount-check {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  padding-top: 0;
  color: var(--muted);
  font-size: 12px;
}
.cte-amount-check b {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
}

/* Empty */
.cte-empty {
  padding: 4px 0;
}
.cte-aside {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.cte-side-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}
.cte-side-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}
.cte-check-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle);
}
.cte-check-row:first-of-type {
  padding-top: 0;
  border-top: 0;
}
.cte-check-row strong {
  flex: 0 0 auto;
  font-size: 12px;
  line-height: 20px;
}
.cte-check-row.is-done strong {
  color: var(--success);
}
.cte-check-row.is-pending strong {
  color: var(--warning);
}
.cte-check-label {
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}
.cte-check-value {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.cte-note-list {
  margin: 0;
  padding-left: 18px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 20px;
}
.cte-note-list li + li {
  margin-top: 6px;
}
.project-target-redesign.cte-embedded :deep(.pt-panel) {
  border-radius: 6px;
}
.project-target-redesign.cte-embedded :deep(.pt-panel-header) {
  min-height: 34px;
  padding: 8px 12px;
  font-size: 13px;
  line-height: 18px;
}
.project-target-redesign.cte-embedded :deep(.pt-panel-body) {
  padding: 10px 12px;
}
.project-target-redesign.cte-embedded :deep(.pt-form-grid) {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px 10px;
}
.project-target-redesign.cte-embedded :deep(.ant-form-item) {
  margin-bottom: 8px;
}
.project-target-redesign.cte-embedded :deep(.ant-form-item-label) {
  padding-bottom: 2px;
}
.project-target-redesign.cte-embedded :deep(.ant-form-item-label > label) {
  height: 18px;
  font-size: 12px;
}
.project-target-redesign.cte-embedded :deep(.ant-input),
.project-target-redesign.cte-embedded :deep(.ant-input-number),
.project-target-redesign.cte-embedded :deep(.ant-select-selector),
.project-target-redesign.cte-embedded :deep(.ant-picker) {
  min-height: 30px;
  font-size: 12px;
}
.project-target-redesign.cte-embedded :deep(.ant-btn) {
  height: 30px;
  padding: 4px 10px;
  font-size: 12px;
}
.project-target-redesign.cte-embedded :deep(.ant-btn-sm) {
  height: 24px;
  padding: 1px 7px;
}
.project-target-redesign.cte-embedded :deep(.ant-table-thead > tr > th),
.project-target-redesign.cte-embedded :deep(.ant-table-tbody > tr > td),
.project-target-redesign.cte-embedded :deep(.ant-table-footer) {
  padding: 5px 8px;
  font-size: 12px;
}
.project-target-redesign.cte-embedded :deep(.ant-empty) {
  margin: 6px 0;
}
.project-target-redesign.cte-embedded :deep(.ant-empty-image) {
  height: 40px;
  margin-bottom: 4px;
}
.project-target-redesign.cte-embedded :deep(.ant-empty-description) {
  font-size: 12px;
}
.project-target-redesign.cte-embedded :deep(textarea.ant-input) {
  min-height: 48px;
}
@media (max-width: 1180px) {
  .cte-shell {
    grid-template-columns: 1fr;
  }

  .cte-kpi-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 768px) {
  .pt-page-head {
    flex-direction: column;
  }

  .cte-kpi-strip {
    grid-template-columns: 1fr;
  }
}
</style>
