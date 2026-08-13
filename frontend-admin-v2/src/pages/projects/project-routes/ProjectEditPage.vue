<script setup lang="ts">
import type {
  DictionaryItem,
  ProjectRecord,
  ProjectUpsertCommand,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { V2Button, V2Dialog, V2PageState, showToast, useToastMessage } from '@/components'
import { loadProject, loadProjectDictionary, updateProject } from '@/services/projects'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { cleanProjectCommand, emptyProjectCommand, projectCommand } from '../model'
import ProjectForm from '../ProjectForm.vue'
import { dictionaryOptions } from './model'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = useToastMessage()
const project = ref<ProjectRecord | null>(null)
const projectTypes = ref<DictionaryItem[]>([])
const form = reactive<ProjectUpsertCommand>(emptyProjectCommand())
let requestId = 0
let controller: AbortController | null = null

watch(errorMessage, (value) => {
  if (value) showToast('error', '操作未完成', value)
})

const projectId = computed(() => String(route.params.projectId ?? ''))
const can = (code: string) => session.hasPermission(code)
const typeOptions = computed(() =>
  dictionaryOptions(projectTypes.value, project.value?.projectType),
)

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
    const [types, current] = await Promise.all([
      loadProjectDictionary('project_type', nextController.signal),
      loadProject(projectId.value, nextController.signal),
    ])
    if (active !== requestId) return false
    projectTypes.value = types
    project.value = current
    Object.assign(form, projectCommand(current))
    return true
  } catch (error) {
    if (!nextController.signal.aborted && active === requestId) {
      project.value = null
      errorMessage.value = message(error, '项目数据加载失败')
    }
    return false
  } finally {
    if (active === requestId) loading.value = false
  }
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
    await updateProject(projectId.value, command)
    await load(true)
    await router.push({ path: '/project/list', query: route.query, hash: route.hash })
    successMessage.value = '项目已更新。'
  } catch (error) {
    errorMessage.value = message(error, '项目保存失败')
    await load(true)
  } finally {
    saving.value = false
  }
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
    <V2Dialog
      :open="Boolean(project)"
      :title="project?.projectName || '项目详情'"
      description="编辑项目基础资料。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard v2-detail-dialog v2-dialog-wide"
      @close="go('/project/list')"
    >
      <section class="v2-detail-dialog__section">
        <div class="v2-detail-dialog__section-heading"><h3>编辑项目</h3></div>
        <ProjectForm
          :model-value="form"
          :type-options="typeOptions"
          @update:model-value="Object.assign(form, $event)"
        />
      </section>
      <template #footer>
        <V2Button type="button" variant="secondary" :disabled="saving" @click="go('/project/list')"
          >取消</V2Button
        >
        <V2Button
          type="button"
          variant="secondary"
          :disabled="saving"
          @click="go(`/project/${project?.id}/overview`)"
          >总览</V2Button
        >
        <V2Button
          v-if="can('project:member:list')"
          type="button"
          variant="secondary"
          :disabled="saving"
          @click="go(`/project/${project?.id}/members`)"
          >成员</V2Button
        >
        <V2Button type="button" :loading="saving" @click="saveProject">保存</V2Button>
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
  </section>
</template>

<style scoped src="./project-pages.css"></style>
