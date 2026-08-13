<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  V2Badge,
  V2Button,
  V2Card,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Pagination,
  V2Stack,
  showToast,
} from '@/components'
import {
  activateMappingVersion,
  createAssignmentRule,
  createMappingVersion,
  loadAssignmentRules,
  loadMappingVersions,
  type AssignmentRuleRecord,
  type MappingVersionRecord,
} from '@/services/cost-subject'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { pageSlice, ruleProjectLabel, statusLabel } from './model'
import './styles.css'

const session = useSessionStore()
const pageSize = 10
const loading = ref(false)
const saving = ref(false)
const error = ref('')
let controller: AbortController | null = null

const can = (permission: string) => session.hasAdminOrPermission(permission)
const canMappingEdit = computed(() => can('cost:subject:mapping:edit'))
const canMappingActivate = computed(() => can('cost:subject:mapping:activate'))
const canRuleEdit = computed(() => can('cost:subject:rule:edit'))

const versions = ref<MappingVersionRecord[]>([])
const rules = ref<AssignmentRuleRecord[]>([])
const versionPageNo = ref(1)
const rulePageNo = ref(1)
const mappingDialog = ref(false)
const activationTarget = ref<MappingVersionRecord | null>(null)
const activationApprovalId = ref('')
const ruleDialog = ref(false)
const mappingForm = reactive({
  versionCode: '',
  versionName: '',
  effectiveDate: '',
  remark: '',
  sourceSubjectId: '',
  targetGroupCode: '',
  targetSubjectId: '',
  historicalDisplayName: '',
  mappingReason: '',
})
const ruleForm = reactive({
  ruleCode: '',
  mappingVersionId: '',
  sourceType: '',
  businessCategory: '*',
  projectId: '',
  costSubjectId: '',
  priority: '100',
  effectiveFrom: '',
  effectiveTo: '',
  remark: '',
})

const pagedVersions = computed(() => pageSlice(versions.value, versionPageNo.value))
const pagedRules = computed(() => pageSlice(rules.value, rulePageNo.value))

function messageOf(value: unknown): string {
  return isApiClientError(value) ? value.message : '请求失败，请稍后重试'
}

async function loadRules(signal?: AbortSignal): Promise<void> {
  versionPageNo.value = 1
  rulePageNo.value = 1
  ;[versions.value, rules.value] = await Promise.all([
    loadMappingVersions(signal),
    loadAssignmentRules(signal),
  ])
}

async function saveMapping(): Promise<void> {
  if (
    !mappingForm.versionCode.trim() ||
    !mappingForm.versionName.trim() ||
    !mappingForm.sourceSubjectId.trim() ||
    !mappingForm.targetGroupCode.trim() ||
    !mappingForm.historicalDisplayName.trim()
  ) {
    showToast('warning', '信息不完整', '版本、源科目、归集组和历史展示名称不能为空。')
    return
  }
  saving.value = true
  try {
    const savedId = String(
      await createMappingVersion({
        versionCode: mappingForm.versionCode.trim(),
        versionName: mappingForm.versionName.trim(),
        effectiveDate: mappingForm.effectiveDate || null,
        remark: mappingForm.remark.trim(),
        items: [
          {
            sourceSubjectId: mappingForm.sourceSubjectId.trim(),
            targetGroupCode: mappingForm.targetGroupCode.trim(),
            targetSubjectId: mappingForm.targetSubjectId.trim() || null,
            historicalDisplayName: mappingForm.historicalDisplayName.trim(),
            mappingReason: mappingForm.mappingReason.trim(),
          },
        ],
      }),
    )
    mappingDialog.value = false
    await loadRules()
    if (!versions.value.some((item) => item.id === savedId)) {
      throw new Error('新映射版本未出现在最新列表')
    }
    showToast('success', '映射草稿已创建', '版本列表已刷新。')
  } catch (value) {
    showToast('error', '创建映射失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function confirmActivation(): Promise<void> {
  if (!activationTarget.value || !activationApprovalId.value.trim()) {
    showToast('warning', '审批实例缺失', '必须填写已通过的审批实例标识。')
    return
  }
  saving.value = true
  try {
    await activateMappingVersion(activationTarget.value.id, activationApprovalId.value)
    activationTarget.value = null
    activationApprovalId.value = ''
    await loadRules()
    showToast('success', '映射版本已启用', '规则和版本状态已刷新。')
  } catch (value) {
    showToast('error', '启用失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function saveRule(): Promise<void> {
  if (
    !ruleForm.ruleCode.trim() ||
    !ruleForm.mappingVersionId.trim() ||
    !ruleForm.sourceType.trim() ||
    !ruleForm.costSubjectId.trim()
  ) {
    showToast('warning', '信息不完整', '规则、映射版本、业务来源和目标科目不能为空。')
    return
  }
  const priority = Number(ruleForm.priority)
  if (!Number.isInteger(priority)) {
    showToast('warning', '优先级无效', '优先级必须为整数。')
    return
  }
  saving.value = true
  try {
    const savedId = String(
      await createAssignmentRule({
        ruleCode: ruleForm.ruleCode.trim(),
        mappingVersionId: ruleForm.mappingVersionId.trim(),
        sourceType: ruleForm.sourceType.trim(),
        businessCategory: ruleForm.businessCategory.trim() || '*',
        projectId: ruleForm.projectId.trim() || null,
        costSubjectId: ruleForm.costSubjectId.trim(),
        priority,
        effectiveFrom: ruleForm.effectiveFrom || null,
        effectiveTo: ruleForm.effectiveTo || null,
        remark: ruleForm.remark.trim(),
      }),
    )
    ruleDialog.value = false
    await loadRules()
    if (!rules.value.some((item) => item.id === savedId)) {
      throw new Error('新归集规则未出现在最新列表')
    }
    showToast('success', '归集规则已创建', '规则列表已刷新。')
  } catch (value) {
    showToast('error', '创建规则失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function loadPage(): Promise<void> {
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  error.value = ''
  try {
    await loadRules(current.signal)
  } catch (value) {
    if (!current.signal.aborted) error.value = messageOf(value)
  } finally {
    if (controller === current) loading.value = false
  }
}

async function refreshRules(): Promise<void> {
  await loadPage()
  if (error.value) showToast('error', '刷新失败', error.value)
  else showToast('success', '已刷新', '当前内容已更新。')
}

onMounted(() => void loadPage())
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <V2Stack class="cost-subject-page" :gap="4">
    <V2Card title="归集规则与映射版本" :heading-level="1">
      <template #actions>
        <V2Button size="small" variant="secondary" @click="refreshRules">刷新</V2Button>
      </template>
    </V2Card>

    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在读取成本科目事实"
      description="请稍候。"
    />
    <V2PageState v-else-if="error" kind="error" title="成本科目加载失败" :description="error">
      <template #actions><V2Button @click="loadPage">重试</V2Button></template>
    </V2PageState>

    <template v-else>
      <V2Card title="映射版本">
        <template #actions>
          <V2Button v-if="canMappingEdit" size="small" @click="mappingDialog = true">
            新建映射版本
          </V2Button>
        </template>
        <V2PageState
          v-if="!versions.length"
          kind="empty"
          title="暂无映射版本"
          description="映射必须先保存为草稿，再绑定已通过审批启用。"
        />
        <div v-else class="cost-subject-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>版本</th>
                <th>名称</th>
                <th>映射数</th>
                <th>生效日期</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in pagedVersions" :key="record.id">
                <th scope="row">{{ record.versionCode }}</th>
                <td>{{ record.versionName }}</td>
                <td>{{ record.itemCount }}</td>
                <td>{{ record.effectiveDate || '—' }}</td>
                <td>
                  <V2Badge tone="neutral">{{ statusLabel(record.status) }}</V2Badge>
                </td>
                <td>
                  <V2Button
                    v-if="record.status === 'DRAFT' && canMappingActivate"
                    size="small"
                    variant="secondary"
                    @click="activationTarget = record"
                  >
                    审批后启用
                  </V2Button>
                  <span v-else>—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <V2Pagination
            :total="versions.length"
            :page-no="versionPageNo"
            :page-size="pageSize"
            label="映射版本分页"
            @update:page-no="versionPageNo = $event"
          />
        </template>
      </V2Card>

      <V2Card title="显式归集规则">
        <template #actions>
          <V2Button v-if="canRuleEdit" size="small" @click="ruleDialog = true">新增规则</V2Button>
        </template>
        <V2PageState
          v-if="!rules.length"
          kind="empty"
          title="暂无归集规则"
          description="无规则命中时保持待归类。"
        />
        <div v-else class="cost-subject-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>规则</th>
                <th>来源</th>
                <th>业务分类</th>
                <th>项目</th>
                <th>科目编码</th>
                <th>科目名称</th>
                <th>版本</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in pagedRules" :key="record.id">
                <th scope="row">{{ record.ruleCode }}</th>
                <td>{{ record.sourceType }}</td>
                <td>{{ record.businessCategory }}</td>
                <td>{{ ruleProjectLabel(record) }}</td>
                <td>{{ record.subjectCode }}</td>
                <td>{{ record.subjectName }}</td>
                <td>{{ record.versionCode }}</td>
                <td>
                  <V2Badge tone="neutral">{{ statusLabel(record.status) }}</V2Badge>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <V2Pagination
            :total="rules.length"
            :page-no="rulePageNo"
            :page-size="pageSize"
            label="归集规则分页"
            @update:page-no="rulePageNo = $event"
          />
        </template>
      </V2Card>
    </template>

    <V2Dialog
      :open="mappingDialog"
      title="新建科目映射版本"
      description="本次创建一条映射；后续可继续创建新版本。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="mappingDialog = false"
    >
      <form id="mapping-form" class="cost-subject-page__form" @submit.prevent="saveMapping">
        <V2Input v-model="mappingForm.versionCode" label="版本编码" required />
        <V2Input v-model="mappingForm.versionName" label="版本名称" required />
        <V2Input v-model="mappingForm.effectiveDate" label="生效日期" placeholder="YYYY-MM-DD" />
        <V2Input v-model="mappingForm.sourceSubjectId" label="源科目标识" required />
        <V2Input v-model="mappingForm.targetGroupCode" label="归集组编码" required />
        <V2Input v-model="mappingForm.targetSubjectId" label="目标末级科目标识" />
        <V2Input v-model="mappingForm.historicalDisplayName" label="历史展示名称" required />
        <V2Input v-model="mappingForm.mappingReason" label="映射原因" />
        <V2Input v-model="mappingForm.remark" label="备注" />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="mappingDialog = false"
          >取消</V2Button
        >
        <V2Button type="submit" form="mapping-form" :loading="saving">创建草稿</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="Boolean(activationTarget)"
      title="启用映射版本"
      description="仅绑定已通过且业务匹配的审批实例。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="activationTarget = null"
    >
      <form id="activation-form" @submit.prevent="confirmActivation">
        <V2Input v-model="activationApprovalId" label="审批实例标识" required />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="activationTarget = null"
          >取消</V2Button
        >
        <V2Button type="submit" form="activation-form" :loading="saving">确认启用</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="ruleDialog"
      title="新增显式归集规则"
      description="未命中或同优先级冲突时保持待归类并失败关闭。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="ruleDialog = false"
    >
      <form id="rule-form" class="cost-subject-page__form" @submit.prevent="saveRule">
        <V2Input v-model="ruleForm.ruleCode" label="规则编码" required />
        <V2Input v-model="ruleForm.mappingVersionId" label="映射版本标识" required />
        <V2Input v-model="ruleForm.sourceType" label="业务来源" required />
        <V2Input v-model="ruleForm.businessCategory" label="业务分类" />
        <V2Input v-model="ruleForm.projectId" label="项目标识" hint="留空表示全局规则" />
        <V2Input v-model="ruleForm.costSubjectId" label="目标末级科目标识" required />
        <V2Input v-model="ruleForm.priority" label="优先级" />
        <V2Input v-model="ruleForm.effectiveFrom" label="生效日期" placeholder="YYYY-MM-DD" />
        <V2Input v-model="ruleForm.effectiveTo" label="失效日期" placeholder="YYYY-MM-DD" />
        <V2Input v-model="ruleForm.remark" label="备注" />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="ruleDialog = false">取消</V2Button>
        <V2Button type="submit" form="rule-form" :loading="saving">创建规则</V2Button>
      </template>
    </V2Dialog>
  </V2Stack>
</template>
