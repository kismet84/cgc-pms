<script setup lang="ts">
import { computed } from 'vue'
import type { MatStockTxnVO } from '@/types/inventory'
import { MoreOutlined } from '@ant-design/icons-vue'
import {
  TXN_TYPE_COLOR,
  TXN_TYPE_LABEL,
  getSourceTypeColor,
  getSourceTypeLabel,
} from '../composables/useStockLedger'

const props = defineProps<{
  txnList: MatStockTxnVO[]
  loading: boolean
  gridColumns: Record<string, unknown>[]
  fmtQty: (val: string | number) => string
  safetyStockQty: number
}>()

const visibleGridColumns = computed(() => props.gridColumns)

const emit = defineEmits<{
  sortChange: [params: { field: string; order: 'asc' | 'desc' | null }]
  showDetail: [row: MatStockTxnVO]
}>()
</script>

<template>
  <div class="lg-table-wrap">
    <vxe-grid
      :data="txnList"
      :columns="visibleGridColumns"
      :loading="loading"
      :column-config="{ resizable: true }"
      stripe
      border="inner"
      size="small"
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
            color: Number(row.availableAfter) < safetyStockQty ? '#ef4444' : 'var(--text)',
            fontWeight: Number(row.availableAfter) < safetyStockQty ? 700 : 600,
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
        <span v-else style="color: var(--muted)">-</span>
      </template>
      <template #ops="{ row }: { row: MatStockTxnVO }">
        <a-dropdown :trigger="['click']">
          <a-button class="lg-row-action-trigger" size="small" type="text">
            <MoreOutlined />
          </a-button>
          <template #overlay>
            <a-menu>
              <a-menu-item @click="emit('showDetail', row)">详情</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </template>
    </vxe-grid>
  </div>
</template>
