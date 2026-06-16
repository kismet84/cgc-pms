<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { useSettlementStore } from '@/stores/settlement'
import { submitSettlement } from '@/api/modules/settlement'
import type { SettlementStatus } from '@/types/settlement'
import { SETTLEMENT_STATUS_LABEL, SETTLEMENT_STATUS_COLOR } from '@/types/settlement'
import { SOURCE_TYPE_LABEL, SOURCE_TYPE_COLOR } from '@/types/cost'
import type { SourceType } from '@/types/cost'

const route = useRoute()
const router = useRouter()
const store = useSettlementStore()

const settlementId = route.params.id as string
const activeTab = ref('basic')
const submitting = ref(false)

const APPROVAL_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
}
const APPROVAL_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  APPROVING: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
  WITHDRAWN: 'warning',
}

const DIRECTION_LABEL: Record<string, string> = {
  COST: '成本增加',
  DEDUCT: '成本减少',
  NEUTRAL: '中性变更',
}
const DIRECTION_COLOR: Record<string, string> = {
  COST: 'red',
  DEDUCT: 'green',
  NEUTRAL: 'blue',
}

const COST_TYPE_LABEL: Record<string, string> = {
  MATERIAL: '材料费',
  LABOR: '人工费',
  MACHINERY: '机械费',
  SUBCONTRACT: '分包费',
  OTHER: '其他费用',
}
const COST_STATUS_LABEL: Record<string, string> = {
  LOCKED: '已锁定',
  CONFIRMED: '已确认',
  PENDING: '待确认',
}
const COST_STATUS_COLOR: Record<string, string> = {
  LOCKED: 'blue',
  CONFIRMED: 'green',
  PENDING: 'orange',
}

const PAY_TYPE_LABEL: Record<string, string> = {
  ADVANCE: '预付款',
  PROGRESS: '进度款',
  FINAL: '结算款',
  OTHER: '其他',
}
const PAY_STATUS_LABEL: Record<string, string> = {
  UNPAID: '未支付',
  PARTIAL: '部分支付',
  PAID: '已支付',
}
const PAY_STATUS_COLOR: Record<string, string> = {
  UNPAID: 'default',
  PARTIAL: 'warning',
  PAID: 'success',
}

const actionNameMap: Record<string, string> = {
  SUBMIT: '提交审批',
  APPROVE: '同意',
  REJECT: '驳回',
  WITHDRAW: '撤回',
  RESUBMIT: '重新提交',
  TRANSFER: '转办',
  ADD_SIGN: '加签',
}

// ---- Variation table columns ----
const variationColumns = [
  { title: '签证编号', dataIndex: 'varCode', key: 'varCode', width: 140 },
  { title: '签证名称', dataIndex: 'varName', key: 'varName', width: 180 },
  { title: '变更类型', dataIndex: 'varType', key: 'varType', width: 100 },
  { title: '方向', dataIndex: 'direction', key: 'direction', width: 100 },
  {
    title: '上报金额',
    dataIndex: 'reportedAmount',
    key: 'reportedAmount',
    width: 120,
    align: 'right' as const,
  },
  {
    title: '审批金额',
    dataIndex: 'approvedAmount',
    key: 'approvedAmount',
    width: 120,
    align: 'right' as const,
  },
  {
    title: '确认金额',
    dataIndex: 'confirmedAmount',
    key: 'confirmedAmount',
    width: 120,
    align: 'right' as const,
  },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160 },
]

// ---- Payment table columns ----
const paymentColumns = [
  { title: '申请编号', dataIndex: 'applyCode', key: 'applyCode', width: 150 },
  { title: '付款类型', dataIndex: 'payType', key: 'payType', width: 100 },
  {
    title: '申请金额',
    dataIndex: 'applyAmount',
    key: 'applyAmount',
    width: 120,
    align: 'right' as const,
  },
  {
    title: '审批金额',
    dataIndex: 'approvedAmount',
    key: 'approvedAmount',
    width: 120,
    align: 'right' as const,
  },
  {
    title: '实际付款',
    dataIndex: 'actualPayAmount',
    key: 'actualPayAmount',
    width: 120,
    align: 'right' as const,
  },
  { title: '付款状态', dataIndex: 'payStatus', key: 'payStatus', width: 100 },
  { title: '付款日期', dataIndex: 'payDate', key: 'payDate', width: 120 },
  { title: '凭证号', dataIndex: 'voucherNo', key: 'voucherNo', width: 140 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160 },
]

// ---- Cost table columns ----
const costColumns = [
  { title: '成本科目', dataIndex: 'costSubjectName', key: 'costSubjectName', width: 140 },
  { title: '费用类型', dataIndex: 'costType', key: 'costType', width: 100 },
  { title: '来源类型', dataIndex: 'sourceType', key: 'sourceType', width: 120 },
  { title: '来源单据', dataIndex: 'sourceId', key: 'sourceId', width: 140 },
  { title: '金额(含税)', dataIndex: 'amount', key: 'amount', width: 120, align: 'right' as const },
  { title: '税额', dataIndex: 'taxAmount', key: 'taxAmount', width: 100, align: 'right' as const },
  {
    title: '不含税金额',
    dataIndex: 'amountWithoutTax',
    key: 'amountWithoutTax',
    width: 130,
    align: 'right' as const,
  },
  { title: '成本日期', dataIndex: 'costDate', key: 'costDate', width: 110 },
  { title: '状态', dataIndex: 'costStatus', key: 'costStatus', width: 80 },
]

// ---- Attachment table columns ----
const attachmentColumns = [
  { title: '文件名', dataIndex: 'originalName', key: 'originalName', width: 240 },
  { title: '文件大小', dataIndex: 'fileSize', key: 'fileSize', width: 100 },
  { title: '类型', dataIndex: 'fileType', key: 'fileType', width: 80 },
  { title: '上传人', dataIndex: 'uploadedBy', key: 'uploadedBy', width: 100 },
  { title: '上传时间', dataIndex: 'uploadedAt', key: 'uploadedAt', width: 160 },
]

// ---- Helpers ----
function formatAmount(val: string | number): string {
  const n = typeof val === 'string' ? parseFloat(val) : val
  if (isNaN(n)) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function goBack() {
  router.push('/settlement')
}

async function loadData() {
  await store.fetchSettlement(settlementId)
  await Promise.all([
    store.fetchItems(settlementId),
    store.fetchVariations(settlementId),
    store.fetchPayments(settlementId),
    store.fetchCosts(settlementId),
    store.fetchAttachments(settlementId),
    store.fetchApprovalRecords(settlementId),
  ])
}

function handleSubmitApproval() {
  Modal.confirm({
    title: '确认提交审批？',
    content: '提交后将进入审批流程，无法编辑结算单。',
    okText: '确认提交',
    cancelText: '取消',
    onOk: async () => {
      submitting.value = true
      try {
        await submitSettlement(settlementId)
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

/** Jump to source document detail page */
function jumpToSource(sourceType: string, sourceId: string) {
  const routeMap: Record<string, string> = {
    VAR_ORDER: '/variation/order',
    SUB_MEASURE: '/subcontract/measure',
    MAT_RECEIPT: '/purchase/receipt',
    CT_CONTRACT: '/contract',
    PAY_REQUEST: '/payment/application',
  }
  const base = routeMap[sourceType]
  if (base) {
    router.push(`${base}/${sourceId}`)
  } else {
    message.info(`来源类型 "${sourceType}" 暂不支持跳转`)
  }
}

onMounted(() => {
  loadData()
})

const settlement = computed(() => store.currentSettlement)
const variations = computed(() => store.variations)
const payments = computed(() => store.payments)
const costs = computed(() => store.costs)
const attachments = computed(() => store.attachments)
const approvalRecords = computed(() => store.approvalRecords)
const loading = computed(() => store.loading)
const variationsLoading = computed(() => store.variationsLoading)
const paymentsLoading = computed(() => store.paymentsLoading)
const costsLoading = computed(() => store.costsLoading)
const attachmentsLoading = computed(() => store.attachmentsLoading)
const recordsLoading = computed(() => store.recordsLoading)
const isFinalized = computed(() => settlement.value?.settlementStatus === 'FINALIZED')
const isDraft = computed(() => settlement.value?.approvalStatus === 'DRAFT')
</script>

<template>
  <div class="stl-detail-page">
    <a-page-header title="结算详情" @back="goBack">
      <template #tags>
        <a-tag
          v-if="settlement"
          :color="SETTLEMENT_STATUS_COLOR[settlement.settlementStatus as SettlementStatus]"
        >
          {{ SETTLEMENT_STATUS_LABEL[settlement.settlementStatus as SettlementStatus] }}
        </a-tag>
        <a-tag
          v-if="settlement"
          :color="APPROVAL_STATUS_COLOR[settlement.approvalStatus] || 'default'"
        >
          {{ APPROVAL_STATUS_LABEL[settlement.approvalStatus] || settlement.approvalStatus }}
        </a-tag>
      </template>
      <template #extra>
        <a-button
          v-if="settlement && isDraft"
          type="primary"
          :loading="submitting"
          @click="handleSubmitApproval"
        >
          提交审批
        </a-button>
        <a-button
          v-if="settlement && !isFinalized"
          @click="() => router.push(`/settlement/${settlementId}/edit`)"
        >
          编辑
        </a-button>
      </template>
    </a-page-header>

    <a-spin :spinning="loading">
      <div v-if="settlement" class="stl-detail-content">
        <!-- Tabs -->
        <a-card :bordered="false" class="tabs-card">
          <a-tabs v-model:activeKey="activeTab">
            <!-- Tab 1: 基本信息 -->
            <a-tab-pane key="basic" tab="基本信息">
              <a-descriptions :column="3" size="small" bordered>
                <a-descriptions-item label="结算编号" :span="2">{{
                  settlement.settlementCode
                }}</a-descriptions-item>
                <a-descriptions-item label="结算类型">{{
                  settlement.settlementType || '-'
                }}</a-descriptions-item>
                <a-descriptions-item label="关联合同" :span="2">{{
                  settlement.contractName
                }}</a-descriptions-item>
                <a-descriptions-item label="所属项目">{{
                  settlement.projectName
                }}</a-descriptions-item>
                <a-descriptions-item label="合作方">{{
                  settlement.partnerName
                }}</a-descriptions-item>
                <a-descriptions-item label="合同金额(含税)">
                  <span style="font-weight: 600"
                    >{{ formatAmount(settlement.contractAmount) }} 元</span
                  >
                </a-descriptions-item>
                <a-descriptions-item label="变更金额">
                  <span
                    :style="{
                      color: parseFloat(settlement.changeAmount) >= 0 ? '#ef4444' : '#16a34a',
                    }"
                  >
                    {{ formatAmount(settlement.changeAmount) }} 元
                  </span>
                </a-descriptions-item>
                <a-descriptions-item label="计量金额"
                  >{{ formatAmount(settlement.measuredAmount) }} 元</a-descriptions-item
                >
                <a-descriptions-item label="结算金额">
                  <span style="font-weight: 600; color: #3b82f6; font-size: 16px"
                    >{{ formatAmount(settlement.finalAmount) }} 元</span
                  >
                </a-descriptions-item>
                <a-descriptions-item label="扣减金额"
                  >{{ formatAmount(settlement.deductionAmount) }} 元</a-descriptions-item
                >
                <a-descriptions-item label="已付款"
                  >{{ formatAmount(settlement.paidAmount) }} 元</a-descriptions-item
                >
                <a-descriptions-item label="未付款">
                  <span style="font-weight: 600; color: #ef4444"
                    >{{ formatAmount(settlement.unpaidAmount) }} 元</span
                  >
                </a-descriptions-item>
                <a-descriptions-item label="质保金额"
                  >{{ formatAmount(settlement.warrantyAmount) }} 元</a-descriptions-item
                >
                <a-descriptions-item label="合同状态">{{
                  settlement.status || '-'
                }}</a-descriptions-item>
                <a-descriptions-item v-if="settlement.finalizedAt" label="定案时间">{{
                  settlement.finalizedAt
                }}</a-descriptions-item>
                <a-descriptions-item label="创建人">{{ settlement.createdBy }}</a-descriptions-item>
                <a-descriptions-item label="创建时间">{{
                  settlement.createdAt
                }}</a-descriptions-item>
                <a-descriptions-item label="更新人">{{
                  settlement.updatedBy || '-'
                }}</a-descriptions-item>
                <a-descriptions-item label="更新时间" :span="2">{{
                  settlement.updatedAt || '-'
                }}</a-descriptions-item>
                <a-descriptions-item v-if="settlement.remark" label="备注" :span="3">{{
                  settlement.remark
                }}</a-descriptions-item>
              </a-descriptions>
            </a-tab-pane>

            <!-- Tab 2: 汇总（自动计算，只读） -->
            <a-tab-pane key="summary" tab="汇总">
              <div class="stl-summary-readonly">
                <div class="stl-summary-title">结算汇总（自动计算，只读）</div>
                <a-descriptions :column="2" size="small" bordered>
                  <a-descriptions-item label="合同金额(含税)">
                    {{ formatAmount(settlement.contractAmount) }} 元
                  </a-descriptions-item>
                  <a-descriptions-item label="变更金额合计">
                    <span
                      :style="{
                        color: parseFloat(settlement.changeAmount) >= 0 ? '#ef4444' : '#16a34a',
                      }"
                    >
                      {{ formatAmount(settlement.changeAmount) }} 元
                    </span>
                  </a-descriptions-item>
                  <a-descriptions-item label="计量金额合计">
                    {{ formatAmount(settlement.measuredAmount) }} 元
                  </a-descriptions-item>
                  <a-descriptions-item label="扣减金额">
                    {{ formatAmount(settlement.deductionAmount) }} 元
                  </a-descriptions-item>
                </a-descriptions>
                <a-divider />
                <a-descriptions :column="2" size="small" bordered>
                  <a-descriptions-item label="结算金额（定案金额）">
                    <span style="font-weight: 700; color: #3b82f6; font-size: 18px">
                      {{ formatAmount(settlement.finalAmount) }} 元
                    </span>
                  </a-descriptions-item>
                  <a-descriptions-item label="计算公式">
                    <span style="color: #6b7280; font-size: 12px">
                      结算金额 = 合同金额 + 变更金额 + 计量金额 - 扣减金额
                    </span>
                  </a-descriptions-item>
                  <a-descriptions-item label="已付款合计">
                    {{ formatAmount(settlement.paidAmount) }} 元
                  </a-descriptions-item>
                  <a-descriptions-item label="质保金额">
                    {{ formatAmount(settlement.warrantyAmount) }} 元
                  </a-descriptions-item>
                  <a-descriptions-item label="未付款余额">
                    <span style="font-weight: 700; color: #ef4444; font-size: 16px">
                      {{ formatAmount(settlement.unpaidAmount) }} 元
                    </span>
                  </a-descriptions-item>
                  <a-descriptions-item label="计算公式">
                    <span style="color: #6b7280; font-size: 12px">
                      未付款 = 结算金额 - 已付款 - 质保金额
                    </span>
                  </a-descriptions-item>
                </a-descriptions>
              </div>
            </a-tab-pane>

            <!-- Tab 3: 变更签证 -->
            <a-tab-pane key="variations" tab="变更签证">
              <a-spin :spinning="variationsLoading">
                <a-table
                  v-if="variations.length > 0"
                  :columns="variationColumns"
                  :data-source="variations"
                  :pagination="false"
                  :scroll="{ x: 1060 }"
                  size="small"
                  bordered
                  row-key="id"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.key === 'direction'">
                      <a-tag :color="DIRECTION_COLOR[record.direction] || 'default'">
                        {{ DIRECTION_LABEL[record.direction] || record.direction }}
                      </a-tag>
                    </template>
                    <template v-else-if="column.key === 'reportedAmount'">
                      {{ formatAmount(record.reportedAmount) }}
                    </template>
                    <template v-else-if="column.key === 'approvedAmount'">
                      {{ formatAmount(record.approvedAmount) }}
                    </template>
                    <template v-else-if="column.key === 'confirmedAmount'">
                      <span style="font-weight: 600">{{
                        formatAmount(record.confirmedAmount)
                      }}</span>
                    </template>
                    <template v-else-if="column.key === 'varCode'">
                      <a
                        style="color: #1677ff; cursor: pointer"
                        @click="jumpToSource('VAR_ORDER', record.id)"
                      >
                        {{ record.varCode }}
                      </a>
                    </template>
                  </template>
                </a-table>
                <a-empty v-else description="暂无变更签证记录" />
              </a-spin>
            </a-tab-pane>

            <!-- Tab 4: 付款明细 -->
            <a-tab-pane key="payments" tab="付款明细">
              <a-spin :spinning="paymentsLoading">
                <a-table
                  v-if="payments.length > 0"
                  :columns="paymentColumns"
                  :data-source="payments"
                  :pagination="false"
                  :scroll="{ x: 1130 }"
                  size="small"
                  bordered
                  row-key="id"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.key === 'payType'">
                      <a-tag>{{ PAY_TYPE_LABEL[record.payType] || record.payType }}</a-tag>
                    </template>
                    <template v-else-if="column.key === 'payStatus'">
                      <a-tag :color="PAY_STATUS_COLOR[record.payStatus] || 'default'">
                        {{ PAY_STATUS_LABEL[record.payStatus] || record.payStatus }}
                      </a-tag>
                    </template>
                    <template v-else-if="column.key === 'applyCode'">
                      <a
                        style="color: #1677ff; cursor: pointer"
                        @click="jumpToSource('PAY_REQUEST', record.applicationId)"
                      >
                        {{ record.applyCode }}
                      </a>
                    </template>
                    <template
                      v-else-if="
                        ['applyAmount', 'approvedAmount', 'actualPayAmount'].includes(column.key)
                      "
                    >
                      {{ formatAmount(record[column.key]) }}
                    </template>
                  </template>
                </a-table>
                <a-empty v-else description="暂无付款记录" />
              </a-spin>
            </a-tab-pane>

            <!-- Tab 5: 成本明细 -->
            <a-tab-pane key="costs" tab="成本明细">
              <a-spin :spinning="costsLoading">
                <a-table
                  v-if="costs.length > 0"
                  :columns="costColumns"
                  :data-source="costs"
                  :pagination="false"
                  :scroll="{ x: 1080 }"
                  size="small"
                  bordered
                  row-key="id"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.key === 'sourceType'">
                      <a-tag
                        :color="SOURCE_TYPE_COLOR[record.sourceType as SourceType] || 'default'"
                      >
                        {{
                          SOURCE_TYPE_LABEL[record.sourceType as SourceType] || record.sourceType
                        }}
                      </a-tag>
                    </template>
                    <template v-else-if="column.key === 'sourceId'">
                      <a
                        v-if="record.sourceType && record.sourceId"
                        style="color: #1677ff; cursor: pointer"
                        @click="jumpToSource(record.sourceType, record.sourceId)"
                      >
                        {{ record.sourceId }}
                      </a>
                      <span v-else>-</span>
                    </template>
                    <template v-else-if="column.key === 'costType'">
                      {{ COST_TYPE_LABEL[record.costType] || record.costType || '-' }}
                    </template>
                    <template v-else-if="column.key === 'costStatus'">
                      <a-tag :color="COST_STATUS_COLOR[record.costStatus] || 'default'">
                        {{ COST_STATUS_LABEL[record.costStatus] || record.costStatus }}
                      </a-tag>
                    </template>
                    <template
                      v-else-if="['amount', 'taxAmount', 'amountWithoutTax'].includes(column.key)"
                    >
                      {{ formatAmount(record[column.key]) }}
                    </template>
                  </template>
                </a-table>
                <a-empty v-else description="暂无成本记录" />
              </a-spin>
            </a-tab-pane>

            <!-- Tab 6: 附件 -->
            <a-tab-pane key="attachments" tab="附件">
              <a-spin :spinning="attachmentsLoading">
                <a-table
                  v-if="attachments.length > 0"
                  :columns="attachmentColumns"
                  :data-source="attachments"
                  :pagination="false"
                  :scroll="{ x: 680 }"
                  size="small"
                  bordered
                  row-key="id"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.key === 'originalName'">
                      <span style="color: #1677ff; cursor: pointer">{{ record.originalName }}</span>
                    </template>
                    <template v-else-if="column.key === 'fileSize'">
                      {{ fmtFileSize(record.fileSize) }}
                    </template>
                  </template>
                </a-table>
                <a-empty v-else description="暂无附件" />
              </a-spin>
            </a-tab-pane>

            <!-- Tab 7: 审批记录 -->
            <a-tab-pane key="approval" tab="审批记录">
              <a-spin :spinning="recordsLoading">
                <a-timeline v-if="approvalRecords.length > 0">
                  <a-timeline-item v-for="record in approvalRecords" :key="record.id">
                    <div>
                      <strong>{{ record.operatorName }}</strong>
                      <a-tag style="margin-left: 8px">
                        {{ actionNameMap[record.actionType] || record.actionName }}
                      </a-tag>
                      <span
                        v-if="record.nodeName"
                        style="margin-left: 8px; color: #999; font-size: 13px"
                      >
                        {{ record.nodeName }}
                      </span>
                    </div>
                    <div
                      v-if="record.comment"
                      style="color: #666; font-size: 13px; margin-top: 4px"
                    >
                      {{ record.comment }}
                    </div>
                    <div style="color: #999; font-size: 12px; margin-top: 2px">
                      {{ record.createdAt }}
                    </div>
                  </a-timeline-item>
                </a-timeline>
                <a-empty v-else description="暂无审批记录" />
              </a-spin>
            </a-tab-pane>
          </a-tabs>
        </a-card>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.stl-detail-page {
  padding: 0;
  background: #f0f2f5;
  min-height: 100vh;
}
.stl-detail-content {
  padding: 16px;
}
.tabs-card {
  margin-bottom: 16px;
}

/* Summary tab */
.stl-summary-readonly {
  padding: 8px 0;
}
.stl-summary-title {
  font-size: 15px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 16px;
  padding-left: 4px;
}
</style>
