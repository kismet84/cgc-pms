<script setup lang="ts">
import type {
  ContractItemRecord,
  ContractRecord,
  SiteFileRecord,
  SubcontractMeasureCommand,
  SubcontractMeasureItemCommand,
  SubcontractMeasureItemRecord,
  SubcontractMeasureRecord,
  SubcontractTaskCommand,
  SubcontractTaskRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { formatAmount, formatDecimal } from '@/pages/dashboard/model'
import {
  V2Badge,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  showToast,
} from '@/components'
import { loadContractItems, loadContractPage } from '@/services/commercial'
import { deleteSiteFile, listSiteFiles, uploadSiteFile } from '@/services/delivery'
import { isApiClientError } from '@/services/request'
import {
  createSubcontractMeasure,
  createSubcontractTask,
  deleteSubcontractMeasure,
  deleteSubcontractTask,
  loadSubcontractMeasure,
  loadSubcontractMeasureItems,
  loadSubcontractMeasures,
  loadSubcontractTask,
  loadSubcontractTasks,
  saveSubcontractMeasureItems,
  submitSubcontractMeasure,
  updateSubcontractMeasure,
  updateSubcontractTask,
} from '@/services/subcontract'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

type Mode = 'task' | 'measure'
type RecordRow = SubcontractTaskRecord | SubcontractMeasureRecord
type FormMode = 'create' | 'edit'
type PendingAction = 'delete-record' | 'submit-measure' | 'delete-file'

const route = useRoute()
const session = useSessionStore()
const workspace = useWorkspaceStore()
const records = ref<RecordRow[]>([])
const selected = ref<RecordRow | null>(null)
const measureItems = ref<SubcontractMeasureItemRecord[]>([])
const files = ref<SiteFileRecord[]>([])
const contracts = ref<ContractRecord[]>([])
const contractItems = ref<ContractItemRecord[]>([])
const taskCandidates = ref<SubcontractTaskRecord[]>([])
const itemDrafts = ref<SubcontractMeasureItemCommand[]>([])
const uploadFile = ref<File | null>(null)
const projectId = computed(() => workspace.selectedProjectId || '')
const status = ref('')
const pageNo = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
const detailLoading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const formOpen = ref(false)
const itemsOpen = ref(false)
const pendingAction = ref<PendingAction | null>(null)
const pendingFile = ref<SiteFileRecord | null>(null)
const formMode = ref<FormMode>('create')
const form = reactive<Record<string, string>>({})
let listController: AbortController | null = null
let detailController: AbortController | null = null
let candidateController: AbortController | null = null
let listGeneration = 0
let detailGeneration = 0
let candidateGeneration = 0

const mode = computed<Mode>(() => (route.path.endsWith('/measure') ? 'measure' : 'task'))
const title = computed(() => (mode.value === 'task' ? '分包任务' : '分包计量'))
const canQuery = computed(() =>
  session.hasPermission(mode.value === 'task' ? 'subtask:query' : 'subcontract:measure:query'),
)
const canAdd = computed(() =>
  session.hasPermission(mode.value === 'task' ? 'subtask:add' : 'subcontract:measure:add'),
)
const canEdit = computed(() =>
  session.hasPermission(mode.value === 'task' ? 'subtask:edit' : 'subcontract:measure:edit'),
)
const canDelete = computed(() =>
  session.hasPermission(mode.value === 'task' ? 'subtask:delete' : 'subcontract:measure:delete'),
)
const canSubmit = computed(
  () => mode.value === 'measure' && session.hasPermission('subcontract:measure:submit'),
)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const projectOptions = computed(() => workspace.projects)
const confirmationTitle = computed(() => {
  if (pendingAction.value === 'delete-record') return `删除${title.value}`
  if (pendingAction.value === 'submit-measure') return '提交分包计量'
  return '删除附件'
})
const confirmationDescription = computed(() => {
  if (pendingAction.value === 'delete-file') {
    return pendingFile.value ? `确认删除附件“${pendingFile.value.originalName}”？` : ''
  }
  if (!selected.value) return ''
  return pendingAction.value === 'submit-measure'
    ? `确认提交“${recordCode(selected.value)}”审批？`
    : `确认删除“${recordCode(selected.value)}”？`
})
const contractOptions = computed(() =>
  contracts.value.map((item) => ({
    value: item.id,
    label: `${item.contractCode || '未编号'} · ${item.contractName}`,
  })),
)
const taskOptions = computed(() =>
  taskCandidates.value
    .filter((item) => item.id !== selected.value?.id)
    .map((item) => ({ value: item.id, label: `${item.taskCode || '未编号'} · ${item.taskName}` })),
)
const contractItemOptions = computed(() =>
  contractItems.value
    .filter((item): item is ContractItemRecord & { id: string } => Boolean(item.id))
    .map((item) => ({
      value: item.id,
      label: `${item.itemCode || '未编号'} · ${item.itemName}`,
    })),
)
const statusOptions = computed(() =>
  mode.value === 'task'
    ? [
        { value: 'NOT_STARTED', label: '未开始' },
        { value: 'IN_PROGRESS', label: '进行中' },
        { value: 'COMPLETED', label: '已完成' },
        { value: 'SUSPENDED', label: '已暂停' },
      ]
    : [
        { value: 'DRAFT', label: '草稿' },
        { value: 'APPROVING', label: '审批中' },
        { value: 'APPROVED', label: '已通过' },
        { value: 'REJECTED', label: '已驳回' },
      ],
)
const selectedMeasure = computed(() =>
  selected.value && 'measureCode' in selected.value ? selected.value : null,
)
const selectedEditable = computed(() => {
  if (!selected.value || !canEdit.value) return false
  if ('measureCode' in selected.value) return ['DRAFT', 'REJECTED'].includes(selected.value.status)
  return selected.value.status !== 'COMPLETED'
})
const selectedSubmittable = computed(
  () =>
    selectedMeasure.value &&
    canSubmit.value &&
    ['DRAFT', 'REJECTED'].includes(selectedMeasure.value.status),
)

function errorText(error: unknown, fallback: string): string {
  if (isApiClientError(error)) return error.message
  return error instanceof Error ? error.message : fallback
}

function required(name: string, label: string): string {
  const value = form[name]?.trim() ?? ''
  if (!value) throw new TypeError(`${label}不能为空`)
  return value
}

function optional(name: string): string | null {
  return form[name]?.trim() || null
}

function decimal(value: string, label: string): string {
  const normalized = value.trim()
  if (!/^\d+(?:\.\d+)?$/.test(normalized)) throw new TypeError(`${label}必须为非负十进制数`)
  return normalized
}

function statusLabel(value?: string | null): string {
  const labels: Record<string, string> = {
    NOT_STARTED: '未开始',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    SUSPENDED: '已暂停',
    DRAFT: '草稿',
    APPROVING: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    WITHDRAWN: '已撤回',
  }
  return value ? (labels[value] ?? '未知状态') : '未知状态'
}

function recordCode(record: RecordRow): string {
  const code = 'taskCode' in record ? record.taskCode : record.measureCode
  return code && !/^\d{15,}$/.test(code) ? code : '编号待生成'
}

function recordName(record: RecordRow): string {
  return 'taskName' in record ? record.taskName : record.measurePeriod || '未填写计量期间'
}

function clearDetail(): void {
  detailController?.abort()
  selected.value = null
  measureItems.value = []
  files.value = []
  uploadFile.value = null
}

async function loadPage(): Promise<void> {
  if (!canQuery.value) return
  listController?.abort()
  detailController?.abort()
  const controller = new AbortController()
  listController = controller
  const generation = ++listGeneration
  selected.value = null
  measureItems.value = []
  files.value = []
  loading.value = true
  errorMessage.value = ''
  try {
    const query = {
      pageNo: pageNo.value,
      pageSize,
      projectId: projectId.value || undefined,
      status: status.value || undefined,
    }
    const page =
      mode.value === 'task'
        ? await loadSubcontractTasks(query, controller.signal)
        : await loadSubcontractMeasures(query, controller.signal)
    if (generation !== listGeneration) return
    records.value = page.records
    total.value = page.total
  } catch (error) {
    if (!controller.signal.aborted && generation === listGeneration) {
      records.value = []
      total.value = 0
      errorMessage.value = errorText(error, `${title.value}加载失败`)
      showToast('error', `${title.value}读取失败`, errorMessage.value)
    }
  } finally {
    if (generation === listGeneration) loading.value = false
  }
}

async function selectRecord(record: RecordRow): Promise<boolean> {
  return selectRecordById(record.id, record)
}

async function selectRecordById(
  id: string,
  initial: RecordRow | null = null,
  notifyError = true,
): Promise<boolean> {
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const generation = ++detailGeneration
  selected.value = initial
  measureItems.value = []
  files.value = []
  detailLoading.value = true
  try {
    if (mode.value === 'task') {
      const detail = await loadSubcontractTask(id, controller.signal)
      if (generation === detailGeneration) selected.value = detail
    } else {
      const [detail, items, nextFiles] = await Promise.all([
        loadSubcontractMeasure(id, controller.signal),
        loadSubcontractMeasureItems(id, controller.signal),
        listSiteFiles('SUBCONTRACT', id, controller.signal),
      ])
      if (generation !== detailGeneration) return false
      selected.value = detail
      measureItems.value = items
      files.value = nextFiles
    }
    return generation === detailGeneration
  } catch (error) {
    if (!controller.signal.aborted && generation === detailGeneration) {
      selected.value = null
      if (notifyError) showToast('error', '详情读取失败', errorText(error, '详情加载失败'))
    }
    return false
  } finally {
    if (generation === detailGeneration) detailLoading.value = false
  }
}

async function loadCandidates(candidateProjectId: string, contractId = ''): Promise<void> {
  candidateController?.abort()
  const controller = new AbortController()
  candidateController = controller
  const generation = ++candidateGeneration
  contracts.value = []
  contractItems.value = []
  taskCandidates.value = []
  if (!candidateProjectId) return
  try {
    const contractPage = await loadContractPage(
      { pageNo: 1, pageSize: 200, projectId: candidateProjectId, contractType: 'SUB' },
      controller.signal,
    )
    if (generation !== candidateGeneration) return
    contracts.value = contractPage.records
    if (contractId && contracts.value.some((item) => item.id === contractId)) {
      await changeContract(contractId, controller.signal, generation)
    }
  } catch (error) {
    if (!controller.signal.aborted && generation === candidateGeneration)
      showToast('error', '业务候选读取失败', errorText(error, '合同候选读取失败'))
  }
}

async function changeProject(value: string): Promise<void> {
  form.projectId = value
  form.contractId = ''
  form.partnerId = ''
  form.predecessorTaskId = ''
  form.subTaskId = ''
  await loadCandidates(value)
}

async function changeContract(
  value: string,
  signal = candidateController?.signal,
  generation = candidateGeneration,
): Promise<void> {
  form.contractId = value
  const contract = contracts.value.find((item) => item.id === value)
  form.partnerId = contract?.partyBId || ''
  form.partnerName = contract?.partyBName || ''
  form.predecessorTaskId = ''
  form.subTaskId = ''
  contractItems.value = []
  taskCandidates.value = []
  if (!value || !form.projectId) return
  const [items, tasks] = await Promise.all([
    loadContractItems(value, signal),
    loadSubcontractTasks(
      {
        pageNo: 1,
        pageSize: 200,
        projectId: form.projectId,
        contractId: value,
        partnerId: form.partnerId || undefined,
      },
      signal,
    ),
  ])
  if (generation !== candidateGeneration || form.contractId !== value) return
  contractItems.value = items
  taskCandidates.value = tasks.records
}

async function openForm(record?: RecordRow): Promise<void> {
  for (const key of Object.keys(form)) delete form[key]
  formMode.value = record ? 'edit' : 'create'
  const fallbackProject = projectId.value || workspace.selectedProjectId || ''
  if (mode.value === 'task') {
    const task = record && 'taskCode' in record ? record : null
    Object.assign(form, {
      projectId: task?.projectId || fallbackProject,
      contractId: task?.contractId || '',
      partnerId: task?.partnerId || '',
      partnerName: task?.partnerName || '',
      predecessorTaskId: task?.predecessorTaskId || '',
      taskName: task?.taskName || '',
      workArea: task?.workArea || '',
      plannedStartDate: task?.plannedStartDate || '',
      plannedEndDate: task?.plannedEndDate || '',
      actualStartDate: task?.actualStartDate || '',
      actualEndDate: task?.actualEndDate || '',
      progressPercent: task?.progressPercent || '0',
      status: task?.status || 'NOT_STARTED',
      remark: task?.remark || '',
    })
  } else {
    const measure = record && 'measureCode' in record ? record : null
    Object.assign(form, {
      projectId: measure?.projectId || fallbackProject,
      contractId: measure?.contractId || '',
      partnerId: measure?.partnerId || '',
      partnerName: measure?.partnerName || '',
      subTaskId: measure?.subTaskId || '',
      measurePeriod: measure?.measurePeriod || new Date().toISOString().slice(0, 7),
      measureDate: measure?.measureDate || new Date().toISOString().slice(0, 10),
      status: measure?.status || 'DRAFT',
      remark: measure?.remark || '',
    })
  }
  formOpen.value = true
  busy.value = true
  try {
    await loadCandidates(form.projectId, form.contractId)
  } finally {
    busy.value = false
  }
}

function taskCommand(): SubcontractTaskCommand {
  return {
    projectId: required('projectId', '项目'),
    contractId: required('contractId', '分包合同'),
    partnerId: required('partnerId', '分包单位'),
    predecessorTaskId: optional('predecessorTaskId'),
    taskName: required('taskName', '任务名称'),
    workArea: optional('workArea'),
    plannedStartDate: optional('plannedStartDate'),
    plannedEndDate: optional('plannedEndDate'),
    actualStartDate: optional('actualStartDate'),
    actualEndDate: optional('actualEndDate'),
    progressPercent: decimal(form.progressPercent || '0', '进度'),
    status: required('status', '状态'),
    remark: optional('remark'),
  }
}

function measureCommand(): SubcontractMeasureCommand {
  return {
    projectId: required('projectId', '项目'),
    contractId: required('contractId', '分包合同'),
    partnerId: required('partnerId', '分包单位'),
    subTaskId: optional('subTaskId'),
    measurePeriod: required('measurePeriod', '计量期间'),
    measureDate: required('measureDate', '计量日期'),
    status: required('status', '状态'),
    remark: optional('remark'),
  }
}

async function saveForm(): Promise<void> {
  if (busy.value) return
  busy.value = true
  try {
    const editingId = formMode.value === 'edit' ? selected.value?.id : undefined
    let id: string
    if (mode.value === 'task') {
      const command = taskCommand()
      if (editingId) {
        await updateSubcontractTask(editingId, command)
        id = editingId
      } else {
        id = await createSubcontractTask(command)
      }
    } else {
      const command = measureCommand()
      if (editingId) {
        await updateSubcontractMeasure(editingId, command)
        id = editingId
      } else {
        id = await createSubcontractMeasure(command)
      }
    }
    formOpen.value = false
    await loadPage()
    const reread = await selectRecordById(id, null, false)
    if (!reread) {
      showToast('warning', `${title.value}已保存，结果未确认`, '暂未取得最新结果，请刷新重试。')
      return
    }
    showToast('success', `${title.value}已保存`, '最新数据已加载。')
  } catch (error) {
    showToast('error', `${title.value}保存失败`, errorText(error, '保存失败'))
  } finally {
    busy.value = false
  }
}

function removeSelected(): void {
  if (selected.value) pendingAction.value = 'delete-record'
}

async function confirmAction(): Promise<void> {
  const action = pendingAction.value
  const record = selected.value
  const file = pendingFile.value
  if (!action || !record || busy.value) return
  busy.value = true
  try {
    if (action === 'delete-record') {
      if (mode.value === 'task') await deleteSubcontractTask(record.id)
      else await deleteSubcontractMeasure(record.id)
      pendingAction.value = null
      clearDetail()
      await loadPage()
      showToast('success', `${title.value}已删除`, '列表已刷新。')
      return
    }
    if (action === 'submit-measure' && 'measureCode' in record) {
      await submitSubcontractMeasure(record.id)
      pendingAction.value = null
      await loadPage()
      const reread = await selectRecordById(record.id, null, false)
      if (!reread) {
        showToast('warning', '分包计量已提交，结果未确认', '暂未取得最新结果，请刷新重试。')
        return
      }
      showToast('success', '分包计量已提交', '审批与业务状态已更新。')
      return
    }
    if (action === 'delete-file' && file) {
      await deleteSiteFile(file.id)
      pendingAction.value = null
      pendingFile.value = null
      const reread = await selectRecordById(record.id, null, false)
      if (!reread) {
        showToast('warning', '附件已删除，结果未确认', '暂未取得最新结果，请刷新重试。')
        return
      }
      showToast('success', '附件已删除', '附件列表已更新。')
    }
  } catch (error) {
    const fallback = action === 'submit-measure' ? '提交失败' : '删除失败'
    showToast('error', fallback, errorText(error, fallback))
  } finally {
    busy.value = false
    pendingAction.value = null
    pendingFile.value = null
  }
}

async function openItems(): Promise<void> {
  const measure = selectedMeasure.value
  if (!measure?.contractId) return
  itemDrafts.value = measureItems.value.map((item) => ({
    contractItemId: item.contractItemId,
    currentQuantity: item.currentQuantity,
  }))
  busy.value = true
  try {
    contractItems.value = await loadContractItems(measure.contractId)
    itemsOpen.value = true
  } catch (error) {
    showToast('error', '合同清单读取失败', errorText(error, '合同清单读取失败'))
  } finally {
    busy.value = false
  }
}

function addItemDraft(): void {
  const first = contractItemOptions.value.find(
    (item) => !itemDrafts.value.some((draft) => draft.contractItemId === item.value),
  )
  if (first) itemDrafts.value.push({ contractItemId: first.value, currentQuantity: '0' })
}

function changeItem(index: number, value: string): void {
  const row = itemDrafts.value[index]
  if (row) row.contractItemId = value
}

function changeItemQuantity(index: number, value: string): void {
  const row = itemDrafts.value[index]
  if (row) row.currentQuantity = value
}

async function saveItems(): Promise<void> {
  const measure = selectedMeasure.value
  if (!measure || busy.value) return
  busy.value = true
  try {
    const ids = new Set<string>()
    const commands = itemDrafts.value.map((item) => {
      if (!item.contractItemId) throw new TypeError('合同清单不能为空')
      if (ids.has(item.contractItemId)) throw new TypeError('合同清单不能重复')
      ids.add(item.contractItemId)
      return {
        contractItemId: item.contractItemId,
        currentQuantity: decimal(item.currentQuantity, '本期数量'),
      }
    })
    await saveSubcontractMeasureItems(measure.id, commands)
    itemsOpen.value = false
    const reread = await selectRecordById(measure.id, null, false)
    if (!reread) {
      showToast('warning', '计量清单已保存，结果未确认', '暂未取得最新结果，请刷新重试。')
      return
    }
    showToast('success', '计量清单已保存', '数量与金额已更新。')
  } catch (error) {
    showToast('error', '计量清单保存失败', errorText(error, '保存失败'))
  } finally {
    busy.value = false
  }
}

function submitSelected(): void {
  const measure = selectedMeasure.value
  if (measure && !busy.value) pendingAction.value = 'submit-measure'
}

function chooseFile(event: Event): void {
  uploadFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function uploadAttachment(): Promise<void> {
  const measure = selectedMeasure.value
  if (!measure || !uploadFile.value || busy.value) return
  busy.value = true
  try {
    await uploadSiteFile(uploadFile.value, 'SUBCONTRACT', measure.id, 'MEASURE_SUPPORT')
    uploadFile.value = null
    const reread = await selectRecordById(measure.id, null, false)
    if (!reread) {
      showToast('warning', '附件已上传，结果未确认', '暂未取得最新结果，请刷新重试。')
      return
    }
    showToast('success', '附件已上传', '附件列表已更新。')
  } catch (error) {
    showToast('error', '附件上传失败', errorText(error, '上传失败'))
  } finally {
    busy.value = false
  }
}

function removeFile(file: SiteFileRecord): void {
  if (selectedMeasure.value) {
    pendingFile.value = file
    pendingAction.value = 'delete-file'
  }
}

function changePage(next: number): void {
  if (next < 1 || next > pageCount.value || next === pageNo.value) return
  pageNo.value = next
  void loadPage()
}

watch(
  [mode, projectId, status],
  () => {
    pageNo.value = 1
    void loadPage()
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  listController?.abort()
  detailController?.abort()
  candidateController?.abort()
})
</script>

<template>
  <section class="subcontract-workspace">
    <V2PageState
      v-if="!canQuery"
      kind="forbidden"
      :title="`无权访问${title}`"
      description="系统未加载分包业务数据。"
    />
    <template v-else>
      <V2Card :title="title" :heading-level="1">
        <template #actions>
          <div class="subcontract-workspace__filters">
            <V2Select
              v-model="status"
              label="状态"
              hide-label
              allow-empty
              :options="statusOptions"
              placeholder="全部状态"
            />
            <V2Button v-if="canAdd" size="small" @click="openForm()">新建{{ title }}</V2Button>
          </div>
        </template>
      </V2Card>

      <V2PageState
        v-if="loading && !errorMessage && !records.length"
        kind="loading"
        title="正在加载"
        :description="`正在读取${title}。`"
      />
      <V2PageState
        v-else-if="!loading && !errorMessage && !records.length"
        :title="`暂无${title}`"
        description="当前项目和状态范围没有可访问记录。"
      >
        <template v-if="canAdd" #actions>
          <V2Button @click="openForm()">新建{{ title }}</V2Button>
        </template>
      </V2PageState>
      <V2Card v-else :heading-level="2">
        <div
          class="subcontract-workspace__table-wrap"
          role="region"
          :aria-label="`${title}列表`"
          tabindex="0"
        >
          <table>
            <thead>
              <tr>
                <th>编号</th>
                <th>{{ mode === 'task' ? '任务名称' : '计量期间' }}</th>
                <th>项目</th>
                <th>合同</th>
                <th>分包单位</th>
                <th v-if="mode === 'task'">进度</th>
                <th v-else>报量金额</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in records" :key="record.id">
                <td>
                  <V2Button
                    size="small"
                    variant="ghost"
                    class="v2-table__record-link"
                    @click="selectRecord(record)"
                  >
                    {{ recordCode(record) }}
                  </V2Button>
                </td>
                <td>{{ recordName(record) }}</td>
                <td>{{ record.projectName || '项目信息缺失' }}</td>
                <td>{{ record.contractName || '合同信息缺失' }}</td>
                <td>{{ record.partnerName || '分包单位信息缺失' }}</td>
                <td v-if="'taskCode' in record">{{ formatDecimal(record.progressPercent) }}%</td>
                <td v-else>{{ formatAmount(record.reportedAmount) }}</td>
                <td>
                  <V2Badge>{{ statusLabel(record.status) }}</V2Badge>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <nav class="subcontract-workspace__pagination" aria-label="分包业务分页">
            <span>共 {{ total }} 条</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="pageNo <= 1 || loading"
              @click="changePage(pageNo - 1)"
              >上一页</V2Button
            >
            <span>第 {{ pageNo }} 页</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="pageNo >= pageCount || loading"
              @click="changePage(pageNo + 1)"
              >下一页</V2Button
            >
          </nav>
        </template>
      </V2Card>

      <V2Dialog
        :open="Boolean(selected)"
        :title="`${title}详情`"
        :description="selected ? recordCode(selected) : ''"
        panel-class="v2-detail-dialog"
        :close-on-backdrop="!selectedEditable"
        @close="clearDetail"
      >
        <V2PageState
          v-if="detailLoading"
          kind="loading"
          title="正在读取详情"
          description="请稍候。"
        />
        <template v-else-if="selected">
          <dl class="subcontract-workspace__facts v2-detail-dialog__facts">
            <div>
              <dt>编号</dt>
              <dd>{{ recordCode(selected) }}</dd>
            </div>
            <div>
              <dt>项目</dt>
              <dd>{{ selected.projectName || '项目信息缺失' }}</dd>
            </div>
            <div>
              <dt>合同</dt>
              <dd>{{ selected.contractName || '合同信息缺失' }}</dd>
            </div>
            <div>
              <dt>分包单位</dt>
              <dd>{{ selected.partnerName || '分包单位信息缺失' }}</dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd>{{ statusLabel(selected.status) }}</dd>
            </div>
            <template v-if="'taskCode' in selected">
              <div>
                <dt>任务名称</dt>
                <dd>{{ selected.taskName }}</dd>
              </div>
              <div>
                <dt>施工区域</dt>
                <dd>{{ selected.workArea || '—' }}</dd>
              </div>
              <div>
                <dt>进度</dt>
                <dd>{{ formatDecimal(selected.progressPercent) }}%</dd>
              </div>
              <div>
                <dt>计划周期</dt>
                <dd>
                  {{ selected.plannedStartDate || '—' }} 至 {{ selected.plannedEndDate || '—' }}
                </dd>
              </div>
              <div>
                <dt>实际周期</dt>
                <dd>
                  {{ selected.actualStartDate || '—' }} 至 {{ selected.actualEndDate || '—' }}
                </dd>
              </div>
              <div>
                <dt>前置任务</dt>
                <dd>{{ selected.predecessorTaskName || '无' }}</dd>
              </div>
            </template>
            <template v-else>
              <div>
                <dt>计量期间</dt>
                <dd>{{ selected.measurePeriod || '—' }}</dd>
              </div>
              <div>
                <dt>计量日期</dt>
                <dd>{{ selected.measureDate || '—' }}</dd>
              </div>
              <div>
                <dt>关联任务</dt>
                <dd>{{ selected.subTaskName || '未关联' }}</dd>
              </div>
              <div>
                <dt>报量金额</dt>
                <dd>{{ formatAmount(selected.reportedAmount) }}</dd>
              </div>
              <div>
                <dt>审定金额</dt>
                <dd>{{ formatAmount(selected.approvedAmount) }}</dd>
              </div>
              <div>
                <dt>扣款金额</dt>
                <dd>{{ formatAmount(selected.deductionAmount) }}</dd>
              </div>
              <div>
                <dt>净额</dt>
                <dd>{{ formatAmount(selected.netAmount) }}</dd>
              </div>
              <div>
                <dt>审批状态</dt>
                <dd>{{ statusLabel(selected.approvalStatus) }}</dd>
              </div>
            </template>
          </dl>
          <template v-if="selectedMeasure">
            <h3>计量清单</h3>
            <V2PageState
              v-if="!errorMessage && !measureItems.length"
              title="暂无计量清单"
              description="草稿状态可维护合同清单与本期数量。"
              :heading-level="3"
            />
            <div
              v-else-if="measureItems.length"
              class="subcontract-workspace__table-wrap"
              role="region"
              aria-label="计量清单"
              tabindex="0"
            >
              <table>
                <thead>
                  <tr>
                    <th>清单项</th>
                    <th>单位</th>
                    <th>合同数量</th>
                    <th>本期数量</th>
                    <th>累计数量</th>
                    <th>单价</th>
                    <th>金额</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(item, index) in measureItems"
                    :key="item.id || `${item.contractItemId}-${index}`"
                  >
                    <td>{{ item.itemName }}</td>
                    <td>{{ item.unit || '—' }}</td>
                    <td>{{ formatDecimal(item.contractQuantity) }}</td>
                    <td>{{ formatDecimal(item.currentQuantity) }}</td>
                    <td>{{ formatDecimal(item.cumulativeQuantity) }}</td>
                    <td>{{ formatAmount(item.unitPrice) }}</td>
                    <td>{{ formatAmount(item.amount) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <h3>提交附件</h3>
            <ul v-if="files.length" class="subcontract-workspace__files">
              <li v-for="file in files" :key="file.id">
                <span>{{ file.originalName }}</span>
                <V2Button
                  v-if="selectedEditable"
                  type="button"
                  size="small"
                  variant="ghost"
                  @click="removeFile(file)"
                  >删除</V2Button
                >
              </li>
            </ul>
            <V2PageState
              v-else-if="!errorMessage"
              title="暂无附件"
              description="提交前需上传通过安全扫描的附件。"
              :heading-level="3"
            />
            <div v-if="selectedEditable" class="subcontract-workspace__upload">
              <input
                class="v2-file-input"
                type="file"
                aria-label="选择计量附件"
                @change="chooseFile"
              />
              <V2Button
                size="small"
                type="button"
                :disabled="!uploadFile"
                :loading="busy"
                @click="uploadAttachment"
                >上传附件</V2Button
              >
            </div>
          </template>
        </template>
        <template #footer>
          <V2Button
            v-if="selectedEditable"
            type="button"
            variant="secondary"
            @click="openForm(selected)"
            >编辑</V2Button
          >
          <V2Button
            v-if="selectedMeasure && selectedEditable"
            type="button"
            variant="secondary"
            @click="openItems"
            >维护计量清单</V2Button
          >
          <V2Button
            v-if="canDelete"
            type="button"
            variant="danger"
            :loading="busy"
            @click="removeSelected"
            >删除</V2Button
          >
          <V2Button v-if="selectedSubmittable" type="button" :loading="busy" @click="submitSelected"
            >提交审批</V2Button
          >
        </template>
      </V2Dialog>

      <V2Dialog
        v-model:open="formOpen"
        :title="`${formMode === 'create' ? '新建' : '编辑'}${title}`"
        description="保存后刷新状态、数量与金额。"
        :close-disabled="busy"
        :close-on-backdrop="false"
      >
        <form
          id="subcontract-workspace-editor-form"
          class="subcontract-workspace__form"
          @submit.prevent="saveForm"
        >
          <V2Select
            v-model="form.projectId"
            label="项目"
            :options="projectOptions"
            :disabled="busy"
            required
            @update:model-value="changeProject"
          />
          <V2Select
            v-model="form.contractId"
            label="分包合同"
            :options="contractOptions"
            :disabled="busy || !form.projectId"
            required
            @update:model-value="changeContract"
          />
          <V2Input v-model="form.partnerName" label="分包单位" hint="由所选分包合同确定" disabled />
          <template v-if="mode === 'task'">
            <V2Select
              v-model="form.predecessorTaskId"
              label="前置任务"
              :options="taskOptions"
              allow-empty
              :disabled="busy || !form.contractId"
            />
            <V2Input v-model="form.taskName" label="任务名称" required />
            <V2Input v-model="form.workArea" label="施工区域" />
            <V2Input
              v-model="form.plannedStartDate"
              label="计划开始日期"
              placeholder="YYYY-MM-DD"
            />
            <V2Input v-model="form.plannedEndDate" label="计划结束日期" placeholder="YYYY-MM-DD" />
            <V2Input v-model="form.actualStartDate" label="实际开始日期" placeholder="YYYY-MM-DD" />
            <V2Input v-model="form.actualEndDate" label="实际结束日期" placeholder="YYYY-MM-DD" />
            <V2Input
              v-model="form.progressPercent"
              label="进度百分比"
              :decimal-scale="2"
              required
            />
          </template>
          <template v-else>
            <V2Select
              v-model="form.subTaskId"
              label="关联任务"
              :options="taskOptions"
              allow-empty
              :disabled="busy || !form.contractId"
            />
            <V2Input v-model="form.measurePeriod" label="计量期间" placeholder="YYYY-MM" required />
            <V2Input
              v-model="form.measureDate"
              label="计量日期"
              placeholder="YYYY-MM-DD"
              required
            />
          </template>
          <V2Select v-model="form.status" label="状态" :options="statusOptions" required />
          <V2Input v-model="form.remark" label="备注" />
        </form>
        <template #footer>
          <V2Button type="button" variant="secondary" :disabled="busy" @click="formOpen = false"
            >取消</V2Button
          >
          <V2Button type="submit" form="subcontract-workspace-editor-form" :loading="busy">
            保存
          </V2Button>
        </template>
      </V2Dialog>

      <V2Dialog
        v-model:open="itemsOpen"
        title="维护计量清单"
        description="仅录入合同清单与本期数量；累计数量、单价、金额自动计算。"
        :close-disabled="busy"
        :close-on-backdrop="false"
      >
        <form
          id="subcontract-workspace-items-form"
          class="subcontract-workspace__form"
          @submit.prevent="saveItems"
        >
          <div
            v-for="(item, index) in itemDrafts"
            :key="index"
            class="subcontract-workspace__item-row"
          >
            <V2Select
              :model-value="item.contractItemId"
              label="合同清单"
              :options="contractItemOptions"
              required
              @update:model-value="changeItem(index, $event)"
            />
            <V2Input
              :model-value="item.currentQuantity"
              :decimal-scale="2"
              label="本期数量"
              required
              @update:model-value="changeItemQuantity(index, $event)"
            />
            <V2Button
              type="button"
              size="small"
              variant="ghost"
              @click="itemDrafts.splice(index, 1)"
              >移除</V2Button
            >
          </div>
          <V2Button
            type="button"
            size="small"
            variant="secondary"
            :disabled="itemDrafts.length >= contractItemOptions.length"
            @click="addItemDraft"
            >添加清单项</V2Button
          >
        </form>
        <template #footer>
          <V2Button type="button" variant="secondary" :disabled="busy" @click="itemsOpen = false"
            >取消</V2Button
          >
          <V2Button type="submit" form="subcontract-workspace-items-form" :loading="busy">
            保存清单
          </V2Button>
        </template>
      </V2Dialog>

      <V2ConfirmDialog
        :open="pendingAction !== null"
        :title="confirmationTitle"
        :description="confirmationDescription"
        :confirm-text="pendingAction === 'submit-measure' ? '确认提交' : '确认删除'"
        :danger="pendingAction !== 'submit-measure'"
        :loading="busy"
        @close="pendingAction = null"
        @confirm="confirmAction"
      />
    </template>
  </section>
</template>

<style scoped>
.subcontract-workspace {
  display: grid;
  gap: var(--v2-space-4);
}
.subcontract-workspace__filters,
.subcontract-workspace__actions,
.subcontract-workspace__upload,
.subcontract-workspace__pagination {
  display: flex;
  align-items: center;
  gap: var(--v2-space-2);
  flex-wrap: wrap;
}
.subcontract-workspace__table-wrap {
  overflow-x: auto;
}
.subcontract-workspace__pagination {
  justify-content: flex-end;
}
.subcontract-workspace__facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--v2-space-8);
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}
.subcontract-workspace__form {
  display: grid;
  gap: var(--v2-space-3);
}
.subcontract-workspace__item-row {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr) auto;
  gap: var(--v2-space-2);
  align-items: end;
}
.subcontract-workspace__files {
  display: grid;
  gap: var(--v2-space-2);
  padding: 0;
  list-style: none;
}
.subcontract-workspace__files li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-2);
}
@media (max-width: 720px) {
  .subcontract-workspace__facts {
    grid-template-columns: 1fr;
  }
  .subcontract-workspace__item-row {
    grid-template-columns: 1fr;
  }
}
</style>
