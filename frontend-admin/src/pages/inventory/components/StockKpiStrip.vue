<script setup lang="ts">
import {
  InboxOutlined,
  FallOutlined,
  RiseOutlined,
  AlertOutlined,
} from '@ant-design/icons-vue'
import type { StockKpiVO } from '@/types/inventory'

defineProps<{
  kpi: StockKpiVO
  kpiMax: { txnInCount: number; txnOutCount: number }
  kpiPct: (value: number, max: number) => number
  isMobile: boolean
}>()
</script>

<template>
  <!-- KPI 横条：桌面 -->
  <div v-if="!isMobile" class="lg-kpi-strip">
    <div class="lg-kpi-card">
      <span class="lg-kpi-card-label">仓库数量</span>
      <span class="lg-kpi-card-value">{{ kpi.warehouseCount }} <small>个</small></span>
      <span class="lg-kpi-card-bar"
        ><span style="width: 100%; background: var(--kpi-total)"></span
      ></span>
    </div>
    <div class="lg-kpi-card">
      <span class="lg-kpi-card-label">物料种类</span>
      <span class="lg-kpi-card-value">{{ kpi.materialTypeCount }} <small>种</small></span>
      <span class="lg-kpi-card-bar"
        ><span style="width: 100%; background: var(--kpi-amount)"></span
      ></span>
    </div>
    <div class="lg-kpi-card is-warn" v-if="kpi.lowStockCount > 0" :key="'warn'">
      <span class="lg-kpi-card-label">低库存物料</span>
      <span class="lg-kpi-card-value">{{ kpi.lowStockCount }} <small>种</small></span>
      <span class="lg-kpi-card-bar"
        ><span
          :style="{
            width: kpiPct(kpi.lowStockCount, Math.max(kpi.materialTypeCount, 1)) + '%',
            background: 'var(--kpi-overdue)',
          }"
        ></span
      ></span>
    </div>
    <div class="lg-kpi-card" v-else :key="'normal'">
      <span class="lg-kpi-card-label">低库存物料</span>
      <span class="lg-kpi-card-value">0 <small>种</small></span>
      <span class="lg-kpi-card-bar"
        ><span style="width: 0%; background: var(--kpi-overdue)"></span
      ></span>
    </div>
    <div class="lg-kpi-card">
      <span class="lg-kpi-card-label">入库记录</span>
      <span class="lg-kpi-card-value">{{ kpi.txnInCount }} <small>条</small></span>
      <span class="lg-kpi-card-bar"
        ><span
          :style="{
            width: kpiPct(kpi.txnInCount, kpiMax.txnInCount) + '%',
            background: 'var(--kpi-paid)',
          }"
        ></span
      ></span>
    </div>
    <div class="lg-kpi-card">
      <span class="lg-kpi-card-label">出库记录</span>
      <span class="lg-kpi-card-value">{{ kpi.txnOutCount }} <small>条</small></span>
      <span class="lg-kpi-card-bar"
        ><span
          :style="{
            width: kpiPct(kpi.txnOutCount, kpiMax.txnOutCount) + '%',
            background: 'var(--kpi-unpaid)',
          }"
        ></span
      ></span>
    </div>
  </div>

  <!-- KPI 移动端：单卡片 -->
  <div v-else class="lg-kpi-single">
    <div
      class="lg-kpi-single-row"
      v-for="item in [
        {
          icon: InboxOutlined,
          bg: 'var(--kpi-total)',
          label: '仓库数量',
          value: kpi.warehouseCount,
          unit: '个',
        },
        {
          icon: InboxOutlined,
          bg: 'var(--kpi-amount)',
          label: '物料种类',
          value: kpi.materialTypeCount,
          unit: '种',
        },
        {
          icon: AlertOutlined,
          bg: 'var(--kpi-overdue)',
          label: '低库存物料',
          value: kpi.lowStockCount,
          unit: '种',
        },
        {
          icon: RiseOutlined,
          bg: 'var(--kpi-paid)',
          label: '入库记录',
          value: kpi.txnInCount,
          unit: '条',
        },
        {
          icon: FallOutlined,
          bg: 'var(--kpi-unpaid)',
          label: '出库记录',
          value: kpi.txnOutCount,
          unit: '条',
        },
      ]"
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
