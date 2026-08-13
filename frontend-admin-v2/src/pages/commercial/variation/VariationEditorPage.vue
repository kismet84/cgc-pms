<script setup lang="ts">
import type {
  ContractRecord,
  ProjectContextOption,
  VariationRecord,
  VariationSaveCommand,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { V2Button, V2Dialog, V2Input, V2PageState, V2Select, showToast } from '@/components'
import {
  createVariation,
  loadAllContracts,
  loadProjectContextOptions,
  loadVariation,
  updateVariation,
} from '@/services/commercial'
import { isApiClientError } from '@/services/request'

const props = defineProps<{ mode: 'create' | 'edit' }>()
const route = useRoute()
const router = useRouter()
const detail = ref<VariationRecord | null>(null)
const form = ref<VariationSaveCommand>(emptyForm())
const projects = ref<ProjectContextOption[]>([])
const contracts = ref<ContractRecord[]>([])
const loading = ref(false)
const action = ref('')
const errorMessage = ref('')
let detailController: AbortController | null = null
let referenceController: AbortController | null = null
let generation = 0

const variationId = computed(() =>
  typeof route.query.id === 'string' ? route.query.id.trim() : '',
)
const busy = computed(() => Boolean(action.value))
const projectOptions = computed(() => {
  const options = projects.value
    .filter(
      (item) =>
        item.status === 'ACTIVE' || (props.mode === 'edit' && item.id === form.value.projectId),
    )
    .map((item) => ({ value: item.id, label: `${item.projectCode} · ${item.projectName}` }))
  if (
    form.value.projectId &&
    !options.some((option) => option.value === form.value.projectId) &&
    detail.value?.projectName
  ) {
    options.push({ value: form.value.projectId, label: detail.value.projectName })
  }
  return options
})
const contractOptions = computed(() => {
  const options = contracts.value
    .filter((item) => !form.value.projectId || item.projectId === form.value.projectId)
    .filter(
      (item) =>
        form.value.direction !== 'INCOME' ||
        (item.approvalStatus === 'APPROVED' &&
          item.contractStatus === 'PERFORMING' &&
          item.contractType === 'MAIN') ||
        (props.mode === 'edit' && item.id === form.value.contractId),
    )
    .map((item) => ({ value: item.id, label: `${item.contractCode} · ${item.contractName}` }))
  if (
    form.value.contractId &&
    !options.some((option) => option.value === form.value.contractId) &&
    detail.value?.contractName
  ) {
    options.push({ value: form.value.contractId, label: detail.value.contractName })
  }
  return options
})
const directionOptions = computed(() => {
  const contract = contracts.value.find((item) => item.id === form.value.contractId)
  const incomeEligible =
    !contract ||
    (contract.contractType === 'MAIN' &&
      contract.approvalStatus === 'APPROVED' &&
      contract.contractStatus === 'PERFORMING') ||
    (props.mode === 'edit' && form.value.direction === 'INCOME')
  return [
    { value: 'COST', label: '成本' },
    { value: 'INCOME', label: '收入', disabled: !incomeEligible },
  ]
})
const partnerOptions = computed(() => {
  const contract = contracts.value.find((item) => item.id === form.value.contractId)
  const expectedPartnerId =
    form.value.direction === 'INCOME' ? contract?.partyAId : contract?.partyBId
  const expectedPartnerName =
    form.value.direction === 'INCOME' ? contract?.partyAName : contract?.partyBName
  const options = expectedPartnerId
    ? [{ value: expectedPartnerId, label: expectedPartnerName || '合同往来单位', disabled: false }]
    : []
  if (
    form.value.partnerId &&
    !options.some((option) => option.value === form.value.partnerId) &&
    detail.value?.partnerName
  ) {
    options.push({
      value: form.value.partnerId,
      label: `${detail.value.partnerName}（历史值）`,
      disabled: true,
    })
  }
  return options
})

function emptyForm(): VariationSaveCommand {
  return {
    projectId: typeof route.query.projectId === 'string' ? route.query.projectId.trim() : '',
    contractId: '',
    partnerId: null,
    varName: '',
    eventDate: null,
    claimDeadline: null,
    eventDescription: null,
    causeCategory: null,
    responsibleParty: null,
    businessMatterKey: null,
    varType: 'OTHER',
    direction: 'COST',
    impactDays: null,
    version: null,
    remark: null,
  }
}

function formFromDetail(value: VariationRecord): VariationSaveCommand {
  return {
    projectId: value.projectId,
    contractId: value.contractId ?? '',
    partnerId: value.partnerId ?? null,
    varName: value.varName,
    eventDate: value.eventDate ?? null,
    claimDeadline: value.claimDeadline ?? null,
    eventDescription: value.eventDescription ?? null,
    causeCategory: value.causeCategory ?? null,
    responsibleParty: value.responsibleParty ?? null,
    businessMatterKey: value.businessMatterKey ?? null,
    varType: value.varType ?? 'OTHER',
    direction: value.direction ?? 'COST',
    impactDays: value.impactDays ?? null,
    version: value.version ?? null,
    remark: value.remark ?? null,
  }
}

function cleaned(value?: string | null): string | null {
  const normalized = value?.trim()
  return normalized ? normalized : null
}

function cleanForm(): VariationSaveCommand {
  return {
    ...form.value,
    projectId: form.value.projectId.trim(),
    contractId: form.value.contractId.trim(),
    partnerId: cleaned(form.value.partnerId),
    varName: form.value.varName.trim(),
    eventDate: cleaned(form.value.eventDate),
    claimDeadline: cleaned(form.value.claimDeadline),
    eventDescription: cleaned(form.value.eventDescription),
    causeCategory: cleaned(form.value.causeCategory),
    responsibleParty: cleaned(form.value.responsibleParty),
    businessMatterKey: cleaned(form.value.businessMatterKey),
    varType: form.value.varType.trim(),
    direction: cleaned(form.value.direction),
    remark: cleaned(form.value.remark),
  }
}

function versionOf(value = detail.value): string | number {
  const version = value?.version
  if (version == null || String(version).trim() === '')
    throw new TypeError('缺少最新版本，请刷新后重试')
  return version
}

function errorText(error: unknown, fallback: string): string {
  if (isApiClientError(error)) return error.message
  return error instanceof Error ? error.message : fallback
}

async function loadDetail(preserveNotice = false): Promise<void> {
  if (!variationId.value) {
    detail.value = null
    errorMessage.value = '缺少签证变更编号'
    return
  }
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const currentGeneration = ++generation
  loading.value = true
  if (!preserveNotice) errorMessage.value = ''
  try {
    const value = await loadVariation(variationId.value, controller.signal)
    if (currentGeneration !== generation) return
    detail.value = value
    form.value = formFromDetail(value)
  } catch (error) {
    if (!controller.signal.aborted && currentGeneration === generation) {
      detail.value = null
      errorMessage.value = errorText(error, '签证变更详情加载失败')
      showToast('error', '签证变更操作未完成', errorMessage.value)
    }
  } finally {
    if (currentGeneration === generation) loading.value = false
  }
}

async function loadReferences(): Promise<void> {
  referenceController?.abort()
  const controller = new AbortController()
  referenceController = controller
  try {
    const projectId = detail.value?.projectId || form.value.projectId
    const [projectValues, contractValues] = await Promise.all([
      loadProjectContextOptions(controller.signal),
      projectId
        ? loadAllContracts({ projectId }, controller.signal)
        : Promise.resolve([] as ContractRecord[]),
    ])
    if (referenceController !== controller) return
    projects.value = projectValues
    contracts.value = contractValues
  } catch (error) {
    if (!controller.signal.aborted) {
      errorMessage.value = errorText(error, '业务候选数据加载失败')
      showToast('error', '签证变更操作未完成', errorMessage.value)
    }
  } finally {
    if (referenceController === controller) referenceController = null
  }
}

async function backToList(): Promise<void> {
  const query = { ...route.query }
  delete query.mode
  delete query.id
  await router.push({ path: '/variation/order', query })
}

async function runAction(name: string, operation: () => Promise<void>): Promise<void> {
  if (action.value) return
  action.value = name
  errorMessage.value = ''
  try {
    await operation()
  } catch (error) {
    errorMessage.value = errorText(error, `${name}失败`)
    showToast('error', '签证变更操作未完成', errorMessage.value)
    if (variationId.value) await loadDetail(true)
  } finally {
    action.value = ''
  }
}

async function saveForm(): Promise<void> {
  await runAction('保存', async () => {
    const command = cleanForm()
    if (!command.projectId || !command.contractId || !command.varName || !command.varType) {
      throw new TypeError('项目、合同、变更名称和类型不能为空')
    }
    if (props.mode === 'create') {
      const id = await createVariation(command)
      detail.value = await loadVariation(id)
      await backToList()
      showToast('success', '操作成功', '签证变更已创建。')
      return
    }
    await updateVariation(variationId.value, { ...command, version: versionOf() })
    await loadDetail(true)
    await backToList()
    showToast('success', '操作成功', '签证变更已保存并刷新。')
  })
}

function updateForm(key: keyof VariationSaveCommand, value: string): void {
  if (key === 'impactDays') {
    form.value = { ...form.value, impactDays: value.trim() ? Number(value) : null }
    return
  }
  form.value = { ...form.value, [key]: value }
}

async function updateProject(value: string): Promise<void> {
  form.value = { ...form.value, projectId: value, contractId: '', partnerId: null }
  await loadReferences()
}

function updateContract(value: string): void {
  const contract = contracts.value.find((item) => item.id === value)
  form.value = {
    ...form.value,
    contractId: value,
    partnerId:
      (form.value.direction === 'INCOME' ? contract?.partyAId : contract?.partyBId) ?? null,
  }
}

function updateDirection(value: string): void {
  const contract = contracts.value.find((item) => item.id === form.value.contractId)
  const direction = value || 'COST'
  const contractStillEligible =
    direction === 'COST' ||
    (contract?.approvalStatus === 'APPROVED' &&
      contract.contractStatus === 'PERFORMING' &&
      contract.contractType === 'MAIN') ||
    (props.mode === 'edit' && form.value.direction === 'INCOME')
  form.value = {
    ...form.value,
    direction,
    contractId: contractStillEligible ? form.value.contractId : '',
    partnerId: contractStillEligible
      ? direction === 'INCOME'
        ? (contract?.partyAId ?? null)
        : (contract?.partyBId ?? null)
      : null,
  }
}

watch(
  () => route.fullPath,
  async () => {
    detailController?.abort()
    if (props.mode === 'create') {
      detail.value = null
      form.value = emptyForm()
      errorMessage.value = ''
      await loadReferences()
      return
    }
    await loadDetail()
    await loadReferences()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  detailController?.abort()
  referenceController?.abort()
})
</script>

<template>
  <V2Dialog
    :open="true"
    :title="props.mode === 'create' ? '新建签证变更' : '编辑签证变更'"
    description="维护签证变更基础事实。"
    :close-disabled="busy"
    :close-on-backdrop="false"
    panel-class="v2-dialog-standard v2-dialog-wide"
    @close="backToList"
  >
    <V2PageState v-if="loading" kind="loading" title="正在加载签证变更" description="请稍候。" />
    <form v-else id="variation-editor-form" class="variation-page__form" @submit.prevent="saveForm">
      <V2Select
        :model-value="form.projectId"
        label="项目"
        :options="projectOptions"
        required
        :disabled="props.mode === 'edit'"
        @update:model-value="updateProject"
      />
      <V2Select
        :model-value="form.contractId"
        label="合同"
        :options="contractOptions"
        required
        :disabled="props.mode === 'edit'"
        @update:model-value="updateContract"
      />
      <V2Select
        :model-value="form.partnerId ?? ''"
        label="往来单位"
        :options="partnerOptions"
        allow-empty
        placeholder="请选择往来单位"
        @update:model-value="updateForm('partnerId', $event)"
      />
      <V2Input
        :model-value="form.varName"
        label="变更名称"
        required
        @update:model-value="updateForm('varName', $event)"
      />
      <V2Select
        :model-value="form.varType"
        label="变更类型"
        required
        :options="[
          { value: 'DESIGN', label: '设计变更' },
          { value: 'SITE', label: '现场签证' },
          { value: 'OTHER', label: '其他' },
        ]"
        @update:model-value="updateForm('varType', $event)"
      />
      <V2Select
        :model-value="form.direction ?? ''"
        label="方向"
        :options="directionOptions"
        @update:model-value="updateDirection"
      />
      <V2Input
        :model-value="form.eventDate ?? ''"
        label="发生日期"
        placeholder="YYYY-MM-DD"
        @update:model-value="updateForm('eventDate', $event)"
      />
      <V2Input
        :model-value="form.claimDeadline ?? ''"
        label="申报截止日"
        placeholder="YYYY-MM-DD"
        @update:model-value="updateForm('claimDeadline', $event)"
      />
      <V2Input
        :model-value="String(form.impactDays ?? '')"
        label="影响工期（天）"
        @update:model-value="updateForm('impactDays', $event)"
      />
      <V2Input
        :model-value="form.causeCategory ?? ''"
        label="原因分类"
        @update:model-value="updateForm('causeCategory', $event)"
      />
      <V2Input
        :model-value="form.responsibleParty ?? ''"
        label="责任方"
        @update:model-value="updateForm('responsibleParty', $event)"
      />
      <V2Input
        :model-value="form.businessMatterKey ?? ''"
        label="业务事项键"
        @update:model-value="updateForm('businessMatterKey', $event)"
      />
      <label class="variation-page__native-field variation-page__wide">
        事件说明
        <textarea
          :value="form.eventDescription ?? ''"
          @input="updateForm('eventDescription', ($event.target as HTMLTextAreaElement).value)"
        />
      </label>
      <label class="variation-page__native-field variation-page__wide">
        备注
        <textarea
          :value="form.remark ?? ''"
          @input="updateForm('remark', ($event.target as HTMLTextAreaElement).value)"
        />
      </label>
    </form>
    <template #footer>
      <V2Button type="button" variant="secondary" :disabled="busy" @click="backToList">
        取消
      </V2Button>
      <V2Button
        type="submit"
        form="variation-editor-form"
        :loading="action === '保存'"
        :disabled="busy"
      >
        保存
      </V2Button>
    </template>
  </V2Dialog>
</template>
