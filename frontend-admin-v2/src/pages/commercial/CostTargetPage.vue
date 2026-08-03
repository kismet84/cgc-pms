<script setup lang="ts">
import type {
  CostTargetItemRecord,
  CostBudgetDraftSaveCommand,
  CostTargetQuery,
  CostTargetRecord,
  CostTargetSaveCommand,
  CostTargetDefaultAllocation,
  ProjectBudgetRecord,
  BudgetAvailabilityRecord,
  ProjectContextOption,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatAmount } from '@/pages/dashboard/model'
import {
  V2ActionMenu,
  V2Badge,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  showToast,
  useToastMessage,
} from '@/components'
import {
  deleteCostTarget,
  loadCostSubjectOptions,
  loadCostTarget,
  loadCostTargetItems,
  loadCostTargetDefaultAllocation,
  loadCostTargetPage,
  loadBudgetAvailability,
  loadBudgetPage,
  loadProjectContextOptions,
  saveCostBudgetDraft,
  submitCostTarget,
} from '@/services/commercial'
import type { CostSubjectOption } from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { loadProjectUsers } from '@/services/projects'
import { useSessionStore } from '@/stores/session'

type PendingAction = 'delete' | 'submit' | null
type CostBudgetEditorForm = CostTargetSaveCommand & {
  projectManagerId: string
  sourceContractAmount: string
  targetCostRate: string
}

const APPROVAL_OPTIONS = [
  { value: '', label: '全部审批状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'APPROVING', label: '审批中' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
]
const ACTIVE_OPTIONS = [
  { value: '', label: '全部版本' },
  { value: '1', label: '当前活动版本' },
  { value: '0', label: '历史版本' },
]
const DECIMAL_PATTERN = /^\d+(?:\.\d+)?$/

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const { embedded = false } = defineProps<{ embedded?: boolean }>()

const filter = reactive<CostTargetQuery>({ pageNo: 1, pageSize: 10 })
const records = ref<CostTargetRecord[]>([])
const total = ref(0)
const projects = ref<ProjectContextOption[]>([])
const costSubjects = ref<CostSubjectOption[]>([])
const responsibleUsers = ref<Array<{ value: string; label: string }>>([])
const detail = ref<CostTargetRecord | null>(null)
const items = ref<CostTargetItemRecord[]>([])
const executionBudget = ref<ProjectBudgetRecord | null>(null)
const budgetAvailability = ref<BudgetAvailabilityRecord[]>([])
const form = reactive<CostBudgetEditorForm>(emptyForm())
const loading = ref(false)
const detailLoading = ref(false)
const actionBusy = ref(false)
const errorMessage = ref('')
const successMessage = useToastMessage()

watch(errorMessage, (message) => {
  if (message) showToast('error', '项目成本预算操作未完成', message)
})
const detailOpen = ref(false)
const pendingAction = ref<PendingAction>(null)

let listGeneration = 0
let detailGeneration = 0
let listController: AbortController | null = null
let detailController: AbortController | null = null
let projectController: AbortController | null = null
let allocationController: AbortController | null = null

const mode = computed<'list' | 'create' | 'edit'>(() => {
  if (route.path.endsWith('/create')) return 'create'
  if (route.path.endsWith('/edit')) return 'edit'
  return 'list'
})
const routeId = computed(() => String(route.params.id ?? '').trim())
const canQuery = computed(() => session.hasPermission('cost:target:query'))
const canAdd = computed(() => session.hasPermission('cost:target:add'))
const canEdit = computed(() => session.hasPermission('cost:target:edit'))
const canDelete = computed(() => session.hasPermission('cost:target:delete'))
const canSubmit = computed(() => session.hasPermission('cost:target:submit'))
const canSaveDraft = computed(() => (mode.value === 'create' ? canAdd.value : canEdit.value))
const editable = computed(
  () =>
    mode.value === 'create' ||
    (!!detail.value &&
      detail.value.isActive !== 1 &&
      ['DRAFT', 'REJECTED'].includes(detail.value.approvalStatus)),
)
const fixedTargetVersion = computed(
  () => mode.value === 'create' || Boolean(detail.value?.sourceContractAmount),
)
const targetDifference = computed(() => amountDifference('targetAmount'))
const responsibilityDifference = computed(() => amountDifference('responsibilityAmount'))
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / (filter.pageSize ?? 10))))
const projectOptions = computed(() =>
  projects.value.map((project) => ({ value: project.id, label: project.projectName })),
)
const costSubjectOptions = computed(() => {
  const options = costSubjects.value.map((subject) => ({
    value: subject.id,
    label: `${subject.subjectCode} · ${subject.subjectName}`,
  }))
  for (const [index, item] of items.value.entries()) {
    if (item.costSubjectId && !options.some((option) => option.value === item.costSubjectId)) {
      options.push({
        value: item.costSubjectId,
        label: `成本科目名称缺失（第 ${index + 1} 行）`,
      })
    }
  }
  return options
})
const costSubjectLabel = (id: string, index: number) => {
  const subject = costSubjects.value.find((item) => item.id === id)
  return subject
    ? `${subject.subjectCode} · ${subject.subjectName}`
    : `成本科目名称缺失（第 ${index + 1} 行）`
}
const availabilityBySubject = computed(
  () => new Map(budgetAvailability.value.map((row) => [row.costSubjectId, row])),
)

function projectLabel(projectId?: string | null): string {
  return projects.value.find((project) => project.id === projectId)?.projectName ?? '项目名称缺失'
}

function emptyForm(): CostBudgetEditorForm {
  return {
    projectId: '',
    projectManagerId: '',
    versionNo: '',
    versionName: '',
    totalTargetAmount: '',
    totalBidCostAmount: '',
    totalResponsibilityAmount: '',
    effectiveDate: null,
    version: null,
    remark: null,
    sourceContractAmount: '',
    targetCostRate: '',
  }
}

function blankItem(): CostTargetItemRecord {
  return {
    costSubjectId: '',
    targetAmount: '',
    bidCostAmount: '',
    responsibilityAmount: '',
    responsibleUserId: null,
    responsibilityUnit: null,
    remark: null,
  }
}

function errorText(error: unknown, fallback: string): string {
  if (isApiClientError(error)) return error.message
  return error instanceof Error ? error.message : fallback
}

function resetNotices(): void {
  errorMessage.value = ''
  successMessage.value = ''
}

function hydrateFilter(): void {
  const pageNo = typeof route.query.pageNo === 'string' ? Number(route.query.pageNo) : 1
  filter.pageNo = Number.isInteger(pageNo) && pageNo > 0 ? pageNo : 1
  filter.pageSize = 10
  filter.projectId = typeof route.query.projectId === 'string' ? route.query.projectId : undefined
  filter.versionNo = typeof route.query.versionNo === 'string' ? route.query.versionNo : undefined
  filter.approvalStatus =
    typeof route.query.approvalStatus === 'string' ? route.query.approvalStatus : undefined
  filter.isActive = typeof route.query.isActive === 'string' ? route.query.isActive : undefined
}

async function replaceQuery(): Promise<boolean> {
  const location = {
    path: route.path === '/cost-budget' ? '/cost-budget' : '/cost-target/index',
    query: {
      ...route.query,
      view: undefined,
      versionNo: filter.versionNo?.trim() || undefined,
      approvalStatus: filter.approvalStatus || undefined,
      isActive:
        filter.isActive !== undefined && filter.isActive !== ''
          ? String(filter.isActive)
          : undefined,
      pageNo: filter.pageNo && filter.pageNo > 1 ? String(filter.pageNo) : undefined,
    },
    hash: route.hash,
  }
  if (router.resolve(location).fullPath === route.fullPath) return false
  await router.replace(location)
  return true
}

async function loadProjects(): Promise<void> {
  projectController?.abort()
  const controller = new AbortController()
  projectController = controller
  try {
    const [value, subjects] = await Promise.all([
      loadProjectContextOptions(controller.signal),
      loadCostSubjectOptions(controller.signal),
    ])
    if (projectController !== controller) return
    projects.value = value
    costSubjects.value = subjects
    if (mode.value === 'list') {
      responsibleUsers.value = []
      return
    }
    const users = await loadProjectUsers(controller.signal)
    if (projectController !== controller) return
    responsibleUsers.value = users.records
      .filter((user) => user.status === 'ENABLE')
      .map((user) => ({ value: user.id, label: user.realName || user.username }))
    if (form.projectId && mode.value === 'create') await selectProject(form.projectId)
    else if (form.projectId && !form.projectManagerId)
      form.projectManagerId =
        projects.value.find((project) => project.id === form.projectId)?.projectManagerId ?? ''
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = errorText(error, '可见项目加载失败')
  }
}

async function loadList(preserveNotice = false): Promise<void> {
  if (!canQuery.value) return
  hydrateFilter()
  listController?.abort()
  const controller = new AbortController()
  listController = controller
  const generation = ++listGeneration
  loading.value = true
  if (!preserveNotice) resetNotices()
  try {
    const page = await loadCostTargetPage({ ...filter }, controller.signal)
    if (generation !== listGeneration) return
    records.value = page.records
    total.value = page.total
  } catch (error) {
    if (!controller.signal.aborted && generation === listGeneration) {
      records.value = []
      total.value = 0
      errorMessage.value = errorText(error, '项目成本预算加载失败')
    }
  } finally {
    if (generation === listGeneration) loading.value = false
  }
}

async function loadDetail(id: string, preserveNotice = false): Promise<void> {
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const generation = ++detailGeneration
  detailLoading.value = true
  if (!preserveNotice) resetNotices()
  try {
    const [target, targetItems] = await Promise.all([
      loadCostTarget(id, controller.signal),
      loadCostTargetItems(id, controller.signal),
    ])
    if (generation !== detailGeneration) return
    detail.value = target
    items.value = targetItems.map((item) => ({ ...item }))
    executionBudget.value = null
    budgetAvailability.value = []
    if (target.isActive === 1 && session.hasPermission('budget:query')) {
      const page = await loadBudgetPage(
        { pageNo: 1, pageSize: 100, projectId: target.projectId, status: 'ACTIVE' },
        controller.signal,
      )
      const budget = page.records.find((row) => row.sourceCostTargetId === target.id) ?? null
      executionBudget.value = budget
      if (budget)
        budgetAvailability.value = await loadBudgetAvailability(budget.id, controller.signal)
    }
    Object.assign(form, {
      projectId: target.projectId,
      projectManagerId:
        projects.value.find((project) => project.id === target.projectId)?.projectManagerId ?? '',
      versionNo: target.versionNo,
      versionName: target.versionName,
      totalTargetAmount: target.totalTargetAmount,
      totalBidCostAmount: target.totalBidCostAmount,
      totalResponsibilityAmount: target.totalResponsibilityAmount,
      sourceContractAmount: target.sourceContractAmount ?? '',
      targetCostRate: target.targetCostRate ?? '',
      effectiveDate: target.effectiveDate ?? null,
      version: target.version ?? null,
      remark: target.remark ?? null,
    })
  } catch (error) {
    if (!controller.signal.aborted && generation === detailGeneration) {
      detail.value = null
      items.value = []
      executionBudget.value = null
      budgetAvailability.value = []
      errorMessage.value = errorText(error, '项目成本预算详情加载失败')
    }
  } finally {
    if (generation === detailGeneration) detailLoading.value = false
  }
}

async function query(): Promise<void> {
  filter.pageNo = 1
  if (!(await replaceQuery())) await loadList()
}

async function changePage(next: number): Promise<void> {
  if (next < 1 || next > pageCount.value || loading.value) return
  filter.pageNo = next
  if (!(await replaceQuery())) await loadList()
}

async function openDetail(record: CostTargetRecord): Promise<void> {
  detailOpen.value = true
  await loadDetail(record.id)
}

function closeDetail(): void {
  detailController?.abort()
  detailGeneration += 1
  detailOpen.value = false
  detail.value = null
  items.value = []
  executionBudget.value = null
  budgetAvailability.value = []
}

function requireDecimal(value: string | null | undefined, label: string): string {
  const normalized = value?.trim() ?? ''
  if (!normalized || !DECIMAL_PATTERN.test(normalized))
    throw new TypeError(`${label}必须为非负十进制数`)
  return normalized
}

function command(): CostBudgetDraftSaveCommand {
  if (
    !form.projectId.trim() ||
    !form.projectManagerId.trim() ||
    !form.versionNo.trim() ||
    !form.versionName.trim()
  ) {
    throw new TypeError('项目、项目经理、版本号和版本名称不能为空')
  }
  return {
    projectId: form.projectId.trim(),
    projectManagerId: form.projectManagerId.trim(),
    versionNo: form.versionNo.trim(),
    versionName: form.versionName.trim(),
    effectiveDate: form.effectiveDate || null,
    version: form.version ?? null,
    remark: form.remark?.trim() || null,
    items: cleanItems(),
  }
}

async function selectProject(projectId: string): Promise<void> {
  form.projectId = projectId
  form.projectManagerId =
    projects.value.find((project) => project.id === projectId)?.projectManagerId ?? ''
  form.sourceContractAmount = ''
  form.targetCostRate = ''
  form.totalTargetAmount = ''
  form.totalBidCostAmount = ''
  form.totalResponsibilityAmount = ''
  items.value = []
  if (!projectId || mode.value !== 'create') return
  allocationController?.abort()
  const controller = new AbortController()
  allocationController = controller
  actionBusy.value = true
  resetNotices()
  try {
    const allocation = await loadCostTargetDefaultAllocation(projectId, controller.signal)
    if (allocationController !== controller || form.projectId !== projectId) return
    applyDefaultAllocation(allocation)
  } catch (error) {
    if (!controller.signal.aborted)
      errorMessage.value = errorText(error, '目标成本默认分配生成失败')
  } finally {
    if (allocationController === controller) actionBusy.value = false
  }
}

function applyDefaultAllocation(allocation: CostTargetDefaultAllocation): void {
  const managerId = allocation.projectManagerId || form.projectManagerId
  form.projectManagerId = managerId || ''
  form.sourceContractAmount = allocation.sourceContractAmount
  form.targetCostRate = allocation.targetCostRate
  form.totalTargetAmount = allocation.totalTargetAmount
  form.totalBidCostAmount = '0.00'
  form.totalResponsibilityAmount = allocation.totalTargetAmount
  items.value = allocation.items.map((item, index) => ({
    ...item,
    responsibleUserId: item.responsibleUserId || managerId || null,
    responsibilityUnit: item.responsibilityUnit || '项目成本责任人',
    sortOrder: index + 1,
  }))
}

function amountDifference(key: 'targetAmount' | 'responsibilityAmount'): string {
  const expected = Number(form.totalTargetAmount || 0)
  const actual = items.value.reduce((sum, item) => sum + Number(item[key] || 0), 0)
  return (expected - actual).toFixed(2)
}

async function saveHeader(): Promise<void> {
  if (actionBusy.value) return
  actionBusy.value = true
  resetNotices()
  try {
    await saveCostBudgetDraft(mode.value === 'create' ? null : routeId.value, command())
    if (mode.value === 'create') {
      successMessage.value = '项目成本预算已创建。'
      await router.replace({
        path: '/cost-target/index',
        query: { ...route.query, projectId: form.projectId },
      })
      return
    }
    await loadDetail(routeId.value, true)
    successMessage.value = '项目成本预算已保存，并已刷新服务端合计。'
  } catch (error) {
    errorMessage.value = errorText(error, '项目成本预算保存失败')
    if (mode.value === 'edit' && routeId.value) await loadDetail(routeId.value, true)
  } finally {
    actionBusy.value = false
  }
}

function closeEditor(): void {
  if (actionBusy.value) return
  void router.push({
    path: '/cost-target/index',
    query: { ...route.query, projectId: form.projectId || undefined },
  })
}

function cleanItems(): CostTargetItemRecord[] {
  return items.value.map((item) => ({
    id: item.id ?? null,
    targetId: routeId.value,
    projectId: form.projectId,
    costSubjectId: item.costSubjectId.trim(),
    targetAmount: requireDecimal(item.targetAmount, '明细目标金额'),
    bidCostAmount: requireDecimal(item.bidCostAmount, '明细投标金额'),
    responsibilityAmount: requireDecimal(item.responsibilityAmount, '明细责任金额'),
    responsibleUserId: item.responsibleUserId?.trim() || null,
    responsibilityUnit: item.responsibilityUnit?.trim() || null,
    sortOrder: item.sortOrder ?? null,
    remark: item.remark?.trim() || null,
  }))
}

function requestAction(action: Exclude<PendingAction, null>, record?: CostTargetRecord): void {
  if (record) detail.value = record
  pendingAction.value = action
}

async function confirmAction(): Promise<void> {
  const action = pendingAction.value
  const record = detail.value
  if (!action || !record || actionBusy.value) return
  actionBusy.value = true
  resetNotices()
  try {
    const version = record.version
    if (version === null || version === undefined) throw new TypeError('缺少最新版本，请刷新后重试')
    if (action === 'delete') await deleteCostTarget(record.id, version)
    if (action === 'submit') await submitCostTarget(record.id, version)
    pendingAction.value = null
    if (mode.value === 'list') {
      closeDetail()
      await loadList(true)
    } else if (action === 'delete') {
      await router.replace({ path: '/cost-target/index', query: { projectId: record.projectId } })
    } else {
      await loadDetail(record.id, true)
    }
    successMessage.value = action === 'delete' ? '项目成本预算已删除。' : '项目成本预算已提交审批。'
  } catch (error) {
    errorMessage.value = errorText(error, '项目成本预算操作失败')
    pendingAction.value = null
    if (mode.value === 'list') await loadList(true)
    else await loadDetail(record.id, true)
  } finally {
    actionBusy.value = false
  }
}

function updateItem(index: number, key: keyof CostTargetItemRecord, value: string): void {
  items.value = items.value.map((item, itemIndex) =>
    itemIndex === index ? { ...item, [key]: value } : item,
  )
}

function approvalLabel(status: string): string {
  return APPROVAL_OPTIONS.find((option) => option.value === status)?.label ?? status
}

function budgetStatusLabel(status: string): string {
  return (
    { DRAFT: '草稿', ACTIVE: '已启用', SUPERSEDED: '已替代', CLOSED: '已关闭' }[status] ??
    '未知状态'
  )
}

function approvalTone(status: string): 'neutral' | 'info' | 'success' | 'warning' {
  if (status === 'APPROVED') return 'success'
  if (status === 'APPROVING') return 'info'
  if (status === 'REJECTED') return 'warning'
  return 'neutral'
}

watch(
  () => form.projectManagerId,
  (managerId) => {
    if (mode.value !== 'create' || !managerId) return
    items.value = items.value.map((item) => ({ ...item, responsibleUserId: managerId }))
  },
)

watch(
  () => route.fullPath,
  () => {
    if (mode.value !== 'list' || canQuery.value) void loadProjects()
    if (mode.value === 'list') void loadList()
    else if (mode.value === 'create') {
      Object.assign(form, emptyForm(), {
        projectId: typeof route.query.projectId === 'string' ? route.query.projectId : '',
      })
      detail.value = null
      items.value = [blankItem()]
      resetNotices()
      if (canQuery.value) void loadList()
    } else void loadDetail(routeId.value)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  listController?.abort()
  detailController?.abort()
  projectController?.abort()
  allocationController?.abort()
})
</script>

<template>
  <div class="cost-target-page">
    <V2PageState
      v-if="(mode === 'list' && !canQuery) || (mode === 'create' && !canAdd)"
      code="403"
      title="无权访问项目成本预算"
      description="当前账号没有访问权限，页面未加载业务数据。"
      kind="error"
    />
    <template v-else>
      <template v-if="mode === 'list' || (mode === 'create' && canQuery)">
        <V2Card title="项目成本预算版本" :heading-level="embedded ? 2 : 1">
          <template #actions>
            <form class="cost-target-page__filters" @submit.prevent="query">
              <V2Input
                v-model="filter.versionNo"
                type="search"
                label="版本号"
                hide-label
                placeholder="输入版本号"
              />
              <V2Select
                v-model="filter.approvalStatus"
                label="审批状态"
                hide-label
                :options="APPROVAL_OPTIONS"
                allow-empty
                placeholder="全部审批状态"
                @update:model-value="query"
              />
              <V2Select
                v-model="filter.isActive"
                label="版本范围"
                hide-label
                :options="ACTIVE_OPTIONS"
                allow-empty
                placeholder="全部版本"
                @update:model-value="query"
              />
              <V2Button type="submit" size="small" variant="secondary" :loading="loading">
                查询
              </V2Button>
            </form>
            <V2Button
              v-if="canAdd"
              type="button"
              size="small"
              @click="router.push({ path: '/cost-target/create', query: route.query })"
              >新建版本</V2Button
            >
          </template>
        </V2Card>
        <V2PageState
          v-if="loading && !records.length"
          title="正在加载项目成本预算"
          description="正在读取当前项目的成本预算版本。"
          kind="loading"
        />
        <V2PageState
          v-else-if="!records.length && !errorMessage"
          title="暂无项目成本预算"
          description="当前筛选条件下没有可访问版本。"
        />
        <V2Card v-else-if="records.length">
          <div class="cost-target-page__table-wrap" :aria-busy="loading">
            <table class="v2-table--top" aria-label="项目成本预算版本列表">
              <thead>
                <tr>
                  <th>版本</th>
                  <th>项目</th>
                  <th>目标成本</th>
                  <th>投标成本</th>
                  <th>责任成本</th>
                  <th>状态</th>
                  <th class="v2-table-cell--actions">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(record, index) in records" :key="record.id">
                  <td>
                    <V2Button
                      size="small"
                      variant="ghost"
                      class="v2-table__record-link"
                      @click="openDetail(record)"
                    >
                      {{ record.versionNo }}
                    </V2Button>
                    <small>{{ record.versionName }}</small>
                  </td>
                  <td>{{ projectLabel(record.projectId) }}</td>
                  <td>{{ formatAmount(record.totalTargetAmount) }}</td>
                  <td>{{ formatAmount(record.totalBidCostAmount) }}</td>
                  <td>{{ formatAmount(record.totalResponsibilityAmount) }}</td>
                  <td>
                    <V2Badge :tone="approvalTone(record.approvalStatus)">{{
                      approvalLabel(record.approvalStatus)
                    }}</V2Badge
                    ><V2Badge v-if="record.isActive === 1" tone="success">活动版本</V2Badge>
                  </td>
                  <td class="v2-table-cell--actions">
                    <V2ActionMenu
                      :label="`${record.versionNo}更多操作`"
                      :placement="index >= records.length - 3 ? 'top-end' : 'bottom-end'"
                    >
                      <V2Button
                        v-if="
                          canEdit &&
                          record.isActive !== 1 &&
                          ['DRAFT', 'REJECTED'].includes(record.approvalStatus)
                        "
                        size="small"
                        variant="secondary"
                        @click="
                          router.push({
                            path: `/cost-target/${record.id}/edit`,
                            query: route.query,
                          })
                        "
                        >编辑</V2Button
                      >
                      <V2Button
                        v-if="
                          canSubmit &&
                          record.isActive !== 1 &&
                          ['DRAFT', 'REJECTED'].includes(record.approvalStatus)
                        "
                        size="small"
                        @click="requestAction('submit', record)"
                        >提交</V2Button
                      >
                      <V2Button
                        v-if="
                          canDelete &&
                          record.isActive !== 1 &&
                          ['DRAFT', 'REJECTED'].includes(record.approvalStatus)
                        "
                        size="small"
                        variant="danger"
                        @click="requestAction('delete', record)"
                        >删除</V2Button
                      >
                    </V2ActionMenu>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <template #footer>
            <nav class="cost-target-page__pager" aria-label="项目成本预算分页">
              <span>共 {{ total }} 条</span
              ><V2Button
                size="small"
                variant="secondary"
                :disabled="(filter.pageNo ?? 1) <= 1"
                @click="changePage((filter.pageNo ?? 1) - 1)"
                >上一页</V2Button
              ><span>第 {{ filter.pageNo }} 页</span
              ><V2Button
                size="small"
                variant="secondary"
                :disabled="(filter.pageNo ?? 1) >= pageCount"
                @click="changePage((filter.pageNo ?? 1) + 1)"
                >下一页</V2Button
              >
            </nav>
          </template>
        </V2Card>
      </template>

      <component
        :is="mode === 'create' ? V2Dialog : 'div'"
        v-if="mode === 'create' || mode === 'edit'"
        v-bind="
          mode === 'create'
            ? {
                open: true,
                title: '新建项目成本预算',
                panelClass: 'v2-dialog-wide cost-target-page__editor-dialog',
                closeOnBackdrop: true,
              }
            : {}
        "
        @close="closeEditor"
      >
        <V2PageState
          v-if="detailLoading"
          title="正在加载项目成本预算详情"
          description="正在读取项目成本预算版本和科目明细。"
          kind="loading"
        />
        <V2Card
          v-else-if="mode === 'create' || detail"
          :title="mode === 'create' ? '成本预算信息' : '编辑项目成本预算'"
          :heading-level="mode === 'create' ? 2 : 1"
        >
          <form class="cost-target-page__editor" @submit.prevent="saveHeader">
            <section class="cost-target-page__editor-section" aria-labelledby="cost-budget-version">
              <h3 id="cost-budget-version">版本信息</h3>
              <div class="cost-target-page__form">
                <V2Select
                  :model-value="form.projectId"
                  label="项目"
                  :options="projectOptions"
                  required
                  :disabled="actionBusy || mode === 'edit' || !canSaveDraft"
                  @update:model-value="selectProject"
                />
                <V2Select
                  v-model="form.projectManagerId"
                  label="项目经理"
                  :options="responsibleUsers"
                  required
                  :disabled="actionBusy || !editable || !canSaveDraft"
                />
                <V2Input
                  v-model="form.versionNo"
                  label="版本号"
                  required
                  :disabled="actionBusy || !editable || !canSaveDraft"
                />
                <V2Input
                  v-model="form.versionName"
                  label="版本名称"
                  required
                  :disabled="actionBusy || !editable || !canSaveDraft"
                />
                <V2Input
                  v-if="form.sourceContractAmount"
                  v-model="form.sourceContractAmount"
                  label="合同金额快照"
                  :decimal-scale="2"
                  disabled
                />
                <V2Input
                  v-if="form.targetCostRate"
                  v-model="form.targetCostRate"
                  label="目标成本率"
                  :decimal-scale="2"
                  disabled
                />
                <V2Input
                  v-if="form.totalTargetAmount"
                  v-model="form.totalTargetAmount"
                  label="目标成本合计（服务端）"
                  :decimal-scale="2"
                  disabled
                />
                <V2Input
                  v-if="form.totalBidCostAmount"
                  v-model="form.totalBidCostAmount"
                  label="投标成本合计（服务端）"
                  :decimal-scale="2"
                  disabled
                />
                <V2Input
                  v-if="form.totalResponsibilityAmount"
                  v-model="form.totalResponsibilityAmount"
                  label="责任预算合计（服务端）"
                  :decimal-scale="2"
                  disabled
                />
                <label class="cost-target-page__native-field"
                  ><span>生效日期</span
                  ><input
                    v-model="form.effectiveDate"
                    type="date"
                    :disabled="actionBusy || !editable || !canSaveDraft"
                /></label>
              </div>
            </section>

            <section class="cost-target-page__editor-section" aria-labelledby="cost-budget-lines">
              <header class="cost-target-page__section-heading">
                <div>
                  <h3 id="cost-budget-lines">成本预算明细</h3>
                  <p>
                    固定10类科目；目标差额 {{ targetDifference }}，责任差额
                    {{ responsibilityDifference }}。服务端最终校验。
                  </p>
                </div>
                <V2Button
                  v-if="canSaveDraft && editable && !fixedTargetVersion"
                  size="small"
                  variant="secondary"
                  :disabled="actionBusy"
                  @click="items = [...items, blankItem()]"
                  >添加明细</V2Button
                >
              </header>
              <V2PageState
                v-if="!items.length && !errorMessage"
                title="暂无明细"
                description="草稿或驳回版本可添加明细。"
              />
              <div
                v-else-if="items.length"
                class="cost-target-page__table-wrap"
                role="region"
                aria-label="成本预算明细编辑表格"
                tabindex="0"
              >
                <table class="v2-table--top cost-target-page__editor-table">
                  <thead>
                    <tr>
                      <th>成本科目编码/名称<span aria-hidden="true">*</span></th>
                      <th>目标金额<span aria-hidden="true">*</span></th>
                      <th>投标金额<span aria-hidden="true">*</span></th>
                      <th>责任金额<span aria-hidden="true">*</span></th>
                      <th>责任单位</th>
                      <th>责任人<span aria-hidden="true">*</span></th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(item, index) in items" :key="item.id || index">
                      <td>
                        <V2Select
                          :model-value="item.costSubjectId"
                          label="成本科目"
                          hide-label
                          :options="costSubjectOptions"
                          required
                          :disabled="!canSaveDraft || !editable || fixedTargetVersion"
                          @update:model-value="updateItem(index, 'costSubjectId', $event)"
                        />
                      </td>
                      <td>
                        <V2Input
                          :model-value="item.targetAmount"
                          label="目标金额"
                          :decimal-scale="2"
                          hide-label
                          required
                          :disabled="!canSaveDraft || !editable"
                          @update:model-value="updateItem(index, 'targetAmount', $event)"
                        />
                      </td>
                      <td>
                        <V2Input
                          :model-value="item.bidCostAmount ?? ''"
                          label="投标金额"
                          :decimal-scale="2"
                          hide-label
                          required
                          :disabled="!canSaveDraft || !editable"
                          @update:model-value="updateItem(index, 'bidCostAmount', $event)"
                        />
                      </td>
                      <td>
                        <V2Input
                          :model-value="item.responsibilityAmount ?? ''"
                          label="责任金额"
                          :decimal-scale="2"
                          hide-label
                          required
                          :disabled="!canSaveDraft || !editable"
                          @update:model-value="updateItem(index, 'responsibilityAmount', $event)"
                        />
                      </td>
                      <td>
                        <V2Input
                          :model-value="item.responsibilityUnit ?? ''"
                          label="责任单位"
                          hide-label
                          :disabled="!canSaveDraft || !editable"
                          @update:model-value="updateItem(index, 'responsibilityUnit', $event)"
                        />
                      </td>
                      <td>
                        <V2Select
                          :model-value="item.responsibleUserId ?? ''"
                          label="责任人"
                          hide-label
                          :options="responsibleUsers"
                          required
                          :disabled="!canSaveDraft || !editable"
                          @update:model-value="updateItem(index, 'responsibleUserId', $event)"
                        />
                      </td>
                      <td>
                        <V2Button
                          v-if="canSaveDraft && editable && !fixedTargetVersion"
                          size="small"
                          variant="danger"
                          :disabled="actionBusy"
                          @click="items = items.filter((_, itemIndex) => itemIndex !== index)"
                          >移除</V2Button
                        >
                        <span v-else>—</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>

            <div class="cost-target-page__editor-actions">
              <V2Button variant="secondary" :disabled="actionBusy" @click="closeEditor">
                {{ mode === 'create' ? '取消' : '返回列表' }}
              </V2Button>
              <V2Button
                v-if="mode === 'edit' && canSubmit && editable"
                :disabled="actionBusy"
                @click="requestAction('submit')"
                >提交审批</V2Button
              >
              <V2Button
                v-if="mode === 'edit' && canDelete && editable"
                variant="danger"
                :disabled="actionBusy"
                @click="requestAction('delete')"
                >删除版本</V2Button
              >
              <V2Button
                v-if="items.length && editable && canSaveDraft"
                type="submit"
                :loading="actionBusy"
              >
                {{ mode === 'create' ? '创建项目成本预算' : '保存项目成本预算' }}
              </V2Button>
            </div>
          </form>
        </V2Card>
      </component>

      <V2Dialog
        :open="detailOpen"
        title="项目成本预算详情"
        panel-class="v2-detail-dialog"
        :close-on-backdrop="true"
        @close="closeDetail"
      >
        <V2PageState
          v-if="detailLoading"
          title="正在加载项目成本预算详情"
          description="正在读取项目成本预算版本和科目明细。"
          kind="loading"
        />
        <div v-else-if="detail" class="cost-target-page__detail">
          <dl class="v2-detail-dialog__facts">
            <div>
              <dt>版本</dt>
              <dd>{{ detail.versionNo }} / {{ detail.versionName }}</dd>
            </div>
            <div>
              <dt>项目</dt>
              <dd>{{ projectLabel(detail.projectId) }}</dd>
            </div>
            <div>
              <dt>目标成本</dt>
              <dd>{{ formatAmount(detail.totalTargetAmount) }}</dd>
            </div>
            <div>
              <dt>投标成本</dt>
              <dd>{{ formatAmount(detail.totalBidCostAmount) }}</dd>
            </div>
            <div>
              <dt>责任成本</dt>
              <dd>{{ formatAmount(detail.totalResponsibilityAmount) }}</dd>
            </div>
            <div>
              <dt>审批状态</dt>
              <dd>{{ approvalLabel(detail.approvalStatus) }}</dd>
            </div>
            <div>
              <dt>活动版本</dt>
              <dd>{{ detail.isActive === 1 ? '是' : '否' }}</dd>
            </div>
            <div v-if="executionBudget">
              <dt>执行预算</dt>
              <dd>
                {{ executionBudget.budgetCode }} / {{ budgetStatusLabel(executionBudget.status) }}
              </dd>
            </div>
            <div>
              <dt>备注</dt>
              <dd>{{ detail.remark || '—' }}</dd>
            </div>
          </dl>
          <h3>明细</h3>
          <V2PageState
            v-if="!items.length && !errorMessage"
            title="暂无明细"
            description="当前项目成本预算版本尚未录入科目明细。"
          />
          <div
            v-else-if="items.length"
            class="cost-target-page__table-wrap"
            role="region"
            aria-label="项目成本预算明细表格"
            tabindex="0"
          >
            <table class="v2-table--top">
              <thead>
                <tr>
                  <th>成本科目</th>
                  <th>目标金额</th>
                  <th>投标金额</th>
                  <th>责任金额</th>
                  <th v-if="executionBudget">已占用</th>
                  <th v-if="executionBudget">已消耗</th>
                  <th v-if="executionBudget">可用额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in items" :key="item.id || item.costSubjectId">
                  <td>{{ costSubjectLabel(item.costSubjectId, index) }}</td>
                  <td>{{ formatAmount(item.targetAmount) }}</td>
                  <td>{{ formatAmount(item.bidCostAmount) }}</td>
                  <td>{{ formatAmount(item.responsibilityAmount) }}</td>
                  <td v-if="executionBudget">
                    {{
                      formatAmount(availabilityBySubject.get(item.costSubjectId)?.reservedAmount)
                    }}
                  </td>
                  <td v-if="executionBudget">
                    {{
                      formatAmount(availabilityBySubject.get(item.costSubjectId)?.consumedAmount)
                    }}
                  </td>
                  <td v-if="executionBudget">
                    {{
                      formatAmount(availabilityBySubject.get(item.costSubjectId)?.availableAmount)
                    }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </V2Dialog>

      <V2ConfirmDialog
        :open="pendingAction !== null"
        :title="pendingAction === 'delete' ? '删除项目成本预算' : '提交项目成本预算'"
        :description="
          pendingAction === 'delete'
            ? '只能删除未激活的草稿或驳回版本，此操作不可撤销。'
            : '操作将使用当前服务端版本做并发校验。'
        "
        :confirm-text="pendingAction === 'delete' ? '确认删除' : '确认提交'"
        :danger="pendingAction === 'delete'"
        :loading="actionBusy"
        @close="pendingAction = null"
        @confirm="confirmAction"
      />
    </template>
  </div>
</template>

<style scoped>
.cost-target-page,
.cost-target-page__detail {
  display: grid;
  gap: var(--v2-space-4);
}
.cost-target-page__filters,
.cost-target-page__form {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--v2-space-3);
  align-items: end;
}
.cost-target-page__filters {
  grid-template-columns: repeat(4, minmax(0, 1fr)) auto;
}
.cost-target-page__editor,
.cost-target-page__editor-section {
  display: grid;
  gap: var(--v2-space-4);
}
.cost-target-page__editor-section + .cost-target-page__editor-section {
  padding-top: var(--v2-space-5);
  border-top: var(--v2-border-width) solid var(--v2-color-border);
}
.cost-target-page__editor-section h3,
.cost-target-page__editor-section p {
  margin: 0;
}
.cost-target-page__section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-3);
}
.cost-target-page__section-heading p {
  margin-top: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
}
.cost-target-page__editor-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--v2-space-2);
  padding-top: var(--v2-space-4);
  border-top: var(--v2-border-width) solid var(--v2-color-border);
}
.cost-target-page__table-wrap {
  max-width: 100%;
  overflow-x: auto;
}
.cost-target-page__editor-table {
  width: 100%;
  min-width: 56rem;
  table-layout: fixed;
}
.cost-target-page__editor-table th:first-child {
  width: 22%;
}
.cost-target-page__editor-table th:last-child {
  width: 6%;
}
.cost-target-page__editor-table th span {
  color: var(--v2-color-danger);
}
.cost-target-page__editor-table .v2-field {
  min-width: 0;
}
table {
  min-width: 52rem;
}
td small {
  display: block;
  color: var(--v2-color-text-secondary);
}
.cost-target-page__actions,
.cost-target-page__pager {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  align-items: center;
}
.cost-target-page__pager {
  justify-content: flex-end;
}
.cost-target-page__native-field {
  display: grid;
  gap: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
}
.cost-target-page__native-field input {
  min-height: var(--v2-control-height-md);
  padding: var(--v2-space-2) var(--v2-space-3);
}
dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--v2-space-2) var(--v2-space-4);
  margin: 0;
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
@media (max-width: 64rem) {
  .cost-target-page__filters,
  .cost-target-page__form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 40rem) {
  .cost-target-page__filters,
  .cost-target-page__form {
    grid-template-columns: 1fr;
  }
}
</style>
