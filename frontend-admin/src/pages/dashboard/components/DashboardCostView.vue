<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { AimOutlined, LineChartOutlined, RiseOutlined, DollarOutlined } from '@ant-design/icons-vue'
import type { CostManagerDashboardVO, CostBreakdownVO } from '@/types/dashboard'
import { fmtWan, fmtDeviation, devColor, devSign } from '../utils/formatUtils'
import {
  costExecutionOption,
  costCompositionOption,
  costDeviationTrendOption,
  costBarOption,
} from '../utils/chartOptions'
import { alertCols } from '../utils/tableColumns'

const props = defineProps<{
  data: CostManagerDashboardVO
  breakdown: CostBreakdownVO | null
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'barClick', params: { name?: string }): void
}>()

const subs = computed(() => props.breakdown?.subjectBreakdowns ?? [])

const execOpt = computed(() => costExecutionOption(props.data))
const compOpt = computed(() => costCompositionOption(subs.value))
const devOpt = computed(() => costDeviationTrendOption(props.data))
const barOpt = computed(() => costBarOption(subs.value))

function onChartClick(params: { name?: string }) {
  emit('barClick', params)
}
</script>

<template>
  <div class="role-dashboard-grid">
    <div class="role-metric-strip">
      <div class="kpi-card">
        <div class="kpi-icon" style="background: #3b82f6"><AimOutlined /></div>
        <div class="kpi-body">
          <div class="kpi-title">目标成本</div>
          <div class="kpi-value">{{ fmtWan(data.targetCost) }} <small>万元</small></div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background: #f59e0b"><LineChartOutlined /></div>
        <div class="kpi-body">
          <div class="kpi-title">动态成本</div>
          <div class="kpi-value">{{ fmtWan(data.dynamicCost) }} <small>万元</small></div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" :style="{ background: devColor(data.costDeviation) }">
          <RiseOutlined />
        </div>
        <div class="kpi-body">
          <div class="kpi-title">成本偏差</div>
          <div class="kpi-value" :style="{ color: devColor(data.costDeviation) }">
            {{ devSign(data.costDeviation) }}{{ fmtDeviation(data.costDeviation) }}
            <small>万元</small>
          </div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background: #22c55e"><DollarOutlined /></div>
        <div class="kpi-body">
          <div class="kpi-title">预计利润</div>
          <div class="kpi-value">{{ fmtWan(data.expectedProfit) }} <small>万元</small></div>
        </div>
      </div>
    </div>
    <div class="role-analysis-grid">
      <div class="panel role-panel">
        <div class="panel-header">成本执行概览</div>
        <v-chart :option="execOpt" autoresize class="role-chart" />
      </div>
      <div class="panel role-panel">
        <div class="panel-header">成本构成分析 <span class="panel-hint">点击柱体可下钻</span></div>
        <v-chart :option="compOpt" autoresize class="role-chart" @click="onChartClick" />
      </div>
      <div class="panel role-panel">
        <div class="panel-header">偏差趋势分析</div>
        <v-chart :option="devOpt" autoresize class="role-chart" />
      </div>
    </div>
    <div class="role-table-grid">
      <div class="panel role-panel">
        <div class="panel-header">超预算预警</div>
        <a-table
          :columns="alertCols"
          :data-source="data.overBudgetAlerts"
          :pagination="false"
          size="small"
          row-key="message"
        />
      </div>
      <div class="panel role-panel">
        <div class="panel-header">成本科目排行</div>
        <v-chart :option="barOpt" autoresize class="role-mini-chart" @click="onChartClick" />
      </div>
      <div class="panel role-panel">
        <div class="panel-header">成本偏差明细</div>
        <div class="role-summary-strip">
          <span>合同锁定成本</span><b>{{ fmtWan(data.contractLockedCost) }} 万元</b
          ><span>实际成本</span><b>{{ fmtWan(data.actualCost) }} 万元</b>
        </div>
      </div>
    </div>
  </div>
</template>
