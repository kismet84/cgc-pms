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
  <div class="role-reference-shell">
    <section class="role-reference-kpis role-reference-kpis--4">
      <article class="role-reference-kpi" style="--kpi-accent: #2f7cf6">
        <div class="role-reference-kpi-title">合同总额</div>
        <div class="role-reference-kpi-value">
          {{ fmtWan(data.totalContractAmount) }} <small>万元</small>
        </div>
        <div class="role-reference-kpi-meta"><span><DollarOutlined /> 合同规模</span></div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #f97316">
        <div class="role-reference-kpi-title">合同变更</div>
        <div class="role-reference-kpi-value is-warning">
          {{ fmtWan(data.contractChangeAmount) }} <small>万元</small>
        </div>
        <div class="role-reference-kpi-meta"><span><SwapOutlined /> 当前变更额</span></div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #8b5cf6">
        <div class="role-reference-kpi-title">签证变更</div>
        <div class="role-reference-kpi-value">
          {{ fmtWan(data.varOrderAmount) }} <small>万元</small>
        </div>
        <div class="role-reference-kpi-meta"><span><FileTextOutlined /> 已确认签证</span></div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #16a34a">
        <div class="role-reference-kpi-title">结算进度</div>
        <div class="role-reference-kpi-value is-cyan">{{ data.settlementProgress }}</div>
        <div class="role-reference-kpi-meta"><span><FundOutlined /> 履约结算状态</span></div>
      </article>
    </section>

    <section class="role-reference-main-grid">
      <div class="role-reference-panel role-reference-analysis">
        <div class="role-reference-panel-head">
          <div>
            <strong>合同经营概览</strong>
            <span>合同、签证与结算情况</span>
          </div>
        </div>
        <v-chart :option="bizOpt" autoresize class="role-reference-chart" />
        <div class="role-reference-summary-grid">
          <div class="role-reference-summary-item">
            <span>合同总额</span>
            <b>{{ fmtWan(data.totalContractAmount) }} 万元</b>
          </div>
          <div class="role-reference-summary-item">
            <span>合同变更</span>
            <b>{{ fmtWan(data.contractChangeAmount) }} 万元</b>
          </div>
          <div class="role-reference-summary-item">
            <span>签证变更</span>
            <b>{{ fmtWan(data.varOrderAmount) }} 万元</b>
          </div>
          <div class="role-reference-summary-item">
            <span>付款比例</span>
            <b>{{ data.paidRatio }}</b>
          </div>
        </div>
      </div>

      <aside class="role-reference-side-stack">
        <div class="role-reference-panel role-mini-panel is-blue">
          <div class="role-reference-panel-head mini">
            <strong>近期合同变更 <b>（{{ data.recentChanges.length }}）</b></strong>
          </div>
          <a-table
            :columns="bmChangeCols"
            :data-source="data.recentChanges"
            :loading="loading"
            :pagination="false"
            size="small"
            row-key="contractId"
            class="role-reference-table"
          />
        </div>
        <div class="role-reference-panel role-mini-panel is-orange">
          <div class="role-reference-panel-head mini">
            <strong>待结算事项 <b>（{{ data.settlementItems.length }}）</b></strong>
          </div>
          <a-table
            :columns="bmSettleCols"
            :data-source="data.settlementItems"
            :loading="loading"
            :pagination="false"
            size="small"
            row-key="projectId"
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
              <strong>变更签证分析</strong>
              <span>合同与签证变化</span>
            </div>
          </div>
          <v-chart :option="chgOpt" autoresize class="role-reference-mini-chart" />
        </div>
        <div class="role-reference-chart-block">
          <div class="role-reference-panel-head">
            <div>
              <strong>结算收付概览</strong>
              <span>收付款与结算节奏</span>
            </div>
          </div>
          <v-chart :option="settleOpt" autoresize class="role-reference-mini-chart" />
        </div>
      </div>
      <div class="role-reference-summary-grid role-reference-summary-grid--2">
        <div class="role-reference-summary-item">
          <span>付款比例</span>
          <b>{{ data.paidRatio }}</b>
        </div>
        <div class="role-reference-summary-item">
          <span>分包计量</span>
          <b>{{ fmtWan(data.subMeasureAmount) }} 万元</b>
        </div>
        <div class="role-reference-summary-item">
          <span>结算进度</span>
          <b>{{ data.settlementProgress }}</b>
        </div>
        <div class="role-reference-summary-item">
          <span>待结算项目</span>
          <b>{{ data.settlementItems.length }} 个</b>
        </div>
      </div>
    </section>
  </div>
</template>
