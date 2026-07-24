<script setup lang="ts">
import type {
  CostTargetItemRecord,
  CostTargetQuery,
  CostTargetRecord,
  CostTargetSaveCommand,
  ProjectContextOption,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  V2Alert,
  V2Badge,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  useToastMessage,
} from '@/components'
import {
  activateCostTarget,
  createCostTarget,
  deleteCostTarget,
  loadCostSubjectOptions,
  loadCostTarget,
  loadCostTargetItems,
  loadCostTargetPage,
  loadProjectContextOptions,
  saveCostTargetItems,
  submitCostTarget,
  updateCostTarget,
} from '@/services/commercial'
import type { CostSubjectOption } from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'

type PendingAction = 'delete' | 'submit' | 'activate' | null

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

const filter = reactive<CostTargetQuery>({ pageNo: 1, pageSize: 10 })
const records = ref<CostTargetRecord[]>([])
const total = ref(0)
const projects = ref<ProjectContextOption[]>([])
const costSubjects = ref<CostSubjectOption[]>([])
const detail = ref<CostTargetRecord | null>(null)
const items = ref<CostTargetItemRecord[]>([])
const form = reactive<CostTargetSaveCommand>(emptyForm())
const loading = ref(false)
const detailLoading = ref(false)
const actionBusy = ref(false)
const errorMessage = ref('')
const successMessage = useToastMessage()
const detailOpen = ref(false)
const pendingAction = ref<PendingAction>(null)

let listGeneration = 0
let detailGeneration = 0
let listController: AbortController | null = null
let detailController: AbortController | null = null
let projectController: AbortController | null = null

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
const canActivate = computed(() => session.hasPermission('cost:target:activate'))
const editable = computed(
  () =>
    mode.value === 'create' ||
    (!!detail.value &&
      detail.value.isActive !== 1 &&
      ['DRAFT', 'REJECTED'].includes(detail.value.approvalStatus)),
)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / (filter.pageSize ?? 10))))
const projectOptions = computed(() =>
  projects.value.map((project) => ({ value: project.id, label: project.projectName })),
)
const costSubjectLabel = (id: string, index: number) => {
  const subject = costSubjects.value.find((item) => item.id === id)
  return subject ? `${subject.subjectCode} · ${subject.subjectName}` : `成本科目 ${index + 1}`
}

function projectLabel(projectId?: string | null): string {
  return projects.value.find((project) => project.id === projectId)?.projectName ?? '—'
}

function emptyForm(): CostTargetSaveCommand {
  return {
    projectId: '',
    versionNo: '',
    versionName: '',
    totalTargetAmount: '',
    totalBidCostAmount: '',
    totalResponsibilityAmount: '',
    effectiveDate: null,
    version: null,
    remark: null,
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
    path: '/cost-target/index',
    query: {
      ...route.query,
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
      errorMessage.value = errorText(error, '目标成本加载失败')
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
    Object.assign(form, {
      projectId: target.projectId,
      versionNo: target.versionNo,
      versionName: target.versionName,
      totalTargetAmount: target.totalTargetAmount,
      totalBidCostAmount: target.totalBidCostAmount,
      totalResponsibilityAmount: target.totalResponsibilityAmount,
      effectiveDate: target.effectiveDate ?? null,
      version: target.version ?? null,
      remark: target.remark ?? null,
    })
  } catch (error) {
    if (!controller.signal.aborted && generation === detailGeneration) {
      detail.value = null
      items.value = []
      errorMessage.value = errorText(error, '目标成本详情加载失败')
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
}

function requireDecimal(value: string | null | undefined, label: string): string {
  const normalized = value?.trim() ?? ''
  if (!normalized || !DECIMAL_PATTERN.test(normalized))
    throw new TypeError(`${label}必须为非负十进制数`)
  return normalized
}

function command(): CostTargetSaveCommand {
  if (!form.projectId.trim() || !form.versionNo.trim() || !form.versionName.trim()) {
    throw new TypeError('项目、版本号和版本名称不能为空')
  }
  return {
    projectId: form.projectId.trim(),
    versionNo: form.versionNo.trim(),
    versionName: form.versionName.trim(),
    totalTargetAmount: requireDecimal(form.totalTargetAmount, '目标成本总额'),
    totalBidCostAmount: requireDecimal(form.totalBidCostAmount, '投标成本总额'),
    totalResponsibilityAmount: requireDecimal(form.totalResponsibilityAmount, '责任成本总额'),
    effectiveDate: form.effectiveDate || null,
    version: form.version ?? null,
    remark: form.remark?.trim() || null,
  }
}

function versionOf(): string | number {
  const version = detail.value?.version
  if (version === null || version === undefined || String(version).trim() === '') {
    throw new TypeError('缺少最新版本，请刷新后重试')
  }
  return version
}

async function saveHeader(): Promise<void> {
  if (actionBusy.value) return
  actionBusy.value = true
  resetNotices()
  try {
    const payload = command()
    if (mode.value === 'create') {
      const id = await createCostTarget(payload)
      successMessage.value = '目标成本版本已创建。'
      await router.replace({ path: `/cost-target/${id}/edit`, query: route.query })
      return
    }
    await updateCostTarget(routeId.value, payload)
    await loadDetail(routeId.value, true)
    successMessage.value = '目标成本已保存，并已刷新最新数据。'
  } catch (error) {
    errorMessage.value = errorText(error, '目标成本保存失败')
    if (mode.value === 'edit' && routeId.value) await loadDetail(routeId.value, true)
  } finally {
    actionBusy.value = false
  }
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

async function saveItems(): Promise<void> {
  if (actionBusy.value || !editable.value || !canEdit.value) return
  actionBusy.value = true
  resetNotices()
  try {
    const payload = cleanItems()
    if (payload.some((item) => !item.costSubjectId)) throw new TypeError('成本科目ID不能为空')
    await saveCostTargetItems(routeId.value, payload, versionOf())
    await loadDetail(routeId.value, true)
    successMessage.value = '目标成本明细已保存。'
  } catch (error) {
    errorMessage.value = errorText(error, '目标成本明细保存失败')
    await loadDetail(routeId.value, true)
  } finally {
    actionBusy.value = false
  }
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
    if (action === 'activate') await activateCostTarget(record.id, version)
    pendingAction.value = null
    if (mode.value === 'list') {
      closeDetail()
      await loadList(true)
    } else if (action === 'delete') {
      await router.replace({ path: '/cost-target/index', query: { projectId: record.projectId } })
    } else {
      await loadDetail(record.id, true)
    }
    successMessage.value =
      action === 'delete'
        ? '目标成本已删除。'
        : action === 'submit'
          ? '目标成本已提交审批。'
          : '目标成本已激活。'
  } catch (error) {
    errorMessage.value = errorText(error, '目标成本操作失败')
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

function approvalTone(status: string): 'neutral' | 'info' | 'success' | 'warning' {
  if (status === 'APPROVED') return 'success'
  if (status === 'APPROVING') return 'info'
  if (status === 'REJECTED') return 'warning'
  return 'neutral'
}

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
      items.value = []
      resetNotices()
    } else void loadDetail(routeId.value)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  listController?.abort()
  detailController?.abort()
  projectController?.abort()
})
</script>

<template>
  <div class="cost-target-page">
    <V2PageState
      v-if="mode === 'list' && !canQuery"
      code="403"
      title="无权访问目标成本"
      description="当前账号没有访问权限，页面未加载业务数据。"
      kind="error"
    />
    <template v-else>
      <V2Alert
        v-if="errorMessage"
        tone="danger"
        title="操作失败"
        dismissible
        @dismiss="errorMessage = ''"
      >
        {{ errorMessage }}
      </V2Alert>
      <template v-if="mode === 'list'">
        <V2Card title="目标成本版本" :heading-level="1">
          <template #actions>
            <V2Button
              v-if="canAdd"
              @click="router.push({ path: '/cost-target/create', query: route.query })"
              >新建版本</V2Button
            >
          </template>
          <div class="cost-target-page__filters">
            <V2Input
              v-model="filter.versionNo"
              type="search"
              label="版本号"
              hide-label
              placeholder="输入版本号"
              @keyup.enter="query"
            />
            <V2Select
              v-model="filter.approvalStatus"
              label="审批状态"
              hide-label
              :options="APPROVAL_OPTIONS"
              allow-empty
              placeholder="全部审批状态"
            />
            <V2Select
              v-model="filter.isActive"
              label="版本范围"
              hide-label
              :options="ACTIVE_OPTIONS"
              allow-empty
              placeholder="全部版本"
            />
            <V2Button variant="secondary" :loading="loading" @click="query">查询</V2Button>
          </div>
        </V2Card>
        <V2PageState
          v-if="loading && !records.length"
          title="正在加载目标成本"
          description="正在读取当前项目的目标成本版本。"
          kind="loading"
        />
        <V2PageState
          v-else-if="!records.length"
          title="暂无目标成本"
          description="当前筛选条件下没有可访问版本。"
        />
        <V2Card v-else title="目标成本版本列表" :heading-level="2">
          <div class="cost-target-page__table-wrap" :aria-busy="loading">
            <table class="v2-table--top" aria-label="目标成本版本列表">
              <thead>
                <tr>
                  <th>版本</th>
                  <th>项目</th>
                  <th>目标成本</th>
                  <th>投标成本</th>
                  <th>责任成本</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="record in records" :key="record.id">
                  <td>
                    {{ record.versionNo }}<small>{{ record.versionName }}</small>
                  </td>
                  <td>{{ projectLabel(record.projectId) }}</td>
                  <td>{{ record.totalTargetAmount }}</td>
                  <td>{{ record.totalBidCostAmount }}</td>
                  <td>{{ record.totalResponsibilityAmount }}</td>
                  <td>
                    <V2Badge :tone="approvalTone(record.approvalStatus)">{{
                      approvalLabel(record.approvalStatus)
                    }}</V2Badge
                    ><V2Badge v-if="record.isActive === 1" tone="success">活动版本</V2Badge>
                  </td>
                  <td>
                    <div class="cost-target-page__actions">
                      <V2Button size="small" variant="secondary" @click="openDetail(record)"
                        >详情</V2Button
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
                          canActivate &&
                          record.approvalStatus === 'APPROVED' &&
                          record.isActive !== 1
                        "
                        size="small"
                        @click="requestAction('activate', record)"
                        >激活</V2Button
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
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <template #footer>
            <nav class="cost-target-page__pager" aria-label="目标成本分页">
              <span>共 {{ total }} 条</span
              ><V2Button
                variant="secondary"
                :disabled="(filter.pageNo ?? 1) <= 1"
                @click="changePage((filter.pageNo ?? 1) - 1)"
                >上一页</V2Button
              ><span>第 {{ filter.pageNo }} 页</span
              ><V2Button
                variant="secondary"
                :disabled="(filter.pageNo ?? 1) >= pageCount"
                @click="changePage((filter.pageNo ?? 1) + 1)"
                >下一页</V2Button
              >
            </nav>
          </template>
        </V2Card>
      </template>

      <template v-else>
        <V2PageState
          v-if="detailLoading"
          title="正在加载目标成本详情"
          description="正在读取目标成本版本和科目明细。"
          kind="loading"
        />
        <template v-else-if="mode === 'create' || detail">
          <V2Card
            :title="mode === 'create' ? '新建目标成本版本' : '编辑目标成本版本'"
            :heading-level="1"
          >
            <form class="cost-target-page__form" @submit.prevent="saveHeader">
              <V2Select
                v-model="form.projectId"
                label="项目"
                :options="projectOptions"
                required
                :disabled="actionBusy || mode === 'edit'"
              />
              <V2Input
                v-model="form.versionNo"
                label="版本号"
                required
                :disabled="actionBusy || !editable"
              />
              <V2Input
                v-model="form.versionName"
                label="版本名称"
                required
                :disabled="actionBusy || !editable"
              />
              <V2Input
                v-model="form.totalTargetAmount"
                label="目标成本总额"
                required
                :disabled="actionBusy || !editable"
              />
              <V2Input
                v-model="form.totalBidCostAmount"
                label="投标成本总额"
                required
                :disabled="actionBusy || !editable"
              />
              <V2Input
                v-model="form.totalResponsibilityAmount"
                label="责任成本总额"
                required
                :disabled="actionBusy || !editable"
              />
              <label class="cost-target-page__native-field"
                ><span>生效日期</span
                ><input
                  v-model="form.effectiveDate"
                  type="date"
                  :disabled="actionBusy || !editable"
              /></label>
              <label class="cost-target-page__native-field"
                ><span>备注</span
                ><textarea
                  v-model="form.remark"
                  maxlength="500"
                  :disabled="actionBusy || !editable"
                ></textarea>
              </label>
              <div class="cost-target-page__actions">
                <V2Button v-if="editable" type="submit" :loading="actionBusy">{{
                  mode === 'create' ? '创建' : '保存版本'
                }}</V2Button>
                <V2Button
                  variant="secondary"
                  :disabled="actionBusy"
                  @click="
                    router.push({
                      path: '/cost-target/index',
                      query: { projectId: form.projectId },
                    })
                  "
                  >返回列表</V2Button
                >
                <V2Button
                  v-if="mode === 'edit' && canSubmit && editable"
                  :disabled="actionBusy"
                  @click="requestAction('submit')"
                  >提交审批</V2Button
                >
                <V2Button
                  v-if="
                    mode === 'edit' &&
                    canActivate &&
                    detail?.approvalStatus === 'APPROVED' &&
                    detail.isActive !== 1
                  "
                  :disabled="actionBusy"
                  @click="requestAction('activate')"
                  >激活版本</V2Button
                >
                <V2Button
                  v-if="mode === 'edit' && canDelete && editable"
                  variant="danger"
                  :disabled="actionBusy"
                  @click="requestAction('delete')"
                  >删除版本</V2Button
                >
              </div>
            </form>
          </V2Card>

          <V2Card
            v-if="mode === 'edit'"
            title="目标成本明细"
            subtitle="金额均按服务端十进制字符串保存，页面不计算业务合计。"
          >
            <template #actions
              ><V2Button
                v-if="canEdit && editable"
                size="small"
                variant="secondary"
                :disabled="actionBusy"
                @click="items = [...items, blankItem()]"
                >添加明细</V2Button
              ></template
            >
            <V2PageState
              v-if="!items.length"
              title="暂无明细"
              description="草稿或驳回版本可添加明细。"
            />
            <div v-else class="cost-target-page__items">
              <div
                v-for="(item, index) in items"
                :key="item.id || index"
                class="cost-target-page__item"
              >
                <V2Input
                  :model-value="item.costSubjectId"
                  label="成本科目ID"
                  required
                  :disabled="!canEdit || !editable"
                  @update:model-value="updateItem(index, 'costSubjectId', $event)"
                />
                <V2Input
                  :model-value="item.targetAmount"
                  label="目标金额"
                  required
                  :disabled="!canEdit || !editable"
                  @update:model-value="updateItem(index, 'targetAmount', $event)"
                />
                <V2Input
                  :model-value="item.bidCostAmount ?? ''"
                  label="投标金额"
                  required
                  :disabled="!canEdit || !editable"
                  @update:model-value="updateItem(index, 'bidCostAmount', $event)"
                />
                <V2Input
                  :model-value="item.responsibilityAmount ?? ''"
                  label="责任金额"
                  required
                  :disabled="!canEdit || !editable"
                  @update:model-value="updateItem(index, 'responsibilityAmount', $event)"
                />
                <V2Input
                  :model-value="item.responsibleUserId ?? ''"
                  label="责任人ID"
                  :disabled="!canEdit || !editable"
                  @update:model-value="updateItem(index, 'responsibleUserId', $event)"
                />
                <V2Input
                  :model-value="item.responsibilityUnit ?? ''"
                  label="责任单位"
                  :disabled="!canEdit || !editable"
                  @update:model-value="updateItem(index, 'responsibilityUnit', $event)"
                />
                <V2Button
                  v-if="canEdit && editable"
                  size="small"
                  variant="danger"
                  :disabled="actionBusy"
                  @click="items = items.filter((_, itemIndex) => itemIndex !== index)"
                  >移除</V2Button
                >
              </div>
            </div>
            <template v-if="items.length && canEdit && editable" #footer
              ><V2Button :loading="actionBusy" @click="saveItems">保存明细</V2Button></template
            >
          </V2Card>
        </template>
      </template>

      <V2Dialog
        :open="detailOpen"
        title="目标成本详情"
        panel-class="v2-detail-dialog"
        :close-on-backdrop="false"
        @close="closeDetail"
      >
        <V2PageState
          v-if="detailLoading"
          title="正在加载目标成本详情"
          description="正在读取目标成本版本和科目明细。"
          kind="loading"
        />
        <div v-else-if="detail" class="cost-target-page__detail">
          <dl class="v2-detail-dialog__facts">
            <dt>版本</dt>
            <dd>{{ detail.versionNo }} / {{ detail.versionName }}</dd>
            <dt>项目</dt>
            <dd>{{ projectLabel(detail.projectId) }}</dd>
            <dt>目标成本</dt>
            <dd>{{ detail.totalTargetAmount }}</dd>
            <dt>投标成本</dt>
            <dd>{{ detail.totalBidCostAmount }}</dd>
            <dt>责任成本</dt>
            <dd>{{ detail.totalResponsibilityAmount }}</dd>
            <dt>审批状态</dt>
            <dd>{{ approvalLabel(detail.approvalStatus) }}</dd>
            <dt>活动版本</dt>
            <dd>{{ detail.isActive === 1 ? '是' : '否' }}</dd>
            <dt>备注</dt>
            <dd>{{ detail.remark || '—' }}</dd>
          </dl>
          <h3>明细</h3>
          <V2PageState
            v-if="!items.length"
            title="暂无明细"
            description="当前目标成本版本尚未录入科目明细。"
          />
          <div v-else class="cost-target-page__table-wrap">
            <table class="v2-table--top">
              <thead>
                <tr>
                  <th>成本科目</th>
                  <th>目标金额</th>
                  <th>投标金额</th>
                  <th>责任金额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in items" :key="item.id || item.costSubjectId">
                  <td>{{ costSubjectLabel(item.costSubjectId, index) }}</td>
                  <td>{{ item.targetAmount }}</td>
                  <td>{{ item.bidCostAmount }}</td>
                  <td>{{ item.responsibilityAmount }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </V2Dialog>

      <V2ConfirmDialog
        :open="pendingAction !== null"
        :title="
          pendingAction === 'delete'
            ? '删除目标成本'
            : pendingAction === 'submit'
              ? '提交目标成本'
              : '激活目标成本'
        "
        :description="
          pendingAction === 'delete'
            ? '只能删除未激活的草稿或驳回版本，此操作不可撤销。'
            : '操作将使用当前服务端版本做并发校验。'
        "
        :confirm-text="
          pendingAction === 'delete'
            ? '确认删除'
            : pendingAction === 'submit'
              ? '确认提交'
              : '确认激活'
        "
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
.cost-target-page__detail,
.cost-target-page__items {
  display: grid;
  gap: var(--v2-space-4);
}
.cost-target-page__filters,
.cost-target-page__form {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--v2-space-3);
  align-items: end;
}
.cost-target-page__filters {
  grid-template-columns: repeat(4, minmax(0, 1fr)) auto;
}
.cost-target-page__table-wrap {
  max-width: 100%;
  overflow-x: auto;
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
.cost-target-page__item {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--v2-space-3);
  align-items: end;
  padding: var(--v2-space-3);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}
.cost-target-page__native-field {
  display: grid;
  gap: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
}
.cost-target-page__native-field input,
.cost-target-page__native-field textarea {
  min-height: var(--v2-control-height-md);
  padding: var(--v2-space-2) var(--v2-space-3);
}
.cost-target-page__native-field textarea {
  min-height: var(--v2-control-height-textarea);
  resize: vertical;
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
  .cost-target-page__form,
  .cost-target-page__item {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 40rem) {
  .cost-target-page__filters,
  .cost-target-page__form,
  .cost-target-page__item {
    grid-template-columns: 1fr;
  }
}
</style>
