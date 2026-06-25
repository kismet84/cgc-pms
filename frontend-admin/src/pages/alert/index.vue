<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { useReferenceStore } from '@/stores/reference'
import { useAlertStore } from '@/stores/alert'
import { RULE_TYPE_LABELS, SEVERITY_COLOR, type AlertLogVO } from '@/types/alert'

const store = useAlertStore()

// ── Filters ──
const filter = reactive({
  keyword: '',
  projectId: undefined as string | undefined,
  severity: undefined as string | undefined,
  isRead: undefined as number | undefined,
})

// ── Project dropdown ──
const referenceStore = useReferenceStore()
const projectOptions = computed(() => referenceStore.projects ?? [])
const projectsLoading = ref(false)

// ── Store-based pagination (frontend-side slice) ──
const pageNo = ref(1)
const pageSize = ref(20)

const pagedAlerts = computed(() => {
  const start = (pageNo.value - 1) * pageSize.value
  return store.alerts.slice(start, start + pageSize.value)
})

const total = computed(() => store.alerts.length)

// ── Fetch ──
async function fetchData() {
  try {
    await store.fetchAlerts({
      projectId: filter.projectId,
      severity: filter.severity,
      isRead: filter.isRead,
    })
  } catch (e: unknown) {
    console.error(e)
    message.error('加载预警列表失败，请稍后重试')
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  filter.keyword = ''
  filter.projectId = undefined
  filter.severity = undefined
  filter.isRead = undefined
  pageNo.value = 1
  fetchData()
}

function handlePageChange(page: number) {
  pageNo.value = page
}

function handlePageSizeChange(_cur: number, size: number) {
  pageSize.value = size
  pageNo.value = 1
}

// ── Actions ──
async function handleMarkRead(record: AlertLogVO) {
  try {
    await store.markRead(record.id)
    message.success('已标记为已读')
  } catch (e: unknown) {
    console.error(e)
    message.error('操作失败')
  }
}

async function handleBatchEvaluate() {
  try {
    const result = await store.triggerBatchEvaluate()
    message.success(`评估完成，生成 ${result.alertsGenerated} 条预警`)
    await fetchData()
  } catch (e: unknown) {
    console.error(e)
    message.error('触发评估失败')
  }
}

// ── KPI ──
const kpi = computed(() => {
  const all = store.alerts
  const highCount = all.filter((a) => a.severity === 'HIGH').length
  const unreadCount = all.filter((a) => a.isRead === 0).length
  return {
    total: all.length,
    high: highCount,
    unread: unreadCount,
  }
})

const kpiMax = computed(() => ({
  total: Math.max(kpi.value.total, 1),
  high: Math.max(kpi.value.high, 1),
  unread: Math.max(kpi.value.unread, 1),
}))
const severitySummary = computed(() => [
  {
    label: '高危',
    count: store.alerts.filter((a) => a.severity === 'HIGH').length,
    color: '#ff4d4f',
  },
  {
    label: '中危',
    count: store.alerts.filter((a) => a.severity === 'MEDIUM').length,
    color: '#faad14',
  },
  {
    label: '低危',
    count: store.alerts.filter((a) => a.severity === 'LOW').length,
    color: '#52c41a',
  },
])
const recentUnreadAlerts = computed(() => store.alerts.filter((a) => a.isRead === 0).slice(0, 4))
function kpiPct(value: number, max: number): number {
  if (max === 0) return 0
  return Math.min(Math.round((value / max) * 100), 100)
}

// ── Columns ──
const gridColumns = computed(() => [
  { field: 'message', title: '预警内容', ellipsis: true, minWidth: 260 },
  {
    field: 'projectId',
    title: '项目',
    width: 130,
    ellipsis: true,
    slots: { default: 'projectId' },
  },
  { field: 'severity', title: '严重度', width: 92, slots: { default: 'severity' } },
  { field: 'ruleType', title: '规则类型', width: 116, slots: { default: 'ruleType' } },
  { field: 'triggeredAt', title: '触发时间', width: 160 },
  { field: 'isRead', title: '状态', width: 82, slots: { default: 'isRead' } },
  { title: '操作', width: 92, slots: { default: 'action' } },
])

function getProjectName(projectId: string): string {
  const p = projectOptions.value.find((o) => String(o.id) === String(projectId))
  return p ? `${p.projectCode} ${p.projectName}` : `项目#${projectId}`
}

// ── Keyword filter ──
const filteredAlerts = computed(() => {
  const kw = filter.keyword.trim().toLowerCase()
  if (!kw) return pagedAlerts.value
  return pagedAlerts.value.filter(
    (a) =>
      a.message.toLowerCase().includes(kw) ||
      getProjectName(a.projectId).toLowerCase().includes(kw) ||
      (RULE_TYPE_LABELS[a.ruleType] || a.ruleType).toLowerCase().includes(kw),
  )
})

// ── Init ──
onMounted(async () => {
  projectsLoading.value = true
  try {
    await referenceStore.fetchProjects()
  } finally {
    projectsLoading.value = false
  }
  fetchData()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="al-breadcrumb">
          <a-breadcrumb-item>预警中心</a-breadcrumb-item>
          <a-breadcrumb-item>预警列表</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索预警内容、项目、规则类型…"
        allow-clear
        size="large"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: #697380" /></template>
      </a-input>
      <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
    </div>

    <div class="lg-grid">
      <div class="lg-left">
        <!-- KPI 横条 -->
        <div class="lg-kpi-strip">
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">预警总数</span>
            <span class="lg-kpi-card-value">{{ kpi.total }} <small>条</small></span>
            <span class="lg-kpi-card-bar"
              ><span style="width: 100%; background: var(--kpi-total)"></span
            ></span>
          </div>
          <div class="lg-kpi-card is-warn">
            <span class="lg-kpi-card-label">高危预警</span>
            <span class="lg-kpi-card-value">{{ kpi.high }} <small>条</small></span>
            <span class="lg-kpi-card-bar"
              ><span
                :style="{
                  width: kpiPct(kpi.high, kpiMax.total) + '%',
                  background: 'var(--kpi-overdue)',
                }"
              ></span
            ></span>
            <span class="lg-kpi-card-hint">占 {{ kpiPct(kpi.high, kpiMax.total) }}%</span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">未读预警</span>
            <span class="lg-kpi-card-value">{{ kpi.unread }} <small>条</small></span>
            <span class="lg-kpi-card-bar"
              ><span
                :style="{ width: kpiPct(kpi.unread, kpiMax.total) + '%', background: '#2f7df6' }"
              ></span
            ></span>
            <span class="lg-kpi-card-hint">占 {{ kpiPct(kpi.unread, kpiMax.total) }}%</span>
          </div>
        </div>

        <main class="lg-list-table-panel">
          <!-- 工具栏 -->
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <a-button
                type="primary"
                danger
                :loading="store.evaluating"
                @click="handleBatchEvaluate"
              >
                触发评估
              </a-button>
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
              </a-button>
            </div>
            <div class="lg-toolbar-right">
              <a-select
                v-model:value="filter.projectId"
                placeholder="全部项目"
                allow-clear
                style="width: 160px"
                size="small"
                :loading="projectsLoading"
                @change="handleSearch"
              >
                <a-select-option v-for="p in projectOptions" :key="p.id" :value="p.id">
                  {{ p.projectName }}
                </a-select-option>
              </a-select>
            </div>
          </div>

          <!-- 表格 -->
          <div class="lg-table-wrap">
            <vxe-grid
              :data="filteredAlerts"
              :columns="gridColumns"
              :loading="store.loading"
              :column-config="{ resizable: true }"
              stripe
              border="inner"
              size="small"
            >
              <template #projectId="{ row }">
                <span class="al-muted">{{ getProjectName(row.projectId) }}</span>
              </template>
              <template #severity="{ row }">
                <a-tag :color="SEVERITY_COLOR[row.severity] ?? 'default'">
                  {{ row.severity === 'HIGH' ? '高' : row.severity === 'MEDIUM' ? '中' : '低' }}
                </a-tag>
              </template>
              <template #ruleType="{ row }">
                <a-tag>{{ RULE_TYPE_LABELS[row.ruleType] || row.ruleType }}</a-tag>
              </template>
              <template #isRead="{ row }">
                <a-badge v-if="row.isRead === 0" status="processing" text="未读" />
                <span v-else class="al-muted">已读</span>
              </template>
              <template #action="{ row }">
                <a-button
                  v-if="row.isRead === 0"
                  type="link"
                  size="small"
                  :loading="store.markingRead.has(row.id)"
                  @click="handleMarkRead(row)"
                >
                  标为已读
                </a-button>
                <span v-else class="al-muted">—</span>
              </template>
            </vxe-grid>
          </div>

          <!-- 分页 -->
          <div class="lg-pagination">
            <span class="lg-total">共 {{ total }} 条</span>
            <a-pagination
              v-model:current="pageNo"
              v-model:page-size="pageSize"
              :total="total"
              :page-size-options="['10', '20', '50', '100']"
              show-size-changer
              show-quick-jumper
              @change="handlePageChange"
              @show-size-change="handlePageSizeChange"
            />
          </div>
        </main>
      </div>

      <aside class="lg-analysis-rail">
        <div class="lg-panel">
          <div class="lg-panel-title">预警等级分布</div>
          <div class="lg-type-list">
            <div v-for="item in severitySummary" :key="item.label" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <strong>{{ item.count }}</strong>
            </div>
          </div>
        </div>
        <div class="lg-panel">
          <div class="lg-panel-title">未读预警</div>
          <div class="lg-rail-list">
            <div v-for="item in recentUnreadAlerts" :key="item.id" class="lg-rail-item">
              <span class="lg-type-dot"></span>
              <span>{{ item.message }}</span>
            </div>
            <div v-if="!recentUnreadAlerts.length" class="lg-empty-text">暂无未读预警</div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.al-breadcrumb {
  margin-bottom: 5px;
  font-size: 13px;
}
.al-muted {
  color: var(--muted);
}
</style>
