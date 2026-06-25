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
import { ColumnSettingsButton } from '@/components/list-page'
import { useColumnSettings } from '@/composables/useColumnSettings'

const props = defineProps<{
  txnList: MatStockTxnVO[]
  loading: boolean
  gridColumns: Record<string, unknown>[]
  fmtQty: (val: string | number) => string
}>()

const gridColumnsSource = computed(() => props.gridColumns)
const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('stock_txn_cols', gridColumnsSource)

const emit = defineEmits<{
  sortChange: [params: { field: string; order: 'asc' | 'desc' | null }]
  showDetail: [row: MatStockTxnVO]
}>()
</script>

<template>
  <div class="lg-table-wrap">
    <div class="stock-txn-header">
      <span>出入库流水</span>
      <ColumnSettingsButton :columns="columnSettings" :visible="colVisible" @toggle="toggleCol" />
    </div>
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

<style scoped>
.stock-txn-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px 0;
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
}
</style>
