<script setup lang="ts">
import { computed } from 'vue'
import {
  FileTextOutlined,
  DollarOutlined,
  PayCircleOutlined,
  WalletOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons-vue'
import type { ContractKpiVO } from '@/types/contract'

const props = defineProps<{
  kpi: ContractKpiVO
  isMobile: boolean
  fmtAmount: (val: string) => string
  kpiMax: { totalCount: number; totalAmount: number; overdueCount: number }
  kpiPct: (value: number, max: number) => number
}>()

const mobileItems = computed(() => [
  {
    icon: FileTextOutlined,
    bg: 'var(--kpi-total)',
    label: '合同总数',
    value: props.kpi.totalCount,
    unit: '份',
  },
  {
    icon: DollarOutlined,
    bg: 'var(--kpi-amount)',
    label: '合同总金额(含税)',
    value: props.fmtAmount(props.kpi.totalAmount),
    unit: '万元',
  },
  {
    icon: PayCircleOutlined,
    bg: 'var(--kpi-paid)',
    label: '已付款金额',
    value: props.fmtAmount(props.kpi.paidAmount),
    unit: '万元',
  },
  {
    icon: WalletOutlined,
    bg: 'var(--kpi-unpaid)',
    label: '未付款金额',
    value: props.fmtAmount(props.kpi.unpaidAmount),
    unit: '万元',
  },
  {
    icon: ClockCircleOutlined,
    bg: 'var(--kpi-overdue)',
    label: '逾期合同数',
    value: props.kpi.overdueCount,
    unit: '份',
  },
])
</script>

<template>
  <!-- KPI 桌面/平板 -->
  <div v-if="!isMobile" class="lg-kpi-strip">
    <div class="lg-kpi-card">
      <span class="lg-kpi-card-label">合同总数</span>
      <span class="lg-kpi-card-value">{{ kpi.totalCount }} <small>份</small></span>
      <span class="lg-kpi-card-bar"
        ><span style="width: 100%; background: var(--kpi-total)"></span
      ></span>
    </div>
    <div class="lg-kpi-card">
      <span class="lg-kpi-card-label">合同总金额(含税)</span>
      <span class="lg-kpi-card-value"
        >{{ fmtAmount(kpi.totalAmount) }} <small>万元</small></span
      >
      <span class="lg-kpi-card-bar"
        ><span style="width: 100%; background: var(--kpi-amount)"></span
      ></span>
    </div>
    <div class="lg-kpi-card">
      <span class="lg-kpi-card-label">已付款</span>
      <span class="lg-kpi-card-value"
        >{{ fmtAmount(kpi.paidAmount) }} <small>万元</small></span
      >
      <span class="lg-kpi-card-bar"
        ><span
          :style="{
            width: kpiPct(parseFloat(kpi.paidAmount), kpiMax.totalAmount) + '%',
            background: 'var(--kpi-paid)',
          }"
        ></span
      ></span>
      <span class="lg-kpi-card-hint"
        >{{ kpiPct(parseFloat(kpi.paidAmount), kpiMax.totalAmount) }}%</span
      >
    </div>
    <div class="lg-kpi-card">
      <span class="lg-kpi-card-label">未付款</span>
      <span class="lg-kpi-card-value"
        >{{ fmtAmount(kpi.unpaidAmount) }} <small>万元</small></span
      >
      <span class="lg-kpi-card-bar"
        ><span
          :style="{
            width: kpiPct(parseFloat(kpi.unpaidAmount), kpiMax.totalAmount) + '%',
            background: 'var(--kpi-unpaid)',
          }"
        ></span
      ></span>
      <span class="lg-kpi-card-hint"
        >{{ kpiPct(parseFloat(kpi.unpaidAmount), kpiMax.totalAmount) }}%</span
      >
    </div>
    <div class="lg-kpi-card is-warn">
      <span class="lg-kpi-card-label">逾期合同</span>
      <span class="lg-kpi-card-value">{{ kpi.overdueCount }} <small>份</small></span>
      <span class="lg-kpi-card-bar"
        ><span
          :style="{
            width: kpiPct(kpi.overdueCount, kpiMax.overdueCount) + '%',
            background: 'var(--kpi-overdue)',
          }"
        ></span
      ></span>
      <span class="lg-kpi-card-hint" v-if="kpi.overdueCount"
        >占 {{ kpiPct(kpi.overdueCount, kpiMax.totalCount) }}%</span
      >
    </div>
  </div>

  <!-- KPI 移动端：单条卡片 -->
  <div v-else class="lg-kpi-single">
    <div
      class="lg-kpi-single-row"
      v-for="item in mobileItems"
      :key="item.label"
    >
      <div class="lg-kpi-single-icon" :style="{ background: item.bg }">
        <component :is="item.icon" />
      </div>
      <span class="lg-kpi-single-label">{{ item.label }}</span>
      <span class="lg-kpi-single-value"
        >{{ item.value }} <small>{{ item.unit }}</small></span
      >
    </div>
  </div>
</template>
