<script setup lang="ts">
import type {
  BudgetAvailabilityRecord,
  BudgetLineRecord,
  BudgetQuery,
  BudgetSaveCommand,
  ProjectBudgetRecord,
  ProjectContextOption,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  V2Alert,
  V2Badge,
  V2Button,
  V2Card,
  V2Dialog,
  V2GlassButton,
  V2Input,
  V2PageState,
  V2Select,
  useToastMessage,
} from '@/components'
import {
  createBudget,
  deleteBudget,
  loadBudget,
  loadBudgetAvailability,
  loadBudgetPage,
  loadCostSubjectOptions,
  loadProjectContextOptions,
  saveBudgetLines,
  submitBudget,
  updateBudget,
} from '@/services/commercial'
import type { CostSubjectOption } from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { reportPeriodBounds } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'
const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const filter = reactive<BudgetQuery>({ pageNo: 1, pageSize: 10 })
const records = ref<ProjectBudgetRecord[]>([])
const total = ref(0)
const projects = ref<ProjectContextOption[]>([])
const costSubjects = ref<CostSubjectOption[]>([])
const detail = ref<ProjectBudgetRecord | null>(null)
const availability = ref<BudgetAvailabilityRecord[]>([])
const form = reactive<BudgetSaveCommand>(blank())
const lines = ref<BudgetLineRecord[]>([])
const loading = ref(false)
const detailLoading = ref(false)
const actionBusy = ref(false)
const errorMessage = ref('')
const successMessage = useToastMessage()
const dialog = ref<'closed' | 'detail' | 'create' | 'edit'>('closed')
let controller: AbortController | null = null
let detailController: AbortController | null = null
let generation = 0
let detailGeneration = 0
const canQuery = computed(() => session.hasPermission('budget:query'))
const canAdd = computed(() => session.hasPermission('budget:add'))
const canEdit = computed(() => session.hasPermission('budget:edit'))
const canDelete = computed(() => session.hasPermission('budget:delete'))
const canSubmit = computed(() => session.hasPermission('budget:submit'))
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / (filter.pageSize || 10))))
const projectOptions = computed(() =>
  projects.value.map((p) => ({ value: p.id, label: p.projectName })),
)
const costSubjectOptions = computed(() =>
  costSubjects.value
    .filter((subject) => ['ACTIVE', 'ENABLE'].includes(subject.status))
    .map((subject) => ({
      value: subject.id,
      label: `${subject.subjectCode} · ${subject.subjectName}`,
    })),
)
const projectLabel = (id: string) =>
  projects.value.find((project) => project.id === id)?.projectName ?? '未识别项目'
const costSubjectLabel = (id: string, name?: string | null) => {
  if (name) return name
  const subject = costSubjects.value.find((item) => item.id === id)
  return subject ? `${subject.subjectCode} · ${subject.subjectName}` : '未识别成本科目'
}
const approvalStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}
const budgetStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '已启用',
  SUPERSEDED: '已替代',
  CLOSED: '已关闭',
}
const statusOptions = [
  { value: '', label: '全部状态' },
  ...Object.entries(budgetStatusLabels).map(([value, label]) => ({ value, label })),
]
const approvalStatusLabel = (value: string) => approvalStatusLabels[value] ?? '未知状态'
const budgetStatusLabel = (value: string) => budgetStatusLabels[value] ?? '未知状态'
const approvalStatusTone = (value: string) =>
  value === 'APPROVED'
    ? 'success'
    : value === 'REJECTED'
      ? 'danger'
      : value === 'APPROVING'
        ? 'warning'
        : 'neutral'
const budgetStatusTone = (value: string) => (value === 'ACTIVE' ? 'success' : 'neutral')
const decimal = (v: string) => /^(?:0|[1-9]\d*)(?:\.\d+)?$/.test(v) && !/^0(?:\.0+)?$/.test(v)
const errorText = (e: unknown, f: string) =>
  isApiClientError(e) ? e.message : e instanceof Error ? e.message : f
const needsReload = (e: unknown) => isApiClientError(e) && e.status === 409
function blank(): BudgetSaveCommand {
  return {
    projectId: '',
    versionNo: '',
    budgetName: '',
    totalAmount: '',
    version: null,
    remark: null,
  }
}
function hydrate() {
  const bounds = reportPeriodBounds(
    typeof route.query.period === 'string' ? route.query.period : null,
  )
  Object.assign(filter, {
    pageNo: Math.max(1, Number(route.query.pageNo) || 1),
    pageSize: 10,
    projectId: typeof route.query.projectId === 'string' ? route.query.projectId : undefined,
    status: typeof route.query.status === 'string' ? route.query.status : undefined,
    startDate: bounds?.startDate,
    endDate: bounds?.endDate,
  })
}
async function load() {
  if (!canQuery.value) return
  hydrate()
  controller?.abort()
  const current = new AbortController()
  controller = current
  const token = ++generation
  loading.value = true
  errorMessage.value = ''
  try {
    const [page, options, subjects] = await Promise.all([
      loadBudgetPage({ ...filter }, current.signal),
      loadProjectContextOptions(current.signal),
      loadCostSubjectOptions(current.signal),
    ])
    if (token !== generation) return
    records.value = page.records
    total.value = page.total
    projects.value = options
    costSubjects.value = subjects
  } catch (e) {
    if (!current.signal.aborted && token === generation) {
      records.value = []
      total.value = 0
      errorMessage.value = errorText(e, '项目预算加载失败')
    }
  } finally {
    if (token === generation) loading.value = false
  }
}
async function query() {
  filter.pageNo = 1
  await router.replace({
    path: '/budget',
    query: {
      ...route.query,
      status: filter.status || undefined,
      pageNo: undefined,
    },
    hash: route.hash,
  })
}
async function page(value: number) {
  await router.replace({
    query: { ...route.query, ...(value > 1 ? { pageNo: String(value) } : { pageNo: undefined }) },
    hash: route.hash,
  })
}
async function openDetail(id: string, mode: 'detail' | 'edit' = 'detail') {
  detailController?.abort()
  const current = new AbortController()
  detailController = current
  const token = ++detailGeneration
  dialog.value = mode
  detailLoading.value = true
  errorMessage.value = ''
  try {
    const [value, balance] = await Promise.all([
      loadBudget(id, current.signal),
      loadBudgetAvailability(id, current.signal),
    ])
    if (token !== detailGeneration) return
    detail.value = value
    availability.value = balance
    lines.value = (value.lines ?? []).map((row) => ({ ...row }))
    Object.assign(form, {
      projectId: value.projectId,
      versionNo: value.versionNo,
      budgetName: value.budgetName,
      totalAmount: value.totalAmount,
      version: value.version ?? null,
      remark: value.remark ?? null,
    })
  } catch (e) {
    if (!current.signal.aborted && token === detailGeneration) {
      detail.value = null
      availability.value = []
      errorMessage.value = errorText(e, '预算详情加载失败')
    }
  } finally {
    if (token === detailGeneration) detailLoading.value = false
  }
}
function openCreate() {
  detail.value = null
  availability.value = []
  lines.value = [{ costSubjectId: '', budgetAmount: '' }]
  Object.assign(form, blank(), { projectId: filter.projectId ?? '' })
  dialog.value = 'create'
}
async function run(
  action: () => Promise<unknown>,
  success: string,
  options: { refreshDetailId?: string } = {},
) {
  if (actionBusy.value) return
  actionBusy.value = true
  errorMessage.value = ''
  try {
    await action()
    successMessage.value = success
    await load()
    if (options.refreshDetailId) await openDetail(options.refreshDetailId, 'detail')
    else dialog.value = 'closed'
  } catch (e) {
    const message = errorText(e, '预算操作失败')
    if (needsReload(e)) {
      await load()
      if (detail.value) await openDetail(detail.value.id, 'detail')
    }
    errorMessage.value = message
  } finally {
    actionBusy.value = false
  }
}
async function save() {
  if (
    !form.projectId ||
    !form.versionNo.trim() ||
    !form.budgetName.trim() ||
    !decimal(form.totalAmount)
  ) {
    errorMessage.value = '请完整填写预算版本与正数金额'
    return
  }
  const command = { ...form, versionNo: form.versionNo.trim(), budgetName: form.budgetName.trim() }
  if (dialog.value === 'create')
    await run(async () => {
      const id = await createBudget(command)
      if (lines.value.some((row) => row.costSubjectId && decimal(row.budgetAmount)))
        await saveBudgetLines(
          id,
          lines.value.filter((row) => row.costSubjectId && decimal(row.budgetAmount)),
          '0',
        )
    }, '预算已创建')
  else if (detail.value) await run(() => updateBudget(detail.value!.id, command), '预算已更新')
}
async function saveLines() {
  if (
    !detail.value ||
    lines.value.length === 0 ||
    lines.value.some((row) => !row.costSubjectId || !decimal(row.budgetAmount))
  ) {
    errorMessage.value = '预算明细必须完整且金额大于0'
    return
  }
  await run(
    () => saveBudgetLines(detail.value!.id, lines.value, detail.value!.version ?? ''),
    '预算明细已保存',
    { refreshDetailId: detail.value.id },
  )
}
function addLine() {
  lines.value.push({ costSubjectId: '', budgetAmount: '' })
}
watch(() => route.fullPath, load, { immediate: true })
onBeforeUnmount(() => {
  controller?.abort()
  detailController?.abort()
})
</script>
<template>
  <div class="budget-page">
    <V2PageState
      v-if="!canQuery"
      title="无权访问项目预算"
      description="系统未加载预算业务数据。"
      kind="forbidden"
    /><template v-else
      ><V2Alert v-if="errorMessage" tone="danger" title="预算操作未完成">{{ errorMessage }}</V2Alert
      ><V2Card title="项目预算" :heading-level="1"
        ><template #actions
          ><V2Button v-if="canAdd" variant="secondary" @click="openCreate"
            >新建预算</V2Button
          ></template
        >
        <div class="filters">
          <V2Select
            v-model="filter.status"
            label="状态"
            hide-label
            :options="statusOptions"
            allow-empty
            placeholder="全部状态"
          /><V2Button class="budget-query" variant="secondary" :loading="loading" @click="query"
            >查询</V2Button
          >
        </div>
        <V2PageState
          v-if="loading && !records.length"
          title="正在加载项目预算"
          description="正在读取当前项目和报告期内的预算版本。"
          kind="loading"
        /><V2PageState
          v-else-if="!records.length"
          title="暂无项目预算"
          description="当前筛选条件下没有可访问的预算版本。"
          kind="empty"
        />
        <div
          v-else
          class="table-wrap"
          role="region"
          aria-label="项目预算列表"
          tabindex="0"
          :aria-busy="loading"
        >
          <table>
            <caption class="v2-visually-hidden">
              项目预算列表
            </caption>
            <thead>
              <tr>
                <th scope="col">预算名称</th>
                <th scope="col">版本</th>
                <th scope="col">预算总额</th>
                <th scope="col">审批状态</th>
                <th scope="col">业务状态</th>
                <th scope="col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in records" :key="row.id">
                <td>{{ row.budgetName }}</td>
                <td>{{ row.versionNo }}</td>
                <td>{{ row.totalAmount }}</td>
                <td>
                  <V2Badge :tone="approvalStatusTone(row.approvalStatus)">{{
                    approvalStatusLabel(row.approvalStatus)
                  }}</V2Badge>
                </td>
                <td>
                  <V2Badge :tone="budgetStatusTone(row.status)">{{
                    budgetStatusLabel(row.status)
                  }}</V2Badge>
                </td>
                <td>
                  <div class="actions">
                    <V2Button size="small" variant="secondary" @click="openDetail(row.id)"
                      >详情</V2Button
                    ><V2Button
                      v-if="canEdit && ['DRAFT', 'REJECTED'].includes(row.approvalStatus)"
                      size="small"
                      variant="secondary"
                      @click="openDetail(row.id, 'edit')"
                      >编辑</V2Button
                    ><V2Button
                      v-if="canSubmit && ['DRAFT', 'REJECTED'].includes(row.approvalStatus)"
                      size="small"
                      variant="secondary"
                      :disabled="actionBusy"
                      @click="run(() => submitBudget(row.id, row.version ?? ''), '预算已提交')"
                      >提交</V2Button
                    ><V2Button
                      v-if="canDelete && row.approvalStatus === 'DRAFT'"
                      size="small"
                      variant="danger"
                      :disabled="actionBusy"
                      @click="run(() => deleteBudget(row.id, row.version ?? ''), '预算已删除')"
                      >删除</V2Button
                    >
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template v-if="records.length" #footer>
          <nav aria-label="项目预算分页">
            <span>共 {{ total }} 条</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="(filter.pageNo || 1) <= 1"
              @click="page((filter.pageNo || 1) - 1)"
              >上一页</V2Button
            ><span>第 {{ filter.pageNo }} 页</span
            ><V2Button
              size="small"
              variant="secondary"
              :disabled="(filter.pageNo || 1) >= pageCount"
              @click="page((filter.pageNo || 1) + 1)"
              >下一页</V2Button
            >
          </nav>
        </template></V2Card
      >
      <V2Dialog
        :open="dialog !== 'closed'"
        :title="dialog === 'create' ? '新建预算' : dialog === 'edit' ? '编辑预算' : '预算详情'"
        :panel-class="dialog === 'detail' ? 'v2-detail-dialog' : undefined"
        :close-on-backdrop="false"
        :close-disabled="actionBusy"
        @close="dialog = 'closed'"
        ><V2PageState
          v-if="detailLoading"
          title="正在加载预算详情"
          description="正在读取预算明细和服务端可用余额。"
          kind="loading"
        />
        <form
          v-else-if="dialog === 'create' || dialog === 'edit'"
          id="budget-form"
          class="form"
          @submit.prevent="save"
        >
          <V2Select
            v-model="form.projectId"
            label="项目"
            :options="projectOptions"
            :disabled="dialog === 'edit'"
          /><V2Input v-model="form.versionNo" label="预算版本号" required /><V2Input
            v-model="form.budgetName"
            label="预算名称"
            required
          /><V2Input v-model="form.totalAmount" label="预算总额" required />
          <div v-if="dialog === 'create'" class="lines">
            <div v-for="(line, index) in lines" :key="index" class="line">
              <V2Select
                v-model="line.costSubjectId"
                label="成本科目"
                :options="costSubjectOptions"
              /><V2Input v-model="line.budgetAmount" label="预算金额" />
            </div>
            <V2GlassButton text="添加明细" :on-click="addLine" />
          </div>
        </form>
        <div v-else-if="detail" class="form">
          <dl class="v2-detail-dialog__facts">
            <dt>预算名称</dt>
            <dd>{{ detail.budgetName }}</dd>
            <dt>项目</dt>
            <dd>{{ projectLabel(detail.projectId) }}</dd>
            <dt>预算版本</dt>
            <dd>{{ detail.versionNo }}</dd>
            <dt>预算总额</dt>
            <dd>{{ detail.totalAmount }}</dd>
            <dt>审批状态</dt>
            <dd>{{ approvalStatusLabel(detail.approvalStatus) }}</dd>
            <dt>预算状态</dt>
            <dd>{{ budgetStatusLabel(detail.status) }}</dd>
          </dl>
          <div
            v-if="canEdit && ['DRAFT', 'REJECTED'].includes(detail.approvalStatus)"
            class="lines"
          >
            <div v-for="(line, index) in lines" :key="line.id || index" class="line">
              <V2Select
                v-model="line.costSubjectId"
                label="成本科目"
                :options="costSubjectOptions"
              /><V2Input v-model="line.budgetAmount" label="预算金额" />
            </div>
            <V2GlassButton text="添加明细" :on-click="addLine" /><V2GlassButton
              text="保存明细"
              :loading="actionBusy"
              :on-click="saveLines"
            />
          </div>
          <div class="table-wrap" role="region" aria-label="预算可用额" tabindex="0">
            <table>
              <thead>
                <tr>
                  <th>科目</th>
                  <th>预算</th>
                  <th>占用</th>
                  <th>消耗</th>
                  <th>可用</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in availability" :key="row.budgetLineId">
                  <td>{{ costSubjectLabel(row.costSubjectId) }}</td>
                  <td>{{ row.budgetAmount }}</td>
                  <td>{{ row.reservedAmount }}</td>
                  <td>{{ row.consumedAmount }}</td>
                  <td>{{ row.availableAmount }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <template v-if="dialog === 'create' || dialog === 'edit'" #footer>
          <V2GlassButton text="取消" :disabled="actionBusy" :on-click="() => (dialog = 'closed')" />
          <V2Button type="submit" form="budget-form" :loading="actionBusy">保存预算</V2Button>
        </template></V2Dialog
      ></template
    >
  </div>
</template>
<style scoped>
.budget-page,
.form,
.lines {
  display: grid;
  gap: var(--v2-space-4);
}
.filters,
.line {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: var(--v2-space-3);
  align-items: end;
}
.filters {
  grid-template-columns: minmax(12rem, 1fr) auto;
}
.actions {
  display: flex;
  gap: var(--v2-space-2);
  flex-wrap: wrap;
}
.budget-query {
  color: var(--v2-color-text-strong);
}
dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--v2-space-2) var(--v2-space-4);
  margin: 0;
}
dd {
  margin: 0;
}
.table-wrap {
  min-width: 0;
  overflow-x: auto;
}
table {
  min-width: 48rem;
}
nav {
  display: flex;
  gap: var(--v2-space-2);
  align-items: center;
  justify-content: flex-end;
}
@media (max-width: 48rem) {
  .filters,
  .line {
    grid-template-columns: 1fr;
  }
}
</style>
