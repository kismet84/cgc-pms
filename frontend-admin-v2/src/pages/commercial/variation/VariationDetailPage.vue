<script setup lang="ts">
import type {
  VariationItemRecord,
  VariationOwnerReviewCommand,
  VariationOwnerSubmissionRecord,
  VariationRecord,
  WbsTaskRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  V2Button,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  showToast,
} from '@/components'
import { formatAmount } from '@/shared/display'
import {
  type CostSubjectOption,
  deleteVariation,
  loadCostSubjectOptions,
  loadVariation,
  loadVariationTrace,
  reviewVariationOwner,
  saveVariationItems,
  submitVariation,
  submitVariationToOwner,
} from '@/services/commercial'
import { loadSchedule, loadSchedules, uploadSiteFile } from '@/services/delivery'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const detail = ref<VariationRecord | null>(null)
const items = ref<VariationItemRecord[]>([])
const trace = ref<Array<{ key: string; value: string }>>([])
const costSubjects = ref<CostSubjectOption[]>([])
const wbsTasks = ref<WbsTaskRecord[]>([])
const siteEvidenceFile = ref<File | null>(null)
const ownerFile = ref<File | null>(null)
const externalDocumentNo = ref('')
const responseDocumentNo = ref('')
const responseComment = ref('')
const ownerConclusion = ref<'CONFIRMED' | 'RETURNED'>('CONFIRMED')
const reviewLines = ref<VariationOwnerReviewCommand['items']>([])
const loading = ref(false)
const action = ref('')
const errorMessage = ref('')
const pendingDelete = ref(false)
let detailController: AbortController | null = null
let referenceController: AbortController | null = null
let generation = 0

const variationId = computed(() =>
  typeof route.query.id === 'string' ? route.query.id.trim() : '',
)
const busy = computed(() => Boolean(action.value))
const canEdit = computed(() => session.hasPermission('variation:order:edit'))
const canEditItems = computed(() => session.hasPermission('variation:order:item:edit'))
const canDelete = computed(() => session.hasPermission('variation:order:delete'))
const canSubmit = computed(() => session.hasPermission('variation:order:submit'))
const canOwnerSubmit = computed(() => session.hasPermission('variation:owner:submit'))
const canOwnerReview = computed(() => session.hasPermission('variation:owner:review'))
const canTrace = computed(() => session.hasPermission('variation:trace'))
const isDraft = computed(() => detail.value?.approvalStatus === 'DRAFT')
const detailHasEditableControls = computed(
  () =>
    Boolean(detail.value) &&
    ((canEdit.value && isDraft.value) ||
      (canEditItems.value && isDraft.value) ||
      (canOwnerSubmit.value &&
        ['INTERNAL_APPROVED', 'OWNER_RETURNED'].includes(detail.value?.ownerStatus || '')) ||
      (canOwnerReview.value && detail.value?.ownerStatus === 'OWNER_SUBMITTED')),
)
const latestSubmission = computed<VariationOwnerSubmissionRecord | null>(
  () => detail.value?.ownerSubmissions?.at(-1) ?? null,
)
const costSubjectOptions = computed(() => {
  const parentIds = new Set(
    costSubjects.value.map((item) => item.parentId).filter((id): id is string => Boolean(id)),
  )
  const options = costSubjects.value
    .filter((item) => item.status === 'ENABLE' && !parentIds.has(item.id))
    .map((item) => ({
      value: item.id,
      label: `${item.subjectCode} · ${item.subjectName}`,
      disabled: false,
    }))
  for (const [index, item] of items.value.entries()) {
    if (item.costSubjectId && !options.some((option) => option.value === item.costSubjectId)) {
      const historical = costSubjects.value.find((subject) => subject.id === item.costSubjectId)
      options.push({
        value: item.costSubjectId,
        label: historical
          ? `${historical.subjectCode} · ${historical.subjectName}（历史值）`
          : `成本科目名称缺失（第 ${index + 1} 行）`,
        disabled: true,
      })
    }
  }
  return options
})
const wbsTaskOptions = computed(() => {
  const options = wbsTasks.value.map((item) => ({
    value: item.id,
    label: `${item.taskCode} · ${item.taskName}`,
  }))
  for (const [index, item] of items.value.entries()) {
    if (item.wbsTaskId && !options.some((option) => option.value === item.wbsTaskId)) {
      options.push({ value: item.wbsTaskId, label: `WBS名称缺失（第 ${index + 1} 行）` })
    }
  }
  return options
})

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

function errorText(error: unknown, fallback: string): string {
  if (isApiClientError(error)) return error.message
  return error instanceof Error ? error.message : fallback
}

function cleaned(value?: string | null): string | null {
  const normalized = value?.trim()
  return normalized ? normalized : null
}

function versionOf(value = detail.value): string | number {
  const version = value?.version
  if (version == null || String(version).trim() === '')
    throw new TypeError('缺少最新版本，请刷新后重试')
  return version
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
    wbsTaskId: '',
    remark: null,
  }
}

function prepareReview(submission: VariationOwnerSubmissionRecord | null): void {
  reviewLines.value = (submission?.items ?? []).map((item) => ({
    submissionItemId: item.id,
    confirmedAmount: String(
      item.confirmedAmount ??
        item.confirmed_amount ??
        item.claimedAmount ??
        item.claimed_amount ??
        '',
    ),
    reductionReason: item.reduction_reason ?? null,
  }))
}

async function loadDetail(preserveNotice = false): Promise<void> {
  if (!variationId.value) {
    detail.value = null
    errorMessage.value = '缺少签证变更编号'
    return
  }
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const currentGeneration = ++generation
  loading.value = true
  if (!preserveNotice) errorMessage.value = ''
  try {
    const value = await loadVariation(variationId.value, controller.signal)
    if (currentGeneration !== generation) return
    detail.value = value
    items.value = (value.items ?? []).map((item) => ({ ...item }))
    prepareReview(value.ownerSubmissions?.at(-1) ?? null)
  } catch (error) {
    if (!controller.signal.aborted && currentGeneration === generation) {
      detail.value = null
      errorMessage.value = errorText(error, '签证变更详情加载失败')
      showToast('error', '签证变更操作未完成', errorMessage.value)
    }
  } finally {
    if (currentGeneration === generation) loading.value = false
  }
}

async function loadReferences(): Promise<void> {
  referenceController?.abort()
  const controller = new AbortController()
  referenceController = controller
  try {
    const projectId = detail.value?.projectId
    const subjectValues = await loadCostSubjectOptions(controller.signal)
    let taskValues: WbsTaskRecord[] = []
    if (projectId) {
      const activeSchedules = (await loadSchedules(projectId, controller.signal)).filter(
        (item) => item.status === 'ACTIVE',
      )
      if (activeSchedules.length === 1) {
        taskValues = (await loadSchedule(activeSchedules[0]!.id, controller.signal)).tasks
      }
    }
    if (referenceController !== controller) return
    costSubjects.value = subjectValues
    wbsTasks.value = taskValues
  } catch (error) {
    if (!controller.signal.aborted) {
      errorMessage.value = errorText(error, '业务候选数据加载失败')
      showToast('error', '签证变更操作未完成', errorMessage.value)
    }
  } finally {
    if (referenceController === controller) referenceController = null
  }
}

async function backToList(): Promise<void> {
  const query = { ...route.query }
  delete query.mode
  delete query.id
  await router.push({ path: '/variation/order', query })
}

async function openWorkspace(mode: 'edit', id: string): Promise<void> {
  await router.push({ path: '/variation/order', query: { ...route.query, mode, id } })
}

async function runAction(name: string, operation: () => Promise<void>): Promise<void> {
  if (busy.value) return
  action.value = name
  errorMessage.value = ''
  try {
    await operation()
  } catch (error) {
    errorMessage.value = errorText(error, `${name}失败`)
    showToast('error', '签证变更操作未完成', errorMessage.value)
    if (variationId.value) await loadDetail(true)
  } finally {
    action.value = ''
  }
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
    wbsTaskId: item.wbsTaskId?.trim() ?? '',
    remark: cleaned(item.remark),
  }))
}

async function saveItems(): Promise<void> {
  await runAction('保存明细', async () => {
    const command = cleanItems()
    if (
      command.some(
        (item) => !item.itemName || !item.quantity || !item.costSubjectId || !item.wbsTaskId,
      )
    ) {
      throw new TypeError('明细名称、数量、WBS任务和成本科目不能为空')
    }
    await saveVariationItems(variationId.value, command, versionOf())
    await loadDetail(true)
    showToast('success', '操作成功', '变更明细已保存并刷新。')
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
    await backToList()
    showToast('success', '操作成功', '签证变更已提交审批。')
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
    showToast('success', '操作成功', '业主申报已登记。')
  })
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
    showToast('success', '操作成功', '业主回复已登记，合同金额以系统结果为准。')
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

async function confirmDelete(): Promise<void> {
  await runAction('删除', async () => {
    await deleteVariation(variationId.value, versionOf())
    pendingDelete.value = false
    await router.replace('/variation/order')
    showToast('success', '操作成功', '签证变更已删除。')
  })
}

watch(
  () => route.fullPath,
  async () => {
    trace.value = []
    await loadDetail()
    await loadReferences()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  detailController?.abort()
  referenceController?.abort()
})
</script>

<template>
  <V2Dialog
    :open="true"
    :title="detail?.varName || '签证变更详情'"
    description="查看签证变更、明细和业务追溯。"
    :close-disabled="busy"
    :close-on-backdrop="!detailHasEditableControls"
    panel-class="v2-dialog-standard v2-detail-dialog v2-dialog-wide"
    @close="backToList"
  >
    <V2PageState
      v-if="loading && !detail"
      kind="loading"
      title="正在加载签证变更"
      description="请稍候。"
    />
    <V2PageState
      v-else-if="!detail"
      kind="error"
      code="404"
      title="签证变更不可访问"
      :description="errorMessage || '记录不存在或当前账号无权查看。'"
    >
      <template #actions>
        <V2Button type="button" @click="backToList">返回台账</V2Button>
      </template>
    </V2PageState>
    <template v-else>
      <section class="v2-detail-dialog__section">
        <div class="v2-detail-dialog__section-heading"><h3>基本信息</h3></div>
        <dl class="variation-page__detail-grid v2-detail-dialog__facts">
          <div>
            <dt>项目</dt>
            <dd>{{ detail.projectName || '—' }}</dd>
          </div>
          <div>
            <dt>合同</dt>
            <dd>{{ detail.contractName || '合同名称缺失' }}</dd>
          </div>
          <div>
            <dt>审批状态</dt>
            <dd>{{ approvalStatusLabel(detail.approvalStatus) }}</dd>
          </div>
          <div>
            <dt>业主状态</dt>
            <dd>{{ ownerStatusLabel(detail.ownerStatus) }}</dd>
          </div>
          <div>
            <dt>申报金额</dt>
            <dd>{{ formatAmount(detail.reportedAmount) }}</dd>
          </div>
          <div>
            <dt>核定金额</dt>
            <dd>{{ formatAmount(detail.confirmedAmount) }}</dd>
          </div>
          <div>
            <dt>预计成本</dt>
            <dd>{{ formatAmount(detail.estimatedCostAmount) }}</dd>
          </div>
          <div>
            <dt>版本</dt>
            <dd>{{ detail.version ?? '—' }}</dd>
          </div>
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
      </section>

      <section class="v2-detail-dialog__section">
        <div class="v2-detail-dialog__section-heading">
          <h3>变更明细</h3>
          <V2Button
            v-if="canEditItems && isDraft"
            type="button"
            size="small"
            variant="secondary"
            :disabled="busy"
            @click="items = [...items, blankItem()]"
          >
            添加明细
          </V2Button>
        </div>
        <V2PageState
          v-if="!items.length && !errorMessage"
          title="暂无明细"
          description="草稿可添加变更明细。"
        />
        <div v-else-if="items.length" class="variation-page__items">
          <div v-for="(item, index) in items" :key="item.id || index" class="variation-page__item">
            <V2Input
              :model-value="item.itemName"
              label="明细名称"
              :disabled="!canEditItems || !isDraft"
              @update:model-value="updateItem(index, 'itemName', $event)"
            />
            <V2Input
              :model-value="item.quantity"
              label="数量"
              :decimal-scale="2"
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
              :decimal-scale="2"
              :disabled="!canEditItems || !isDraft"
              @update:model-value="updateItem(index, 'unitPrice', $event)"
            />
            <V2Input
              :model-value="item.claimUnitPrice ?? ''"
              label="申报单价"
              :decimal-scale="2"
              :disabled="!canEditItems || !isDraft"
              @update:model-value="updateItem(index, 'claimUnitPrice', $event)"
            />
            <V2Select
              :model-value="item.wbsTaskId ?? ''"
              label="WBS任务"
              :options="wbsTaskOptions"
              :disabled="!canEditItems || !isDraft"
              @update:model-value="updateItem(index, 'wbsTaskId', $event)"
            />
            <V2Select
              :model-value="item.costSubjectId"
              label="成本科目"
              :options="costSubjectOptions"
              :disabled="!canEditItems || !isDraft"
              @update:model-value="updateItem(index, 'costSubjectId', $event)"
            />
            <div v-if="canEditItems && isDraft" class="variation-page__actions">
              <V2Button
                type="button"
                size="small"
                variant="danger"
                :disabled="busy"
                @click="items = items.filter((_, itemIndex) => itemIndex !== index)"
              >
                移除
              </V2Button>
            </div>
          </div>
        </div>
        <div v-if="canEditItems && isDraft && items.length" class="variation-page__actions">
          <V2Button
            type="button"
            :loading="action === '保存明细'"
            :disabled="busy"
            @click="saveItems"
          >
            保存明细
          </V2Button>
        </div>
      </section>

      <section
        v-if="
          canOwnerSubmit &&
          ['INTERNAL_APPROVED', 'OWNER_RETURNED'].includes(detail.ownerStatus || '')
        "
        class="v2-detail-dialog__section"
      >
        <div class="v2-detail-dialog__section-heading"><h3>提交业主申报</h3></div>
        <div class="variation-page__form">
          <V2Input v-model="externalDocumentNo" label="对外发文号" required />
          <label class="variation-page__native-field">
            业主申报文件<input type="file" :disabled="busy" @change="onOwnerFile" />
          </label>
        </div>
        <div class="variation-page__actions">
          <V2Button
            type="button"
            :loading="action === '业主申报'"
            :disabled="busy"
            @click="submitOwner"
          >
            提交业主申报
          </V2Button>
        </div>
      </section>

      <section
        v-if="canOwnerReview && detail.ownerStatus === 'OWNER_SUBMITTED'"
        class="v2-detail-dialog__section"
      >
        <div class="v2-detail-dialog__section-heading"><h3>登记业主回复</h3></div>
        <div class="variation-page__form">
          <V2Select
            v-model="ownerConclusion"
            label="业主结论"
            :options="[
              { value: 'CONFIRMED', label: '核定' },
              { value: 'RETURNED', label: '退回' },
            ]"
          />
          <V2Input v-model="responseDocumentNo" label="业主回复文号" required />
          <V2Input v-model="responseComment" label="回复说明" />
          <label class="variation-page__native-field">
            业主回复文件<input type="file" :disabled="busy" @change="onOwnerFile" />
          </label>
        </div>
        <div v-if="ownerConclusion === 'CONFIRMED'" class="variation-page__items">
          <div
            v-for="(line, index) in reviewLines"
            :key="line.submissionItemId"
            class="variation-page__item"
          >
            <span>{{
              latestSubmission?.items?.[index]?.item_name || `申报明细 ${index + 1}`
            }}</span>
            <V2Input
              :model-value="line.confirmedAmount"
              label="核定金额"
              :decimal-scale="2"
              required
              @update:model-value="updateReviewLine(index, 'confirmedAmount', $event)"
            />
            <V2Input
              :model-value="line.reductionReason ?? ''"
              label="核减原因"
              @update:model-value="updateReviewLine(index, 'reductionReason', $event)"
            />
          </div>
        </div>
        <div class="variation-page__actions">
          <V2Button
            type="button"
            :loading="action === '业主回复'"
            :disabled="busy"
            @click="reviewOwner"
          >
            登记业主回复
          </V2Button>
        </div>
      </section>

      <section v-if="canTrace" class="v2-detail-dialog__section">
        <div class="v2-detail-dialog__section-heading">
          <h3>全链追溯</h3>
          <V2Button
            type="button"
            size="small"
            variant="secondary"
            :loading="action === '加载追溯'"
            :disabled="busy"
            @click="showTrace"
          >
            加载追溯
          </V2Button>
        </div>
        <V2PageState
          v-if="!trace.length"
          title="尚未加载追溯"
          description="按需读取审批、业主申报与合同变更链。"
        />
        <dl v-else class="variation-page__trace v2-detail-dialog__facts">
          <div v-for="row in trace" :key="row.key">
            <dt>{{ row.key }}</dt>
            <dd>{{ row.value }}</dd>
          </div>
        </dl>
      </section>
    </template>
    <template #footer>
      <V2Button type="button" variant="secondary" :disabled="busy" @click="backToList"
        >关闭</V2Button
      >
      <V2Button
        v-if="detail && canEdit && isDraft"
        type="button"
        variant="secondary"
        :disabled="busy"
        @click="openWorkspace('edit', detail.id)"
      >
        编辑
      </V2Button>
      <V2Button
        v-if="detail && canSubmit && isDraft"
        type="button"
        :loading="action === '提交审批'"
        :disabled="busy"
        @click="submitApproval"
      >
        提交审批
      </V2Button>
      <V2Button
        v-if="detail && canDelete && isDraft"
        type="button"
        variant="danger"
        :loading="action === '删除'"
        :disabled="busy"
        @click="pendingDelete = true"
      >
        删除
      </V2Button>
    </template>
  </V2Dialog>

  <V2ConfirmDialog
    :open="pendingDelete"
    title="删除签证变更"
    description="删除后不可恢复；仅草稿可删除。"
    confirm-text="确认删除"
    danger
    :loading="action === '删除'"
    @close="pendingDelete = false"
    @confirm="confirmDelete"
  />
</template>
