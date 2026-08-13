<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import {
  V2Button,
  V2Card,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Pagination,
  V2Select,
  V2Stack,
  showToast,
} from '@/components'
import {
  loadProjectScopes,
  saveProjectScope,
  type ProjectScopeRecord,
} from '@/services/cost-subject'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { enabledOptions, pageSlice } from './model'
import './styles.css'

const session = useSessionStore()
const pageSize = 10
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const scopeProjectId = ref('')
const scopes = ref<ProjectScopeRecord[]>([])
const scopePageNo = ref(1)
const scopeDialog = ref(false)
const scopeForm = reactive({
  costSubjectId: '',
  enabled: 'true',
  effectiveFrom: '',
  effectiveTo: '',
  remark: '',
})

const canScopeEdit = computed(() => session.hasAdminOrPermission('cost:subject:scope:edit'))
const pagedScopes = computed(() => pageSlice(scopes.value, scopePageNo.value))

function messageOf(value: unknown): string {
  return isApiClientError(value) ? value.message : '请求失败，请稍后重试'
}

async function queryScopes(): Promise<void> {
  if (!scopeProjectId.value.trim()) {
    showToast('warning', '项目缺失', '请输入项目标识。')
    return
  }
  loading.value = true
  scopePageNo.value = 1
  error.value = ''
  try {
    scopes.value = await loadProjectScopes(scopeProjectId.value)
  } catch (value) {
    scopes.value = []
    error.value = messageOf(value)
  } finally {
    loading.value = false
  }
}

function editScope(record?: ProjectScopeRecord): void {
  Object.assign(scopeForm, {
    costSubjectId: record?.costSubjectId ?? '',
    enabled: record?.enabled === 0 ? 'false' : 'true',
    effectiveFrom: record?.effectiveFrom ?? '',
    effectiveTo: record?.effectiveTo ?? '',
    remark: '',
  })
  scopeDialog.value = true
}

async function submitScope(): Promise<void> {
  if (!scopeProjectId.value.trim() || !scopeForm.costSubjectId.trim()) {
    showToast('warning', '信息不完整', '项目和末级科目不能为空。')
    return
  }
  saving.value = true
  try {
    await saveProjectScope({
      projectId: scopeProjectId.value.trim(),
      costSubjectId: scopeForm.costSubjectId.trim(),
      enabled: scopeForm.enabled === 'true',
      effectiveFrom: scopeForm.effectiveFrom || null,
      effectiveTo: scopeForm.effectiveTo || null,
      remark: scopeForm.remark.trim(),
    })
    scopeDialog.value = false
    scopes.value = await loadProjectScopes(scopeProjectId.value)
    scopePageNo.value = 1
    showToast('success', '项目范围已保存', '适用范围已刷新。')
  } catch (value) {
    showToast('error', '保存范围失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function refreshScope(): void {
  scopes.value = []
  scopePageNo.value = 1
  error.value = ''
  showToast('success', '已刷新', '当前内容已更新。')
}
</script>

<template>
  <V2Stack class="cost-subject-page" :gap="4">
    <V2Card title="项目适用与目标成本" :heading-level="1">
      <template #actions>
        <form class="v2-page-heading__filters" @submit.prevent="queryScopes">
          <V2Input
            v-model="scopeProjectId"
            label="项目标识"
            hide-label
            placeholder="项目标识"
            required
          />
          <V2Button type="submit" size="small">查询</V2Button>
          <V2Button
            v-if="canScopeEdit"
            type="button"
            size="small"
            :disabled="!scopeProjectId.trim()"
            @click="editScope()"
          >
            维护范围
          </V2Button>
          <span class="cost-subject-page__hint">
            项目存在范围配置后，目标成本和财务分摊只能使用范围内启用末级科目。
          </span>
        </form>
        <V2Button size="small" variant="secondary" @click="refreshScope">刷新</V2Button>
      </template>
    </V2Card>

    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在读取成本科目事实"
      description="请稍候。"
    />
    <V2PageState v-else-if="error" kind="error" title="成本科目加载失败" :description="error">
      <template #actions><V2Button @click="queryScopes">重试</V2Button></template>
    </V2PageState>
    <V2PageState
      v-else-if="!scopes.length"
      kind="empty"
      title="暂无项目范围结果"
      description="输入项目标识查询；空结果不代表可自行放宽范围。"
    />
    <V2Card v-else title="范围结果">
      <div class="cost-subject-page__table-wrap">
        <table>
          <thead>
            <tr>
              <th>科目编码</th>
              <th>科目名称</th>
              <th>状态</th>
              <th>生效</th>
              <th>失效</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in pagedScopes" :key="record.id">
              <th scope="row">{{ record.subjectCode }}</th>
              <td>{{ record.subjectName }}</td>
              <td>{{ record.enabled === 1 ? '启用' : '停用' }}</td>
              <td>{{ record.effectiveFrom || '—' }}</td>
              <td>{{ record.effectiveTo || '—' }}</td>
              <td>
                <V2Button
                  v-if="canScopeEdit"
                  size="small"
                  variant="secondary"
                  @click="editScope(record)"
                >
                  维护
                </V2Button>
                <span v-else>—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <V2Pagination
          :total="scopes.length"
          :page-no="scopePageNo"
          :page-size="pageSize"
          label="项目范围分页"
          @update:page-no="scopePageNo = $event"
        />
      </template>
    </V2Card>

    <V2Dialog
      :open="scopeDialog"
      title="维护项目科目范围"
      description="启用、日期和末级科目资格由系统校验。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="scopeDialog = false"
    >
      <form id="scope-form" class="cost-subject-page__form" @submit.prevent="submitScope">
        <V2Input :model-value="scopeProjectId" label="项目标识" disabled />
        <V2Input v-model="scopeForm.costSubjectId" label="末级科目标识" required />
        <V2Select v-model="scopeForm.enabled" :options="enabledOptions" label="状态" />
        <V2Input v-model="scopeForm.effectiveFrom" label="生效日期" placeholder="YYYY-MM-DD" />
        <V2Input v-model="scopeForm.effectiveTo" label="失效日期" placeholder="YYYY-MM-DD" />
        <V2Input v-model="scopeForm.remark" label="备注" />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="scopeDialog = false"
          >取消</V2Button
        >
        <V2Button type="submit" form="scope-form" :loading="saving">保存范围</V2Button>
      </template>
    </V2Dialog>
  </V2Stack>
</template>
