<script setup lang="ts">
import type { MatPurchaseOrderItemVO, MatPurchaseOrderVO } from '@/types/purchase'
import type { SelectOption } from '@/types/ui'

const props = defineProps<{
  open: boolean
  title: string
  isViewMode: boolean
  formData: Partial<MatPurchaseOrderVO>
  formPartnerName: string
  projectList: Array<{ id: string; projectName?: string }>
  contractList: Array<{ id: string; contractName?: string }>
  materialList: Array<{ id: string; materialName?: string; specification?: string; unit?: string }>
  itemList: Array<Partial<MatPurchaseOrderItemVO> & { key: number }>
  itemsTotalAmount: string
  onProjectChange: (value: string) => void
  onContractChange: (value: string) => void
  onAddItem: () => void
  onRemoveItem: (index: number) => void
  onMaterialChange: (index: number, value: string | undefined) => void
  onItemQtyChange: (index: number) => void
  onItemPriceChange: (index: number) => void
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'ok'): void
  (e: 'cancel'): void
}>()

function handleCancel() {
  emit('update:open', false)
  emit('cancel')
}
</script>

<template>
  <a-modal
    :open="open"
    :title="title"
    :width="800"
    :ok-button-props="isViewMode ? { style: { display: 'none' } } : undefined"
    :cancel-text="isViewMode ? '关闭' : '取消'"
    @update:open="emit('update:open', $event)"
    @ok="emit('ok')"
    @cancel="handleCancel"
  >
    <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" style="margin-bottom: 8px">
      <a-form-item label="项目" required>
        <a-select
          v-model:value="props.formData.projectId"
          :disabled="isViewMode"
          placeholder="请选择项目"
          show-search
          @change="onProjectChange"
          :filter-option="
            (input: string, option: SelectOption) =>
              option.label?.toLowerCase().includes(input.toLowerCase())
          "
        >
          <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
            {{ p.projectName }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="采购合同">
        <a-select
          v-model:value="props.formData.contractId"
          :disabled="isViewMode"
          placeholder="请选择合同"
          allow-clear
          show-search
          :filter-option="
            (input: string, option: SelectOption) =>
              option.label?.toLowerCase().includes(input.toLowerCase())
          "
          @change="onContractChange"
        >
          <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
            {{ c.contractName }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="供应商">
        <a-input :value="formPartnerName" disabled placeholder="选择合同后自动填充乙方" />
      </a-form-item>
      <a-form-item label="订单类型">
        <a-select
          v-model:value="props.formData.orderType"
          :disabled="isViewMode"
          placeholder="请选择类型"
          allow-clear
        >
          <a-select-option value="MATERIAL">材料采购</a-select-option>
          <a-select-option value="EQUIPMENT">设备采购</a-select-option>
          <a-select-option value="SERVICE">服务采购</a-select-option>
          <a-select-option value="OTHER">其他</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="订单日期">
        <a-date-picker
          v-model:value="props.formData.orderDate"
          :disabled="isViewMode"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </a-form-item>
      <a-form-item label="交货日期">
        <a-date-picker
          v-model:value="props.formData.deliveryDate"
          :disabled="isViewMode"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </a-form-item>
      <a-form-item label="备注">
        <a-textarea
          v-model:value="props.formData.remark"
          :disabled="isViewMode"
          :rows="2"
          placeholder="请输入备注"
        />
      </a-form-item>
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
        <span style="font-weight: 600; font-size: 14px">订单明细</span>
        <a-button v-if="!isViewMode" type="dashed" size="small" @click="onAddItem"
          >+ 添加明细</a-button
        >
      </div>

      <a-table
        :data-source="itemList"
        :pagination="false"
        row-key="key"
        size="small"
        :scroll="{ y: 250 }"
      >
        <a-table-column title="材料" width="200">
          <template #default="{ record: item, index }">
            <a-select
              :value="item.materialId"
              :disabled="isViewMode"
              placeholder="请选择材料"
              allow-clear
              style="width: 100%"
              show-search
              :filter-option="
                (input: string, option: SelectOption) =>
                  option.label?.toLowerCase().includes(input.toLowerCase())
              "
              @change="(val: string) => onMaterialChange(index, val)"
            >
              <a-select-option v-for="m in materialList" :key="m.id" :value="m.id">
                {{ m.materialName }}
              </a-select-option>
            </a-select>
          </template>
        </a-table-column>
        <a-table-column title="规格" width="100">
          <template #default="{ record: item }">
            <span>{{ item.specification || '-' }}</span>
          </template>
        </a-table-column>
        <a-table-column title="单位" width="70">
          <template #default="{ record: item }">
            <span>{{ item.unit || '-' }}</span>
          </template>
        </a-table-column>
        <a-table-column title="数量" width="120">
          <template #default="{ record: item, index }">
            <a-input-number
              v-model:value="item.quantity"
              :disabled="isViewMode"
              :min="0"
              :precision="2"
              style="width: 100%"
              @change="onItemQtyChange(index)"
            />
          </template>
        </a-table-column>
        <a-table-column title="单价(元)" width="130">
          <template #default="{ record: item, index }">
            <a-input-number
              v-model:value="item.unitPrice"
              :disabled="isViewMode"
              :min="0"
              :precision="2"
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
            <a-button
              v-if="!isViewMode"
              type="link"
              size="small"
              danger
              @click="onRemoveItem(index)"
              >删除</a-button
            >
          </template>
        </a-table-column>
      </a-table>

      <div style="text-align: right; margin-top: 8px; font-size: 14px">
        合计：<span style="font-weight: 600; color: #1677ff"
          >¥{{
            Number(itemsTotalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
          }}</span
        >
      </div>
    </div>
  </a-modal>
</template>
