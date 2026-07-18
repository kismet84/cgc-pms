<script setup lang="ts">
import type { BudgetLineVO } from '@/types/budget'
import type {
  PayApplicationVO,
  PaymentApplicationSourceVO,
  PaymentSourceOptionVO,
} from '@/types/payment'

type ProjectOption = { id: string; projectName?: string }
type ContractOption = { id: string; contractName?: string }
type SourceRow = Partial<PaymentApplicationSourceVO> & { key: number }

const props = defineProps<{
  open: boolean
  title: string
  formData: Partial<PayApplicationVO>
  projects?: ProjectOption[]
  contracts?: ContractOption[]
  formPartnerName: string
  payTypeLabel: Record<string, string>
  budgetLines: BudgetLineVO[]
  sourceList: SourceRow[]
  sourceOptions: PaymentSourceOptionVO[]
  sourceOptionsLoading: boolean
  proofFileName?: string
  onFormProjectChange: (value: string) => void
  onContractChange: (value: string) => void
  onBudgetLineChange: (value: string) => void
  onAddSource: () => void
  onRemoveSource: (index: number) => void
  onSourceTypeChange: (record: SourceRow) => void
  onProofFileChange: (event: Event) => void
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'submit'): void
}>()
</script>

<template>
  <a-modal
    :open="props.open"
    :title="props.title"
    :width="900"
    @update:open="(value) => emit('update:open', value)"
    @ok="emit('submit')"
  >
    <a-alert
      type="info"
      show-icon
      style="margin-bottom: 16px"
      message="付款申请提交前将校验项目、合同、预算、附件、费用分类、付款对象与来源金额。"
    />
    <a-form layout="vertical" :model="props.formData">
      <a-row :gutter="16">
        <a-col :span="12"
          ><a-form-item label="项目" required
            ><a-select
              v-model:value="props.formData.projectId"
              :options="(props.projects ?? []).map((p) => ({ value: p.id, label: p.projectName }))"
              @change="props.onFormProjectChange" /></a-form-item
        ></a-col>
        <a-col :span="12"
          ><a-form-item label="合同" required
            ><a-select
              v-model:value="props.formData.contractId"
              :options="
                (props.contracts ?? []).map((c) => ({ value: c.id, label: c.contractName }))
              "
              @change="props.onContractChange" /></a-form-item
        ></a-col>
        <a-col :span="12"
          ><a-form-item label="付款对象" required
            ><a-input
              :value="props.formPartnerName"
              disabled
              placeholder="选择合同后自动带出乙方" /></a-form-item
        ></a-col>
        <a-col :span="12"
          ><a-form-item label="预算/成本科目" required
            ><a-select
              v-model:value="props.formData.budgetLineId"
              @change="props.onBudgetLineChange"
              ><a-select-option v-for="line in props.budgetLines" :key="line.id" :value="line.id"
                >{{ line.costSubjectName }}（可用 {{ line.availableAmount }}）</a-select-option
              ></a-select
            ></a-form-item
          ></a-col
        >
        <a-col :span="8"
          ><a-form-item label="付款类型" required
            ><a-select v-model:value="props.formData.payType"
              ><a-select-option
                v-for="(label, key) in props.payTypeLabel"
                :key="key"
                :value="key"
                >{{ label }}</a-select-option
              ></a-select
            ></a-form-item
          ></a-col
        >
        <a-col :span="8"
          ><a-form-item label="费用分类" required
            ><a-select v-model:value="props.formData.expenseCategory"
              ><a-select-option value="LABOR">人工费</a-select-option
              ><a-select-option value="MATERIAL">材料费</a-select-option
              ><a-select-option value="SUBCONTRACT">分包费</a-select-option
              ><a-select-option value="OTHER">其他</a-select-option></a-select
            ></a-form-item
          ></a-col
        >
        <a-col :span="8"
          ><a-form-item label="申请金额" required
            ><a-input-number
              v-model:value="props.formData.applyAmount"
              :min="0.01"
              :precision="2"
              style="width: 100%" /></a-form-item
        ></a-col>
        <a-col :span="24"
          ><a-form-item label="申请原因" required
            ><a-textarea v-model:value="props.formData.applyReason" :rows="2" /></a-form-item
        ></a-col>
        <a-col :span="24"
          ><a-form-item label="付款附件" required
            ><input
              type="file"
              accept=".pdf,.png,.jpg,.jpeg"
              @change="props.onProofFileChange"
            /><span v-if="props.proofFileName" style="margin-left: 8px">{{
              props.proofFileName
            }}</span></a-form-item
          ></a-col
        >
      </a-row>
    </a-form>

    <div style="border-top: 1px solid #f0f0f0; padding-top: 12px">
      <div
        style="
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 10px;
        "
      >
        <strong>统一付款来源</strong
        ><a-button size="small" @click="props.onAddSource">添加来源</a-button>
      </div>
      <a-table :data-source="props.sourceList" :pagination="false" row-key="key" size="small">
        <a-table-column title="来源类型" width="160"
          ><template #default="{ record }"
            ><a-select
              v-model:value="record.sourceType"
              style="width: 100%"
              @change="props.onSourceTypeChange(record)"
              ><a-select-option value="EXPENSE">费用申请</a-select-option
              ><a-select-option value="SUB_MEASURE">已审批分包计量（进度款）</a-select-option
              ><a-select-option value="SETTLEMENT">结算申请</a-select-option
              ><a-select-option value="DIRECT">直接付款</a-select-option></a-select
            ></template
          ></a-table-column
        >
        <a-table-column title="来源业务单据"
          ><template #default="{ record }"
            ><a-select
              v-if="record.sourceType === 'SUB_MEASURE' || record.sourceType === 'SETTLEMENT'"
              v-model:value="record.sourceRefId"
              :loading="props.sourceOptionsLoading"
              :options="
                props.sourceOptions
                  .filter((option) => option.sourceType === record.sourceType)
                  .map((option) => ({
                    value: option.sourceRefId,
                    label: `${option.documentCode}（可申请 ${option.availableAmount}）`,
                  }))
              "
              show-search
              option-filter-prop="label"
              placeholder="请选择当前上下文内的可付业务单据"
              style="width: 100%" /><a-input
              v-else
              v-model:value="record.sourceRefId"
              :disabled="record.sourceType === 'DIRECT'"
              :placeholder="
                record.sourceType === 'DIRECT'
                  ? '保存后自动使用付款申请ID'
                  : '请输入已审批费用来源单据ID'
              " /></template
        ></a-table-column>
        <a-table-column title="来源金额" width="200"
          ><template #default="{ record }"
            ><a-input-number
              v-model:value="record.sourceAmount"
              :min="0.01"
              :precision="2"
              style="width: 100%" /></template
        ></a-table-column>
        <a-table-column title="操作" width="80"
          ><template #default="{ index }"
            ><a-button type="link" danger @click="props.onRemoveSource(index)"
              >删除</a-button
            ></template
          ></a-table-column
        >
      </a-table>
    </div>
  </a-modal>
</template>
