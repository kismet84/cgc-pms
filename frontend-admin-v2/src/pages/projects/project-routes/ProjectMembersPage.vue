<script setup lang="ts">
import type {
  ProjectMember,
  ProjectMemberCommand,
  ProjectMemberOptions,
  ProjectRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
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
import {
  addProjectMember,
  deleteProjectMember,
  loadProject,
  loadProjectMemberOptions,
  loadProjectMembers,
  updateProjectMember,
} from '@/services/projects'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { cleanMemberCommand, projectRoleLabel, projectRoleOptions } from '../model'
import { memberStatusLabel } from './model'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = useToastMessage()
const project = ref<ProjectRecord | null>(null)
const members = ref<ProjectMember[]>([])
const memberOptions = ref<ProjectMemberOptions>({ roles: [], users: [], usersTruncated: false })
const candidateKeyword = ref('')
const optionsLoading = ref(false)
const memberOpen = ref(false)
const editingMemberId = ref('')
const memberForm = reactive<ProjectMemberCommand>({
  userId: '',
  roleCode: '',
  positionName: '',
  startDate: '',
  endDate: '',
  status: 'ACTIVE',
  remark: '',
})
const pendingRemoval = ref<ProjectMember | null>(null)
let requestId = 0
let controller: AbortController | null = null
let optionsController: AbortController | null = null

watch(errorMessage, (value) => {
  if (value) showToast('error', '操作未完成', value)
})

const projectId = computed(() => String(route.params.projectId ?? ''))
const can = (code: string) => session.hasPermission(code)
const userOptions = computed(() =>
  memberOptions.value.users.map((item) => ({
    value: item.userId,
    label: item.realName ? `${item.realName}（${item.username}）` : item.username,
  })),
)
const memberName = (member: ProjectMember) =>
  member.realName
    ? `${member.realName}（${member.username ?? member.userId}）`
    : (userOptions.value.find((item) => item.value === member.userId)?.label ??
      member.username ??
      '成员姓名缺失')
const roleLabel = (member: ProjectMember) =>
  member.roleName ??
  memberOptions.value.roles.find((item) => item.roleCode === member.roleCode)?.roleName ??
  projectRoleLabel(member.roleCode)
const availableMemberUserOptions = computed(() => {
  const existingUserIds = new Set(members.value.map((member) => member.userId))
  return userOptions.value.filter((option) => !existingUserIds.has(option.value))
})
const memberRoleOptions = computed(() => {
  const user = memberOptions.value.users.find((item) => item.userId === memberForm.userId)
  const allowed = new Set(user?.roleCodes ?? [])
  const options = memberOptions.value.roles
    .filter((item) => allowed.has(item.roleCode))
    .map((item) => ({ value: item.roleCode, label: item.roleName }))
  const current = editingMemberId.value ? memberForm.roleCode : ''
  if (!current || options.some((item) => item.value === current)) return options
  const currentOption = projectRoleOptions(current).find((item) => item.value === current)
  return [
    ...options,
    {
      value: current,
      label: currentOption?.label ?? projectRoleLabel(current),
      disabled: true,
    },
  ]
})
watch(
  () => memberForm.userId,
  () => {
    if (
      !editingMemberId.value &&
      memberForm.roleCode &&
      !memberRoleOptions.value.some((item) => item.value === memberForm.roleCode)
    ) {
      memberForm.roleCode = ''
    }
  },
)
const confirmationCopy = computed(() => ({
  title: '移除项目成员',
  description: pendingRemoval.value
    ? `确认移除成员 ${memberName(pendingRemoval.value)}？该成员将失去当前项目角色。`
    : '',
  confirmText: '确认移除',
  danger: true,
}))

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
    const current = await loadProject(projectId.value, nextController.signal)
    if (active !== requestId) return false
    project.value = current
    const canLoadOptions = can('project:member:add') || can('project:member:edit')
    const [page, options] = await Promise.all([
      loadProjectMembers(projectId.value, { pageNo: 1, pageSize: 200 }, nextController.signal),
      canLoadOptions
        ? loadProjectMemberOptions(projectId.value, {}, nextController.signal)
        : Promise.resolve<ProjectMemberOptions>({ roles: [], users: [], usersTruncated: false }),
    ])
    if (active !== requestId) return false
    members.value = page.records
    memberOptions.value = options
    return true
  } catch (error) {
    if (!nextController.signal.aborted && active === requestId) {
      project.value = null
      members.value = []
      memberOptions.value = { roles: [], users: [], usersTruncated: false }
      errorMessage.value = message(error, '项目数据加载失败')
    }
    return false
  } finally {
    if (active === requestId) loading.value = false
  }
}

async function refreshMemberOptions(includeUserId?: string): Promise<void> {
  optionsController?.abort()
  const nextController = new AbortController()
  optionsController = nextController
  optionsLoading.value = true
  try {
    memberOptions.value = await loadProjectMemberOptions(
      projectId.value,
      {
        keyword: candidateKeyword.value,
        includeUserId,
      },
      nextController.signal,
    )
  } catch (error) {
    if (!nextController.signal.aborted) {
      memberOptions.value = { roles: [], users: [], usersTruncated: false }
      errorMessage.value = message(error, '候选成员加载失败')
    }
  } finally {
    if (optionsController === nextController) optionsLoading.value = false
  }
}

function openMember(member?: ProjectMember) {
  editingMemberId.value = member?.id ?? ''
  candidateKeyword.value = ''
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
  void refreshMemberOptions(member?.userId)
}

function searchMemberCandidates() {
  memberForm.userId = ''
  memberForm.roleCode = ''
  void refreshMemberOptions()
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
    successMessage.value = '成员已保存。'
    await load(true)
  } catch (error) {
    errorMessage.value = message(error, '成员保存失败')
    await load(true)
  } finally {
    saving.value = false
  }
}

function requestMemberRemoval(member: ProjectMember) {
  pendingRemoval.value = member
}

async function confirmMemberRemoval() {
  const member = pendingRemoval.value
  if (!member || saving.value) return
  saving.value = true
  resetNotices()
  try {
    await deleteProjectMember(projectId.value, member.id)
    successMessage.value = '成员已移除。'
    await load(true)
  } catch (error) {
    errorMessage.value = message(error, '成员移除失败')
    await load(true)
  } finally {
    saving.value = false
    pendingRemoval.value = null
  }
}

function closeConfirmation() {
  if (!saving.value) pendingRemoval.value = null
}

function go(path: string) {
  void router.push({ path, query: route.query, hash: route.hash })
}

watch(
  () => route.fullPath,
  () => void load(),
  { immediate: true },
)
onBeforeUnmount(() => {
  controller?.abort()
  optionsController?.abort()
})
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
      description="查看和维护项目成员。"
      :close-disabled="saving"
      panel-class="v2-dialog-standard v2-detail-dialog v2-dialog-wide"
      @close="go('/project/list')"
    >
      <section class="v2-detail-dialog__section">
        <div class="v2-detail-dialog__section-heading">
          <h3>项目成员</h3>
          <V2Badge tone="neutral">共 {{ members.length }} 人</V2Badge>
          <V2Button
            v-if="can('project:member:add')"
            type="button"
            size="small"
            @click="openMember()"
          >
            添加成员
          </V2Button>
        </div>
        <div class="project-page__members">
          <article v-for="member in members" :key="member.id">
            <div>
              <strong>{{ memberName(member) }}</strong>
              <p>
                {{ roleLabel(member) }} · {{ member.positionName || '未填写岗位' }} ·
                {{ memberStatusLabel(member.status) }}
              </p>
            </div>
            <div class="project-page__actions">
              <V2Button
                v-if="can('project:member:edit')"
                type="button"
                size="small"
                variant="ghost"
                @click="openMember(member)"
                >编辑</V2Button
              >
              <V2Button
                v-if="can('project:member:delete')"
                type="button"
                size="small"
                variant="danger"
                :loading="saving"
                @click="requestMemberRemoval(member)"
                >移除</V2Button
              >
            </div>
          </article>
        </div>
        <V2PageState
          v-if="!members.length && !errorMessage"
          kind="empty"
          title="暂无项目成员"
          description="具备添加权限的账号可维护成员。"
          :heading-level="3"
        />
      </section>
      <template #footer>
        <V2Button type="button" variant="secondary" :disabled="saving" @click="go('/project/list')"
          >关闭</V2Button
        >
        <V2Button
          type="button"
          variant="secondary"
          :disabled="saving"
          @click="go(`/project/${project?.id}/overview`)"
          >总览</V2Button
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
      :open="Boolean(pendingRemoval)"
      :title="confirmationCopy.title"
      :description="confirmationCopy.description"
      :confirm-text="confirmationCopy.confirmText"
      :danger="confirmationCopy.danger"
      :loading="saving"
      @close="closeConfirmation"
      @confirm="confirmMemberRemoval"
    />
    <V2Dialog
      v-model:open="memberOpen"
      :title="editingMemberId ? '编辑成员' : '添加成员'"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
    >
      <form class="project-page__form project-page__form--dialog" @submit.prevent="saveMember">
        <template v-if="!editingMemberId">
          <V2Input
            v-model="candidateKeyword"
            label="搜索候选成员"
            hint="按姓名或账号搜索，避免加载无界租户用户列表。"
          />
          <V2Button
            type="button"
            size="small"
            :loading="optionsLoading"
            @click="searchMemberCandidates"
          >
            搜索候选
          </V2Button>
        </template>
        <V2Select
          v-if="!editingMemberId"
          v-model="memberForm.userId"
          label="用户"
          :options="availableMemberUserOptions"
          placeholder="请选择用户"
          :hint="
            memberOptions.usersTruncated ? '结果较多，仅显示前100名；请缩小搜索词。' : undefined
          "
          :disabled="optionsLoading || !availableMemberUserOptions.length"
          required
        />
        <V2Select
          v-model="memberForm.roleCode"
          label="项目角色"
          :options="memberRoleOptions"
          :disabled="optionsLoading || !memberRoleOptions.length"
          required
        />
        <V2Input
          v-model="memberForm.positionName"
          class="project-page__form-span"
          label="岗位名称"
        />
        <label>开始日期<input v-model="memberForm.startDate" type="date" /></label>
        <label>结束日期<input v-model="memberForm.endDate" type="date" /></label>
      </form>
      <template #footer>
        <V2Button variant="secondary" @click="memberOpen = false">取消</V2Button>
        <V2Button :loading="saving" @click="saveMember">保存</V2Button>
      </template>
    </V2Dialog>
  </section>
</template>

<style scoped src="./project-pages.css"></style>
