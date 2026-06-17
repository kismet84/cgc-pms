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
  <div class="overview app-page project-target-redesign">
    <div class="pt-page-head">
      <div>
        <a-breadcrumb class="pt-breadcrumb">
          <a-breadcrumb-item>项目管理</a-breadcrumb-item>
          <a-breadcrumb-item>项目总览</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <a-spin :spinning="loading" tip="加载中...">
      <template v-if="data">
        <div class="pt-kpi-strip">
          <div class="pt-kpi">
            <div class="pt-kpi-label">合同总额</div>
            <div class="pt-kpi-value">{{ fmtWan(data.totalContractAmount) }} <small>万元</small></div>
          </div>
          <div class="pt-kpi">
            <div class="pt-kpi-label">动态成本</div>
            <div class="pt-kpi-value">{{ fmtWan(data.dynamicCost) }} <small>万元</small></div>
          </div>
          <div class="pt-kpi">
            <div class="pt-kpi-label">已付金额</div>
            <div class="pt-kpi-value">{{ fmtWan(data.paidAmount) }} <small>万元</small></div>
          </div>
          <div class="pt-kpi">
            <div class="pt-kpi-label">预警数量</div>
            <div class="pt-kpi-value">{{ fmtNum(data.warningCount) }} <small>条</small></div>
          </div>
        </div>

        <div class="overview-summary pt-panel">
          <div class="summary-cell">
            <span>项目状态</span>
            <b>执行中</b>
          </div>
          <div class="summary-cell">
            <span>项目经理</span>
            <b>{{ data.members.find((m) => m.roleCode === 'PM')?.userName || '待维护' }}</b>
          </div>
          <div class="summary-cell">
            <span>合同数量</span>
            <b>{{ fmtNum(data.contractCount) }} 份</b>
          </div>
          <div class="summary-cell">
            <span>成员数量</span>
            <b>{{ fmtNum(data.memberCount) }} 人</b>
          </div>
        </div>

        <div class="overview-analysis-grid">
          <section class="pt-panel">
            <div class="pt-panel-header">项目经营概览</div>
            <div class="pt-panel-body">
              <ul class="pt-compact-list">
                <li class="pt-compact-row"><span>合同总额</span><b>{{ fmtWan(data.totalContractAmount) }} 万元</b></li>
                <li class="pt-compact-row"><span>已付金额</span><b>{{ fmtWan(data.paidAmount) }} 万元</b></li>
                <li class="pt-compact-row"><span>未付金额</span><b>{{
                  fmtWan(String(Math.max(0, (parseFloat(data.totalContractAmount) || 0) - (parseFloat(data.paidAmount) || 0))))
                }} 万元</b></li>
              </ul>
            </div>
          </section>
          <section class="pt-panel">
            <div class="pt-panel-header">成本执行概览</div>
            <VChart :option="pieOption" autoresize class="overview-chart" />
          </section>
          <section class="pt-panel">
            <div class="pt-panel-header">关键风险</div>
            <div class="pt-panel-body">
              <ul class="pt-compact-list">
                <li class="pt-compact-row"><span>本月预警</span><b>{{ fmtNum(data.warningCount) }} 条</b></li>
                <li class="pt-compact-row"><span>成本偏差</span><b>{{ fmtWan(data.dynamicCost) }} 万元</b></li>
                <li class="pt-compact-row"><span>成员覆盖</span><b>{{ fmtNum(data.memberCount) }} 人</b></li>
              </ul>
            </div>
          </section>
        </div>

        <div class="overview-bottom-grid">
          <section class="pt-panel">
            <div class="pt-panel-header">合同清单</div>
            <div class="pt-panel-body">
              <div class="summary-cell compact"><span>合同数量</span><b>{{ fmtNum(data.contractCount) }} 份</b></div>
            </div>
          </section>
          <section class="pt-panel">
            <div class="pt-panel-header">待办事项</div>
            <div class="pt-panel-body">
              <div class="summary-cell compact"><span>预警待处理</span><b>{{ fmtNum(data.warningCount) }} 条</b></div>
            </div>
          </section>
          <section class="pt-panel">
            <div class="pt-panel-header">
              <TeamOutlined />
              项目成员
              <span class="overview-hint">共 {{ fmtNum(data.memberCount) }} 人</span>
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
          </section>
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
  padding: 4px 0;
}
.overview-summary {
  display: flex;
  gap: 0;
  margin-bottom: 10px;
  overflow: hidden;
}
.summary-cell {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  flex: 1;
  padding: 12px 16px;
  border-right: 1px solid var(--border-subtle);
}
.summary-cell:last-child {
  border-right: none;
}
.summary-cell span {
  color: var(--muted);
  font-size: 13px;
}
.summary-cell b {
  color: var(--text);
  font-size: 18px;
  font-weight: 800;
}
.summary-cell.compact {
  padding: 0;
  border-right: none;
}
.overview-analysis-grid,
.overview-bottom-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 10px;
}
.overview-chart {
  height: 230px;
}
.overview-hint {
  margin-left: auto;
  color: var(--muted);
  font-size: 12px;
  font-weight: 400;
}
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
@media (max-width: 1100px) {
  .overview-analysis-grid,
  .overview-bottom-grid {
    grid-template-columns: 1fr;
  }
  .overview-summary {
    flex-wrap: wrap;
  }
  .summary-cell {
    min-width: 50%;
  }
}
@media (max-width: 520px) {
  .summary-cell {
    min-width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--border-subtle);
  }
}
</style>
