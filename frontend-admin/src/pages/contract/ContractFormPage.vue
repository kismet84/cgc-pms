<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import StepWizard, { type StepConfig } from '@/components/StepWizard.vue'
import ContractItemEditor, { type EditableContractItem } from '@/components/ContractItemEditor.vue'
import PaymentTermEditor, { type EditablePaymentTerm } from '@/components/PaymentTermEditor.vue'
import {
  createContract,
  updateContract,
  getContractDetail,
  getContractItems,
  getPaymentTerms,
  saveContractItems,
  savePaymentTerms,
  submitForApproval,
} from '@/api/modules/contract'
import { useReferenceStore } from '@/stores/reference'
import type { ContractType, ContractItem, ContractPaymentTerm } from '@/types/contract'

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)
const contractId = computed(() => String(route.params.id || ''))
const loadingDetail = ref(false)

// ---- Steps ----
const stepConfig: StepConfig[] = [
  { title: '基本信息', description: '合同主体信息' },
  { title: '合同明细', description: '明细清单' },
  { title: '付款条款', description: '付款节点' },
  { title: '确认提交', description: '核对并提交' },
]
const current = ref(0)
const submitting = ref(false)

// ---- Data dictionaries ----
const contractTypeOptions: { value: ContractType; label: string }[] = [
  { value: 'MAIN', label: '总包合同' },
  { value: 'SUB', label: '分包合同' },
  { value: 'PURCHASE', label: '采购合同' },
  { value: 'LEASE', label: '租赁合同' },
  { value: 'SERVICE', label: '服务合同' },
]
const paymentMethodOptions = [
  { value: '银行转账', label: '银行转账' },
  { value: '银行承兑汇票', label: '银行承兑汇票' },
  { value: '商业承兑汇票', label: '商业承兑汇票' },
  { value: '现金', label: '现金' },
]
const settlementMethodOptions = [
  { value: '按月结算', label: '按月结算' },
  { value: '按进度结算', label: '按进度结算' },
  { value: '一次性结算', label: '一次性结算' },
  { value: '按节点结算', label: '按节点结算' },
]

const TYPE_LABEL: Record<ContractType, string> = {
  MAIN: '总包合同',
  SUB: '分包合同',
  PURCHASE: '采购合同',
  LEASE: '租赁合同',
  SERVICE: '服务合同',
}

// ---- Reactive form data ----
const formData = reactive({
  contractName: '',
  contractType: undefined as ContractType | undefined,
  projectId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  partyA: '',
  partyB: '',
  contractAmount: undefined as number | undefined,
  signedDate: '',
  startDate: '',
  endDate: '',
  paymentMethod: undefined as string | undefined,
  settlementMethod: undefined as string | undefined,
  warrantyRate: 0,
  warrantyAmount: undefined as number | undefined,
  remark: '',
})

const items = ref<EditableContractItem[]>([])
const terms = ref<EditablePaymentTerm[]>([])

// ---- Reference data (projects / partners) ----
const referenceStore = useReferenceStore()
const projects = computed(() => referenceStore.projects ?? [])
const partners = computed(() => referenceStore.partners ?? [])

const projectName = computed(
  () => projects.value.find((p) => p.id === formData.projectId)?.projectName ?? '-',
)
const partnerName = computed(
  () => partners.value.find((p) => p.id === formData.partnerId)?.partnerName ?? '-',
)

// ---- Step 1 form & validation ----
const basicFormRef = ref<FormInstance>()
const basicRules: Record<string, Rule[]> = {
  contractName: [{ required: true, message: '请输入合同名称', trigger: 'blur' }],
  contractType: [{ required: true, message: '请选择合同类型', trigger: 'change' }],
  projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }],
  partnerId: [{ required: true, message: '请选择合作方', trigger: 'change' }],
  contractAmount: [{ required: true, message: '请输入合同金额', trigger: 'blur' }],
  signedDate: [{ required: true, message: '请选择签订日期', trigger: 'change' }],
}

async function validateBasic(): Promise<boolean> {
  try {
    await basicFormRef.value?.validate()
    return true
  } catch (e: unknown) {
    console.error(e)
    message.warning('请完善基本信息后再继续')
    return false
  }
}

function validateItems(): boolean {
  if (items.value.length === 0) {
    message.warning('请至少添加一条合同明细')
    return false
  }
  const invalid = items.value.some((r) => !r.itemName?.trim())
  if (invalid) {
    message.warning('存在未填写名称的明细项')
    return false
  }
  return true
}

function validateTerms(): boolean {
  if (terms.value.length === 0) {
    message.warning('请至少添加一条付款条款')
    return false
  }
  const invalid = terms.value.some((r) => !r.termName?.trim())
  if (invalid) {
    message.warning('存在未填写名称的付款条款')
    return false
  }
  return true
}

// ---- Navigation ----
async function handleNext() {
  let ok = true
  if (current.value === 0) ok = await validateBasic()
  else if (current.value === 1) ok = validateItems()
  else if (current.value === 2) ok = validateTerms()
  if (ok && current.value < stepConfig.length - 1) {
    current.value += 1
  }
}

function handlePrev() {
  if (current.value > 0) current.value -= 1
}

function handleCancel() {
  Modal.confirm({
    title: isEdit.value ? '放弃编辑合同？' : '放弃新建合同？',
    content: '已填写的内容将不会保存。',
    okText: '确认放弃',
    cancelText: '继续编辑',
    onOk: () => router.push('/contract/ledger'),
  })
}

// ---- Build payloads ----
function buildContractPayload() {
  return {
    contractName: formData.contractName,
    contractType: formData.contractType,
    projectId: formData.projectId,
    partnerId: formData.partnerId,
    partyA: formData.partyA,
    partyB: formData.partyB,
    contractAmount: String(formData.contractAmount ?? 0),
    signedDate: formData.signedDate,
    startDate: formData.startDate,
    endDate: formData.endDate,
    paymentMethod: formData.paymentMethod ?? '',
    settlementMethod: formData.settlementMethod ?? '',
    warrantyRate: formData.warrantyRate,
    warrantyAmount: String(formData.warrantyAmount ?? 0),
    remark: formData.remark,
  }
}

function buildItemsPayload(): ContractItem[] {
  return items.value.map((r, idx) => ({
    id: r.id ?? '',
    itemCode: r.itemCode ?? '',
    itemName: r.itemName ?? '',
    itemSpec: r.itemSpec ?? '',
    unit: r.unit ?? '',
    quantity: Number(r.quantity) || 0,
    unitPrice: String(r.unitPrice ?? '0'),
    amount: String(r.amount ?? '0'),
    taxRate: Number(r.taxRate) || 0,
    taxAmount: String(r.taxAmount ?? '0'),
    amountWithoutTax: String(r.amountWithoutTax ?? '0'),
    sortOrder: idx + 1,
  }))
}

function buildTermsPayload(): ContractPaymentTerm[] {
  return terms.value.map((r, idx) => ({
    id: r.id ?? '',
    termName: r.termName ?? '',
    paymentRatio: Number(r.paymentRatio) || 0,
    paymentAmount: String(r.paymentAmount ?? '0'),
    paymentCondition: r.paymentCondition ?? '',
    plannedDate: r.plannedDate ?? '',
    termStatus: r.termStatus ?? 'PENDING',
    sortOrder: idx + 1,
  }))
}

// ---- Submit ----
async function doSubmit(withApproval: boolean) {
  if (!(await validateBasic()) || !validateItems() || !validateTerms()) return
  submitting.value = true
  try {
    let targetId: string
    if (isEdit.value) {
      await updateContract(contractId.value, buildContractPayload())
      targetId = contractId.value
    } else {
      const created = await createContract(buildContractPayload())
      targetId = created?.id
      if (!targetId) {
        message.error('合同创建成功但未返回ID，无法保存明细')
        return
      }
    }
    await saveContractItems(targetId, buildItemsPayload())
    await savePaymentTerms(targetId, buildTermsPayload())
    if (withApproval) {
      await submitForApproval(targetId)
      message.success(isEdit.value ? '合同已更新并提交审批' : '合同已创建并提交审批')
    } else {
      message.success(isEdit.value ? '合同已更新' : '合同已保存为草稿')
    }
    router.push('/contract/ledger')
  } catch (e: unknown) {
    console.error(e)
    message.error('保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

function handleSubmit() {
  doSubmit(true)
}
function handleSaveDraft() {
  doSubmit(false)
}

function fmtMoney(val: string | number | undefined): string {
  const n = Number(val) || 0
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const itemsTotal = computed(() =>
  items.value.reduce((s, r) => s + (Number(r.amount) || 0), 0).toFixed(2),
)
const termsTotal = computed(() =>
  terms.value.reduce((s, r) => s + (Number(r.paymentAmount) || 0), 0).toFixed(2),
)

onMounted(async () => {
  await Promise.all([referenceStore.fetchProjects(), referenceStore.fetchPartners()])
  if (isEdit.value) {
    await loadContractDetail()
  }
})

async function loadContractDetail() {
  loadingDetail.value = true
  try {
    const contract = await getContractDetail(contractId.value)
    formData.contractName = contract.contractName
    formData.contractType = contract.contractType
    formData.projectId = contract.projectId
    formData.partnerId = contract.partnerId
    formData.partyA = contract.partyA || ''
    formData.partyB = contract.partyB || ''
    formData.contractAmount = Number(contract.contractAmount) || undefined
    formData.signedDate = contract.signedDate || ''
    formData.startDate = contract.startDate || ''
    formData.endDate = contract.endDate || ''
    formData.paymentMethod = contract.paymentMethod || undefined
    formData.settlementMethod = contract.settlementMethod || undefined
    formData.warrantyRate = contract.warrantyRate ?? 0
    formData.warrantyAmount = Number(contract.warrantyAmount) || undefined
    formData.remark = contract.remark || ''

    try {
      const existingItems = await getContractItems(contractId.value)
      items.value = existingItems.map((it, idx) => ({
        _key: genItemKey(),
        id: it.id,
        itemCode: it.itemCode ?? '',
        itemName: it.itemName ?? '',
        itemSpec: it.itemSpec ?? '',
        unit: it.unit ?? '',
        quantity: it.quantity,
        unitPrice: it.unitPrice ?? '0',
        amount: it.amount ?? '0',
        taxRate: it.taxRate,
        taxAmount: it.taxAmount ?? '0',
        amountWithoutTax: it.amountWithoutTax ?? '0',
        sortOrder: idx + 1,
      }))
    } catch (e: unknown) {
      console.error(e)
      items.value = []
    }

    try {
      const existingTerms = await getPaymentTerms(contractId.value)
      terms.value = existingTerms.map((t, idx) => ({
        _key: genTermKey(),
        id: t.id,
        termName: t.termName ?? '',
        paymentRatio: t.paymentRatio,
        paymentAmount: t.paymentAmount ?? '0',
        paymentCondition: t.paymentCondition ?? '',
        plannedDate: t.plannedDate ?? '',
        termStatus: t.termStatus ?? 'PENDING',
        sortOrder: idx + 1,
      }))
    } catch (e: unknown) {
      console.error(e)
      terms.value = []
    }
  } catch (e: unknown) {
    console.error(e)
    message.error('加载合同信息失败')
    router.push('/contract/ledger')
  } finally {
    loadingDetail.value = false
  }
}

function genItemKey(): string {
  return `ci_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

function genTermKey(): string {
  return `ct_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}
</script>

<template>
  <div class="cf-page">
    <a-breadcrumb class="cf-breadcrumb">
      <a-breadcrumb-item>合同管理</a-breadcrumb-item>
      <a-breadcrumb-item>合同台账</a-breadcrumb-item>
      <a-breadcrumb-item>{{ isEdit ? '编辑合同' : '新建合同' }}</a-breadcrumb-item>
    </a-breadcrumb>

    <div class="cf-card">
      <StepWizard
        :current="current"
        :steps="stepConfig"
        :loading="submitting"
        @prev="handlePrev"
        @next="handleNext"
        @submit="handleSubmit"
      >
        <!-- Step 1: Basic Info -->
        <div v-show="current === 0">
          <a-form ref="basicFormRef" :model="formData" :rules="basicRules" layout="vertical">
            <a-row :gutter="24">
              <a-col :span="12">
                <a-form-item label="合同名称" name="contractName">
                  <a-input
                    v-model:value="formData.contractName"
                    placeholder="请输入合同名称"
                    allow-clear
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="合同类型" name="contractType">
                  <a-select
                    v-model:value="formData.contractType"
                    placeholder="请选择合同类型"
                    :options="contractTypeOptions"
                    allow-clear
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="所属项目" name="projectId">
                  <a-select
                    v-model:value="formData.projectId"
                    placeholder="请选择项目"
                    show-search
                    option-filter-prop="label"
                    allow-clear
                  >
                    <a-select-option
                      v-for="p in projects"
                      :key="p.id"
                      :value="p.id"
                      :label="p.projectName"
                    >
                      {{ p.projectName }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="合作方" name="partnerId">
                  <a-select
                    v-model:value="formData.partnerId"
                    placeholder="请选择合作方"
                    show-search
                    option-filter-prop="label"
                    allow-clear
                  >
                    <a-select-option
                      v-for="p in partners"
                      :key="p.id"
                      :value="p.id"
                      :label="p.partnerName"
                    >
                      {{ p.partnerName }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="甲方" name="partyA">
                  <a-input
                    v-model:value="formData.partyA"
                    placeholder="请输入甲方名称"
                    allow-clear
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="乙方" name="partyB">
                  <a-input
                    v-model:value="formData.partyB"
                    placeholder="请输入乙方名称"
                    allow-clear
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="合同金额(元)" name="contractAmount">
                  <a-input-number
                    v-model:value="formData.contractAmount"
                    :min="0"
                    :precision="2"
                    placeholder="请输入合同金额"
                    style="width: 100%"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="签订日期" name="signedDate">
                  <a-date-picker
                    v-model:value="formData.signedDate"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="开始日期" name="startDate">
                  <a-date-picker
                    v-model:value="formData.startDate"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="结束日期" name="endDate">
                  <a-date-picker
                    v-model:value="formData.endDate"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="付款方式" name="paymentMethod">
                  <a-select
                    v-model:value="formData.paymentMethod"
                    placeholder="请选择付款方式"
                    :options="paymentMethodOptions"
                    allow-clear
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="结算方式" name="settlementMethod">
                  <a-select
                    v-model:value="formData.settlementMethod"
                    placeholder="请选择结算方式"
                    :options="settlementMethodOptions"
                    allow-clear
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="质保金比例(%)" name="warrantyRate">
                  <a-input-number
                    v-model:value="formData.warrantyRate"
                    :min="0"
                    :max="100"
                    :precision="2"
                    style="width: 100%"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="质保金金额(元)" name="warrantyAmount">
                  <a-input-number
                    v-model:value="formData.warrantyAmount"
                    :min="0"
                    :precision="2"
                    style="width: 100%"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="24">
                <a-form-item label="备注" name="remark">
                  <a-textarea
                    v-model:value="formData.remark"
                    placeholder="请输入备注信息"
                    :rows="3"
                    allow-clear
                  />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </div>

        <!-- Step 2: Contract Items -->
        <div v-show="current === 1">
          <ContractItemEditor v-model="items" />
        </div>

        <!-- Step 3: Payment Terms -->
        <div v-show="current === 2">
          <PaymentTermEditor v-model="terms" />
        </div>

        <!-- Step 4: Review & Submit -->
        <div v-show="current === 3" class="cf-review">
          <a-descriptions title="基本信息" bordered :column="2" size="small">
            <a-descriptions-item label="合同名称">{{
              formData.contractName || '-'
            }}</a-descriptions-item>
            <a-descriptions-item label="合同类型">
              {{ formData.contractType ? TYPE_LABEL[formData.contractType] : '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="所属项目">{{ projectName }}</a-descriptions-item>
            <a-descriptions-item label="合作方">{{ partnerName }}</a-descriptions-item>
            <a-descriptions-item label="甲方">{{ formData.partyA || '-' }}</a-descriptions-item>
            <a-descriptions-item label="乙方">{{ formData.partyB || '-' }}</a-descriptions-item>
            <a-descriptions-item label="合同金额">
              {{ fmtMoney(formData.contractAmount) }} 元
            </a-descriptions-item>
            <a-descriptions-item label="签订日期">{{
              formData.signedDate || '-'
            }}</a-descriptions-item>
            <a-descriptions-item label="开始日期">{{
              formData.startDate || '-'
            }}</a-descriptions-item>
            <a-descriptions-item label="结束日期">{{
              formData.endDate || '-'
            }}</a-descriptions-item>
            <a-descriptions-item label="付款方式">{{
              formData.paymentMethod || '-'
            }}</a-descriptions-item>
            <a-descriptions-item label="结算方式">{{
              formData.settlementMethod || '-'
            }}</a-descriptions-item>
            <a-descriptions-item label="质保金比例"
              >{{ formData.warrantyRate }}%</a-descriptions-item
            >
            <a-descriptions-item label="质保金金额">
              {{ fmtMoney(formData.warrantyAmount) }} 元
            </a-descriptions-item>
            <a-descriptions-item label="备注" :span="2">{{
              formData.remark || '-'
            }}</a-descriptions-item>
          </a-descriptions>

          <div class="cf-review-section">
            <div class="cf-review-title">
              合同明细
              <span class="cf-review-sub"
                >共 {{ items.length }} 项，合计 {{ fmtMoney(itemsTotal) }} 元</span
              >
            </div>
            <a-table
              :data-source="items"
              :pagination="false"
              row-key="_key"
              size="small"
              bordered
              :columns="[
                { title: '名称', dataIndex: 'itemName' },
                { title: '规格', dataIndex: 'itemSpec' },
                { title: '单位', dataIndex: 'unit' },
                { title: '数量', dataIndex: 'quantity', align: 'right' },
                { title: '单价', dataIndex: 'unitPrice', align: 'right' },
                { title: '金额', dataIndex: 'amount', align: 'right' },
              ]"
            />
          </div>

          <div class="cf-review-section">
            <div class="cf-review-title">
              付款条款
              <span class="cf-review-sub"
                >共 {{ terms.length }} 项，合计 {{ fmtMoney(termsTotal) }} 元</span
              >
            </div>
            <a-table
              :data-source="terms"
              :pagination="false"
              row-key="_key"
              size="small"
              bordered
              :columns="[
                { title: '付款节点', dataIndex: 'termName' },
                { title: '比例(%)', dataIndex: 'paymentRatio', align: 'right' },
                { title: '金额', dataIndex: 'paymentAmount', align: 'right' },
                { title: '付款条件', dataIndex: 'paymentCondition' },
                { title: '计划日期', dataIndex: 'plannedDate' },
              ]"
            />
          </div>

          <div class="cf-review-actions">
            <a-button :loading="submitting" @click="handleSaveDraft">保存草稿</a-button>
          </div>
        </div>
      </StepWizard>

      <div class="cf-cancel">
        <a-button type="text" :disabled="submitting" @click="handleCancel">取消</a-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cf-page {
  background: var(--bg);
  min-height: 100%;
  padding: 4px 0;
}
.cf-breadcrumb {
  margin-bottom: 16px;
  font-size: 14px;
}
.cf-card {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
  padding-bottom: 12px;
  position: relative;
}
.cf-review {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.cf-review-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.cf-review-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.cf-review-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--muted);
}
.cf-review-actions {
  display: flex;
  justify-content: center;
}
.cf-cancel {
  position: absolute;
  top: 28px;
  right: 32px;
}
</style>
