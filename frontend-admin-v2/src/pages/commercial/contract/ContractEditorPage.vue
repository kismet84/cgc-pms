<script setup lang="ts">
import type {
  ContractCompositeRecord,
  ContractItemRecord,
  ContractPaymentTermRecord,
  ContractProjectOption,
  ContractSaveCommand,
  ContractType,
  MaterialRecord,
  PartnerRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  MaterialSearchPicker,
  V2Alert,
  V2Button,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  showToast,
  useToastMessage,
} from '@/components'
import {
  createContractComposite,
  loadContractComposite,
  loadContractProjectOptions,
  loadPartners,
  updateContractComposite,
} from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { loadMaterials } from '@/services/supply-chain'
import { useSessionStore } from '@/stores/session'
import {
  CONTRACT_TYPE_OPTIONS,
  blankItem,
  blankTerm,
  cloneCommandFromDetail,
  emptyCommand,
  partnerCandidates,
  previewTaxBreakdown,
  sanitizeCommand,
  validateCommand,
  type ContractEditorMode,
} from './model'

const props = defineProps<{ mode: ContractEditorMode }>()
const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = useToastMessage()
const detail = ref<ContractCompositeRecord | null>(null)
const projects = ref<ContractProjectOption[]>([])
const partners = ref<PartnerRecord[]>([])
const materials = ref<MaterialRecord[]>([])
const form = ref<ContractSaveCommand>(emptyCommand(projectIdFromQuery()))

let detailGeneration = 0
let detailController: AbortController | null = null
let refController: AbortController | null = null

const isCreate = computed(() => props.mode === 'create')
const contractId = computed(() =>
  typeof route.params.id === 'string' ? route.params.id.trim() : '',
)
const canCreate = computed(() => session.hasPermission('contract:add'))
const canEdit = computed(() => session.hasPermission('contract:edit'))
const canQuery = computed(() => session.hasPermission('contract:query'))
const currentContract = computed(() => detail.value?.contract ?? null)
const currentContractIsDraft = computed(() => currentContract.value?.approvalStatus === 'DRAFT')
const formLocked = computed(
  () => saving.value || (!isCreate.value && !currentContractIsDraft.value),
)
const contractAmountLocked = computed(
  () =>
    !isCreate.value &&
    projects.value.find((project) => project.id === form.value.contract.projectId)?.status ===
      'ACTIVE',
)
const materialOptions = computed(() =>
  materials.value.map((item) => ({
    value: item.id,
    label: `${item.materialCode} · ${item.materialName}`,
  })),
)
const projectOptions = computed(() => {
  const currentProjectId = form.value.contract.projectId
  const isEligible = (project: ContractProjectOption) =>
    form.value.contract.contractType === 'MAIN' ? project.mainEligible : project.nonMainEligible
  const options = projects.value
    .filter(
      (project) => isEligible(project) || (!isCreate.value && project.id === currentProjectId),
    )
    .map((project) => {
      const eligible = isEligible(project)
      return {
        value: project.id,
        label: `${project.projectName}${eligible ? '' : '（历史值）'}`,
        disabled: !eligible,
      }
    })
  if (
    !isCreate.value &&
    currentProjectId &&
    !options.some((project) => project.value === currentProjectId) &&
    detail.value?.contract.projectName
  ) {
    options.push({
      value: currentProjectId,
      label: `${detail.value.contract.projectName}（历史值）`,
      disabled: true,
    })
  }
  return options
})
const partyAOptions = computed(() =>
  partnerCandidates(
    partners.value,
    form.value.contract.partyBId,
    form.value.contract.partyAId,
    currentContract.value?.partyAName,
  ),
)
const partyBOptions = computed(() =>
  partnerCandidates(
    partners.value,
    form.value.contract.partyAId,
    form.value.contract.partyBId,
    currentContract.value?.partyBName,
    (partner) =>
      form.value.contract.contractType !== 'PURCHASE' || partner.partnerType === 'SUPPLIER',
  ),
)

watch(errorMessage, (message) => {
  if (message) showToast('error', '合同操作未完成', message)
})

function projectIdFromQuery(): string {
  return typeof route.query.projectId === 'string' ? route.query.projectId.trim() : ''
}

function resetNotices(): void {
  errorMessage.value = ''
  successMessage.value = ''
}

function errorText(error: unknown, fallback: string): string {
  return isApiClientError(error) ? error.message : fallback
}

async function loadReferenceData(): Promise<void> {
  refController?.abort()
  const controller = new AbortController()
  refController = controller
  try {
    const projectOptions = await loadContractProjectOptions(controller.signal)
    if (refController !== controller) return
    projects.value = projectOptions
    const [partnerPage, materialPage] = await Promise.all([
      loadPartners(undefined, controller.signal),
      loadMaterials({ pageNo: 1, pageSize: 200, status: 'ENABLE' }, controller.signal),
    ])
    if (refController !== controller) return
    partners.value = partnerPage.records
    materials.value = materialPage.records
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = errorText(error, '合同候选数据加载失败')
  } finally {
    if (refController === controller) refController = null
  }
}

async function loadDetail(preserveNotice = false): Promise<void> {
  if (!contractId.value) return
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const generation = ++detailGeneration
  loading.value = true
  if (!preserveNotice) resetNotices()
  try {
    const value = await loadContractComposite(contractId.value, controller.signal)
    if (generation !== detailGeneration) return
    detail.value = value
    form.value = cloneCommandFromDetail(value)
  } catch (error) {
    if (!controller.signal.aborted && generation === detailGeneration) {
      detail.value = null
      errorMessage.value = errorText(error, '合同详情加载失败')
    }
  } finally {
    if (generation === detailGeneration) loading.value = false
  }
}

function updateContractField(
  key: keyof ContractSaveCommand['contract'],
  value: string | ContractType,
): void {
  const contract = { ...form.value.contract, [key]: value }
  if (key === 'contractAmount' || key === 'taxRate') {
    const breakdown = previewTaxBreakdown(contract.contractAmount, contract.taxRate)
    contract.taxAmount = breakdown.taxAmount
    contract.amountWithoutTax = breakdown.amountWithoutTax
  }
  form.value = { ...form.value, contract }
}

function updateContractType(value: string): void {
  const contractType = value as ContractType
  const selectedProject = projects.value.find(
    (project) => project.id === form.value.contract.projectId,
  )
  const selectedProjectEligible = selectedProject
    ? contractType === 'MAIN'
      ? selectedProject.mainEligible
      : selectedProject.nonMainEligible
    : false
  form.value = {
    ...form.value,
    contract: {
      ...form.value.contract,
      contractType,
      projectId: selectedProjectEligible ? form.value.contract.projectId : '',
      pricingMode: value === 'PURCHASE' ? form.value.contract.pricingMode || 'FIXED' : null,
    },
  }
}

function updateItem(index: number, key: keyof ContractItemRecord, value: string): void {
  form.value = {
    ...form.value,
    items: form.value.items.map((item, itemIndex) =>
      itemIndex === index ? { ...item, [key]: value } : item,
    ),
  }
}

function updateItemMaterial(index: number, materialId: string): void {
  const material = materials.value.find((item) => item.id === materialId)
  form.value = {
    ...form.value,
    items: form.value.items.map((item, itemIndex) =>
      itemIndex === index
        ? {
            ...item,
            materialId,
            itemCode: material?.materialCode ?? item.itemCode,
            itemName: material?.materialName ?? item.itemName,
            itemSpec: material?.specification ?? item.itemSpec,
            unit: material?.unit ?? item.unit,
          }
        : item,
    ),
  }
}

function updateTerm(index: number, key: keyof ContractPaymentTermRecord, value: string): void {
  form.value = {
    ...form.value,
    paymentTerms: form.value.paymentTerms.map((term, termIndex) =>
      termIndex === index ? { ...term, [key]: value } : term,
    ),
  }
}

function addItem(): void {
  form.value = { ...form.value, items: [...form.value.items, blankItem(form.value.items.length)] }
}

function addMaterialItem(material: MaterialRecord): void {
  if (!materials.value.some((item) => item.id === material.id)) materials.value.push(material)
  const item = {
    ...blankItem(form.value.items.length),
    materialId: material.id,
    itemCode: material.materialCode,
    itemName: material.materialName,
    itemSpec: material.specification ?? '',
    unit: material.unit ?? '',
  }
  const emptyIndex = form.value.items.findIndex(
    (current) => !current.id && !current.materialId && !current.itemCode && !current.itemName,
  )
  const items = [...form.value.items]
  if (emptyIndex >= 0) items[emptyIndex] = item
  else items.push(item)
  form.value = { ...form.value, items }
}

function removeItem(index: number): void {
  form.value = {
    ...form.value,
    items: form.value.items.filter((_, itemIndex) => itemIndex !== index),
  }
}

function addTerm(): void {
  form.value = {
    ...form.value,
    paymentTerms: [...form.value.paymentTerms, blankTerm(form.value.paymentTerms.length)],
  }
}

function removeTerm(index: number): void {
  form.value = {
    ...form.value,
    paymentTerms: form.value.paymentTerms.filter((_, termIndex) => termIndex !== index),
  }
}

async function saveContract(): Promise<void> {
  if (formLocked.value) return
  const command = sanitizeCommand(form.value)
  const validation = validateCommand(command, projects.value)
  if (validation) {
    errorMessage.value = validation
    return
  }
  saving.value = true
  resetNotices()
  try {
    if (isCreate.value) {
      const id = await createContractComposite(command)
      detail.value = await loadContractComposite(id)
      await backToLedger()
      successMessage.value = '合同已创建，并已刷新最新数据。'
      return
    }
    await updateContractComposite(contractId.value, command)
    await loadDetail(true)
    await backToLedger()
    successMessage.value = '合同已保存，并已刷新最新数据。'
  } catch (error) {
    errorMessage.value = errorText(error, '合同保存失败')
    if (!isCreate.value && contractId.value) {
      await loadDetail(true)
      if (isApiClientError(error) && error.status === 409) {
        form.value = detail.value ? cloneCommandFromDetail(detail.value) : form.value
        errorMessage.value = `${error.message}；已刷新最新数据`
      }
    }
  } finally {
    saving.value = false
  }
}

async function backToLedger(): Promise<void> {
  await router.push({ path: '/contract/ledger', query: route.query })
}

watch(
  () => route.fullPath,
  async () => {
    await loadReferenceData()
    if (isCreate.value) {
      resetNotices()
      detail.value = null
      form.value = emptyCommand(projectIdFromQuery())
    } else {
      await loadDetail()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  detailController?.abort()
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

    <V2Dialog
      :open="isCreate || Boolean(detail)"
      :title="isCreate ? '新建合同' : '编辑合同'"
      description="维护合同及其清单和付款条款。"
      :close-disabled="saving"
      panel-class="v2-dialog-standard v2-detail-dialog v2-dialog-wide"
      @close="backToLedger"
    >
      <V2Alert v-if="!isCreate && !currentContractIsDraft" tone="warning" title="非草稿锁定">
        当前合同不处于草稿状态，禁止再次编辑。
      </V2Alert>

      <section class="v2-detail-dialog__section">
        <div class="v2-detail-dialog__section-heading"><h3>合同头</h3></div>
        <div class="contract-page__form-grid">
          <V2Select
            :model-value="form.contract.projectId || ''"
            label="项目"
            :options="projectOptions"
            :disabled="formLocked"
            @update:model-value="updateContractField('projectId', $event)"
          />
          <V2Input
            :model-value="form.contract.contractName"
            label="合同名称"
            :disabled="formLocked"
            @update:model-value="updateContractField('contractName', $event)"
          />
          <V2Select
            :model-value="form.contract.contractType"
            label="合同类型"
            :options="CONTRACT_TYPE_OPTIONS"
            :disabled="formLocked"
            @update:model-value="updateContractType"
          />
          <V2Select
            v-if="form.contract.contractType === 'PURCHASE'"
            :model-value="form.contract.pricingMode || 'FIXED'"
            label="采购计价模式"
            :options="[
              { value: 'FIXED', label: '合同固定价' },
              { value: 'ACTUAL', label: '实际采购价' },
            ]"
            :disabled="formLocked"
            @update:model-value="updateContractField('pricingMode', $event)"
          />
          <V2Select
            :model-value="form.contract.partyAId || ''"
            label="甲方"
            :options="partyAOptions"
            :disabled="formLocked"
            @update:model-value="updateContractField('partyAId', $event)"
          />
          <V2Select
            :model-value="form.contract.partyBId || ''"
            label="乙方"
            :options="partyBOptions"
            :disabled="formLocked"
            @update:model-value="updateContractField('partyBId', $event)"
          />
          <V2Input
            :model-value="form.contract.contractAmount || ''"
            label="合同金额"
            :decimal-scale="2"
            :disabled="formLocked || contractAmountLocked"
            :hint="contractAmountLocked ? '项目已在建，合同总价调整请发起合同变更。' : undefined"
            @update:model-value="updateContractField('contractAmount', $event)"
          />
          <V2Input
            :model-value="form.contract.taxRate || ''"
            label="税率"
            :decimal-scale="2"
            :disabled="formLocked"
            @update:model-value="updateContractField('taxRate', $event)"
          />
          <V2Input
            :model-value="form.contract.taxAmount || ''"
            label="税额（自动计算）"
            :decimal-scale="2"
            disabled
          />
          <V2Input
            :model-value="form.contract.amountWithoutTax || ''"
            label="不含税金额（自动计算）"
            :decimal-scale="2"
            disabled
          />
          <V2Input
            :model-value="form.contract.paymentMethod || ''"
            label="付款方式"
            :disabled="formLocked"
            @update:model-value="updateContractField('paymentMethod', $event)"
          />
          <V2Input
            :model-value="form.contract.settlementMethod || ''"
            label="结算方式"
            :disabled="formLocked"
            @update:model-value="updateContractField('settlementMethod', $event)"
          />
          <label class="contract-page__native-field">
            <span>签订日期</span
            ><input v-model="form.contract.signedDate" type="date" :disabled="formLocked" />
          </label>
          <label class="contract-page__native-field">
            <span>开始日期</span
            ><input v-model="form.contract.startDate" type="date" :disabled="formLocked" />
          </label>
          <label class="contract-page__native-field">
            <span>结束日期</span
            ><input v-model="form.contract.endDate" type="date" :disabled="formLocked" />
          </label>
          <label class="contract-page__native-field contract-page__wide">
            <span>备注</span
            ><textarea v-model="form.contract.remark" rows="3" :disabled="formLocked" />
          </label>
        </div>
      </section>

      <section class="v2-detail-dialog__section">
        <div class="v2-detail-dialog__section-heading">
          <h3>合同清单</h3>
          <div class="contract-page__section-actions">
            <MaterialSearchPicker
              v-if="form.contract.contractType === 'PURCHASE'"
              :disabled="formLocked"
              @select="addMaterialItem"
            />
            <V2Button type="button" size="small" :disabled="formLocked" @click="addItem"
              >新增清单</V2Button
            >
          </div>
        </div>
        <div v-if="form.items.length" class="contract-page__editor-list">
          <article
            v-for="(item, index) in form.items"
            :key="item.id || `${index}-${item.itemName}`"
          >
            <div class="contract-page__form-grid">
              <V2Select
                v-if="form.contract.contractType === 'PURCHASE'"
                :model-value="item.materialId || ''"
                label="物料"
                :options="materialOptions"
                :disabled="formLocked"
                @update:model-value="updateItemMaterial(index, $event)"
              />
              <V2Input
                :model-value="item.itemName"
                label="名称"
                :disabled="formLocked"
                @update:model-value="updateItem(index, 'itemName', $event)"
              />
              <V2Input
                :model-value="item.itemCode || ''"
                label="编号"
                :disabled="formLocked"
                @update:model-value="updateItem(index, 'itemCode', $event)"
              />
              <V2Input
                :model-value="item.itemSpec || ''"
                label="规格"
                :disabled="formLocked"
                @update:model-value="updateItem(index, 'itemSpec', $event)"
              />
              <V2Input
                :model-value="item.unit || ''"
                label="单位"
                :disabled="formLocked"
                @update:model-value="updateItem(index, 'unit', $event)"
              />
              <V2Input
                :model-value="item.quantity || ''"
                label="数量"
                :decimal-scale="2"
                :disabled="formLocked"
                @update:model-value="updateItem(index, 'quantity', $event)"
              />
              <V2Input
                :model-value="item.unitPrice || ''"
                label="单价"
                :decimal-scale="2"
                :disabled="formLocked"
                @update:model-value="updateItem(index, 'unitPrice', $event)"
              />
            </div>
            <div class="contract-page__actions">
              <V2Button
                type="button"
                size="small"
                variant="danger"
                :disabled="formLocked"
                @click="removeItem(index)"
                >移除</V2Button
              >
            </div>
          </article>
        </div>
        <V2PageState
          v-else-if="!errorMessage"
          kind="empty"
          title="暂无合同清单"
          description="可按最小闭环先保存合同头，再补录清单。"
          :heading-level="3"
        />
      </section>

      <section class="v2-detail-dialog__section">
        <div class="v2-detail-dialog__section-heading">
          <h3>付款条款</h3>
          <V2Button type="button" size="small" :disabled="formLocked" @click="addTerm"
            >新增条款</V2Button
          >
        </div>
        <div v-if="form.paymentTerms.length" class="contract-page__editor-list">
          <article
            v-for="(term, index) in form.paymentTerms"
            :key="term.id || `${index}-${term.termName}`"
          >
            <div class="contract-page__form-grid">
              <V2Input
                :model-value="term.termName"
                label="条款名称"
                :disabled="formLocked"
                @update:model-value="updateTerm(index, 'termName', $event)"
              />
              <V2Input
                :model-value="term.paymentRatio || ''"
                label="付款比例"
                :decimal-scale="2"
                :disabled="formLocked"
                @update:model-value="updateTerm(index, 'paymentRatio', $event)"
              />
              <V2Input
                :model-value="term.paymentAmount || ''"
                label="付款金额"
                :decimal-scale="2"
                :disabled="formLocked"
                @update:model-value="updateTerm(index, 'paymentAmount', $event)"
              />
              <label class="contract-page__native-field">
                <span>计划日期</span
                ><input v-model="term.plannedDate" type="date" :disabled="formLocked" />
              </label>
            </div>
            <div class="contract-page__actions">
              <V2Button
                type="button"
                size="small"
                variant="danger"
                :disabled="formLocked"
                @click="removeTerm(index)"
                >移除</V2Button
              >
            </div>
          </article>
        </div>
        <V2PageState
          v-else-if="!errorMessage"
          kind="empty"
          title="暂无付款条款"
          description="可先保存草稿，再按节点补录付款安排。"
          :heading-level="3"
        />
      </section>

      <template #footer>
        <V2Button type="button" variant="secondary" :disabled="saving" @click="backToLedger"
          >取消</V2Button
        >
        <V2Button
          v-if="isCreate ? canCreate : canEdit"
          type="button"
          :loading="saving"
          :disabled="formLocked || !canQuery"
          @click="saveContract"
          >{{ isCreate ? '创建合同' : '保存变更' }}</V2Button
        >
      </template>
    </V2Dialog>

    <V2PageState
      v-if="!isCreate && !loading && !detail"
      kind="error"
      title="合同不可访问"
      description="合同不存在、超出项目范围，或当前账号没有访问权限。"
      title-id="contract-title"
      :heading-level="1"
    >
      <template #actions
        ><V2Button variant="secondary" @click="backToLedger">返回台账</V2Button></template
      >
    </V2PageState>
  </section>
</template>

<style scoped src="./contract-page.css"></style>
