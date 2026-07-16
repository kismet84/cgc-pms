<script setup lang="ts">
import type { PropType } from 'vue'
import type { VarOrderItemVO, VarOrderVO } from '@/types/variation'

type ProjectOption = {
  id: string
  projectName?: string
}

type ContractOption = {
  id: string
  contractName?: string
  projectId?: string
}

defineProps({
  open: {
    type: Boolean,
    required: true,
  },
  title: {
    type: String,
    required: true,
  },
  modalReadonly: {
    type: Boolean,
    required: true,
  },
  formData: {
    type: Object as PropType<Partial<VarOrderVO>>,
    required: true,
  },
  projectList: {
    type: Array as PropType<ProjectOption[]>,
    required: true,
  },
  contractList: {
    type: Array as PropType<ContractOption[]>,
    required: true,
  },
  formPartnerName: {
    type: String,
    required: true,
  },
  varTypeOptions: {
    type: Array as PropType<Array<{ value: string; label: string }>>,
    required: true,
  },
  directionOptions: {
    type: Array as PropType<Array<{ value: string; label: string; disabled?: boolean }>>,
    required: true,
  },
  itemList: {
    type: Array as PropType<Array<Partial<VarOrderItemVO> & { key: number }>>,
    required: true,
  },
  contractItemsLoading: {
    type: Boolean,
    required: true,
  },
  costSubjectOptions: {
    type: Array as PropType<Array<{ value: string; label: string }>>,
    required: true,
  },
  itemsTotalAmount: {
    type: Number,
    required: true,
  },
  itemsClaimTotalAmount: { type: Number, required: true },
  onFormProjectChange: {
    type: Function as PropType<(projectId: string) => void>,
    required: true,
  },
  onContractChange: {
    type: Function as PropType<(contractId: string) => void | Promise<void>>,
    required: true,
  },
  handleSubmit: {
    type: Function as PropType<() => void | Promise<void>>,
    required: true,
  },
  handleAddItem: {
    type: Function as PropType<() => void>,
    required: true,
  },
  handleItemQtyChange: {
    type: Function as PropType<(index: number) => void>,
    required: true,
  },
  handleItemPriceChange: {
    type: Function as PropType<(index: number) => void>,
    required: true,
  },
  handleItemClaimPriceChange: {
    type: Function as PropType<(index: number) => void>,
    required: true,
  },
  onEvidenceFileChange: {
    type: Function as PropType<(file?: File) => void>,
    required: true,
  },
  handleRemoveItem: {
    type: Function as PropType<(index: number) => void>,
    required: true,
  },
})

defineEmits<{
  'update:open': [value: boolean]
}>()
</script>

<template>
  <a-modal
    :open="open"
    :title="title"
    :width="800"
    :footer="modalReadonly ? null : undefined"
    @update:open="$emit('update:open', $event)"
    @ok="handleSubmit"
  >
    <a-form layout="vertical" :model="formData" :disabled="modalReadonly">
      <a-row :gutter="16">
        <a-col :span="8"
          ><a-form-item label="项目"
            ><a-select
              v-model:value="formData.projectId"
              placeholder="请选择项目"
              style="width: 100%"
              :options="(projectList ?? []).map((p) => ({ value: p.id, label: p.projectName }))"
              @change="
                (v: string) => {
                  onFormProjectChange(v)
                }
              " /></a-form-item
        ></a-col>
        <a-col :span="8"
          ><a-form-item label="合同"
            ><a-select
              v-model:value="formData.contractId"
              placeholder="请选择合同"
              style="width: 100%"
              :options="
                (contractList ?? [])
                  .filter((c) => !formData.projectId || c.projectId === formData.projectId)
                  .map((c) => ({ value: c.id, label: c.contractName }))
              "
              @change="onContractChange" /></a-form-item
        ></a-col>
        <a-col :span="8"
          ><a-form-item label="合作方"
            ><a-input
              :value="formPartnerName"
              disabled
              placeholder="选择合同后自动填充乙方" /></a-form-item
        ></a-col>
      </a-row>
      <a-row :gutter="16">
        <a-col :span="8"
          ><a-form-item label="变更类型"
            ><a-select v-model:value="formData.varType" placeholder="请选择" style="width: 100%"
              ><a-select-option v-for="o in varTypeOptions" :key="o.value" :value="o.value">{{
                o.label
              }}</a-select-option></a-select
            ></a-form-item
          ></a-col
        >
        <a-col :span="8"
          ><a-form-item label="变更名称"
            ><a-input v-model:value="formData.varName" placeholder="变更名称" /></a-form-item
        ></a-col>
        <a-col :span="8"
          ><a-form-item label="方向"
            ><a-select v-model:value="formData.direction" placeholder="请选择"
              ><a-select-option
                v-for="o in directionOptions"
                :key="o.value"
                :value="o.value"
                :disabled="o.disabled"
                >{{ o.label }}</a-select-option
              ></a-select
            ></a-form-item
          ></a-col
        >
      </a-row>
      <a-row :gutter="16">
        <a-col :span="8"
          ><a-form-item label="影响工期(天)"
            ><a-input-number
              v-model:value="formData.impactDays"
              :min="0"
              style="width: 100%" /></a-form-item
        ></a-col>
        <a-col :span="8"
          ><a-form-item label="事件日期（必填）"
            ><a-input v-model:value="formData.eventDate" type="date" /></a-form-item
        ></a-col>
        <a-col :span="8"
          ><a-form-item label="索赔申报截止日"
            ><a-input v-model:value="formData.claimDeadline" type="date" /></a-form-item
        ></a-col>
      </a-row>
      <a-row :gutter="16">
        <a-col :span="8"
          ><a-form-item label="原因分类（必填）"
            ><a-input v-model:value="formData.causeCategory" /></a-form-item
        ></a-col>
        <a-col :span="8"
          ><a-form-item label="责任方"
            ><a-input v-model:value="formData.responsibleParty" /></a-form-item
        ></a-col>
        <a-col :span="8"
          ><a-form-item label="业务事项键"
            ><a-input
              v-model:value="formData.businessMatterKey"
              placeholder="用于防止重复立项" /></a-form-item
        ></a-col>
      </a-row>
      <a-form-item label="事件及影响说明（必填）"
        ><a-textarea v-model:value="formData.eventDescription" :rows="2"
      /></a-form-item>
      <a-row :gutter="16">
        <a-col :span="12"
          ><a-form-item label="现场证据（提交审批必需）"
            ><input
              type="file"
              :disabled="modalReadonly"
              @change="
                onEvidenceFileChange(($event.target as HTMLInputElement).files?.[0])
              " /></a-form-item
        ></a-col>
        <a-col :span="12"
          ><a-form-item label="备注"
            ><a-textarea v-model:value="formData.remark" :rows="2" /></a-form-item
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
        <span style="font-weight: 600; font-size: 14px">变更明细</span
        ><a-button type="dashed" size="small" :disabled="modalReadonly" @click="handleAddItem"
          >+ 添加明细</a-button
        >
      </div>
      <a-table
        :data-source="itemList"
        :loading="contractItemsLoading"
        :pagination="false"
        row-key="key"
        size="small"
        :scroll="{ x: 1180, y: 250 }"
      >
        <a-table-column title="清单项名称" width="160"
          ><template #default="{ record: item }"
            ><a-input
              v-model:value="item.itemName"
              placeholder="名称"
              :disabled="modalReadonly"
              style="width: 100%" /></template
        ></a-table-column>
        <a-table-column title="单位" width="70"
          ><template #default="{ record: item }"
            ><a-input
              v-model:value="item.unit"
              placeholder="单位"
              :disabled="modalReadonly"
              style="width: 100%" /></template
        ></a-table-column>
        <a-table-column title="成本科目" width="180"
          ><template #default="{ record: item }"
            ><a-select
              v-model:value="item.costSubjectId"
              placeholder="选择成本科目"
              :options="costSubjectOptions"
              show-search
              option-filter-prop="label"
              popup-match-select-width="false"
              :dropdown-style="{ minWidth: '280px' }"
              :disabled="modalReadonly"
              style="width: 100%" /></template
        ></a-table-column>
        <a-table-column title="数量" width="120"
          ><template #default="{ record: item, index }"
            ><a-input-number
              v-model:value="item.quantity"
              :min="0"
              :precision="4"
              :disabled="modalReadonly"
              style="width: 100%"
              @change="handleItemQtyChange(index)" /></template
        ></a-table-column>
        <a-table-column title="内部成本单价" width="130"
          ><template #default="{ record: item, index }"
            ><a-input-number
              v-model:value="item.unitPrice"
              :min="0"
              :precision="4"
              :disabled="modalReadonly"
              style="width: 100%"
              @change="handleItemPriceChange(index)" /></template
        ></a-table-column>
        <a-table-column title="内部成本金额" width="130"
          ><template #default="{ record: item }"
            ><span>{{
              Number(item.amount || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
            }}</span></template
          ></a-table-column
        >
        <a-table-column title="业主申报单价" width="130"
          ><template #default="{ record: item, index }"
            ><a-input-number
              v-model:value="item.claimUnitPrice"
              :min="0"
              :precision="4"
              :disabled="modalReadonly"
              style="width: 100%"
              @change="handleItemClaimPriceChange(index)" /></template
        ></a-table-column>
        <a-table-column title="业主申报金额" width="130"
          ><template #default="{ record: item }"
            ><span>{{
              Number(item.claimAmount || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
            }}</span></template
          ></a-table-column
        >
        <a-table-column title="操作" width="76"
          ><template #default="{ index }"
            ><a-button
              type="link"
              size="small"
              danger
              :disabled="modalReadonly"
              @click="handleRemoveItem(index)"
              >删除</a-button
            ></template
          ></a-table-column
        >
      </a-table>
      <div style="text-align: right; margin-top: 8px; font-size: 14px">
        内部成本：<span style="font-weight: 600; color: #cf1322"
          >¥{{
            Number(itemsTotalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
          }}</span
        >　业主申报：<span style="font-weight: 600; color: #1677ff"
          >¥{{
            Number(itemsClaimTotalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
          }}</span
        >
      </div>
    </div>
  </a-modal>
</template>
