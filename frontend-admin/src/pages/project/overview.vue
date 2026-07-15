<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import VChart from 'vue-echarts'
import {
  ArrowLeftOutlined,
  FileTextOutlined,
  MoreOutlined,
  RightOutlined,
  TeamOutlined,
} from '@ant-design/icons-vue'
import { getProjectDetail, getProjectOverview } from '@/api/modules/project'
import { useMobileViewport } from '@/composables/useMobileViewport'
import type { ProjectOverviewVO, ProjectVO } from '@/types/project'

/* ── Route param ── */
const route = useRoute()
const router = useRouter()
const projectId = route.params.projectId as string
const { isMobile } = useMobileViewport()

/* ── Data ── */
const data = ref<ProjectOverviewVO | null>(null)
const project = ref<ProjectVO | null>(null)
const loading = ref(false)

async function fetchOverview() {
  if (!projectId) return
  loading.value = true
  try {
    const [projectDetail, overview] = await Promise.all([
      getProjectDetail(projectId),
      getProjectOverview(projectId),
    ])
    project.value = projectDetail
    data.value = overview
  } catch (e: unknown) {
    console.error(e)
    message.error('加载项目总览数据失败')
    data.value = null
    project.value = null
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

const members = computed(() => (Array.isArray(data.value?.members) ? data.value.members : []))
const projectManagerName = computed(
  () => members.value.find((member) => member.roleCode === 'PM')?.userName || '待维护',
)

const projectStatusLabel: Record<string, string> = {
  DRAFT: '前期',
  ACTIVE: '在建',
  ONGOING: '在建',
  COMPLETED: '已竣工',
  SUSPENDED: '已暂停',
  CLOSED: '已关闭',
}
const projectStatusColor: Record<string, string> = {
  DRAFT: 'processing',
  ACTIVE: 'success',
  ONGOING: 'success',
  COMPLETED: 'green',
  SUSPENDED: 'warning',
  CLOSED: 'default',
}
const projectTypeLabel: Record<string, string> = {
  CONSTRUCTION: '施工总承包',
  BUILDING: '施工总承包',
  MAIN: '施工总承包',
  MUNICIPAL: '市政工程',
  DECORATION: '装饰装修',
  INFRASTRUCTURE: '基础设施',
  SUB: '专业分包',
  PROFESSIONAL_SUB: '专业分包',
  PROFESSIONAL_SUBCONTRACT: '专业分包',
  LABOR: '劳务分包',
  LABOR_SUB: '劳务分包',
  LABOR_SUBCONTRACT: '劳务分包',
  PURCHASE: '材料采购',
  MATERIAL: '材料采购',
  MATERIAL_PURCHASE: '材料采购',
  OTHER: '其他',
}

function formatPlan(projectData: ProjectVO): string {
  if (!projectData.plannedStartDate && !projectData.plannedEndDate) return '待维护'
  return `${projectData.plannedStartDate || '-'} 至 ${projectData.plannedEndDate || '-'}`
}

function goBack() {
  router.back()
}

function goToEdit() {
  router.push(`/project/${projectId}/edit`)
}

function goToMembers() {
  router.push(`/project/${projectId}/members`)
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
  <div class="overview lg-page app-page project-target-redesign">
    <div class="pt-page-head">
      <div>
        <a-breadcrumb class="pt-breadcrumb">
          <a-breadcrumb-item>项目管理</a-breadcrumb-item>
          <a-breadcrumb-item>项目总览</a-breadcrumb-item>
        </a-breadcrumb>
        <div class="pt-page-title">项目总览</div>
        <div class="overview-page-subtitle">汇总项目经营、成员和关键风险信息</div>
      </div>
    </div>

    <a-spin :spinning="loading" tip="加载中...">
      <template v-if="data && project">
        <div v-if="isMobile" class="project-mobile-detail">
          <div class="project-mobile-detail-head">
            <button
              type="button"
              class="project-mobile-back"
              aria-label="返回项目列表"
              @click="goBack"
            >
              <ArrowLeftOutlined aria-hidden="true" />
            </button>
            <strong>项目详情</strong>
            <a-dropdown :trigger="['click']">
              <button type="button" class="project-mobile-more" aria-label="更多项目操作">
                <MoreOutlined aria-hidden="true" />
              </button>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="goToEdit">编辑项目</a-menu-item>
                  <a-menu-item @click="goToMembers">项目成员</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>

          <section class="project-mobile-hero" aria-label="项目摘要">
            <div class="project-mobile-hero-title-row">
              <div>
                <h1>{{ project.projectName }}</h1>
                <div class="project-mobile-code">{{ project.projectCode }}</div>
              </div>
              <a-tag :color="projectStatusColor[project.status]">
                {{ projectStatusLabel[project.status] ?? project.status }}
              </a-tag>
            </div>
          </section>

          <section class="project-mobile-facts" aria-label="项目关键数据">
            <div>
              <span>合同金额</span>
              <b>{{ fmtWan(project.contractAmount) }} 万元</b>
            </div>
            <div>
              <span>项目经理</span>
              <b>{{ projectManagerName }}</b>
            </div>
            <div>
              <span>计划工期</span>
              <b>{{ formatPlan(project) }}</b>
            </div>
            <div>
              <span>项目地点</span>
              <b>{{ project.projectAddress || '待维护' }}</b>
            </div>
          </section>

          <div class="project-mobile-sections">
            <details class="project-mobile-section" open>
              <summary>基本信息 <RightOutlined aria-hidden="true" /></summary>
              <dl>
                <div>
                  <dt>项目类型</dt>
                  <dd>{{ projectTypeLabel[project.projectType] ?? project.projectType }}</dd>
                </div>
                <div>
                  <dt>建设单位</dt>
                  <dd>{{ project.ownerUnit || '待维护' }}</dd>
                </div>
                <div>
                  <dt>监理单位</dt>
                  <dd>{{ project.supervisorUnit || '待维护' }}</dd>
                </div>
                <div>
                  <dt>设计单位</dt>
                  <dd>{{ project.designUnit || '待维护' }}</dd>
                </div>
              </dl>
            </details>

            <details class="project-mobile-section">
              <summary>合同与成本 <RightOutlined aria-hidden="true" /></summary>
              <dl>
                <div>
                  <dt>合同数量</dt>
                  <dd>{{ fmtNum(data.contractCount) }} 份</dd>
                </div>
                <div>
                  <dt>合同总额</dt>
                  <dd>{{ fmtWan(data.totalContractAmount) }} 万元</dd>
                </div>
                <div>
                  <dt>动态成本</dt>
                  <dd>{{ fmtWan(data.dynamicCost) }} 万元</dd>
                </div>
                <div>
                  <dt>已付金额</dt>
                  <dd>{{ fmtWan(data.paidAmount) }} 万元</dd>
                </div>
              </dl>
            </details>

            <details class="project-mobile-section">
              <summary>
                项目成员（{{ fmtNum(data.memberCount) }}） <RightOutlined aria-hidden="true" />
              </summary>
              <div v-if="members.length" class="project-mobile-members">
                <div v-for="member in members.slice(0, 5)" :key="member.userId">
                  <span>{{ member.userName }}</span>
                  <span>{{ roleLabel(member.roleCode) }}</span>
                </div>
                <a-button type="link" size="small" @click="goToMembers">查看全部成员</a-button>
              </div>
              <div v-else class="project-mobile-empty">暂无成员数据</div>
            </details>
          </div>
        </div>

        <template v-else>
          <div class="pt-kpi-strip">
            <div class="pt-kpi">
              <div class="pt-kpi-label">合同总额</div>
              <div class="pt-kpi-value">
                {{ fmtWan(data.totalContractAmount) }} <small>万元</small>
              </div>
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
              <b>{{ projectStatusLabel[project.status] ?? project.status }}</b>
            </div>
            <div class="summary-cell">
              <span>项目经理</span>
              <b>{{ projectManagerName }}</b>
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
                  <li class="pt-compact-row">
                    <span>合同总额</span><b>{{ fmtWan(data.totalContractAmount) }} 万元</b>
                  </li>
                  <li class="pt-compact-row">
                    <span>已付金额</span><b>{{ fmtWan(data.paidAmount) }} 万元</b>
                  </li>
                  <li class="pt-compact-row">
                    <span>未付金额</span
                    ><b
                      >{{
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
                      万元</b
                    >
                  </li>
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
                  <li class="pt-compact-row">
                    <span>本月预警</span><b>{{ fmtNum(data.warningCount) }} 条</b>
                  </li>
                  <li class="pt-compact-row">
                    <span>成本偏差</span><b>{{ fmtWan(data.dynamicCost) }} 万元</b>
                  </li>
                  <li class="pt-compact-row">
                    <span>成员覆盖</span><b>{{ fmtNum(data.memberCount) }} 人</b>
                  </li>
                </ul>
              </div>
            </section>
          </div>

          <div class="overview-bottom-grid">
            <section class="pt-panel">
              <div class="pt-panel-header">合同清单</div>
              <div class="pt-panel-body">
                <div class="summary-cell compact">
                  <span>合同数量</span><b>{{ fmtNum(data.contractCount) }} 份</b>
                </div>
              </div>
            </section>
            <section class="pt-panel">
              <div class="pt-panel-header">待办事项</div>
              <div class="pt-panel-body">
                <div class="summary-cell compact">
                  <span>预警待处理</span><b>{{ fmtNum(data.warningCount) }} 条</b>
                </div>
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
                :data-source="members"
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
              <div v-if="!members.length" class="empty-hint">暂无成员数据</div>
            </section>
          </div>
        </template>
      </template>

      <!-- Empty state -->
      <div v-if="!loading && (!data || !project)" class="empty-page">
        <FileTextOutlined style="font-size: 48px; color: #d1d5db; margin-bottom: 16px" />
        <div>暂未加载项目总览数据</div>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.overview {
  min-height: 100%;
  background: var(--bg);
}
.overview-page-subtitle {
  margin-top: 4px;
  color: var(--muted);
  font-size: 13px;
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
  color: var(--muted);
  font-size: 13px;
}

.empty-page {
  padding: 80px 20px;
  text-align: center;
  color: var(--muted);
  font-size: 14px;
  background: var(--surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}

.project-mobile-detail {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.project-mobile-detail-head {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) 40px;
  align-items: center;
  min-height: 40px;
  color: var(--text);
  text-align: center;
}

.project-mobile-back,
.project-mobile-more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  color: var(--text);
  background: transparent;
  border: 0;
  border-radius: var(--radius-md);
  cursor: pointer;
}

.project-mobile-hero,
.project-mobile-facts,
.project-mobile-section {
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.project-mobile-hero {
  padding: 12px;
}

.project-mobile-hero-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.project-mobile-hero h1 {
  margin: 0 0 4px;
  color: var(--text);
  font-size: 18px;
  line-height: 24px;
}

.project-mobile-code {
  color: var(--text-secondary);
  font-size: 12px;
}

.project-mobile-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  overflow: hidden;
}

.project-mobile-facts > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 10px 12px;
  border-right: 1px solid var(--border-subtle);
  border-bottom: 1px solid var(--border-subtle);
}

.project-mobile-facts > div:nth-child(2n) {
  border-right: 0;
}

.project-mobile-facts > div:nth-last-child(-n + 2) {
  border-bottom: 0;
}

.project-mobile-facts span,
.project-mobile-section dt {
  color: var(--muted);
  font-size: 12px;
}

.project-mobile-facts b {
  overflow: hidden;
  color: var(--text);
  font-size: 13px;
  line-height: 18px;
  text-overflow: ellipsis;
}

.project-mobile-sections {
  display: grid;
  gap: 6px;
}

.project-mobile-section {
  overflow: hidden;
}

.project-mobile-section summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 0 12px;
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  list-style: none;
}

.project-mobile-section summary::-webkit-details-marker {
  display: none;
}

.project-mobile-section summary .anticon {
  color: var(--muted);
  font-size: 11px;
  transition: transform 0.16s ease;
}

.project-mobile-section[open] summary .anticon {
  transform: rotate(90deg);
}

.project-mobile-section dl {
  display: grid;
  gap: 0;
  padding: 0 12px 8px;
  margin: 0;
}

.project-mobile-section dl > div,
.project-mobile-members > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 34px;
  border-top: 1px solid var(--border-subtle);
}

.project-mobile-section dd {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--text);
  font-size: 13px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-mobile-members {
  padding: 0 12px 8px;
}

.project-mobile-members > div {
  color: var(--text-secondary);
  font-size: 13px;
}

.project-mobile-empty {
  padding: 12px;
  color: var(--muted);
  font-size: 13px;
  text-align: center;
  border-top: 1px solid var(--border-subtle);
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
@media (width < 500px) {
  .overview {
    min-height: auto;
    padding: 0;
    background: var(--surface-subtle);
  }

  .pt-page-head {
    display: none;
  }
}
</style>
