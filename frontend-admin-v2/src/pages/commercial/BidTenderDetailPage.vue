<script setup lang="ts">
import type {
  BidCostRecord,
  BidDocumentVersionRecord,
  BidStatus,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { V2Alert, V2Badge, V2Button, V2Card, V2PageState, V2Select } from '@/components'
import V2Tabs from '@/components/V2Tabs.vue'
import {
  appendBidDocument,
  finalizeBidDocument,
  loadBidCost,
  loadBidDocuments,
  updateBidCost,
  voidBidDocument,
} from '@/services/commercial'
import { getSiteFileUrl, uploadSiteFile } from '@/services/delivery'
import { dashboardStatusLabel } from '@/pages/dashboard/model'
import { isApiClientError } from '@/services/request'
import { loadEnabledDictDataByCode, type DictDataRecord } from '@/services/system-management'
import { useSessionStore } from '@/stores/session'

type DocumentGroup = 'TENDER' | 'SUBMISSION' | 'RESULT'

const STATUS_FALLBACKS: Record<BidStatus, string> = {
  PREPARING: '注册',
  SUBMITTED: '投标',
  EVALUATING: '评标',
  WON: '中标',
  LOST: '未中标',
  CLOSED: '已关闭',
  WITHDRAWN: '已撤回',
  TERMINATED: '已终止',
}
const DOCUMENT_TYPES: Record<DocumentGroup, Array<{ value: string; label: string }>> = {
  TENDER: [
    { value: 'TENDER_DOCUMENT', label: '招标文件' },
    { value: 'BILL_OF_QUANTITIES', label: '工程量清单' },
    { value: 'TENDER_DRAWING', label: '招标图纸' },
  ],
  SUBMISSION: [
    { value: 'BID_PRICE', label: '投标报价' },
    { value: 'TECHNICAL_DOCUMENT', label: '技术文件' },
    { value: 'BID_DRAWING', label: '投标图纸' },
  ],
  RESULT: [
    { value: 'CANDIDATE_NOTICE', label: '候选人公示' },
    { value: 'AWARD_NOTICE', label: '中标通知书' },
    { value: 'LOSS_NOTICE', label: '未中标通知' },
    { value: 'OBJECTION_REPLY', label: '异议及答复' },
    { value: 'AWARD_CLARIFICATION', label: '中标澄清' },
    { value: 'OTHER_RESULT', label: '其他结果文件' },
  ],
}
const DOCUMENT_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  FINAL: '正式版',
  SUPERSEDED: '已被替代',
  VOID: '已作废',
}

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const bidId = computed(() => String(route.params.id ?? ''))
const record = ref<BidCostRecord | null>(null)
const documents = ref<BidDocumentVersionRecord[]>([])
const bidStatuses = ref<DictDataRecord[]>([])
const documentTypes = ref<DictDataRecord[]>([])
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const activeTab = ref('basic')
const file = ref<File | null>(null)
const documentForm = reactive({
  documentType: 'TENDER_DOCUMENT',
  publishedAt: '',
  receivedAt: '',
  submittedAt: '',
})
const edit = reactive<Record<string, string>>({})
let controller: AbortController | null = null

const canEdit = computed(() => session.hasPermission('bid:edit'))
const canManageFiles = computed(
  () => session.hasPermission('bid:file:manage') && session.hasPermission('bid:status'),
)
const canEditBasic = computed(
  () =>
    canEdit.value &&
    ['PREPARING', 'SUBMITTED', 'EVALUATING'].includes(record.value?.bidStatus ?? ''),
)
const activeGroup = computed<DocumentGroup | null>(() =>
  activeTab.value === 'tender'
    ? 'TENDER'
    : activeTab.value === 'submission'
      ? 'SUBMISSION'
      : activeTab.value === 'result'
        ? 'RESULT'
        : null,
)
const visibleDocuments = computed(() =>
  documents.value.filter((item) => item.documentGroup === activeGroup.value),
)
const documentTypeOptions = computed(
  () =>
    Object.fromEntries(
      Object.entries(DOCUMENT_TYPES).map(([group, options]) => [
        group,
        options.map((option) => ({ ...option, label: documentTypeLabel(option.value) })),
      ]),
    ) as Record<DocumentGroup, Array<{ value: string; label: string }>>,
)
const tabs = computed(() => [
  { value: 'basic', label: '基本信息' },
  {
    value: 'tender',
    label: '招标文件',
    count: documents.value.filter((d) => d.documentGroup === 'TENDER').length,
  },
  {
    value: 'submission',
    label: '投标文件',
    count: documents.value.filter((d) => d.documentGroup === 'SUBMISSION').length,
  },
  {
    value: 'result',
    label: '中标文件',
    count: documents.value.filter((d) => d.documentGroup === 'RESULT').length,
  },
])

function message(value: unknown, fallback: string) {
  return isApiClientError(value) ? value.message : fallback
}
function text(value: unknown) {
  return value == null ? '' : String(value)
}
function documentStatusLabel(value: string): string {
  return DOCUMENT_STATUS_LABELS[value] ?? dashboardStatusLabel(value)
}
function dictionaryLabel(rows: DictDataRecord[], value: string, fallback: string): string {
  return rows.find((item) => item.dictValue === value)?.dictLabel ?? fallback
}
function bidStatusLabel(value: BidStatus): string {
  return dictionaryLabel(bidStatuses.value, value, STATUS_FALLBACKS[value])
}
function documentTypeLabel(value: string): string {
  const fallback = Object.values(DOCUMENT_TYPES)
    .flat()
    .find((item) => item.value === value)?.label
  return dictionaryLabel(documentTypes.value, value, fallback ?? value)
}
function hydrate(value: BidCostRecord) {
  for (const key of [
    'bidProjectName',
    'bidSectionName',
    'tendereeName',
    'agencyName',
    'projectLocation',
    'tenderMethod',
    'sourcePlatform',
    'externalBidNo',
    'sourceUrl',
    'ownerId',
    'documentReceivedDate',
    'bidDeadlineAt',
    'openingAt',
    'bidValidUntil',
    'plannedStartDate',
    'plannedEndDate',
    'ceilingPrice',
    'finalBidPrice',
    'remark',
  ])
    edit[key] = text(value[key as keyof BidCostRecord])
}
async function load() {
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const [bid, docs, nextBidStatuses, nextDocumentTypes] = await Promise.all([
      loadBidCost(bidId.value, controller.signal),
      loadBidDocuments(bidId.value, controller.signal),
      loadEnabledDictDataByCode('bid_status', controller.signal),
      loadEnabledDictDataByCode('bid_document_type', controller.signal),
    ])
    record.value = bid
    documents.value = docs
    bidStatuses.value = nextBidStatuses
    documentTypes.value = nextDocumentTypes
    hydrate(bid)
  } catch (value) {
    if (!controller.signal.aborted) error.value = message(value, '投标详情加载失败')
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}
async function saveBasic() {
  if (!record.value || busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await updateBidCost(
      record.value.id,
      Object.fromEntries(
        Object.entries(edit).map(([key, value]) => [key, value.trim() || null]),
      ) as never,
    )
    notice.value = '基本信息已保存。'
    await load()
  } catch (value) {
    error.value = message(value, '基本信息保存失败')
  } finally {
    busy.value = false
  }
}
function chooseFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] ?? null
}
function currentLocalDateTime(): string {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}
async function uploadDocument() {
  if (!activeGroup.value || !file.value || busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const uploaded = await uploadSiteFile(
      file.value,
      'BID_COST',
      bidId.value,
      documentForm.documentType,
    )
    const version = await appendBidDocument(bidId.value, {
      documentGroup: activeGroup.value,
      documentType: documentForm.documentType,
      logicalName: file.value.name.slice(0, 200),
      sysFileId: uploaded.id,
      publishedAt: activeGroup.value === 'TENDER' ? null : documentForm.publishedAt || null,
      receivedAt: activeGroup.value === 'TENDER' ? null : documentForm.receivedAt || null,
      submittedAt:
        activeGroup.value === 'SUBMISSION'
          ? documentForm.submittedAt || currentLocalDateTime()
          : null,
    })
    await finalizeBidDocument(bidId.value, version.id)
    file.value = null
    notice.value = '文件已上传，投标状态已自动更新。'
    await load()
  } catch (value) {
    error.value = message(value, '文件版本追加失败')
  } finally {
    busy.value = false
  }
}
async function finalize(item: BidDocumentVersionRecord) {
  busy.value = true
  try {
    await finalizeBidDocument(bidId.value, item.id)
    notice.value = '文件已定版。'
    await load()
  } catch (value) {
    error.value = message(value, '文件定版失败')
  } finally {
    busy.value = false
  }
}
async function voidVersion(item: BidDocumentVersionRecord) {
  const reason = window.prompt('请输入作废原因')?.trim()
  if (!reason) return
  busy.value = true
  try {
    await voidBidDocument(bidId.value, item.id, reason)
    notice.value = '文件版本已作废。'
    await load()
  } catch (value) {
    error.value = message(value, '文件作废失败')
  } finally {
    busy.value = false
  }
}
async function download(item: BidDocumentVersionRecord) {
  window.open(await getSiteFileUrl(item.sysFileId), '_blank', 'noopener')
}
watch(activeGroup, (group) => {
  if (group) documentForm.documentType = DOCUMENT_TYPES[group][0]!.value
})
watch(bidId, () => void load(), { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <div class="bid-detail">
    <V2Alert v-if="error" tone="danger" title="操作未完成">{{ error }}</V2Alert>
    <V2Alert v-if="notice" tone="success" title="已完成">{{ notice }}</V2Alert>
    <V2PageState
      v-if="loading"
      title="正在加载投标详情"
      description="请稍候，正在读取投标资料。"
      kind="loading"
    />
    <template v-else-if="record">
      <V2Card
        class="bid-detail__heading"
        :title="`${record.bidCode} · ${record.bidProjectName}`"
        :heading-level="1"
        leading-action-label="返回"
        @leading-action="router.push('/engineering-tender/records')"
      >
        <template #actions>
          <V2Badge tone="info">{{ bidStatusLabel(record.bidStatus) }}</V2Badge>
        </template>
      </V2Card>
      <V2Tabs v-model="activeTab" :tabs="tabs" id-prefix="bid-detail" aria-label="投标详情分区" />
      <V2Card>
        <section
          v-if="activeTab === 'basic'"
          id="bid-detail-panel-basic"
          role="tabpanel"
          class="bid-detail__form"
        >
          <label
            v-for="field in [
              ['bidProjectName', '工程名称'],
              ['tendereeName', '招标人'],
              ['agencyName', '招标代理'],
              ['projectLocation', '建设地点'],
              ['tenderMethod', '招标方式'],
              ['documentReceivedDate', '获取文件日期', 'date'],
              ['bidDeadlineAt', '投标截止时间', 'datetime-local'],
              ['openingAt', '开标时间', 'datetime-local'],
              ['bidValidUntil', '投标有效期', 'date'],
              ['plannedStartDate', '计划开始日期', 'date'],
              ['plannedEndDate', '计划结束日期', 'date'],
              ['ceilingPrice', '招标控制价'],
              ['finalBidPrice', '最终投标价'],
              ['remark', '备注'],
            ]"
            :key="field[0]"
            >{{ field[1]
            }}<input v-model="edit[field[0]!]" :type="field[2] || 'text'" :disabled="!canEditBasic"
          /></label>
          <V2Button
            v-if="canEditBasic"
            class="bid-detail__save"
            size="small"
            :loading="busy"
            @click="saveBasic"
            >保存基本信息</V2Button
          >
        </section>
        <section v-else :id="`bid-detail-panel-${activeTab}`" role="tabpanel">
          <div v-if="canManageFiles" class="bid-detail__upload">
            <V2Select
              v-model="documentForm.documentType"
              label="文件分类"
              :options="documentTypeOptions[activeGroup!]"
            />
            <label v-if="activeGroup !== 'TENDER'"
              >发布时间<input v-model="documentForm.publishedAt" type="datetime-local"
            /></label>
            <label v-if="activeGroup !== 'TENDER'"
              >接收时间<input v-model="documentForm.receivedAt" type="datetime-local"
            /></label>
            <label v-if="activeGroup === 'SUBMISSION'"
              >递交时间<input v-model="documentForm.submittedAt" type="datetime-local"
            /></label>
            <label>选择文件<input type="file" @change="chooseFile" /></label>
            <V2Button :disabled="!file" :loading="busy" @click="uploadDocument">追加版本</V2Button>
          </div>
          <div class="bid-detail__table-wrap">
            <table>
              <thead>
                <tr>
                  <th>逻辑文件</th>
                  <th>分类</th>
                  <th>版本</th>
                  <th>状态</th>
                  <th>来源</th>
                  <th>发布时间</th>
                  <th>接收/递交时间</th>
                  <th>上传人</th>
                  <th>上传时间</th>
                  <th>替代版本</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in visibleDocuments" :key="item.id">
                  <td>{{ item.logicalName }}</td>
                  <td>{{ documentTypeLabel(item.documentType) }}</td>
                  <td>V{{ item.versionNo }}</td>
                  <td>{{ documentStatusLabel(item.status) }}</td>
                  <td>{{ item.sourceName || item.sourceUrl || '—' }}</td>
                  <td>{{ item.publishedAt || '—' }}</td>
                  <td>{{ item.submittedAt || item.receivedAt || '—' }}</td>
                  <td>{{ item.createdBy || '—' }}</td>
                  <td>{{ item.createdAt || '—' }}</td>
                  <td>{{ item.supersedesId || '—' }}</td>
                  <td class="bid-detail__actions">
                    <V2Button size="small" variant="ghost" @click="download(item)">下载</V2Button
                    ><V2Button
                      v-if="canManageFiles && item.status === 'DRAFT'"
                      size="small"
                      @click="finalize(item)"
                      >定版</V2Button
                    ><V2Button
                      v-if="canManageFiles && item.status === 'DRAFT'"
                      size="small"
                      variant="danger"
                      @click="voidVersion(item)"
                      >作废</V2Button
                    >
                  </td>
                </tr>
                <tr v-if="!visibleDocuments.length">
                  <td colspan="11">暂无文件版本</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </V2Card>
    </template>
  </div>
</template>

<style scoped>
.bid-detail {
  display: grid;
  gap: var(--v2-space-4);
}
.bid-detail__heading {
  min-width: 0;
}
.bid-detail__heading :deep(.v2-card__title-row) {
  gap: 50px;
  align-items: center;
}
.bid-detail__upload {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--v2-space-3);
  align-items: end;
}
.bid-detail__form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--v2-space-3);
}
.bid-detail__save {
  align-self: end;
  justify-self: start;
}
label {
  display: grid;
  gap: 6px;
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-13);
}
input {
  min-height: 38px;
  padding: 0 10px;
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-sm);
  background: var(--v2-color-surface);
  color: var(--v2-color-text);
}
.bid-detail__table-wrap {
  overflow: auto;
  margin-top: var(--v2-space-4);
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  padding: 10px;
  border-bottom: 1px solid var(--v2-color-border);
  text-align: left;
  white-space: nowrap;
}
.bid-detail__actions {
  display: flex;
  gap: 4px;
}
@media (max-width: 40rem) {
  .bid-detail__heading :deep(.v2-card__title-row) {
    gap: var(--v2-space-3);
  }
}
</style>
