<script setup lang="ts">
import { computed } from 'vue'
import { V2Badge, V2Button, V2Dialog, V2PageState } from '@/components'
import type { PaymentTraceEntity, PaymentTraceRecord } from '@cgc-pms/frontend-contracts'

const props = defineProps<{
  open: boolean
  traces: PaymentTraceRecord[]
  loading?: boolean
  error?: string
}>()
const emit = defineEmits<{ close: [] }>()
const one = (entity?: PaymentTraceEntity | null) => (entity ? [entity] : [])

const groups = computed(
  () =>
    [
      ['审批', props.traces.flatMap((trace) => trace.approvalRecords || [])],
      ['来源', props.traces.flatMap((trace) => trace.applicationSources || [])],
      ['付款', props.traces.flatMap((trace) => trace.paymentRecords || [])],
      ['现金日记', props.traces.flatMap((trace) => trace.cashJournals || [])],
      ['不可变证据', props.traces.flatMap((trace) => trace.paymentDocuments || [])],
      ['发票', props.traces.flatMap((trace) => trace.invoices || [])],
      ['会计凭证', props.traces.flatMap((trace) => trace.accountingEntries || [])],
      ['合同预算', props.traces.flatMap((trace) => one(trace.contractBudgetAllocation))],
      ['项目预算', props.traces.flatMap((trace) => one(trace.projectBudget))],
      ['项目预算行', props.traces.flatMap((trace) => one(trace.projectBudgetLine))],
      ['成本科目', props.traces.flatMap((trace) => one(trace.costSubject))],
      ['材料验收', props.traces.flatMap((trace) => trace.materialReceipts || [])],
      ['验收明细', props.traces.flatMap((trace) => trace.materialReceiptItems || [])],
    ] as Array<[string, PaymentTraceEntity[]]>,
)

function text(entity: PaymentTraceEntity | null | undefined, ...keys: string[]): string {
  if (!entity) return '—'
  for (const key of keys) {
    const value = entity[key]
    if (value !== null && value !== undefined && String(value).trim()) return String(value)
  }
  return entity.id ? String(entity.id) : '—'
}
</script>

<template>
  <V2Dialog
    :open="open"
    title="付款全链路 Trace"
    description="服务端权威关系；缺链由接口直接拒绝，不在页面补链。"
    panel-class="payment-trace-dialog"
    @update:open="(value) => !value && emit('close')"
  >
    <V2PageState v-if="loading" kind="loading" title="正在读取 Trace" />
    <V2PageState v-else-if="error" kind="error" title="Trace 不完整" :description="error" />
    <V2PageState
      v-else-if="!traces.length"
      kind="empty"
      title="暂无 Trace"
      description="当前对象尚无可追溯付款事实。"
    />
    <div v-else class="payment-trace-dialog__content">
      <article v-for="(trace, index) in traces" :key="text(trace.paymentApplication, 'id') + index">
        <header>
          <strong>{{ text(trace.paymentApplication, 'applyCode', 'id') }}</strong>
          <V2Badge tone="info">{{
            text(trace.paymentApplication, 'approvalStatus', 'payStatus')
          }}</V2Badge>
        </header>
        <dl>
          <div>
            <dt>项目</dt>
            <dd>{{ text(trace.project, 'projectName', 'projectCode') }}</dd>
          </div>
          <div>
            <dt>合同</dt>
            <dd>{{ text(trace.contract, 'contractName', 'contractCode') }}</dd>
          </div>
          <div>
            <dt>申请金额</dt>
            <dd>{{ text(trace.paymentApplication, 'applyAmount') }}</dd>
          </div>
          <div>
            <dt>合同当前额</dt>
            <dd>{{ text(trace.contract, 'currentAmount') }}</dd>
          </div>
          <div>
            <dt>预算净占用</dt>
            <dd>{{ trace.budgetConservation?.netReserved ?? '—' }}</dd>
          </div>
          <div>
            <dt>预算净消耗</dt>
            <dd>{{ trace.budgetConservation?.netConsumed ?? '—' }}</dd>
          </div>
          <div>
            <dt>来源净实付</dt>
            <dd>{{ trace.budgetConservation?.netPaid ?? '—' }}</dd>
          </div>
          <div>
            <dt>现金净流出</dt>
            <dd>{{ trace.budgetConservation?.netCashOutflow ?? '—' }}</dd>
          </div>
        </dl>
      </article>
      <section class="payment-trace-dialog__groups" aria-label="付款追溯节点">
        <div v-for="[name, rows] in groups" :key="name">
          <span>{{ name }}</span
          ><strong>{{ rows.length }}</strong>
        </div>
      </section>
    </div>
    <template #footer>
      <V2Button variant="secondary" @click="emit('close')">关闭</V2Button>
    </template>
  </V2Dialog>
</template>

<style scoped>
.payment-trace-dialog__content,
.payment-trace-dialog__content article,
.payment-trace-dialog__groups {
  display: grid;
  gap: var(--v2-space-3);
}
.payment-trace-dialog__content article {
  padding: var(--v2-space-3);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}
.payment-trace-dialog__content header,
.payment-trace-dialog__groups div {
  display: flex;
  justify-content: space-between;
  gap: var(--v2-space-2);
}
.payment-trace-dialog__content dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-2);
  margin: 0;
}
.payment-trace-dialog__content dl div {
  min-width: 0;
}
.payment-trace-dialog__content dt {
  color: var(--v2-color-text-secondary);
}
.payment-trace-dialog__content dd {
  margin: var(--v2-space-1) 0 0;
  overflow-wrap: anywhere;
}
.payment-trace-dialog__groups {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.payment-trace-dialog__groups div {
  padding: var(--v2-space-2);
  background: var(--v2-color-surface-muted);
  border-radius: var(--v2-radius-sm);
}
@media (max-width: 48rem) {
  .payment-trace-dialog__content dl,
  .payment-trace-dialog__groups {
    grid-template-columns: 1fr;
  }
}
</style>
