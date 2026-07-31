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
  <div class="role-reference-shell">
    <section class="role-reference-kpis role-reference-kpis--4">
      <article class="role-reference-kpi" style="--kpi-accent: #2f7cf6">
        <div class="role-reference-kpi-title">在建项目</div>
        <div class="role-reference-kpi-value">
          {{ fmtNum(data.activeProjectCount) }} <small>个</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><ProjectOutlined /> 在建范围</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #16a34a">
        <div class="role-reference-kpi-title">合同总额</div>
        <div class="role-reference-kpi-value">
          {{ fmtWan(data.totalContractAmount) }} <small>万元</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><DollarOutlined /> 经营盘子</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #f97316">
        <div class="role-reference-kpi-title">动态成本</div>
        <div class="role-reference-kpi-value is-warning">
          {{ fmtWan(data.totalDynamicCost) }} <small>万元</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><LineChartOutlined /> 成本滚动</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #ef4444">
        <div class="role-reference-kpi-title">风险预警</div>
        <div class="role-reference-kpi-value is-danger">
          {{ fmtNum(data.totalRiskCount) }} <small>项</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><WarningOutlined /> 重点预警</span>
        </div>
      </article>
    </section>

    <section class="role-reference-main-grid">
      <div class="role-reference-panel role-reference-analysis">
        <div class="role-reference-panel-head">
          <div>
            <strong>项目经营总览</strong>
            <span>项目、合同与成本全貌</span>
          </div>
        </div>
        <v-chart :option="overOpt" autoresize class="role-reference-chart" />
        <div class="role-reference-summary-grid">
          <div class="role-reference-summary-item">
            <span>在建项目</span>
            <b>{{ fmtNum(data.activeProjectCount) }} 个</b>
          </div>
          <div class="role-reference-summary-item">
            <span>合同总额</span>
            <b>{{ fmtWan(data.totalContractAmount) }} 万元</b>
          </div>
          <div class="role-reference-summary-item">
            <span>动态成本</span>
            <b>{{ fmtWan(data.totalDynamicCost) }} 万元</b>
          </div>
          <div class="role-reference-summary-item">
            <span>风险预警</span>
            <b>{{ fmtNum(data.totalRiskCount) }} 项</b>
          </div>
        </div>
      </div>

      <aside class="role-reference-side-stack">
        <div class="role-reference-panel role-mini-panel is-red">
          <div class="role-reference-panel-head mini">
            <strong
              >重大风险 <b>（{{ data.majorRisks.length }}）</b></strong
            >
          </div>
          <a-table
            :columns="alertCols"
            :data-source="data.majorRisks"
            :pagination="false"
            size="small"
            row-key="message"
            class="role-reference-table"
          />
        </div>
        <div class="role-reference-panel role-mini-panel is-orange">
          <div class="role-reference-panel-head mini">
            <strong
              >逾期事项 <b>（{{ data.overdueItems.length }}）</b></strong
            >
          </div>
          <a-table
            :columns="pmTaskCols"
            :data-source="data.overdueItems"
            :pagination="false"
            size="small"
            row-key="taskId"
            class="role-reference-table"
          />
        </div>
      </aside>
    </section>

    <section class="role-reference-panel role-reference-bottom-panel">
      <div class="role-reference-chart-grid role-reference-chart-grid--2">
        <div class="role-reference-chart-block">
          <div class="role-reference-panel-head">
            <div>
              <strong>项目风险分布</strong>
              <span>待办、风险与逾期</span>
            </div>
          </div>
          <v-chart :option="riskOpt" autoresize class="role-reference-mini-chart" />
        </div>
        <div class="role-reference-chart-block">
          <div class="role-reference-panel-head">
            <div>
              <strong>经营趋势概览</strong>
              <span>经营与成本趋势</span>
            </div>
          </div>
          <v-chart :option="trendOpt" autoresize class="role-reference-mini-chart" />
        </div>
      </div>
      <div class="role-reference-panel-head">
        <div>
          <strong>项目经营排名</strong>
          <span>项目综合表现</span>
        </div>
      </div>
      <a-table
        :columns="mgmtRankCols"
        :data-source="data.projectRankings"
        :loading="loading"
        :pagination="false"
        size="small"
        row-key="projectId"
        class="role-reference-table"
      />
    </section>
  </div>
</template>
