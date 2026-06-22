<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import {
  PayCircleOutlined,
  ClockCircleOutlined,
  AuditOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import type { FinanceDashboardVO } from '@/types/dashboard'
import { fmtWan, fmtNum } from '../utils/formatUtils'
import {
  financePaymentOption,
  financeStructureOption,
  financeRiskOption,
} from '../utils/chartOptions'
import { financePayCols } from '../utils/tableColumns'

const props = defineProps<{
  data: FinanceDashboardVO
  loading: boolean
}>()

const payOpt = computed(() => financePaymentOption(props.data))
const structOpt = computed(() => financeStructureOption(props.data))
const riskOpt = computed(() => financeRiskOption())
</script>

<template>
  <div class="role-dashboard-grid">
    <div class="role-metric-strip">
      <div class="kpi-card">
        <div class="kpi-icon" style="background: #ef4444"><PayCircleOutlined /></div>
        <div class="kpi-body">
          <div class="kpi-title">待付款金额</div>
          <div class="kpi-value">{{ fmtWan(data.pendingPaymentAmount) }} <small>万元</small></div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background: #f59e0b"><ClockCircleOutlined /></div>
        <div class="kpi-body">
          <div class="kpi-title">待付款笔数</div>
          <div class="kpi-value">{{ fmtNum(data.pendingPaymentCount) }} <small>笔</small></div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background: #3b82f6"><AuditOutlined /></div>
        <div class="kpi-body">
          <div class="kpi-title">已审批未支付</div>
          <div class="kpi-value">{{ fmtWan(data.approvedUnpaidAmount) }} <small>万元</small></div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background: #14b8c7"><WarningOutlined /></div>
        <div class="kpi-body">
          <div class="kpi-title">质保金到期</div>
          <div class="kpi-value">{{ fmtWan(data.warrantyExpiringAmount) }} <small>万元</small></div>
        </div>
      </div>
    </div>
    <div class="role-analysis-grid">
      <div class="panel role-panel">
        <div class="panel-header">资金支付概览</div>
        <v-chart :option="payOpt" autoresize class="role-chart" />
      </div>
      <div class="panel role-panel">
        <div class="panel-header">付款结构分析</div>
        <v-chart :option="structOpt" autoresize class="role-chart" />
      </div>
      <div class="panel role-panel">
        <div class="panel-header">资金风险概览</div>
        <v-chart :option="riskOpt" autoresize class="role-chart" />
      </div>
    </div>
    <div class="role-table-grid">
      <div class="panel role-panel">
        <div class="panel-header">待付款明细</div>
        <a-table
          :columns="financePayCols"
          :data-source="data.pendingPayments"
          :loading="loading"
          :pagination="false"
          size="small"
          row-key="payRecordId"
        />
      </div>
      <div class="panel role-panel">
        <div class="panel-header">超比例付款</div>
        <a-table
          :columns="financePayCols"
          :data-source="data.overRatioPayments"
          :loading="loading"
          :pagination="false"
          size="small"
          row-key="payRecordId"
        />
      </div>
      <div class="panel role-panel">
        <div class="panel-header">质保金到期</div>
        <div class="role-summary-strip">
          <span>到期金额</span><b>{{ fmtWan(data.warrantyExpiringAmount) }} 万元</b
          ><span>风险金额</span><b>{{ fmtWan(data.overRatioAmount) }} 万元</b>
        </div>
      </div>
    </div>
  </div>
</template>
