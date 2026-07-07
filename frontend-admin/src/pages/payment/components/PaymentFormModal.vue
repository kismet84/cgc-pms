<script setup lang="ts">
import type { PayApplicationBasisVO, PayApplicationVO } from '@/types/payment'

type ProjectOption = {
  id: string
  projectName?: string
}

type ContractOption = {
  id: string
  contractName?: string
}

type BasisOption = {
  id: string
  label: string
}

type BasisRow = Partial<PayApplicationBasisVO> & { key: number }

const props = defineProps<{
  open: boolean
  title: string
  formData: Partial<PayApplicationVO>
  projects?: ProjectOption[]
  contracts?: ContractOption[]
  formPartnerName: string
  payTypeLabel: Record<string, string>
  basisList: BasisRow[]
  getSourceOptions: (sourceType?: string) => BasisOption[]
  onFormProjectChange: (value: string) => void
  onContractChange: (value: string) => void
  onAddBasis: () => void
  onSourceChange: (index: number) => void
  onRemoveBasis: (index: number) => void
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
    :width="800"
    @update:open="(value) => emit('update:open', value)"
    @ok="emit('submit')"
  >
    <a-form layout="vertical" :model="props.formData">
      <a-row :gutter="16">
        <a-col :span="12"
          ><a-form-item label="项目"
            ><a-select
              v-model:value="props.formData.projectId"
              placeholder="请选择项目"
              style="width: 100%"
              :options="(props.projects ?? []).map((p) => ({ value: p.id, label: p.projectName }))"
              @change="props.onFormProjectChange" /></a-form-item
        ></a-col>
        <a-col :span="12"
          ><a-form-item label="合同"
            ><a-select
              v-model:value="props.formData.contractId"
              placeholder="请选择合同"
              style="width: 100%"
              :options="(props.contracts ?? []).map((c) => ({ value: c.id, label: c.contractName }))"
              @change="props.onContractChange" /></a-form-item
        ></a-col>
      </a-row>
      <a-row :gutter="16">
        <a-col :span="12"
          ><a-form-item label="合作方"
            ><a-input
              :value="props.formPartnerName"
              disabled
              placeholder="选择合同后自动填充乙方" /></a-form-item
        ></a-col>
        <a-col :span="12"
          ><a-form-item label="付款类型"
            ><a-select
              v-model:value="props.formData.payType"
              placeholder="请选择付款类型"
              style="width: 100%"
              ><a-select-option v-for="(label, key) in props.payTypeLabel" :key="key" :value="key">{{
                label
              }}</a-select-option></a-select
            ></a-form-item
          ></a-col
        >
      </a-row>
      <a-row :gutter="16">
        <a-col :span="12"
          ><a-form-item label="申请编号" required
            ><a-input v-model:value="props.formData.applyCode" placeholder="请输入申请编号" /></a-form-item
        ></a-col>
        <a-col :span="12"
          ><a-form-item label="申请金额"
            ><a-input-number
              v-model:value="props.formData.applyAmount"
              :min="0"
              :precision="2"
              style="width: 100%"
              placeholder="金额（元）" /></a-form-item
        ></a-col>
        <a-col :span="12"
          ><a-form-item label="申请原因"
            ><a-textarea
              v-model:value="props.formData.applyReason"
              placeholder="申请原因"
              :rows="2" /></a-form-item
        ></a-col>
      </a-row>
    </a-form>
    <div style="border-top: 1px solid #f0f0f0; padding-top: 12px; margin-top: 4px">
      <div
        style="
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 10px;
        "
      >
        <span style="font-weight: 600; font-size: 14px">付款依据</span
        ><a-button size="small" @click="props.onAddBasis">添加依据行</a-button>
      </div>
      <a-table :data-source="props.basisList" :pagination="false" row-key="key" size="small" :scroll="{ y: 240 }">
        <a-table-column title="来源类型" width="100"
          ><template #default="{ record: item, index }"
            ><a-select
              v-model:value="item.basisType"
              size="small"
              style="width: 100%"
              @change="props.onSourceChange(index)"
              ><a-select-option value="MAT_RECEIPT">材料验收</a-select-option
              ><a-select-option value="SUB_MEASURE">分包计量</a-select-option></a-select
            ></template
          ></a-table-column
        >
        <a-table-column title="来源单据" width="240"
          ><template #default="{ record: item }"
            ><a-select
              v-model:value="item.basisId"
              size="small"
              placeholder="选择明细"
              allow-clear
              style="width: 100%"
              ><a-select-option v-for="opt in props.getSourceOptions(item.basisType)" :key="opt.id" :value="opt.id">{{
                opt.label
              }}</a-select-option></a-select
            ></template
          ></a-table-column
        >
        <a-table-column title="金额" width="160"
          ><template #default="{ record: item }"
            ><a-input-number
              v-model:value="item.basisAmount"
              :min="0"
              :precision="2"
              size="small"
              style="width: 100%"
              placeholder="金额" /></template
        ></a-table-column>
        <a-table-column title="操作" width="76"
          ><template #default="{ index }"
            ><a-button type="link" size="small" danger @click="props.onRemoveBasis(index)">删除</a-button></template
          ></a-table-column
        >
      </a-table>
    </div>
  </a-modal>
</template>
