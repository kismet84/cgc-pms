<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { useContractStore } from '@/stores/contract'
import { submitForApproval } from '@/api/modules/contract'
import ContractStatusTag from '@/components/ContractStatusTag.vue'
import ApprovalStatusTag from '@/components/ApprovalStatusTag.vue'
import ContractChangeList from '@/components/ContractChangeList.vue'
import { fetchDictData, getDictLabelSync, getDictTagColorSync } from '@/utils/dict'

const route = useRoute()
const router = useRouter()
const contractStore = useContractStore()

const contractId = route.params.id as string
const activeTab = ref('items')
const submitting = ref(false)

const CONTRACT_TYPE_DICT = 'contract_type'

const itemColumns = [
  { title: '清单编码', dataIndex: 'itemCode', key: 'itemCode', width: 118, ellipsis: true },
  { title: '清单名称', dataIndex: 'itemName', key: 'itemName', width: 160, ellipsis: true },
  { title: '规格型号', dataIndex: 'itemSpec', key: 'itemSpec', width: 120, ellipsis: true },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 70, align: 'center' as const },
  { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 90, align: 'right' as const },
  { title: '单价', dataIndex: 'unitPrice', key: 'unitPrice', width: 100, align: 'right' as const },
  { title: '金额', dataIndex: 'amount', key: 'amount', width: 120, align: 'right' as const },
  { title: '税率(%)', dataIndex: 'taxRate', key: 'taxRate', width: 92, align: 'right' as const },
  { title: '税额', dataIndex: 'taxAmount', key: 'taxAmount', width: 100, align: 'right' as const },
  {
    title: '不含税金额',
    dataIndex: 'amountWithoutTax',
    key: 'amountWithoutTax',
    width: 132,
    align: 'right' as const,
  },
]

const termColumns = [
  { title: '条款名称', dataIndex: 'termName', key: 'termName', width: 160, ellipsis: true },
  {
    title: '付款比例(%)',
    dataIndex: 'paymentRatio',
    key: 'paymentRatio',
    width: 100,
    align: 'right' as const,
  },
  {
    title: '付款金额',
    dataIndex: 'paymentAmount',
    key: 'paymentAmount',
    width: 120,
    align: 'right' as const,
  },
  {
    title: '付款条件',
    dataIndex: 'paymentCondition',
    key: 'paymentCondition',
    width: 160,
    ellipsis: true,
  },
  { title: '计划日期', dataIndex: 'plannedDate', key: 'plannedDate', width: 110 },
  { title: '实际日期', dataIndex: 'actualDate', key: 'actualDate', width: 110 },
  { title: '状态', dataIndex: 'termStatus', key: 'termStatus', width: 92 },
]

const actionNameMap: Record<string, string> = {
  SUBMIT: '提交审批',
  APPROVE: '同意',
  REJECT: '驳回',
  WITHDRAW: '撤回',
  RESUBMIT: '重新提交',
  TRANSFER: '转办',
  ADD_SIGN: '加签',
}

function formatAmount(val: string | number): string {
  const n = typeof val === 'string' ? parseFloat(val) : val
  if (isNaN(n)) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function goBack() {
  router.push('/contract/ledger')
}

async function loadData() {
  await contractStore.fetchContract(contractId)
  // Load items and payment terms in parallel
  await Promise.all([
    contractStore.fetchItems(contractId),
    contractStore.fetchPaymentTerms(contractId),
    contractStore.fetchApprovalRecords(contractId),
  ])
}

function handleSubmitApproval() {
  Modal.confirm({
    title: '确认提交审批？',
    content: '提交后将进入审批流程，无法编辑合同内容。',
    okText: '确认提交',
    cancelText: '取消',
    onOk: async () => {
      submitting.value = true
      try {
        await submitForApproval(contractId)
        message.success('已提交审批')
        await loadData()
      } catch (e: unknown) {
        console.error(e)
        message.error('提交失败，请稍后重试')
      } finally {
        submitting.value = false
      }
    },
  })
}

onMounted(() => {
  void fetchDictData(CONTRACT_TYPE_DICT)
  loadData()
})

const contract = computed(() => contractStore.currentContract)
const items = computed(() => (Array.isArray(contractStore.items) ? contractStore.items : []))
const paymentTerms = computed(() =>
  Array.isArray(contractStore.paymentTerms) ? contractStore.paymentTerms : [],
)
const approvalRecords = computed(() =>
  Array.isArray(contractStore.approvalRecords) ? contractStore.approvalRecords : [],
)
const loading = computed(() => contractStore.loading)
const itemsLoading = computed(() => contractStore.itemsLoading)
const termsLoading = computed(() => contractStore.termsLoading)
const recordsLoading = computed(() => contractStore.recordsLoading)

// ---- Mobile detection ----
const MOBILE_BP = 768
const isMobile = ref(window.innerWidth < MOBILE_BP)
function onResize() {
  isMobile.value = window.innerWidth < MOBILE_BP
}
onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => window.removeEventListener('resize', onResize))
</script>

<template>
  <div class="contract-detail-page lg-page app-page" :class="{ 'is-mobile': isMobile }">
    <a-page-header title="合同详情" @back="goBack" :class="{ 'cd-header-mobile': isMobile }">
      <template #tags>
        <ContractStatusTag v-if="contract" :status="contract.contractStatus" />
        <ApprovalStatusTag v-if="contract" :status="contract.approvalStatus" />
      </template>
      <template #extra>
        <a-button
          v-if="contract && contract.approvalStatus === 'DRAFT'"
          type="primary"
          :loading="submitting"
          @click="handleSubmitApproval"
        >
          提交审批
        </a-button>
        <a-button @click="() => router.push(`/contract/${contractId}/edit`)">编辑</a-button>
      </template>
    </a-page-header>

    <a-spin :spinning="loading">
      <div v-if="contract" class="contract-detail-content lg-page-shell">
        <!-- Basic Info: desktop -->
        <a-card v-if="!isMobile" title="基本信息" :bordered="false" class="info-card lg-section">
          <a-descriptions :column="3" size="small" bordered>
            <a-descriptions-item label="合同名称" :span="3">{{
              contract.contractName
            }}</a-descriptions-item>
            <a-descriptions-item label="合同编号">{{ contract.contractCode }}</a-descriptions-item>
            <a-descriptions-item label="合同类型">
              <a-tag :color="getDictTagColorSync(CONTRACT_TYPE_DICT, contract.contractType)">
                {{ getDictLabelSync(CONTRACT_TYPE_DICT, contract.contractType) }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="项目名称">{{ contract.projectName }}</a-descriptions-item>
            <a-descriptions-item label="甲方">{{ contract.partyAName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="乙方">{{ contract.partyBName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="合同金额(含税)">
              <span class="contract-detail-contract-amount"
                >{{ formatAmount(contract.contractAmount) }} 元</span
              >
            </a-descriptions-item>
            <a-descriptions-item label="当前金额"
              >{{ formatAmount(contract.currentAmount) }} 元</a-descriptions-item
            >
            <a-descriptions-item label="税率">{{ contract.taxRate }}%</a-descriptions-item>
            <a-descriptions-item label="税额"
              >{{ formatAmount(contract.taxAmount) }} 元</a-descriptions-item
            >
            <a-descriptions-item label="不含税金额"
              >{{ formatAmount(contract.amountWithoutTax) }} 元</a-descriptions-item
            >
            <a-descriptions-item label="签订日期">{{ contract.signedDate }}</a-descriptions-item>
            <a-descriptions-item label="开始日期">{{ contract.startDate }}</a-descriptions-item>
            <a-descriptions-item label="结束日期">{{ contract.endDate }}</a-descriptions-item>
            <a-descriptions-item label="付款方式">{{ contract.paymentMethod }}</a-descriptions-item>
            <a-descriptions-item label="结算方式">{{
              contract.settlementMethod
            }}</a-descriptions-item>
            <a-descriptions-item label="创建人">{{ contract.createdBy }}</a-descriptions-item>
            <a-descriptions-item label="创建时间" :span="2">{{
              contract.createdAt
            }}</a-descriptions-item>
            <a-descriptions-item label="更新人">{{ contract.updatedBy }}</a-descriptions-item>
            <a-descriptions-item label="更新时间" :span="2">{{
              contract.updatedAt
            }}</a-descriptions-item>
            <a-descriptions-item v-if="contract.remark" label="备注" :span="3">{{
              contract.remark
            }}</a-descriptions-item>
          </a-descriptions>
        </a-card>

        <!-- Basic Info: mobile (vertical card) -->
        <div v-else class="cd-info-mobile">
          <div class="cd-info-mobile-title">基本信息</div>
          <div class="cd-info-row">
            <span class="cd-info-label">合同名称</span>
            <span class="cd-info-value cd-info-name">{{ contract.contractName }}</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">合同编号</span>
            <span class="cd-info-value">{{ contract.contractCode }}</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">合同类型</span>
            <span class="cd-info-value">
              <a-tag :color="getDictTagColorSync(CONTRACT_TYPE_DICT, contract.contractType)">
                {{ getDictLabelSync(CONTRACT_TYPE_DICT, contract.contractType) }}
              </a-tag>
            </span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">项目名称</span>
            <span class="cd-info-value">{{ contract.projectName }}</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">甲方</span>
            <span class="cd-info-value">{{ contract.partyAName || '-' }}</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">乙方</span>
            <span class="cd-info-value">{{ contract.partyBName || '-' }}</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">合同金额(含税)</span>
            <span class="cd-info-value cd-info-money"
              >{{ formatAmount(contract.contractAmount) }} 元</span
            >
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">当前金额</span>
            <span class="cd-info-value">{{ formatAmount(contract.currentAmount) }} 元</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">税率</span>
            <span class="cd-info-value">{{ contract.taxRate }}%</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">税额</span>
            <span class="cd-info-value">{{ formatAmount(contract.taxAmount) }} 元</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">不含税金额</span>
            <span class="cd-info-value">{{ formatAmount(contract.amountWithoutTax) }} 元</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">签订日期</span>
            <span class="cd-info-value">{{ contract.signedDate }}</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">开始日期</span>
            <span class="cd-info-value">{{ contract.startDate }}</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">结束日期</span>
            <span class="cd-info-value">{{ contract.endDate }}</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">付款方式</span>
            <span class="cd-info-value">{{ contract.paymentMethod }}</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">结算方式</span>
            <span class="cd-info-value">{{ contract.settlementMethod }}</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">创建人</span>
            <span class="cd-info-value">{{ contract.createdBy }}</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">创建时间</span>
            <span class="cd-info-value">{{ contract.createdAt }}</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">更新人</span>
            <span class="cd-info-value">{{ contract.updatedBy }}</span>
          </div>
          <div class="cd-info-row">
            <span class="cd-info-label">更新时间</span>
            <span class="cd-info-value">{{ contract.updatedAt }}</span>
          </div>
          <div v-if="contract.remark" class="cd-info-row">
            <span class="cd-info-label">备注</span>
            <span class="cd-info-value">{{ contract.remark }}</span>
          </div>
        </div>

        <!-- Tabs -->
        <a-card :bordered="false" class="tabs-card lg-section">
          <a-tabs v-model:activeKey="activeTab">
            <!-- Items Tab -->
            <a-tab-pane key="items" tab="合同清单">
              <a-spin :spinning="itemsLoading">
                <!-- desktop table -->
                <a-table
                  v-if="!isMobile"
                  :columns="itemColumns"
                  :data-source="items"
                  :pagination="false"
                  size="small"
                  bordered
                  row-key="id"
                  class="lg-table-wrap"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.key === 'quantity'">
                      {{ record.quantity }}
                    </template>
                    <template v-else-if="column.key === 'unitPrice'">
                      {{ formatAmount(record.unitPrice) }}
                    </template>
                    <template v-else-if="column.key === 'amount'">
                      {{ formatAmount(record.amount) }}
                    </template>
                    <template v-else-if="column.key === 'taxRate'">
                      {{ record.taxRate }}
                    </template>
                    <template v-else-if="column.key === 'taxAmount'">
                      {{ formatAmount(record.taxAmount) }}
                    </template>
                    <template v-else-if="column.key === 'amountWithoutTax'">
                      {{ formatAmount(record.amountWithoutTax) }}
                    </template>
                  </template>
                </a-table>
                <!-- mobile: item cards -->
                <div v-else class="cd-mobile-list">
                  <div v-for="(row, idx) in items" :key="row.id ?? idx" class="cd-mobile-card">
                    <div class="cd-mc-head">
                      <span class="cd-mc-code">{{ row.itemCode }}</span>
                      <span class="cd-mc-name">{{ row.itemName }}</span>
                    </div>
                    <div class="cd-mc-body">
                      <div class="cd-mc-field">
                        <span class="cd-mc-label">规格型号</span>
                        <span class="cd-mc-val">{{ row.itemSpec || '-' }}</span>
                      </div>
                      <div class="cd-mc-row">
                        <div class="cd-mc-field">
                          <span class="cd-mc-label">单位</span>
                          <span class="cd-mc-val">{{ row.unit }}</span>
                        </div>
                        <div class="cd-mc-field">
                          <span class="cd-mc-label">数量</span>
                          <span class="cd-mc-val">{{ row.quantity }}</span>
                        </div>
                        <div class="cd-mc-field">
                          <span class="cd-mc-label">单价</span>
                          <span class="cd-mc-val">{{ formatAmount(row.unitPrice) }}</span>
                        </div>
                      </div>
                      <div class="cd-mc-row">
                        <div class="cd-mc-field">
                          <span class="cd-mc-label">金额</span>
                          <span class="cd-mc-val cd-mc-money">{{ formatAmount(row.amount) }}</span>
                        </div>
                        <div class="cd-mc-field">
                          <span class="cd-mc-label">税率</span>
                          <span class="cd-mc-val">{{ row.taxRate }}%</span>
                        </div>
                      </div>
                      <div class="cd-mc-row">
                        <div class="cd-mc-field">
                          <span class="cd-mc-label">税额</span>
                          <span class="cd-mc-val">{{ formatAmount(row.taxAmount) }}</span>
                        </div>
                        <div class="cd-mc-field">
                          <span class="cd-mc-label">不含税金额</span>
                          <span class="cd-mc-val">{{ formatAmount(row.amountWithoutTax) }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                  <a-empty v-if="!items.length" description="暂无清单数据" />
                </div>
              </a-spin>
            </a-tab-pane>

            <!-- Payment Terms Tab -->
            <a-tab-pane key="payment-terms" tab="付款条件">
              <a-spin :spinning="termsLoading">
                <a-table
                  v-if="!isMobile"
                  :columns="termColumns"
                  :data-source="paymentTerms"
                  :pagination="false"
                  size="small"
                  bordered
                  row-key="id"
                  class="lg-table-wrap"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.key === 'paymentRatio'">
                      {{ record.paymentRatio }}
                    </template>
                    <template v-else-if="column.key === 'paymentAmount'">
                      {{ formatAmount(record.paymentAmount) }}
                    </template>
                  </template>
                </a-table>
                <!-- mobile: term cards -->
                <div v-else class="cd-mobile-list">
                  <div
                    v-for="(row, idx) in paymentTerms"
                    :key="row.id ?? idx"
                    class="cd-mobile-card"
                  >
                    <div class="cd-mc-head">
                      <span class="cd-mc-name">{{ row.termName }}</span>
                      <a-tag v-if="row.termStatus" size="small">{{ row.termStatus }}</a-tag>
                    </div>
                    <div class="cd-mc-body">
                      <div class="cd-mc-row">
                        <div class="cd-mc-field">
                          <span class="cd-mc-label">付款比例</span>
                          <span class="cd-mc-val">{{ row.paymentRatio }}%</span>
                        </div>
                        <div class="cd-mc-field">
                          <span class="cd-mc-label">付款金额</span>
                          <span class="cd-mc-val cd-mc-money">{{
                            formatAmount(row.paymentAmount)
                          }}</span>
                        </div>
                      </div>
                      <div class="cd-mc-field">
                        <span class="cd-mc-label">付款条件</span>
                        <span class="cd-mc-val">{{ row.paymentCondition || '-' }}</span>
                      </div>
                      <div class="cd-mc-row">
                        <div class="cd-mc-field">
                          <span class="cd-mc-label">计划日期</span>
                          <span class="cd-mc-val">{{ row.plannedDate || '-' }}</span>
                        </div>
                        <div class="cd-mc-field">
                          <span class="cd-mc-label">实际日期</span>
                          <span class="cd-mc-val">{{ row.actualDate || '-' }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                  <a-empty v-if="!paymentTerms.length" description="暂无付款条件数据" />
                </div>
              </a-spin>
            </a-tab-pane>

            <!-- Approval History Tab -->
            <a-tab-pane key="approval-history" tab="审批记录">
              <a-spin :spinning="recordsLoading">
                <a-timeline
                  v-if="approvalRecords.length > 0"
                  :class="{ 'cd-timeline-mobile': isMobile }"
                >
                  <a-timeline-item v-for="record in approvalRecords" :key="record.id">
                    <div>
                      <strong>{{ record.operatorName }}</strong>
                      <a-tag class="contract-detail-action-tag">
                        {{ actionNameMap[record.actionType] || record.actionName }}
                      </a-tag>
                    </div>
                    <div v-if="record.nodeName" class="contract-detail-record-node">
                      {{ record.nodeName }}
                    </div>
                    <div v-if="record.comment" class="contract-detail-record-comment">
                      {{ record.comment }}
                    </div>
                    <div class="contract-detail-record-time">
                      {{ record.createdAt }}
                    </div>
                  </a-timeline-item>
                </a-timeline>
                <a-empty v-else description="暂无审批记录" />
              </a-spin>
            </a-tab-pane>

            <!-- Contract Changes Tab -->
            <a-tab-pane key="contract-changes" tab="合同变更">
              <ContractChangeList :contract-id="contractId" @refresh="loadData" />
            </a-tab-pane>
          </a-tabs>
        </a-card>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.contract-detail-content {
  padding: 0;
}

.contract-detail-page :deep(.ant-page-header) {
  margin-bottom: 16px;
  padding: 18px 24px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
}

.contract-detail-page :deep(.ant-page-header-heading-title) {
  color: var(--text);
  font-size: 18px;
  font-weight: 600;
}

/* ---- Mobile: basic info vertical card ---- */
.cd-info-mobile {
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  padding: 14px;
  margin-bottom: 12px;
}
.cd-info-mobile-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 10px;
  color: var(--text);
}
.cd-info-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 6px 0;
  font-size: 13px;
  border-bottom: 1px solid var(--border-subtle);
}
.cd-info-row:last-of-type {
  border-bottom: none;
}
.cd-info-label {
  color: var(--text-secondary);
  white-space: nowrap;
  min-width: 88px;
  flex-shrink: 0;
  padding-top: 1px;
}
.cd-info-value {
  color: var(--text);
  word-break: break-all;
}
.cd-info-name {
  font-weight: 600;
}
.cd-info-money {
  font-weight: 700;
}

/* ---- Mobile: header tweaks ---- */
.cd-header-mobile :deep(.ant-page-header-heading-left) {
  flex-wrap: wrap;
}
.cd-header-mobile :deep(.ant-page-header-heading-extra) {
  white-space: normal;
  margin-top: 8px;
}

/* ---- Mobile: content padding ---- */
.contract-detail-page.is-mobile .info-card,
.contract-detail-page.is-mobile .tabs-card {
  margin-bottom: 10px;
}

/* ---- Mobile: list cards (items / terms) ---- */
.cd-mobile-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.cd-mobile-card {
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  padding: 10px 12px;
}
.cd-mc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-subtle);
  margin-bottom: 8px;
}
.cd-mc-code {
  font-size: 11px;
  color: var(--text-secondary);
  font-family: var(--font-family);
  font-variant-numeric: tabular-nums;
}
.cd-mc-name {
  font-size: 13px;
  font-weight: 600;
  flex: 1;
}
.cd-mc-body {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.cd-mc-field {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 12px;
}
.cd-mc-label {
  color: var(--text-secondary);
  white-space: nowrap;
  min-width: 64px;
  flex-shrink: 0;
}
.cd-mc-val {
  color: var(--text);
}
.cd-mc-money {
  font-weight: 600;
}
.cd-mc-row {
  display: flex;
  gap: 12px;
}
.cd-mc-row .cd-mc-field {
  flex: 1;
  min-width: 0;
}

/* ---- Mobile: timeline tighter ---- */
.cd-timeline-mobile :deep(.ant-timeline-item-content) {
  margin-left: 0;
}

.contract-detail-contract-amount,
.cd-info-money,
.cd-mc-money {
  color: var(--primary);
  font-variant-numeric: tabular-nums;
}

.contract-detail-contract-amount {
  font-weight: 600;
}

.contract-detail-action-tag {
  margin-left: 8px;
}

.contract-detail-record-node,
.contract-detail-record-comment {
  color: var(--text-secondary);
  font-size: 13px;
}

.contract-detail-record-node,
.contract-detail-record-time {
  margin-top: 2px;
}

.contract-detail-record-comment {
  margin-top: 4px;
}

.contract-detail-record-time {
  color: var(--muted);
  font-size: 12px;
}
</style>
