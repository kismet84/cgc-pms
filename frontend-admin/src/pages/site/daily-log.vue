<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { message, Modal } from 'ant-design-vue'
import { FilterOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { useReferenceStore } from '@/stores/reference'
import { useUserStore } from '@/stores/user'
import {
  createSiteDailyLog,
  getSiteDailyLog,
  getSiteDailyLogs,
  getSiteDailyQualitySafetyFacts,
  submitSiteDailyLog,
  updateSiteDailyLog,
} from '@/api/modules/site-daily-log'
import { deleteFile, getFileUrl, listFiles, uploadFile } from '@/api/modules/file'
import {
  getDailyProgress,
  replaceDailyProgress,
  type DailyProgressRequest,
} from '@/api/modules/projectSchedule'
import type {
  SiteDailyLogCommand,
  SiteDailyLogVO,
  SiteDailyQualitySafetyVO,
} from '@/types/site-daily-log'
import type { SysFileVO } from '@/types/file'
import { useMobileViewport } from '@/composables/useMobileViewport'

const SITE_DAILY_LOG = 'SITE_DAILY_LOG'
const referenceStore = useReferenceStore()
const userStore = useUserStore()
const { projects } = storeToRefs(referenceStore)
const { isMobile } = useMobileViewport()
const mobileFiltersOpen = ref(false)
const canEdit = computed(
  () =>
    userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(role)) ||
    userStore.hasPermission('site:daily:edit'),
)
const canReportProgress = computed(
  () =>
    userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(role)) ||
    userStore.hasPermission('schedule:progress'),
)
const canViewQualitySafety = computed(
  () =>
    userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(role)) ||
    userStore.hasPermission('quality:safety:query'),
)

interface DailyProgressFormRow extends DailyProgressRequest {
  taskCode: string
  taskName: string
  included: boolean
}

const loading = ref(false)
const hasLoaded = ref(false)
const listError = ref('')
const records = ref<SiteDailyLogVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const filter = reactive({
  projectId: undefined as string | undefined,
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined,
  status: undefined as string | undefined,
})

const modalOpen = ref(false)
const modalMode = ref<'create' | 'edit' | 'view'>('create')
const activeRecord = ref<SiteDailyLogVO | null>(null)
const saving = ref(false)
const files = ref<SysFileVO[]>([])
const filesLoading = ref(false)
const progressSaving = ref(false)
const progressRows = ref<DailyProgressFormRow[]>([])
const qualitySafetyFacts = ref<SiteDailyQualitySafetyVO[]>([])
const qualitySafetyLoading = ref(false)
const qualitySafetyError = ref('')
let qualitySafetyRequestId = 0
const form = reactive<SiteDailyLogCommand>({
  projectId: undefined,
  reportDate: undefined,
  constructionContent: '',
  issuesDelays: '',
  nextDayPlan: '',
  weatherSummary: '',
  onSiteHeadcount: null,
})

const columns = [
  { title: '项目', dataIndex: 'projectName', key: 'projectName' },
  { title: '日报日期', dataIndex: 'reportDate', key: 'reportDate', width: 120 },
  {
    title: '施工内容',
    dataIndex: 'constructionContent',
    key: 'constructionContent',
    ellipsis: true,
  },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '提交时间', dataIndex: 'submittedAt', key: 'submittedAt', width: 170 },
  { title: '操作', key: 'action', width: 220 },
]

const submittedCount = computed(
  () => records.value.filter((record) => record.status === 'SUBMITTED').length,
)
const draftCount = computed(
  () => records.value.filter((record) => record.status === 'DRAFT').length,
)
const recentRecords = computed(() => records.value.slice(0, 4))

async function fetchData() {
  loading.value = true
  listError.value = ''
  try {
    const page = await getSiteDailyLogs({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      ...filter,
    })
    records.value = page.records
    total.value = Number(page.total || 0)
  } catch {
    records.value = []
    total.value = 0
    listError.value = '现场日报加载失败，请稍后重试。'
  } finally {
    loading.value = false
    hasLoaded.value = true
  }
}

function resetForm(record?: SiteDailyLogVO) {
  Object.assign(form, {
    projectId: record?.projectId,
    reportDate: record?.reportDate,
    constructionContent: record?.constructionContent ?? '',
    issuesDelays: record?.issuesDelays ?? '',
    nextDayPlan: record?.nextDayPlan ?? '',
    weatherSummary: record?.weatherSummary ?? '',
    onSiteHeadcount: record?.onSiteHeadcount ?? null,
  })
}

function openCreate() {
  qualitySafetyRequestId += 1
  qualitySafetyFacts.value = []
  qualitySafetyError.value = ''
  modalMode.value = 'create'
  activeRecord.value = null
  files.value = []
  resetForm()
  modalOpen.value = true
}

async function openRecord(record: SiteDailyLogVO, edit = false) {
  modalMode.value = edit ? 'edit' : 'view'
  try {
    const detail = await getSiteDailyLog(record.id)
    activeRecord.value = detail
    await loadProgress(detail)
    resetForm(detail)
    modalOpen.value = true
    await fetchFiles(record.id)
    await loadQualitySafetyFacts(record.id)
  } catch {
    message.error('现场日报详情加载失败')
  }
}

async function loadQualitySafetyFacts(dailyLogId: string) {
  const requestId = ++qualitySafetyRequestId
  qualitySafetyFacts.value = []
  qualitySafetyError.value = ''
  if (!canViewQualitySafety.value) {
    qualitySafetyLoading.value = false
    return
  }
  qualitySafetyLoading.value = true
  try {
    const facts = await getSiteDailyQualitySafetyFacts(dailyLogId)
    if (requestId === qualitySafetyRequestId && activeRecord.value?.id === dailyLogId)
      qualitySafetyFacts.value = facts
  } catch {
    if (requestId === qualitySafetyRequestId && activeRecord.value?.id === dailyLogId)
      qualitySafetyError.value = '当日质量安全检查加载失败，不影响日报正文查看。'
  } finally {
    if (requestId === qualitySafetyRequestId) qualitySafetyLoading.value = false
  }
}

async function loadProgress(detail: SiteDailyLogVO) {
  if (!detail.scheduleManaged) {
    progressRows.value = []
    return
  }
  const existing = await getDailyProgress(detail.id)
  const byTask = new Map(existing.map((row) => [String(row.wbsTaskId), row]))
  progressRows.value = (detail.plannedTasks ?? []).map((task) => {
    const row = byTask.get(task.id)
    return {
      wbsTaskId: task.id,
      taskCode: task.taskCode,
      taskName: task.taskName,
      currentProgress: Number(row?.currentProgress ?? task.progressPercent ?? 0),
      completedQuantity: Number(row?.completedQuantity ?? 0),
      workDescription: String(row?.workDescription ?? ''),
      included: Boolean(row),
    }
  })
}

async function saveProgress() {
  if (!activeRecord.value?.scheduleManaged) return true
  const items = progressRows.value.filter((row) => row.included)
  if (!items.length) {
    message.warning('至少选择一条当周WBS任务填报实际进度')
    return false
  }
  if (items.some((row) => !row.workDescription.trim())) {
    message.warning('已选任务必须填写完成情况')
    return false
  }
  progressSaving.value = true
  try {
    await replaceDailyProgress(
      activeRecord.value.id,
      items.map(({ wbsTaskId, currentProgress, completedQuantity, workDescription }) => ({
        wbsTaskId,
        currentProgress,
        completedQuantity,
        workDescription,
      })),
    )
    message.success('实际进度已保存')
    return true
  } finally {
    progressSaving.value = false
  }
}

async function save() {
  if (!form.projectId || !form.reportDate || !form.constructionContent.trim()) {
    message.warning('请完整填写项目、日报日期和施工内容')
    return
  }
  saving.value = true
  try {
    if (modalMode.value === 'edit' && activeRecord.value)
      await updateSiteDailyLog(activeRecord.value.id, form)
    else await createSiteDailyLog(form)
    message.success('日报草稿已保存')
    modalOpen.value = false
    await fetchData()
  } catch {
    message.error('保存现场日报失败')
  } finally {
    saving.value = false
  }
}

function submitRecord(record: SiteDailyLogVO) {
  Modal.confirm({
    title: '提交现场日报',
    content: '提交后正文和附件不可修改，确认提交？',
    async onOk() {
      if (record.scheduleManaged && !(await saveProgress())) return
      await submitSiteDailyLog(record.id)
      message.success('现场日报已提交')
      modalOpen.value = false
      await fetchData()
    },
  })
}

async function fetchFiles(id: string) {
  filesLoading.value = true
  try {
    files.value = await listFiles(SITE_DAILY_LOG, id)
  } catch {
    files.value = []
    message.error('附件列表加载失败')
  } finally {
    filesLoading.value = false
  }
}

async function onFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || !activeRecord.value) return
  await uploadFile(file, SITE_DAILY_LOG, activeRecord.value.id)
  await fetchFiles(activeRecord.value.id)
}

async function download(file: SysFileVO) {
  const url = await getFileUrl(file.id)
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function removeFile(file: SysFileVO) {
  await deleteFile(file.id)
  if (activeRecord.value) await fetchFiles(activeRecord.value.id)
}

function resetFilters() {
  filter.projectId = undefined
  filter.startDate = undefined
  filter.endDate = undefined
  filter.status = undefined
  pageNo.value = 1
  mobileFiltersOpen.value = false
  fetchData()
}

function applyMobileFilters() {
  mobileFiltersOpen.value = false
  pageNo.value = 1
  fetchData()
}

function auditRowKey(audit: NonNullable<SiteDailyLogVO['auditTrail']>[number]) {
  return `${audit.createdAt}-${audit.operationType}-${audit.userId}-${audit.success}`
}

onMounted(() => {
  referenceStore.fetchProjects()
  fetchData()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page site-daily-page project-operation-list-page">
    <div class="lg-page-head site-daily-page-head">
      <a-breadcrumb>
        <a-breadcrumb-item>项目经营</a-breadcrumb-item>
        <a-breadcrumb-item>现场日报</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="lg-grid site-daily-workspace project-operation-workspace">
      <div class="lg-left site-daily-main project-operation-main-column">
        <section
          class="lg-search-bar site-daily-query project-operation-query-panel"
          aria-label="现场日报查询条件"
        >
          <div class="site-daily-search-row">
            <a-select
              v-model:value="filter.projectId"
              placeholder="搜索项目名称"
              allow-clear
              show-search
              size="large"
              class="site-daily-project-select"
            >
              <a-select-option v-for="project in projects" :key="project.id" :value="project.id">
                {{ project.projectName }}
              </a-select-option>
            </a-select>
            <a-button
              type="primary"
              class="site-daily-search-button project-operation-desktop-query-action"
              @click="fetchData"
            >
              <template #icon><SearchOutlined /></template>搜索
            </a-button>
            <a-button
              class="site-daily-reset-button project-operation-desktop-query-action"
              @click="resetFilters"
            >
              <template #icon><ReloadOutlined /></template>重置
            </a-button>
            <a-button
              class="site-daily-filter-button project-operation-filter-toggle"
              :aria-expanded="mobileFiltersOpen"
              aria-controls="site-daily-filter-panel"
              @click="mobileFiltersOpen = !mobileFiltersOpen"
            >
              <template #icon><FilterOutlined /></template>筛选
            </a-button>
          </div>
          <div
            id="site-daily-filter-panel"
            class="site-daily-filter-panel project-operation-filter-panel"
            :class="{ 'is-open': mobileFiltersOpen }"
          >
            <a-date-picker
              v-model:value="filter.startDate"
              value-format="YYYY-MM-DD"
              placeholder="开始日期"
            />
            <a-date-picker
              v-model:value="filter.endDate"
              value-format="YYYY-MM-DD"
              placeholder="结束日期"
            />
            <a-select v-model:value="filter.status" placeholder="全部状态" allow-clear>
              <a-select-option value="DRAFT">草稿</a-select-option>
              <a-select-option value="SUBMITTED">已提交</a-select-option>
            </a-select>
            <div class="site-daily-filter-actions">
              <a-button @click="resetFilters">重置</a-button>
              <a-button type="primary" @click="applyMobileFilters">应用筛选</a-button>
            </div>
          </div>
        </section>

        <main class="lg-list-table-panel site-daily-table-panel project-operation-table-panel">
          <div class="lg-toolbar site-daily-toolbar">
            <div class="lg-toolbar-left">
              <strong>现场日报</strong><span>共 {{ total }} 条</span>
            </div>
            <div class="lg-toolbar-right">
              <a-button v-if="canEdit" type="primary" @click="openCreate">
                <template #icon><PlusOutlined /></template>新建日报
              </a-button>
              <a-button v-if="!isMobile" @click="fetchData">
                <template #icon><ReloadOutlined /></template>刷新
              </a-button>
            </div>
          </div>

          <div class="lg-table-wrap site-daily-table-wrap">
            <a-result
              v-if="listError"
              status="error"
              title="现场日报加载失败"
              :sub-title="listError"
            >
              <template #extra
                ><a-button type="primary" @click="fetchData">重试</a-button></template
              >
            </a-result>
            <a-empty
              v-else-if="hasLoaded && !loading && !records.length"
              description="暂无现场日报"
            />
            <div v-else-if="isMobile" class="site-daily-mobile-list">
              <article v-for="record in records" :key="record.id" class="site-daily-mobile-card">
                <div class="site-daily-mobile-card-head">
                  <strong>{{ record.projectName || '-' }}</strong>
                  <a-tag :color="record.status === 'DRAFT' ? 'default' : 'success'">
                    {{ record.status === 'DRAFT' ? '草稿' : '已提交' }}
                  </a-tag>
                </div>
                <div class="site-daily-mobile-date">{{ record.reportDate }}</div>
                <div class="site-daily-mobile-content">{{ record.constructionContent || '-' }}</div>
                <button type="button" class="site-daily-card-hitarea" @click="openRecord(record)">
                  查看详情
                </button>
              </article>
            </div>
            <a-table
              v-else
              class="site-daily-desktop-table"
              :loading="loading"
              :columns="columns"
              :data-source="records"
              :pagination="false"
              row-key="id"
              :scroll="{ x: 900 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'status'">
                  <a-tag :color="record.status === 'DRAFT' ? 'default' : 'success'">
                    {{ record.status === 'DRAFT' ? '草稿' : '已提交' }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'action'">
                  <a-space>
                    <a-button type="link" @click="openRecord(record)">查看</a-button>
                    <a-button
                      v-if="canEdit && record.status === 'DRAFT'"
                      type="link"
                      @click="openRecord(record, true)"
                      >编辑</a-button
                    >
                    <a-button
                      v-if="canEdit && record.status === 'DRAFT'"
                      type="link"
                      @click="submitRecord(record)"
                      >提交</a-button
                    >
                  </a-space>
                </template>
              </template>
            </a-table>
          </div>
          <div class="lg-pagination site-daily-pagination">
            <span class="lg-total">共 {{ total }} 条</span>
            <a-pagination
              v-model:current="pageNo"
              v-model:page-size="pageSize"
              :total="total"
              :show-size-changer="!isMobile"
              @change="fetchData"
            />
          </div>
        </main>
      </div>

      <aside
        class="lg-analysis-rail site-daily-analysis project-operation-analysis-rail"
        aria-label="现场日报辅助分析"
      >
        <div class="lg-analysis-panel lg-fill-card site-daily-analysis-panel">
          <header class="lg-analysis-header">
            <div>
              <strong class="lg-analysis-heading">辅助分析</strong>
              <span class="lg-analysis-description">日报状态与近期记录</span>
            </div>
          </header>
          <section class="lg-analysis-section">
            <div class="lg-analysis-section-title">日报概览</div>
            <div class="lg-analysis-overview-list">
              <div class="lg-analysis-overview-row">
                <span>当前记录</span><strong>{{ total }} 条</strong>
              </div>
              <div class="lg-analysis-overview-row">
                <span>已提交</span><strong>{{ submittedCount }} 条</strong>
              </div>
              <div class="lg-analysis-overview-row">
                <span>草稿</span><strong>{{ draftCount }} 条</strong>
              </div>
            </div>
          </section>
          <section class="lg-analysis-section">
            <div class="lg-analysis-section-title">近期日报</div>
            <div v-for="record in recentRecords" :key="record.id" class="lg-type-row">
              <span class="lg-type-dot lg-analysis-dot-primary"></span>
              <span class="lg-type-label">{{ record.projectName || '-' }}</span>
              <span class="lg-type-pct">{{ record.reportDate }}</span>
            </div>
            <div v-if="!recentRecords.length" class="site-daily-analysis-empty">暂无日报</div>
          </section>
        </div>
      </aside>
    </div>

    <a-modal
      v-model:open="modalOpen"
      :title="
        modalMode === 'create'
          ? '新建现场日报'
          : modalMode === 'edit'
            ? '编辑现场日报'
            : '现场日报详情'
      "
      :confirm-loading="saving"
      :ok-button-props="{ style: { display: modalMode === 'view' ? 'none' : '' } }"
      @ok="save"
    >
      <a-form layout="vertical" :disabled="modalMode === 'view'">
        <a-form-item label="项目" required
          ><a-select v-model:value="form.projectId"
            ><a-select-option v-for="project in projects" :key="project.id" :value="project.id">{{
              project.projectName
            }}</a-select-option></a-select
          ></a-form-item
        >
        <a-form-item label="日报日期" required
          ><a-date-picker
            v-model:value="form.reportDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
        /></a-form-item>
        <a-form-item label="施工内容" required
          ><a-textarea v-model:value="form.constructionContent" :rows="4"
        /></a-form-item>
        <a-form-item label="问题与延误"
          ><a-textarea v-model:value="form.issuesDelays" :rows="3"
        /></a-form-item>
        <a-form-item label="次日计划"
          ><a-textarea v-model:value="form.nextDayPlan" :rows="3"
        /></a-form-item>
        <a-form-item label="人工天气摘要"
          ><a-textarea
            v-model:value="form.weatherSummary"
            :maxlength="200"
            :rows="2"
            placeholder="人工填写当日天气，可留空"
        /></a-form-item>
        <a-form-item label="在场人数">
          <span v-if="modalMode === 'view'">{{
            form.onSiteHeadcount == null ? '未填写' : form.onSiteHeadcount
          }}</span>
          <a-input-number
            v-else
            v-model:value="form.onSiteHeadcount"
            :min="0"
            :max="100000"
            :precision="0"
            placeholder="未填写"
            style="width: 100%"
          />
        </a-form-item>
      </a-form>
      <section v-if="activeRecord && modalMode === 'view'" class="site-daily-deliveries">
        <strong>当日材料到货</strong>
        <a-table
          v-if="activeRecord.deliveries?.length"
          :data-source="activeRecord.deliveries"
          :pagination="false"
          row-key="receiptItemId"
          size="small"
        >
          <a-table-column key="receiptCode" title="验收单" data-index="receiptCode" />
          <a-table-column key="partnerName" title="供应商" data-index="partnerName" />
          <a-table-column key="materialName" title="物料" data-index="materialName" />
          <a-table-column key="actualQuantity" title="实收" data-index="actualQuantity" />
          <a-table-column key="qualifiedQuantity" title="合格" data-index="qualifiedQuantity" />
          <template #bodyCell="{ column, record: delivery }"
            ><span v-if="column.key === 'receiptCode'">{{ delivery.receiptCode }}</span
            ><span v-else-if="column.key === 'partnerName'">{{ delivery.partnerName || '-' }}</span
            ><span v-else-if="column.key === 'materialName'">{{
              delivery.materialName || '-'
            }}</span
            ><span v-else-if="column.key === 'actualQuantity'">{{
              delivery.actualQuantity || '-'
            }}</span
            ><span v-else-if="column.key === 'qualifiedQuantity'">{{
              delivery.qualifiedQuantity || '-'
            }}</span></template
          >
        </a-table>
        <a-empty v-else description="当日暂无已审批材料到货" />
      </section>
      <section v-if="activeRecord && modalMode === 'view'" class="site-daily-planned-tasks">
        <strong>当日已审批领料</strong>
        <a-table
          v-if="activeRecord.requisitions?.length"
          :data-source="activeRecord.requisitions"
          :pagination="false"
          row-key="requisitionItemId"
          size="small"
        >
          <a-table-column key="requisitionCode" title="领料单号" data-index="requisitionCode" />
          <a-table-column key="materialName" title="物料" data-index="materialName" />
          <a-table-column key="quantity" title="领料数量" data-index="quantity" />
          <a-table-column key="materialUnit" title="单位" data-index="materialUnit" />
          <a-table-column key="useLocation" title="使用部位" data-index="useLocation" />
          <template #bodyCell="{ column, record: requisition }"
            ><span v-if="column.key === 'requisitionCode'">{{ requisition.requisitionCode }}</span
            ><span v-else-if="column.key === 'materialName'">{{
              requisition.materialName || '-'
            }}</span
            ><span v-else-if="column.key === 'quantity'">{{ requisition.quantity ?? '-' }}</span
            ><span v-else-if="column.key === 'materialUnit'">{{
              requisition.materialUnit || '-'
            }}</span
            ><span v-else-if="column.key === 'useLocation'">{{
              requisition.useLocation || '-'
            }}</span></template
          >
        </a-table>
        <a-empty v-else description="当日暂无已审批且已出库领料" />
      </section>
      <section v-if="activeRecord && modalMode === 'view'" class="site-daily-planned-tasks">
        <strong>当日WBS实际进度</strong>
        <a-alert
          v-if="activeRecord.scheduleManaged"
          type="info"
          show-icon
          message="项目已启用基线计划：提交日报时至少填报一条已审批周计划内任务，提交后自动更新WBS并生成偏差快照。"
        />
        <a-table
          v-if="activeRecord.scheduleManaged && progressRows.length"
          :data-source="progressRows"
          :pagination="false"
          row-key="wbsTaskId"
          size="small"
        >
          <a-table-column key="included" title="填报" width="70">
            <template #default="{ record: progress }">
              <a-checkbox
                v-model:checked="progress.included"
                :disabled="activeRecord.status !== 'DRAFT' || !canReportProgress"
              />
            </template>
          </a-table-column>
          <a-table-column key="taskCode" title="任务编号" data-index="taskCode" />
          <a-table-column key="taskName" title="任务" data-index="taskName" />
          <a-table-column key="currentProgress" title="累计进度(%)" width="150">
            <template #default="{ record: progress }">
              <a-input-number
                v-model:value="progress.currentProgress"
                :min="0"
                :max="100"
                :disabled="
                  !progress.included || activeRecord.status !== 'DRAFT' || !canReportProgress
                "
              />
            </template>
          </a-table-column>
          <a-table-column key="completedQuantity" title="累计完成量" width="150">
            <template #default="{ record: progress }">
              <a-input-number
                v-model:value="progress.completedQuantity"
                :min="0"
                :disabled="
                  !progress.included || activeRecord.status !== 'DRAFT' || !canReportProgress
                "
              />
            </template>
          </a-table-column>
          <a-table-column key="workDescription" title="完成情况">
            <template #default="{ record: progress }">
              <a-input
                v-model:value="progress.workDescription"
                :disabled="
                  !progress.included || activeRecord.status !== 'DRAFT' || !canReportProgress
                "
              />
            </template>
          </a-table-column>
        </a-table>
        <a-button
          v-if="
            activeRecord.scheduleManaged && activeRecord.status === 'DRAFT' && canReportProgress
          "
          type="primary"
          :loading="progressSaving"
          @click="saveProgress"
          >保存实际进度</a-button
        >
      </section>
      <section
        v-if="activeRecord && modalMode === 'view' && canViewQualitySafety"
        class="site-daily-planned-tasks"
      >
        <strong>当日质量安全检查</strong>
        <a-spin :spinning="qualitySafetyLoading">
          <a-alert
            v-if="qualitySafetyError"
            type="warning"
            show-icon
            :message="qualitySafetyError"
          />
          <a-table
            v-else-if="qualitySafetyFacts.length"
            :data-source="qualitySafetyFacts"
            :pagination="false"
            row-key="inspectionId"
            size="small"
          >
            <a-table-column key="inspectionCode" title="检查编号" data-index="inspectionCode" />
            <a-table-column key="location" title="检查地点" data-index="location" />
            <a-table-column key="conclusion" title="结论" data-index="conclusion" />
            <a-table-column key="issueCount" title="问题总数" data-index="issueCount" />
            <a-table-column
              key="highSeverityIssueCount"
              title="高风险"
              data-index="highSeverityIssueCount"
            />
            <a-table-column key="openIssueCount" title="未关闭" data-index="openIssueCount" />
          </a-table>
          <a-empty v-else description="当日暂无已提交质量安全检查" />
        </a-spin>
      </section>
      <section v-if="activeRecord && modalMode === 'view'" class="site-daily-planned-tasks">
        <strong>当日计划任务</strong>
        <a-table
          v-if="activeRecord.plannedTasks?.length"
          :data-source="activeRecord.plannedTasks"
          :pagination="false"
          row-key="id"
          size="small"
        >
          <a-table-column key="taskCode" title="任务编号" data-index="taskCode" />
          <a-table-column key="taskName" title="任务" data-index="taskName" />
          <a-table-column key="workArea" title="作业区域" data-index="workArea" />
          <a-table-column key="plannedDate" title="计划日期" />
          <a-table-column key="status" title="状态" data-index="status" />
          <a-table-column key="progressPercent" title="进度(%)" data-index="progressPercent" />
          <template #bodyCell="{ column, record: planned }"
            ><span v-if="column.key === 'taskCode'">{{ planned.taskCode }}</span
            ><span v-else-if="column.key === 'taskName'">{{ planned.taskName }}</span
            ><span v-else-if="column.key === 'workArea'">{{ planned.workArea || '-' }}</span
            ><span v-else-if="column.key === 'plannedDate'"
              >{{ planned.plannedStartDate }} 至 {{ planned.plannedEndDate }}</span
            ><span v-else-if="column.key === 'status'">{{ planned.status }}</span
            ><span v-else-if="column.key === 'progressPercent'">{{
              planned.progressPercent ?? '-'
            }}</span></template
          >
        </a-table>
        <a-empty v-else description="当日暂无计划任务" />
      </section>
      <section v-if="activeRecord && modalMode === 'view'" class="site-daily-audit-trail">
        <strong>变更历史</strong>
        <a-table
          v-if="activeRecord.auditTrail?.length"
          :data-source="activeRecord.auditTrail"
          :pagination="false"
          :row-key="auditRowKey"
          size="small"
        >
          <a-table-column key="operationType" title="动作" data-index="operationType" />
          <a-table-column key="userId" title="用户ID" data-index="userId" />
          <a-table-column key="success" title="结果" />
          <a-table-column key="createdAt" title="时间" data-index="createdAt" />
          <template #bodyCell="{ column, record: audit }"
            ><span v-if="column.key === 'operationType'">{{ audit.operationType }}</span
            ><span v-else-if="column.key === 'userId'">{{ audit.userId || '-' }}</span
            ><a-tag
              v-else-if="column.key === 'success'"
              :color="audit.success ? 'success' : 'error'"
              >{{ audit.success ? '成功' : '失败' }}</a-tag
            ><span v-else-if="column.key === 'createdAt'">{{
              audit.createdAt || '-'
            }}</span></template
          >
        </a-table>
        <a-empty v-else description="暂无变更记录" />
      </section>
      <section v-if="activeRecord" class="site-daily-files">
        <strong>附件</strong
        ><input
          v-if="canEdit && activeRecord.status === 'DRAFT'"
          type="file"
          @change="onFileChange"
        /><a-spin :spinning="filesLoading"
          ><div v-for="file in files" :key="file.id">
            <a-button type="link" @click="download(file)">{{ file.originalName }}</a-button
            ><a-button
              v-if="canEdit && activeRecord.status === 'DRAFT'"
              danger
              type="link"
              @click="removeFile(file)"
              >删除</a-button
            >
          </div>
          <a-empty v-if="!files.length" description="暂无附件"
        /></a-spin>
      </section>
      <template #footer
        ><a-button @click="modalOpen = false">关闭</a-button
        ><a-button v-if="modalMode !== 'view'" type="primary" :loading="saving" @click="save"
          >保存草稿</a-button
        ><a-button
          v-if="canEdit && activeRecord?.status === 'DRAFT'"
          type="primary"
          @click="submitRecord(activeRecord)"
          >提交定稿</a-button
        ></template
      >
    </a-modal>
  </div>
</template>

<style scoped>
.site-daily-page {
  min-width: 0;
}

.site-daily-page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.site-daily-query {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  box-sizing: border-box;
  min-height: 60px;
  padding: 10px 14px;
  border: 0;
  box-shadow:
    inset 0 0 0 1px var(--border),
    var(--shadow-soft);
}

.site-daily-search-row,
.site-daily-filter-panel,
.site-daily-filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.site-daily-search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto auto;
  width: 100%;
}

.site-daily-project-select {
  width: 100%;
  min-width: 0;
}

.site-daily-project-select :deep(.ant-select-selector) {
  height: 40px !important;
  align-items: center;
}

.site-daily-filter-panel.is-open {
  display: grid !important;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 10px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.site-daily-filter-actions {
  grid-column: 1 / -1;
  justify-content: flex-end;
}

.site-daily-table-panel,
.site-daily-table-wrap {
  min-height: 0;
}

.site-daily-table-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.site-daily-table-wrap {
  flex: 1 1 auto;
  overflow: auto;
}

.site-daily-table-wrap :deep(.site-daily-desktop-table),
.site-daily-table-wrap :deep(.site-daily-desktop-table .ant-spin-nested-loading),
.site-daily-table-wrap :deep(.site-daily-desktop-table .ant-spin-container),
.site-daily-table-wrap :deep(.site-daily-desktop-table .ant-table),
.site-daily-table-wrap :deep(.site-daily-desktop-table .ant-table-container) {
  height: 100%;
  min-height: 0;
}

.site-daily-table-wrap :deep(.site-daily-desktop-table .ant-spin-container),
.site-daily-table-wrap :deep(.site-daily-desktop-table .ant-table),
.site-daily-table-wrap :deep(.site-daily-desktop-table .ant-table-container) {
  display: flex;
  flex-direction: column;
}

.site-daily-table-wrap :deep(.site-daily-desktop-table .ant-table-container),
.site-daily-table-wrap :deep(.site-daily-desktop-table .ant-table-content) {
  flex: 1 1 auto;
  min-height: 0;
}

.site-daily-table-wrap :deep(.site-daily-desktop-table .ant-table-content) {
  height: 100% !important;
  overflow: auto;
}

.site-daily-pagination {
  justify-content: flex-end;
}

.site-daily-analysis-panel header,
.site-daily-analysis-panel section {
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-subtle);
}

.site-daily-analysis-panel header {
  display: grid;
  gap: 2px;
}

.site-daily-analysis-panel header span,
.site-daily-analysis-panel small,
.site-daily-analysis-empty {
  color: var(--text-secondary);
  font-size: 12px;
}

.site-daily-analysis-panel h3 {
  margin: 0 0 10px;
  font-size: 14px;
}

.site-daily-analysis-row,
.site-daily-recent-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-height: 28px;
}

.site-daily-recent-row {
  padding: 0;
  color: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.site-daily-recent-row span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.site-daily-mobile-list {
  display: grid;
}

.site-daily-mobile-card {
  position: relative;
  display: grid;
  gap: 4px;
  min-height: 92px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-subtle);
}

.site-daily-mobile-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.site-daily-mobile-date,
.site-daily-mobile-content {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.site-daily-card-hitarea {
  position: absolute;
  inset: 0;
  overflow: hidden;
  color: transparent;
  background: transparent;
  border: 0;
  cursor: pointer;
}
.site-daily-files {
  display: grid;
  gap: 8px;
  margin-top: 16px;
}
.site-daily-deliveries {
  display: grid;
  gap: 8px;
  margin-top: 16px;
}
.site-daily-planned-tasks {
  display: grid;
  gap: 8px;
  margin-top: 16px;
}
.site-daily-audit-trail {
  display: grid;
  gap: 8px;
  margin-top: 16px;
}

@media (width < 500px) {
  .site-daily-query {
    display: flex;
    min-height: 40px;
    padding: 0;
    background: transparent;
    box-shadow: none;
  }

  .site-daily-search-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 6px;
  }

  .site-daily-project-select {
    min-width: 0;
  }

  .site-daily-filter-panel.is-open {
    display: grid !important;
    grid-template-columns: 1fr;
    gap: 8px;
    margin-top: 8px;
    padding: 10px;
    background: var(--surface);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
  }

  .site-daily-toolbar {
    min-height: 48px;
    padding: 8px 12px;
  }

  .site-daily-mobile-card {
    min-height: 88px;
    padding: 10px 14px;
  }
}
</style>
