<script setup lang="ts">
import { ProjectOutlined } from '@ant-design/icons-vue'
import { useDashboardData } from './composables/useDashboardData'
import { fmtWan, fmtDeviation, devColor, devSign } from './utils/formatUtils'
import { drillCols } from './utils/tableColumns'
import DashboardPmView from './components/DashboardPmView.vue'
import DashboardBmView from './components/DashboardBmView.vue'
import DashboardCostView from './components/DashboardCostView.vue'
import DashboardFinanceView from './components/DashboardFinanceView.vue'
import DashboardMgmtView from './components/DashboardMgmtView.vue'

const {
  availableRoles,
  activeRole,
  roleLabel,
  projectList,
  selectedProjectId,
  pmData,
  bmData,
  costData,
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
} = useDashboardData()
</script>

<template>
  <div class="dashboard lg-list-page lg-page app-page">
    <div class="dashboard-header lg-page-head">
      <div>
        <a-breadcrumb class="lg-page-head-breadcrumb">
          <a-breadcrumb-item>首页</a-breadcrumb-item>
          <a-breadcrumb-item>驾驶舱</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
      <div v-if="activeRole !== 'mgmt'" class="project-field">
        <label>选择项目</label>
        <a-select v-model:value="selectedProjectId" placeholder="请选择项目" style="width: 260px">
          <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
            {{ p.projectName }}
          </a-select-option>
        </a-select>
      </div>
    </div>

    <div class="lg-card role-tabs-card">
      <a-tabs v-model:activeKey="activeRole" class="role-tabs" size="small">
        <a-tab-pane v-for="role in availableRoles" :key="role" :tab="roleLabel[role]" />
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
      width="860px"
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

<style scoped>
.dashboard {
  min-height: 100%;
}

.dashboard-header {
  margin-bottom: 16px;
}

.project-field {
  display: flex;
  align-items: center;
  gap: 10px;
}

.project-field label {
  color: var(--text-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.role-tabs {
  margin: 0;
}

.role-tabs-card {
  padding: 0 20px;
  margin-bottom: 16px;
}

.role-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}

.role-tabs :deep(.ant-tabs-tab) {
  padding: 8px 14px;
  font-size: 13px;
}

.kpi-grid,
.role-metric-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(178px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.kpi-card {
  position: relative;
  min-height: 98px;
  padding: 18px 20px;
  background: var(--surface);
  border: 0;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr);
  column-gap: 6px;
  align-items: flex-start;
  overflow: hidden;
  transition:
    background 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.kpi-card::before {
  display: none;
}

.kpi-card:hover {
  background: var(--surface);
  border-color: rgba(22, 119, 255, 0.24);
  box-shadow: var(--shadow-soft);
  transform: translateY(-1px);
}

.kpi-icon {
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

.kpi-card:nth-child(2) .kpi-icon {
  color: #d48806;
}

.kpi-card:nth-child(3) .kpi-icon {
  color: #389e0d;
}

.kpi-card:nth-child(4) .kpi-icon {
  color: #cf1322;
}

.kpi-body {
  min-width: 0;
  min-height: 62px;
  display: grid;
  grid-template-rows: 16px 26px 16px;
  row-gap: 2px;
  align-content: center;
}

.kpi-body::after {
  min-height: 16px;
  content: '';
  grid-row: 3;
}

.kpi-title {
  margin-bottom: 0;
  color: var(--muted);
  font-size: 13px;
  font-weight: 500;
  line-height: 16px;
  grid-row: 1;
}

.kpi-value {
  color: var(--text);
  font-size: 22px;
  font-weight: 800;
  line-height: 26px;
  font-variant-numeric: tabular-nums;
  grid-row: 2;
  word-break: break-word;
}

.kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.kpi-delta {
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

.kpi-delta.danger {
  color: var(--error);
}

.kpi-delta.success {
  color: var(--success);
}

.panel,
.role-panel {
  min-width: 0;
  background: var(--surface);
  border: 0;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
  overflow: hidden;
}

.panel-header {
  min-height: 56px;
  padding: 16px 24px;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
}

.panel-hint {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 400;
}

.pm-reference-grid,
.role-analysis-grid {
  display: grid;
  grid-template-columns: minmax(320px, 1.2fr) minmax(260px, 0.9fr) minmax(320px, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.pm-bottom-grid,
.role-table-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.pm-chart,
.pm-donut-chart,
.role-chart {
  width: 100%;
  height: 240px;
}

.role-mini-chart {
  width: 100%;
  height: 210px;
}

.pm-table-panel,
.role-panel {
  min-width: 0;
}

.pm-table-panel :deep(.ant-table),
.role-panel :deep(.ant-table) {
  font-size: 13px;
}

.pm-table-panel :deep(.ant-table-thead > tr > th),
.role-panel :deep(.ant-table-thead > tr > th) {
  color: var(--text-secondary);
  background: var(--surface-subtle);
  font-size: 12px;
  font-weight: 700;
}

.pm-table-panel :deep(.ant-table-tbody > tr > td),
.role-panel :deep(.ant-table-tbody > tr > td) {
  padding-top: 8px;
  padding-bottom: 8px;
}

.role-summary-strip {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 10px 12px;
  padding: 18px 16px;
  color: var(--text-secondary);
  font-size: 13px;
}

.role-summary-strip b {
  color: var(--text);
  font-variant-numeric: tabular-nums;
}

.empty-page {
  padding: 80px 20px;
  text-align: center;
  color: var(--muted);
  background: var(--surface);
  border: 0;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}

.drill-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 20px;
  padding: 16px;
  background: var(--surface-subtle);
  border-radius: var(--radius-md);
  font-size: 13px;
}

@media (max-width: 1100px) {
  .pm-reference-grid,
  .pm-bottom-grid,
  .role-analysis-grid,
  .role-table-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .dashboard-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .project-field {
    width: 100%;
    align-items: flex-start;
    flex-direction: column;
  }

  .project-field :deep(.ant-select) {
    width: 100% !important;
  }

  .pm-chart,
  .pm-donut-chart,
  .role-chart,
  .role-mini-chart {
    height: 220px;
  }
}
</style>
