<script setup lang="ts">
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  PayCircleOutlined,
  ReloadOutlined,
  SearchOutlined,
  WalletOutlined,
} from '@ant-design/icons-vue'

type FilterState = {
  projectId?: string
  contractId?: string
  payType?: string
  payStatus?: string
  approvalStatus?: string
}

type ProjectOption = {
  id: string
  projectName?: string
}

type ContractOption = {
  id: string
  contractName?: string
}

type BreakdownItem = {
  key: string
  label: string
  count: number
  percent: number
  color: string
}

type PendingPaymentItem = {
  id: string
  project: string
  title: string
  amount: string
}

const props = defineProps<{
  filter: FilterState
  projects?: ProjectOption[]
  contracts?: ContractOption[]
  payTypeLabel: Record<string, string>
  payStatusLabelMap: Record<string, string>
  payStatusLabel: (status: string | undefined) => string
  fmtAmountText: (value: number) => string
  onProjectChange: (value: string | undefined) => void
  onSearch: () => void
  onReset: () => void
  onRefresh: () => void
  total: number
  kpiTotalApply: number
  kpiActualPaid: number
  kpiUnpaid: number
  kpiApprovedUnpaid: number
  paidPct: number
  kpiUnpaidPct: number
  statusBreakdown: BreakdownItem[]
  approvalBreakdown: BreakdownItem[]
  pendingPayments: PendingPaymentItem[]
}>()

function handleProjectChange(value: string | undefined) {
  props.onProjectChange(value)
}
</script>

<template>
  <div class="lg-page-head payment-page-head">
    <div class="payment-page-meta-row">
      <div>
        <a-breadcrumb class="payment-breadcrumb">
          <a-breadcrumb-item>付款管理</a-breadcrumb-item>
          <a-breadcrumb-item>付款申请</a-breadcrumb-item>
        </a-breadcrumb>
        <div class="payment-page-title-row">
          <h1>付款申请台账</h1>
          <span>待付、已审未付、实际支付回写集中跟踪</span>
        </div>
      </div>
      <div class="payment-head-digest">
        <div>
          <span>待付金额</span>
          <strong>{{ props.fmtAmountText(props.kpiUnpaid) }}万</strong>
        </div>
        <div>
          <span>已审未付</span>
          <strong>{{ props.fmtAmountText(props.kpiApprovedUnpaid) }}万</strong>
        </div>
        <div>
          <span>支付完成率</span>
          <strong>{{ props.paidPct }}%</strong>
        </div>
      </div>
    </div>
  </div>

  <div class="lg-search-bar payment-search-bar">
    <div class="payment-search-title">
      <strong>查询条件</strong>
      <span>项目 / 合同 / 类型 / 状态</span>
    </div>
    <div class="payment-search-fields">
      <a-select
        v-model:value="props.filter.projectId"
        class="payment-search-select"
        placeholder="全部项目"
        allow-clear
        size="large"
        @change="handleProjectChange"
      >
        <template #suffixIcon><SearchOutlined /></template>
        <a-select-option v-for="p in props.projects" :key="p.id" :value="p.id">
          {{ p.projectName }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="props.filter.contractId"
        class="payment-search-select"
        placeholder="全部合同"
        allow-clear
        size="large"
        @change="props.onSearch"
      >
        <a-select-option v-for="c in props.contracts" :key="c.id" :value="c.id">
          {{ c.contractName }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="props.filter.payType"
        class="payment-search-select is-compact"
        placeholder="类型"
        allow-clear
        size="large"
        @change="props.onSearch"
      >
        <a-select-option v-for="(label, key) in props.payTypeLabel" :key="key" :value="key">
          {{ label }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="props.filter.payStatus"
        class="payment-search-select is-compact"
        placeholder="状态"
        allow-clear
        size="large"
        @change="props.onSearch"
      >
        <a-select-option v-for="(_, key) in props.payStatusLabelMap" :key="key" :value="key">
          {{ props.payStatusLabel(String(key)) }}
        </a-select-option>
      </a-select>
    </div>
    <div class="payment-search-actions">
      <a-button type="primary" size="large" @click="props.onSearch">搜索</a-button>
      <a-button size="large" @click="props.onReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
    </div>
  </div>

  <div class="lg-grid payment-workspace">
    <div class="lg-left">
      <div class="lg-kpi-strip payment-kpi-summary" aria-label="付款关键指标">
        <div class="lg-kpi-card payment-kpi-item">
          <span class="payment-kpi-icon is-total"><PayCircleOutlined /></span>
          <span class="payment-kpi-label">申请总数</span>
          <span class="payment-kpi-value">{{ props.total }} <small>单</small></span>
        </div>
        <div class="lg-kpi-card payment-kpi-item is-wide">
          <span class="payment-kpi-icon is-amount"><DollarOutlined /></span>
          <span class="payment-kpi-label">申请金额</span>
          <span class="payment-kpi-value"
            >{{ props.fmtAmountText(props.kpiTotalApply) }} <small>万元</small></span
          >
        </div>
        <div class="lg-kpi-card payment-kpi-item is-progress">
          <span class="payment-kpi-icon is-paid"><CheckCircleOutlined /></span>
          <span class="payment-kpi-label">已付金额</span>
          <span class="payment-kpi-value"
            >{{ props.fmtAmountText(props.kpiActualPaid) }} <small>万元</small></span
          >
          <span class="payment-kpi-progress"><span :style="{ width: props.paidPct + '%' }"></span></span>
        </div>
        <div class="lg-kpi-card payment-kpi-item is-progress is-unpaid">
          <span class="payment-kpi-icon is-unpaid"><WalletOutlined /></span>
          <span class="payment-kpi-label">待付款金额</span>
          <span class="payment-kpi-value"
            >{{ props.fmtAmountText(props.kpiUnpaid) }} <small>万元</small></span
          >
          <span class="payment-kpi-progress">
            <span :style="{ width: props.kpiUnpaidPct + '%' }"></span>
          </span>
        </div>
        <div class="lg-kpi-card payment-kpi-item">
          <span class="payment-kpi-icon is-pending"><ClockCircleOutlined /></span>
          <span class="payment-kpi-label">已批未付</span>
          <span class="payment-kpi-value"
            >{{ props.fmtAmountText(props.kpiApprovedUnpaid) }} <small>万元</small></span
          >
        </div>
      </div>

      <slot />
    </div>

    <aside class="lg-analysis-rail payment-analysis-rail" aria-label="付款辅助分析">
      <div class="payment-analysis-panel">
        <header class="payment-analysis-head">
          <div>
            <div class="payment-analysis-title">辅助分析</div>
            <div class="payment-analysis-subtitle">支付状态、审批状态与待付款</div>
          </div>
          <a-button type="link" size="small" @click="props.onRefresh">刷新</a-button>
        </header>

        <section class="payment-analysis-focus">
          <span>本页重点</span>
          <strong>{{ props.fmtAmountText(props.kpiApprovedUnpaid) }} 万</strong>
          <em>已审批但尚未完成支付，优先核对付款回写。</em>
        </section>

        <section class="payment-analysis-section">
          <div class="payment-section-title">付款状态统计</div>
          <div v-for="it in props.statusBreakdown" :key="it.label" class="lg-type-row">
            <span class="lg-type-dot" :style="{ background: it.color }"></span>
            <span class="lg-type-label">{{ it.label }}</span>
            <span class="lg-type-bar-wrap">
              <span class="lg-type-bar" :style="{ width: it.percent + '%', background: it.color }"></span>
            </span>
            <span class="lg-type-num">{{ it.count }}</span>
            <span class="lg-type-pct">{{ it.percent }}%</span>
          </div>
          <div v-if="!props.statusBreakdown.length" class="payment-analysis-empty">暂无付款状态数据</div>
        </section>

        <section class="payment-analysis-section">
          <div class="payment-section-title">审批状态</div>
          <div v-for="it in props.approvalBreakdown" :key="it.key" class="lg-type-row">
            <span class="lg-type-dot" :style="{ background: it.color }"></span>
            <span class="lg-type-label">{{ it.label }}</span>
            <span class="lg-type-bar-wrap">
              <span class="lg-type-bar" :style="{ width: it.percent + '%', background: it.color }"></span>
            </span>
            <span class="lg-type-num">{{ it.count }}</span>
            <span class="lg-type-pct">{{ it.percent }}%</span>
          </div>
        </section>

        <section class="payment-analysis-section">
          <div class="payment-warning-head">
            <div class="payment-section-title">待付款提醒</div>
            <span class="payment-warning-count">{{ props.pendingPayments.length }} 项</span>
          </div>
          <div v-for="item in props.pendingPayments" :key="item.id" class="lg-warning-item">
            <span class="lg-warning-project">{{ item.project }}</span>
            <span class="lg-warning-title">{{ item.title }}</span>
            <span class="payment-warning-amount">{{ item.amount }}万</span>
          </div>
          <div v-if="!props.pendingPayments.length" class="lg-warning-empty">暂无待付款提醒</div>
        </section>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.payment-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid var(--border-subtle);
  border-left: 4px solid var(--primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.payment-page-meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  width: 100%;
  min-width: 0;
}

.payment-breadcrumb {
  margin-bottom: 6px;
  font-size: 13px;
  line-height: 20px;
}

.payment-page-title-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
}

.payment-page-title-row h1 {
  margin: 0;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 32px;
}

.payment-page-title-row span,
.payment-head-digest span,
.payment-search-title span {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.payment-head-digest {
  display: grid;
  grid-template-columns: repeat(3, minmax(96px, 1fr));
  gap: 10px;
  min-width: 360px;
}

.payment-head-digest > div {
  padding: 10px 12px;
  background: var(--surface-subtle);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.payment-head-digest strong {
  display: block;
  margin-top: 3px;
  color: var(--text);
  font-size: 17px;
  font-weight: 800;
  line-height: 22px;
}

.payment-search-bar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  justify-content: space-between;
  gap: 12px;
  min-height: 0;
  padding: 16px;
  border-left: 4px solid var(--primary-soft);
}

.payment-search-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  grid-column: 1 / -1;
}

.payment-search-title strong {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
}

.payment-search-fields {
  display: flex;
  flex: 1 1 auto;
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.payment-search-select {
  width: 230px;
  flex: 0 0 230px;
}

.payment-search-select.is-compact {
  width: 150px;
  flex-basis: 150px;
}

.payment-search-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.payment-workspace {
  align-items: stretch;
  min-height: 0;
}

.payment-kpi-summary {
  display: grid;
  grid-template-columns: 1fr 1.25fr 1.15fr 1.15fr 1fr;
  gap: 0;
  overflow: hidden;
  min-height: 84px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.payment-kpi-item {
  position: relative;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  grid-template-rows: 19px 27px 8px;
  column-gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 16px 18px;
  border-right: 1px solid var(--border-subtle);
}

.payment-kpi-item:last-child {
  border-right: 0;
}

.payment-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: var(--radius-sm);
  grid-row: 1 / span 2;
}

.payment-kpi-icon.is-amount {
  color: var(--warning);
  background: var(--warning-soft);
}

.payment-kpi-icon.is-paid {
  color: var(--success);
  background: var(--success-soft);
}

.payment-kpi-icon.is-unpaid,
.payment-kpi-icon.is-pending {
  color: var(--error);
  background: var(--error-soft);
}

.payment-kpi-label {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.payment-kpi-value {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.payment-kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.payment-kpi-progress {
  display: block;
  overflow: hidden;
  height: 4px;
  background: var(--surface-subtle);
  border-radius: var(--radius-sm);
  grid-column: 2;
}

.payment-kpi-progress > span {
  display: block;
  height: 100%;
  background: var(--kpi-paid);
  border-radius: var(--radius-sm);
}

.payment-kpi-item.is-unpaid .payment-kpi-progress > span {
  background: var(--kpi-unpaid);
}

.payment-analysis-rail {
  width: 336px;
}

.payment-analysis-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 100%;
  padding: 18px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.payment-analysis-focus {
  display: grid;
  gap: 4px;
  padding: 14px;
  background: var(--error-soft);
  border: 1px solid rgba(239, 68, 68, 0.18);
  border-radius: var(--radius-md);
}

.payment-analysis-focus span,
.payment-analysis-focus em {
  color: var(--text-secondary);
  font-size: 12px;
  font-style: normal;
}

.payment-analysis-focus strong {
  color: var(--error);
  font-size: 24px;
  font-weight: 800;
  line-height: 30px;
}

.payment-analysis-head,
.payment-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.payment-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}

.payment-analysis-subtitle,
.payment-warning-count {
  color: var(--text-secondary);
  font-size: 12px;
}

.payment-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}

.payment-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.payment-analysis-empty {
  padding: 10px 0;
  color: var(--text-secondary);
  font-size: 13px;
  text-align: center;
}

.payment-analysis-section :deep(.lg-type-row),
.lg-type-row {
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 20px 38px;
}

.payment-warning-amount {
  color: var(--error);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

@media (max-width: 1200px) {
  .payment-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .payment-kpi-item {
    border-bottom: 1px solid var(--border-subtle);
  }

  .payment-analysis-rail {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .payment-page-meta-row,
  .payment-search-bar,
  .payment-search-fields {
    align-items: stretch;
    flex-direction: column;
  }

  .payment-head-digest {
    width: 100%;
    min-width: 0;
    grid-template-columns: 1fr;
  }

  .payment-search-select,
  .payment-search-select.is-compact {
    width: 100%;
    flex-basis: auto;
  }
}
</style>
