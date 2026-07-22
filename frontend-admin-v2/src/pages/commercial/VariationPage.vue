<script setup lang="ts">
import type {
  VariationItemRecord,
  VariationOwnerReviewCommand,
  VariationOwnerSubmissionRecord,
  VariationPage,
  VariationQuery,
  VariationRecord,
  VariationSaveCommand,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  V2Alert,
  V2Badge,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Input,
  V2PageState,
  V2Select,
} from '@/components'
import { formatAmount } from '@/pages/dashboard/model'
import {
  createVariation,
  deleteVariation,
  loadVariation,
  loadVariationPage,
  loadVariationTrace,
  saveVariationItems,
  submitVariation,
  submitVariationToOwner,
  updateVariation,
  reviewVariationOwner,
} from '@/services/commercial'
import { uploadSiteFile } from '@/services/delivery'
import { isApiClientError } from '@/services/request'
import { reportPeriodBounds } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'

type WorkspaceMode = 'list' | 'create' | 'detail' | 'edit'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const loading = ref(false)
const action = ref('')
const errorMessage = ref('')
const successMessage = ref('')
const records = ref<VariationPage['records']>([])
const total = ref(0)
const detail = ref<VariationRecord | null>(null)
const trace = ref<Array<{ key: string; value: string }>>([])
const form = ref<VariationSaveCommand>(emptyForm())
const items = ref<VariationItemRecord[]>([])
const siteEvidenceFile = ref<File | null>(null)
const ownerFile = ref<File | null>(null)
const externalDocumentNo = ref('')
const responseDocumentNo = ref('')
const responseComment = ref('')
const ownerConclusion = ref<'CONFIRMED' | 'RETURNED'>('CONFIRMED')
const reviewLines = ref<VariationOwnerReviewCommand['items']>([])
const pendingDelete = ref(false)
const filter = reactive<VariationQuery>({
  pageNo: 1,
  pageSize: 10,
  projectId: '',
  varCode: '',
  varType: '',
  direction: '',
  startDate: undefined,
  endDate: undefined,
})

let listGeneration = 0
let detailGeneration = 0
let listController: AbortController | null = null
let detailController: AbortController | null = null

const mode = computed<WorkspaceMode>(() => {
  const requested = typeof route.query.mode === 'string' ? route.query.mode : ''
  return requested === 'create' || requested === 'detail' || requested === 'edit'
    ? requested
    : 'list'
})
const variationId = computed(() =>
  typeof route.query.id === 'string' ? route.query.id.trim() : '',
)
const busy = computed(() => Boolean(action.value))
const canCreate = computed(() => session.hasPermission('variation:order:add'))
const canEdit = computed(() => session.hasPermission('variation:order:edit'))
const canEditItems = computed(() => session.hasPermission('variation:order:item:edit'))
const canDelete = computed(() => session.hasPermission('variation:order:delete'))
const canSubmit = computed(() => session.hasPermission('variation:order:submit'))
const canOwnerSubmit = computed(() => session.hasPermission('variation:owner:submit'))
const canOwnerReview = computed(() => session.hasPermission('variation:owner:review'))
const canTrace = computed(() => session.hasPermission('variation:trace'))
const isDraft = computed(() => detail.value?.approvalStatus === 'DRAFT')
const latestSubmission = computed<VariationOwnerSubmissionRecord | null>(
  () => detail.value?.ownerSubmissions?.at(-1) ?? null,
)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / (filter.pageSize ?? 10))))

const APPROVAL_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
}

const OWNER_STATUS_LABELS: Record<string, string> = {
  NOT_READY: '未就绪',
  NOT_SUBMITTED: '未申报',
  INTERNAL_APPROVED: '内部已通过',
  OWNER_SUBMITTED: '已申报',
  OWNER_RETURNED: '业主退回',
  CHANGE_PENDING: '合同变更审批中',
  CHANGE_EFFECTIVE: '已生效',
}

function approvalStatusLabel(value?: string | null): string {
  return (value && APPROVAL_STATUS_LABELS[value]) || '未知状态'
}

function ownerStatusLabel(value?: string | null): string {
  return (value && OWNER_STATUS_LABELS[value]) || '未知状态'
}

function emptyForm(): VariationSaveCommand {
  return {
    projectId: typeof route.query.projectId === 'string' ? route.query.projectId.trim() : '',
    contractId: '',
    partnerId: null,
    varName: '',
    eventDate: null,
    claimDeadline: null,
    eventDescription: null,
    causeCategory: null,
    responsibleParty: null,
    businessMatterKey: null,
    varType: 'OTHER',
    direction: 'COST',
    impactDays: null,
    version: null,
    remark: null,
  }
}

function blankItem(): VariationItemRecord {
  return {
    itemName: '',
    unit: null,
    quantity: '',
    unitPrice: null,
    amount: null,
    claimUnitPrice: null,
    claimAmount: null,
    costSubjectId: '',
    remark: null,
  }
}

function hydrateFilter(): void {
  filter.projectId = textQuery('projectId')
  filter.varCode = textQuery('varCode')
  filter.varType = textQuery('varType')
  filter.direction = textQuery('direction')
  const periodBounds = reportPeriodBounds(textQuery('period'))
  filter.startDate = periodBounds?.startDate
  filter.endDate = periodBounds?.endDate
  const pageNo = Number(textQuery('pageNo') || '1')
  filter.pageNo = Number.isInteger(pageNo) && pageNo > 0 ? pageNo : 1
}

function textQuery(key: string): string {
  return typeof route.query[key] === 'string' ? route.query[key].trim() : ''
}

function resetNotices(): void {
  errorMessage.value = ''
  successMessage.value = ''
}

function errorText(error: unknown, fallback: string): string {
  if (isApiClientError(error)) return error.message
  return error instanceof Error ? error.message : fallback
}

async function replaceListQuery(): Promise<boolean> {
  const location = {
    path: '/variation/order',
    query: {
      ...(filter.projectId ? { projectId: filter.projectId } : {}),
      ...(filter.varCode ? { varCode: filter.varCode } : {}),
      ...(filter.varType ? { varType: filter.varType } : {}),
      ...(filter.direction ? { direction: filter.direction } : {}),
      ...(textQuery('period') ? { period: textQuery('period') } : {}),
      ...(filter.pageNo && filter.pageNo > 1 ? { pageNo: String(filter.pageNo) } : {}),
    },
  }
  if (router.resolve(location).fullPath === route.fullPath) return false
  await router.replace(location)
  return true
}

async function loadList(preserveNotice = false): Promise<void> {
  hydrateFilter()
  listController?.abort()
  const controller = new AbortController()
  listController = controller
  const generation = ++listGeneration
  loading.value = true
  if (!preserveNotice) resetNotices()
  try {
    const page = await loadVariationPage(filter, controller.signal)
    if (generation !== listGeneration) return
    records.value = page.records
    total.value = page.total
  } catch (error) {
    if (!controller.signal.aborted && generation === listGeneration) {
      records.value = []
      total.value = 0
      errorMessage.value = errorText(error, '变更签证台账加载失败')
    }
  } finally {
    if (generation === listGeneration) loading.value = false
  }
}

async function loadDetail(preserveNotice = false): Promise<void> {
  if (!variationId.value) {
    detail.value = null
    errorMessage.value = '缺少变更签证 ID'
    return
  }
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const generation = ++detailGeneration
  loading.value = true
  if (!preserveNotice) resetNotices()
  try {
    const value = await loadVariation(variationId.value, controller.signal)
    if (generation !== detailGeneration) return
    detail.value = value
    items.value = (value.items ?? []).map((item) => ({ ...item }))
    if (mode.value === 'edit') form.value = formFromDetail(value)
    prepareReview(value.ownerSubmissions?.at(-1) ?? null)
  } catch (error) {
    if (!controller.signal.aborted && generation === detailGeneration) {
      detail.value = null
      errorMessage.value = errorText(error, '变更签证详情加载失败')
    }
  } finally {
    if (generation === detailGeneration) loading.value = false
  }
}

function formFromDetail(value: VariationRecord): VariationSaveCommand {
  return {
    projectId: value.projectId,
    contractId: value.contractId ?? '',
    partnerId: value.partnerId ?? null,
    varName: value.varName,
    eventDate: value.eventDate ?? null,
    claimDeadline: value.claimDeadline ?? null,
    eventDescription: value.eventDescription ?? null,
    causeCategory: value.causeCategory ?? null,
    responsibleParty: value.responsibleParty ?? null,
    businessMatterKey: value.businessMatterKey ?? null,
    varType: value.varType ?? 'OTHER',
    direction: value.direction ?? 'COST',
    impactDays: value.impactDays ?? null,
    version: value.version ?? null,
    remark: value.remark ?? null,
  }
}

function cleaned(value?: string | null): string | null {
  const normalized = value?.trim()
  return normalized ? normalized : null
}

function cleanForm(): VariationSaveCommand {
  return {
    ...form.value,
    projectId: form.value.projectId.trim(),
    contractId: form.value.contractId.trim(),
    partnerId: cleaned(form.value.partnerId),
    varName: form.value.varName.trim(),
    eventDate: cleaned(form.value.eventDate),
    claimDeadline: cleaned(form.value.claimDeadline),
    eventDescription: cleaned(form.value.eventDescription),
    causeCategory: cleaned(form.value.causeCategory),
    responsibleParty: cleaned(form.value.responsibleParty),
    businessMatterKey: cleaned(form.value.businessMatterKey),
    varType: form.value.varType.trim(),
    direction: cleaned(form.value.direction),
    remark: cleaned(form.value.remark),
  }
}

function versionOf(value = detail.value): string | number {
  const version = value?.version
  if (version == null || String(version).trim() === '')
    throw new TypeError('缺少最新版本，请刷新后重试')
  return version
}

async function runAction(name: string, operation: () => Promise<void>): Promise<void> {
  if (busy.value) return
  action.value = name
  resetNotices()
  try {
    await operation()
  } catch (error) {
    errorMessage.value = errorText(error, `${name}失败`)
    if (variationId.value) await loadDetail(true)
  } finally {
    action.value = ''
  }
}

async function saveForm(): Promise<void> {
  await runAction('保存', async () => {
    const command = cleanForm()
    if (!command.projectId || !command.contractId || !command.varName || !command.varType) {
      throw new TypeError('项目、合同、变更名称和类型不能为空')
    }
    if (mode.value === 'create') {
      const id = await createVariation(command)
      await router.replace({
        path: '/variation/order',
        query: { ...route.query, id, mode: 'detail' },
      })
      successMessage.value = '变更签证已创建。'
      return
    }
    await updateVariation(variationId.value, command)
    await loadDetail(true)
    successMessage.value = '变更签证已保存并刷新。'
  })
}

function cleanItems(): VariationItemRecord[] {
  return items.value.map((item) => ({
    ...item,
    id: cleaned(item.id),
    varOrderId: variationId.value,
    itemName: item.itemName.trim(),
    unit: cleaned(item.unit),
    quantity: item.quantity.trim(),
    unitPrice: cleaned(item.unitPrice),
    amount: cleaned(item.amount),
    claimUnitPrice: cleaned(item.claimUnitPrice),
    claimAmount: cleaned(item.claimAmount),
    costSubjectId: item.costSubjectId.trim(),
    remark: cleaned(item.remark),
  }))
}

async function saveItems(): Promise<void> {
  await runAction('保存明细', async () => {
    const command = cleanItems()
    if (command.some((item) => !item.itemName || !item.quantity || !item.costSubjectId)) {
      throw new TypeError('明细名称、数量和成本科目 ID 不能为空')
    }
    await saveVariationItems(variationId.value, command, versionOf())
    await loadDetail(true)
    successMessage.value = '变更明细已保存并刷新。'
  })
}

async function submitApproval(): Promise<void> {
  await runAction('提交审批', async () => {
    if (siteEvidenceFile.value) {
      await uploadSiteFile(siteEvidenceFile.value, 'VARIATION', variationId.value, 'SITE_EVIDENCE')
      siteEvidenceFile.value = null
    }
    await submitVariation(variationId.value, versionOf())
    await loadDetail(true)
    successMessage.value = '变更签证已提交审批。'
  })
}

async function removeVariation(): Promise<void> {
  pendingDelete.value = true
}

async function confirmDelete(): Promise<void> {
  await runAction('删除', async () => {
    await deleteVariation(variationId.value, versionOf())
    pendingDelete.value = false
    await router.replace('/variation/order')
    successMessage.value = '变更签证已删除。'
  })
}

function onOwnerFile(event: Event): void {
  ownerFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

function onSiteEvidence(event: Event): void {
  siteEvidenceFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function submitOwner(): Promise<void> {
  await runAction('业主申报', async () => {
    const file = ownerFile.value
    if (!file) throw new TypeError('请上传本版业主申报文件')
    if (!externalDocumentNo.value.trim()) throw new TypeError('对外发文号不能为空')
    await uploadSiteFile(file, 'VARIATION', variationId.value, 'OWNER_SUBMISSION')
    await submitVariationToOwner(
      variationId.value,
      {
        externalDocumentNo: externalDocumentNo.value.trim(),
        submittedAt: new Date().toISOString(),
      },
      versionOf(),
    )
    ownerFile.value = null
    externalDocumentNo.value = ''
    await loadDetail(true)
    successMessage.value = '业主申报已登记。'
  })
}

function prepareReview(submission: VariationOwnerSubmissionRecord | null): void {
  reviewLines.value = (submission?.items ?? []).map((item) => ({
    submissionItemId: item.id,
    confirmedAmount: item.confirmed_amount ?? item.claimed_amount ?? '',
    reductionReason: item.reduction_reason ?? null,
  }))
}

async function reviewOwner(): Promise<void> {
  await runAction('业主回复', async () => {
    const submission = latestSubmission.value
    const file = ownerFile.value
    if (!submission) throw new TypeError('未找到待核定业主申报')
    if (!file) throw new TypeError('请上传本版业主回复文件')
    if (!responseDocumentNo.value.trim()) throw new TypeError('业主回复文号不能为空')
    const reviewItems = ownerConclusion.value === 'RETURNED' ? [] : reviewLines.value
    if (reviewItems.some((item) => !item.confirmedAmount.trim())) {
      throw new TypeError('核定金额不能为空')
    }
    await uploadSiteFile(file, 'VARIATION', variationId.value, 'OWNER_CONFIRMATION')
    await reviewVariationOwner(
      variationId.value,
      submission.id,
      {
        conclusion: ownerConclusion.value,
        responseDocumentNo: responseDocumentNo.value.trim(),
        responseComment: cleaned(responseComment.value),
        reviewedAt: new Date().toISOString(),
        items: reviewItems,
      },
      versionOf(),
    )
    ownerFile.value = null
    responseDocumentNo.value = ''
    responseComment.value = ''
    await loadDetail(true)
    successMessage.value = '业主回复已登记，合同金额以系统结果为准。'
  })
}

async function showTrace(): Promise<void> {
  await runAction('加载追溯', async () => {
    const value = await loadVariationTrace(variationId.value)
    trace.value = Object.entries(value).map(([key, item]) => ({
      key,
      value: typeof item === 'string' ? item : JSON.stringify(item),
    }))
  })
}

function updateForm(key: keyof VariationSaveCommand, value: string): void {
  if (key === 'impactDays') {
    form.value = { ...form.value, impactDays: value.trim() ? Number(value) : null }
    return
  }
  form.value = { ...form.value, [key]: value }
}

function updateItem(index: number, key: keyof VariationItemRecord, value: string): void {
  items.value = items.value.map((item, itemIndex) =>
    itemIndex === index ? { ...item, [key]: value } : item,
  )
}

function updateReviewLine(
  index: number,
  key: 'confirmedAmount' | 'reductionReason',
  value: string,
): void {
  reviewLines.value = reviewLines.value.map((item, itemIndex) =>
    itemIndex === index ? { ...item, [key]: value } : item,
  )
}

async function openWorkspace(nextMode: Exclude<WorkspaceMode, 'list'>, id?: string): Promise<void> {
  await router.push({
    path: '/variation/order',
    query: {
      ...route.query,
      mode: nextMode,
      ...(id ? { id } : {}),
    },
  })
}

async function backToList(): Promise<void> {
  const query = { ...route.query }
  delete query.mode
  delete query.id
  await router.push({ path: '/variation/order', query })
}

async function search(): Promise<void> {
  filter.pageNo = 1
  if (!(await replaceListQuery())) await loadList()
}

async function changePage(pageNo: number): Promise<void> {
  filter.pageNo = pageNo
  if (!(await replaceListQuery())) await loadList()
}

watch(
  () => route.fullPath,
  () => {
    trace.value = []
    if (mode.value === 'list') void loadList()
    else if (mode.value === 'create') {
      detailController?.abort()
      detail.value = null
      items.value = []
      form.value = emptyForm()
      resetNotices()
    } else void loadDetail()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  listController?.abort()
  detailController?.abort()
})
</script>

<template>
  <div class="variation-page">
    <V2Alert
      v-if="errorMessage"
      tone="danger"
      title="操作未完成"
      dismissible
      @dismiss="errorMessage = ''"
      >{{ errorMessage }}</V2Alert
    >
    <V2Alert
      v-if="successMessage"
      tone="success"
      title="操作完成"
      dismissible
      @dismiss="successMessage = ''"
      >{{ successMessage }}</V2Alert
    >

    <V2Card v-if="mode === 'list'" title="变更签证" :heading-level="1">
      <template #actions>
        <V2Button v-if="canCreate" @click="openWorkspace('create')">新建变更</V2Button>
      </template>
      <form class="variation-page__filters" @submit.prevent="search">
        <V2Input v-model="filter.projectId" label="项目 ID" />
        <V2Input v-model="filter.varCode" label="变更编号" />
        <V2Select
          v-model="filter.varType"
          label="变更类型"
          allow-empty
          :options="[
            { value: '', label: '全部类型' },
            { value: 'DESIGN', label: '设计变更' },
            { value: 'SITE', label: '现场签证' },
            { value: 'OTHER', label: '其他' },
          ]"
        />
        <V2Select
          v-model="filter.direction"
          label="方向"
          allow-empty
          :options="[
            { value: '', label: '全部方向' },
            { value: 'COST', label: '成本' },
            { value: 'INCOME', label: '收入' },
          ]"
        />
        <V2Button type="submit" :loading="loading">查询</V2Button>
      </form>
      <V2PageState
        v-if="loading && !records.length"
        kind="loading"
        title="正在加载变更签证"
        description="请稍候。"
      />
      <V2PageState
        v-else-if="!records.length"
        title="暂无变更签证"
        description="当前筛选条件下没有数据。"
      />
      <div v-else class="variation-page__table-wrap">
        <table>
          <thead>
            <tr>
              <th>编号</th>
              <th>名称</th>
              <th>项目</th>
              <th>合同</th>
              <th>申报金额</th>
              <th>审批</th>
              <th>业主</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in records" :key="record.id">
              <td>
                <strong>{{ record.varCode }}</strong>
              </td>
              <td>{{ record.varName }}</td>
              <td>{{ record.projectName || record.projectId }}</td>
              <td>{{ record.contractName || record.contractId || '—' }}</td>
              <td>{{ formatAmount(record.reportedAmount) }}</td>
              <td>
                <V2Badge>{{ approvalStatusLabel(record.approvalStatus) }}</V2Badge>
              </td>
              <td>{{ ownerStatusLabel(record.ownerStatus) }}</td>
              <td class="variation-page__actions">
                <V2Button
                  size="small"
                  variant="secondary"
                  @click="openWorkspace('detail', record.id)"
                  >查看</V2Button
                ><V2Button
                  v-if="canEdit && record.approvalStatus === 'DRAFT'"
                  size="small"
                  variant="ghost"
                  @click="openWorkspace('edit', record.id)"
                  >编辑</V2Button
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="variation-page__pager">
        <span>共 {{ total }} 条</span
        ><V2Button
          size="small"
          variant="secondary"
          :disabled="(filter.pageNo ?? 1) <= 1 || loading"
          @click="changePage((filter.pageNo ?? 1) - 1)"
          >上一页</V2Button
        ><span>第 {{ filter.pageNo }} 页</span
        ><V2Button
          size="small"
          variant="secondary"
          :disabled="(filter.pageNo ?? 1) >= pageCount || loading"
          @click="changePage((filter.pageNo ?? 1) + 1)"
          >下一页</V2Button
        >
      </div>
    </V2Card>

    <V2Card
      v-else-if="mode === 'create' || mode === 'edit'"
      :title="mode === 'create' ? '新建变更签证' : '编辑变更签证'"
      :heading-level="1"
    >
      <template #actions
        ><V2Button variant="secondary" @click="backToList">返回台账</V2Button></template
      >
      <V2PageState v-if="loading" kind="loading" title="正在加载变更签证" description="请稍候。" />
      <form v-else class="variation-page__form" @submit.prevent="saveForm">
        <V2Input
          :model-value="form.projectId"
          label="项目 ID"
          required
          :disabled="mode === 'edit'"
          @update:model-value="updateForm('projectId', $event)"
        />
        <V2Input
          :model-value="form.contractId"
          label="合同 ID"
          required
          :disabled="mode === 'edit'"
          @update:model-value="updateForm('contractId', $event)"
        />
        <V2Input
          :model-value="form.partnerId ?? ''"
          label="往来单位 ID"
          @update:model-value="updateForm('partnerId', $event)"
        />
        <V2Input
          :model-value="form.varName"
          label="变更名称"
          required
          @update:model-value="updateForm('varName', $event)"
        />
        <V2Select
          :model-value="form.varType"
          label="变更类型"
          required
          :options="[
            { value: 'DESIGN', label: '设计变更' },
            { value: 'SITE', label: '现场签证' },
            { value: 'OTHER', label: '其他' },
          ]"
          @update:model-value="updateForm('varType', $event)"
        />
        <V2Select
          :model-value="form.direction ?? ''"
          label="方向"
          :options="[
            { value: 'COST', label: '成本' },
            { value: 'INCOME', label: '收入' },
          ]"
          @update:model-value="updateForm('direction', $event)"
        />
        <V2Input
          :model-value="form.eventDate ?? ''"
          label="发生日期"
          placeholder="YYYY-MM-DD"
          @update:model-value="updateForm('eventDate', $event)"
        />
        <V2Input
          :model-value="form.claimDeadline ?? ''"
          label="申报截止日"
          placeholder="YYYY-MM-DD"
          @update:model-value="updateForm('claimDeadline', $event)"
        />
        <V2Input
          :model-value="String(form.impactDays ?? '')"
          label="影响工期（天）"
          @update:model-value="updateForm('impactDays', $event)"
        />
        <V2Input
          :model-value="form.causeCategory ?? ''"
          label="原因分类"
          @update:model-value="updateForm('causeCategory', $event)"
        />
        <V2Input
          :model-value="form.responsibleParty ?? ''"
          label="责任方"
          @update:model-value="updateForm('responsibleParty', $event)"
        />
        <V2Input
          :model-value="form.businessMatterKey ?? ''"
          label="业务事项键"
          @update:model-value="updateForm('businessMatterKey', $event)"
        />
        <label class="variation-page__native-field variation-page__wide"
          >事件说明<textarea
            :value="form.eventDescription ?? ''"
            @input="updateForm('eventDescription', ($event.target as HTMLTextAreaElement).value)"
          />
        </label>
        <label class="variation-page__native-field variation-page__wide"
          >备注<textarea
            :value="form.remark ?? ''"
            @input="updateForm('remark', ($event.target as HTMLTextAreaElement).value)"
          />
        </label>
        <div class="variation-page__wide variation-page__actions">
          <V2Button type="submit" :loading="action === '保存'" :disabled="busy">保存</V2Button
          ><V2Button
            variant="secondary"
            :disabled="busy"
            @click="mode === 'edit' ? openWorkspace('detail', variationId) : backToList()"
            >取消</V2Button
          >
        </div>
      </form>
    </V2Card>

    <template v-else>
      <V2PageState
        v-if="loading && !detail"
        kind="loading"
        title="正在加载变更签证"
        description="请稍候。"
      />
      <V2PageState
        v-else-if="!detail"
        kind="error"
        code="404"
        title="变更签证不可访问"
        :description="errorMessage || '记录不存在或当前账号无权查看。'"
      >
        <template #actions><V2Button @click="backToList">返回台账</V2Button></template>
      </V2PageState>
      <template v-else>
        <V2Card :title="detail.varName" :subtitle="detail.varCode" :heading-level="1">
          <template #actions
            ><div class="variation-page__actions">
              <V2Button variant="secondary" @click="backToList">返回台账</V2Button
              ><V2Button
                v-if="canEdit && isDraft"
                variant="secondary"
                @click="openWorkspace('edit', detail.id)"
                >编辑</V2Button
              ><V2Button
                v-if="canSubmit && isDraft"
                :loading="action === '提交审批'"
                :disabled="busy"
                @click="submitApproval"
                >提交审批</V2Button
              ><V2Button
                v-if="canDelete && isDraft"
                variant="danger"
                :loading="action === '删除'"
                :disabled="busy"
                @click="removeVariation"
                >删除</V2Button
              >
            </div></template
          >
          <dl class="variation-page__detail-grid">
            <dt>项目</dt>
            <dd>{{ detail.projectName || detail.projectId }}</dd>
            <dt>合同</dt>
            <dd>{{ detail.contractName || detail.contractId || '—' }}</dd>
            <dt>审批状态</dt>
            <dd>{{ approvalStatusLabel(detail.approvalStatus) }}</dd>
            <dt>业主状态</dt>
            <dd>{{ ownerStatusLabel(detail.ownerStatus) }}</dd>
            <dt>申报金额</dt>
            <dd>{{ formatAmount(detail.reportedAmount) }}</dd>
            <dt>核定金额</dt>
            <dd>{{ formatAmount(detail.confirmedAmount) }}</dd>
            <dt>预计成本</dt>
            <dd>{{ formatAmount(detail.estimatedCostAmount) }}</dd>
            <dt>版本</dt>
            <dd>{{ detail.version ?? '—' }}</dd>
          </dl>
          <label v-if="canEdit && isDraft" class="variation-page__native-field">
            本次现场证据（提交审批前上传）
            <input
              id="variation-site-evidence"
              type="file"
              :disabled="busy"
              @change="onSiteEvidence"
            />
          </label>
        </V2Card>

        <V2Card title="变更明细" subtitle="维护工程量、单价和申报金额">
          <template #actions
            ><V2Button
              v-if="canEditItems && isDraft"
              size="small"
              variant="secondary"
              :disabled="busy"
              @click="items = [...items, blankItem()]"
              >添加明细</V2Button
            ></template
          >
          <V2PageState v-if="!items.length" title="暂无明细" description="草稿可添加变更明细。" />
          <div v-else class="variation-page__items">
            <div
              v-for="(item, index) in items"
              :key="item.id || index"
              class="variation-page__item"
            >
              <V2Input
                :model-value="item.itemName"
                label="明细名称"
                :disabled="!canEditItems || !isDraft"
                @update:model-value="updateItem(index, 'itemName', $event)"
              />
              <V2Input
                :model-value="item.quantity"
                label="数量"
                :disabled="!canEditItems || !isDraft"
                @update:model-value="updateItem(index, 'quantity', $event)"
              />
              <V2Input
                :model-value="item.unit ?? ''"
                label="单位"
                :disabled="!canEditItems || !isDraft"
                @update:model-value="updateItem(index, 'unit', $event)"
              />
              <V2Input
                :model-value="item.unitPrice ?? ''"
                label="单价"
                :disabled="!canEditItems || !isDraft"
                @update:model-value="updateItem(index, 'unitPrice', $event)"
              />
              <V2Input
                :model-value="item.claimUnitPrice ?? ''"
                label="申报单价"
                :disabled="!canEditItems || !isDraft"
                @update:model-value="updateItem(index, 'claimUnitPrice', $event)"
              />
              <V2Input
                :model-value="item.costSubjectId"
                label="成本科目 ID"
                :disabled="!canEditItems || !isDraft"
                @update:model-value="updateItem(index, 'costSubjectId', $event)"
              />
              <div v-if="canEditItems && isDraft" class="variation-page__actions">
                <V2Button
                  size="small"
                  variant="danger"
                  :disabled="busy"
                  @click="items = items.filter((_, itemIndex) => itemIndex !== index)"
                  >移除</V2Button
                >
              </div>
            </div>
          </div>
          <template v-if="canEditItems && isDraft && items.length" #footer
            ><V2Button :loading="action === '保存明细'" :disabled="busy" @click="saveItems"
              >保存明细</V2Button
            ></template
          >
        </V2Card>

        <V2Card
          v-if="
            canOwnerSubmit &&
            ['INTERNAL_APPROVED', 'OWNER_RETURNED'].includes(detail.ownerStatus || '')
          "
          title="提交业主申报"
          subtitle="每版申报必须上传对应往来文件"
        >
          <div class="variation-page__form">
            <V2Input v-model="externalDocumentNo" label="对外发文号" required /><label
              class="variation-page__native-field"
              >业主申报文件<input type="file" :disabled="busy" @change="onOwnerFile"
            /></label>
          </div>
          <template #footer
            ><V2Button :loading="action === '业主申报'" :disabled="busy" @click="submitOwner"
              >提交业主申报</V2Button
            ></template
          >
        </V2Card>

        <V2Card
          v-if="canOwnerReview && detail.ownerStatus === 'OWNER_SUBMITTED'"
          title="登记业主回复"
          subtitle="登记本版核定或退回结果"
        >
          <div class="variation-page__form">
            <V2Select
              v-model="ownerConclusion"
              label="业主结论"
              :options="[
                { value: 'CONFIRMED', label: '核定' },
                { value: 'RETURNED', label: '退回' },
              ]"
            /><V2Input v-model="responseDocumentNo" label="业主回复文号" required /><V2Input
              v-model="responseComment"
              label="回复说明"
            /><label class="variation-page__native-field"
              >业主回复文件<input type="file" :disabled="busy" @change="onOwnerFile"
            /></label>
          </div>
          <div v-if="ownerConclusion === 'CONFIRMED'" class="variation-page__items">
            <div
              v-for="(line, index) in reviewLines"
              :key="line.submissionItemId"
              class="variation-page__item"
            >
              <span>申报明细 {{ line.submissionItemId }}</span
              ><V2Input
                :model-value="line.confirmedAmount"
                label="核定金额"
                required
                @update:model-value="updateReviewLine(index, 'confirmedAmount', $event)"
              /><V2Input
                :model-value="line.reductionReason ?? ''"
                label="核减原因"
                @update:model-value="updateReviewLine(index, 'reductionReason', $event)"
              />
            </div>
          </div>
          <template #footer
            ><V2Button :loading="action === '业主回复'" :disabled="busy" @click="reviewOwner"
              >登记业主回复</V2Button
            ></template
          >
        </V2Card>

        <V2Card v-if="canTrace" title="全链追溯">
          <template #actions
            ><V2Button
              size="small"
              variant="secondary"
              :loading="action === '加载追溯'"
              :disabled="busy"
              @click="showTrace"
              >加载追溯</V2Button
            ></template
          >
          <V2PageState
            v-if="!trace.length"
            title="尚未加载追溯"
            description="按需读取审批、业主申报与合同变更链。"
          />
          <dl v-else class="variation-page__trace">
            <template v-for="row in trace" :key="row.key"
              ><dt>{{ row.key }}</dt>
              <dd>{{ row.value }}</dd></template
            >
          </dl>
        </V2Card>
      </template>
    </template>

    <V2ConfirmDialog
      :open="pendingDelete"
      title="删除变更签证"
      description="删除后不可恢复；仅草稿可删除。"
      confirm-text="确认删除"
      danger
      :loading="action === '删除'"
      @close="pendingDelete = false"
      @confirm="confirmDelete"
    />
  </div>
</template>

<style scoped>
.variation-page {
  display: grid;
  gap: var(--v2-space-4);
}
.variation-page__filters,
.variation-page__form {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--v2-space-3);
  align-items: end;
}
.variation-page__filters {
  grid-template-columns: repeat(4, minmax(0, 1fr)) auto;
}
.variation-page__table-wrap {
  overflow-x: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--v2-font-size-12);
  line-height: var(--v2-line-height-ui);
}
th,
td {
  padding: var(--v2-space-3);
  border-bottom: 1px solid var(--v2-color-border);
  text-align: left;
  vertical-align: middle;
  white-space: nowrap;
}
.variation-page__table-wrap table {
  min-width: 64rem;
}
.variation-page__table-wrap .variation-page__actions {
  flex-wrap: nowrap;
}
.variation-page__actions,
.variation-page__pager {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  align-items: center;
}
.variation-page__pager {
  justify-content: flex-end;
  margin-top: var(--v2-space-4);
  font-size: var(--v2-font-size-12);
  line-height: var(--v2-line-height-ui);
}
.variation-page__detail-grid,
.variation-page__trace {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--v2-space-2) var(--v2-space-4);
  margin: 0;
  font-size: var(--v2-font-size-12);
  line-height: var(--v2-line-height-ui);
}
dt {
  color: var(--v2-color-text-secondary);
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.variation-page__items {
  display: grid;
  gap: var(--v2-space-3);
}
.variation-page__item {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--v2-space-3);
  padding: var(--v2-space-3);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}
.variation-page__native-field {
  display: grid;
  gap: var(--v2-space-2);
  color: var(--v2-color-text-secondary);
}
.variation-page__native-field input,
.variation-page__native-field textarea {
  min-height: 2.75rem;
  padding: var(--v2-space-2) var(--v2-space-3);
  color: var(--v2-color-text);
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}
.variation-page__native-field textarea {
  min-height: 6rem;
  resize: vertical;
}
.variation-page__wide {
  grid-column: 1 / -1;
}
@media (max-width: 64rem) {
  .variation-page__filters,
  .variation-page__form,
  .variation-page__item {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 48rem) {
  .variation-page__filters,
  .variation-page__form,
  .variation-page__item {
    grid-template-columns: 1fr;
  }
}
</style>
