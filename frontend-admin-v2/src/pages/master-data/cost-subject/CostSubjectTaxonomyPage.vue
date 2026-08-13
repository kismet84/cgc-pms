<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { formatDecimal } from '@/shared/display'
import {
  V2Button,
  V2Card,
  V2Cluster,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  V2Stack,
  V2StatusToggle,
  showToast,
} from '@/components'
import {
  createCostSubject,
  deleteCostSubject,
  loadCostSubject,
  loadCostSubjectTree,
  toggleCostSubjectStatus,
  updateCostSubject,
  type CostSubjectCommand,
  type CostSubjectRecord,
} from '@/services/cost-subject'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { isGovernedSubject, statusOptions, subjectTypeLabel } from './model'
import './styles.css'

const session = useSessionStore()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
let controller: AbortController | null = null

const can = (permission: string) => session.hasAdminOrPermission(permission)
const canSubjectAdd = computed(() => can('cost:add'))
const canSubjectEdit = computed(() => can('cost:edit'))
const canSubjectDelete = computed(() => can('cost:delete'))

const subjects = ref<CostSubjectRecord[]>([])
const selectedFirstLevelId = ref('')
const selectedSubjectId = ref('')
const standardCostRoot = computed(
  () => subjects.value.find((item) => item.subjectCode === '5401') ?? null,
)
const firstLevelSubjects = computed(() => standardCostRoot.value?.children ?? [])
const selectedFirstLevel = computed(
  () => firstLevelSubjects.value.find((item) => item.id === selectedFirstLevelId.value) ?? null,
)
const secondLevelSubjects = computed(() => selectedFirstLevel.value?.children ?? [])
const selectedSubject = computed(
  () => secondLevelSubjects.value.find((item) => item.id === selectedSubjectId.value) ?? null,
)
const editingGovernedSubject = computed(
  () => subjectMode.value === 'edit' && isGovernedSubject(selectedSubject.value),
)
const subjectDialog = ref(false)
const subjectMode = ref<'create' | 'edit'>('create')
const subjectDeleteTarget = ref<CostSubjectRecord | null>(null)
const subjectStatusTarget = ref<CostSubjectRecord | null>(null)
const subjectForm = reactive({
  parentId: '0',
  subjectCode: '',
  subjectName: '',
  subjectType: 'MATERIAL',
  sortOrder: '0',
  status: 'ENABLE',
})

function messageOf(value: unknown): string {
  return isApiClientError(value) ? value.message : '请求失败，请稍后重试'
}

function normalizeSelection(): void {
  if (!firstLevelSubjects.value.some((item) => item.id === selectedFirstLevelId.value)) {
    selectedFirstLevelId.value = firstLevelSubjects.value[0]?.id ?? ''
  }
  if (!secondLevelSubjects.value.some((item) => item.id === selectedSubjectId.value)) {
    selectedSubjectId.value = secondLevelSubjects.value[0]?.id ?? ''
  }
}

function selectFirstLevel(subject: CostSubjectRecord): void {
  selectedFirstLevelId.value = subject.id
  selectedSubjectId.value = subject.children?.[0]?.id ?? ''
}

function clearSubjectForm(): void {
  Object.assign(subjectForm, {
    parentId: '0',
    subjectCode: '',
    subjectName: '',
    subjectType: 'MATERIAL',
    sortOrder: '0',
    status: 'ENABLE',
  })
}

function openSubjectCreate(parent?: CostSubjectRecord): void {
  subjectMode.value = 'create'
  clearSubjectForm()
  subjectForm.parentId = parent?.id ?? '0'
  subjectDialog.value = true
}

function openSubjectEdit(): void {
  const current = selectedSubject.value
  if (!current) return
  subjectMode.value = 'edit'
  Object.assign(subjectForm, {
    parentId: current.parentId || '0',
    subjectCode: current.subjectCode,
    subjectName: current.subjectName,
    subjectType: current.subjectType,
    sortOrder: String(current.sortOrder ?? 0),
    status: current.status,
  })
  subjectDialog.value = true
}

function subjectCommand(): CostSubjectCommand | null {
  const subjectCode = subjectForm.subjectCode.trim()
  const subjectName = subjectForm.subjectName.trim()
  if (!subjectCode || !subjectName) {
    showToast('warning', '信息不完整', '科目编码和名称不能为空。')
    return null
  }
  const sortOrder = Number(subjectForm.sortOrder)
  if (!Number.isInteger(sortOrder) || sortOrder < 0) {
    showToast('warning', '排序无效', '排序必须为非负整数。')
    return null
  }
  return {
    parentId: subjectForm.parentId || '0',
    subjectCode,
    subjectName,
    subjectType: subjectForm.subjectType.trim() || 'MATERIAL',
    accountCategory: 'COST',
    sortOrder,
    status: subjectForm.status as 'ENABLE' | 'DISABLE',
  }
}

async function loadTaxonomy(signal?: AbortSignal): Promise<void> {
  subjects.value = await loadCostSubjectTree(signal)
  normalizeSelection()
}

async function saveSubject(): Promise<void> {
  const command = subjectCommand()
  if (!command) return
  saving.value = true
  try {
    const currentId = subjectMode.value === 'edit' ? selectedSubjectId.value : ''
    const savedId = currentId || String(await createCostSubject(command))
    if (currentId) await updateCostSubject(currentId, command)
    await loadCostSubject(savedId)
    subjectDialog.value = false
    await loadTaxonomy()
    selectedSubjectId.value = savedId
    showToast('success', '成本科目已保存', '科目树已刷新。')
  } catch (value) {
    showToast('error', '保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function confirmSubjectStatus(): Promise<void> {
  if (!subjectStatusTarget.value || isGovernedSubject(subjectStatusTarget.value)) return
  saving.value = true
  try {
    const id = subjectStatusTarget.value.id
    await toggleCostSubjectStatus(id)
    subjectStatusTarget.value = null
    await loadCostSubject(id)
    await loadTaxonomy()
    showToast('success', '科目状态已更新', '最新状态已刷新。')
  } catch (value) {
    showToast('error', '状态更新失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function confirmSubjectDelete(): Promise<void> {
  if (!subjectDeleteTarget.value) return
  saving.value = true
  try {
    await deleteCostSubject(subjectDeleteTarget.value.id)
    subjectDeleteTarget.value = null
    selectedSubjectId.value = ''
    await loadTaxonomy()
    showToast('success', '成本科目已删除', '科目树已刷新。')
  } catch (value) {
    showToast('error', '删除失败', messageOf(value))
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
    await loadTaxonomy(current.signal)
  } catch (value) {
    if (!current.signal.aborted) error.value = messageOf(value)
  } finally {
    if (controller === current) loading.value = false
  }
}

async function refreshTaxonomy(): Promise<void> {
  await loadPage()
  if (error.value) showToast('error', '刷新失败', error.value)
  else showToast('success', '已刷新', '当前内容已更新。')
}

onMounted(() => void loadPage())
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <V2Stack class="cost-subject-page" :gap="4">
    <V2Card title="成本科目体系" :heading-level="1">
      <template #actions>
        <V2Button size="small" variant="secondary" @click="refreshTaxonomy">刷新</V2Button>
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

    <V2Card v-else>
      <div class="cost-subject-page__columns cost-subject-page__taxonomy">
        <section aria-labelledby="cost-subject-first-level-title">
          <div class="cost-subject-page__section-heading">
            <span>
              <h3 id="cost-subject-first-level-title">1. 一级科目</h3>
              <small>5401.xx · 共 {{ firstLevelSubjects.length }} 个</small>
            </span>
            <V2Button
              v-if="canSubjectAdd && standardCostRoot"
              size="small"
              @click="openSubjectCreate(standardCostRoot)"
            >
              新增一级科目
            </V2Button>
          </div>
          <V2PageState
            v-if="!standardCostRoot"
            kind="empty"
            title="缺少标准成本根科目"
            description="未读取到 5401 标准成本体系。"
          />
          <div class="cost-subject-page__list">
            <div
              v-for="subject in firstLevelSubjects"
              :key="subject.id"
              class="cost-subject-page__list-item"
              :class="{ 'is-selected': selectedFirstLevelId === subject.id }"
              @click="selectFirstLevel(subject)"
            >
              <button
                type="button"
                class="cost-subject-page__select"
                :aria-pressed="selectedFirstLevelId === subject.id"
                @click="selectFirstLevel(subject)"
              >
                <span>
                  <strong>{{ subject.subjectName }}</strong>
                  <small>{{ subject.subjectCode }}</small>
                </span>
              </button>
              <V2StatusToggle
                :enabled="subject.status === 'ENABLE'"
                :disabled="!canSubjectEdit || saving || isGovernedSubject(subject)"
                :aria-label="`${subject.status === 'ENABLE' ? '停用' : '启用'}成本科目 ${subject.subjectName}`"
                @toggle="subjectStatusTarget = subject"
              />
            </div>
          </div>
        </section>

        <section aria-labelledby="cost-subject-second-level-title">
          <div class="cost-subject-page__section-heading">
            <span>
              <h3 id="cost-subject-second-level-title">2. 二级科目</h3>
              <small>5401.xx.xx · 共 {{ secondLevelSubjects.length }} 个</small>
            </span>
            <V2Button
              v-if="
                canSubjectAdd && selectedFirstLevel && selectedFirstLevel.subjectCode !== '5401.03'
              "
              size="small"
              @click="openSubjectCreate(selectedFirstLevel)"
            >
              新增二级科目
            </V2Button>
          </div>
          <V2PageState
            v-if="!selectedFirstLevel"
            kind="empty"
            title="请选择一级科目"
            description="选择后读取所属二级科目。"
          />
          <V2PageState
            v-else-if="!secondLevelSubjects.length"
            kind="empty"
            title="暂无二级科目"
            description="可在当前一级科目下新增。"
          />
          <div v-else class="cost-subject-page__list">
            <div
              v-for="subject in secondLevelSubjects"
              :key="subject.id"
              class="cost-subject-page__list-item"
              :class="{ 'is-selected': selectedSubjectId === subject.id }"
              @click="selectedSubjectId = subject.id"
            >
              <button
                type="button"
                class="cost-subject-page__select"
                :aria-pressed="selectedSubjectId === subject.id"
                @click="selectedSubjectId = subject.id"
              >
                <span>
                  <strong>{{ subject.subjectName }}</strong>
                  <small>
                    {{ subject.subjectCode }} · {{ subjectTypeLabel(subject.subjectType)
                    }}<template v-if="subject.defaultTargetRatio != null">
                      · {{ formatDecimal(subject.defaultTargetRatio) }}%</template
                    >
                  </small>
                </span>
              </button>
              <V2StatusToggle
                :enabled="subject.status === 'ENABLE'"
                :disabled="!canSubjectEdit || saving || isGovernedSubject(subject)"
                :aria-label="`${subject.status === 'ENABLE' ? '停用' : '启用'}成本科目 ${subject.subjectName}`"
                @toggle="subjectStatusTarget = subject"
              />
            </div>
          </div>
        </section>

        <section aria-labelledby="cost-subject-detail-title">
          <div class="cost-subject-page__section-heading">
            <h3 id="cost-subject-detail-title">3. 科目详情</h3>
            <template v-if="isGovernedSubject(selectedSubject)">
              <V2Button v-if="canSubjectEdit" size="small" @click="openSubjectEdit">编辑</V2Button>
              <V2StatusToggle
                :enabled="selectedSubject.status === 'ENABLE'"
                disabled
                :aria-label="`成本科目 ${selectedSubject.subjectName} 状态由系统维护`"
              />
            </template>
            <V2Cluster v-else-if="selectedSubject">
              <V2Button
                v-if="canSubjectAdd"
                size="small"
                variant="secondary"
                @click="openSubjectCreate(selectedSubject)"
              >
                新增子科目
              </V2Button>
              <V2Button v-if="canSubjectEdit" size="small" @click="openSubjectEdit">编辑</V2Button>
              <V2StatusToggle
                :enabled="selectedSubject.status === 'ENABLE'"
                :disabled="!canSubjectEdit || saving"
                :aria-label="`${selectedSubject.status === 'ENABLE' ? '停用' : '启用'}成本科目 ${selectedSubject.subjectName}`"
                @toggle="subjectStatusTarget = selectedSubject"
              />
              <V2Button
                v-if="canSubjectDelete"
                size="small"
                variant="danger"
                @click="subjectDeleteTarget = selectedSubject"
              >
                删除
              </V2Button>
            </V2Cluster>
          </div>
          <dl v-if="selectedSubject" class="cost-subject-page__facts">
            <div>
              <dt>编码</dt>
              <dd>{{ selectedSubject.subjectCode }}</dd>
            </div>
            <div>
              <dt>名称</dt>
              <dd>{{ selectedSubject.subjectName }}</dd>
            </div>
            <div>
              <dt>类型</dt>
              <dd>{{ subjectTypeLabel(selectedSubject.subjectType) }}</dd>
            </div>
            <div>
              <dt>层级</dt>
              <dd>{{ selectedSubject.level }}</dd>
            </div>
            <div>
              <dt>排序</dt>
              <dd>{{ selectedSubject.sortOrder }}</dd>
            </div>
            <div>
              <dt>末级</dt>
              <dd>{{ selectedSubject.children?.length ? '否' : '是' }}</dd>
            </div>
            <div v-if="selectedSubject.defaultTargetRatio != null">
              <dt>默认目标成本比例</dt>
              <dd>{{ formatDecimal(selectedSubject.defaultTargetRatio) }}%</dd>
            </div>
          </dl>
          <V2PageState v-else kind="empty" title="请选择科目" description="选择科目后查看详情。" />
        </section>
      </div>
    </V2Card>

    <V2Dialog
      :open="subjectDialog"
      :title="subjectMode === 'edit' ? '编辑成本科目' : '新增成本科目'"
      description="层级、租户、唯一性和引用保护由系统校验。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="subjectDialog = false"
    >
      <form id="cost-subject-form" class="cost-subject-page__form" @submit.prevent="saveSubject">
        <V2Input
          v-model="subjectForm.subjectCode"
          label="科目编码"
          required
          :disabled="editingGovernedSubject"
        />
        <V2Input v-model="subjectForm.subjectName" label="科目名称" required />
        <V2Input
          v-model="subjectForm.subjectType"
          label="科目类型"
          required
          :disabled="editingGovernedSubject"
        />
        <V2Input
          v-model="subjectForm.parentId"
          label="父科目标识"
          :disabled="editingGovernedSubject"
        />
        <V2Input v-model="subjectForm.sortOrder" label="排序" />
        <V2Select
          v-model="subjectForm.status"
          :options="statusOptions"
          label="状态"
          required
          :disabled="subjectMode === 'edit'"
        />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="subjectDialog = false"
          >取消</V2Button
        >
        <V2Button type="submit" form="cost-subject-form" :loading="saving">保存</V2Button>
      </template>
    </V2Dialog>

    <V2ConfirmDialog
      :open="Boolean(subjectStatusTarget)"
      title="更新成本科目状态"
      :description="
        subjectStatusTarget
          ? `确认${subjectStatusTarget.status === 'ENABLE' ? '停用' : '启用'}“${subjectStatusTarget.subjectName}”？存在引用时系统会拒绝停用。`
          : ''
      "
      :danger="subjectStatusTarget?.status === 'ENABLE'"
      :loading="saving"
      @close="subjectStatusTarget = null"
      @confirm="confirmSubjectStatus"
    />

    <V2ConfirmDialog
      :open="Boolean(subjectDeleteTarget)"
      title="删除成本科目"
      :description="
        subjectDeleteTarget
          ? `确认删除“${subjectDeleteTarget.subjectName}”？子科目或任何业务引用存在时系统会拒绝。`
          : ''
      "
      danger
      :loading="saving"
      @close="subjectDeleteTarget = null"
      @confirm="confirmSubjectDelete"
    />
  </V2Stack>
</template>
