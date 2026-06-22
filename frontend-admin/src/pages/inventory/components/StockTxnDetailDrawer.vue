<script setup lang="ts">
import type { MatStockTxnVO } from '@/types/inventory'
import {
  TXN_TYPE_COLOR,
  TXN_TYPE_LABEL,
  getSourceTypeColor,
  getSourceTypeLabel,
} from '../composables/useStockLedger'

defineProps<{
  detailItem: MatStockTxnVO | null
  fmtQty: (val: string | number) => string
  getWarehouseName: (id: string) => string
  getMaterialName: (id: string) => string
}>()

const open = defineModel<boolean>('open', { required: true })

const emit = defineEmits<{
  close: []
}>()
</script>

<template>
  <a-drawer
    :open="open"
    title="流水详情"
    placement="right"
    :width="480"
    @close="emit('close')"
  >
    <template v-if="detailItem">
      <a-descriptions :column="2" size="small" bordered>
        <a-descriptions-item label="流水编号">{{ detailItem.id }}</a-descriptions-item>
        <a-descriptions-item label="交易类型">
          <a-tag :color="TXN_TYPE_COLOR[detailItem.txnType]">
            {{ TXN_TYPE_LABEL[detailItem.txnType] ?? detailItem.txnType }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="仓库名称">
          {{ detailItem.warehouseName || getWarehouseName(detailItem.warehouseId) }}
        </a-descriptions-item>
        <a-descriptions-item label="物料名称">
          {{ detailItem.materialName || getMaterialName(detailItem.materialId) }}
        </a-descriptions-item>
        <a-descriptions-item label="变动量">
          <span
            :style="{
              color: detailItem.txnType === 'OUT' ? '#ef4444' : '#16a34a',
              fontWeight: 600,
            }"
          >
            {{ detailItem.txnType === 'OUT' ? '−' : '+' }}{{ fmtQty(detailItem.quantity) }}
          </span>
        </a-descriptions-item>
        <a-descriptions-item label="变动后余量">
          <span style="font-weight: 600">{{ fmtQty(detailItem.availableAfter) }}</span>
        </a-descriptions-item>
        <a-descriptions-item label="来源类型">
          <a-tag :color="getSourceTypeColor(detailItem.sourceType)" size="small">
            {{ getSourceTypeLabel(detailItem.sourceType) }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="来源单号">
          {{ detailItem.sourceId || '-' }}
        </a-descriptions-item>
        <a-descriptions-item label="操作时间" :span="2">
          {{ detailItem.createdTime || '-' }}
        </a-descriptions-item>
      </a-descriptions>
    </template>
  </a-drawer>
</template>
