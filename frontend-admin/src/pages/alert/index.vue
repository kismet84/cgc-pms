<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import type { Dayjs } from 'dayjs'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  AlertOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  FileTextOutlined,
  MoreOutlined,
  ReloadOutlined,
  SearchOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { useReferenceStore } from '@/stores/reference'
import { useAlertStore } from '@/stores/alert'
import { useUserStore } from '@/stores/user'
import { getAlertSubscription, updateAlertSubscription } from '@/api/modules/alert'
import {
  ALERT_CHANNEL_LABELS,
  ALERT_CATEGORY_LABELS,
  ALERT_PROCESS_STATUS_COLOR,
  ALERT_PROCESS_STATUS_LABELS,
  RULE_CATEGORY_LABELS,
  RULE_TYPE_LABELS,
  SEVERITY_COLOR,
  getAlertRuleCategory,
  type AlertSubscriptionConfig,
  type AlertSubscriptionResponse,
  type AlertLogVO,
} from '@/types/alert'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton } from '@/components/list-page'

const router = useRouter()
const store = useAlertStore()
const userStore = useUserStore()
const subscriptionVisible = ref(false)
const subscriptionLoading = ref(false)
const subscriptionSaving = ref(false)
const subscriptionState = ref<AlertSubscriptionResponse | null>(null)
const subscriptionForm = reactive<AlertSubscriptionConfig>({
  enabled: false,
  channels: [],
  domains: [],
  minSeverity: 'HIGH',
  notifyOnStatusChanged: false,
})

// ── Filters ──
const filter = reactive({
  keyword: '',
  projectId: undefined as string | undefined,
  severity: undefined as string | undefined,
  isRead: undefined as number | undefined,
  processStatus: undefined as string | undefined,
  ruleType: undefined as string | undefined,
  category: undefined as string | undefined,
  triggeredAtRange: null as [Dayjs, Dayjs] | null,
  onlyDefaultScope: false,
})

// ── Project dropdown ──
const referenceStore = useReferenceStore()
const projectOptions = computed(() => referenceStore.projects ?? [])
const projectsLoading = ref(false)

const pageNo = ref(1)
const pageSize = ref(20)
const total = computed(() => store.total)
const processStatusOptions = computed(() =>
  Object.entries(ALERT_PROCESS_STATUS_LABELS).map(([value, label]) => ({ value, label })),
)
const currentPageUnreadAlerts = computed(() => store.alerts.filter((item) => item.isRead === 0))
const roleDefaultApplied = ref(false)

type AlertRolePreset = {
  alertDomain?: string
  onlyDefaultScope?: boolean
  downgraded?: boolean
}

function resolveRoleDefaultPreset(): AlertRolePreset {
  const roleCodes = userStore.roles.map((item) => String(item).toUpperCase())
  const roleName = String(userStore.userInfo?.roleName ?? '')

  if (roleCodes.includes('SUPER_ADMIN') || roleCodes.includes('ADMIN') || roleName.includes('超级管理员')) {
    return {}
  }
  if (roleCodes.includes('PROJECT_MANAGER') || roleName.includes('项目经理')) {
    return {}
  }
  if (roleCodes.includes('COMMERCIAL_MANAGER') || roleName.includes('商务经理')) {
    return {}
  }
  if (roleCodes.includes('PURCHASE_MANAGER') || roleName.includes('采购经理')) {
    return { alertDomain: 'PURCHASE' }
  }
  if (roleCodes.includes('PRODUCTION_MANAGER') || roleName.includes('生产经理')) {
    return { downgraded: true }
  }
  if (roleCodes.includes('CHIEF_ENGINEER') || roleName.includes('总工程师')) {
    return { downgraded: true }
  }

  return {}
}

function applyRoleDefaultView(force = false) {
  if (roleDefaultApplied.value && !force) return
  const preset = resolveRoleDefaultPreset()
  filter.category = preset.alertDomain
  filter.onlyDefaultScope = preset.onlyDefaultScope ?? false
  roleDefaultApplied.value = true
}

const activeRolePreset = computed(() => resolveRoleDefaultPreset())
const hasDefaultScopeDomain = computed(() => Boolean(activeRolePreset.value.alertDomain))
const availableSubscriptionDomains = computed(() => subscriptionState.value?.availableOptions.domains ?? [])
const availableSubscriptionChannels = computed(() => subscriptionState.value?.availableOptions.channels ?? [])
const availableSeverityOptions = computed(
  () => subscriptionState.value?.availableOptions.minSeverityOptions ?? ['LOW', 'MEDIUM', 'HIGH'],
)
const defaultSubscriptionEnabled = computed(
  () => Boolean(subscriptionState.value?.defaultSubscription.enabled),
)
const defaultStatusChangeEnabled = computed(
  () => Boolean(subscriptionState.value?.defaultSubscription.notifyOnStatusChanged),
)

function resolveSearchAlertDomain() {
  const preset = activeRolePreset.value
  if (filter.onlyDefaultScope && preset.alertDomain) {
    return preset.alertDomain
  }
  return filter.category
}

// ── Fetch ──
async function fetchData() {
  try {
    await store.fetchAlerts({
      keyword: filter.keyword.trim() || undefined,
      pageNo: pageNo.value,
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      severity: filter.severity,
      isRead: filter.isRead,
      processStatus: filter.processStatus,
      ruleType: filter.ruleType,
      alertDomain: resolveSearchAlertDomain(),
      triggeredStart: filter.triggeredAtRange?.[0]?.startOf('day').format('YYYY-MM-DD HH:mm:ss'),
      triggeredEnd: filter.triggeredAtRange?.[1]?.endOf('day').format('YYYY-MM-DD HH:mm:ss'),
      onlyDefaultScope: filter.onlyDefaultScope || undefined,
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
  applyRoleDefaultView(true)
  filter.keyword = ''
  filter.projectId = undefined
  filter.severity = undefined
  filter.isRead = undefined
  filter.processStatus = undefined
  filter.ruleType = undefined
  filter.triggeredAtRange = null
  pageNo.value = 1
  fetchData()
}

function handlePageChange(page: number) {
  pageNo.value = page
  fetchData()
}

function handlePageSizeChange(_cur: number, size: number) {
  pageSize.value = size
  pageNo.value = 1
  fetchData()
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

async function handleBatchMarkRead() {
  if (!currentPageUnreadAlerts.value.length) {
    message.info('当前页没有未读预警')
    return
  }
  try {
    const count = await store.batchMarkRead(currentPageUnreadAlerts.value.map((item) => item.id))
    message.success(`已标记 ${count} 条预警为已读`)
  } catch (e: unknown) {
    console.error(e)
    message.error('批量已读失败')
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

async function handleChangeStatus(
  record: AlertLogVO,
  processStatus: 'PROCESSED' | 'ARCHIVED' | 'INVALID',
  statusRemark?: string,
) {
  try {
    await store.changeStatus(record.id, processStatus, statusRemark)
    message.success(
      processStatus === 'PROCESSED'
        ? '已标记为已处理'
        : processStatus === 'ARCHIVED'
          ? '已归档'
          : '已标记为失效',
    )
  } catch (e: unknown) {
    console.error(e)
    message.error('状态更新失败')
  }
}

// ── KPI ──
const kpi = computed(() => {
  const all = store.alerts
  const highCount = all.filter((a) => a.severity === 'HIGH').length
  const mediumCount = all.filter((a) => a.severity === 'MEDIUM').length
  const unreadCount = all.filter((a) => a.isRead === 0).length
  const readCount = all.filter((a) => a.isRead === 1).length
  return {
    total: total.value,
    high: highCount,
    medium: mediumCount,
    unread: unreadCount,
    read: readCount,
    pageCount: all.length,
  }
})

const kpiMax = computed(() => ({
  total: Math.max(kpi.value.pageCount, 1),
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
const readStatusSummary = computed(() => [
  {
    label: '未读',
    count: kpi.value.unread,
    color: '#1677ff',
  },
  {
    label: '已读',
    count: kpi.value.read,
    color: '#52c41a',
  },
])
const recentUnreadAlerts = computed(() => store.alerts.filter((a) => a.isRead === 0).slice(0, 4))
function kpiPct(value: number, max: number): number {
  if (max === 0) return 0
  return Math.min(Math.round((value / max) * 100), 100)
}

function alertPercent(value: number): number {
  if (!kpi.value.pageCount) return 0
  return Math.min(Math.round((value / kpi.value.pageCount) * 100), 100)
}

// ── Columns ──
const gridColumns = computed(() => [
  { field: 'message', title: '预警内容', ellipsis: true, minWidth: 260, slots: { default: 'message' } },
  {
    field: 'projectId',
    title: '项目',
    width: 130,
    ellipsis: true,
    slots: { default: 'projectId' },
  },
  { field: 'severity', title: '严重度', width: 92, slots: { default: 'severity' } },
  { field: 'category', title: '分类', width: 96, slots: { default: 'category' } },
  { field: 'alertCategory', title: '标签', width: 108, slots: { default: 'alertCategory' } },
  { field: 'ruleType', title: '规则类型', width: 116, slots: { default: 'ruleType' } },
  { field: 'triggeredAt', title: '触发时间', width: 120, slots: { default: 'triggeredAt' } },
  { field: 'isRead', title: '状态', width: 82, slots: { default: 'isRead' } },
  { field: 'processStatus', title: '处理口径', width: 104, slots: { default: 'processStatus' } },
  { title: '操作', width: 92, slots: { default: 'action' } },
])

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('alert_list_cols', gridColumns)

function getProjectName(projectId: string): string {
  const p = projectOptions.value.find((o) => String(o.id) === String(projectId))
  return p ? `${p.projectCode} ${p.projectName}` : `项目#${projectId}`
}

function getAlertCategoryLabel(record: AlertLogVO): string {
  const category = getAlertRuleCategory(record.ruleType, record.alertDomain, record.category)
  return RULE_CATEGORY_LABELS[category] || '未分类'
}

function getAlertTagLabel(record: AlertLogVO): string {
  return ALERT_CATEGORY_LABELS[String(record.alertCategory ?? '').trim()] || '未细分'
}

function getProcessStatusLabel(record: AlertLogVO): string {
  return ALERT_PROCESS_STATUS_LABELS[String(record.processStatus ?? 'OPEN').trim()] || '待处理'
}

function getAlertMessageText(value: unknown): string {
  const text = String(value ?? '').trim()
  return text || '-'
}

function formatTriggeredDate(value: unknown): string {
  const text = String(value ?? '').trim()
  return text ? text.slice(0, 10) : '-'
}

function buildAlertBusinessPath(record: AlertLogVO): string {
  const businessType = String(record.sourceType ?? record.businessType ?? '').trim()
  const businessId = String(record.sourceId ?? record.businessId ?? '').trim()
  if (businessType && businessId) {
    const dynamicRouteMap: Record<string, (id: string) => string> = {
      CONTRACT: (id) => `/contract/${id}`,
      CONTRACT_APPROVAL: (id) => `/contract/${id}`,
      PURCHASE_REQUEST: (id) => `/inventory/purchase-request?businessId=${id}`,
      SUB_MEASURE: (id) => `/subcontract/measure?businessId=${id}`,
      PAY_APPLICATION: (id) => `/payment/application?businessId=${id}`,
      PAY_REQUEST: (id) => `/payment/application?businessId=${id}`,
      PURCHASE_ORDER: (id) => `/purchase/order?businessId=${id}`,
      VAR_ORDER: (id) => `/variation/order?businessId=${id}`,
      VARIATION: (id) => `/variation/order?businessId=${id}`,
      PURCHASE: (id) => `/purchase/order?businessId=${id}`,
    }
    return dynamicRouteMap[businessType]?.(businessId) ?? ''
  }

  const contractId = String(record.contractId ?? '').trim()
  if (
    contractId &&
    ['CONTRACT_OVERDUE', 'CONTRACT_EXPIRING'].includes(String(record.ruleType ?? '').trim())
  ) {
    return `/contract/${contractId}`
  }

  return ''
}

function canOpenBusinessEntry(record: AlertLogVO): boolean {
  return Boolean(buildAlertBusinessPath(record))
}

function openBusinessEntry(record: AlertLogVO) {
  const path = buildAlertBusinessPath(record)
  if (path) router.push(path)
}

function applySubscriptionForm(data: AlertSubscriptionConfig) {
  subscriptionForm.enabled = Boolean(data.enabled)
  subscriptionForm.channels = [...(data.channels ?? [])]
  subscriptionForm.domains = [...(data.domains ?? [])]
  subscriptionForm.minSeverity = data.minSeverity ?? 'HIGH'
  subscriptionForm.notifyOnStatusChanged = Boolean(data.notifyOnStatusChanged)
}

async function loadSubscription() {
  subscriptionLoading.value = true
  try {
    const result = await getAlertSubscription()
    subscriptionState.value = result
    applySubscriptionForm(result.effectiveSubscription)
  } catch (error) {
    console.error(error)
    message.error('加载通知订阅失败')
  } finally {
    subscriptionLoading.value = false
  }
}

function openSubscriptionModal() {
  subscriptionVisible.value = true
  if (!subscriptionState.value) {
    loadSubscription()
    return
  }
  applySubscriptionForm(subscriptionState.value.effectiveSubscription)
}

async function handleSaveSubscription() {
  subscriptionSaving.value = true
  try {
    const result = await updateAlertSubscription({
      enabled: subscriptionForm.enabled,
      channels: subscriptionForm.channels,
      domains: subscriptionForm.domains,
      minSeverity: subscriptionForm.minSeverity,
      notifyOnStatusChanged: subscriptionForm.notifyOnStatusChanged,
    })
    subscriptionState.value = result
    applySubscriptionForm(result.effectiveSubscription)
    subscriptionVisible.value = false
    message.success('通知订阅已保存')
  } catch (error) {
    console.error(error)
    message.error('通知订阅保存失败')
  } finally {
    subscriptionSaving.value = false
  }
}

// ── Init ──
onMounted(async () => {
  applyRoleDefaultView()
  projectsLoading.value = true
  try {
    await referenceStore.fetchProjects()
  } finally {
    projectsLoading.value = false
  }
  await loadSubscription()
  fetchData()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page alert-page">
    <div class="lg-page-head alert-page-head">
      <div class="alert-page-meta-row">
        <a-breadcrumb class="al-breadcrumb">
          <a-breadcrumb-item>预警中心</a-breadcrumb-item>
          <a-breadcrumb-item>预警列表</a-breadcrumb-item>
        </a-breadcrumb>
        <span class="alert-page-subtitle">统一管理风险等级、阅读状态与规则触发记录</span>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar alert-search-bar">
      <div class="alert-search-fields">
        <a-input
          v-model:value="filter.keyword"
          class="alert-search-input"
          placeholder="搜索预警内容、项目…"
          allow-clear
          size="large"
          @press-enter="handleSearch"
        >
          <template #prefix><SearchOutlined class="alert-search-prefix-icon" /></template>
        </a-input>
        <a-select
          v-model:value="filter.projectId"
          class="alert-search-select"
          placeholder="全部项目"
          allow-clear
          size="large"
          :loading="projectsLoading"
          @change="handleSearch"
        >
          <a-select-option v-for="p in projectOptions" :key="p.id" :value="p.id">
            {{ p.projectName }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.severity"
          class="alert-search-select is-compact"
          placeholder="预警等级"
          allow-clear
          size="large"
          @change="handleSearch"
        >
          <a-select-option value="HIGH">高危</a-select-option>
          <a-select-option value="MEDIUM">中危</a-select-option>
          <a-select-option value="LOW">低危</a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.isRead"
          class="alert-search-select is-compact"
          placeholder="阅读状态"
          allow-clear
          size="large"
          @change="handleSearch"
        >
          <a-select-option :value="0">未读</a-select-option>
          <a-select-option :value="1">已读</a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.processStatus"
          class="alert-search-select is-compact"
          placeholder="处理口径"
          allow-clear
          size="large"
          @change="handleSearch"
        >
          <a-select-option v-for="item in processStatusOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </a-select-option>
        </a-select>
        <a-range-picker
          v-model:value="filter.triggeredAtRange"
          class="alert-search-range"
          show-time
          value-format="YYYY-MM-DD HH:mm:ss"
        />
        <div class="alert-default-scope-switch">
          <span>只看默认域</span>
          <a-switch
            v-model:checked="filter.onlyDefaultScope"
            :disabled="!hasDefaultScopeDomain"
            @change="handleSearch"
          />
        </div>
      </div>
      <div class="alert-search-actions">
        <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
        <a-button size="large" @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </div>
    </div>

    <div class="lg-grid alert-workspace">
      <div class="lg-left">
        <!-- KPI 横条 -->
        <div class="alert-kpi-summary" aria-label="预警关键指标">
          <div class="alert-kpi-item">
            <span class="alert-kpi-icon is-total"><FileTextOutlined /></span>
            <span class="alert-kpi-label">预警总数</span>
            <span class="alert-kpi-value">{{ kpi.total }} <small>条</small></span>
          </div>
          <div class="alert-kpi-item is-wide">
            <span class="alert-kpi-icon is-overdue"><AlertOutlined /></span>
            <span class="alert-kpi-label">高危预警</span>
            <span class="alert-kpi-value">{{ kpi.high }} <small>条</small></span>
          </div>
          <div class="alert-kpi-item is-progress">
            <span class="alert-kpi-icon is-amount"><WarningOutlined /></span>
            <span class="alert-kpi-label">中危预警</span>
            <span class="alert-kpi-value">{{ kpi.medium }} <small>条</small></span>
            <span class="alert-kpi-progress">
              <span :style="{ width: kpiPct(kpi.medium, kpiMax.total) + '%' }"></span>
            </span>
          </div>
          <div class="alert-kpi-item is-progress is-unread">
            <span class="alert-kpi-icon is-unpaid"><ClockCircleOutlined /></span>
            <span class="alert-kpi-label">未读预警</span>
            <span class="alert-kpi-value">{{ kpi.unread }} <small>条</small></span>
            <span class="alert-kpi-progress">
              <span :style="{ width: kpiPct(kpi.unread, kpiMax.total) + '%' }"></span>
            </span>
          </div>
          <div class="alert-kpi-item">
            <span class="alert-kpi-icon is-paid"><CheckCircleOutlined /></span>
            <span class="alert-kpi-label">已读预警</span>
            <span class="alert-kpi-value">{{ kpi.read }} <small>条</small></span>
          </div>
        </div>

        <main class="lg-list-table-panel alert-table-panel">
          <!-- 工具栏 -->
          <div class="lg-toolbar alert-toolbar">
            <div class="lg-toolbar-left">
              <span class="alert-table-title">预警记录</span>
              <span class="alert-table-count">共 {{ total }} 条</span>
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
              <a-button :loading="subscriptionLoading" @click="openSubscriptionModal">
                通知订阅
              </a-button>
              <a-button :disabled="!currentPageUnreadAlerts.length" @click="handleBatchMarkRead">
                当前页未读标已读
              </a-button>
              <a-button
                type="primary"
                danger
                :loading="store.evaluating"
                @click="handleBatchEvaluate"
              >
                触发评估
              </a-button>
            </div>
            <div class="lg-toolbar-right">
              <span class="alert-toolbar-hint">固定表头 / 风险分级 / 行操作可展开</span>
            </div>
          </div>

          <!-- 表格 -->
          <div class="lg-table-wrap">
            <vxe-grid
              :data="store.alerts"
              :columns="visibleGridColumns"
              :loading="store.loading"
              :column-config="{ resizable: true }"
              stripe
              border="inner"
              size="small"
            >
              <template #message="{ row }">
                <a-tooltip :title="row.message">
                  <span class="alert-message-text">{{ getAlertMessageText(row.message) }}</span>
                </a-tooltip>
              </template>
              <template #projectId="{ row }">
                <button
                  v-if="canOpenBusinessEntry(row)"
                  type="button"
                  class="alert-project-link"
                  @click="openBusinessEntry(row)"
                >
                  {{ getProjectName(row.projectId) }}
                </button>
                <span v-else class="al-muted">{{ getProjectName(row.projectId) }}</span>
              </template>
              <template #severity="{ row }">
                <a-tag :color="SEVERITY_COLOR[row.severity] ?? 'default'">
                  {{ row.severity === 'HIGH' ? '高' : row.severity === 'MEDIUM' ? '中' : '低' }}
                </a-tag>
              </template>
              <template #category="{ row }">
                <a-tag>{{ getAlertCategoryLabel(row) }}</a-tag>
              </template>
              <template #alertCategory="{ row }">
                <a-tag color="purple">{{ getAlertTagLabel(row) }}</a-tag>
              </template>
              <template #ruleType="{ row }">
                <a-tag>{{ RULE_TYPE_LABELS[row.ruleType] || row.ruleType }}</a-tag>
              </template>
              <template #triggeredAt="{ row }">
                <span>{{ formatTriggeredDate(row.triggeredAt) }}</span>
              </template>
              <template #isRead="{ row }">
                <a-badge v-if="row.isRead === 0" status="processing" text="未读" />
                <span v-else class="al-muted">已读</span>
              </template>
              <template #processStatus="{ row }">
                <a-tag :color="ALERT_PROCESS_STATUS_COLOR[String(row.processStatus ?? 'OPEN')] ?? 'default'">
                  {{ getProcessStatusLabel(row) }}
                </a-tag>
              </template>
              <template #action="{ row }">
                <a-dropdown v-if="row.isRead === 0 || canOpenBusinessEntry(row) || row.processStatus !== 'ARCHIVED'" :trigger="['click']">
                  <a-button
                    class="lg-row-action-trigger"
                    size="small"
                    type="text"
                    :loading="store.markingRead.has(String(row.id))"
                  >
                    <MoreOutlined />
                  </a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item v-if="row.isRead === 0" @click="handleMarkRead(row)">
                        标为已读
                      </a-menu-item>
                      <a-menu-item v-if="row.processStatus !== 'PROCESSED'" @click="handleChangeStatus(row, 'PROCESSED')">
                        标为已处理
                      </a-menu-item>
                      <a-menu-item v-if="row.processStatus !== 'ARCHIVED'" @click="handleChangeStatus(row, 'ARCHIVED')">
                        归档
                      </a-menu-item>
                      <a-menu-item v-if="row.processStatus !== 'INVALID'" @click="handleChangeStatus(row, 'INVALID')">
                        标为失效
                      </a-menu-item>
                      <a-menu-item v-if="canOpenBusinessEntry(row)" @click="openBusinessEntry(row)">
                        查看业务单据
                      </a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
                <span v-else class="al-muted">-</span>
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

      <aside class="lg-analysis-rail alert-analysis-rail" aria-label="预警辅助分析">
        <div class="alert-analysis-panel">
          <header class="alert-analysis-head">
            <div>
              <div class="alert-analysis-title">预警分析</div>
              <div class="alert-analysis-subtitle">等级、状态与未读风险</div>
            </div>
            <a-button type="link" size="small" @click="fetchData">刷新</a-button>
          </header>

          <section class="alert-analysis-section">
            <div class="alert-section-title">预警等级分布</div>
            <div v-for="item in severitySummary" :key="item.label" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: alertPercent(item.count) + '%', background: item.color }"
                ></span>
              </span>
              <span class="lg-type-num">{{ item.count }}</span>
              <span class="lg-type-pct">{{ alertPercent(item.count) }}%</span>
            </div>
          </section>

          <section class="alert-analysis-section">
            <div class="alert-section-title">阅读状态</div>
            <div v-for="item in readStatusSummary" :key="item.label" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: alertPercent(item.count) + '%', background: item.color }"
                ></span>
              </span>
              <span class="lg-type-num">{{ item.count }}</span>
              <span class="lg-type-pct">{{ alertPercent(item.count) }}%</span>
            </div>
          </section>

          <section class="alert-analysis-section">
            <div class="lg-warning-head">
              <div class="alert-section-title">未读预警</div>
              <span class="alert-warning-count">{{ recentUnreadAlerts.length }} 项</span>
            </div>
            <div v-for="item in recentUnreadAlerts" :key="item.id" class="lg-warning-item">
              <span class="lg-warning-project">{{ getProjectName(item.projectId) }}</span>
              <span class="lg-warning-title">{{ item.message }}</span>
            </div>
            <div v-if="!recentUnreadAlerts.length" class="lg-warning-empty">暂无未读预警</div>
          </section>
        </div>
      </aside>
    </div>

    <a-modal
      v-model:open="subscriptionVisible"
      title="通知订阅"
      :confirm-loading="subscriptionSaving"
      @ok="handleSaveSubscription"
    >
      <a-spin :spinning="subscriptionLoading">
        <div class="alert-subscription-form">
          <div class="alert-subscription-row">
            <span class="alert-subscription-label">接收通知</span>
            <a-switch
              v-model:checked="subscriptionForm.enabled"
              :disabled="!defaultSubscriptionEnabled"
            />
          </div>
          <div class="alert-subscription-row is-block">
            <span class="alert-subscription-label">通知渠道</span>
            <a-checkbox-group v-model:value="subscriptionForm.channels">
              <a-checkbox
                v-for="channel in availableSubscriptionChannels"
                :key="channel"
                :value="channel"
              >
                {{ ALERT_CHANNEL_LABELS[channel] ?? channel }}
              </a-checkbox>
            </a-checkbox-group>
          </div>
          <div class="alert-subscription-row is-block">
            <span class="alert-subscription-label">预警域</span>
            <a-checkbox-group v-model:value="subscriptionForm.domains">
              <a-checkbox
                v-for="domain in availableSubscriptionDomains"
                :key="domain"
                :value="domain"
              >
                {{ RULE_CATEGORY_LABELS[domain] ?? domain }}
              </a-checkbox>
            </a-checkbox-group>
          </div>
          <div class="alert-subscription-row is-block">
            <span class="alert-subscription-label">最低严重度</span>
            <a-radio-group v-model:value="subscriptionForm.minSeverity">
              <a-radio-button v-for="item in availableSeverityOptions" :key="item" :value="item">
                {{ item === 'HIGH' ? '高危' : item === 'MEDIUM' ? '中危' : '低危' }}
              </a-radio-button>
            </a-radio-group>
          </div>
          <div class="alert-subscription-row">
            <span class="alert-subscription-label">状态变更通知</span>
            <a-switch
              v-model:checked="subscriptionForm.notifyOnStatusChanged"
              :disabled="!defaultStatusChangeEnabled"
            />
          </div>
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<style scoped>
.alert-page {
  gap: 14px;
}

.alert-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 0;
}

.alert-page-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  min-width: 0;
}

.al-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.alert-page-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
  white-space: nowrap;
}

.al-muted {
  color: var(--muted);
}

.alert-search-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 74px;
}

.alert-search-fields {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.alert-search-input {
  width: min(520px, 31vw);
  min-width: 320px;
  flex: 1 1 auto;
}

.alert-search-prefix-icon {
  color: var(--text-secondary);
}

.alert-search-select {
  width: 180px;
  flex: 0 0 180px;
}

.alert-search-select.is-compact {
  width: 150px;
  flex-basis: 150px;
}

.alert-search-range {
  width: 320px;
  flex: 0 0 320px;
}

.alert-default-scope-switch {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
  position: relative;
  z-index: 1;
}

.alert-search-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.alert-workspace {
  align-items: stretch;
  min-height: 0;
}

.alert-kpi-summary {
  display: grid;
  grid-template-columns: 1fr 1.25fr 1.15fr 1.15fr 1fr;
  gap: 0;
  overflow: hidden;
  min-height: 84px;
  margin-bottom: 16px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.alert-kpi-item {
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

.alert-kpi-item:last-child {
  border-right: 0;
}

.alert-kpi-icon {
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

.alert-kpi-icon.is-amount {
  color: var(--warning);
  background: var(--warning-soft);
}

.alert-kpi-icon.is-paid {
  color: var(--success);
  background: var(--success-soft);
}

.alert-kpi-icon.is-unpaid {
  color: var(--primary);
  background: var(--surface-tint);
}

.alert-kpi-icon.is-overdue {
  color: var(--error);
  background: var(--error-soft);
}

.alert-kpi-label {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-kpi-value {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.alert-kpi-progress {
  display: block;
  overflow: hidden;
  height: 4px;
  background: var(--surface-subtle);
  border-radius: var(--radius-sm);
  grid-column: 2;
}

.alert-kpi-progress > span {
  display: block;
  height: 100%;
  background: var(--warning);
  border-radius: var(--radius-sm);
}

.alert-kpi-item.is-unread .alert-kpi-progress > span {
  background: var(--primary);
}

.alert-table-panel {
  overflow: hidden;
  border: 1px solid var(--border-subtle);
}

.alert-toolbar {
  border-bottom: 1px solid var(--border-subtle);
}

.alert-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}

.alert-table-count,
.alert-toolbar-hint {
  color: var(--text-secondary);
  font-size: 13px;
}

.alert-message-text {
  display: inline-block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-project-link {
  max-width: 100%;
  padding: 0;
  overflow: hidden;
  color: var(--primary);
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.alert-project-link:hover {
  text-decoration: underline;
}

.alert-analysis-rail {
  width: 336px;
}

.alert-analysis-panel {
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

.alert-analysis-head,
.alert-analysis-section .lg-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.alert-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}

.alert-analysis-subtitle,
.alert-warning-count {
  color: var(--text-secondary);
  font-size: 12px;
}

.alert-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}

.alert-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.alert-analysis-section :deep(.lg-type-row),
.alert-analysis-section .lg-type-row {
  display: grid;
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 20px 38px;
  align-items: center;
  gap: 8px;
  color: var(--text);
  line-height: 1.5;
}

.alert-analysis-section .lg-type-dot {
  margin-top: 0;
}

.alert-analysis-section .lg-type-label {
  overflow: hidden;
  color: var(--text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-subscription-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.alert-subscription-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.alert-subscription-row.is-block {
  align-items: flex-start;
  flex-direction: column;
}

.alert-subscription-label {
  color: var(--text);
  font-weight: 600;
}

@media (max-width: 1200px) {
  .alert-page-head,
  .alert-search-bar,
  .alert-search-fields {
    align-items: stretch;
    flex-direction: column;
  }

  .alert-page-meta-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .alert-search-input,
  .alert-search-select,
  .alert-search-select.is-compact,
  .alert-search-range {
    width: 100%;
    min-width: 0;
    flex: 1 1 100%;
  }

  .alert-default-scope-switch {
    justify-content: space-between;
  }

  .alert-search-actions {
    justify-content: flex-start;
  }

  .alert-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .alert-kpi-item {
    border-bottom: 1px solid var(--border-subtle);
  }

  .alert-analysis-rail {
    width: 100%;
  }

  .alert-toolbar-hint {
    display: none;
  }
}
</style>
