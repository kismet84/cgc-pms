<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import {
  AuditOutlined,
  WarningOutlined,
  ClockCircleOutlined,
  FileTextOutlined,
} from '@ant-design/icons-vue'
import type { ProjectManagerDashboardVO } from '@/types/dashboard'
import { fmtNum } from '../utils/formatUtils'
import {
  pmBusinessOverviewOption,
  pmCostCompositionOption,
  pmFundingOverviewOption,
} from '../utils/chartOptions'
import { pmTaskCols, pmProjectCols, pmContractCols } from '../utils/tableColumns'

const props = defineProps<{
  data: ProjectManagerDashboardVO
  loading: boolean
}>()

const pmBizOpt = computed(() =>
  pmBusinessOverviewOption({
    pendingTaskCount: props.data.pendingTaskCount,
    laggingProjectCount: props.data.laggingProjectCount,
    pendingApprovalCount: props.data.pendingApprovalCount,
    expiringContractCount: props.data.expiringContractCount,
  }),
)
const pmCostOpt = computed(() => pmCostCompositionOption())
const pmFundingOpt = computed(() => pmFundingOverviewOption())
</script>

<template>
  <div class="kpi-grid kpi-grid-4">
    <div class="kpi-card">
      <div class="kpi-icon" style="background: #3b82f6"><AuditOutlined /></div>
      <div class="kpi-body">
        <div class="kpi-title">待办任务</div>
        <div class="kpi-value">{{ fmtNum(data.pendingTaskCount) }} <small>项</small></div>
        <div class="kpi-delta">较昨日 +2</div>
      </div>
    </div>
    <div class="kpi-card">
      <div class="kpi-icon" style="background: #f59e0b"><WarningOutlined /></div>
      <div class="kpi-body">
        <div class="kpi-title">滞后项目</div>
        <div class="kpi-value">{{ fmtNum(data.laggingProjectCount) }} <small>个</small></div>
        <div class="kpi-delta danger">较昨日 +1</div>
      </div>
    </div>
    <div class="kpi-card">
      <div class="kpi-icon" style="background: #22c55e"><ClockCircleOutlined /></div>
      <div class="kpi-body">
        <div class="kpi-title">待审批</div>
        <div class="kpi-value">{{ fmtNum(data.pendingApprovalCount) }} <small>项</small></div>
        <div class="kpi-delta success">较昨日 -3</div>
      </div>
    </div>
    <div class="kpi-card">
      <div class="kpi-icon" style="background: #ef4444"><FileTextOutlined /></div>
      <div class="kpi-body">
        <div class="kpi-title">临期合同</div>
        <div class="kpi-value">{{ fmtNum(data.expiringContractCount) }} <small>份</small></div>
        <div class="kpi-delta danger">30天内到期</div>
      </div>
    </div>
  </div>

  <div class="pm-reference-grid">
    <div class="panel pm-panel pm-business-panel">
      <div class="panel-header">项目经营概览</div>
      <v-chart :option="pmBizOpt" autoresize class="pm-chart" />
    </div>
    <div class="panel pm-panel pm-cost-panel">
      <div class="panel-header">成本构成分析</div>
      <v-chart :option="pmCostOpt" autoresize class="pm-donut-chart" />
    </div>
    <div class="panel pm-panel pm-funding-panel">
      <div class="panel-header">资金收支概览</div>
      <v-chart :option="pmFundingOpt" autoresize class="pm-chart" />
    </div>
  </div>

  <div class="pm-bottom-grid">
    <div class="panel pm-table-panel">
      <div class="panel-header">待办任务</div>
      <a-table
        :columns="pmTaskCols"
        :data-source="data.pendingTasks"
        :loading="loading"
        :pagination="false"
        size="small"
        row-key="taskId"
      />
    </div>
    <div class="panel pm-table-panel">
      <div class="panel-header">滞后项目</div>
      <a-table
        :columns="pmProjectCols"
        :data-source="data.laggingProjects"
        :loading="loading"
        :pagination="false"
        size="small"
        row-key="projectId"
      />
    </div>
    <div class="panel pm-table-panel">
      <div class="panel-header">临期合同（30天内到期）</div>
      <a-table
        :columns="pmContractCols"
        :data-source="data.expiringContracts"
        :loading="loading"
        :pagination="false"
        size="small"
        row-key="contractId"
      />
    </div>
  </div>
</template>
