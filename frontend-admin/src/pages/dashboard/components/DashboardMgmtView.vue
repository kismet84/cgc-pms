<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import {
  ProjectOutlined,
  DollarOutlined,
  LineChartOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import type { ManagementDashboardVO } from '@/types/dashboard'
import { fmtWan, fmtNum } from '../utils/formatUtils'
import { mgmtOverviewOption, mgmtRiskOption, mgmtTrendOption } from '../utils/chartOptions'
import { mgmtRankCols, alertCols, pmTaskCols } from '../utils/tableColumns'

const props = defineProps<{
  data: ManagementDashboardVO
  loading: boolean
}>()

const overOpt = computed(() => mgmtOverviewOption(props.data))
const riskOpt = computed(() =>
  mgmtRiskOption({
    totalPendingTaskCount: props.data.totalPendingTaskCount,
    totalRiskCount: props.data.totalRiskCount,
    overdueItemCount: props.data.overdueItems.length,
  }),
)
const trendOpt = computed(() => mgmtTrendOption(props.data))
</script>

<template>
  <div class="role-dashboard-grid">
    <div class="role-metric-strip">
      <div class="kpi-card">
        <div class="kpi-icon" style="background: #3b82f6"><ProjectOutlined /></div>
        <div class="kpi-body">
          <div class="kpi-title">在建项目</div>
          <div class="kpi-value">{{ fmtNum(data.activeProjectCount) }} <small>个</small></div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background: #22c55e"><DollarOutlined /></div>
        <div class="kpi-body">
          <div class="kpi-title">合同总额</div>
          <div class="kpi-value">{{ fmtWan(data.totalContractAmount) }} <small>万元</small></div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background: #f59e0b"><LineChartOutlined /></div>
        <div class="kpi-body">
          <div class="kpi-title">动态成本</div>
          <div class="kpi-value">{{ fmtWan(data.totalDynamicCost) }} <small>万元</small></div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background: #ef4444"><WarningOutlined /></div>
        <div class="kpi-body">
          <div class="kpi-title">风险预警</div>
          <div class="kpi-value">{{ fmtNum(data.totalRiskCount) }} <small>项</small></div>
        </div>
      </div>
    </div>
    <div class="role-analysis-grid">
      <div class="panel role-panel">
        <div class="panel-header">项目经营总览</div>
        <v-chart :option="overOpt" autoresize class="role-chart" />
      </div>
      <div class="panel role-panel">
        <div class="panel-header">项目风险分布</div>
        <v-chart :option="riskOpt" autoresize class="role-chart" />
      </div>
      <div class="panel role-panel">
        <div class="panel-header">经营趋势概览</div>
        <v-chart :option="trendOpt" autoresize class="role-chart" />
      </div>
    </div>
    <div class="role-table-grid">
      <div class="panel role-panel">
        <div class="panel-header">项目经营排名</div>
        <a-table
          :columns="mgmtRankCols"
          :data-source="data.projectRankings"
          :loading="loading"
          :pagination="false"
          size="small"
          row-key="projectId"
        />
      </div>
      <div class="panel role-panel">
        <div class="panel-header">重大风险</div>
        <a-table
          :columns="alertCols"
          :data-source="data.majorRisks"
          :pagination="false"
          size="small"
          row-key="message"
        />
      </div>
      <div class="panel role-panel">
        <div class="panel-header">逾期事项（&gt;7天）</div>
        <a-table
          :columns="pmTaskCols"
          :data-source="data.overdueItems"
          :pagination="false"
          size="small"
          row-key="taskId"
        />
      </div>
    </div>
  </div>
</template>
