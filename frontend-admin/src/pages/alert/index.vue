<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { Dayjs } from 'dayjs'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  BellOutlined,
  FolderOpenOutlined,
  InboxOutlined,
  ReloadOutlined,
  SearchOutlined,
  ThunderboltOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { useReferenceStore } from '@/stores/reference'
import { useAlertStore } from '@/stores/alert'
import { useUserStore } from '@/stores/user'
import {
  getAlertSubscription,
  updateAlertSubscription,
  type AlertProcessStatus,
  type BatchAlertOperationResult,
} from '@/api/modules/alert'
import {
  ALERT_CATEGORY_LABELS,
  ALERT_CHANNEL_LABELS,
  ALERT_PROCESS_STATUS_COLOR,
  ALERT_PROCESS_STATUS_LABELS,
  RULE_CATEGORY_LABELS,
  RULE_TYPE_LABELS,
  SEVERITY_COLOR,
  getAlertRuleCategory,
  type AlertLogVO,
  type AlertSubscriptionConfig,
  type AlertSubscriptionResponse,
} from '@/types/alert'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton } from '@/components/list-page'

const router = useRouter()
const store = useAlertStore()
const userStore = useUserStore()
const referenceStore = useReferenceStore()

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

const filter = reactive({
  keyword: '',
  projectId: undefined as string | undefined,
  alertDomain: undefined as string | undefined,
  ruleType: undefined as string | undefined,
  severity: undefined as string | undefined,
  isRead: undefined as number | undefined,
  processStatus: undefined as string | undefined,
  triggeredAtRange: null as [Dayjs, Dayjs] | null,
  onlyDefaultScope: false,
})

const projectsLoading = ref(false)
const pageNo = ref(1)
const pageSize = ref(20)
const selectedRowKeys = ref<Array<string | number>>([])
const activeRecord = ref<AlertLogVO | null>(null)
const statusRemarkDraft = ref('')
const gridRef = ref()
const roleDefaultApplied = ref(false)

const projectOptions = computed(() => referenceStore.projects ?? [])
const total = computed(() => store.total)
const alerts = computed(() => store.alerts)
const processStatusOptions = computed(() =>
  Object.entries(ALERT_PROCESS_STATUS_LABELS).map(([value, label]) => ({ value, label })),
)

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
  filter.alertDomain = preset.alertDomain
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
const currentOperator = computed(
  () =>
    String(
      userStore.userInfo?.nickName ??
        userStore.userInfo?.nickname ??
        userStore.userInfo?.username ??
        '',
    ).trim() || '-',
)

function resolveSearchAlertDomain() {
  const preset = activeRolePreset.value
  if (filter.onlyDefaultScope && preset.alertDomain) {
    return preset.alertDomain
  }
  return filter.alertDomain
}

function syncActiveRecord(id: string | number, updater: (record: AlertLogVO) => void) {
  if (activeRecord.value && String(activeRecord.value.id) === String(id)) {
    updater(activeRecord.value)
  }
}

function syncBatchReadResult(successIds: Array<string | number>) {
  successIds.forEach((id) => {
    syncActiveRecord(id, (item) => {
      item.isRead = 1
    })
  })
}

function syncBatchStatusResult(
  successIds: Array<string | number>,
  processStatus: AlertProcessStatus,
  statusRemark?: string,
) {
  successIds.forEach((id) => {
    syncActiveRecord(id, (item) => {
      item.processStatus = processStatus
      item.statusRemark = statusRemark
      if (processStatus === 'PROCESSED') item.processedAt = new Date().toISOString()
      if (processStatus === 'ARCHIVED' || processStatus === 'INVALID') item.archivedAt = new Date().toISOString()
    })
  })
}

function buildBatchFailureMessage(result: BatchAlertOperationResult) {
  if (!result.failed) return ''
  return result.failures
    .slice(0, 3)
    .map((item) => `#${item.alertId} ${item.reason}`)
    .join('；')
}

function showBatchResult(actionText: string, result: BatchAlertOperationResult) {
  if (!result.failed) {
    message.success(`${actionText}成功 ${result.success} 条`)
    return
  }
  const detail = buildBatchFailureMessage(result)
  message.warning(
    `${actionText}部分完成：成功 ${result.success} 条，失败 ${result.failed} 条${detail ? `，失败原因：${detail}` : ''}`,
  )
}

function syncActiveRecordFromList() {
  const list = alerts.value
  if (!list.length) {
    activeRecord.value = null
    return
  }
  const currentId = activeRecord.value?.id
  const matched = currentId
    ? list.find((item) => String(item.id) === String(currentId))
    : null
  activeRecord.value = { ...(matched ?? list[0]) }
}

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
    syncActiveRecordFromList()
  } catch (error) {
    console.error(error)
    message.error('加载预警列表失败，请稍后重试')
  }
}

function clearSelection() {
  selectedRowKeys.value = []
}

function handleSearch() {
  pageNo.value = 1
  clearSelection()
  fetchData()
}

function handleReset() {
  applyRoleDefaultView(true)
  filter.keyword = ''
  filter.projectId = undefined
  filter.alertDomain = activeRolePreset.value.alertDomain
  filter.ruleType = undefined
  filter.severity = undefined
  filter.isRead = undefined
  filter.processStatus = undefined
  filter.triggeredAtRange = null
  pageNo.value = 1
  clearSelection()
  fetchData()
}

function handlePageChange(page: number) {
  pageNo.value = page
  clearSelection()
  fetchData()
}

function handlePageSizeChange(_cur: number, size: number) {
  pageSize.value = size
  pageNo.value = 1
  clearSelection()
  fetchData()
}

function isRowSelected(id: string | number) {
  return selectedRowKeys.value.some((item) => String(item) === String(id))
}

function toggleRowSelection(record: AlertLogVO, checked: boolean) {
  if (checked) {
    if (!isRowSelected(record.id)) {
      selectedRowKeys.value = [...selectedRowKeys.value, record.id]
    }
    return
  }
  selectedRowKeys.value = selectedRowKeys.value.filter((item) => String(item) !== String(record.id))
}

const allPageSelected = computed(
  () => alerts.value.length > 0 && alerts.value.every((item) => isRowSelected(item.id)),
)

const pageSelectionIndeterminate = computed(
  () => selectedRowKeys.value.length > 0 && !allPageSelected.value,
)

function togglePageSelection(checked: boolean) {
  selectedRowKeys.value = checked ? alerts.value.map((item) => item.id) : []
}

function getSelectedRows(): AlertLogVO[] {
  const selected = new Set(selectedRowKeys.value.map((item) => String(item)))
  return alerts.value.filter((item) => selected.has(String(item.id)))
}

function openDetail(record: AlertLogVO) {
  activeRecord.value = { ...record }
}

async function handleMarkRead(record: AlertLogVO) {
  try {
    await store.markRead(String(record.id))
    syncActiveRecord(record.id, (item) => {
      item.isRead = 1
    })
    message.success('已标记为已读')
  } catch (error) {
    console.error(error)
    message.error('操作失败')
  }
}

async function handleBatchMarkRead() {
  const unreadIds = getSelectedRows()
    .filter((item) => item.isRead === 0)
    .map((item) => item.id)
  if (!unreadIds.length) {
    message.info('所选告警均已读，或尚未勾选记录')
    return
  }
  try {
    const result = await store.batchMarkRead(unreadIds)
    syncBatchReadResult(result.successIds)
    showBatchResult('批量标记已读', result)
    clearSelection()
  } catch (error) {
    console.error(error)
    message.error('批量已读失败')
  }
}

async function handleBatchStatus(processStatus: AlertProcessStatus) {
  const rows = getSelectedRows()
  if (!rows.length) {
    message.info('请先勾选告警')
    return
  }

  Modal.confirm({
    title: '确认批量处理',
    content: `将对 ${rows.length} 条告警执行${ALERT_PROCESS_STATUS_LABELS[processStatus]}，继续吗？`,
    async onOk() {
      const result = await store.batchChangeStatus(
        rows.map((item) => item.id),
        processStatus,
      )
      syncBatchStatusResult(result.successIds, processStatus)
      showBatchResult(`批量${ALERT_PROCESS_STATUS_LABELS[processStatus]}`, result)
      clearSelection()
    },
  })
}

async function handleChangeStatus(
  record: AlertLogVO,
  processStatus: AlertProcessStatus,
  statusRemark?: string,
) {
  try {
    await store.changeStatus(record.id, processStatus, statusRemark)
    syncActiveRecord(record.id, (item) => {
      item.processStatus = processStatus
      item.statusRemark = statusRemark
      if (processStatus === 'PROCESSED') item.processedAt = new Date().toISOString()
      if (processStatus === 'ARCHIVED' || processStatus === 'INVALID') item.archivedAt = new Date().toISOString()
    })
    message.success(
      processStatus === 'PROCESSED'
        ? '已标记为已处理'
        : processStatus === 'ARCHIVED'
          ? '已归档'
          : '已标记为失效',
    )
  } catch (error) {
    console.error(error)
    message.error('状态更新失败')
  }
}

async function handleSaveActiveResult() {
  if (!activeRecord.value) return
  const nextStatus: AlertProcessStatus =
    String(activeRecord.value.processStatus ?? 'OPEN') === 'ARCHIVED' ? 'ARCHIVED' : 'PROCESSED'
  await handleChangeStatus(activeRecord.value, nextStatus, statusRemarkDraft.value.trim() || undefined)
}

function isSameLocalDay(value: unknown, target = new Date()) {
  const date = new Date(String(value ?? ''))
  if (Number.isNaN(date.getTime())) return false
  return (
    date.getFullYear() === target.getFullYear() &&
    date.getMonth() === target.getMonth() &&
    date.getDate() === target.getDate()
  )
}

const kpi = computed(() => {
  const list = alerts.value
  const open = list.filter((item) => String(item.processStatus ?? 'OPEN') === 'OPEN').length
  const high = list.filter((item) => item.severity === 'HIGH').length
  const today = list.filter((item) => isSameLocalDay(item.triggeredAt)).length
  const archived = list.filter((item) => String(item.processStatus ?? '') === 'ARCHIVED').length
  return { open, high, today, archived }
})

const kpiCards = computed(() => [
  {
    key: 'open',
    titleCn: '待处理',
    titleEn: '',
    value: kpi.value.open,
    hint: '当前待处理',
    icon: BellOutlined,
    tone: 'danger',
  },
  {
    key: 'high',
    titleCn: '高危',
    titleEn: '',
    value: kpi.value.high,
    hint: '需优先跟进',
    icon: WarningOutlined,
    tone: 'warning',
  },
  {
    key: 'today',
    titleCn: '今日触发',
    titleEn: '',
    value: kpi.value.today,
    hint: `当前页 ${alerts.value.length} 条`,
    icon: ThunderboltOutlined,
    tone: 'primary',
  },
  {
    key: 'archived',
    titleCn: '已归档',
    titleEn: '',
    value: kpi.value.archived,
    hint: `总计 ${total.value} 条`,
    icon: InboxOutlined,
    tone: 'success',
  },
])

const selectedCount = computed(() => selectedRowKeys.value.length)
const tableHeight = '100%'
const checkboxColumn = {
  field: '__selection',
  title: '',
  width: 56,
  fixed: 'left',
  align: 'center' as const,
  headerAlign: 'center' as const,
  slots: { header: 'selectionHeader', default: 'selection' },
}

const subscriptionRows = computed(() => {
  const effective = subscriptionState.value?.effectiveSubscription
  const channels = availableSubscriptionChannels.value.length
    ? availableSubscriptionChannels.value
    : ['IN_APP', 'EMAIL', 'WECHAT', 'SMS']
  return channels.map((channel) => ({
    channel,
    label: ALERT_CHANNEL_LABELS[channel] ?? channel,
    enabled: Boolean(effective?.enabled && effective.channels.includes(channel)),
    minSeverity:
      effective?.minSeverity === 'HIGH' ? 'HIGH' : effective?.minSeverity === 'MEDIUM' ? 'MEDIUM' : 'LOW',
  }))
})

const gridColumns = computed(() => [
  { field: 'id', title: '告警ID', width: 188, showOverflow: 'title', slots: { default: 'id' } },
  { field: 'projectId', title: '项目', minWidth: 220, showOverflow: 'title', slots: { default: 'projectId' } },
  { field: 'alertDomain', title: '规则域', width: 108, slots: { default: 'alertDomain' } },
  { field: 'ruleType', title: '规则类型', minWidth: 148, slots: { default: 'ruleType' } },
  { field: 'alertCategory', title: '细分类', width: 118, slots: { default: 'alertCategory' } },
  { field: 'severity', title: '严重度', width: 98, slots: { default: 'severity' } },
  { field: 'processStatus', title: '处理状态', width: 108, slots: { default: 'processStatus' } },
  { field: 'isRead', title: '已读', width: 88, slots: { default: 'isRead' } },
  { field: 'triggeredAt', title: '触发时间', width: 162, slots: { default: 'triggeredAt' } },
  { field: 'message', title: '消息摘要', minWidth: 300, slots: { default: 'message' } },
  { title: '操作', width: 220, fixed: 'right', slots: { default: 'action' } },
])

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('alert_list_cols', gridColumns)
const tableColumns = computed(() => [checkboxColumn, ...visibleGridColumns.value])

function getProjectName(projectId: string | number): string {
  const project = projectOptions.value.find((item) => String(item.id) === String(projectId))
  return project ? `${project.projectCode} ${project.projectName}` : `项目#${projectId}`
}

function getAlertDomainLabel(record: AlertLogVO): string {
  const category = getAlertRuleCategory(record.ruleType, record.alertDomain, record.category)
  return RULE_CATEGORY_LABELS[category] || category || '未分类'
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

function formatDateTime(value: unknown): string {
  const text = String(value ?? '').trim()
  return text || '-'
}

function formatSeverityText(value: unknown) {
  const text = String(value ?? '').trim()
  const severityMap: Record<string, string> = {
    HIGH: '高危',
    MEDIUM: '中危',
    LOW: '低危',
  }
  return severityMap[text] ?? (text || '-')
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
  if (contractId && ['CONTRACT_OVERDUE', 'CONTRACT_EXPIRING'].includes(String(record.ruleType ?? '').trim())) {
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

function exportCurrentView() {
  if (!alerts.value.length) {
    message.info('当前没有可导出的预警数据')
    return
  }
  const rows = alerts.value.map((item) => [
    item.id,
    getProjectName(item.projectId),
    getAlertDomainLabel(item),
    RULE_TYPE_LABELS[item.ruleType] || item.ruleType,
    getAlertTagLabel(item),
    formatSeverityText(item.severity),
    getProcessStatusLabel(item),
    item.isRead === 0 ? '未读' : '已读',
    formatDateTime(item.triggeredAt),
    getAlertMessageText(item.message).replace(/\r?\n/g, ' '),
  ])
  const header = ['告警ID', '项目', '规则域', '规则类型', '细分类', '严重度', '处理状态', '已读', '触发时间', '消息摘要']
  const csv = [header, ...rows]
    .map((line) =>
      line
        .map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`)
        .join(','),
    )
    .join('\n')
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `alerts-${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  URL.revokeObjectURL(url)
  message.success('已导出当前列表')
}

watch(activeRecord, (value) => {
  statusRemarkDraft.value = String(value?.statusRemark ?? '')
}, { immediate: true })

onMounted(async () => {
  applyRoleDefaultView()
  projectsLoading.value = true
  try {
    if (!referenceStore.projects?.length) {
      await referenceStore.fetchProjects()
    }
  } catch (error) {
    console.error(error)
  } finally {
    projectsLoading.value = false
  }
  await Promise.all([fetchData(), loadSubscription()])
})
</script>

<template>
  <div class="lg-list-page lg-page app-page alert-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="lg-page-head-breadcrumb">
          <a-breadcrumb-item>首页</a-breadcrumb-item>
          <a-breadcrumb-item>预警中心</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <div class="lg-grid alert-shell">
      <div class="lg-left alert-main">
        <section class="lg-kpi-strip alert-kpi-grid">
          <article
            v-for="card in kpiCards"
            :key="card.key"
            class="lg-kpi-card alert-kpi-card"
            :class="`is-${card.tone}`"
          >
            <div class="alert-kpi-content">
              <div class="alert-kpi-label">
                <span>{{ card.titleCn }}</span>
                <small v-if="card.titleEn">{{ card.titleEn }}</small>
              </div>
              <div class="alert-kpi-value">{{ card.value }}</div>
              <div class="alert-kpi-hint">{{ card.hint }}</div>
            </div>
            <div class="alert-kpi-icon">
              <component :is="card.icon" />
            </div>
          </article>
        </section>

        <section class="lg-search-bar alert-filter-panel">
          <div class="alert-filter-grid">
            <div class="alert-filter-item">
              <label>项目</label>
              <a-select
                v-model:value="filter.projectId"
                allow-clear
                show-search
                :loading="projectsLoading"
                :options="projectOptions.map((item) => ({ value: String(item.id), label: `${item.projectCode} ${item.projectName}` }))"
                placeholder="请选择项目"
              />
            </div>
            <div class="alert-filter-item">
              <label>严重度</label>
              <a-select
                v-model:value="filter.severity"
                allow-clear
                :options="[
                  { value: 'HIGH', label: '高危' },
                  { value: 'MEDIUM', label: '中危' },
                  { value: 'LOW', label: '低危' },
                ]"
                placeholder="请选择严重度"
              />
            </div>
            <div class="alert-filter-item">
              <label>已读状态</label>
              <a-select
                v-model:value="filter.isRead"
                allow-clear
                :options="[
                  { value: 0, label: '未读' },
                  { value: 1, label: '已读' },
                ]"
                placeholder="全部"
              />
            </div>
            <div class="alert-filter-item">
              <label>处理状态</label>
              <a-select
                v-model:value="filter.processStatus"
                allow-clear
                :options="processStatusOptions"
                placeholder="全部"
              />
            </div>
            <div class="alert-filter-item alert-filter-item-range">
              <label>触发时间</label>
              <a-range-picker v-model:value="filter.triggeredAtRange" value-format="" style="width: 100%" />
            </div>
          </div>
          <div class="alert-filter-foot">
            <div class="alert-filter-item alert-filter-item-keyword">
              <a-input
                v-model:value="filter.keyword"
                allow-clear
                placeholder="告警ID/消息摘要/业务单据号"
              >
                <template #prefix>
                  <SearchOutlined />
                </template>
              </a-input>
            </div>
            <div class="alert-filter-actions">
              <a-button type="primary" @click="handleSearch">搜索</a-button>
              <a-button @click="handleReset">
                <template #icon><ReloadOutlined /></template>
                重置
              </a-button>
            </div>
            <div v-if="hasDefaultScopeDomain" class="alert-filter-scope">
              <a-checkbox v-model:checked="filter.onlyDefaultScope">仅看默认范围</a-checkbox>
            </div>
          </div>
        </section>

        <section class="lg-list-table-panel alert-table-panel">
          <div class="lg-toolbar alert-toolbar">
            <div class="lg-toolbar-left alert-toolbar-left">
              <a-button type="primary" :disabled="selectedCount === 0" @click="handleBatchStatus('PROCESSED')">批量处理</a-button>
              <a-button :disabled="selectedCount === 0" @click="handleBatchMarkRead">标记已读</a-button>
              <a-button :disabled="selectedCount === 0" @click="handleBatchStatus('ARCHIVED')">归档</a-button>
              <a-button @click="exportCurrentView">导出</a-button>
              <span class="alert-toolbar-meta">已选择 {{ selectedCount }} 条</span>
            </div>
            <div class="lg-toolbar-right alert-toolbar-right">
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
            </div>
          </div>

          <div class="lg-table-wrap alert-grid-wrap">
            <vxe-grid
              ref="gridRef"
              :data="alerts"
              :columns="tableColumns"
              :loading="store.loading"
              :height="tableHeight"
              :column-config="{ resizable: true }"
              :row-config="{ isHover: true }"
              border="inner"
              size="mini"
              show-overflow="title"
            >
              <template #selectionHeader>
                <a-checkbox
                  :checked="allPageSelected"
                  :indeterminate="pageSelectionIndeterminate"
                  @change="(event) => togglePageSelection(event.target.checked)"
                />
              </template>
              <template #selection="{ row }">
                <a-checkbox
                  :checked="isRowSelected(row.id)"
                  @change="(event) => toggleRowSelection(row, event.target.checked)"
                />
              </template>
              <template #id="{ row }">
                <button type="button" class="alert-link" @click="openDetail(row)">{{ row.id }}</button>
              </template>
              <template #projectId="{ row }">
                <button type="button" class="alert-link alert-project-link" @click="openDetail(row)">
                  {{ getProjectName(row.projectId) }}
                </button>
              </template>
              <template #alertDomain="{ row }">
                <span class="alert-cell-text">{{ getAlertDomainLabel(row) }}</span>
              </template>
              <template #ruleType="{ row }">
                <span class="alert-cell-text">{{ RULE_TYPE_LABELS[row.ruleType] || row.ruleType }}</span>
              </template>
              <template #alertCategory="{ row }">
                <span class="alert-cell-text">{{ getAlertTagLabel(row) }}</span>
              </template>
              <template #severity="{ row }">
                <a-tag :color="SEVERITY_COLOR[row.severity] ?? 'default'" class="alert-tag">
                  {{ formatSeverityText(row.severity) }}
                </a-tag>
              </template>
              <template #processStatus="{ row }">
                <a-tag :color="ALERT_PROCESS_STATUS_COLOR[String(row.processStatus ?? 'OPEN')] ?? 'default'" class="alert-tag">
                  {{ getProcessStatusLabel(row) }}
                </a-tag>
              </template>
              <template #isRead="{ row }">
                <span class="alert-read-state" :class="{ 'is-unread': row.isRead === 0 }">
                  <i></i>
                  {{ row.isRead === 0 ? '未读' : '已读' }}
                </span>
              </template>
              <template #triggeredAt="{ row }">
                <span class="alert-cell-text">{{ formatDateTime(row.triggeredAt) }}</span>
              </template>
              <template #message="{ row }">
                <button type="button" class="alert-message-button" @click="openDetail(row)">
                  {{ getAlertMessageText(row.message) }}
                </button>
              </template>
              <template #action="{ row }">
                <div class="alert-row-actions">
                  <a-button v-if="row.isRead === 0" type="link" size="small" @click="handleMarkRead(row)">标记已读</a-button>
                  <a-button
                    v-if="String(row.processStatus ?? 'OPEN') !== 'PROCESSED'"
                    type="link"
                    size="small"
                    @click="handleChangeStatus(row, 'PROCESSED')"
                  >
                    处理
                  </a-button>
                  <a-button
                    v-if="String(row.processStatus ?? 'OPEN') !== 'ARCHIVED'"
                    type="link"
                    size="small"
                    @click="handleChangeStatus(row, 'ARCHIVED')"
                  >
                    归档
                  </a-button>
                  <a-button type="link" size="small" @click="openDetail(row)">详情</a-button>
                </div>
              </template>
            </vxe-grid>
          </div>

          <div class="lg-pagination alert-pagination">
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
        </section>
      </div>

      <aside class="lg-analysis-rail lg-panel alert-detail-panel">
        <div class="alert-detail-head">
          <div class="alert-detail-title">告警详情</div>
        </div>

        <template v-if="activeRecord">
          <section class="alert-detail-section">
            <div class="alert-section-title">基本信息</div>
            <div class="alert-detail-grid">
              <div class="alert-detail-item">
                <span>告警ID</span>
                <strong>{{ activeRecord.id }}</strong>
              </div>
              <div class="alert-detail-item">
                <span>严重度</span>
                <a-tag :color="SEVERITY_COLOR[activeRecord.severity] ?? 'default'">{{ formatSeverityText(activeRecord.severity) }}</a-tag>
              </div>
              <div class="alert-detail-item">
                <span>处理状态</span>
                <a-tag :color="ALERT_PROCESS_STATUS_COLOR[String(activeRecord.processStatus ?? 'OPEN')] ?? 'default'">
                  {{ getProcessStatusLabel(activeRecord) }}
                </a-tag>
              </div>
              <div class="alert-detail-item">
                <span>已读状态</span>
                <span class="alert-read-state" :class="{ 'is-unread': activeRecord.isRead === 0 }">
                  <i></i>
                  {{ activeRecord.isRead === 0 ? '未读' : '已读' }}
                </span>
              </div>
              <div class="alert-detail-item">
                <span>触发时间</span>
                <strong>{{ formatDateTime(activeRecord.triggeredAt) }}</strong>
              </div>
            </div>
          </section>

          <section class="alert-detail-section">
            <div class="alert-section-title">告警内容</div>
            <div class="alert-content-list">
              <div class="alert-content-row">
                <span>规则域</span>
                <strong>{{ getAlertDomainLabel(activeRecord) }}</strong>
              </div>
              <div class="alert-content-row">
                <span>项目</span>
                <strong>{{ getProjectName(activeRecord.projectId) }}</strong>
              </div>
              <div class="alert-content-row">
                <span>消息摘要</span>
                <strong class="is-message">{{ getAlertMessageText(activeRecord.message) }}</strong>
              </div>
            </div>
          </section>

          <section class="alert-detail-section">
            <div class="alert-section-title">状态备注</div>
            <a-textarea
              v-model:value="statusRemarkDraft"
              :maxlength="200"
              :auto-size="{ minRows: 4, maxRows: 6 }"
              placeholder="请填写处理备注，支持 200 字以内"
            />
            <div class="alert-detail-tip">
              当前状态：{{ getProcessStatusLabel(activeRecord) }}，保存时会同步备注。
            </div>
          </section>

          <section class="alert-detail-section">
            <div class="alert-section-title">处理信息</div>
            <div class="alert-content-list">
              <div class="alert-content-row">
                <span>处理人</span>
                <strong>{{ currentOperator }}</strong>
              </div>
              <div class="alert-content-row">
                <span>处理时间</span>
                <strong>{{ formatDateTime(activeRecord.processedAt) }}</strong>
              </div>
              <div class="alert-content-row">
                <span>归档时间</span>
                <strong>{{ formatDateTime(activeRecord.archivedAt) }}</strong>
              </div>
            </div>
          </section>

          <section class="alert-detail-section">
            <div class="alert-section-row">
              <div class="alert-section-title">通知订阅</div>
              <a-button type="link" size="small" @click="openSubscriptionModal">编辑</a-button>
            </div>
            <div class="alert-subscription-summary">{{ subscriptionSummaryText }}</div>
            <div class="alert-subscription-table">
              <div class="alert-subscription-header">
                <span>通知渠道</span>
                <span>是否启用</span>
                <span>最低严重度</span>
              </div>
              <div v-for="item in subscriptionRows" :key="item.channel" class="alert-subscription-line">
                <span>{{ item.label }}</span>
                <a-switch :checked="item.enabled" disabled size="small" />
                <span>{{ formatSeverityText(item.minSeverity) }}</span>
              </div>
            </div>
          </section>

          <div class="alert-detail-actions">
            <a-button v-if="activeRecord.isRead === 0" @click="handleMarkRead(activeRecord)">标记已读</a-button>
            <a-button v-if="String(activeRecord.processStatus ?? 'OPEN') !== 'ARCHIVED'" @click="handleChangeStatus(activeRecord, 'ARCHIVED', statusRemarkDraft.trim() || undefined)">
              归档
            </a-button>
            <a-button v-if="canOpenBusinessEntry(activeRecord)" @click="openBusinessEntry(activeRecord)">
              查看业务单据
            </a-button>
            <a-button type="primary" @click="handleSaveActiveResult">保存处理结果</a-button>
          </div>
        </template>

        <div v-else class="alert-detail-empty">
          <div class="alert-detail-empty-title">未选择预警</div>
          <div class="alert-detail-empty-text">请从左侧列表选择一条告警查看详情。</div>
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
            <a-switch v-model:checked="subscriptionForm.enabled" :disabled="!defaultSubscriptionEnabled" />
          </div>
          <div class="alert-subscription-row is-block">
            <span class="alert-subscription-label">通知渠道</span>
            <a-checkbox-group v-model:value="subscriptionForm.channels">
              <a-checkbox v-for="channel in availableSubscriptionChannels" :key="channel" :value="channel">
                {{ ALERT_CHANNEL_LABELS[channel] ?? channel }}
              </a-checkbox>
            </a-checkbox-group>
          </div>
          <div class="alert-subscription-row is-block">
            <span class="alert-subscription-label">预警域</span>
            <a-checkbox-group v-model:value="subscriptionForm.domains">
              <a-checkbox v-for="domain in availableSubscriptionDomains" :key="domain" :value="domain">
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
            <a-switch v-model:checked="subscriptionForm.notifyOnStatusChanged" :disabled="!defaultStatusChangeEnabled" />
          </div>
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<style scoped>
.alert-page {
  gap: 16px;
  min-height: 100%;
  background: #f5f7fb;
}

.alert-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: stretch;
  height: calc(100vh - 74px);
  min-height: 720px;
}

.alert-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  min-height: 0;
}

.alert-panel,
.alert-detail-panel {
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 12px;
  box-shadow: 0 4px 14px rgba(31, 35, 41, 0.04);
}

.alert-kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 12px;
  box-shadow: 0 4px 14px rgba(31, 35, 41, 0.04);
}

.alert-kpi-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 18px;
  min-height: 72px;
}

.alert-kpi-card + .alert-kpi-card {
  border-left: 1px solid #eef2f7;
}

.alert-kpi-content {
  min-width: 0;
}

.alert-kpi-label {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #5f6b7a;
  font-size: 13px;
  font-weight: 600;
}

.alert-kpi-label small {
  color: #8a94a6;
  font-size: 12px;
  font-weight: 500;
}

.alert-kpi-value {
  margin-top: 4px;
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
}

.alert-kpi-hint {
  margin-top: 6px;
  color: #8a94a6;
  font-size: 12px;
}

.alert-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  font-size: 18px;
  border-radius: 10px;
  flex: 0 0 auto;
}

.alert-kpi-card.is-danger .alert-kpi-value {
  color: #ff4d4f;
}

.alert-kpi-card.is-danger .alert-kpi-icon {
  color: #ff4d4f;
  background: #fff1f0;
}

.alert-kpi-card.is-warning .alert-kpi-value {
  color: #fa8c16;
}

.alert-kpi-card.is-warning .alert-kpi-icon {
  color: #fa8c16;
  background: #fff7e8;
}

.alert-kpi-card.is-primary .alert-kpi-value {
  color: #1677ff;
}

.alert-kpi-card.is-primary .alert-kpi-icon {
  color: #1677ff;
  background: #eaf2ff;
}

.alert-kpi-card.is-success .alert-kpi-value {
  color: #16a34a;
}

.alert-kpi-card.is-success .alert-kpi-icon {
  color: #16a34a;
  background: #edf9f0;
}

.alert-filter-panel {
  padding: 12px 18px;
}

.alert-filter-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  width: 100%;
}

.alert-filter-item {
  min-width: 0;
}

.alert-filter-item :deep(.ant-select),
.alert-filter-item :deep(.ant-picker),
.alert-filter-item :deep(.ant-input-affix-wrapper) {
  width: 100%;
}

.alert-filter-item label {
  display: block;
  margin-bottom: 4px;
  color: #3b4554;
  font-size: 12px;
  font-weight: 600;
}

.alert-filter-item-keyword {
  min-width: 0;
}

.alert-filter-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.alert-filter-foot .alert-filter-item-keyword {
  flex: 1 1 auto;
}

.alert-filter-actions {
  display: flex;
  gap: 8px;
  flex: 0 0 auto;
}

.alert-filter-scope {
  margin-left: auto;
  color: #5f6b7a;
  font-size: 13px;
}

.alert-table-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.alert-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid #eef2f7;
}

.alert-toolbar-left {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.alert-toolbar-meta {
  margin-left: 4px;
  color: #8a94a6;
  font-size: 13px;
}

.alert-grid-wrap {
  flex: 1;
  min-height: 0;
  padding: 0 14px 6px;
}

.alert-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 30px;
  padding: 0 18px;
}

.alert-link,
.alert-message-button {
  padding: 0;
  background: transparent;
  border: 0;
}

.alert-link {
  display: block;
  width: 100%;
  overflow: hidden;
  color: #1677ff;
  line-height: 20px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.alert-project-link {
  text-align: left;
}

.alert-message-button {
  display: block;
  width: 100%;
  overflow: hidden;
  color: #1f2329;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.alert-cell-text {
  display: block;
  overflow: hidden;
  color: #1f2329;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-tag {
  font-weight: 600;
}

.alert-read-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #5f6b7a;
}

.alert-read-state i {
  width: 7px;
  height: 7px;
  background: #52c41a;
  border-radius: 50%;
}

.alert-read-state.is-unread {
  color: #ff4d4f;
}

.alert-read-state.is-unread i {
  background: #ff4d4f;
}

.alert-row-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  margin-left: -8px;
}

.alert-detail-panel {
  position: sticky;
  top: 0;
  height: 100%;
  min-height: 0;
  padding: 0 0 10px;
}

.alert-detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #eef2f7;
}

.alert-detail-title {
  color: #1f2329;
  font-size: 15px;
  font-weight: 700;
}

.alert-detail-section {
  padding: 10px 16px 0;
}

.alert-section-title {
  margin-bottom: 8px;
  color: #1f2329;
  font-size: 15px;
  font-weight: 700;
}

.alert-section-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.alert-detail-grid,
.alert-content-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.alert-detail-item,
.alert-content-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  color: #5f6b7a;
  font-size: 12px;
}

.alert-detail-item strong,
.alert-content-row strong {
  color: #1f2329;
  font-weight: 600;
  text-align: right;
}

.alert-content-row .is-message {
  max-width: 220px;
  line-height: 1.35;
  white-space: pre-wrap;
}

.alert-detail-tip,
.alert-subscription-summary {
  margin-top: 6px;
  color: #8a94a6;
  font-size: 12px;
  line-height: 1.35;
}

.alert-subscription-table {
  margin-top: 8px;
  overflow: hidden;
  border: 1px solid #eef2f7;
  border-radius: 10px;
}

.alert-subscription-header,
.alert-subscription-line {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr 0.8fr;
  gap: 8px;
  align-items: center;
  padding: 6px 10px;
  font-size: 12px;
}

.alert-subscription-header {
  color: #5f6b7a;
  font-weight: 600;
  background: #fafbfd;
}

.alert-subscription-line + .alert-subscription-line {
  border-top: 1px solid #eef2f7;
}

.alert-detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px;
  margin-top: 10px;
  border-top: 1px solid #eef2f7;
}

.alert-detail-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 420px;
  color: #8a94a6;
  text-align: center;
}

.alert-detail-empty-title {
  color: #1f2329;
  font-size: 16px;
  font-weight: 700;
}

.alert-detail-empty-text {
  margin-top: 8px;
  font-size: 13px;
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
  color: #1f2329;
  font-weight: 600;
}

:deep(.alert-grid-wrap .vxe-grid) {
  height: 100%;
}

:deep(.alert-grid-wrap .vxe-table--header-wrapper),
:deep(.alert-grid-wrap .vxe-table--body-wrapper) {
  width: 100%;
}

:deep(.alert-grid-wrap .vxe-header--column),
:deep(.alert-grid-wrap .vxe-body--column) {
  padding-top: 0;
  padding-bottom: 0;
}

:deep(.alert-grid-wrap .vxe-cell) {
  padding: 0 6px;
  line-height: 16px;
  font-size: 12px;
}

:deep(.alert-grid-wrap .vxe-header--column .vxe-cell) {
  line-height: 24px;
  font-size: 12px;
}

:deep(.alert-grid-wrap .vxe-body--row) {
  height: 16px;
}

:deep(.alert-grid-wrap .ant-tag) {
  margin: 0;
  padding: 0 3px;
  line-height: 14px;
  font-size: 10px;
}

:deep(.alert-grid-wrap .ant-btn-sm) {
  height: 16px;
  padding: 0 2px;
  line-height: 14px;
  font-size: 12px;
}

:deep(.alert-grid-wrap .vxe-table--body-wrapper) {
  min-height: 0;
}

@media (max-width: 1440px) {
  .alert-shell {
    grid-template-columns: 1fr;
  }

  .alert-detail-panel {
    position: static;
    min-height: 0;
  }
}

@media (max-width: 1100px) {
  .alert-kpi-grid,
  .alert-filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .alert-filter-foot {
    flex-wrap: wrap;
  }

  .alert-filter-item-keyword {
    flex-basis: 100%;
  }

  .alert-filter-scope {
    width: 100%;
    margin-left: 0;
  }
}

@media (max-width: 768px) {
  .alert-kpi-grid,
  .alert-filter-grid {
    grid-template-columns: 1fr;
  }

  .alert-kpi-card + .alert-kpi-card {
    border-top: 1px solid #eef2f7;
    border-left: 0;
  }

  .alert-filter-foot,
  .alert-filter-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .alert-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .alert-filter-scope {
    width: auto;
  }
}
</style>
