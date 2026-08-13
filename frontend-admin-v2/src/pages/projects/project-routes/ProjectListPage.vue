<script setup lang="ts">
import type {
  DictionaryItem,
  ProjectRecord,
  ProjectUpsertCommand,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  V2ActionMenu,
  V2Badge,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  showToast,
  useToastMessage,
} from '@/components'
import { formatAmount } from '@/shared/display'
import { isApiClientError } from '@/services/request'
import {
  archiveProject,
  createProject,
  deleteProject,
  loadProject,
  loadProjectDictionary,
  loadProjectPage,
  submitProject,
} from '@/services/projects'
import { useSessionStore } from '@/stores/session'
import { cleanProjectCommand, emptyProjectCommand, isSuperAdmin } from '../model'
import ProjectForm from '../ProjectForm.vue'
import {
  approvalStatus,
  approvalStatusLabel,
  approvalStatusTone,
  dictionaryLabel,
  dictionaryOptions,
} from './model'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = useToastMessage()
const projects = ref<ProjectRecord[]>([])
const total = ref(0)
const projectTypes = ref<DictionaryItem[]>([])
const projectStatuses = ref<DictionaryItem[]>([])
const createOpen = ref(false)
const filter = reactive({ keyword: '', projectType: '', status: '', pageNo: 1, pageSize: 10 })
const form = reactive<ProjectUpsertCommand>(emptyProjectCommand())
type ProjectAction = 'archive' | 'submit' | 'delete'
type PendingProjectAction = { action: ProjectAction; target: ProjectRecord }
const pendingConfirmation = ref<PendingProjectAction | null>(null)
let requestId = 0
let controller: AbortController | null = null

watch(errorMessage, (value) => {
  if (value) showToast('error', '操作未完成', value)
})

const can = (code: string) => session.hasPermission(code)
const contextProjectId = computed(() =>
  typeof route.query.projectId === 'string' ? route.query.projectId.trim() : '',
)
const canDeleteProject = computed(() => isSuperAdmin(session.roles))
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / filter.pageSize)))
const typeOptions = computed(() => dictionaryOptions(projectTypes.value))
const statusOptions = computed(() => dictionaryOptions(projectStatuses.value))
const canSubmitProject = (item: ProjectRecord) =>
  can('project:submit') && ['DRAFT', 'REJECTED'].includes(approvalStatus(item.approvalStatus))
const canArchiveProject = (item: ProjectRecord) => can('project:edit') && item.status === 'CLOSED'
const hasMoreActions = (item: ProjectRecord) =>
  can('project:member:list') ||
  can('project:edit') ||
  canSubmitProject(item) ||
  canArchiveProject(item) ||
  canDeleteProject.value
const confirmationCopy = computed(() => {
  const pending = pendingConfirmation.value
  if (!pending) return { title: '', description: '', confirmText: '确认', danger: false }
  if (pending.action === 'delete')
    return {
      title: '删除项目',
      description: `“${pending.target.projectName}”将被永久删除，此操作无法撤销。`,
      confirmText: '永久删除',
      danger: true,
    }
  if (pending.action === 'archive')
    return {
      title: '归档项目',
      description: `确认归档“${pending.target.projectName}”？归档前请确认项目已完成收口。`,
      confirmText: '确认归档',
      danger: false,
    }
  return {
    title: '提交项目审批',
    description: `确认将“${pending.target.projectName}”提交审批？`,
    confirmText: '确认提交',
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

function hydrateQuery() {
  filter.keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  filter.projectType = typeof route.query.projectType === 'string' ? route.query.projectType : ''
  filter.status = typeof route.query.status === 'string' ? route.query.status : ''
  const page = Number(route.query.pageNo)
  filter.pageNo = Number.isInteger(page) && page > 0 ? page : 1
}

async function setQuery(): Promise<boolean> {
  const location = {
    query: {
      ...(typeof route.query.projectId === 'string' ? { projectId: route.query.projectId } : {}),
      ...(typeof route.query.period === 'string' ? { period: route.query.period } : {}),
      ...(filter.keyword ? { keyword: filter.keyword } : {}),
      ...(filter.projectType ? { projectType: filter.projectType } : {}),
      ...(filter.status ? { status: filter.status } : {}),
      ...(filter.pageNo > 1 ? { pageNo: String(filter.pageNo) } : {}),
    },
    hash: route.hash,
  }
  if (router.resolve(location).fullPath === route.fullPath) return false
  await router.replace(location)
  return true
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
    hydrateQuery()
    if (contextProjectId.value) {
      const current = await loadProject(contextProjectId.value, nextController.signal)
      if (active !== requestId) return false
      const keyword = filter.keyword.trim().toLocaleLowerCase()
      const matches =
        (!keyword ||
          current.projectCode.toLocaleLowerCase().includes(keyword) ||
          current.projectName.toLocaleLowerCase().includes(keyword)) &&
        (!filter.projectType || current.projectType === filter.projectType) &&
        (!filter.status || current.status === filter.status)
      projects.value = matches ? [current] : []
      total.value = projects.value.length
    } else {
      const page = await loadProjectPage({ ...filter }, nextController.signal)
      if (active !== requestId) return false
      projects.value = page.records
      total.value = page.total
    }
    return true
  } catch (error) {
    if (!nextController.signal.aborted && active === requestId) {
      errorMessage.value = message(error, '项目数据加载失败')
    }
    return false
  } finally {
    if (active === requestId) loading.value = false
  }
}

async function refreshProjects() {
  if (await load()) showToast('success', '刷新完成', '项目台账已刷新。')
}

function openCreate() {
  Object.assign(form, emptyProjectCommand())
  createOpen.value = true
  resetNotices()
}

async function saveProject() {
  const command = cleanProjectCommand(form)
  if (!command.projectName || !command.projectType) {
    errorMessage.value = '项目名称和项目类型不能为空'
    return
  }
  saving.value = true
  resetNotices()
  try {
    await createProject(command)
    createOpen.value = false
    await load(true)
    successMessage.value = '项目已创建。'
  } catch (error) {
    errorMessage.value = message(error, '项目保存失败')
    await load(true)
  } finally {
    saving.value = false
  }
}

function requestProjectAction(action: ProjectAction, target: ProjectRecord) {
  pendingConfirmation.value = { action, target }
}

async function act(pending: PendingProjectAction) {
  saving.value = true
  resetNotices()
  try {
    if (pending.action === 'archive') await archiveProject(pending.target.id)
    if (pending.action === 'submit') await submitProject(pending.target.id)
    if (pending.action === 'delete') await deleteProject(pending.target.id)
    successMessage.value = '操作成功。'
    await load(true)
  } catch (error) {
    errorMessage.value = message(error, '项目操作失败')
    await load(true)
  } finally {
    saving.value = false
  }
}

function closeConfirmation() {
  if (!saving.value) pendingConfirmation.value = null
}

async function confirmPendingAction() {
  const pending = pendingConfirmation.value
  if (!pending || saving.value) return
  await act(pending)
  pendingConfirmation.value = null
}

async function search() {
  filter.pageNo = 1
  if (!(await setQuery())) await load()
}

function applySelectFilter(key: 'projectType' | 'status', value: string) {
  filter[key] = value
  void search()
}

async function changePage(next: number) {
  if (next < 1 || next > pageCount.value || next === filter.pageNo) return
  filter.pageNo = next
  if (!(await setQuery())) await load()
}

function go(path: string) {
  void router.push({ path, query: route.query, hash: route.hash })
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

    <template v-else>
      <V2Card
        class="project-page__toolbar-card"
        title="项目台账"
        title-id="project-title"
        :heading-level="1"
      >
        <template #actions>
          <form class="project-page__filters" @submit.prevent="search">
            <V2Input
              v-model="filter.keyword"
              type="search"
              label="关键词"
              hide-label
              placeholder="项目编号或名称"
            />
            <V2Select
              :model-value="filter.projectType"
              label="项目类型"
              hide-label
              :options="typeOptions"
              allow-empty
              placeholder="全部类型"
              @update:model-value="applySelectFilter('projectType', $event)"
            />
            <V2Select
              :model-value="filter.status"
              label="项目状态"
              hide-label
              :options="statusOptions"
              allow-empty
              placeholder="全部状态"
              @update:model-value="applySelectFilter('status', $event)"
            />
            <V2Button type="submit" size="small">查询</V2Button>
            <V2Button type="button" size="small" variant="ghost" @click="refreshProjects">
              刷新
            </V2Button>
            <V2Button v-if="can('project:add')" type="button" size="small" @click="openCreate">
              新建项目
            </V2Button>
          </form>
        </template>
      </V2Card>
      <V2PageState
        v-if="!projects.length && !errorMessage"
        kind="empty"
        title="没有可见项目"
        description="调整查询条件，或联系管理员核对项目范围。"
        :heading-level="2"
      />
      <V2Card v-else>
        <div class="project-page__table-wrap" role="region" aria-label="项目台账" tabindex="0">
          <table class="project-page__table v2-table--top">
            <caption class="v2-visually-hidden">
              项目台账
            </caption>
            <thead>
              <tr>
                <th>项目编号</th>
                <th>项目名称</th>
                <th>项目类型</th>
                <th>项目状态</th>
                <th>审批状态</th>
                <th>合同额</th>
                <th class="v2-table-cell--actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in projects" :key="item.id">
                <th scope="row" class="project-page__primary">
                  <V2Button
                    size="small"
                    variant="ghost"
                    class="v2-table__record-link"
                    @click="go(`/project/${item.id}/overview`)"
                  >
                    {{ item.projectCode }}
                  </V2Button>
                </th>
                <td>{{ item.projectName }}</td>
                <td>{{ dictionaryLabel(projectTypes, item.projectType) }}</td>
                <td>
                  <V2Badge tone="info">{{ dictionaryLabel(projectStatuses, item.status) }}</V2Badge>
                </td>
                <td>
                  <V2Badge :tone="approvalStatusTone(item.approvalStatus)">
                    {{ approvalStatusLabel(item.approvalStatus) }}
                  </V2Badge>
                </td>
                <td>{{ formatAmount(item.contractAmount) }}</td>
                <td class="v2-table-cell--actions">
                  <div class="project-page__actions">
                    <V2ActionMenu
                      v-if="hasMoreActions(item)"
                      :label="`${item.projectName}更多操作`"
                      :placement="index >= projects.length - 3 ? 'top-end' : 'bottom-end'"
                    >
                      <V2Button
                        v-if="can('project:member:list')"
                        size="small"
                        variant="ghost"
                        @click="go(`/project/${item.id}/members`)"
                        >成员</V2Button
                      >
                      <V2Button
                        v-if="can('project:edit')"
                        size="small"
                        variant="ghost"
                        @click="go(`/project/${item.id}/edit`)"
                        >编辑</V2Button
                      >
                      <V2Button
                        v-if="canSubmitProject(item)"
                        size="small"
                        variant="ghost"
                        :loading="saving"
                        @click="requestProjectAction('submit', item)"
                        >提交</V2Button
                      >
                      <V2Button
                        v-if="canArchiveProject(item)"
                        size="small"
                        variant="ghost"
                        :loading="saving"
                        @click="requestProjectAction('archive', item)"
                        >归档</V2Button
                      >
                      <V2Button
                        v-if="canDeleteProject"
                        size="small"
                        variant="danger"
                        :loading="saving"
                        @click="requestProjectAction('delete', item)"
                        >删除</V2Button
                      >
                    </V2ActionMenu>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <nav class="project-page__pagination" aria-label="项目台账分页">
            <span>共 {{ total }} 条</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="filter.pageNo <= 1"
              @click="changePage(filter.pageNo - 1)"
              >上一页</V2Button
            >
            <span>第 {{ filter.pageNo }} 页</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="filter.pageNo >= pageCount"
              @click="changePage(filter.pageNo + 1)"
              >下一页</V2Button
            >
          </nav>
        </template>
      </V2Card>
    </template>

    <V2ConfirmDialog
      :open="Boolean(pendingConfirmation)"
      :title="confirmationCopy.title"
      :description="confirmationCopy.description"
      :confirm-text="confirmationCopy.confirmText"
      :danger="confirmationCopy.danger"
      :loading="saving"
      @close="closeConfirmation"
      @confirm="confirmPendingAction"
    />
    <V2Dialog
      v-model:open="createOpen"
      title="新建项目"
      description="项目编号由服务端生成。"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
    >
      <ProjectForm
        class="project-form--dialog"
        :model-value="form"
        :type-options="typeOptions"
        @update:model-value="Object.assign(form, $event)"
      />
      <template #footer>
        <V2Button variant="secondary" @click="createOpen = false">取消</V2Button>
        <V2Button :loading="saving" @click="saveProject">创建</V2Button>
      </template>
    </V2Dialog>
  </section>
</template>

<style scoped src="./project-pages.css"></style>
