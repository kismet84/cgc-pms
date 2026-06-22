<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { DollarOutlined, SwapOutlined, FileTextOutlined, FundOutlined } from '@ant-design/icons-vue'
import type { BusinessManagerDashboardVO } from '@/types/dashboard'
import { fmtWan } from '../utils/formatUtils'
import { bmBusinessOption, bmChangeOption, bmSettlementOption } from '../utils/chartOptions'
import { bmChangeCols, bmSettleCols } from '../utils/tableColumns'

const props = defineProps<{
  data: BusinessManagerDashboardVO
  loading: boolean
}>()

const bizOpt = computed(() => bmBusinessOption(props.data))
const chgOpt = computed(() => bmChangeOption(props.data))
const settleOpt = computed(() => bmSettlementOption())
</script>

<template>
  <div class="role-dashboard-grid">
    <div class="role-metric-strip">
      <div class="kpi-card">
        <div class="kpi-icon" style="background: #3b82f6"><DollarOutlined /></div>
        <div class="kpi-body">
          <div class="kpi-title">合同总额</div>
          <div class="kpi-value">{{ fmtWan(data.totalContractAmount) }} <small>万元</small></div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background: #f59e0b"><SwapOutlined /></div>
        <div class="kpi-body">
          <div class="kpi-title">合同变更</div>
          <div class="kpi-value">{{ fmtWan(data.contractChangeAmount) }} <small>万元</small></div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background: #8b5cf6"><FileTextOutlined /></div>
        <div class="kpi-body">
          <div class="kpi-title">签证变更</div>
          <div class="kpi-value">{{ fmtWan(data.varOrderAmount) }} <small>万元</small></div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background: #22c55e"><FundOutlined /></div>
        <div class="kpi-body">
          <div class="kpi-title">结算进度</div>
          <div class="kpi-value">{{ data.settlementProgress }}</div>
        </div>
      </div>
    </div>
    <div class="role-analysis-grid">
      <div class="panel role-panel">
        <div class="panel-header">合同经营概览</div>
        <v-chart :option="bizOpt" autoresize class="role-chart" />
      </div>
      <div class="panel role-panel">
        <div class="panel-header">变更签证分析</div>
        <v-chart :option="chgOpt" autoresize class="role-chart" />
      </div>
      <div class="panel role-panel">
        <div class="panel-header">结算收付概览</div>
        <v-chart :option="settleOpt" autoresize class="role-chart" />
      </div>
    </div>
    <div class="role-table-grid">
      <div class="panel role-panel">
        <div class="panel-header">近期合同变更</div>
        <a-table
          :columns="bmChangeCols"
          :data-source="data.recentChanges"
          :loading="loading"
          :pagination="false"
          size="small"
          row-key="contractId"
        />
      </div>
      <div class="panel role-panel">
        <div class="panel-header">待结算事项</div>
        <a-table
          :columns="bmSettleCols"
          :data-source="data.settlementItems"
          :loading="loading"
          :pagination="false"
          size="small"
          row-key="projectId"
        />
      </div>
      <div class="panel role-panel">
        <div class="panel-header">收付款关注</div>
        <div class="role-summary-strip">
          <span>付款比例</span><b>{{ data.paidRatio }}</b
          ><span>分包计量</span><b>{{ fmtWan(data.subMeasureAmount) }} 万元</b>
        </div>
      </div>
    </div>
  </div>
</template>
