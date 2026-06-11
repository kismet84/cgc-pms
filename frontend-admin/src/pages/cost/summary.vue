<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  ReloadOutlined,
  AimOutlined,
  LockOutlined,
  LineChartOutlined,
  DollarOutlined,
} from '@ant-design/icons-vue'
import { getCostSummary, refreshCostSummary } from '@/api/modules/cost'
import { getProjectList } from '@/api/modules/project'
import type { CostSummaryVO, CostSubjectSummaryVO } from '@/types/cost'
import type { ProjectVO } from '@/types/project'

const projectList = ref<ProjectVO[]>([])
const selectedProjectId = ref<string | undefined>(undefined)
const loading = ref(false)
const summary = ref<CostSummaryVO | null>(null)

async function fetchProjects() {
  try {
    const res = await getProjectList({ pageNum: 1, pageSize: 500 })
    projectList.value = res.records
  } catch {
    projectList.value = []
  }
}

async function fetchSummary() {
  if (!selectedProjectId.value) {
    summary.value = null
    return
  }
  loading.value = true
  try {
    summary.value = await getCostSummary(selectedProjectId.value)
  } catch {
    summary.value = null
    message.error('加载动态成本汇总失败')
  } finally {
    loading.value = false
  }
}

async function handleRefresh() {
  if (!selectedProjectId.value) {
    message.warning('请先选择项目')
    return
  }
  loading.value = true
  try {
    summary.value = await refreshCostSummary(selectedProjectId.value)
    message.success('刷新成功')
  } catch {
    message.error('刷新失败')
  } finally {
    loading.value = false
  }
}

function handleProjectChange(val: string | undefined) {
  selectedProjectId.value = val
  if (val) fetchSummary()
  else summary.value = null
}

function fmtAmount(val: string | undefined): string {
  if (!val) return '0.00'
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtDeviation(val: string | undefined): string {
  if (!val) return '0.00%'
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00%'
  return (n * 100).toFixed(2) + '%'
}

function getDeviationColor(val: string | undefined): string {
  if (!val) return '#6b7280'
  const n = parseFloat(val)
  if (n > 0) return '#ef4444'
  if (n < 0) return '#22c55e'
  return '#6b7280'
}

const subjectColumns = [
  { title: '成本科目', dataIndex: 'costSubjectName', width: 180 },
  { title: '目标成本(万元)', dataIndex: 'targetCost', width: 140, align: 'right' as const, key: 'targetCost' },
  { title: '合同锁定成本(万元)', dataIndex: 'contractLockedCost', width: 160, align: 'right' as const, key: 'contractLockedCost' },
  { title: '实际成本(万元)', dataIndex: 'actualCost', width: 140, align: 'right' as const, key: 'actualCost' },
  { title: '已付款(万元)', dataIndex: 'paidAmount', width: 130, align: 'right' as const, key: 'paidAmount' },
  { title: '动态成本(万元)', dataIndex: 'dynamicCost', width: 140, align: 'right' as const, key: 'dynamicCost' },
  { title: '偏差率', dataIndex: 'costDeviation', width: 100, align: 'right' as const, key: 'costDeviation' },
]

onMounted(() => {
  fetchProjects()
})
</script>

<template>
  <div class="cl-page">
    <a-breadcrumb class="cl-breadcrumb">
      <a-breadcrumb-item>成本管理</a-breadcrumb-item>
      <a-breadcrumb-item>动态成本汇总</a-breadcrumb-item>
    </a-breadcrumb>

    <!-- Project selector bar -->
    <div class="cl-card cl-filter" style="margin-bottom:14px">
      <div class="cl-filter-row cl-filter-row--last">
        <div class="cl-field">
          <label>选择项目：</label>
          <a-select
            v-model:value="selectedProjectId"
            placeholder="请选择项目查看成本汇总"
            allow-clear
            style="width:280px"
            @change="handleProjectChange"
          >
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="cl-filter-actions">
          <a-button type="primary" @click="handleRefresh" :disabled="!selectedProjectId">
            <template #icon><ReloadOutlined /></template>刷新
          </a-button>
        </div>
      </div>
    </div>

    <!-- No project selected placeholder -->
    <div v-if="!selectedProjectId" class="cl-card" style="padding: 80px 20px; text-align: center; color: #9ca3af; font-size: 14px;">
      <AimOutlined style="font-size: 48px; margin-bottom: 16px; color: #d1d5db;" />
      <div>请选择一个项目查看动态成本汇总</div>
    </div>

    <template v-else-if="summary">
      <!-- KPI cards -->
      <div class="cl-kpis">
        <div class="cl-kpi">
          <div class="cl-kpi-icon" style="background:#3b82f6"><AimOutlined /></div>
          <div>
            <div class="cl-kpi-title">目标成本</div>
            <div class="cl-kpi-value">{{ fmtAmount(summary.targetCost) }} <small>万元</small></div>
          </div>
        </div>
        <div class="cl-kpi">
          <div class="cl-kpi-icon" style="background:#f59e0b"><LockOutlined /></div>
          <div>
            <div class="cl-kpi-title">合同锁定成本</div>
            <div class="cl-kpi-value">{{ fmtAmount(summary.contractLockedCost) }} <small>万元</small></div>
          </div>
        </div>
        <div class="cl-kpi">
          <div class="cl-kpi-icon" style="background:#22c55e"><DollarOutlined /></div>
          <div>
            <div class="cl-kpi-title">实际成本</div>
            <div class="cl-kpi-value">{{ fmtAmount(summary.actualCost) }} <small>万元</small></div>
          </div>
        </div>
        <div class="cl-kpi">
          <div class="cl-kpi-icon" style="background:#8b5cf6"><LineChartOutlined /></div>
          <div>
            <div class="cl-kpi-title">已付款</div>
            <div class="cl-kpi-value">{{ fmtAmount(summary.paidAmount) }} <small>万元</small></div>
          </div>
        </div>
        <div class="cl-kpi">
          <div class="cl-kpi-icon" style="background:#14b8c7"><LineChartOutlined /></div>
          <div>
            <div class="cl-kpi-title">动态成本</div>
            <div class="cl-kpi-value">{{ fmtAmount(summary.dynamicCost) }} <small>万元</small></div>
          </div>
        </div>
        <div class="cl-kpi">
          <div class="cl-kpi-icon" :style="{ background: getDeviationColor(summary.costDeviation) }"><LineChartOutlined /></div>
          <div>
            <div class="cl-kpi-title">成本偏差率</div>
            <div class="cl-kpi-value">{{ fmtDeviation(summary.costDeviation) }}</div>
          </div>
        </div>
      </div>

      <!-- Subject detail table -->
      <div class="cl-card cl-table-wrap">
        <div style="padding: 16px 20px; font-size: 15px; font-weight: 700; color: #111827; border-bottom: 1px solid #f0f0f0;">
          科目明细
        </div>
        <a-table
          :columns="subjectColumns"
          :data-source="summary.subjects"
          :loading="loading"
          :pagination="false"
          row-key="costSubjectId"
          size="small"
          :scroll="{ y: 480 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'targetCost'">
              <span>{{ fmtAmount(record.targetCost) }}</span>
            </template>
            <template v-else-if="column.key === 'contractLockedCost'">
              <span>{{ fmtAmount(record.contractLockedCost) }}</span>
            </template>
            <template v-else-if="column.key === 'actualCost'">
              <span>{{ fmtAmount(record.actualCost) }}</span>
            </template>
            <template v-else-if="column.key === 'paidAmount'">
              <span>{{ fmtAmount(record.paidAmount) }}</span>
            </template>
            <template v-else-if="column.key === 'dynamicCost'">
              <span>{{ fmtAmount(record.dynamicCost) }}</span>
            </template>
            <template v-else-if="column.key === 'costDeviation'">
              <span :style="{ color: getDeviationColor(record.costDeviation), fontWeight: 600 }">
                {{ fmtDeviation(record.costDeviation) }}
              </span>
            </template>
          </template>
        </a-table>
      </div>
    </template>
  </div>
</template>

<style scoped>
.cl-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}
.cl-breadcrumb {
  margin-bottom: 16px;
  font-size: 14px;
}
.cl-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}

/* KPI */
.cl-kpis {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}
.cl-kpi {
  height: 96px;
  padding: 18px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #edf1f7;
  display: flex;
  gap: 14px;
  align-items: flex-start;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}
.cl-kpi-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 15px;
  flex-shrink: 0;
}
.cl-kpi-title {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 6px;
}
.cl-kpi-value {
  font-size: 21px;
  font-weight: 800;
  color: #111827;
  letter-spacing: 0.2px;
}
.cl-kpi-value small {
  font-size: 13px;
  font-weight: 500;
  margin-left: 4px;
}

/* Filter */
.cl-filter {
  padding: 20px 22px;
}
.cl-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: center;
  margin-bottom: 14px;
}
.cl-filter-row--last {
  margin-bottom: 0;
}
.cl-field {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  white-space: nowrap;
}
.cl-field label {
  color: #374151;
  min-width: 56px;
}
.cl-filter-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
  align-items: center;
}

/* Table */
.cl-table-wrap {
  overflow: hidden;
}
</style>
