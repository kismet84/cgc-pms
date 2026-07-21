<script setup lang="ts">
import type {
  DictionaryItem,
  ProjectMember,
  ProjectMemberCommand,
  ProjectOverview,
  ProjectRecord,
  ProjectUpsertCommand,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  V2Alert,
  V2Badge,
  V2Button,
  V2Card,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
} from '@/components'
import { isApiClientError } from '@/services/request'
import {
  addProjectMember,
  archiveProject,
  changeProjectStatus,
  createProject,
  deleteProject,
  deleteProjectMember,
  loadProject,
  loadProjectDictionary,
  loadProjectMembers,
  loadProjectOverview,
  loadProjectPage,
  loadProjectUsers,
  submitProject,
  updateProject,
  updateProjectMember,
} from '@/services/projects'
import { useSessionStore } from '@/stores/session'
import {
  cleanMemberCommand,
  cleanProjectCommand,
  emptyProjectCommand,
  isSuperAdmin,
  projectCommand,
  PROJECT_ROLE_OPTIONS,
} from './model'
import ProjectForm from './ProjectForm.vue'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const projects = ref<ProjectRecord[]>([])
const total = ref(0)
const project = ref<ProjectRecord | null>(null)
const overview = ref<ProjectOverview | null>(null)
const members = ref<ProjectMember[]>([])
const projectTypes = ref<DictionaryItem[]>([])
const projectStatuses = ref<DictionaryItem[]>([])
const userOptions = ref<Array<{ value: string; label: string }>>([])
const createOpen = ref(false)
const memberOpen = ref(false)
const editingMemberId = ref('')
const filter = reactive({ keyword: '', projectType: '', status: '', pageNo: 1, pageSize: 20 })
const form = reactive<ProjectUpsertCommand>(emptyProjectCommand())
const memberForm = reactive<ProjectMemberCommand>({
  userId: '',
  roleCode: '',
  positionName: '',
  startDate: '',
  endDate: '',
  status: 'ACTIVE',
  remark: '',
})
const statusForm = reactive({ targetStatus: '', reason: '' })
let requestId = 0
let controller: AbortController | null = null

const projectId = computed(() => String(route.params.projectId ?? ''))
const contextProjectId = computed(() =>
  mode.value === 'list' && typeof route.query.projectId === 'string'
    ? route.query.projectId.trim()
    : '',
)
const mode = computed(() =>
  route.path === '/project/list'
    ? 'list'
    : route.path.endsWith('/members')
      ? 'members'
      : route.path.endsWith('/edit')
        ? 'edit'
        : 'overview',
)
const can = (code: string) => session.hasPermission(code)
const canDeleteProject = computed(() => isSuperAdmin(session.roles))
const typeOptions = computed(() =>
  projectTypes.value
    .filter((item) => ['ACTIVE', 'ENABLE'].includes(item.status))
    .map((item) => ({ value: item.dictValue, label: item.dictLabel })),
)
const statusOptions = computed(() =>
  projectStatuses.value
    .filter((item) => ['ACTIVE', 'ENABLE'].includes(item.status))
    .map((item) => ({ value: item.dictValue, label: item.dictLabel })),
)
const roleLabel = (value: string) =>
  PROJECT_ROLE_OPTIONS.find((item) => item.value === value)?.label ?? value
const dictLabel = (items: DictionaryItem[], value: string) =>
  items.find((item) => item.dictValue === value)?.dictLabel ?? value

function message(error: unknown, fallback: string) {
  return isApiClientError(error) ? error.message : fallback
}
function resetNotices() {
  errorMessage.value = ''
  successMessage.value = ''
}
function setQuery() {
  void router.replace({
    query: {
      ...(typeof route.query.projectId === 'string' ? { projectId: route.query.projectId } : {}),
      ...(typeof route.query.period === 'string' ? { period: route.query.period } : {}),
      ...(filter.keyword ? { keyword: filter.keyword } : {}),
      ...(filter.projectType ? { projectType: filter.projectType } : {}),
      ...(filter.status ? { status: filter.status } : {}),
      ...(filter.pageNo > 1 ? { pageNo: String(filter.pageNo) } : {}),
    },
    hash: route.hash,
  })
}
function hydrateQuery() {
  filter.keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  filter.projectType = typeof route.query.projectType === 'string' ? route.query.projectType : ''
  filter.status = typeof route.query.status === 'string' ? route.query.status : ''
  const page = Number(route.query.pageNo)
  filter.pageNo = Number.isInteger(page) && page > 0 ? page : 1
}

async function load(preserveNotice = false) {
  controller?.abort()
  controller = new AbortController()
  const active = ++requestId
  loading.value = true
  if (!preserveNotice) resetNotices()
  try {
    const dictionaries = await Promise.all([
      loadProjectDictionary('project_type', controller.signal),
      loadProjectDictionary('project_status', controller.signal),
    ])
    if (active !== requestId) return
    ;[projectTypes.value, projectStatuses.value] = dictionaries
    if (mode.value === 'list') {
      hydrateQuery()
      if (contextProjectId.value) {
        const current = await loadProject(contextProjectId.value, controller.signal)
        if (active !== requestId) return
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
        const page = await loadProjectPage({ ...filter }, controller.signal)
        if (active !== requestId) return
        projects.value = page.records
        total.value = page.total
      }
    } else {
      const current = await loadProject(projectId.value, controller.signal)
      if (active !== requestId) return
      project.value = current
      if (mode.value === 'overview')
        overview.value = await loadProjectOverview(projectId.value, controller.signal)
      if (mode.value === 'members') {
        const page = await loadProjectMembers(
          projectId.value,
          { pageNo: 1, pageSize: 200 },
          controller.signal,
        )
        if (active !== requestId) return
        members.value = page.records
        if (
          (can('system:user:query') || isSuperAdmin(session.roles)) &&
          !userOptions.value.length
        ) {
          const users = await loadProjectUsers(controller.signal)
          if (active !== requestId) return
          userOptions.value = users.records
            .filter((item) => ['ACTIVE', 'ENABLE'].includes(item.status))
            .map((item) => ({
              value: item.id,
              label: item.realName ? `${item.realName}（${item.username}）` : item.username,
            }))
        }
      }
      if (mode.value === 'edit') Object.assign(form, projectCommand(current))
    }
  } catch (error) {
    if (!controller.signal.aborted && active === requestId) {
      project.value = null
      overview.value = null
      members.value = []
      errorMessage.value = message(error, '项目数据加载失败')
    }
  } finally {
    if (active === requestId) loading.value = false
  }
}

function openCreate() {
  Object.assign(form, emptyProjectCommand())
  createOpen.value = true
  resetNotices()
}
async function saveProject(create: boolean) {
  const command = cleanProjectCommand(form)
  if (!command.projectName || !command.projectType) {
    errorMessage.value = '项目名称和项目类型不能为空'
    return
  }
  saving.value = true
  resetNotices()
  try {
    if (create) await createProject(command)
    else await updateProject(projectId.value, command)
    successMessage.value = create
      ? '项目已创建；列表已按服务端状态重读。'
      : '项目已更新；详情已按服务端状态重读。'
    createOpen.value = false
    await load(true)
  } catch (error) {
    errorMessage.value = message(error, '项目保存失败')
    await load(true)
  } finally {
    saving.value = false
  }
}
async function act(action: 'archive' | 'submit' | 'status' | 'delete', target: ProjectRecord) {
  const prompts = {
    archive: `确认归档“${target.projectName}”？`,
    submit: `确认提交“${target.projectName}”审批？`,
    status: `确认变更“${target.projectName}”状态？`,
    delete: `确认永久删除“${target.projectName}”？`,
  }
  if (!window.confirm(prompts[action])) return
  if (action === 'status' && (!statusForm.targetStatus || !statusForm.reason.trim())) {
    errorMessage.value = '状态和变更原因不能为空'
    return
  }
  saving.value = true
  resetNotices()
  try {
    if (action === 'archive') await archiveProject(target.id)
    if (action === 'submit') await submitProject(target.id)
    if (action === 'status')
      await changeProjectStatus(target.id, {
        targetStatus: statusForm.targetStatus,
        reason: statusForm.reason.trim(),
      })
    if (action === 'delete') await deleteProject(target.id)
    successMessage.value = '操作成功；页面已按服务端权威状态重读。'
    await load(true)
  } catch (error) {
    errorMessage.value = message(error, '项目操作失败')
    await load(true)
  } finally {
    saving.value = false
  }
}
function openMember(member?: ProjectMember) {
  editingMemberId.value = member?.id ?? ''
  Object.assign(memberForm, {
    userId: member?.userId ?? '',
    roleCode: member?.roleCode ?? '',
    positionName: member?.positionName ?? '',
    startDate: member?.startDate ?? '',
    endDate: member?.endDate ?? '',
    status: member?.status ?? 'ACTIVE',
    remark: member?.remark ?? '',
  })
  memberOpen.value = true
  resetNotices()
}
async function saveMember() {
  const command = cleanMemberCommand(memberForm)
  if (!command.userId || !command.roleCode) {
    errorMessage.value = '用户和项目角色不能为空'
    return
  }
  saving.value = true
  resetNotices()
  try {
    if (editingMemberId.value)
      await updateProjectMember(projectId.value, editingMemberId.value, command)
    else await addProjectMember(projectId.value, command)
    memberOpen.value = false
    successMessage.value = '成员已保存；成员列表已按服务端状态重读。'
    await load(true)
  } catch (error) {
    errorMessage.value = message(error, '成员保存失败')
    await load(true)
  } finally {
    saving.value = false
  }
}
async function removeMember(member: ProjectMember) {
  if (!window.confirm(`确认移除成员 ${member.userId}？`)) return
  saving.value = true
  resetNotices()
  try {
    await deleteProjectMember(projectId.value, member.id)
    successMessage.value = '成员已移除；列表已重读。'
    await load(true)
  } catch (error) {
    errorMessage.value = message(error, '成员移除失败')
    await load(true)
  } finally {
    saving.value = false
  }
}
function search() {
  filter.pageNo = 1
  setQuery()
  void load()
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
    <V2Alert v-if="errorMessage" tone="danger" title="请求未完成">{{ errorMessage }}</V2Alert>
    <V2Alert v-if="successMessage" tone="success" title="操作完成">{{ successMessage }}</V2Alert>
    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在加载项目数据"
      description="按当前账号和项目范围读取。"
    />

    <template v-else-if="mode === 'list'">
      <V2Card class="project-page__toolbar-card">
        <template #actions>
          <form class="project-page__filters" @submit.prevent="search">
            <h1 id="project-title" class="sr-only">项目台账</h1>
            <V2Input
              v-model="filter.keyword"
              type="search"
              label="关键词"
              placeholder="项目编号或名称"
            />
            <V2Select
              v-model="filter.projectType"
              label="项目类型"
              :options="typeOptions"
              allow-empty
              placeholder="全部类型"
            />
            <V2Select
              v-model="filter.status"
              label="项目状态"
              :options="statusOptions"
              allow-empty
              placeholder="全部状态"
            />
            <V2Button type="submit">查询</V2Button>
            <V2Button type="button" size="small" variant="ghost" @click="load()">刷新</V2Button>
            <V2Button v-if="can('project:add')" type="button" size="small" @click="openCreate"
              >新建项目</V2Button
            >
          </form>
        </template>
      </V2Card>
      <V2PageState
        v-if="!projects.length"
        kind="empty"
        title="没有可见项目"
        description="调整查询条件，或联系管理员核对项目范围。"
      />
      <div v-else class="project-page__grid">
        <V2Card
          v-for="item in projects"
          :key="item.id"
          :title="item.projectName"
          :subtitle="item.projectCode"
        >
          <div class="project-page__facts">
            <span>{{ dictLabel(projectTypes, item.projectType) }}</span
            ><V2Badge tone="info">{{ dictLabel(projectStatuses, item.status) }}</V2Badge
            ><span>合同额 {{ item.contractAmount || '0' }} 元</span>
          </div>
          <template #footer
            ><div class="project-page__actions">
              <V2Button size="small" variant="secondary" @click="go(`/project/${item.id}/overview`)"
                >总览</V2Button
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
                v-if="can('project:submit')"
                size="small"
                variant="ghost"
                :loading="saving"
                @click="act('submit', item)"
                >提交</V2Button
              >
              <V2Button
                v-if="can('project:edit')"
                size="small"
                variant="ghost"
                :loading="saving"
                @click="act('archive', item)"
                >归档</V2Button
              >
              <V2Button
                v-if="canDeleteProject"
                size="small"
                variant="danger"
                :loading="saving"
                @click="act('delete', item)"
                >删除</V2Button
              >
            </div></template
          >
        </V2Card>
      </div>
    </template>

    <template v-else-if="project">
      <V2Card
        :title="project.projectName"
        :subtitle="`${project.projectCode} · ${dictLabel(projectStatuses, project.status)}`"
      >
        <template #actions
          ><div class="project-page__actions">
            <V2Button size="small" variant="ghost" @click="go('/project/list')">返回台账</V2Button
            ><V2Button
              size="small"
              variant="secondary"
              @click="go(`/project/${project.id}/overview`)"
              >总览</V2Button
            ><V2Button
              v-if="can('project:member:list')"
              size="small"
              variant="secondary"
              @click="go(`/project/${project.id}/members`)"
              >成员</V2Button
            ><V2Button
              v-if="can('project:edit')"
              size="small"
              variant="secondary"
              @click="go(`/project/${project.id}/edit`)"
              >编辑</V2Button
            >
          </div></template
        >
        <h1 id="project-title" class="sr-only">{{ project.projectName }}</h1>
      </V2Card>

      <div v-if="mode === 'overview' && overview" class="project-page__grid">
        <V2Card title="合同与成本"
          ><dl>
            <dt>合同数</dt>
            <dd>{{ overview.contractCount }}</dd>
            <dt>合同总额</dt>
            <dd>{{ overview.totalContractAmount }} 元</dd>
            <dt>动态成本</dt>
            <dd>{{ overview.dynamicCost }} 元</dd>
            <dt>已付款</dt>
            <dd>{{ overview.paidAmount }} 元</dd>
          </dl></V2Card
        >
        <V2Card title="项目态势"
          ><dl>
            <dt>预警数</dt>
            <dd>{{ overview.warningCount }}</dd>
            <dt>成员数</dt>
            <dd>{{ overview.memberCount }}</dd>
            <dt>计划周期</dt>
            <dd>{{ project.plannedStartDate || '—' }} 至 {{ project.plannedEndDate || '—' }}</dd>
            <dt>地址</dt>
            <dd>{{ project.projectAddress || '—' }}</dd>
          </dl></V2Card
        >
        <V2Card
          v-if="can('project:status')"
          title="状态变更"
          subtitle="原因必填；提交后重读服务端状态。"
          ><div class="project-page__filters">
            <V2Select
              v-model="statusForm.targetStatus"
              label="目标状态"
              :options="statusOptions"
            /><V2Input v-model="statusForm.reason" label="变更原因" /><V2Button
              :loading="saving"
              @click="act('status', project)"
              >确认变更</V2Button
            >
          </div></V2Card
        >
      </div>

      <template v-if="mode === 'edit'">
        <V2Card title="编辑项目" subtitle="只提交服务端允许写入的字段。"
          ><ProjectForm
            :model-value="form"
            :type-options="typeOptions"
            @update:model-value="Object.assign(form, $event)"
          /><template #footer
            ><V2Button :loading="saving" @click="saveProject(false)">保存并重读</V2Button></template
          ></V2Card
        >
      </template>

      <template v-if="mode === 'members'">
        <V2Card title="项目成员" :subtitle="`共 ${members.length} 人`"
          ><template #actions
            ><V2Button v-if="can('project:member:add')" size="small" @click="openMember()"
              >添加成员</V2Button
            ></template
          >
          <div class="project-page__members">
            <article v-for="member in members" :key="member.id">
              <div>
                <strong>用户 {{ member.userId }}</strong>
                <p>
                  {{ roleLabel(member.roleCode) }} · {{ member.positionName || '未填写岗位' }} ·
                  {{ member.status }}
                </p>
              </div>
              <div class="project-page__actions">
                <V2Button
                  v-if="can('project:member:edit')"
                  size="small"
                  variant="ghost"
                  @click="openMember(member)"
                  >编辑</V2Button
                ><V2Button
                  v-if="can('project:member:delete')"
                  size="small"
                  variant="danger"
                  @click="removeMember(member)"
                  >移除</V2Button
                >
              </div>
            </article>
          </div>
          <V2PageState
            v-if="!members.length"
            kind="empty"
            title="暂无项目成员"
            description="具备添加权限的账号可维护成员。"
          />
        </V2Card>
      </template>
    </template>
    <V2PageState
      v-else
      kind="error"
      title="项目不可访问"
      description="项目不存在、超出当前账号范围，或请求被拒绝。"
      ><template #actions
        ><V2Button variant="secondary" @click="load()">重试</V2Button></template
      ></V2PageState
    >

    <V2Dialog v-model:open="createOpen" title="新建项目" description="项目编号由服务端生成。"
      ><ProjectForm
        :model-value="form"
        :type-options="typeOptions"
        @update:model-value="Object.assign(form, $event)"
      /><template #footer
        ><V2Button variant="secondary" @click="createOpen = false">取消</V2Button
        ><V2Button :loading="saving" @click="saveProject(true)">创建并重读</V2Button></template
      ></V2Dialog
    >
    <V2Dialog v-model:open="memberOpen" :title="editingMemberId ? '编辑成员' : '添加成员'">
      <form class="project-page__form" @submit.prevent="saveMember">
        <V2Select
          v-if="userOptions.length && !editingMemberId"
          v-model="memberForm.userId"
          label="用户"
          :options="userOptions"
          required
        /><V2Input
          v-else
          v-model="memberForm.userId"
          label="用户 ID"
          :disabled="Boolean(editingMemberId)"
          required
          hint="无用户目录权限时，请输入已确认的用户 ID。"
        /><V2Select
          v-model="memberForm.roleCode"
          label="项目角色"
          :options="PROJECT_ROLE_OPTIONS"
          required
        /><V2Input v-model="memberForm.positionName" label="岗位名称" /><label
          >开始日期<input v-model="memberForm.startDate" type="date" /></label
        ><label>结束日期<input v-model="memberForm.endDate" type="date" /></label>
      </form>
      <template #footer
        ><V2Button variant="secondary" @click="memberOpen = false">取消</V2Button
        ><V2Button :loading="saving" @click="saveMember">保存并重读</V2Button></template
      >
    </V2Dialog>
  </section>
</template>

<style scoped>
.project-page {
  display: grid;
  gap: var(--v2-space-3);
  color: var(--v2-color-text);
  font-size: var(--v2-font-size-13);
}
.project-page__filters,
.project-page__form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--v2-space-3);
  align-items: end;
}
.project-page__toolbar-card :deep(.v2-card__header) {
  display: block;
}
.project-page__toolbar-card :deep(.v2-card__body) {
  display: none;
}
.project-page__toolbar-card .project-page__filters {
  grid-template-columns: minmax(14rem, 2fr) repeat(2, minmax(10rem, 1fr)) repeat(3, auto);
  align-items: center;
}
.project-page__toolbar-card :deep(.v2-field__label) {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}
.project-page__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
}
.project-page__facts,
.project-page__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  align-items: center;
}
.project-page__members {
  display: grid;
  gap: var(--v2-space-2);
}
.project-page__members article {
  display: flex;
  justify-content: space-between;
  gap: var(--v2-space-3);
  padding: var(--v2-space-3);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}
.project-page__members p {
  margin: var(--v2-space-1) 0 0;
  color: var(--v2-color-text-secondary);
}
dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--v2-space-2) var(--v2-space-4);
  margin: 0;
}
dt {
  color: var(--v2-color-text-secondary);
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.project-page__form label {
  display: grid;
  gap: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
}
.project-page__form input {
  min-height: 2.5rem;
  padding: 0 var(--v2-space-3);
  color: var(--v2-color-text);
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}
@media (max-width: 64rem) {
  .project-page__filters,
  .project-page__toolbar-card .project-page__filters,
  .project-page__form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 48rem) {
  .project-page__grid,
  .project-page__filters,
  .project-page__toolbar-card .project-page__filters,
  .project-page__form {
    grid-template-columns: 1fr;
  }
  .project-page__members article {
    flex-direction: column;
  }
}
</style>
