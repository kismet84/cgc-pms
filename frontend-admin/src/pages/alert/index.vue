<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { Dayjs } from 'dayjs'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  BellOutlined,
  InboxOutlined,
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
  ALERT_PROCESS_STATUS_LABELS,
  RULE_CATEGORY_LABELS,
  RULE_TYPE_LABELS,
  getAlertRuleCategory,
  type AlertLogVO,
  type AlertSubscriptionConfig,
  type AlertSubscriptionResponse,
} from '@/types/alert'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { readPositiveIntQuery, readStringQuery, replaceListQuery } from '@/composables/listPageQuery'
import { downloadBlobFile } from '@/utils/download'
import AlertDetailPanel from './components/AlertDetailPanel.vue'
import AlertFilterPanel from './components/AlertFilterPanel.vue'
import AlertSubscriptionModal from './components/AlertSubscriptionModal.vue'
import AlertTablePanel from './components/AlertTablePanel.vue'

const router = useRouter()
const route = useRoute()
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
const hasLoaded = ref(false)
const listError = ref<string | null>(null)
const pageNo = ref(1)
const pageSize = ref(20)
const selectedRowKeys = ref<Array<string | number>>([])
const activeRecord = ref<AlertLogVO | null>(null)
const statusRemarkDraft = ref('')
const roleDefaultApplied = ref(false)

const projectOptions = computed(() => referenceStore.projects ?? [])
const total = computed(() => store.total)
const alerts = computed(() => store.alerts)
const processStatusOptions = computed(() =>
  Object.entries(ALERT_PROCESS_STATUS_LABELS).map(([value, label]) => ({ value, label })),
)
const isAlertAdmin = computed(() =>
  userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(String(role).toUpperCase())),
)
const canManageAlerts = computed(() => isAlertAdmin.value || userStore.hasPermission('alert:edit'))
const canExportAlerts = computed(() => isAlertAdmin.value || userStore.hasPermission('alert:view'))

type AlertRolePreset = {
  alertDomain?: string
  onlyDefaultScope?: boolean
}

function resolveRoleDefaultPreset(): AlertRolePreset {
  const roleCodes = userStore.roles.map((item) => String(item).toUpperCase())
  const roleName = String(userStore.userInfo?.roleName ?? '')

  if (
    roleCodes.includes('SUPER_ADMIN') ||
    roleCodes.includes('ADMIN') ||
    roleName.includes('超级管理员')
  ) {
    return {}
  }
  if (roleCodes.includes('PROJECT_MANAGER') || roleName.includes('项目经理')) {
    return {}
  }
  if (roleCodes.includes('COMMERCIAL_MANAGER') || roleName.includes('商务经理')) {
    return { alertDomain: 'COST', onlyDefaultScope: true }
  }
  if (roleCodes.includes('PURCHASE_MANAGER') || roleName.includes('采购经理')) {
    return { alertDomain: 'PURCHASE', onlyDefaultScope: true }
  }
  if (roleCodes.includes('PRODUCTION_MANAGER') || roleName.includes('生产经理')) {
    return { alertDomain: 'COST', onlyDefaultScope: true }
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
const availableSubscriptionDomains = computed(
  () => subscriptionState.value?.availableOptions.domains ?? [],
)
const availableSubscriptionChannels = computed(
  () => subscriptionState.value?.availableOptions.channels ?? [],
)
const availableSeverityOptions = computed(
  () => subscriptionState.value?.availableOptions.minSeverityOptions ?? ['LOW', 'MEDIUM', 'HIGH'],
)
const defaultSubscriptionEnabled = computed(() =>
  Boolean(subscriptionState.value?.defaultSubscription.enabled),
)
const defaultStatusChangeEnabled = computed(() =>
  Boolean(subscriptionState.value?.defaultSubscription.notifyOnStatusChanged),
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
const activeOperator = computed(
  () => String(activeRecord.value?.handledBy ?? currentOperator.value ?? '').trim() || '-',
)

function resolveSearchAlertDomain() {
  const preset = activeRolePreset.value
  if (filter.onlyDefaultScope && preset.alertDomain) {
    return preset.alertDomain
  }
  return filter.alertDomain
}

function hydrateFromRouteQuery() {
  filter.keyword = readStringQuery(route.query.keyword) ?? ''
  filter.projectId = readStringQuery(route.query.projectId)
  filter.alertDomain = readStringQuery(route.query.alertDomain)
  filter.ruleType = readStringQuery(route.query.ruleType)
  filter.severity = readStringQuery(route.query.severity)
  const isRead = readStringQuery(route.query.isRead)
  filter.isRead = isRead === undefined ? undefined : Number(isRead)
  filter.processStatus = readStringQuery(route.query.processStatus)
  const triggeredStart = readStringQuery(route.query.triggeredStart)
  const triggeredEnd = readStringQuery(route.query.triggeredEnd)
  filter.triggeredAtRange =
    triggeredStart && triggeredEnd ? [dayjs(triggeredStart), dayjs(triggeredEnd)] : null
  const onlyDefaultScope = readStringQuery(route.query.onlyDefaultScope)
  filter.onlyDefaultScope = onlyDefaultScope === 'true' || onlyDefaultScope === '1'
  pageNo.value = readPositiveIntQuery(route.query.pageNo, 1)
  pageSize.value = readPositiveIntQuery(route.query.pageSize, 20)
}

async function syncRouteQuery() {
  const nextQuery = replaceListQuery(
    route.query,
    {
      keyword: filter.keyword.trim() || undefined,
      projectId: filter.projectId,
      alertDomain: filter.alertDomain,
      ruleType: filter.ruleType,
      severity: filter.severity,
      isRead: filter.isRead,
      processStatus: filter.processStatus,
      triggeredStart: filter.triggeredAtRange?.[0]?.startOf('day').format('YYYY-MM-DD HH:mm:ss'),
      triggeredEnd: filter.triggeredAtRange?.[1]?.endOf('day').format('YYYY-MM-DD HH:mm:ss'),
      onlyDefaultScope: filter.onlyDefaultScope ? 1 : undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    },
    [
      'keyword',
      'projectId',
      'alertDomain',
      'ruleType',
      'severity',
      'isRead',
      'processStatus',
      'triggeredStart',
      'triggeredEnd',
      'onlyDefaultScope',
      'pageNo',
      'pageSize',
    ],
  )
  await router.replace({ path: route.path, query: nextQuery })
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
  const now = new Date().toISOString()
  successIds.forEach((id) => {
    syncActiveRecord(id, (item) => {
      item.processStatus = processStatus
      item.handledStatus = processStatus
      item.handledBy = currentOperator.value
      item.statusRemark = statusRemark
      if (processStatus === 'PROCESSED') {
        item.processedAt = now
        item.handledAt = now
      }
      if (processStatus === 'ARCHIVED' || processStatus === 'INVALID') {
        item.archivedAt = now
        item.handledAt = now
      }
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
  const matched = currentId ? list.find((item) => String(item.id) === String(currentId)) : null
  activeRecord.value = { ...(matched ?? list[0]) }
}

async function fetchData() {
  listError.value = null
  await syncRouteQuery()
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
    listError.value = '请检查筛选条件或网络状态后重试。'
    message.error('加载预警列表失败，请稍后重试')
  } finally {
    hasLoaded.value = true
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
  if (!canManageAlerts.value) return
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
  if (!canManageAlerts.value) return
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
  if (!canManageAlerts.value) return
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
  if (!canManageAlerts.value) return
  try {
    await store.changeStatus(record.id, processStatus, statusRemark)
    const now = new Date().toISOString()
    syncActiveRecord(record.id, (item) => {
      item.processStatus = processStatus
      item.handledStatus = processStatus
      item.handledBy = currentOperator.value
      item.statusRemark = statusRemark
      if (processStatus === 'PROCESSED') {
        item.processedAt = now
        item.handledAt = now
      }
      if (processStatus === 'ARCHIVED' || processStatus === 'INVALID') {
        item.archivedAt = now
        item.handledAt = now
      }
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
  await handleChangeStatus(
    activeRecord.value,
    nextStatus,
    statusRemarkDraft.value.trim() || undefined,
  )
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
    hint: `较昨日 ${Math.max(kpi.value.high - kpi.value.today, 0)}`,
    icon: BellOutlined,
    tone: 'danger',
  },
  {
    key: 'high',
    titleCn: '高危',
    titleEn: '',
    value: kpi.value.high,
    hint: `较昨日 ${Math.max(kpi.value.open - kpi.value.archived, 0)}`,
    icon: WarningOutlined,
    tone: 'warning',
  },
  {
    key: 'today',
    titleCn: '今日触发',
    titleEn: '',
    value: kpi.value.today,
    hint: `当前页 ${alerts.value.length}`,
    icon: ThunderboltOutlined,
    tone: 'primary',
  },
  {
    key: 'archived',
    titleCn: '已归档',
    titleEn: '',
    value: kpi.value.archived,
    hint: `总计 ${total.value}`,
    icon: InboxOutlined,
    tone: 'success',
  },
])

const selectedCount = computed(() => selectedRowKeys.value.length)
const hasActiveFilters = computed(
  () =>
    Boolean(filter.keyword.trim()) ||
    Boolean(filter.projectId) ||
    Boolean(filter.alertDomain) ||
    Boolean(filter.ruleType) ||
    Boolean(filter.severity) ||
    filter.isRead !== undefined ||
    Boolean(filter.processStatus) ||
    Boolean(filter.triggeredAtRange?.length) ||
    filter.onlyDefaultScope,
)
const showEmptyState = computed(
  () => hasLoaded.value && !store.loading && !listError.value && !alerts.value.length,
)
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
      effective?.minSeverity === 'HIGH'
        ? 'HIGH'
        : effective?.minSeverity === 'MEDIUM'
          ? 'MEDIUM'
          : 'LOW',
  }))
})

const gridColumns = computed(() => [
  { field: 'id', title: '告警ID', width: 188, showOverflow: 'title', slots: { default: 'id' } },
  {
    field: 'projectId',
    title: '项目',
    minWidth: 220,
    showOverflow: 'title',
    slots: { default: 'projectId' },
  },
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

function formatSeverityText(value: string) {
  return value === 'HIGH' ? '高' : value === 'MEDIUM' ? '中' : value === 'LOW' ? '低' : value
}

function buildAlertBusinessPath(record: AlertLogVO): string {
  const businessType = String(
    record.bizType ?? record.businessType ?? record.sourceType ?? '',
  ).trim()
  const businessId = String(record.bizId ?? record.businessId ?? record.sourceId ?? '').trim()
  if (businessType && businessId) {
    const dynamicRouteMap: Record<string, (id: string) => string> = {
      CONTRACT: (id) => `/contract/${id}`,
      CONTRACT_APPROVAL: (id) => `/contract/${id}`,
      PURCHASE_REQUEST: (id) => `/inventory/purchase-request?businessId=${id}`,
      SUB_MEASURE: (id) => `/subcontract/measure?businessId=${id}`,
      PAY_APPLICATION: (id) => `/payment/application?businessId=${id}`,
      PAY_REQUEST: (id) => `/payment/application?businessId=${id}`,
      PURCHASE_ORDER: (id) => `/purchase/order?businessId=${id}`,
      PURCHASE_RECEIPT: (id) => `/purchase/receipt?businessId=${id}`,
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

function exportCurrentView() {
  if (!canExportAlerts.value) return
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
  const header = [
    '告警ID',
    '项目',
    '规则域',
    '规则类型',
    '细分类',
    '严重度',
    '处理状态',
    '已读',
    '触发时间',
    '消息摘要',
  ]
  const csv = [header, ...rows]
    .map((line) => line.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(','))
    .join('\n')
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' })
  downloadBlobFile(blob, `alerts-${new Date().toISOString().slice(0, 10)}.csv`)
  message.success('已导出当前列表')
}

watch(
  activeRecord,
  (value) => {
    statusRemarkDraft.value = String(value?.statusRemark ?? '')
  },
  { immediate: true },
)

onMounted(async () => {
  hydrateFromRouteQuery()
  if (!filter.alertDomain && !filter.onlyDefaultScope) {
    applyRoleDefaultView()
  } else {
    roleDefaultApplied.value = true
  }
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
  <div class="lg-page app-page alert-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="lg-page-head-breadcrumb">
          <a-breadcrumb-item>首页</a-breadcrumb-item>
          <a-breadcrumb-item>预警中心</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <div class="alert-shell">
      <div class="alert-main">
        <section class="alert-kpi-grid">
          <article
            v-for="card in kpiCards"
            :key="card.key"
            class="alert-kpi-card"
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

        <AlertFilterPanel
          :filter="filter"
          :projects-loading="projectsLoading"
          :project-options="projectOptions"
          :process-status-options="processStatusOptions"
          :has-default-scope-domain="hasDefaultScopeDomain"
          :handle-search="handleSearch"
          :handle-reset="handleReset"
        />

        <AlertTablePanel
          :alerts="alerts"
          :column-settings="columnSettings"
          :col-visible="colVisible"
          :table-columns="tableColumns"
          :loading="store.loading"
          :table-height="tableHeight"
          :all-page-selected="allPageSelected"
          :page-selection-indeterminate="pageSelectionIndeterminate"
          :total="total"
          :page-no="pageNo"
          :page-size="pageSize"
          :selected-count="selectedCount"
          :list-error="listError"
          :show-empty-state="showEmptyState"
          :has-active-filters="hasActiveFilters"
          :can-manage-alerts="canManageAlerts"
          :can-export-alerts="canExportAlerts"
          :toggle-col="toggleCol"
          :toggle-page-selection="togglePageSelection"
          :is-row-selected="isRowSelected"
          :toggle-row-selection="toggleRowSelection"
          :open-detail="openDetail"
          :get-project-name="getProjectName"
          :get-alert-domain-label="getAlertDomainLabel"
          :get-alert-tag-label="getAlertTagLabel"
          :get-process-status-label="getProcessStatusLabel"
          :format-severity-text="formatSeverityText"
          :format-date-time="formatDateTime"
          :get-alert-message-text="getAlertMessageText"
          :handle-mark-read="handleMarkRead"
          :handle-change-status="handleChangeStatus"
          :handle-batch-status="handleBatchStatus"
          :handle-batch-mark-read="handleBatchMarkRead"
          :handle-page-change="handlePageChange"
          :handle-page-size-change="handlePageSizeChange"
          :handle-reset="handleReset"
          :handle-retry="fetchData"
          :export-current-view="exportCurrentView"
        />
      </div>

      <AlertDetailPanel
        :active-record="activeRecord"
        :status-remark-draft="statusRemarkDraft"
        :current-operator="activeOperator"
        :subscription-rows="subscriptionRows"
        :format-severity-text="formatSeverityText"
        :format-date-time="formatDateTime"
        :get-alert-domain-label="getAlertDomainLabel"
        :get-project-name="getProjectName"
        :get-alert-message-text="getAlertMessageText"
        :get-process-status-label="getProcessStatusLabel"
        :open-subscription-modal="openSubscriptionModal"
        :handle-mark-read="handleMarkRead"
        :handle-change-status="handleChangeStatus"
        :can-open-business-entry="canOpenBusinessEntry"
        :open-business-entry="openBusinessEntry"
        :handle-save-active-result="handleSaveActiveResult"
        @update:status-remark-draft="statusRemarkDraft = $event"
      />
    </div>

    <AlertSubscriptionModal
      :open="subscriptionVisible"
      :loading="subscriptionLoading"
      :saving="subscriptionSaving"
      :form="subscriptionForm"
      :available-subscription-channels="availableSubscriptionChannels"
      :available-subscription-domains="availableSubscriptionDomains"
      :available-severity-options="availableSeverityOptions"
      :default-subscription-enabled="defaultSubscriptionEnabled"
      :default-status-change-enabled="defaultStatusChangeEnabled"
      :handle-save-subscription="handleSaveSubscription"
      @update:open="subscriptionVisible = $event"
    />
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

@media (max-width: 1200px) {
  .alert-shell {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1100px) {
  .alert-kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .alert-kpi-grid {
    grid-template-columns: 1fr;
  }

  .alert-kpi-card + .alert-kpi-card {
    border-top: 1px solid #eef2f7;
    border-left: 0;
  }
}
</style>
