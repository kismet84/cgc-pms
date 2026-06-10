<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useContractStore } from '@/stores/contract'
import type { ContractType, ContractStatus, ApprovalStatus } from '@/types/contract'

const route = useRoute()
const router = useRouter()
const contractStore = useContractStore()

const contractId = route.params.id as string
const activeTab = ref('items')

const TYPE_LABEL: Record<ContractType, string> = {
  MAIN: '总包合同',
  SUB: '分包合同',
  PURCHASE: '采购合同',
  LEASE: '租赁合同',
  SERVICE: '服务合同',
}

const TYPE_COLOR: Record<ContractType, string> = {
  MAIN: 'blue',
  SUB: 'green',
  PURCHASE: 'orange',
  LEASE: 'purple',
  SERVICE: 'cyan',
}

const STATUS_LABEL: Record<ContractStatus, string> = {
  EXECUTING: '履约中',
  COMPLETED: '已完成',
  TERMINATED: '已终止',
  DRAFT: '草稿',
}

const STATUS_COLOR: Record<ContractStatus, string> = {
  EXECUTING: 'success',
  COMPLETED: 'default',
  TERMINATED: 'warning',
  DRAFT: 'processing',
}

const APPROVAL_STATUS_LABEL: Record<ApprovalStatus, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}

const APPROVAL_STATUS_COLOR: Record<ApprovalStatus, string> = {
  DRAFT: 'default',
  SUBMITTED: 'processing',
  APPROVING: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
}

const itemColumns = [
  { title: '清单编码', dataIndex: 'itemCode', key: 'itemCode', width: 120 },
  { title: '清单名称', dataIndex: 'itemName', key: 'itemName', width: 180 },
  { title: '规格型号', dataIndex: 'itemSpec', key: 'itemSpec', width: 140 },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 80, align: 'center' as const },
  { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100, align: 'right' as const },
  { title: '单价', dataIndex: 'unitPrice', key: 'unitPrice', width: 120, align: 'right' as const },
  { title: '金额', dataIndex: 'amount', key: 'amount', width: 140, align: 'right' as const },
  { title: '税率(%)', dataIndex: 'taxRate', key: 'taxRate', width: 100, align: 'right' as const },
  { title: '税额', dataIndex: 'taxAmount', key: 'taxAmount', width: 120, align: 'right' as const },
  { title: '不含税金额', dataIndex: 'amountWithoutTax', key: 'amountWithoutTax', width: 140, align: 'right' as const },
]

const termColumns = [
  { title: '条款名称', dataIndex: 'termName', key: 'termName', width: 180 },
  { title: '付款比例(%)', dataIndex: 'paymentRatio', key: 'paymentRatio', width: 120, align: 'right' as const },
  { title: '付款金额', dataIndex: 'paymentAmount', key: 'paymentAmount', width: 140, align: 'right' as const },
  { title: '付款条件', dataIndex: 'paymentCondition', key: 'paymentCondition', width: 200 },
  { title: '计划日期', dataIndex: 'plannedDate', key: 'plannedDate', width: 120 },
  { title: '实际日期', dataIndex: 'actualDate', key: 'actualDate', width: 120 },
  { title: '状态', dataIndex: 'termStatus', key: 'termStatus', width: 100 },
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
  Promise.all([
    contractStore.fetchItems(contractId),
    contractStore.fetchPaymentTerms(contractId),
    contractStore.fetchApprovalRecords(contractId),
  ])
}

onMounted(() => {
  loadData()
})

const contract = computed(() => contractStore.currentContract)
const items = computed(() => contractStore.items)
const paymentTerms = computed(() => contractStore.paymentTerms)
const approvalRecords = computed(() => contractStore.approvalRecords)
const loading = computed(() => contractStore.loading)
const itemsLoading = computed(() => contractStore.itemsLoading)
const termsLoading = computed(() => contractStore.termsLoading)
const recordsLoading = computed(() => contractStore.recordsLoading)
</script>

<template>
  <div class="contract-detail-page">
    <a-page-header title="合同详情" @back="goBack">
      <template #tags>
        <a-tag v-if="contract" :color="STATUS_COLOR[contract.contractStatus]">
          {{ STATUS_LABEL[contract.contractStatus] }}
        </a-tag>
        <a-tag v-if="contract" :color="APPROVAL_STATUS_COLOR[contract.approvalStatus]">
          {{ APPROVAL_STATUS_LABEL[contract.approvalStatus] }}
        </a-tag>
      </template>
      <template #extra>
        <a-button @click="() => router.push(`/contract/${contractId}/edit`)">编辑</a-button>
      </template>
    </a-page-header>

    <a-spin :spinning="loading">
      <div v-if="contract" class="contract-detail-content">
        <!-- Basic Info Card -->
        <a-card title="基本信息" :bordered="false" class="info-card">
          <a-descriptions :column="3" size="small" bordered>
            <a-descriptions-item label="合同名称" :span="3">{{ contract.contractName }}</a-descriptions-item>
            <a-descriptions-item label="合同编号">{{ contract.contractCode }}</a-descriptions-item>
            <a-descriptions-item label="合同类型">
              <a-tag :color="TYPE_COLOR[contract.contractType]">
                {{ TYPE_LABEL[contract.contractType] }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="项目名称">{{ contract.projectName }}</a-descriptions-item>
            <a-descriptions-item label="合作方">{{ contract.partnerName }}</a-descriptions-item>
            <a-descriptions-item label="甲方">{{ contract.partyA }}</a-descriptions-item>
            <a-descriptions-item label="乙方">{{ contract.partyB }}</a-descriptions-item>
            <a-descriptions-item label="合同金额(含税)">
              <span style="font-weight: 600; color: #1890ff">{{ formatAmount(contract.contractAmount) }} 元</span>
            </a-descriptions-item>
            <a-descriptions-item label="当前金额">{{ formatAmount(contract.currentAmount) }} 元</a-descriptions-item>
            <a-descriptions-item label="税率">{{ contract.taxRate }}%</a-descriptions-item>
            <a-descriptions-item label="税额">{{ formatAmount(contract.taxAmount) }} 元</a-descriptions-item>
            <a-descriptions-item label="不含税金额">{{ formatAmount(contract.amountWithoutTax) }} 元</a-descriptions-item>
            <a-descriptions-item label="质保比例">{{ contract.warrantyRate }}%</a-descriptions-item>
            <a-descriptions-item label="质保金额">{{ formatAmount(contract.warrantyAmount) }} 元</a-descriptions-item>
            <a-descriptions-item label="签订日期">{{ contract.signedDate }}</a-descriptions-item>
            <a-descriptions-item label="开始日期">{{ contract.startDate }}</a-descriptions-item>
            <a-descriptions-item label="结束日期">{{ contract.endDate }}</a-descriptions-item>
            <a-descriptions-item label="付款方式">{{ contract.paymentMethod }}</a-descriptions-item>
            <a-descriptions-item label="结算方式">{{ contract.settlementMethod }}</a-descriptions-item>
            <a-descriptions-item label="创建人">{{ contract.createdBy }}</a-descriptions-item>
            <a-descriptions-item label="创建时间" :span="2">{{ contract.createdAt }}</a-descriptions-item>
            <a-descriptions-item label="更新人">{{ contract.updatedBy }}</a-descriptions-item>
            <a-descriptions-item label="更新时间" :span="2">{{ contract.updatedAt }}</a-descriptions-item>
            <a-descriptions-item v-if="contract.remark" label="备注" :span="3">{{ contract.remark }}</a-descriptions-item>
          </a-descriptions>
        </a-card>

        <!-- Tabs -->
        <a-card :bordered="false" class="tabs-card">
          <a-tabs v-model:activeKey="activeTab">
            <!-- Items Tab -->
            <a-tab-pane key="items" tab="合同清单">
              <a-spin :spinning="itemsLoading">
                <a-table
                  :columns="itemColumns"
                  :data-source="items"
                  :pagination="false"
                  :scroll="{ x: 1200 }"
                  size="small"
                  bordered
                  row-key="id"
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
              </a-spin>
            </a-tab-pane>

            <!-- Payment Terms Tab -->
            <a-tab-pane key="payment-terms" tab="付款条件">
              <a-spin :spinning="termsLoading">
                <a-table
                  :columns="termColumns"
                  :data-source="paymentTerms"
                  :pagination="false"
                  :scroll="{ x: 1000 }"
                  size="small"
                  bordered
                  row-key="id"
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
              </a-spin>
            </a-tab-pane>

            <!-- Approval History Tab -->
            <a-tab-pane key="approval-history" tab="审批记录">
              <a-spin :spinning="recordsLoading">
                <a-timeline v-if="approvalRecords.length > 0">
                  <a-timeline-item v-for="record in approvalRecords" :key="record.id">
                    <div>
                      <strong>{{ record.operatorName }}</strong>
                      <a-tag style="margin-left: 8px">
                        {{ actionNameMap[record.actionType] || record.actionName }}
                      </a-tag>
                      <span v-if="record.nodeName" style="margin-left: 8px; color: #999; font-size: 13px">
                        {{ record.nodeName }}
                      </span>
                    </div>
                    <div v-if="record.comment" style="color: #666; font-size: 13px; margin-top: 4px">
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
.contract-detail-page {
  padding: 0;
  background: #f0f2f5;
  min-height: 100vh;
}

.contract-detail-content {
  padding: 16px;
}

.info-card {
  margin-bottom: 16px;
}

.tabs-card {
  margin-bottom: 16px;
}
</style>
