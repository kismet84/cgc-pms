<script setup lang="ts">
import type { MatStockTxnVO } from '@/types/inventory'
import {
  TXN_TYPE_COLOR,
  TXN_TYPE_LABEL,
  getSourceTypeColor,
  getSourceTypeLabel,
} from '../composables/useStockLedger'

defineProps<{
  txnList: MatStockTxnVO[]
  loading: boolean
  gridColumns: Record<string, unknown>[]
  fmtQty: (val: string | number) => string
}>()

const emit = defineEmits<{
  sortChange: [params: { field: string; order: 'asc' | 'desc' | null }]
  showDetail: [row: MatStockTxnVO]
}>()
</script>

<template>
  <div class="lg-table-wrap">
    <div style="padding: 12px 14px 0 14px; font-weight: 600; font-size: 14px; color: #374151">
      出入库流水
    </div>
    <vxe-grid
      :data="txnList"
      :columns="gridColumns"
      :loading="loading"
      :column-config="{ resizable: true }"
      stripe
      border="inner"
      size="small"
      max-height="480"
      @sort-change="
        (params: { field: string; order: 'asc' | 'desc' | null }) => emit('sortChange', params)
      "
    >
      <template #txnType="{ row }: { row: MatStockTxnVO }">
        <a-tag :color="TXN_TYPE_COLOR[row.txnType]">
          {{ TXN_TYPE_LABEL[row.txnType] ?? row.txnType }}
        </a-tag>
      </template>
      <template #quantity="{ row }: { row: MatStockTxnVO }">
        <span
          class="lg-money"
          :style="{
            color: row.txnType === 'OUT' ? '#ef4444' : '#16a34a',
          }"
        >
          {{ row.txnType === 'OUT' ? '−' : '+' }}{{ fmtQty(row.quantity) }}
        </span>
      </template>
      <template #availableAfter="{ row }: { row: MatStockTxnVO }">
        <span
          class="lg-money"
          :style="{
            color: Number(row.availableAfter) < 10 ? '#ef4444' : 'var(--text)',
            fontWeight: Number(row.availableAfter) < 10 ? 700 : 600,
          }"
        >
          {{ fmtQty(row.availableAfter) }}
        </span>
      </template>
      <template #sourceType="{ row }: { row: MatStockTxnVO }">
        <a-tag :color="getSourceTypeColor(row.sourceType)" size="small">
          {{ getSourceTypeLabel(row.sourceType) }}
        </a-tag>
      </template>
      <template #sourceId="{ row }: { row: MatStockTxnVO }">
        <span v-if="row.sourceId" class="lg-link">
          {{ row.sourceId }}
        </span>
        <span v-else style="color: #9ca3af">-</span>
      </template>
      <template #ops="{ row }: { row: MatStockTxnVO }">
        <a class="lg-link" @click="emit('showDetail', row)">详情</a>
      </template>
    </vxe-grid>
  </div>
</template>
