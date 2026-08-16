<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  V2Badge,
  V2Button,
  V2Card,
  V2Cluster,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Pagination,
  V2Select,
  V2Stack,
  showToast,
} from '@/components'
import {
  cancelProjectConfigRequest,
  createProjectConfigRequest,
  loadGovernanceFormOptions,
  loadProjectConfiguration,
  submitProjectConfigRequest,
  type CostSubjectAuditRow,
  type GovernanceFormOptions,
  type ProjectConfigurationRecord,
} from '@/services/cost-subject'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { formatAmount } from '@/shared/display'
import { pageSlice, statusLabel } from './model'
import './styles.css'

const session = useSessionStore()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const projectId = ref('')
const pageNo = ref(1)
const pageSize = 10
const dialog = ref(false)
const options = ref<GovernanceFormOptions>({
  projects: [],
  costSubjects: [],
  rulePlans: [],
  bidCosts: [],
  targetVersions: [],
  financeSources: [],
  pendingClassifications: [],
})
const configuration = ref<ProjectConfigurationRecord | null>(null)
let controller: AbortController | null = null

const form = reactive({ reason: '', lines: [newLine()] })
const canEdit = computed(() => session.hasPermission('cost:project-config:edit'))
const canSubmit = computed(() => session.hasPermission('cost:project-config:submit'))
const closed = computed(() => configuration.value?.project.projectStatus === 'CLOSED')
const projectOptions = computed(() =>
  options.value.projects.map((item) => ({
    value: item.id,
    label: `${item.projectCode} · ${item.projectName}（${statusLabel(item.projectStatus)}）`,
  })),
)
const subjectOptions = computed(() =>
  options.value.costSubjects
    .filter((item) => item.status === 'ENABLE')
    .map((item) => ({ value: item.id, label: `${item.subjectCode} · ${item.subjectName}` })),
)
const pagedSubjects = computed(() =>
  pageSlice(configuration.value?.subjects ?? [], pageNo.value, pageSize),
)
const impactPreview = computed(() =>
  form.lines.flatMap((line) => {
    const subject = configuration.value?.subjects.find(
      (record) => String(record.id) === line.costSubjectId,
    )
    return subject ? [{ ...subject, nextEnabled: line.enabled === 'true' }] : []
  }),
)

function newLine() {
  return {
    costSubjectId: '',
    enabled: 'false',
    effectiveFrom: new Date().toISOString().slice(0, 10),
    effectiveTo: '',
  }
}
function messageOf(value: unknown): string {
  return isApiClientError(value)
    ? value.message
    : value instanceof Error
      ? value.message
      : '请求失败，请稍后重试'
}

async function loadOptions(signal?: AbortSignal): Promise<void> {
  options.value = await loadGovernanceFormOptions(signal)
  if (!projectId.value && options.value.projects.length === 1)
    projectId.value = options.value.projects[0]?.id ?? ''
}
async function loadConfiguration(): Promise<void> {
  if (!projectId.value) {
    configuration.value = null
    return
  }
  loading.value = true
  error.value = ''
  pageNo.value = 1
  try {
    configuration.value = await loadProjectConfiguration(projectId.value)
  } catch (value) {
    configuration.value = null
    error.value = messageOf(value)
  } finally {
    loading.value = false
  }
}
async function loadPage(): Promise<void> {
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  error.value = ''
  try {
    await loadOptions(current.signal)
    if (projectId.value)
      configuration.value = await loadProjectConfiguration(projectId.value, current.signal)
  } catch (value) {
    if (!current.signal.aborted) error.value = messageOf(value)
  } finally {
    if (controller === current) loading.value = false
  }
}

async function refreshPage(): Promise<void> {
  await loadPage()
  if (!error.value) showToast('success', '已刷新', '项目成本配置已更新。')
}

function openDialog(record?: CostSubjectAuditRow): void {
  Object.assign(form, {
    reason: '',
    lines: [
      {
        costSubjectId: String(record?.id ?? ''),
        enabled: Number(record?.enabled ?? 1) === 0 ? 'true' : 'false',
        effectiveFrom: String(record?.effectiveFrom ?? new Date().toISOString().slice(0, 10)),
        effectiveTo: String(record?.effectiveTo ?? ''),
      },
    ],
  })
  dialog.value = true
}
async function saveConfiguration(): Promise<void> {
  if (
    !projectId.value ||
    !form.reason.trim() ||
    form.lines.some((line) => !line.costSubjectId || !line.effectiveFrom)
  ) {
    showToast('warning', '配置不完整', '请选择项目、科目、状态、生效日并填写调整原因。')
    return
  }
  saving.value = true
  try {
    const created = await createProjectConfigRequest({
      projectId: projectId.value,
      reason: form.reason.trim(),
      lines: form.lines.map((line) => ({
        costSubjectId: line.costSubjectId,
        enabled: line.enabled === 'true',
        effectiveFrom: line.effectiveFrom || null,
        effectiveTo: line.effectiveTo || null,
      })),
    })
    dialog.value = false
    await loadConfiguration()
    showToast(
      'success',
      created.status === 'APPLIED' ? '配置已直接生效' : '配置申请已保存',
      created.status === 'APPLIED'
        ? '筹备且无成本事实，系统已记录版本化配置。'
        : '在建或已有成本，须提交财务负责人审批。',
    )
  } catch (value) {
    showToast('error', '保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}
async function submitRequest(record: CostSubjectAuditRow): Promise<void> {
  saving.value = true
  try {
    await submitProjectConfigRequest(String(record.id))
    await loadConfiguration()
    showToast('success', '已提交审批', '财务负责人审批后生效。')
  } catch (value) {
    showToast('error', '提交失败', messageOf(value))
  } finally {
    saving.value = false
  }
}
async function cancelRequest(record: CostSubjectAuditRow): Promise<void> {
  saving.value = true
  try {
    await cancelProjectConfigRequest(String(record.id))
    await loadConfiguration()
    showToast('success', '配置草稿已取消', '项目范围占用已释放。')
  } catch (value) {
    showToast('error', '取消失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

watch(projectId, () => void loadConfiguration())
onMounted(() => void loadPage())
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <V2Stack class="cost-subject-page" :gap="4">
    <V2Card title="项目成本配置" :heading-level="1"
      ><template #actions>
        <V2Cluster>
          <V2Select
            v-model="projectId"
            :options="projectOptions"
            label="项目"
            hide-label
            placeholder="选择项目"
          />
          <V2Button
            v-if="canEdit"
            size="small"
            :disabled="!projectId || closed"
            @click="openDialog()"
            >调整科目范围</V2Button
          >
          <V2Button size="small" variant="secondary" @click="refreshPage">刷新</V2Button>
        </V2Cluster>
      </template></V2Card
    >
    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在读取项目成本配置"
      description="请稍候。"
    />
    <V2PageState v-else-if="error" kind="error" title="加载失败" :description="error"
      ><template #actions><V2Button @click="loadPage">重试</V2Button></template></V2PageState
    >
    <V2PageState
      v-else-if="!projectId"
      kind="empty"
      title="按项目查看配置"
      description="从页头项目选择器选择范围；企业启用末级成本科目会动态继承到项目。"
    />
    <template v-else-if="configuration">
      <V2Card title="配置概览"
        ><template #actions
          ><V2Button
            size="small"
            variant="secondary"
            @click="router.push({ path: '/cost-target/index', query: { projectId } })"
            >进入目标成本</V2Button
          ></template
        >
        <dl class="cost-subject-page__facts">
          <div>
            <dt>项目</dt>
            <dd>
              {{ configuration.project.projectCode }} · {{ configuration.project.projectName }}
            </dd>
          </div>
          <div>
            <dt>项目状态</dt>
            <dd>{{ statusLabel(String(configuration.project.projectStatus)) }}</dd>
          </div>
          <div>
            <dt>主合同</dt>
            <dd>
              {{ configuration.project.mainContractCode || '—' }} ·
              {{ configuration.project.mainContractName || '—' }}
            </dd>
          </div>
          <div>
            <dt>生效目标成本版本</dt>
            <dd>
              {{ configuration.project.targetVersionNo || '—' }} ·
              {{ configuration.project.targetVersionName || '—' }}
            </dd>
          </div>
          <div>
            <dt>目标成本金额</dt>
            <dd>{{ formatAmount(configuration.project.targetAmount ?? 0) }}</dd>
          </div>
          <div>
            <dt>配置规则</dt>
            <dd>企业末级成本科目动态继承；项目仅保存排除项与例外项</dd>
          </div>
        </dl>
        <V2PageState
          v-if="closed"
          kind="empty"
          title="已关闭项目只读"
          description="确需处理请走成本追溯与转入中的关闭后财务调整。"
        />
      </V2Card>
      <V2Card title="项目科目范围"
        ><V2PageState
          v-if="!configuration.subjects.length"
          kind="empty"
          title="暂无成本末级科目"
          description="请先维护企业成本科目体系。"
        />
        <div v-else class="cost-subject-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>科目编码</th>
                <th>名称</th>
                <th>范围状态</th>
                <th>业务状态</th>
                <th>生效区间</th>
                <th>历史事实</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in pagedSubjects" :key="String(record.id)">
                <th>{{ record.subjectCode }}</th>
                <td>{{ record.subjectName }}</td>
                <td>
                  <V2Badge tone="neutral">{{
                    record.scopeState === 'INHERITED'
                      ? '企业继承'
                      : record.scopeState === 'EXCLUDED'
                        ? '项目排除'
                        : '项目例外'
                  }}</V2Badge>
                </td>
                <td>{{ statusLabel(String(record.status)) }}</td>
                <td>{{ record.effectiveFrom || '当前' }} 至 {{ record.effectiveTo || '长期' }}</td>
                <td>{{ record.costFactCount ?? 0 }}</td>
                <td>
                  <V2Button
                    v-if="canEdit && !closed"
                    size="small"
                    variant="secondary"
                    @click="openDialog(record)"
                    >调整</V2Button
                  ><span v-else>—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer
          ><V2Pagination
            :total="configuration.subjects.length"
            :page-no="pageNo"
            :page-size="pageSize"
            label="项目科目分页"
            @update:page-no="pageNo = $event"
        /></template>
      </V2Card>
      <V2Card title="范围调整记录"
        ><V2PageState
          v-if="!configuration.requests.length"
          kind="empty"
          title="暂无调整记录"
          description="企业新增末级科目无需项目重复配置。"
        />
        <div v-else class="cost-subject-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>申请编号</th>
                <th>项目快照</th>
                <th>方式</th>
                <th>状态</th>
                <th>原因</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in configuration.requests" :key="String(record.id)">
                <th>{{ record.requestCode }}</th>
                <td>{{ statusLabel(String(record.projectStatusSnapshot)) }}</td>
                <td>{{ Number(record.directApply) === 1 ? '筹备期直接调整' : '财务审批' }}</td>
                <td>{{ statusLabel(String(record.status)) }}</td>
                <td>{{ record.reason }}</td>
                <td>
                  <V2Cluster
                    ><V2Button
                      v-if="
                        canSubmit &&
                        !closed &&
                        ['DRAFT', 'REJECTED', 'WITHDRAWN'].includes(String(record.status)) &&
                        Number(record.directApply) !== 1
                      "
                      size="small"
                      :loading="saving"
                      @click="submitRequest(record)"
                      >提交审批</V2Button
                    ><V2Button
                      v-if="
                        canEdit && record.status === 'DRAFT' && Number(record.directApply) !== 1
                      "
                      size="small"
                      variant="secondary"
                      :loading="saving"
                      @click="cancelRequest(record)"
                      >取消草稿</V2Button
                    ><span v-if="record.status !== 'DRAFT' && !canSubmit">—</span></V2Cluster
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </V2Card>
    </template>

    <V2Dialog
      :open="dialog"
      title="调整项目成本科目范围"
      description="展示影响快照；在建或已有成本的项目须财务负责人审批。"
      panel-class="v2-dialog-wide"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="dialog = false"
      ><form
        id="project-config-form"
        class="cost-subject-page__form cost-subject-page__form--wide"
        @submit.prevent="saveConfiguration"
      >
        <V2Input v-model="form.reason" label="调整原因" required class="cost-subject-page__span" />
        <section class="cost-subject-page__span">
          <h3>调整影响预览</h3>
          <dl class="cost-subject-page__facts cost-subject-page__facts--impact">
            <div>
              <dt>主合同</dt>
              <dd>{{ configuration?.project.mainContractCode || '—' }}</dd>
            </div>
            <div>
              <dt>主合同金额</dt>
              <dd>{{ formatAmount(configuration?.project.mainContractAmount ?? 0) }}</dd>
            </div>
            <div>
              <dt>目标成本版本</dt>
              <dd>{{ configuration?.project.targetVersionNo || '—' }}</dd>
            </div>
            <div>
              <dt>目标成本金额</dt>
              <dd>{{ formatAmount(configuration?.project.targetAmount ?? 0) }}</dd>
            </div>
            <div>
              <dt>影响口径</dt>
              <dd>目标成本、预算、规则及既有成本事实</dd>
            </div>
          </dl>
          <div v-if="impactPreview.length" class="cost-subject-page__table-wrap">
            <table data-table-identity="contextual">
              <thead>
                <tr>
                  <th>科目编码</th>
                  <th>调整结果</th>
                  <th>目标成本行</th>
                  <th>预算行</th>
                  <th>归集规则</th>
                  <th>历史事实</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="record in impactPreview" :key="String(record.id)">
                  <th>{{ record.subjectCode }} · {{ record.subjectName }}</th>
                  <td>{{ record.nextEnabled ? '启用项目例外' : '排除新业务选择' }}</td>
                  <td>{{ record.targetItemCount ?? 0 }}</td>
                  <td>{{ record.budgetLineCount ?? 0 }}</td>
                  <td>{{ record.ruleCount ?? 0 }}</td>
                  <td>{{ record.costFactCount ?? 0 }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <V2PageState
            v-else
            kind="empty"
            title="选择科目后生成影响预览"
            description="系统只展示所选科目在当前项目中的权威引用数量。"
          />
        </section>
        <fieldset class="cost-subject-page__lines cost-subject-page__span">
          <legend>排除项与例外项</legend>
          <div
            v-for="(line, index) in form.lines"
            :key="index"
            class="cost-subject-page__line-grid"
          >
            <V2Select
              v-model="line.costSubjectId"
              :options="subjectOptions"
              :label="`成本科目 ${index + 1}`"
              required
            /><V2Select
              v-model="line.enabled"
              :options="[
                { value: 'false', label: '排除新业务选择' },
                { value: 'true', label: '启用项目例外' },
              ]"
              label="调整结果"
            /><V2Input v-model="line.effectiveFrom" label="生效日" required /><V2Input
              v-model="line.effectiveTo"
              label="失效日（可空）"
            /><V2Button
              type="button"
              size="small"
              variant="secondary"
              :disabled="form.lines.length === 1"
              @click="form.lines.splice(index, 1)"
              >移除</V2Button
            >
          </div>
          <V2Button
            type="button"
            size="small"
            variant="secondary"
            @click="form.lines.push(newLine())"
            >增加科目</V2Button
          >
        </fieldset>
      </form>
      <template #footer
        ><V2Button variant="secondary" @click="dialog = false">取消</V2Button
        ><V2Button type="submit" form="project-config-form" :loading="saving"
          >保存配置申请</V2Button
        ></template
      ></V2Dialog
    >
  </V2Stack>
</template>
