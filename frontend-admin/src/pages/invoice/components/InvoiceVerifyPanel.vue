<script setup lang="ts">
import { computed } from 'vue'
import type { InvoiceVO } from '@/types/invoice'
import { VERIFY_STATUS_LABEL } from '@/types/invoice'

const props = defineProps<{
  data: InvoiceVO[]
}>()

const verifyBreakdown = computed(() => {
  const m: Record<string, number> = {}
  props.data.forEach((r) => {
    const label = VERIFY_STATUS_LABEL[r.verifyStatus] ?? r.verifyStatus
    m[label] = (m[label] || 0) + 1
  })
  const total = Object.values(m).reduce((s, v) => s + v, 0) || 1
  return Object.entries(m).map(([label, count]) => ({
    label,
    count,
    pct: Math.round((count / total) * 100),
  }))
})

const kpiAbnormal = computed(
  () => props.data.filter((r) => r.verifyStatus === 'FAILED').length,
)

function dotColor(label: string): string {
  if (label === '已认证') return '#31c48d'
  if (label === '异常') return '#ef4444'
  if (label === '待核验') return '#f59e0b'
  return '#8b5cf6'
}
</script>

<template>
  <aside class="lg-analysis-rail">
    <section class="lg-panel">
      <div class="lg-panel-title">核验状态分布</div>
      <div class="lg-type-list">
        <div v-for="it in verifyBreakdown" :key="it.label" class="lg-type-row">
          <span
            class="lg-type-dot"
            :style="{ background: dotColor(it.label) }"
          ></span>
          <span class="lg-type-label">{{ it.label }}</span>
          <span class="lg-type-bar-wrap">
            <span
              class="lg-type-bar"
              :style="{
                width: it.pct + '%',
                background: dotColor(it.label),
              }"
            ></span>
          </span>
          <span class="lg-type-num">{{ it.count }}</span>
          <span class="lg-type-pct">{{ it.pct }}%</span>
        </div>
      </div>
    </section>

    <section class="lg-panel">
      <div class="lg-panel-title">异常提醒</div>
      <div class="lg-warning-list">
        <div v-if="kpiAbnormal > 0" class="lg-warning-item">
          <span class="lg-warning-project">核验失败</span>
          <span class="lg-warning-title">{{ kpiAbnormal }} 张</span>
          <span class="lg-warning-days" style="color: #ef4444">需处理</span>
        </div>
        <div v-else class="lg-warning-empty">无异常发票</div>
      </div>
    </section>
  </aside>
</template>

<style scoped></style>
