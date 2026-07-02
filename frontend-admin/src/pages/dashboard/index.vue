<script setup lang="ts">
import { computed } from 'vue'
import {
  DollarOutlined,
  FullscreenOutlined,
  InboxOutlined,
  ProjectOutlined,
  ReloadOutlined,
  ShoppingCartOutlined,
  ToolOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { useDashboardData } from './composables/useDashboardData'
import { fmtWan, fmtDeviation, devColor, devSign } from './utils/formatUtils'
import { drillCols } from './utils/tableColumns'
import type { DashboardRole } from '@/types/dashboard'
import DashboardPmView from './components/DashboardPmView.vue'
import DashboardBmView from './components/DashboardBmView.vue'
import DashboardCostView from './components/DashboardCostView.vue'
import DashboardPurchaseView from './components/DashboardPurchaseView.vue'
import DashboardProductionView from './components/DashboardProductionView.vue'
import DashboardChiefEngineerView from './components/DashboardChiefEngineerView.vue'
import DashboardFinanceView from './components/DashboardFinanceView.vue'
import DashboardMgmtView from './components/DashboardMgmtView.vue'

const {
  availableRoles,
  activeRole,
  projectList,
  selectedProjectId,
  selectedMonth,
  monthOptions,
  pmData,
  bmData,
  costData,
  purchaseData,
  productionData,
  chiefEngineerData,
  financeData,
  mgmtData,
  costBreakdown,
  loading,
  drillSubject,
  drillVisible,
  drillChildren,
  needsProject,
  handleBarClick,
  closeDrill,
  fetchViewData,
} = useDashboardData()

const roleTabOrder: DashboardRole[] = ['cost', 'pm', 'purchase', 'production', 'chiefEngineer']
const roleDisplayLabel: Record<DashboardRole, string> = {
  cost: '商务经理',
  pm: '项目经理',
  purchase: '采购经理',
  production: '生产经理',
  chiefEngineer: '总工程师',
  finance: '财务',
  mgmt: '管理层',
  bm: '商务经理',
}
const roleIcon: Record<DashboardRole, unknown> = {
  cost: DollarOutlined,
  pm: UserOutlined,
  purchase: ShoppingCartOutlined,
  production: InboxOutlined,
  chiefEngineer: ToolOutlined,
  finance: DollarOutlined,
  mgmt: ProjectOutlined,
  bm: DollarOutlined,
}

if (availableRoles.value.includes('cost')) {
  activeRole.value = 'cost'
}

const orderedRoles = computed(() =>
  roleTabOrder.filter((role) => availableRoles.value.includes(role)),
)

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
    return
  }
  document.exitFullscreen()
}
</script>

<template>
  <div class="dashboard lg-list-page lg-page app-page">
    <div class="dashboard-header">
      <div class="dashboard-title-block">
        <div class="dashboard-title-row">
          <h1>驾驶舱</h1>
        </div>
      </div>
      <div class="dashboard-actions">
        <div v-if="activeRole !== 'mgmt'" class="project-field">
          <a-select v-model:value="selectedProjectId" placeholder="请选择项目" style="width: 300px">
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </div>
        <a-select v-model:value="selectedMonth" style="width: 112px">
          <a-select-option
            v-for="monthOption in monthOptions"
            :key="monthOption.value || 'all'"
            :value="monthOption.value"
          >
            {{ monthOption.label }}
          </a-select-option>
        </a-select>
        <a-button type="text" :loading="loading" @click="fetchViewData">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button type="text" @click="toggleFullscreen">
          <template #icon><FullscreenOutlined /></template>
          全屏
        </a-button>
      </div>
    </div>

    <div class="role-tabs-card">
      <a-tabs v-model:activeKey="activeRole" class="role-tabs" size="small">
        <a-tab-pane v-for="role in orderedRoles" :key="role">
          <template #tab>
            <span class="role-tab-label">
              <component :is="roleIcon[role]" />
              {{ roleDisplayLabel[role] }}
            </span>
          </template>
        </a-tab-pane>
      </a-tabs>
    </div>

    <template v-if="activeRole === 'pm' && pmData">
      <DashboardPmView :data="pmData" :loading="loading" />
    </template>

    <template v-if="activeRole === 'bm' && bmData">
      <DashboardBmView :data="bmData" :loading="loading" />
    </template>

    <template v-if="activeRole === 'cost' && costData">
      <DashboardCostView
        :data="costData"
        :breakdown="costBreakdown"
        :loading="loading"
        @bar-click="handleBarClick"
      />
    </template>

    <template v-if="activeRole === 'purchase' && purchaseData">
      <DashboardPurchaseView :data="purchaseData" :loading="loading" />
    </template>

    <template v-if="activeRole === 'production' && productionData">
      <DashboardProductionView :data="productionData" :loading="loading" />
    </template>

    <template v-if="activeRole === 'chiefEngineer' && chiefEngineerData">
      <DashboardChiefEngineerView :data="chiefEngineerData" :loading="loading" />
    </template>

    <template v-if="activeRole === 'finance' && financeData">
      <DashboardFinanceView :data="financeData" :loading="loading" />
    </template>

    <template v-if="activeRole === 'mgmt' && mgmtData">
      <DashboardMgmtView :data="mgmtData" :loading="loading" />
    </template>

    <div v-if="!loading && needsProject(activeRole) && projectList.length === 0" class="empty-page">
      <ProjectOutlined style="font-size: 48px; color: #d1d5db; margin-bottom: 16px" />
      <div>暂无项目数据</div>
    </div>

    <a-modal
      v-model:open="drillVisible"
      :title="drillSubject ? `成本明细 - ${drillSubject.costSubjectName}` : '成本明细'"
      :footer="null"
      :width="800"
      @cancel="closeDrill"
    >
      <template v-if="drillSubject">
        <div class="drill-summary">
          <span
            >目标成本：<b>{{ fmtWan(drillSubject.targetCost) }}</b> 万元</span
          >
          <span
            >实际成本：<b>{{ fmtWan(drillSubject.actualCost) }}</b> 万元</span
          >
          <span>
            成本偏差：<b :style="{ color: devColor(drillSubject.costDeviation) }">
              {{ devSign(drillSubject.costDeviation)
              }}{{ fmtDeviation(drillSubject.costDeviation) }} 万元
            </b>
          </span>
        </div>
        <a-table
          v-if="drillChildren.length"
          :columns="drillCols"
          :data-source="drillChildren"
          :pagination="false"
          size="small"
          row-key="costSubjectId"
          style="margin-top: 16px"
        />
        <div v-else style="margin-top: 16px; color: var(--muted); text-align: center">
          该科目下暂无子科目数据（已达第2级）
        </div>
      </template>
    </a-modal>
  </div>
</template>

<style>
.dashboard {
  min-height: 100%;
  color: var(--text);
  background: #f5f7fb;
}

.dashboard-header {
  min-height: 46px;
  margin-bottom: 10px;
  padding: 0 0 2px;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.dashboard-title-block {
  min-width: 0;
}

.dashboard-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 0;
  min-width: 0;
}

.dashboard-title-row h1 {
  margin: 0;
  color: #111827;
  font-size: 24px;
  font-weight: 800;
  line-height: 34px;
  letter-spacing: 0;
}

.dashboard-title-row span:last-child {
  max-width: 460px;
  color: var(--text-secondary);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.dashboard-header.lg-page-head {
  margin-bottom: 16px;
}

.dashboard .project-field {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dashboard .project-field label {
  color: var(--text-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.dashboard .role-tabs {
  margin: 0;
}

.dashboard .role-tabs-card {
  padding: 0;
  margin-bottom: 10px;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.dashboard .role-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}

.dashboard .role-tabs :deep(.ant-tabs-nav::before) {
  border-bottom-color: #dde5f1;
}

.dashboard .role-tabs :deep(.ant-tabs-tab) {
  min-width: 118px;
  margin: 0 4px 0 0;
  padding: 9px 18px;
  background: #fff;
  border: 1px solid #dfe7f2;
  border-bottom-color: #dfe7f2;
  border-radius: 5px 5px 0 0;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.dashboard .role-tabs :deep(.ant-tabs-tab-active) {
  background: #1677ff;
  border-color: #1677ff;
  color: #fff;
}

.dashboard .role-tabs :deep(.ant-tabs-tab-active .ant-tabs-tab-btn) {
  color: #fff;
}

.dashboard .role-tabs :deep(.ant-tabs-ink-bar) {
  display: none;
}

.dashboard .role-tab-label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.dashboard .kpi-grid,
.dashboard .role-metric-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(168px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.dashboard .kpi-card {
  position: relative;
  min-height: 104px;
  padding: 18px 20px 16px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  box-shadow: none;
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr);
  column-gap: 8px;
  align-items: flex-start;
  overflow: hidden;
  transition:
    background 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.dashboard .kpi-card::before {
  display: none;
}

.dashboard .kpi-card:hover {
  background: var(--surface);
  border-color: rgba(22, 119, 255, 0.24);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.dashboard .kpi-icon {
  width: 16px;
  height: 16px;
  margin-top: 0;
  color: var(--primary);
  display: grid;
  flex-shrink: 0;
  font-size: 12px;
  line-height: 1;
  background: transparent !important;
  border-radius: 0;
  box-shadow: none;
  place-items: center;
}

.dashboard .kpi-card:nth-child(2) .kpi-icon {
  color: #d48806;
}

.dashboard .kpi-card:nth-child(3) .kpi-icon {
  color: #389e0d;
}

.dashboard .kpi-card:nth-child(4) .kpi-icon {
  color: #cf1322;
}

.dashboard .kpi-body {
  min-width: 0;
  min-height: 62px;
  display: grid;
  grid-template-rows: 16px 26px 16px;
  row-gap: 2px;
  align-content: center;
}

.dashboard .kpi-body::after {
  min-height: 16px;
  content: '';
  grid-row: 3;
}

.dashboard .kpi-title {
  margin-bottom: 0;
  color: var(--muted);
  font-size: 13px;
  font-weight: 500;
  line-height: 16px;
  grid-row: 1;
}

.dashboard .kpi-value {
  color: var(--text);
  font-size: 22px;
  font-weight: 800;
  line-height: 26px;
  font-variant-numeric: tabular-nums;
  grid-row: 2;
  word-break: break-word;
}

.dashboard .kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.dashboard .kpi-delta {
  display: inline-flex;
  align-items: center;
  min-height: 16px;
  margin-top: 0;
  padding: 0;
  background: transparent;
  border-radius: 0;
  color: var(--muted);
  font-size: 12px;
  font-weight: 500;
  grid-row: 3;
}

.dashboard .kpi-delta.danger {
  color: var(--error);
}

.dashboard .kpi-delta.success {
  color: var(--success);
}

.dashboard .panel,
.dashboard .role-panel {
  min-width: 0;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  box-shadow: none;
  overflow: hidden;
}

.dashboard .panel-header {
  min-height: 48px;
  padding: 13px 18px;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
}

.dashboard .panel-hint {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 400;
}

.dashboard .pm-reference-grid,
.dashboard .role-analysis-grid {
  display: grid;
  grid-template-columns: minmax(320px, 1.2fr) minmax(260px, 0.9fr) minmax(320px, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.dashboard .pm-bottom-grid,
.dashboard .role-table-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.dashboard .pm-chart,
.dashboard .pm-donut-chart,
.dashboard .role-chart {
  width: 100%;
  height: 240px;
}

.dashboard .role-mini-chart {
  width: 100%;
  height: 210px;
}

.dashboard .pm-table-panel,
.dashboard .role-panel {
  min-width: 0;
}

.dashboard .pm-table-panel :deep(.ant-table),
.dashboard .role-panel :deep(.ant-table) {
  font-size: 13px;
}

.dashboard .pm-table-panel :deep(.ant-table-thead > tr > th),
.dashboard .role-panel :deep(.ant-table-thead > tr > th) {
  color: var(--text-secondary);
  background: var(--surface-subtle);
  font-size: 12px;
  font-weight: 700;
}

.dashboard .pm-table-panel :deep(.ant-table-tbody > tr > td),
.dashboard .role-panel :deep(.ant-table-tbody > tr > td) {
  padding-top: 8px;
  padding-bottom: 8px;
}

.dashboard .role-summary-strip {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 10px 12px;
  padding: 18px 16px;
  color: var(--text-secondary);
  font-size: 13px;
}

.dashboard .role-summary-strip b {
  color: var(--text);
  font-variant-numeric: tabular-nums;
}

.dashboard .cost-lens-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(360px, 0.85fr);
  gap: 12px;
  margin-bottom: 12px;
}

.dashboard .cost-main-panel {
  min-height: 526px;
}

.dashboard .cost-main-panel .role-chart {
  height: 294px;
}

.dashboard .cost-subject-list {
  padding: 0 18px 18px;
}

.dashboard .cost-subject-list-head,
.dashboard .cost-subject-row {
  display: grid;
  grid-template-columns: 42px minmax(120px, 1fr) minmax(100px, 1.1fr) 90px 82px;
  align-items: center;
  gap: 12px;
}

.dashboard .cost-subject-list-head {
  padding: 0 0 8px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.dashboard .cost-subject-row {
  appearance: none;
  width: 100%;
  min-height: 38px;
  padding: 0;
  background: transparent;
  border: 0;
  border-top: 1px solid var(--border-subtle);
  color: var(--text);
  font-size: 13px;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  outline: 0;
}

.dashboard .cost-subject-row:hover {
  background: rgba(22, 119, 255, 0.04);
}

.dashboard .cost-subject-row:focus-visible {
  outline: 2px solid rgba(22, 119, 255, 0.32);
  outline-offset: -2px;
}

.dashboard .cost-subject-row strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard .cost-subject-bar {
  height: 7px;
  background: #edf1f7;
  border-radius: 999px;
  overflow: hidden;
}

.dashboard .cost-subject-bar span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #1677ff, #14b8c7);
  border-radius: inherit;
}

.dashboard .cost-subject-number {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.dashboard .decision-stack {
  display: grid;
  gap: 12px;
}

.dashboard .decision-panel {
  min-height: 0;
}

.dashboard .decision-panel :deep(.ant-table-thead > tr > th),
.dashboard .decision-panel :deep(.ant-table-tbody > tr > td) {
  padding-left: 10px;
  padding-right: 10px;
}

.dashboard .cost-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 16px 18px 18px;
}

.dashboard .cost-summary-item {
  padding: 12px;
  background: var(--surface-subtle);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
}

.dashboard .cost-summary-item span {
  display: block;
  margin-bottom: 6px;
  color: var(--text-secondary);
  font-size: 12px;
}

.dashboard .cost-summary-item b {
  color: var(--text);
  font-size: 17px;
  font-variant-numeric: tabular-nums;
}

.dashboard .cost-ledger-panel {
  margin-top: 12px;
}

.dashboard .cost-ledger-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.dashboard .cost-ledger-toolbar strong {
  color: var(--text);
}

.dashboard .cost-ledger-toolbar span {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
}

.dashboard .cost-ledger-panel :deep(.ant-table-thead > tr > th) {
  background: var(--surface-subtle);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.dashboard .cost-ledger-panel :deep(.ant-table-tbody > tr > td) {
  padding-top: 10px;
  padding-bottom: 10px;
}

.dashboard .role-reference-shell {
  display: grid;
  gap: 10px;
}

.dashboard .role-reference-kpis {
  display: grid;
  background: #fff;
  border: 1px solid #e4eaf3;
  border-radius: 6px;
  overflow: hidden;
}

.dashboard .role-reference-kpis--4 {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.dashboard .role-reference-kpi {
  position: relative;
  min-height: 104px;
  padding: 17px 18px 14px;
  border-right: 1px solid #edf1f7;
  background: linear-gradient(180deg, #fff, #fbfdff);
}

.dashboard .role-reference-kpi:last-child {
  border-right: 0;
}

.dashboard .role-reference-kpi::before {
  position: absolute;
  top: 14px;
  bottom: 14px;
  left: 0;
  width: 4px;
  background: var(--kpi-accent);
  border-radius: 0 999px 999px 0;
  content: '';
}

.dashboard .role-reference-kpi-title {
  color: #263246;
  font-size: 14px;
  font-weight: 700;
  line-height: 18px;
}

.dashboard .role-reference-kpi-value {
  margin-top: 12px;
  color: #111827;
  font-size: 22px;
  font-weight: 800;
  line-height: 28px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.dashboard .role-reference-kpi-value.is-cyan {
  color: #13a8ba;
}

.dashboard .role-reference-kpi-value.is-danger {
  color: #b42318;
}

.dashboard .role-reference-kpi-value.is-warning {
  color: #ea7500;
}

.dashboard .role-reference-kpi-value small {
  margin-left: 4px;
  color: #334155;
  font-size: 12px;
  font-weight: 600;
}

.dashboard .role-reference-kpi-meta {
  display: flex;
  gap: 18px;
  margin-top: 10px;
  color: #5d6b82;
  font-size: 12px;
  line-height: 16px;
  white-space: nowrap;
}

.dashboard .role-reference-kpi-meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.dashboard .role-reference-main-grid {
  display: grid;
  grid-template-columns: minmax(420px, 0.72fr) minmax(720px, 1.28fr);
  gap: 10px;
}

.dashboard .role-reference-side-stack {
  display: grid;
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.dashboard .role-reference-panel {
  min-width: 0;
  background: #fff;
  border: 1px solid #e4eaf3;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.03);
  overflow: hidden;
}

.dashboard .role-reference-panel-head {
  min-height: 42px;
  padding: 0 16px;
  border-bottom: 1px solid #edf1f7;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #172033;
  font-size: 14px;
}

.dashboard .role-reference-panel-head strong {
  font-weight: 800;
}

.dashboard .role-reference-panel-head span {
  color: #64748b;
  font-weight: 500;
}

.dashboard .role-reference-panel-head.mini {
  min-height: 36px;
  font-size: 13px;
}

.dashboard .role-reference-panel-head.mini b {
  color: #ef4444;
}

.dashboard .role-reference-analysis {
  display: grid;
}

.dashboard .role-reference-chart {
  width: 100%;
  height: 286px;
}

.dashboard .role-reference-mini-chart {
  width: 100%;
  height: 240px;
}

.dashboard .role-reference-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 16px 18px 18px;
}

.dashboard .role-reference-summary-grid--2 {
  padding-top: 0;
}

.dashboard .role-reference-summary-grid--hero {
  padding-bottom: 0;
}

.dashboard .role-reference-summary-item {
  padding: 12px;
  background: var(--surface-subtle);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
}

.dashboard .role-reference-summary-item span {
  display: block;
  margin-bottom: 6px;
  color: var(--text-secondary);
  font-size: 12px;
}

.dashboard .role-reference-summary-item b {
  color: var(--text);
  font-size: 17px;
  font-variant-numeric: tabular-nums;
}

.dashboard .role-reference-focus-list {
  display: grid;
  gap: 10px;
  padding: 0 18px 18px;
}

.dashboard .role-reference-focus-list > div {
  padding: 12px;
  background: #fbfdff;
  border: 1px solid #edf1f7;
  border-radius: var(--radius-sm);
}

.dashboard .role-reference-focus-list strong {
  display: block;
  margin-bottom: 4px;
  color: #172033;
  font-size: 13px;
}

.dashboard .role-reference-focus-list span {
  color: #64748b;
  font-size: 12px;
  line-height: 18px;
}

.dashboard .role-mini-panel.is-red {
  border-top: 2px solid #ef4444;
}

.dashboard .role-mini-panel.is-orange {
  border-top: 2px solid #f97316;
}

.dashboard .role-mini-panel.is-blue {
  border-top: 2px solid #2f7cf6;
}

.dashboard .role-reference-table :deep(.ant-table) {
  font-size: 12px;
}

.dashboard .role-reference-table :deep(.ant-table-thead > tr > th) {
  color: #536176;
  background: #fbfcff;
  font-size: 12px;
  font-weight: 700;
}

.dashboard .role-reference-table :deep(.ant-table-tbody > tr > td) {
  padding-top: 8px;
  padding-bottom: 8px;
  vertical-align: top;
}

.dashboard .role-reference-chart-grid {
  display: grid;
  gap: 10px;
}

.dashboard .role-reference-chart-grid--2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.dashboard .role-reference-chart-block + .role-reference-chart-block {
  border-left: 1px solid #edf1f7;
}

.dashboard .role-reference-bottom-panel {
  display: grid;
}

.dashboard .empty-page {
  padding: 80px 20px;
  text-align: center;
  color: var(--muted);
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  box-shadow: none;
}

.dashboard .drill-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 20px;
  padding: 16px;
  background: var(--surface-subtle);
  border-radius: var(--radius-md);
  font-size: 13px;
}

@media (max-width: 1100px) {
  .dashboard .pm-reference-grid,
  .dashboard .pm-bottom-grid,
  .dashboard .role-analysis-grid,
  .dashboard .role-table-grid,
  .dashboard .cost-lens-grid,
  .dashboard .role-reference-main-grid,
  .dashboard .role-reference-chart-grid--2,
  .dashboard .role-reference-kpis--4 {
    grid-template-columns: 1fr;
  }

  .dashboard .role-reference-chart-block + .role-reference-chart-block {
    border-left: 0;
    border-top: 1px solid #edf1f7;
  }

  .dashboard .role-reference-side-stack {
    grid-template-rows: none;
  }
}

@media (max-width: 640px) {
  .dashboard-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .dashboard-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .dashboard .project-field {
    width: 100%;
    align-items: flex-start;
    flex-direction: column;
  }

  .dashboard .project-field :deep(.ant-select) {
    width: 100% !important;
  }

  .dashboard .pm-chart,
  .dashboard .pm-donut-chart,
  .dashboard .role-chart,
  .dashboard .role-mini-chart {
    height: 220px;
  }
}
</style>
