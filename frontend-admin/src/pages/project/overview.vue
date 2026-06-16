<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import VChart from 'vue-echarts'
import {
  WarningOutlined,
  TeamOutlined,
  LineChartOutlined,
  PayCircleOutlined,
  FileTextOutlined,
} from '@ant-design/icons-vue'
import { getProjectOverview } from '@/api/modules/project'
import type { ProjectOverviewVO } from '@/types/project'

/* ── Route param ── */
const route = useRoute()
const projectId = route.params.projectId as string

/* ── Data ── */
const data = ref<ProjectOverviewVO | null>(null)
const loading = ref(false)

async function fetchOverview() {
  if (!projectId) return
  loading.value = true
  try {
    data.value = await getProjectOverview(projectId)
  } catch (e: unknown) {
    console.error(e)
    message.error('加载项目总览数据失败')
    data.value = null
  } finally {
    loading.value = false
  }
}

/* ── Formatters ── */
function fmtWan(val: string | undefined): string {
  if (!val) return '0.00'
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtNum(val: string | undefined): string {
  if (!val) return '0'
  const n = parseInt(val, 10)
  return isNaN(n) ? '0' : n.toLocaleString('zh-CN')
}

/* ── Role label map ── */
const roleLabels: Record<string, string> = {
  PM: '项目经理',
  CM: '成本经理',
  CSTM: '成本经理',
  FIN: '财务',
  SUBC: '分包经理',
  MAT: '物资经理',
  BM: '商务经理',
  ADMIN: '管理员',
}

function roleLabel(code: string): string {
  return roleLabels[code] ?? code
}

/* ── Members table columns ── */
const memberCols = [
  { title: '姓名', dataIndex: 'userName', width: 120 },
  { title: '用户ID', dataIndex: 'userId', width: 180 },
  { title: '角色', dataIndex: 'roleCode', width: 120 },
]

/* ── ECharts pie: cost breakdown ── */
const pieOption = computed(() => {
  const totalContract = parseFloat(data.value?.totalContractAmount ?? '0') || 0
  const dynamicCost = parseFloat(data.value?.dynamicCost ?? '0') || 0
  const paidAmount = parseFloat(data.value?.paidAmount ?? '0') || 0
  const remaining = Math.max(0, totalContract - paidAmount)

  const pieData = [
    { name: '已付金额', value: paidAmount },
    { name: '未付金额', value: remaining },
    { name: '动态成本', value: dynamicCost },
  ].filter((d) => d.value > 0)

  return {
    tooltip: {
      trigger: 'item' as const,
      valueFormatter: (v: number) => (v / 10000).toFixed(2) + ' 万元',
    },
    legend: {
      orient: 'vertical' as const,
      right: 10,
      top: 'center',
    },
    series: [
      {
        name: '成本构成',
        type: 'pie',
        radius: ['45%', '72%'],
        center: ['40%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 6,
          borderColor: '#fff',
          borderWidth: 2,
        },
        label: {
          show: false,
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 14,
            fontWeight: 'bold',
          },
        },
        data: pieData,
        color: ['#22c55e', '#8b5cf6', '#f59e0b'],
      },
    ],
  }
})

onMounted(() => {
  fetchOverview()
})
</script>

<template>
  <div class="overview">
    <a-breadcrumb class="breadcrumb">
      <a-breadcrumb-item>项目管理</a-breadcrumb-item>
      <a-breadcrumb-item>项目总览</a-breadcrumb-item>
    </a-breadcrumb>

    <a-spin :spinning="loading" tip="加载中...">
      <template v-if="data">
        <!-- ═══ KPI Cards ═══ -->
        <div class="kpi-grid kpi-grid-4">
          <div class="kpi-card">
            <div class="kpi-icon" style="background: #3b82f6">
              <FileTextOutlined />
            </div>
            <div class="kpi-body">
              <div class="kpi-title">合同总额</div>
              <div class="kpi-value">
                {{ fmtWan(data.totalContractAmount) }} <small>万元</small>
              </div>
            </div>
          </div>
          <div class="kpi-card">
            <div class="kpi-icon" style="background: #f59e0b">
              <LineChartOutlined />
            </div>
            <div class="kpi-body">
              <div class="kpi-title">动态成本</div>
              <div class="kpi-value">{{ fmtWan(data.dynamicCost) }} <small>万元</small></div>
            </div>
          </div>
          <div class="kpi-card">
            <div class="kpi-icon" style="background: #22c55e">
              <PayCircleOutlined />
            </div>
            <div class="kpi-body">
              <div class="kpi-title">已付金额</div>
              <div class="kpi-value">{{ fmtWan(data.paidAmount) }} <small>万元</small></div>
            </div>
          </div>
          <div class="kpi-card">
            <div class="kpi-icon" style="background: #ef4444">
              <WarningOutlined />
            </div>
            <div class="kpi-body">
              <div class="kpi-title">预警数量</div>
              <div class="kpi-value">{{ fmtNum(data.warningCount) }} <small>条</small></div>
            </div>
          </div>
        </div>

        <!-- ═══ Chart + Members ═══ -->
        <div class="chart-row">
          <div class="chart-col">
            <div class="panel">
              <div class="panel-header">成本构成分布</div>
              <v-chart :option="pieOption" autoresize style="height: 360px" />
            </div>
          </div>
          <div class="chart-col">
            <div class="panel">
              <div class="panel-header">
                <TeamOutlined />
                项目成员
                <span class="panel-hint">共 {{ fmtNum(data.memberCount) }} 人</span>
              </div>
              <a-table
                :columns="memberCols"
                :data-source="data.members"
                :pagination="false"
                size="small"
                row-key="userId"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.dataIndex === 'roleCode'">
                    {{ roleLabel(record.roleCode) }}
                  </template>
                </template>
              </a-table>
              <div v-if="!data.members.length" class="empty-hint">暂无成员数据</div>
            </div>
          </div>
        </div>

        <!-- ═══ Summary row ═══ -->
        <div class="summary-row">
          <div class="panel">
            <div class="panel-header">指标概览</div>
            <div class="summary-grid">
              <div class="summary-item">
                <span class="summary-label">合同数量</span>
                <span class="summary-value">{{ fmtNum(data.contractCount) }} 份</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">成员数量</span>
                <span class="summary-value">{{ fmtNum(data.memberCount) }} 人</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">本月预警</span>
                <span class="summary-value warning">{{ fmtNum(data.warningCount) }} 条</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">未付金额</span>
                <span class="summary-value">
                  {{
                    fmtWan(
                      String(
                        Math.max(
                          0,
                          (parseFloat(data.totalContractAmount) || 0) -
                            (parseFloat(data.paidAmount) || 0),
                        ),
                      ),
                    )
                  }}
                  万元
                </span>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- Empty state -->
      <div v-if="!loading && !data" class="empty-page">
        <FileTextOutlined style="font-size: 48px; color: #d1d5db; margin-bottom: 16px" />
        <div>暂未加载项目总览数据</div>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.overview {
  min-height: 100%;
}

.breadcrumb {
  margin-bottom: 14px;
}

/* ── KPI Grid ── */
.kpi-grid {
  display: grid;
  gap: 10px;
  margin-bottom: 14px;
}
.kpi-grid-4 {
  grid-template-columns: repeat(4, 1fr);
}

.kpi-card {
  height: 96px;
  padding: 16px 18px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #edf1f7;
  display: flex;
  gap: 14px;
  align-items: flex-start;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
  overflow: hidden;
}

.kpi-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 15px;
  flex-shrink: 0;
}

.kpi-body {
  flex: 1;
  min-width: 0;
}

.kpi-title {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 6px;
}

.kpi-value {
  font-size: 21px;
  font-weight: 800;
  color: #111827;
  letter-spacing: 0.2px;
}

.kpi-value small {
  font-size: 13px;
  font-weight: 500;
  margin-left: 4px;
  color: #6b7280;
}

/* ── Panel ── */
.panel {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #edf1f7;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
  overflow: hidden;
}

.panel-header {
  padding: 14px 20px;
  font-size: 15px;
  font-weight: 700;
  color: #111827;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-hint {
  font-size: 12px;
  font-weight: 400;
  color: #9ca3af;
  margin-left: auto;
}

/* ── Chart row ── */
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}

.chart-col {
  display: flex;
  flex-direction: column;
}

/* ── Summary row ── */
.summary-row {
  margin-bottom: 14px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
  padding: 18px 20px;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 16px;
  border-right: 1px solid #f0f0f0;
}

.summary-item:last-child {
  border-right: none;
}

.summary-label {
  font-size: 13px;
  color: #6b7280;
}

.summary-value {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
}

.summary-value.warning {
  color: #ef4444;
}

/* ── Empty hints ── */
.empty-hint {
  padding: 40px 20px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}

.empty-page {
  padding: 80px 20px;
  text-align: center;
  color: #9ca3af;
  font-size: 14px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #edf1f7;
}
</style>
