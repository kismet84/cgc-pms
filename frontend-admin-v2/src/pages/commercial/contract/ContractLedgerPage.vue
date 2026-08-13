<script setup lang="ts">
import type {
  ContractPage,
  ContractProjectOption,
  ContractQuery,
  ContractType,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { V2Badge, V2Button, V2Card, V2PageState, showToast, useToastMessage } from '@/components'
import { formatAmount } from '@/shared/display'
import { loadContractPage, loadContractProjectOptions } from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { reportPeriodBounds } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'
import {
  CONTRACT_PRESET_VIEWS,
  approvalStatusLabel,
  contractStatusLabel,
  contractTypeLabel,
} from './model'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const loading = ref(false)
const errorMessage = ref('')
const successMessage = useToastMessage()
const contracts = ref<ContractPage['records']>([])
const total = ref(0)
const projects = ref<ContractProjectOption[]>([])
const filter = reactive<ContractQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  projectId: '',
  contractType: undefined,
  contractStatus: undefined,
  approvalStatus: undefined,
})

let listGeneration = 0
let listController: AbortController | null = null
let refController: AbortController | null = null

const canCreate = computed(() => session.hasPermission('contract:add'))
const canEdit = computed(() => session.hasPermission('contract:edit'))
const pageCount = computed(() => {
  const pageSize = filter.pageSize ?? 10
  return Math.max(1, Math.ceil(total.value / pageSize))
})
const activePresetView = computed(
  () =>
    CONTRACT_PRESET_VIEWS.find(
      (preset) =>
        preset.contractStatus === filter.contractStatus &&
        preset.approvalStatus === filter.approvalStatus,
    )?.id ?? '',
)

watch(errorMessage, (message) => {
  if (message) showToast('error', '合同操作未完成', message)
})

function resetNotices(): void {
  errorMessage.value = ''
  successMessage.value = ''
}

function errorText(error: unknown, fallback: string): string {
  return isApiClientError(error) ? error.message : fallback
}

function hydrateFilter(): void {
  filter.keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  filter.projectId = typeof route.query.projectId === 'string' ? route.query.projectId : ''
  filter.contractStatus =
    typeof route.query.contractStatus === 'string'
      ? (route.query.contractStatus as ContractQuery['contractStatus'])
      : undefined
  filter.approvalStatus =
    typeof route.query.approvalStatus === 'string'
      ? (route.query.approvalStatus as ContractQuery['approvalStatus'])
      : undefined
  filter.contractType =
    typeof route.query.contractType === 'string'
      ? (route.query.contractType as ContractType)
      : undefined
  const periodBounds = reportPeriodBounds(
    typeof route.query.period === 'string' ? route.query.period : null,
  )
  filter.startDate = periodBounds?.startDate
  filter.endDate = periodBounds?.endDate
  const pageNo = typeof route.query.pageNo === 'string' ? Number(route.query.pageNo) : 1
  filter.pageNo = Number.isInteger(pageNo) && pageNo > 0 ? pageNo : 1
}

async function replaceLedgerQuery(): Promise<boolean> {
  const location = {
    path: '/contract/ledger',
    query: {
      ...(filter.keyword ? { keyword: filter.keyword } : {}),
      ...(filter.projectId ? { projectId: filter.projectId } : {}),
      ...(filter.contractType ? { contractType: filter.contractType } : {}),
      ...(filter.contractStatus ? { contractStatus: filter.contractStatus } : {}),
      ...(filter.approvalStatus ? { approvalStatus: filter.approvalStatus } : {}),
      ...(typeof route.query.period === 'string' ? { period: route.query.period } : {}),
      ...(filter.pageNo && filter.pageNo > 1 ? { pageNo: String(filter.pageNo) } : {}),
    },
    hash: route.hash,
  }
  if (router.resolve(location).fullPath === route.fullPath) return false
  await router.replace(location)
  return true
}

async function loadReferenceData(): Promise<void> {
  refController?.abort()
  const controller = new AbortController()
  refController = controller
  try {
    const projectOptions = await loadContractProjectOptions(controller.signal)
    if (refController === controller) projects.value = projectOptions
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = errorText(error, '合同候选数据加载失败')
  } finally {
    if (refController === controller) refController = null
  }
}

async function loadLedger(): Promise<void> {
  hydrateFilter()
  listController?.abort()
  const controller = new AbortController()
  listController = controller
  const generation = ++listGeneration
  loading.value = true
  resetNotices()
  try {
    const page = await loadContractPage(filter, controller.signal)
    if (generation !== listGeneration) return
    contracts.value = page.records
    total.value = page.total
  } catch (error) {
    if (!controller.signal.aborted && generation === listGeneration) {
      contracts.value = []
      total.value = 0
      errorMessage.value = errorText(error, '合同台账加载失败')
    }
  } finally {
    if (generation === listGeneration) loading.value = false
  }
}

function projectLabel(projectId?: string | null): string {
  return projects.value.find((item) => item.id === projectId)?.projectName ?? '项目名称缺失'
}

function openDetail(id: string): void {
  void router.push({ path: `/contract/${id}`, query: route.query })
}

function openCreate(): void {
  void router.push({ path: '/contract/create', query: route.query })
}

async function applyPresetView(preset: (typeof CONTRACT_PRESET_VIEWS)[number]): Promise<void> {
  filter.keyword = ''
  filter.contractType = undefined
  filter.contractStatus = preset.contractStatus
  filter.approvalStatus = preset.approvalStatus
  filter.pageNo = 1
  if (!(await replaceLedgerQuery())) await loadLedger()
}

async function goPage(nextPage: number): Promise<void> {
  if (nextPage < 1 || nextPage > pageCount.value) return
  filter.pageNo = nextPage
  if (!(await replaceLedgerQuery())) await loadLedger()
}

watch(
  () => route.fullPath,
  async () => {
    await loadReferenceData()
    await loadLedger()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  listController?.abort()
  refController?.abort()
})
</script>

<template>
  <section class="contract-page" aria-labelledby="contract-title">
    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在加载合同数据"
      description="请稍候。"
      title-id="contract-title"
      :heading-level="1"
    />

    <template v-else>
      <V2Card
        class="contract-page__list-card contract-page__header-card"
        title="合同台账"
        title-id="contract-title"
        :heading-level="1"
      >
        <template #actions>
          <V2Button v-if="canCreate" size="small" @click="openCreate">新建合同</V2Button>
        </template>
      </V2Card>

      <V2Card class="contract-page__ledger-card">
        <nav class="contract-page__preset-views" aria-label="合同预设视图">
          <V2Button
            v-for="preset in CONTRACT_PRESET_VIEWS"
            :key="preset.id"
            size="small"
            :variant="activePresetView === preset.id ? 'primary' : 'ghost'"
            :aria-pressed="activePresetView === preset.id"
            :disabled="loading"
            @click="applyPresetView(preset)"
          >
            {{ preset.label }}
          </V2Button>
        </nav>

        <V2PageState
          v-if="!contracts.length && !errorMessage"
          kind="empty"
          title="暂无可见合同"
          description="调整筛选条件，或联系管理员核对项目和权限范围。"
          :heading-level="3"
        />

        <div v-else-if="contracts.length" class="contract-page__table-wrap" tabindex="0">
          <table class="contract-page__table">
            <caption class="v2-visually-hidden">
              合同列表
            </caption>
            <thead>
              <tr>
                <th scope="col">合同编号</th>
                <th scope="col">合同名称</th>
                <th scope="col">项目</th>
                <th scope="col">类型</th>
                <th scope="col">合同状态</th>
                <th scope="col">审批状态</th>
                <th scope="col">当前金额</th>
                <th scope="col">已付金额</th>
                <th scope="col">乙方</th>
                <th scope="col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="contract in contracts" :key="contract.id">
                <th scope="row">
                  <V2Button
                    size="small"
                    variant="ghost"
                    class="v2-table__record-link"
                    @click="openDetail(contract.id)"
                  >
                    {{ contract.contractCode }}
                  </V2Button>
                </th>
                <td>{{ contract.contractName }}</td>
                <td>{{ projectLabel(contract.projectId) }}</td>
                <td>
                  <V2Badge tone="info">{{ contractTypeLabel(contract.contractType) }}</V2Badge>
                </td>
                <td>
                  <V2Badge tone="info">{{ contractStatusLabel(contract.contractStatus) }}</V2Badge>
                </td>
                <td>
                  <V2Badge tone="info">{{ approvalStatusLabel(contract.approvalStatus) }}</V2Badge>
                </td>
                <td>{{ formatAmount(contract.currentAmount) }}</td>
                <td>{{ formatAmount(contract.paidAmount) }}</td>
                <td>{{ contract.partyBName || '合作方名称缺失' }}</td>
                <td>
                  <div class="contract-page__actions">
                    <V2Button
                      v-if="canEdit && contract.approvalStatus === 'DRAFT'"
                      size="small"
                      variant="ghost"
                      @click="
                        router.push({ path: `/contract/${contract.id}/edit`, query: route.query })
                      "
                      >编辑</V2Button
                    >
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <template #footer>
          <nav class="contract-page__pagination" aria-label="合同分页">
            <span>共 {{ total }} 条</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="(filter.pageNo ?? 1) <= 1"
              @click="goPage((filter.pageNo ?? 1) - 1)"
              >上一页</V2Button
            >
            <span>第 {{ filter.pageNo ?? 1 }} 页</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="(filter.pageNo ?? 1) >= pageCount"
              @click="goPage((filter.pageNo ?? 1) + 1)"
              >下一页</V2Button
            >
          </nav>
        </template>
      </V2Card>
    </template>
  </section>
</template>

<style scoped src="./contract-page.css"></style>
