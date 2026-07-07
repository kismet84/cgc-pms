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
  <div class="role-reference-shell">
    <section class="role-reference-kpis role-reference-kpis--4">
      <article class="role-reference-kpi" style="--kpi-accent: #ef4444">
        <div class="role-reference-kpi-title">待付款金额</div>
        <div class="role-reference-kpi-value is-danger">
          {{ fmtWan(data.pendingPaymentAmount) }} <small>万元</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><PayCircleOutlined /> 当前待付</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #f97316">
        <div class="role-reference-kpi-title">待付款笔数</div>
        <div class="role-reference-kpi-value is-warning">
          {{ fmtNum(data.pendingPaymentCount) }} <small>笔</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><ClockCircleOutlined /> 待处理付款</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #2f7cf6">
        <div class="role-reference-kpi-title">已审批未支付</div>
        <div class="role-reference-kpi-value">
          {{ fmtWan(data.approvedUnpaidAmount) }} <small>万元</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><AuditOutlined /> 审批已完成</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #14b8a6">
        <div class="role-reference-kpi-title">质保金到期</div>
        <div class="role-reference-kpi-value is-cyan">
          {{ fmtWan(data.warrantyExpiringAmount) }} <small>万元</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><WarningOutlined /> 临近释放</span>
        </div>
      </article>
    </section>

    <section class="role-reference-main-grid">
      <div class="role-reference-panel role-reference-analysis">
        <div class="role-reference-panel-head">
          <div>
            <strong>资金支付概览</strong>
            <span>付款排队与资金占用</span>
          </div>
        </div>
        <v-chart :option="payOpt" autoresize class="role-reference-chart" />
        <div class="role-reference-summary-grid">
          <div class="role-reference-summary-item">
            <span>待付款金额</span>
            <b>{{ fmtWan(data.pendingPaymentAmount) }} 万元</b>
          </div>
          <div class="role-reference-summary-item">
            <span>待付款笔数</span>
            <b>{{ fmtNum(data.pendingPaymentCount) }} 笔</b>
          </div>
          <div class="role-reference-summary-item">
            <span>已审批未支付</span>
            <b>{{ fmtWan(data.approvedUnpaidAmount) }} 万元</b>
          </div>
          <div class="role-reference-summary-item">
            <span>超比例风险</span>
            <b>{{ fmtWan(data.overRatioAmount) }} 万元</b>
          </div>
        </div>
      </div>

      <aside class="role-reference-side-stack">
        <div class="role-reference-panel role-mini-panel is-blue">
          <div class="role-reference-panel-head mini">
            <strong
              >待付款明细 <b>（{{ data.pendingPayments.length }}）</b></strong
            >
          </div>
          <a-table
            :columns="financePayCols"
            :data-source="data.pendingPayments"
            :loading="loading"
            :pagination="false"
            size="small"
            row-key="payRecordId"
            class="role-reference-table"
          />
        </div>
        <div class="role-reference-panel role-mini-panel is-red">
          <div class="role-reference-panel-head mini">
            <strong
              >超比例付款 <b>（{{ data.overRatioPayments.length }}）</b></strong
            >
          </div>
          <a-table
            :columns="financePayCols"
            :data-source="data.overRatioPayments"
            :loading="loading"
            :pagination="false"
            size="small"
            row-key="payRecordId"
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
              <strong>付款结构分析</strong>
              <span>付款类别分布</span>
            </div>
          </div>
          <v-chart :option="structOpt" autoresize class="role-reference-mini-chart" />
        </div>
        <div class="role-reference-chart-block">
          <div class="role-reference-panel-head">
            <div>
              <strong>资金风险概览</strong>
              <span>超比例与质保风险</span>
            </div>
          </div>
          <v-chart :option="riskOpt" autoresize class="role-reference-mini-chart" />
        </div>
      </div>
      <div class="role-reference-summary-grid role-reference-summary-grid--2">
        <div class="role-reference-summary-item">
          <span>质保金到期</span>
          <b>{{ fmtWan(data.warrantyExpiringAmount) }} 万元</b>
        </div>
        <div class="role-reference-summary-item">
          <span>风险金额</span>
          <b>{{ fmtWan(data.overRatioAmount) }} 万元</b>
        </div>
        <div class="role-reference-summary-item">
          <span>待付款记录</span>
          <b>{{ data.pendingPayments.length }} 笔</b>
        </div>
        <div class="role-reference-summary-item">
          <span>超比例记录</span>
          <b>{{ data.overRatioPayments.length }} 笔</b>
        </div>
      </div>
    </section>
  </div>
</template>
