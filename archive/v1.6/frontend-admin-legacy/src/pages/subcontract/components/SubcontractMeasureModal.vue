<script setup lang="ts">
import type { ContractItem, ContractVO } from '@/types/contract'
import type { ProjectVO } from '@/types/project'
import type { SubMeasureItemVO, SubMeasureVO, SubTaskVO } from '@/types/subcontract'

defineProps<{
  open: boolean
  title: string
  formData: Partial<SubMeasureVO>
  projectList: ProjectVO[] | null | undefined
  contractList: ContractVO[] | null | undefined
  subTaskOptions: SubTaskVO[]
  formPartnerName: string
  itemList: Array<Partial<SubMeasureItemVO> & { key: number }>
  contractItemList: ContractItem[]
  itemsTotalAmount: string
  attachmentFileName?: string
  existingAttachmentCount: number
  onOk: () => void
  onCancel: () => void
  onProjectChange: (projectId: string) => void
  onContractSelect: (contractId: string | undefined) => void
  onAddItem: () => void
  onRemoveItem: (index: number) => void
  onContractItemChange: (index: number, itemId: string | undefined) => void
  onItemQtyChange: (index: number) => void
  onItemPriceChange: (index: number) => void
  onAttachmentFileChange: (event: Event) => void
}>()

function filterOptionByLabel(input: string, option?: { label?: string | number }) {
  return String(option?.label ?? '')
    .toLowerCase()
    .includes(input.toLowerCase())
}
</script>

<template>
  <a-modal
    :open="open"
    :title="title"
    :width="800"
    wrap-class-name="compact-subcontract-measure-modal"
    @ok="onOk"
    @cancel="onCancel"
  >
    <div class="subcontract-measure-modal-body">
      <a-form
        :label-col="{ span: 5 }"
        :wrapper-col="{ span: 18 }"
        class="subcontract-measure-modal-form"
        size="small"
      >
        <a-form-item label="项目" required>
          <a-select
            v-model:value="formData.projectId"
            placeholder="请选择项目"
            show-search
            :filter-option="filterOptionByLabel"
            @change="(v: string) => onProjectChange(v)"
          >
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="分包合同" required>
          <a-select
            v-model:value="formData.contractId"
            placeholder="请选择合同"
            allow-clear
            show-search
            :filter-option="filterOptionByLabel"
            @change="(val: string | undefined) => onContractSelect(val)"
          >
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="分包商" required>
          <a-input :value="formPartnerName" disabled placeholder="选择合同后自动填充乙方" />
        </a-form-item>
        <a-form-item label="关联任务" required>
          <a-select
            v-model:value="formData.subTaskId"
            placeholder="请选择关联分包任务"
            allow-clear
            show-search
            :filter-option="filterOptionByLabel"
          >
            <a-select-option v-for="t in subTaskOptions" :key="t.id" :value="t.id">
              {{ t.taskCode }} {{ t.taskName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="计量期次" required>
          <a-input
            v-model:value="formData.measurePeriod"
            placeholder="请输入计量期次（如：第1期）"
          />
        </a-form-item>
        <a-form-item label="计量日期" required>
          <a-date-picker
            v-model:value="formData.measureDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="扣款金额">
          <a-input-number
            v-model:value="formData.deductionAmount"
            :min="0"
            :precision="2"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="计量附件" required>
          <input
            type="file"
            accept=".pdf,.png,.jpg,.jpeg,.xlsx,.docx"
            @change="onAttachmentFileChange"
          />
          <span v-if="attachmentFileName" style="margin-left: 8px">{{ attachmentFileName }}</span>
          <span v-else-if="existingAttachmentCount" style="margin-left: 8px">
            已上传 {{ existingAttachmentCount }} 份
          </span>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="formData.remark" :rows="1" placeholder="请输入备注" />
        </a-form-item>
      </a-form>

      <div class="subcontract-measure-items-section">
        <div class="subcontract-measure-items-head">
          <span>计量明细</span>
          <a-button type="dashed" size="small" @click="onAddItem">+ 添加明细</a-button>
        </div>

        <a-table
          :data-source="itemList"
          :pagination="false"
          row-key="key"
          size="small"
          :scroll="{ x: 826, y: 220 }"
        >
          <a-table-column title="合同清单项" width="200">
            <template #default="{ record: item, index }">
              <a-select
                :value="item.contractItemId"
                placeholder="请选择清单项"
                allow-clear
                style="width: 100%"
                @change="(val: string | undefined) => onContractItemChange(index, val)"
              >
                <a-select-option v-for="ci in contractItemList" :key="ci.id" :value="ci.id">
                  {{ ci.itemName }}
                </a-select-option>
              </a-select>
            </template>
          </a-table-column>
          <a-table-column title="单位" width="70">
            <template #default="{ record: item }">
              <span>{{ item.unit || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="合同量" width="100">
            <template #default="{ record: item }">
              <span>{{ item.contractQuantity || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="本期量" width="120">
            <template #default="{ record: item, index }">
              <a-input-number
                v-model:value="item.currentQuantity"
                :min="0"
                :precision="4"
                style="width: 100%"
                @change="onItemQtyChange(index)"
              />
            </template>
          </a-table-column>
          <a-table-column title="单价(元)" width="130">
            <template #default="{ record: item, index }">
              <a-input-number
                v-model:value="item.unitPrice"
                :min="0"
                :precision="4"
                style="width: 100%"
                @change="onItemPriceChange(index)"
              />
            </template>
          </a-table-column>
          <a-table-column title="金额(元)" width="130">
            <template #default="{ record: item }">
              <span>{{
                Number(item.amount || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
              }}</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" width="76">
            <template #default="{ index }">
              <a-button type="link" size="small" danger @click="onRemoveItem(index)">删除</a-button>
            </template>
          </a-table-column>
        </a-table>

        <div class="subcontract-measure-items-total">
          合计：<span>{{
            Number(itemsTotalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
          }}</span>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<style scoped>
.subcontract-measure-modal-body {
  max-height: calc(100vh - 220px);
  overflow: auto;
  padding-right: 4px;
}

.subcontract-measure-modal-form :deep(.ant-form-item) {
  margin-bottom: 8px;
}

.subcontract-measure-items-section {
  padding-top: 10px;
  margin-top: 2px;
  border-top: 1px solid #f0f0f0;
}

.subcontract-measure-items-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.subcontract-measure-items-head span {
  font-weight: 600;
  font-size: 14px;
  color: #0f172a;
}

.subcontract-measure-items-total {
  margin-top: 8px;
  text-align: right;
  font-size: 13px;
}

.subcontract-measure-items-total span {
  font-weight: 600;
  color: #1677ff;
}

:global(.compact-subcontract-measure-modal .ant-modal-body) {
  padding-top: 12px;
  padding-bottom: 12px;
}

:global(.compact-subcontract-measure-modal .ant-modal-footer) {
  margin-top: 0;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}
</style>
