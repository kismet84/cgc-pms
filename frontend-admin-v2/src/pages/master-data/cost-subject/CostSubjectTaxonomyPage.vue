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
  loadAccountingCatalogOverview,
  loadCostSubject,
  loadCostSubjectTree,
  toggleCostSubjectStatus,
  updateCostSubject,
  type AccountCategory,
  type AccountingCatalogOverview,
  type CostSubjectCommand,
  type CostSubjectRecord,
} from '@/services/cost-subject'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import {
  accountCategoryLabel,
  accountCategoryOptions,
  dimensionRequirementLabel,
  isGovernedSubject,
  statusOptions,
  subjectTypeLabel,
} from './model'
import './styles.css'

type CategoryFilter = 'ALL' | AccountCategory
type FlatSubject = { subject: CostSubjectRecord; depth: number }

const session = useSessionStore()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
let controller: AbortController | null = null

const can = (permission: string) => session.hasAdminOrPermission(permission)
const canSubjectAdd = computed(() => false)
const canSubjectEdit = computed(() => can('cost:edit'))
const canSubjectDelete = computed(() => false)

const subjects = ref<CostSubjectRecord[]>([])
const overview = ref<AccountingCatalogOverview>({
  policies: [],
  carryoverMappings: [],
  legacyReviews: [],
  reportRoutes: [],
})
const categoryFilter = ref<CategoryFilter>('ALL')
const selectedSubjectId = ref('')
const categoryFilters: Array<{ value: CategoryFilter; label: string }> = [
  { value: 'ALL', label: '全部科目' },
  ...accountCategoryOptions,
]

function flatten(items: CostSubjectRecord[], depth = 0): FlatSubject[] {
  return items.flatMap((subject) => [
    { subject, depth },
    ...flatten(subject.children ?? [], depth + 1),
  ])
}

const allSubjects = computed(() =>
  flatten(subjects.value.filter((subject) => subject.accountCategory !== 'ROOT')),
)
const visibleSubjects = computed(() =>
  categoryFilter.value === 'ALL'
    ? allSubjects.value
    : allSubjects.value.filter(({ subject }) => subject.accountCategory === categoryFilter.value),
)
const selectedSubject = computed(
  () =>
    allSubjects.value.find(({ subject }) => subject.id === selectedSubjectId.value)?.subject ??
    null,
)
const selectedPolicy = computed(
  () =>
    overview.value.policies.find((row) => row.subjectCode === selectedSubject.value?.subjectCode) ??
    null,
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
  subjectType: 'GENERAL_LEDGER',
  accountCategory: 'ASSET' as AccountCategory,
  sortOrder: '0',
  status: 'ENABLE',
})

function messageOf(value: unknown): string {
  return isApiClientError(value) ? value.message : '请求失败，请稍后重试'
}

function normalizeSelection(): void {
  if (!visibleSubjects.value.some(({ subject }) => subject.id === selectedSubjectId.value)) {
    selectedSubjectId.value = visibleSubjects.value[0]?.subject.id ?? ''
  }
}

function selectCategory(value: CategoryFilter): void {
  categoryFilter.value = value
  normalizeSelection()
}

function clearSubjectForm(category: AccountCategory): void {
  Object.assign(subjectForm, {
    parentId: '0',
    subjectCode: '',
    subjectName: '',
    subjectType: category === 'COST' ? 'MATERIAL' : 'GENERAL_LEDGER',
    accountCategory: category,
    sortOrder: '0',
    status: 'ENABLE',
  })
}

function openSubjectCreate(parent?: CostSubjectRecord): void {
  const category =
    parent?.accountCategory ?? (categoryFilter.value === 'ALL' ? 'ASSET' : categoryFilter.value)
  subjectMode.value = 'create'
  clearSubjectForm(category as AccountCategory)
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
    accountCategory: current.accountCategory as AccountCategory,
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
    subjectType: subjectForm.subjectType.trim() || 'GENERAL_LEDGER',
    accountCategory: subjectForm.accountCategory,
    sortOrder,
    status: subjectForm.status as 'ENABLE' | 'DISABLE',
  }
}

async function loadTaxonomy(signal?: AbortSignal): Promise<void> {
  ;[subjects.value, overview.value] = await Promise.all([
    loadCostSubjectTree(signal),
    loadAccountingCatalogOverview(signal),
  ])
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
    const saved = await loadCostSubject(savedId)
    subjectDialog.value = false
    categoryFilter.value = 'ALL'
    await loadTaxonomy()
    selectedSubjectId.value = saved.id
    showToast('success', '会计科目已保存', '科目目录已刷新。')
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
    showToast('success', '会计科目已删除', '科目目录已刷新。')
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
    <V2Card title="会计科目" :heading-level="1">
      <template #actions>
        <V2Button size="small" variant="secondary" @click="refreshTaxonomy">刷新</V2Button>
      </template>
    </V2Card>

    <V2Card title="实施口径" :heading-level="2">
      <p class="cost-subject-page__notice">
        固定施工企业总账目录；项目、合同、往来单位、部门、员工使用辅助核算维度，不再拆成科目。预付账款、其他应收款、累计折旧、其他应付款及权益类科目不纳入本轻量化目录。
      </p>
    </V2Card>

    <V2PageState v-if="loading" kind="loading" title="正在读取会计科目" description="请稍候。" />
    <V2PageState v-else-if="error" kind="error" title="会计科目加载失败" :description="error">
      <template #actions><V2Button @click="loadPage">重试</V2Button></template>
    </V2PageState>

    <V2Card v-else>
      <div class="cost-subject-page__columns cost-subject-page__taxonomy">
        <section aria-labelledby="account-category-title">
          <div class="cost-subject-page__section-heading">
            <span>
              <h3 id="account-category-title">1. 科目大类</h3>
              <small>统一目录 · {{ allSubjects.length }} 个</small>
            </span>
            <V2Button v-if="canSubjectAdd" size="small" @click="openSubjectCreate()">
              新增一级科目
            </V2Button>
          </div>
          <div class="cost-subject-page__list">
            <div
              v-for="category in categoryFilters"
              :key="category.value"
              class="cost-subject-page__list-item"
              :class="{ 'is-selected': categoryFilter === category.value }"
            >
              <button
                type="button"
                class="cost-subject-page__select"
                :aria-pressed="categoryFilter === category.value"
                @click="selectCategory(category.value)"
              >
                <span>
                  <strong>{{ category.label }}</strong>
                  <small v-if="category.value !== 'ALL'">
                    {{
                      allSubjects.filter(
                        ({ subject }) => subject.accountCategory === category.value,
                      ).length
                    }}
                    个
                  </small>
                </span>
              </button>
            </div>
          </div>
        </section>

        <section aria-labelledby="account-subject-catalog-title">
          <div class="cost-subject-page__section-heading">
            <span>
              <h3 id="account-subject-catalog-title">2. 科目目录</h3>
              <small>共 {{ visibleSubjects.length }} 个</small>
            </span>
          </div>
          <V2PageState
            v-if="!visibleSubjects.length"
            kind="empty"
            title="当前分类暂无科目"
            description="可新增一级科目。"
          />
          <div v-else class="cost-subject-page__list cost-subject-page__catalog">
            <div
              v-for="item in visibleSubjects"
              :key="item.subject.id"
              class="cost-subject-page__list-item"
              :class="{ 'is-selected': selectedSubjectId === item.subject.id }"
            >
              <button
                type="button"
                class="cost-subject-page__select"
                :aria-pressed="selectedSubjectId === item.subject.id"
                :style="{ paddingLeft: `${item.depth * 16}px` }"
                @click="selectedSubjectId = item.subject.id"
              >
                <span>
                  <strong>{{ item.subject.subjectName }}</strong>
                  <small>
                    {{ item.subject.subjectCode }} ·
                    {{ accountCategoryLabel(item.subject.accountCategory) }}
                  </small>
                </span>
              </button>
              <V2StatusToggle
                :enabled="item.subject.status === 'ENABLE'"
                :disabled="!canSubjectEdit || saving || isGovernedSubject(item.subject)"
                :aria-label="`${item.subject.status === 'ENABLE' ? '停用' : '启用'}会计科目 ${item.subject.subjectName}`"
                @toggle="subjectStatusTarget = item.subject"
              />
            </div>
          </div>
        </section>

        <section aria-labelledby="account-subject-detail-title">
          <div class="cost-subject-page__section-heading">
            <h3 id="account-subject-detail-title">3. 科目详情</h3>
            <template v-if="isGovernedSubject(selectedSubject)">
              <V2Button v-if="canSubjectEdit" size="small" @click="openSubjectEdit">编辑</V2Button>
              <V2StatusToggle
                :enabled="selectedSubject?.status === 'ENABLE'"
                disabled
                :aria-label="`会计科目 ${selectedSubject?.subjectName} 状态由系统维护`"
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
                :aria-label="`${selectedSubject.status === 'ENABLE' ? '停用' : '启用'}会计科目 ${selectedSubject.subjectName}`"
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
              <dt>科目大类</dt>
              <dd>{{ accountCategoryLabel(selectedSubject.accountCategory) }}</dd>
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
            <div>
              <dt>项目辅助核算</dt>
              <dd>{{ dimensionRequirementLabel(selectedPolicy?.projectRequirement) }}</dd>
            </div>
            <div>
              <dt>合同辅助核算</dt>
              <dd>{{ dimensionRequirementLabel(selectedPolicy?.contractRequirement) }}</dd>
            </div>
            <div>
              <dt>往来单位</dt>
              <dd>{{ dimensionRequirementLabel(selectedPolicy?.partnerRequirement) }}</dd>
            </div>
            <div>
              <dt>部门 / 员工</dt>
              <dd>
                {{ dimensionRequirementLabel(selectedPolicy?.departmentRequirement) }} /
                {{ dimensionRequirementLabel(selectedPolicy?.employeeRequirement) }}
              </dd>
            </div>
          </dl>
          <V2PageState v-else kind="empty" title="请选择科目" description="选择科目后查看详情。" />
        </section>
      </div>
    </V2Card>

    <V2Card title="成本结转映射" :heading-level="2">
      <div
        class="cost-subject-page__table-wrap"
        role="region"
        aria-label="成本结转映射"
        tabindex="0"
      >
        <table class="v2-table">
          <thead>
            <tr>
              <th>类别</th>
              <th>合同履约成本</th>
              <th>主营业务成本</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in overview.carryoverMappings" :key="row.categoryCode">
              <td>{{ row.categoryCode }} · {{ row.categoryName }}</td>
              <td>{{ row.fulfillmentCode }} · {{ row.fulfillmentName }}</td>
              <td>{{ row.expenseCode }} · {{ row.expenseName }}</td>
              <td>{{ row.status === 'ENABLE' ? '启用' : '停用' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </V2Card>

    <V2Card title="历史科目复核" :heading-level="2">
      <V2PageState
        v-if="!overview.legacyReviews.length"
        kind="empty"
        title="没有待复核历史科目"
        description="历史凭证保持原样。"
      />
      <div
        v-else
        class="cost-subject-page__table-wrap"
        role="region"
        aria-label="历史科目复核"
        tabindex="0"
      >
        <table class="v2-table">
          <thead>
            <tr>
              <th>历史科目</th>
              <th>建议正式科目</th>
              <th>状态</th>
              <th>处理原则</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in overview.legacyReviews" :key="row.sourceSubjectCode">
              <td>{{ row.sourceSubjectCode }} · {{ row.sourceSubjectName }}</td>
              <td>{{ row.suggestedSubjectCode || '待人工判断' }}</td>
              <td>{{ row.reviewStatus === 'PENDING' ? '待复核' : row.reviewStatus }}</td>
              <td>{{ row.reviewNote || '只读保留，不直接改写历史凭证' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </V2Card>

    <V2Card title="关联报表" :heading-level="2">
      <V2Cluster>
        <RouterLink
          v-for="route in overview.reportRoutes"
          :key="route.path"
          :to="route.path"
          class="v2-button v2-button--secondary v2-button--small"
        >
          {{ route.label }}
        </RouterLink>
      </V2Cluster>
    </V2Card>

    <V2Dialog
      :open="subjectDialog"
      :title="subjectMode === 'edit' ? '编辑会计科目' : '新增会计科目'"
      description="层级、租户、分类、唯一性和引用保护由系统校验。"
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
        <V2Select
          v-model="subjectForm.accountCategory"
          :options="accountCategoryOptions"
          label="科目大类"
          required
          :disabled="subjectMode === 'edit' || subjectForm.parentId !== '0'"
        />
        <V2Input
          v-model="subjectForm.subjectType"
          label="科目类型"
          required
          :disabled="editingGovernedSubject"
        />
        <V2Input
          v-model="subjectForm.parentId"
          label="父科目标识"
          :disabled="subjectMode === 'edit'"
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
      title="更新会计科目状态"
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
      title="删除会计科目"
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
