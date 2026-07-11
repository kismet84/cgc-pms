<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { message, Modal } from 'ant-design-vue'
import { useReferenceStore } from '@/stores/reference'
import { useUserStore } from '@/stores/user'
import {
  createSiteDailyLog,
  getSiteDailyLogs,
  submitSiteDailyLog,
  updateSiteDailyLog,
} from '@/api/modules/site-daily-log'
import { deleteFile, getFileUrl, listFiles, uploadFile } from '@/api/modules/file'
import type { SiteDailyLogCommand, SiteDailyLogVO } from '@/types/site-daily-log'
import type { SysFileVO } from '@/types/file'

const SITE_DAILY_LOG = 'SITE_DAILY_LOG'
const referenceStore = useReferenceStore()
const userStore = useUserStore()
const { projects } = storeToRefs(referenceStore)
const canEdit = computed(
  () => userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(role)) || userStore.hasPermission('site:daily:edit'),
)

const loading = ref(false)
const hasLoaded = ref(false)
const listError = ref('')
const records = ref<SiteDailyLogVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const filter = reactive({ projectId: undefined as string | undefined, startDate: undefined as string | undefined, endDate: undefined as string | undefined, status: undefined as string | undefined })

const modalOpen = ref(false)
const modalMode = ref<'create' | 'edit' | 'view'>('create')
const activeRecord = ref<SiteDailyLogVO | null>(null)
const saving = ref(false)
const files = ref<SysFileVO[]>([])
const filesLoading = ref(false)
const form = reactive<SiteDailyLogCommand>({ projectId: undefined, reportDate: undefined, constructionContent: '', issuesDelays: '', nextDayPlan: '' })

const columns = [
  { title: '项目', dataIndex: 'projectName', key: 'projectName' },
  { title: '日报日期', dataIndex: 'reportDate', key: 'reportDate', width: 120 },
  { title: '施工内容', dataIndex: 'constructionContent', key: 'constructionContent', ellipsis: true },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '提交时间', dataIndex: 'submittedAt', key: 'submittedAt', width: 170 },
  { title: '操作', key: 'action', width: 220 },
]

async function fetchData() {
  loading.value = true
  listError.value = ''
  try {
    const page = await getSiteDailyLogs({ pageNo: pageNo.value, pageSize: pageSize.value, ...filter })
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
  })
}

function openCreate() {
  modalMode.value = 'create'
  activeRecord.value = null
  files.value = []
  resetForm()
  modalOpen.value = true
}

async function openRecord(record: SiteDailyLogVO, edit = false) {
  modalMode.value = edit ? 'edit' : 'view'
  activeRecord.value = record
  resetForm(record)
  modalOpen.value = true
  await fetchFiles(record.id)
}

async function save() {
  if (!form.projectId || !form.reportDate || !form.constructionContent.trim()) {
    message.warning('请完整填写项目、日报日期和施工内容')
    return
  }
  saving.value = true
  try {
    if (modalMode.value === 'edit' && activeRecord.value) await updateSiteDailyLog(activeRecord.value.id, form)
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
      await submitSiteDailyLog(record.id)
      message.success('现场日报已提交')
      modalOpen.value = false
      await fetchData()
    },
  })
}

async function fetchFiles(id: string) {
  filesLoading.value = true
  try { files.value = await listFiles(SITE_DAILY_LOG, id) }
  catch { files.value = []; message.error('附件列表加载失败') }
  finally { filesLoading.value = false }
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
  filter.projectId = undefined; filter.startDate = undefined; filter.endDate = undefined; filter.status = undefined
  pageNo.value = 1
  fetchData()
}

onMounted(() => { referenceStore.fetchProjects(); fetchData() })
</script>

<template>
  <div class="lg-page site-daily-page">
    <div class="lg-page-head"><div><h1>现场日报</h1><p>按项目和日期沉淀施工内容、问题与次日计划。</p></div><a-button v-if="canEdit" type="primary" @click="openCreate">新建日报</a-button></div>
    <div class="lg-search-bar">
      <a-select v-model:value="filter.projectId" placeholder="全部项目" allow-clear style="width:220px"><a-select-option v-for="project in projects" :key="project.id" :value="project.id">{{ project.projectName }}</a-select-option></a-select>
      <a-date-picker v-model:value="filter.startDate" value-format="YYYY-MM-DD" placeholder="开始日期" />
      <a-date-picker v-model:value="filter.endDate" value-format="YYYY-MM-DD" placeholder="结束日期" />
      <a-select v-model:value="filter.status" placeholder="全部状态" allow-clear style="width:130px"><a-select-option value="DRAFT">草稿</a-select-option><a-select-option value="SUBMITTED">已提交</a-select-option></a-select>
      <a-button type="primary" @click="fetchData">查询</a-button><a-button @click="resetFilters">重置</a-button>
    </div>
    <a-result v-if="listError" status="error" title="现场日报加载失败" :sub-title="listError"><template #extra><a-button type="primary" @click="fetchData">重试</a-button></template></a-result>
    <a-empty v-else-if="hasLoaded && !loading && !records.length" description="暂无现场日报" />
    <a-table v-else :loading="loading" :columns="columns" :data-source="records" :pagination="false" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'"><a-tag :color="record.status === 'DRAFT' ? 'default' : 'success'">{{ record.status === 'DRAFT' ? '草稿' : '已提交' }}</a-tag></template>
        <template v-else-if="column.key === 'action'"><a-space><a-button type="link" @click="openRecord(record)">查看</a-button><a-button v-if="canEdit && record.status === 'DRAFT'" type="link" @click="openRecord(record, true)">编辑</a-button><a-button v-if="canEdit && record.status === 'DRAFT'" type="link" @click="submitRecord(record)">提交</a-button></a-space></template>
      </template>
    </a-table>
    <a-pagination v-model:current="pageNo" v-model:page-size="pageSize" :total="total" show-size-changer @change="fetchData" />

    <a-modal v-model:open="modalOpen" :title="modalMode === 'create' ? '新建现场日报' : modalMode === 'edit' ? '编辑现场日报' : '现场日报详情'" :confirm-loading="saving" :ok-button-props="{ style: { display: modalMode === 'view' ? 'none' : '' } }" @ok="save">
      <a-form layout="vertical" :disabled="modalMode === 'view'">
        <a-form-item label="项目" required><a-select v-model:value="form.projectId"><a-select-option v-for="project in projects" :key="project.id" :value="project.id">{{ project.projectName }}</a-select-option></a-select></a-form-item>
        <a-form-item label="日报日期" required><a-date-picker v-model:value="form.reportDate" value-format="YYYY-MM-DD" style="width:100%" /></a-form-item>
        <a-form-item label="施工内容" required><a-textarea v-model:value="form.constructionContent" :rows="4" /></a-form-item>
        <a-form-item label="问题与延误"><a-textarea v-model:value="form.issuesDelays" :rows="3" /></a-form-item>
        <a-form-item label="次日计划"><a-textarea v-model:value="form.nextDayPlan" :rows="3" /></a-form-item>
      </a-form>
      <section v-if="activeRecord" class="site-daily-files"><strong>附件</strong><input v-if="canEdit && activeRecord.status === 'DRAFT'" type="file" @change="onFileChange" /><a-spin :spinning="filesLoading"><div v-for="file in files" :key="file.id"><a-button type="link" @click="download(file)">{{ file.originalName }}</a-button><a-button v-if="canEdit && activeRecord.status === 'DRAFT'" danger type="link" @click="removeFile(file)">删除</a-button></div><a-empty v-if="!files.length" description="暂无附件" /></a-spin></section>
      <template #footer><a-button @click="modalOpen = false">关闭</a-button><a-button v-if="modalMode !== 'view'" type="primary" :loading="saving" @click="save">保存草稿</a-button><a-button v-if="canEdit && activeRecord?.status === 'DRAFT'" type="primary" @click="submitRecord(activeRecord)">提交定稿</a-button></template>
    </a-modal>
  </div>
</template>

<style scoped>
.site-daily-page { display: grid; gap: 16px; }
.lg-page-head { display:flex; justify-content:space-between; align-items:center; }
.lg-page-head h1 { margin:0; }.lg-page-head p { margin:6px 0 0; color:var(--text-secondary); }
.site-daily-files { display:grid; gap:8px; margin-top:16px; }
</style>
