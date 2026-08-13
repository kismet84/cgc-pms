<script setup lang="ts">
import type {
  BudgetLineRecord,
  ContractBudgetAllocationRecord,
  ContractCompositeRecord,
  ContractProjectOption,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BusinessAttachmentPanel,
  V2Badge,
  V2Button,
  V2ConfirmDialog,
  V2Dialog,
  V2PageState,
  V2Select,
  V2Input,
  showToast,
  useToastMessage,
} from '@/components'
import { formatAmount, formatDecimal } from '@/shared/display'
import {
  deleteContract,
  loadBudget,
  loadBudgetPage,
  loadContractBudgetAllocations,
  loadContractComposite,
  loadContractProjectOptions,
  saveContractBudgetAllocations,
  submitContract,
} from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { approvalStatusLabel, paymentTermStatusLabel } from './model'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const loading = ref(false)
const submitting = ref(false)
const deleting = ref(false)
const errorMessage = ref('')
const successMessage = useToastMessage()
const detail = ref<ContractCompositeRecord | null>(null)
const projects = ref<ContractProjectOption[]>([])
const budgetAllocations = ref<ContractBudgetAllocationRecord[]>([])
const allocationDrafts = ref<ContractBudgetAllocationRecord[]>([])
const activeBudgetLines = ref<BudgetLineRecord[]>([])
const allocationEditing = ref(false)
const allocationSaving = ref(false)
const pendingDelete = ref(false)
const pendingSubmit = ref(false)

let detailGeneration = 0
let detailController: AbortController | null = null
let refController: AbortController | null = null

const contractId = computed(() =>
  typeof route.params.id === 'string' ? route.params.id.trim() : '',
)
const canEdit = computed(() => session.hasPermission('contract:edit'))
const canSubmit = computed(() => session.hasPermission('contract:submit'))
const canDelete = computed(() => session.hasPermission('contract:delete'))
const canQueryBudget = computed(() => session.hasPermission('budget:query'))
const canEditBudget = computed(() => session.hasPermission('budget:edit'))
const canUploadFile = computed(() => session.hasPermission('file:upload'))
const canDeleteFile = computed(() => session.hasPermission('file:delete'))
const currentContract = computed(() => detail.value?.contract ?? null)
const currentContractIsDraft = computed(() => currentContract.value?.approvalStatus === 'DRAFT')
const currentContractAttachmentsEditable = computed(() =>
  ['DRAFT', 'REJECTED'].includes(currentContract.value?.approvalStatus ?? ''),
)
const currentContractBudgetEditable = computed(
  () =>
    canEditBudget.value &&
    currentContract.value?.contractType !== 'MAIN' &&
    ['DRAFT', 'REJECTED'].includes(currentContract.value?.approvalStatus ?? ''),
)
const budgetLineOptions = computed(() =>
  activeBudgetLines.value
    .filter((line): line is BudgetLineRecord & { id: string } => Boolean(line.id))
    .map((line) => ({
      value: line.id,
      label: line.costSubjectName || line.costSubjectId,
    })),
)

watch(errorMessage, (message) => {
  if (message) showToast('error', '合同操作未完成', message)
})

function resetNotices(): void {
  errorMessage.value = ''
  successMessage.value = ''
}

function errorText(error: unknown, fallback: string): string {
  return isApiClientError(error) ? error.message : fallback
}

async function loadReferenceData(): Promise<void> {
  refController?.abort()
  const controller = new AbortController()
  refController = controller
  try {
    const options = await loadContractProjectOptions(controller.signal)
    if (refController === controller) projects.value = options
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = errorText(error, '合同候选数据加载失败')
  } finally {
    if (refController === controller) refController = null
  }
}

async function loadDetail(preserveNotice = false): Promise<void> {
  if (!contractId.value) return
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const generation = ++detailGeneration
  loading.value = true
  if (!preserveNotice) resetNotices()
  try {
    const value = await loadContractComposite(contractId.value, controller.signal)
    if (generation !== detailGeneration) return
    detail.value = value
    await loadAllocationContext(value.contract.projectId, controller.signal)
  } catch (error) {
    if (!controller.signal.aborted && generation === detailGeneration) {
      detail.value = null
      errorMessage.value = errorText(error, '合同详情加载失败')
    }
  } finally {
    if (generation === detailGeneration) loading.value = false
  }
}

async function loadAllocationContext(projectId: string, signal?: AbortSignal): Promise<void> {
  allocationEditing.value = false
  if (!canQueryBudget.value) {
    budgetAllocations.value = []
    activeBudgetLines.value = []
    return
  }
  const [allocations, page] = await Promise.all([
    loadContractBudgetAllocations(contractId.value, signal),
    loadBudgetPage({ projectId, status: 'ACTIVE', pageNo: 1, pageSize: 100 }, signal),
  ])
  budgetAllocations.value = allocations
  const activeBudget = page.records.find((row) => row.active)
  activeBudgetLines.value = activeBudget
    ? ((await loadBudget(activeBudget.id, signal)).lines ?? [])
    : []
}

function budgetLineLabel(id: string): string {
  const line = activeBudgetLines.value.find((row) => row.id === id)
  return line?.costSubjectName || line?.costSubjectId || id
}

function beginAllocationEdit(): void {
  allocationDrafts.value = budgetAllocations.value.map((row) => ({ ...row }))
  if (!allocationDrafts.value.length) addAllocation()
  allocationEditing.value = true
}

function addAllocation(): void {
  allocationDrafts.value.push({
    contractId: contractId.value,
    budgetLineId: '',
    allocatedAmount: '',
  })
}

async function saveAllocations(): Promise<void> {
  if (
    !allocationDrafts.value.length ||
    allocationDrafts.value.some((row) => !row.budgetLineId || !row.allocatedAmount)
  ) {
    errorMessage.value = '请完整填写预算科目和分配金额'
    return
  }
  allocationSaving.value = true
  try {
    await saveContractBudgetAllocations(contractId.value, allocationDrafts.value)
    await loadAllocationContext(currentContract.value?.projectId ?? '')
    successMessage.value = '合同预算分配已保存。'
  } catch (error) {
    errorMessage.value = errorText(error, '合同预算分配保存失败')
  } finally {
    allocationSaving.value = false
  }
}

function projectLabel(projectId?: string | null): string {
  return projects.value.find((item) => item.id === projectId)?.projectName ?? '项目名称缺失'
}

async function submitCurrentContract(): Promise<void> {
  const id = contractId.value || currentContract.value?.id || ''
  if (!id || submitting.value) return
  submitting.value = true
  resetNotices()
  try {
    await submitContract(id, currentContract.value?.version)
    await loadDetail(true)
    await backToLedger()
    successMessage.value = '合同已提交审批。'
  } catch (error) {
    errorMessage.value = errorText(error, '合同提交失败')
    await loadDetail(true)
    if (isApiClientError(error) && error.status === 409) {
      errorMessage.value = `${error.message}；草稿已保留并已刷新最新数据`
    }
  } finally {
    submitting.value = false
    pendingSubmit.value = false
  }
}

async function confirmDelete(): Promise<void> {
  if (!contractId.value || deleting.value) return
  deleting.value = true
  resetNotices()
  try {
    await deleteContract(contractId.value)
    pendingDelete.value = false
    await router.push({ path: '/contract/ledger', query: route.query })
  } catch (error) {
    errorMessage.value = errorText(error, '合同删除失败')
    await loadDetail(true)
  } finally {
    deleting.value = false
  }
}

function closeDeleteDialog(): void {
  if (!deleting.value) pendingDelete.value = false
}

function openEdit(): void {
  if (!contractId.value) return
  void router.push({ path: `/contract/${contractId.value}/edit`, query: route.query })
}

async function backToLedger(): Promise<void> {
  await router.push({ path: '/contract/ledger', query: route.query })
}

watch(
  () => route.fullPath,
  async () => {
    await loadReferenceData()
    await loadDetail()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  detailController?.abort()
  refController?.abort()
})
</script>

<template>
  <section class="contract-page" aria-labelledby="contract-title">
    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在加载合同数据"
      description="请稍候。"
      title-id="contract-title"
      :heading-level="1"
    />

    <V2Dialog
      :open="Boolean(detail)"
      :title="currentContract?.contractName || '合同详情'"
      description="查看合同台账详情。"
      close-on-backdrop
      :close-disabled="submitting || deleting"
      panel-class="v2-dialog-standard v2-detail-dialog v2-dialog-wide"
      @close="backToLedger"
    >
      <div v-if="currentContract" class="contract-page__detail-grid">
        <section class="v2-detail-dialog__section">
          <div class="v2-detail-dialog__section-heading"><h3>合同头</h3></div>
          <dl class="v2-detail-dialog__facts">
            <div>
              <dt>项目</dt>
              <dd>{{ projectLabel(currentContract.projectId) }}</dd>
            </div>
            <div>
              <dt>甲方</dt>
              <dd>{{ currentContract.partyAName || '合作方名称缺失' }}</dd>
            </div>
            <div>
              <dt>乙方</dt>
              <dd>{{ currentContract.partyBName || '合作方名称缺失' }}</dd>
            </div>
            <div>
              <dt>合同额</dt>
              <dd>{{ formatAmount(currentContract.contractAmount) }}</dd>
            </div>
            <div>
              <dt>当前额</dt>
              <dd>{{ formatAmount(currentContract.currentAmount) }}</dd>
            </div>
            <div>
              <dt>已付额</dt>
              <dd>{{ formatAmount(currentContract.paidAmount) }}</dd>
            </div>
            <div v-if="currentContract.contractType === 'PURCHASE'">
              <dt>采购净应付</dt>
              <dd>{{ formatAmount(currentContract.payableAmount) }}</dd>
            </div>
            <div>
              <dt>结算额</dt>
              <dd>{{ formatAmount(currentContract.settlementAmount) }}</dd>
            </div>
            <div>
              <dt>税额</dt>
              <dd>{{ formatAmount(currentContract.taxAmount) }}</dd>
            </div>
            <div>
              <dt>不含税额</dt>
              <dd>{{ formatAmount(currentContract.amountWithoutTax) }}</dd>
            </div>
            <div>
              <dt>审批状态</dt>
              <dd>{{ approvalStatusLabel(currentContract.approvalStatus) }}</dd>
            </div>
          </dl>
        </section>

        <section
          v-if="canQueryBudget && currentContract.contractType !== 'MAIN'"
          class="v2-detail-dialog__section"
        >
          <div class="v2-detail-dialog__section-heading">
            <h3>合同预算</h3>
            <div class="contract-page__inline-actions">
              <template v-if="allocationEditing">
                <V2Button type="button" size="small" variant="ghost" @click="addAllocation"
                  >新增科目</V2Button
                >
                <V2Button
                  type="button"
                  size="small"
                  variant="ghost"
                  @click="allocationEditing = false"
                  >取消</V2Button
                >
                <V2Button
                  type="button"
                  size="small"
                  :loading="allocationSaving"
                  @click="saveAllocations"
                  >保存</V2Button
                >
              </template>
              <V2Button
                v-else-if="currentContractBudgetEditable"
                type="button"
                size="small"
                variant="secondary"
                @click="beginAllocationEdit"
                >编辑分配</V2Button
              >
            </div>
          </div>
          <div
            v-if="allocationEditing || budgetAllocations.length"
            class="contract-page__table-wrap"
            role="region"
            aria-label="合同预算分配表格"
            tabindex="0"
          >
            <table
              class="contract-page__table contract-page__detail-table"
              data-table-identity="contextual"
            >
              <thead>
                <tr>
                  <th scope="col">科目名称</th>
                  <th scope="col">分配金额</th>
                  <th scope="col">已占用</th>
                  <th scope="col">已消耗</th>
                  <th v-if="allocationEditing" scope="col">操作</th>
                </tr>
              </thead>
              <tbody v-if="allocationEditing">
                <tr v-for="(row, index) in allocationDrafts" :key="row.id || index">
                  <td>
                    <V2Select
                      v-model="row.budgetLineId"
                      :options="budgetLineOptions"
                      label="预算科目"
                      hide-label
                    />
                  </td>
                  <td>
                    <V2Input
                      v-model="row.allocatedAmount"
                      label="分配金额"
                      :decimal-scale="2"
                      hide-label
                      autocomplete="off"
                    />
                  </td>
                  <td>{{ formatAmount(row.reservedAmount) }}</td>
                  <td>{{ formatAmount(row.consumedAmount) }}</td>
                  <td>
                    <V2Button
                      type="button"
                      size="small"
                      variant="ghost"
                      @click="allocationDrafts.splice(index, 1)"
                      >删除</V2Button
                    >
                  </td>
                </tr>
              </tbody>
              <tbody v-else>
                <tr v-for="row in budgetAllocations" :key="row.id || row.budgetLineId">
                  <td>{{ budgetLineLabel(row.budgetLineId) }}</td>
                  <td>{{ formatAmount(row.allocatedAmount) }}</td>
                  <td>{{ formatAmount(row.reservedAmount) }}</td>
                  <td>{{ formatAmount(row.consumedAmount) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <V2PageState
            v-else-if="!errorMessage"
            kind="empty"
            title="尚未配置合同预算"
            description="提交审批前需按已生效预算科目完成分配。"
            :heading-level="3"
          />
        </section>

        <section class="v2-detail-dialog__section">
          <div class="v2-detail-dialog__section-heading">
            <h3>合同清单</h3>
            <V2Badge tone="neutral">共 {{ detail?.items.length ?? 0 }} 条</V2Badge>
          </div>
          <div
            v-if="detail?.items.length"
            class="contract-page__table-wrap"
            role="region"
            aria-label="合同清单表格"
            tabindex="0"
          >
            <table class="contract-page__table contract-page__detail-table">
              <thead>
                <tr>
                  <th scope="col">名称</th>
                  <th scope="col">编号</th>
                  <th scope="col">规格</th>
                  <th scope="col">单位</th>
                  <th scope="col">数量</th>
                  <th scope="col">单价</th>
                  <th scope="col">金额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in detail.items" :key="item.id || item.itemName">
                  <td>{{ item.itemName }}</td>
                  <td>{{ item.itemCode || '未编号' }}</td>
                  <td>{{ item.itemSpec || '—' }}</td>
                  <td>{{ item.unit || '—' }}</td>
                  <td>{{ formatDecimal(item.quantity) }}</td>
                  <td>{{ formatAmount(item.unitPrice || null) }}</td>
                  <td>{{ formatAmount(item.amount || null) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <V2PageState
            v-else-if="!errorMessage"
            kind="empty"
            title="暂无合同清单"
            description="当前合同还没有明细。"
            :heading-level="3"
          />
        </section>

        <section class="v2-detail-dialog__section">
          <div class="v2-detail-dialog__section-heading">
            <h3>付款条款</h3>
            <V2Badge tone="neutral">共 {{ detail?.paymentTerms.length ?? 0 }} 条</V2Badge>
          </div>
          <div
            v-if="detail?.paymentTerms.length"
            class="contract-page__table-wrap"
            role="region"
            aria-label="付款条款表格"
            tabindex="0"
          >
            <table class="contract-page__table contract-page__detail-table">
              <thead>
                <tr>
                  <th scope="col">条款名称</th>
                  <th scope="col">付款比例</th>
                  <th scope="col">付款金额</th>
                  <th scope="col">付款条件</th>
                  <th scope="col">计划日期</th>
                  <th scope="col">实际日期</th>
                  <th scope="col">状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="term in detail.paymentTerms" :key="term.id || term.termName">
                  <td>{{ term.termName }}</td>
                  <td>{{ formatDecimal(term.paymentRatio) }}</td>
                  <td>{{ formatAmount(term.paymentAmount || null) }}</td>
                  <td>{{ term.paymentCondition || '—' }}</td>
                  <td>{{ term.plannedDate || '—' }}</td>
                  <td>{{ term.actualDate || '—' }}</td>
                  <td>{{ paymentTermStatusLabel(term.termStatus) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <V2PageState
            v-else-if="!errorMessage"
            kind="empty"
            title="暂无付款条款"
            description="当前合同还没有付款节点。"
            :heading-level="3"
          />
        </section>

        <section class="v2-detail-dialog__section">
          <BusinessAttachmentPanel
            title="合同附件"
            business-type="CONTRACT"
            :business-id="currentContract.id"
            document-type="CONTRACT_ATTACHMENT"
            :can-upload="canUploadFile && currentContractAttachmentsEditable"
            :can-delete="canDeleteFile && currentContractAttachmentsEditable"
          />
        </section>

        <section class="v2-detail-dialog__section">
          <div class="v2-detail-dialog__section-heading">
            <h3>审批记录</h3>
            <V2Badge tone="neutral">共 {{ detail?.approvalRecords.length ?? 0 }} 条</V2Badge>
          </div>
          <div v-if="detail?.approvalRecords.length" class="contract-page__rows">
            <article
              v-for="record in detail.approvalRecords"
              :key="record.id"
              class="contract-page__approval-row"
            >
              <strong>{{ record.actionName }}</strong>
              <p>{{ record.nodeName }} · {{ record.operatorName }} · {{ record.createdAt }}</p>
              <p>{{ record.comment || '无审批意见' }}</p>
            </article>
          </div>
          <V2PageState
            v-else-if="!errorMessage"
            kind="empty"
            title="暂无审批历史"
            description="草稿合同还没有审批轨迹。"
            :heading-level="3"
          />
        </section>
      </div>

      <template #footer>
        <V2Button
          type="button"
          variant="secondary"
          :disabled="submitting || deleting"
          @click="backToLedger"
          >关闭</V2Button
        >
        <V2Button
          v-if="canEdit && currentContractIsDraft"
          type="button"
          variant="secondary"
          @click="openEdit"
          >编辑</V2Button
        >
        <V2Button
          v-if="canSubmit && currentContractIsDraft"
          type="button"
          :loading="submitting"
          @click="pendingSubmit = true"
          >提交审批</V2Button
        >
        <V2Button
          v-if="canDelete && currentContractIsDraft"
          type="button"
          variant="danger"
          :loading="deleting"
          @click="pendingDelete = true"
          >删除</V2Button
        >
      </template>
    </V2Dialog>

    <V2PageState
      v-if="!loading && !detail"
      kind="error"
      title="合同不可访问"
      description="合同不存在、超出项目范围，或当前账号没有访问权限。"
      title-id="contract-title"
      :heading-level="1"
    >
      <template #actions>
        <V2Button variant="secondary" @click="backToLedger">返回台账</V2Button>
      </template>
    </V2PageState>

    <V2ConfirmDialog
      :open="pendingSubmit"
      title="提交合同审批"
      description="提交后合同进入审批流程，草稿内容将锁定。"
      confirm-text="确认提交"
      :loading="submitting"
      @close="pendingSubmit = false"
      @confirm="submitCurrentContract"
    />

    <V2ConfirmDialog
      :open="pendingDelete"
      title="删除合同"
      description="删除后不可恢复；当前只会删除服务端允许删除的草稿或可删合同。"
      confirm-text="确认删除"
      danger
      :loading="deleting"
      @close="closeDeleteDialog"
      @confirm="confirmDelete"
    />
  </section>
</template>

<style scoped src="./contract-page.css"></style>
