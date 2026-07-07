<script setup lang="ts">
import { getSourceTypeColor, getSourceTypeLabel } from '@/types/cost'
import type { CostLedgerVO } from '@/types/cost'

defineProps<{
  open: boolean
  detailItem: CostLedgerVO | null
  fmtAmountYuan: (val: string | undefined) => string
  costTypeLabel: (value: string | undefined) => string
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()
</script>

<template>
  <a-drawer
    :open="open"
    title="成本明细"
    placement="right"
    :width="800"
    class="cost-ledger-detail-drawer"
    @close="emit('close')"
  >
    <template v-if="detailItem">
      <div class="cost-ledger-detail-summary">
        <div>
          <span>成本科目</span>
          <strong>{{ detailItem.costSubjectName || '-' }}</strong>
        </div>
        <div>
          <span>金额含税</span>
          <strong>{{ fmtAmountYuan(detailItem.amount) }}</strong>
        </div>
        <div>
          <span>来源类型</span>
          <a-tag :color="getSourceTypeColor(detailItem.sourceType)">
            {{ getSourceTypeLabel(detailItem.sourceType) }}
          </a-tag>
        </div>
      </div>
      <a-descriptions class="cost-ledger-detail-descriptions" :column="2" size="small" bordered>
        <a-descriptions-item label="成本科目">{{
          detailItem.costSubjectName || '-'
        }}</a-descriptions-item>
        <a-descriptions-item label="费用类型">
          {{ costTypeLabel(detailItem.costType) }}
        </a-descriptions-item>
        <a-descriptions-item label="来源类型">
          <a-tag :color="getSourceTypeColor(detailItem.sourceType)">
            {{ getSourceTypeLabel(detailItem.sourceType) }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="金额(含税)">{{
          fmtAmountYuan(detailItem.amount)
        }}</a-descriptions-item>
        <a-descriptions-item label="税额">{{
          fmtAmountYuan(detailItem.taxAmount)
        }}</a-descriptions-item>
        <a-descriptions-item label="不含税金额">
          {{ fmtAmountYuan(detailItem.amountWithoutTax) }}
        </a-descriptions-item>
        <a-descriptions-item label="生成标识">
          {{ detailItem.generatedFlag === '1' ? '自动生成' : '手动录入' }}
        </a-descriptions-item>
        <a-descriptions-item label="来源单据ID">{{
          detailItem.sourceId || '-'
        }}</a-descriptions-item>
        <a-descriptions-item label="来源明细ID">{{
          detailItem.sourceItemId || '-'
        }}</a-descriptions-item>
        <a-descriptions-item label="创建人">{{ detailItem.createdBy || '-' }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{
          detailItem.createdAt || '-'
        }}</a-descriptions-item>
        <a-descriptions-item label="备注" :span="2">{{
          detailItem.remark || '-'
        }}</a-descriptions-item>
      </a-descriptions>
    </template>
  </a-drawer>
</template>

<style scoped>
.cost-ledger-detail-drawer :deep(.ant-drawer-body) {
  background: var(--surface-subtle);
}

.cost-ledger-detail-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  overflow: hidden;
  margin-bottom: 12px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.cost-ledger-detail-summary > div {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding: 14px 16px;
  border-right: 1px solid var(--border-subtle);
}

.cost-ledger-detail-summary > div:last-child {
  border-right: 0;
}

.cost-ledger-detail-summary span {
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.cost-ledger-detail-summary strong {
  overflow: hidden;
  color: var(--text);
  font-size: 18px;
  line-height: 24px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cost-ledger-detail-descriptions {
  background: var(--surface);
  border-radius: var(--radius-lg);
}
</style>
