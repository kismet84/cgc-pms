<script setup lang="ts">
import { computed } from 'vue'
import type { ProcurementTrace } from '@/api/modules/procurement'

const props = defineProps<{
  open: boolean
  loading?: boolean
  trace?: ProcurementTrace
}>()

const emit = defineEmits<{ close: [] }>()

const entityRows = computed(() => {
  const trace = props.trace
  if (!trace) return []
  return [
    ['项目', trace.project],
    ['合同', trace.contract],
    ['采购申请', trace.purchaseRequest],
    ['采购订单', trace.purchaseOrder],
    ['材料验收', trace.receipt],
    ['领料申请', trace.requisition],
    ['退料单', trace.materialReturn],
    ['退供单', trace.supplierReturn],
  ].filter((row): row is [string, Record<string, unknown>] => Boolean(row[1]))
})

function valueOf(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key]
    if (value != null && String(value).trim()) return String(value)
  }
  return '-'
}
</script>

<template>
  <a-drawer :open="open" title="采购—库存—成本全链路追溯" :width="720" @close="emit('close')">
    <a-spin :spinning="loading">
      <a-alert
        message="以下数据由业务外键与来源字段自动关联，不接受人工拼接。"
        type="info"
        show-icon
        style="margin-bottom: 16px"
      />
      <a-timeline v-if="entityRows.length">
        <a-timeline-item
          v-for="([label, record], index) in entityRows"
          :key="label"
          :color="index === entityRows.length - 1 ? 'green' : 'blue'"
        >
          <strong>{{ label }}</strong>
          <div class="trace-code">
            {{
              valueOf(record, [
                'projectName',
                'contractName',
                'requestCode',
                'orderCode',
                'receiptCode',
                'requisitionCode',
                'returnCode',
                'code',
                'id',
              ])
            }}
          </div>
        </a-timeline-item>
      </a-timeline>
      <a-empty v-else description="暂无可追溯业务数据" />
      <a-divider />
      <a-descriptions :column="2" bordered size="small">
        <a-descriptions-item label="库存流水"
          >{{ trace?.stockTransactions?.length ?? 0 }} 条</a-descriptions-item
        >
        <a-descriptions-item label="成本记录"
          >{{ trace?.costs?.length ?? 0 }} 条</a-descriptions-item
        >
        <a-descriptions-item label="审批实例"
          >{{ trace?.approvalInstances?.length ?? 0 }} 个</a-descriptions-item
        >
        <a-descriptions-item label="审批记录"
          >{{ trace?.approvalRecords?.length ?? 0 }} 条</a-descriptions-item
        >
      </a-descriptions>
    </a-spin>
  </a-drawer>
</template>

<style scoped>
.trace-code {
  margin-top: 4px;
  color: var(--muted);
  word-break: break-all;
}
</style>
