<script setup lang="ts">
import type {
  CloseoutOverview,
  DictionaryItem,
  ProjectActivationReadiness,
  ProjectCommencementRecord,
  ProjectOverview,
  ProjectRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BusinessAttachmentPanel,
  V2Badge,
  V2Button,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  showToast,
  useToastMessage,
} from '@/components'
import { dashboardStatusLabel, formatAmount, formatDecimal } from '@/shared/display'
import { loadCloseoutOverview } from '@/services/closeout'
import {
  changeProjectStatus,
  loadProject,
  loadProjectActivationReadiness,
  loadProjectCommencement,
  loadProjectDictionary,
  loadProjectOverview,
  saveProjectCommencement,
  submitProjectCommencement,
} from '@/services/projects'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import {
  approvalStatusLabel,
  dictionaryLabel,
  dictionaryOptions,
  projectStageGate,
  readinessLabel,
} from './model'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = useToastMessage()
const project = ref<ProjectRecord | null>(null)
const overview = ref<ProjectOverview | null>(null)
const constructionOverview = ref<CloseoutOverview | null>(null)
const activationReadiness = ref<ProjectActivationReadiness | null>(null)
const commencement = ref<ProjectCommencementRecord | null>(null)
const projectTypes = ref<DictionaryItem[]>([])
const projectStatuses = ref<DictionaryItem[]>([])
const statusOpen = ref(false)
const commencementOpen = ref(false)
const statusForm = reactive({ targetStatus: '', reason: '' })
const commencementForm = reactive({
  plannedStartDate: '',
  basisType: 'COMMENCEMENT_BASIS',
  remark: '',
})
type PendingStatusChange = {
  target: ProjectRecord
  targetStatus: string
  reason: string
}
const pendingConfirmation = ref<PendingStatusChange | null>(null)
let requestId = 0
let controller: AbortController | null = null

watch(errorMessage, (value) => {
  if (value) showToast('error', '操作未完成', value)
})

const projectId = computed(() => String(route.params.projectId ?? ''))
const can = (code: string) => session.hasPermission(code)
const statusOptions = computed(() => dictionaryOptions(projectStatuses.value))
const currentStageGate = computed(() =>
  project.value && constructionOverview.value
    ? projectStageGate(project.value, constructionOverview.value)
    : null,
)
const confirmationCopy = computed(() => {
  const pending = pendingConfirmation.value
  if (!pending) return { title: '', description: '', confirmText: '确认', danger: false }
  return {
    title: '变更项目状态',
    description: `“${pending.target.projectName}”将变更为“${dictionaryLabel(projectStatuses.value, pending.targetStatus)}”。原因：${pending.reason}`,
    confirmText: '确认变更',
    danger: false,
  }
})

function message(error: unknown, fallback: string) {
  return isApiClientError(error) ? error.message : fallback
}

function resetNotices() {
  errorMessage.value = ''
  successMessage.value = ''
}

async function load(preserveNotice = false): Promise<boolean> {
  controller?.abort()
  const nextController = new AbortController()
  controller = nextController
  const active = ++requestId
  loading.value = true
  if (!preserveNotice) resetNotices()
  try {
    const dictionaries = await Promise.all([
      loadProjectDictionary('project_type', nextController.signal),
      loadProjectDictionary('project_status', nextController.signal),
    ])
    if (active !== requestId) return false
    ;[projectTypes.value, projectStatuses.value] = dictionaries
    const current = await loadProject(projectId.value, nextController.signal)
    if (active !== requestId) return false
    project.value = current
    const canReadCommencement = can('project:commencement:query')
    const [projectOverview, closeoutOverview, readiness, commencementRecord] = await Promise.all([
      loadProjectOverview(projectId.value, nextController.signal),
      can('closeout:query')
        ? loadCloseoutOverview(projectId.value, nextController.signal)
        : Promise.resolve(null),
      canReadCommencement
        ? loadProjectActivationReadiness(projectId.value, nextController.signal)
        : Promise.resolve(null),
      canReadCommencement
        ? loadProjectCommencement(projectId.value, nextController.signal)
        : Promise.resolve(null),
    ])
    if (active !== requestId) return false
    overview.value = projectOverview
    constructionOverview.value = closeoutOverview
    activationReadiness.value = readiness
    commencement.value = commencementRecord
    return true
  } catch (error) {
    if (!nextController.signal.aborted && active === requestId) {
      project.value = null
      overview.value = null
      constructionOverview.value = null
      activationReadiness.value = null
      commencement.value = null
      errorMessage.value = message(error, '项目数据加载失败')
    }
    return false
  } finally {
    if (active === requestId) loading.value = false
  }
}

function openStatus() {
  statusForm.targetStatus = ''
  statusForm.reason = ''
  statusOpen.value = true
  resetNotices()
}

function requestStatusChange() {
  if (!project.value || !statusForm.targetStatus || !statusForm.reason.trim()) {
    errorMessage.value = '状态和变更原因不能为空'
    return
  }
  pendingConfirmation.value = {
    target: project.value,
    targetStatus: statusForm.targetStatus,
    reason: statusForm.reason.trim(),
  }
}

async function confirmStatusChange() {
  const pending = pendingConfirmation.value
  if (!pending || saving.value) return
  saving.value = true
  resetNotices()
  try {
    await changeProjectStatus(pending.target.id, {
      targetStatus: pending.targetStatus,
      reason: pending.reason,
    })
    successMessage.value = '操作成功。'
    statusOpen.value = false
    await load(true)
  } catch (error) {
    errorMessage.value = message(error, '项目操作失败')
    await load(true)
  } finally {
    saving.value = false
    pendingConfirmation.value = null
  }
}

function closeConfirmation() {
  if (!saving.value) pendingConfirmation.value = null
}

function openCommencement() {
  Object.assign(commencementForm, {
    plannedStartDate: commencement.value?.plannedStartDate ?? project.value?.plannedStartDate ?? '',
    basisType: commencement.value?.basisType ?? 'COMMENCEMENT_BASIS',
    remark: commencement.value?.remark ?? '',
  })
  commencementOpen.value = true
  resetNotices()
}

async function saveCommencement() {
  if (!commencementForm.plannedStartDate) {
    errorMessage.value = '拟开工日期不能为空'
    return
  }
  saving.value = true
  resetNotices()
  try {
    await saveProjectCommencement(projectId.value, {
      ...(commencement.value ? { version: commencement.value.version } : {}),
      plannedStartDate: commencementForm.plannedStartDate,
      basisType: commencementForm.basisType,
      ...(commencementForm.remark.trim() ? { remark: commencementForm.remark.trim() } : {}),
    })
    commencementOpen.value = false
    successMessage.value = '开工准入已保存，请在附件区上传开工依据。'
    await load(true)
  } catch (error) {
    errorMessage.value = message(error, '开工准入保存失败')
  } finally {
    saving.value = false
  }
}

async function submitCommencement() {
  if (!commencement.value) return
  saving.value = true
  resetNotices()
  try {
    await submitProjectCommencement(projectId.value, commencement.value.version)
    successMessage.value = '开工准入已提交审批。'
    await load(true)
  } catch (error) {
    errorMessage.value = message(error, '开工准入提交失败')
  } finally {
    saving.value = false
  }
}

function go(path: string) {
  void router.push({ path, query: route.query, hash: route.hash })
}

function goConstruction(path: string, bizId?: string) {
  void router.push({ path, query: { projectId: projectId.value, ...(bizId ? { bizId } : {}) } })
}

function goReadinessBlocker(code: string) {
  if (code.startsWith('PROJECT_OWNER_CONTRACT')) return goConstruction('/contract/ledger')
  if (code.startsWith('COST_TARGET')) return goConstruction('/cost-target/index')
  if (code.startsWith('PROJECT_BUDGET')) return goConstruction('/cost-budget')
  if (code.startsWith('PROJECT_WBS')) return goConstruction('/project-schedule')
  openCommencement()
}

watch(
  () => route.fullPath,
  () => void load(),
  { immediate: true },
)
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="project-page" aria-labelledby="project-title">
    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在加载项目数据"
      description="按当前账号和项目范围读取。"
      title-id="project-title"
      :heading-level="1"
    />
    <V2Dialog
      :open="Boolean(project)"
      :title="project?.projectName || '项目详情'"
      description="查看项目台账详情。"
      :close-disabled="saving"
      panel-class="v2-dialog-standard v2-detail-dialog v2-dialog-wide"
      @close="go('/project/list')"
    >
      <div v-if="overview && project" class="project-page__overview-stack">
        <section class="v2-detail-dialog__section project-page__overview-intro">
          <div class="v2-detail-dialog__section-heading"><h3>项目简介</h3></div>
          <dl class="v2-detail-dialog__facts">
            <div>
              <dt>项目名称</dt>
              <dd>{{ project.projectName || '—' }}</dd>
            </div>
            <div>
              <dt>项目类型</dt>
              <dd>{{ dictionaryLabel(projectTypes, project.projectType) || '—' }}</dd>
            </div>
            <div>
              <dt>项目地址</dt>
              <dd>{{ project.projectAddress || '—' }}</dd>
            </div>
            <div>
              <dt>建设单位</dt>
              <dd>{{ project.ownerUnit || '—' }}</dd>
            </div>
            <div>
              <dt>监理单位</dt>
              <dd>{{ project.supervisorUnit || '—' }}</dd>
            </div>
            <div>
              <dt>设计单位</dt>
              <dd>{{ project.designUnit || '—' }}</dd>
            </div>
            <div>
              <dt>合同金额（元）</dt>
              <dd>{{ formatAmount(project.contractAmount) }}</dd>
            </div>
            <div>
              <dt>目标成本（元）</dt>
              <dd>{{ formatAmount(project.targetCost) }}</dd>
            </div>
            <div>
              <dt>计划开工</dt>
              <dd>{{ project.plannedStartDate || '—' }}</dd>
            </div>
            <div>
              <dt>计划完工</dt>
              <dd>{{ project.plannedEndDate || '—' }}</dd>
            </div>
          </dl>
        </section>
        <section class="v2-detail-dialog__section">
          <div class="v2-detail-dialog__section-heading"><h3>合同与成本</h3></div>
          <dl class="v2-detail-dialog__facts project-page__overview-cost-facts">
            <div>
              <dt>合同数</dt>
              <dd>{{ overview.contractCount }}</dd>
            </div>
            <div>
              <dt>合同总额</dt>
              <dd>{{ formatAmount(overview.totalContractAmount) }}</dd>
            </div>
            <div>
              <dt>动态成本</dt>
              <dd>{{ formatAmount(overview.dynamicCost) }}</dd>
            </div>
            <div>
              <dt>已付款</dt>
              <dd>{{ formatAmount(overview.paidAmount) }}</dd>
            </div>
          </dl>
        </section>
        <section v-if="activationReadiness" class="v2-detail-dialog__section">
          <div class="v2-detail-dialog__section-heading">
            <h3>开工准入</h3>
            <V2Badge :tone="activationReadiness.ready ? 'success' : 'warning'">
              {{ activationReadiness.ready ? '准入条件已满足' : '存在阻塞项' }}
            </V2Badge>
          </div>
          <dl class="v2-detail-dialog__facts project-page__overview-cost-facts">
            <div>
              <dt>立项依据</dt>
              <dd>{{ activationReadiness.initiationBasis || '未确认' }}</dd>
            </div>
            <div>
              <dt>业主主合同</dt>
              <dd>{{ activationReadiness.ownerContractCode || '未指定' }}</dd>
            </div>
            <div>
              <dt>正式合同额</dt>
              <dd>{{ formatAmount(activationReadiness.ownerContractAmount) }}</dd>
            </div>
            <div>
              <dt>开工审批</dt>
              <dd>{{ approvalStatusLabel(activationReadiness.commencementStatus) }}</dd>
            </div>
          </dl>
          <div v-if="activationReadiness.blockers.length" class="project-page__stage-gate">
            <p>服务端阻塞项</p>
            <ul>
              <li v-for="code in activationReadiness.blockers" :key="code">
                <V2Button
                  type="button"
                  size="small"
                  variant="ghost"
                  @click="goReadinessBlocker(code)"
                >
                  {{ readinessLabel(code) }}
                </V2Button>
              </li>
            </ul>
          </div>
          <BusinessAttachmentPanel
            v-if="commencement"
            title="开工依据附件"
            business-type="PROJECT_COMMENCEMENT"
            :business-id="commencement.id"
            document-type="COMMENCEMENT_BASIS"
            :can-upload="
              project.status === 'PREPARING' &&
              ['DRAFT', 'REJECTED'].includes(commencement.approvalStatus) &&
              can('project:commencement:edit')
            "
            :can-delete="
              project.status === 'PREPARING' &&
              ['DRAFT', 'REJECTED'].includes(commencement.approvalStatus) &&
              can('project:commencement:edit')
            "
            @changed="load(true)"
          />
          <div class="project-page__actions">
            <V2Button
              v-if="can('project:commencement:add') || can('project:commencement:edit')"
              type="button"
              size="small"
              variant="secondary"
              :disabled="project.status !== 'PREPARING' || saving"
              @click="openCommencement"
              >{{ commencement ? '编辑开工准入' : '创建开工准入' }}</V2Button
            >
            <V2Button
              v-if="
                commencement &&
                can('project:commencement:submit') &&
                ['DRAFT', 'REJECTED'].includes(commencement.approvalStatus)
              "
              type="button"
              size="small"
              :loading="saving"
              @click="submitCommencement"
              >提交开工审批</V2Button
            >
          </div>
        </section>
        <section class="v2-detail-dialog__section">
          <div class="v2-detail-dialog__section-heading"><h3>项目态势</h3></div>
          <dl class="v2-detail-dialog__facts">
            <div>
              <dt>预警数</dt>
              <dd>{{ overview.warningCount }}</dd>
            </div>
            <div>
              <dt>成员数</dt>
              <dd>{{ overview.memberCount }}</dd>
            </div>
          </dl>
        </section>
        <section class="v2-detail-dialog__section">
          <div class="v2-detail-dialog__section-heading">
            <h3>施工主线</h3>
            <V2Badge tone="info">{{
              dictionaryLabel(projectStatuses, project.status) || project.status
            }}</V2Badge>
          </div>
          <p>
            WBS {{ constructionOverview?.wbsReadiness.totalTasks ?? 0 }} 项，未完成
            {{ constructionOverview?.wbsReadiness.incompleteTasks ?? 0 }} 项。
          </p>
          <V2PageState
            v-if="!constructionOverview && !errorMessage"
            kind="empty"
            title="施工事实不可见"
            description="当前账号无收尾查询权限，不能判定阶段门禁。"
            :heading-level="3"
          />
          <div v-else-if="currentStageGate" class="project-page__stage-gate">
            <strong>{{ currentStageGate.label }}</strong>
            <V2Badge :tone="currentStageGate.blockers.length ? 'danger' : 'success'">
              {{
                currentStageGate.blockers.length
                  ? `${currentStageGate.blockers.length} 项阻塞`
                  : '通过'
              }}
            </V2Badge>
            <ul v-if="currentStageGate.blockers.length">
              <li
                v-for="item in currentStageGate.blockers"
                :key="`${item.gateCode}-${item.bizId ?? ''}`"
              >
                {{ item.reason }}
              </li>
            </ul>
          </div>
          <div v-if="constructionOverview?.wbsTasks.length" class="project-page__wbs-list">
            <article v-for="task in constructionOverview.wbsTasks" :key="task.id">
              <span
                ><strong>{{ task.taskCode }}</strong> {{ task.taskName }} ·
                {{ dashboardStatusLabel(task.status) }} ·
                {{ formatDecimal(task.actualProgress) }}%</span
              >
              <V2Button
                size="small"
                variant="ghost"
                @click="goConstruction('/project-schedule', task.id)"
              >
                打开WBS
              </V2Button>
            </article>
          </div>
          <div class="project-page__detail-actions">
            <V2Button size="small" variant="secondary" @click="goConstruction('/project-schedule')"
              >WBS计划</V2Button
            >
            <V2Button size="small" variant="secondary" @click="goConstruction('/site/daily-log')"
              >施工日报</V2Button
            >
            <V2Button size="small" variant="secondary" @click="goConstruction('/quality-safety')"
              >质量</V2Button
            >
            <V2Button
              size="small"
              variant="secondary"
              @click="goConstruction('/inventory/material-requisition')"
              >材料领用</V2Button
            >
            <V2Button size="small" variant="secondary" @click="goConstruction('/subcontract/task')"
              >分包</V2Button
            >
            <V2Button
              size="small"
              variant="secondary"
              @click="goConstruction('/production-measurement')"
              >产值</V2Button
            >
            <V2Button size="small" variant="secondary" @click="goConstruction('/cost/summary')"
              >成本</V2Button
            >
            <V2Button size="small" @click="goConstruction('/project-closeout')">阶段门禁</V2Button>
          </div>
        </section>
      </div>
      <template #footer>
        <V2Button type="button" variant="secondary" :disabled="saving" @click="go('/project/list')"
          >关闭</V2Button
        >
        <V2Button
          v-if="can('project:status')"
          type="button"
          variant="secondary"
          :disabled="saving"
          @click="openStatus"
          >变更状态</V2Button
        >
        <V2Button
          v-if="can('project:member:list')"
          type="button"
          variant="secondary"
          :disabled="saving"
          @click="go(`/project/${project?.id}/members`)"
          >成员</V2Button
        >
        <V2Button
          v-if="can('project:edit')"
          type="button"
          variant="secondary"
          :disabled="saving"
          @click="go(`/project/${project?.id}/edit`)"
          >编辑</V2Button
        >
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="statusOpen"
      title="变更项目状态"
      description="选择目标状态并填写原因。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
    >
      <form
        id="project-status-form"
        class="project-page__filters"
        @submit.prevent="requestStatusChange"
      >
        <V2Select
          v-model="statusForm.targetStatus"
          label="目标状态"
          :options="statusOptions"
          required
        />
        <V2Input v-model="statusForm.reason" label="变更原因" required />
      </form>
      <template #footer>
        <V2Button type="button" variant="secondary" :disabled="saving" @click="statusOpen = false"
          >取消</V2Button
        >
        <V2Button type="submit" form="project-status-form" :loading="saving">确认变更</V2Button>
      </template>
    </V2Dialog>
    <V2PageState
      v-if="!loading && !project"
      kind="error"
      title="项目不可访问"
      description="项目不存在、超出当前账号范围，或请求被拒绝。"
      title-id="project-title"
      :heading-level="1"
    >
      <template #actions><V2Button variant="secondary" @click="load()">重试</V2Button></template>
    </V2PageState>
    <V2ConfirmDialog
      :open="Boolean(pendingConfirmation)"
      :title="confirmationCopy.title"
      :description="confirmationCopy.description"
      :confirm-text="confirmationCopy.confirmText"
      :danger="confirmationCopy.danger"
      :loading="saving"
      @close="closeConfirmation"
      @confirm="confirmStatusChange"
    />
    <V2Dialog
      v-model:open="commencementOpen"
      :title="commencement ? '编辑开工准入' : '创建开工准入'"
      description="金额、合同、预算、WBS和阻塞项均以服务端复核结果为准。"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
    >
      <form
        class="project-page__form project-page__form--dialog"
        @submit.prevent="saveCommencement"
      >
        <label
          >拟开工日期<input v-model="commencementForm.plannedStartDate" type="date" required
        /></label>
        <V2Input v-model="commencementForm.remark" label="备注" />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="commencementOpen = false"
          >取消</V2Button
        >
        <V2Button :loading="saving" @click="saveCommencement">保存</V2Button>
      </template>
    </V2Dialog>
  </section>
</template>

<style scoped src="./project-pages.css"></style>
