<script setup lang="ts">
interface TypeDistItem {
  key: string
  label: string
  value: number
  color: string
}

interface StatusBarItem {
  key: string
  label: string
  value: number
  color: string
  percent: number
}

interface WarningRow {
  project: string
  title: string
  days: number
}

defineProps<{
  typeDistribution: TypeDistItem[]
  typePercent: (value: number) => number
  statusBars: StatusBarItem[]
  warningRows: WarningRow[]
}>()

const emit = defineEmits<{
  (e: 'allAlerts'): void
}>()
</script>

<template>
  <aside class="lg-analysis-rail">
    <section class="lg-panel">
      <div class="lg-panel-title">合同类型分布</div>
      <div class="lg-type-list">
        <div v-for="item in typeDistribution" :key="item.key" class="lg-type-row">
          <span class="lg-type-dot" :style="{ background: item.color }"></span>
          <span class="lg-type-label">{{ item.label }}</span>
          <span class="lg-type-bar-wrap">
            <span
              class="lg-type-bar"
              :style="{ width: typePercent(item.value) + '%', background: item.color }"
            ></span>
          </span>
          <span class="lg-type-num">{{ item.value }}</span>
          <span class="lg-type-pct">{{ typePercent(item.value) }}%</span>
        </div>
      </div>
    </section>

    <section class="lg-panel">
      <div class="lg-panel-title">合同状态</div>
      <div class="lg-type-list">
        <div v-for="item in statusBars" :key="item.key" class="lg-type-row">
          <span class="lg-type-dot" :style="{ background: item.color }"></span>
          <span class="lg-type-label">{{ item.label }}</span>
          <span class="lg-type-bar-wrap">
            <span
              class="lg-type-bar"
              :style="{ width: item.percent + '%', background: item.color }"
            ></span>
          </span>
          <span class="lg-type-num">{{ item.value }}</span>
          <span class="lg-type-pct">{{ item.percent }}%</span>
        </div>
      </div>
    </section>

    <section class="lg-panel">
      <div class="lg-warning-head">
        <div class="lg-panel-title" style="margin-bottom: 0">逾期预警</div>
        <a-button type="link" size="small" @click="emit('allAlerts')">查看全部</a-button>
      </div>
      <div class="lg-warning-list">
        <div
          v-for="row in warningRows"
          :key="`${row.project}-${row.title}`"
          class="lg-warning-item"
        >
          <span class="lg-warning-project">{{ row.project }}</span>
          <span class="lg-warning-title">{{ row.title }}</span>
          <span class="lg-warning-days">{{ row.days }}天</span>
        </div>
        <div v-if="!warningRows.length" class="lg-warning-empty">暂无逾期合同</div>
      </div>
    </section>
  </aside>
</template>
